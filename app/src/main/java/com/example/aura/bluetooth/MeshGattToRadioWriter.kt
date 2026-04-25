package com.example.aura.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.aura.mesh.transport.BleTransportManager
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireWantConfigHandshake
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "MeshToRadio"

/**
 * Запись через [NodeGattConnection], если постоянный BLE GATT уже в [NodeConnectionState.READY].
 *
 * Сверка MAC с [deviceAddress] не используется: на Android второй одновременный `connectGatt` к той же
 * ноде (когда Aura уже держит GATT в [NodeGattConnection]) часто не поднимается — тогда админ-записи
 * «не доходят». TCP/USB по-прежнему идут в [MeshStreamToRadio], не в BLE-очередь.
 */
private fun shouldRouteWritesThroughPersistentGatt(deviceAddress: String): Boolean {
    if (!NodeGattConnection.isReady) return false
    val nk = MeshNodeSyncMemoryStore.normalizeKey(deviceAddress)
    if (MeshDeviceTransport.isTcpAddress(nk) || MeshDeviceTransport.isUsbAddress(nk)) return false
    return true
}

/** См. [NodeGattConnection.toRadioWriteType] / Meshtastic: ToRadio обычно [WRITE_TYPE_NO_RESPONSE]. */
private fun toRadioWriteTypeForCharacteristic(c: BluetoothGattCharacteristic): Int =
    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    } else {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }

/** Как в [MeshBleScanner]: пауза после записи CCCD перед want_config. */
private const val DRAIN_CCCD_SETTLE_MS = 50L

private val DRAIN_CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Одноразовое подключение: запрос MTU → запись в TORADIO → disconnect.
 */
class MeshGattToRadioWriter(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null

    /**
     * Долгоживущая BLE-сессия чата (как в типичном mesh-клиенте Android): один GATT, опрос FromRadio + очередь ToRadio.
     */
    @Volatile
    private var meshChatSessionActive: Boolean = false
    private var meshSessionAddressNorm: String? = null
    private var sessionToRadioChar: BluetoothGattCharacteristic? = null
    /** После want_config и kickDrain — можно слать пользовательские ToRadio без отдельного connectGatt. */
    @Volatile
    private var meshDrainReadyForUserToradio: Boolean = false
    /** Между connectGatt и onServicesDiscovered: нельзя вызывать closeQuietly() из writeToradio — рвётся приём чата. */
    @Volatile
    private var meshChatSessionBootstrapping: Boolean = false
    private val userToradioWriteQueue: ArrayDeque<Pair<ByteArray, (Boolean, String?) -> Unit>> =
        ArrayDeque()
    private var userToradioWriteInFlight: Boolean = false
    private var pendingUserToradioComplete: ((Boolean, String?) -> Unit)? = null
    /** Pre-API 33: [BluetoothGatt.writeCharacteristic] может вернуть false при занятом стеке — повтор, не срывать запись. */
    private var userToradioWriteLegacyRetryCount: Int = 0
    /** API 33+: r != 0 — повтор; сброс при успехе [onCharacteristicWrite] для userToradio. */
    private var userToradioWriteApiFailRetries: Int = 0

    private fun clearMeshBleSessionState() {
        meshChatSessionActive = false
        meshSessionAddressNorm = null
        sessionToRadioChar = null
        meshDrainReadyForUserToradio = false
        meshChatSessionBootstrapping = false
        userToradioWriteQueue.clear()
        userToradioWriteInFlight = false
        pendingUserToradioComplete = null
        userToradioWriteLegacyRetryCount = 0
        userToradioWriteApiFailRetries = 0
    }

    private fun enqueueUserToradioWrite(
        payload: ByteArray,
        onComplete: (ok: Boolean, error: String?, meshSummary: String?) -> Unit,
        onSessionQueued: (() -> Unit)? = null,
    ) {
        mainHandler.post {
            userToradioWriteQueue.addLast(payload to { ok, err -> onComplete(ok, err, null) })
            onSessionQueued?.invoke()
            pumpUserToradioWrites()
        }
    }

    private fun pumpUserToradioWrites() {
        if (userToradioWriteInFlight) return
        if (!meshDrainReadyForUserToradio) return
        val toChar = sessionToRadioChar ?: return
        val g = gatt ?: return
        val item = userToradioWriteQueue.pollFirst() ?: return
        val payload = item.first
        val cb = item.second
        userToradioWriteInFlight = true
        pendingUserToradioComplete = cb
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val wt = toRadioWriteTypeForCharacteristic(toChar)
                val r = g.writeCharacteristic(
                    toChar,
                    payload,
                    wt,
                )
                if (r != BluetoothGatt.GATT_SUCCESS) {
                    userToradioWriteInFlight = false
                    pendingUserToradioComplete = null
                    userToradioWriteQueue.addFirst(item)
                    userToradioWriteApiFailRetries++
                    if (userToradioWriteApiFailRetries > 220) {
                        userToradioWriteApiFailRetries = 0
                        val head = userToradioWriteQueue.pollFirst()
                        head?.second?.invoke(
                            false,
                            "writeCharacteristic: $r (исчерпаны повторы)",
                        )
                        pumpUserToradioWrites()
                    } else {
                        mainHandler.postDelayed({ pumpUserToradioWrites() }, 40L)
                    }
                } else {
                    userToradioWriteApiFailRetries = 0
                }
            } else {
                @Suppress("DEPRECATION")
                toChar.writeType = toRadioWriteTypeForCharacteristic(toChar)
                @Suppress("DEPRECATION")
                toChar.value = payload
                @Suppress("DEPRECATION")
                if (!g.writeCharacteristic(toChar)) {
                    userToradioWriteInFlight = false
                    pendingUserToradioComplete = null
                    userToradioWriteQueue.addFirst(item)
                    userToradioWriteLegacyRetryCount++
                    if (userToradioWriteLegacyRetryCount > 300) {
                        userToradioWriteLegacyRetryCount = 0
                        val head = userToradioWriteQueue.pollFirst()
                        head?.second?.invoke(false, "writeCharacteristic false")
                        pumpUserToradioWrites()
                    } else {
                        mainHandler.postDelayed({ pumpUserToradioWrites() }, 45L)
                    }
                } else {
                    userToradioWriteLegacyRetryCount = 0
                }
            }
        } catch (e: SecurityException) {
            userToradioWriteInFlight = false
            pendingUserToradioComplete = null
            userToradioWriteQueue.addFirst(item)
            val head = userToradioWriteQueue.pollFirst()
            head?.second?.invoke(false, e.message)
            pumpUserToradioWrites()
        }
    }

    /**
     * Несколько пакетов ToRadio в одном GATT-подключении (begin_edit → … → commit_edit).
     * Если [NodeGattConnection] готов к тому же MAC — маршрутизирует через него без лишнего connectGatt.
     */
    fun writeToradioQueue(
        deviceAddress: String,
        payloads: List<ByteArray>,
        delayBetweenWritesMs: Long = 100L,
        onComplete: (ok: Boolean, error: String?) -> Unit,
    ) {
        if (payloads.isEmpty()) {
            mainHandler.post { onComplete(true, null) }
            return
        }
        if (shouldRouteWritesThroughPersistentGatt(deviceAddress)) {
            // Строго по одному: иначе в очереди ToRadio сразу N кадров — часть стеков GATT/ноды даёт false/ошибки
            // и ломает приём «Отправить» в настройках каналов; следующий пакет — только после успешной записи.
            if (payloads.size == 1) {
                NodeGattConnection.sendToRadio(
                    bytes = payloads[0],
                    delayAfterMs = 280L,
                ) { ok, err -> mainHandler.post { onComplete(ok, err) } }
                return
            }
            var nextIndex = 0
            fun sendNext() {
                if (nextIndex >= payloads.size) {
                    mainHandler.post { onComplete(true, null) }
                    return
                }
                val idx = nextIndex++
                val isLast = idx == payloads.size - 1
                NodeGattConnection.sendToRadio(
                    bytes = payloads[idx],
                    delayAfterMs = if (isLast) 280L else delayBetweenWritesMs,
                ) { ok, err ->
                    if (!ok) {
                        mainHandler.post { onComplete(false, err) }
                    } else if (isLast) {
                        mainHandler.post { onComplete(true, null) }
                    } else {
                        mainHandler.post { sendNext() }
                    }
                }
            }
            mainHandler.post { sendNext() }
            return
        }
        if (payloads.size == 1) {
            writeToradio(deviceAddress, payloads.first(), onComplete = onComplete)
            return
        }
        closeQuietly()
        val norm = MeshNodeSyncMemoryStore.normalizeKey(deviceAddress)
        MeshDeviceTransport.parseTcp(norm)?.let { ep ->
            MeshStreamToRadio.writeToradioQueueTcp(ep, payloads, delayBetweenWritesMs, onComplete)
            return
        }
        MeshDeviceTransport.parseUsb(norm)?.let { ep ->
            MeshStreamToRadio.writeToradioQueueUsb(appContext, ep, payloads, delayBetweenWritesMs, onComplete)
            return
        }
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            mainHandler.post { onComplete(false, "Bluetooth выключен") }
            return
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (_: IllegalArgumentException) {
            mainHandler.post { onComplete(false, "Неверный MAC") }
            return
        }

        val queue = ArrayDeque(payloads)
        val finished = AtomicBoolean(false)
        val queueWritesStarted = AtomicBoolean(false)
        var pendingDone: Runnable? = null
        var mtuFallback: Runnable? = null

        fun finish(ok: Boolean, err: String?) {
            if (!finished.compareAndSet(false, true)) return
            pendingDone?.let { mainHandler.removeCallbacks(it) }
            pendingDone = null
            mtuFallback?.let { mainHandler.removeCallbacks(it) }
            mtuFallback = null
            mainHandler.post {
                onComplete(ok, err)
                closeQuietly()
            }
        }

        lateinit var pendingGatt: BluetoothGatt
        lateinit var pendingChar: BluetoothGattCharacteristic
        var queueToRadioWriteType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var queueHeadWriteRetries = 0

        fun writeHead() {
            val payload = queue.firstOrNull() ?: run {
                Log.w(TAG, "очередь ToRadio пуста (неожиданно)")
                finish(false, "Пустая очередь")
                return
            }
            Log.d(TAG, "ToRadio очередь: пакет ${payload.size} B, ожидает ${queue.size}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val r = pendingGatt.writeCharacteristic(
                        pendingChar,
                        payload,
                        queueToRadioWriteType,
                    )
                    if (r != BluetoothGatt.GATT_SUCCESS) {
                        queueHeadWriteRetries++
                        if (queueHeadWriteRetries > 220) {
                            queueHeadWriteRetries = 0
                            finish(false, "writeCharacteristic: $r (исчерпаны повторы)")
                        } else {
                            mainHandler.postDelayed({ if (!finished.get()) writeHead() }, 40L)
                        }
                    } else {
                        queueHeadWriteRetries = 0
                    }
                } else {
                    @Suppress("DEPRECATION")
                    pendingChar.writeType = queueToRadioWriteType
                    @Suppress("DEPRECATION")
                    pendingChar.value = payload
                    @Suppress("DEPRECATION")
                    if (!pendingGatt.writeCharacteristic(pendingChar)) {
                        queueHeadWriteRetries++
                        if (queueHeadWriteRetries > 300) {
                            queueHeadWriteRetries = 0
                            finish(false, "writeCharacteristic false")
                        } else {
                            mainHandler.postDelayed({ if (!finished.get()) writeHead() }, 45L)
                        }
                    } else {
                        queueHeadWriteRetries = 0
                    }
                }
            } catch (e: SecurityException) {
                finish(false, e.message)
            }
        }

        fun startWritingAfterMtu() {
            if (!queueWritesStarted.compareAndSet(false, true)) return
            mtuFallback?.let { mainHandler.removeCallbacks(it) }
            mtuFallback = null
            if (queue.isEmpty()) {
                finish(true, null)
                return
            }
            writeHead()
        }

        val callback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        BleTransportManager.requestHighPerformance(g)
                        try {
                            g.discoverServices()
                        } catch (e: SecurityException) {
                            finish(false, e.message)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (finished.get()) return
                        val pending = pendingDone
                        if (pending != null && queue.isEmpty()) {
                            mainHandler.removeCallbacks(pending)
                            pendingDone = null
                            finish(true, null)
                        } else {
                            Log.w(TAG, "disconnected early queue (status=$status)")
                            finish(false, "Отключено (GATT $status)")
                        }
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(false, "Сервисы: статус $status")
                    return
                }
                val service = g.getService(MESH_SERVICE_UUID.uuid)
                if (service == null) {
                    finish(false, "Сервис mesh не найден")
                    return
                }
                val toRadio = service.getCharacteristic(TORADIO_UUID)
                if (toRadio == null) {
                    finish(false, "Нет ToRadio")
                    return
                }
                pendingGatt = g
                pendingChar = toRadio
                queueToRadioWriteType = toRadioWriteTypeForCharacteristic(toRadio)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val fall = Runnable {
                        mtuFallback = null
                        if (!finished.get()) {
                            Log.w(TAG, "MTU timeout queue — пишем")
                            startWritingAfterMtu()
                        }
                    }
                    mtuFallback = fall
                    mainHandler.postDelayed(fall, 650L)
                    try {
                        g.requestMtu(512)
                    } catch (e: SecurityException) {
                        mtuFallback?.let { mainHandler.removeCallbacks(it) }
                        mtuFallback = null
                        finish(false, e.message)
                        return
                    }
                } else {
                    startWritingAfterMtu()
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                BleTransportManager.recordMtuNegotiated(mtu, status)
                Log.d(TAG, "MTU queue $mtu status=$status")
                if (!finished.get()) {
                    mtuFallback?.let { mainHandler.removeCallbacks(it) }
                    mtuFallback = null
                    mainHandler.postDelayed({
                        if (!finished.get()) startWritingAfterMtu()
                    }, 28L)
                }
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid != TORADIO_UUID) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(false, "Запись ToRadio: $status")
                    return
                }
                queueHeadWriteRetries = 0
                queue.pollFirst()
                if (queue.isEmpty()) {
                    pendingDone?.let { mainHandler.removeCallbacks(it) }
                    val done = Runnable { finish(true, null) }
                    pendingDone = done
                    mainHandler.postDelayed(done, 280L)
                } else {
                    mainHandler.postDelayed({
                        if (!finished.get()) writeHead()
                    }, delayBetweenWritesMs)
                }
            }
        }

        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, callback)
            }
        } catch (e: SecurityException) {
            finish(false, e.message)
        }
    }

    fun writeToradio(
        deviceAddress: String,
        payload: ByteArray,
        /** Вызывается на main сразу после постановки в очередь долгой BLE-сессии чата (до onCharacteristicWrite). */
        onSessionQueued: (() -> Unit)? = null,
        onComplete: (ok: Boolean, error: String?) -> Unit,
    ) {
        // Используем постоянное соединение только если GATT к тому же адресу, что запрошена запись
        if (shouldRouteWritesThroughPersistentGatt(deviceAddress)) {
            mainHandler.post { onSessionQueued?.invoke() }
            NodeGattConnection.sendToRadio(payload) { ok, err -> onComplete(ok, err) }
            return
        }
        writeToradioInternal(
            deviceAddress,
            payload,
            drainFromRadio = false,
            onSessionQueued = onSessionQueued,
        ) { ok, err, _ ->
            onComplete(ok, err)
        }
    }

    /**
     * После записи в ToRadio читает FromRadio (короткий цикл), собирает краткие расшифровки MeshPacket
     * для UI — аналог того, как официальный клиент оставляет соединение и забирает ответы.
     */
    fun writeToradioWithMeshDrain(
        deviceAddress: String,
        payload: ByteArray,
        onComplete: (ok: Boolean, error: String?, meshSummary: String?) -> Unit,
    ) {
        writeToradioInternal(deviceAddress, payload, drainFromRadio = true, onSessionQueued = null, onComplete)
    }

    private fun writeToradioInternal(
        deviceAddress: String,
        payload: ByteArray,
        drainFromRadio: Boolean,
        onSessionQueued: (() -> Unit)? = null,
        onComplete: (ok: Boolean, error: String?, meshSummary: String?) -> Unit,
    ) {
        val norm = MeshNodeSyncMemoryStore.normalizeKey(deviceAddress)
        if (!drainFromRadio && meshSessionAddressNorm == norm) {
            val sessionReady =
                meshChatSessionActive && gatt != null && sessionToRadioChar != null
            if (meshChatSessionBootstrapping || sessionReady) {
                enqueueUserToradioWrite(payload, onComplete, onSessionQueued)
                return
            }
        }
        // Отдельный connectGatt + drain часто не видит ответы: FromRadio уже крутится на [NodeGattConnection].
        if (drainFromRadio && shouldRouteWritesThroughPersistentGatt(deviceAddress)) {
            NodeGattConnection.sendToRadio(payload) { ok, err ->
                mainHandler.post { onComplete(ok, err, null) }
            }
            return
        }
        closeQuietly()
        MeshDeviceTransport.parseTcp(norm)?.let { ep ->
            MeshStreamToRadio.writeToradioTcp(ep, payload, drainFromRadio, onComplete)
            return
        }
        MeshDeviceTransport.parseUsb(norm)?.let { ep ->
            MeshStreamToRadio.writeToradioUsb(appContext, ep, payload, drainFromRadio, onComplete)
            return
        }

        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            mainHandler.post { onComplete(false, "Bluetooth выключен", null) }
            return
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (_: IllegalArgumentException) {
            mainHandler.post { onComplete(false, "Неверный MAC", null) }
            return
        }

        val finished = AtomicBoolean(false)
        /** Отмена при успешном onMtuChanged — иначе запись до поднятия MTU ломает длинные пакеты. */
        var pendingMtuFallback: Runnable? = null
        /** После успешной записи (без drain) — задержка до disconnect. */
        var pendingSuccessFinish: Runnable? = null
        var pendingDrainTimeout: Runnable? = null
        var pendingDrainReadDelay: Runnable? = null
        var drainActive = false
        var pendingFromRadio: BluetoothGattCharacteristic? = null
        val meshSummaries = mutableListOf<String>()
        var drainReadCount = 0
        var drainEmptyStreak = 0
        val writeOnce = AtomicBoolean(false)
        lateinit var pendingGatt: BluetoothGatt
        lateinit var pendingChar: BluetoothGattCharacteristic

        fun finish(ok: Boolean, err: String?, meshSummary: String?) {
            if (!finished.compareAndSet(false, true)) return
            pendingMtuFallback?.let { mainHandler.removeCallbacks(it) }
            pendingMtuFallback = null
            pendingSuccessFinish?.let { mainHandler.removeCallbacks(it) }
            pendingSuccessFinish = null
            pendingDrainTimeout?.let { mainHandler.removeCallbacks(it) }
            pendingDrainTimeout = null
            pendingDrainReadDelay?.let { mainHandler.removeCallbacks(it) }
            pendingDrainReadDelay = null
            drainActive = false
            mainHandler.post {
                onComplete(ok, err, meshSummary)
                closeQuietly()
            }
        }

        fun completeDrain(err: String? = null) {
            if (!drainFromRadio || !drainActive) return
            drainActive = false
            pendingDrainTimeout?.let { mainHandler.removeCallbacks(it) }
            pendingDrainTimeout = null
            pendingDrainReadDelay?.let { mainHandler.removeCallbacks(it) }
            pendingDrainReadDelay = null
            if (finished.get()) return
            val detail = meshSummaries.distinct().take(12).joinToString("\n").ifBlank { null }
            if (err != null) finish(false, err, detail)
            else finish(true, null, detail)
        }

        fun issueFromRadioRead(fr: BluetoothGattCharacteristic) {
            if (!drainActive || finished.get()) return
            if (drainReadCount >= 72) {
                completeDrain()
                return
            }
            try {
                @Suppress("DEPRECATION")
                if (!pendingGatt.readCharacteristic(fr)) {
                    completeDrain("FromRadio read false")
                }
            } catch (e: SecurityException) {
                completeDrain(e.message)
            }
        }

        fun startDrain() {
            val fr = pendingFromRadio ?: run {
                finish(false, "Нет FromRadio", null)
                return
            }
            drainActive = true
            meshSummaries.clear()
            drainReadCount = 0
            drainEmptyStreak = 0
            val timeout = Runnable {
                if (finished.get()) return@Runnable
                Log.d(TAG, "FromRadio drain timeout")
                completeDrain()
            }
            pendingDrainTimeout = timeout
            mainHandler.postDelayed(timeout, 5500L)
            val first = Runnable {
                pendingDrainReadDelay = null
                issueFromRadioRead(fr)
            }
            pendingDrainReadDelay = first
            mainHandler.postDelayed(first, 120L)
        }

        fun handleFromRadioRead(fr: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (!drainFromRadio || !drainActive) return
            if (fr.uuid != FROMRADIO_UUID) return
            pendingDrainReadDelay?.let { mainHandler.removeCallbacks(it) }
            pendingDrainReadDelay = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completeDrain("FromRadio: статус $status")
                return
            }
            if (value.isEmpty()) {
                drainEmptyStreak++
                if (drainEmptyStreak >= 14) {
                    completeDrain()
                    return
                }
                val r = Runnable {
                    pendingDrainReadDelay = null
                    issueFromRadioRead(fr)
                }
                pendingDrainReadDelay = r
                mainHandler.postDelayed(r, 45L)
                return
            }
            drainEmptyStreak = 0
            drainReadCount++
            MeshWireFromRadioMeshPacketParser.summarizeFromRadio(value)?.let { meshSummaries.add(it) }
            val r = Runnable {
                pendingDrainReadDelay = null
                issueFromRadioRead(fr)
            }
            pendingDrainReadDelay = r
            mainHandler.postDelayed(r, 45L)
        }

        fun tryWrite() {
            if (!writeOnce.compareAndSet(false, true)) return
            Log.d(TAG, "writing ToRadio ${payload.size} B (drain=$drainFromRadio)")
            try {
                val wt = toRadioWriteTypeForCharacteristic(pendingChar)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val r = pendingGatt.writeCharacteristic(
                        pendingChar,
                        payload,
                        wt,
                    )
                    if (r != BluetoothGatt.GATT_SUCCESS) {
                        writeOnce.set(false)
                        finish(false, "writeCharacteristic: $r", null)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    pendingChar.writeType = wt
                    @Suppress("DEPRECATION")
                    pendingChar.value = payload
                    @Suppress("DEPRECATION")
                    if (!pendingGatt.writeCharacteristic(pendingChar)) {
                        writeOnce.set(false)
                        finish(false, "writeCharacteristic false", null)
                    }
                }
            } catch (e: SecurityException) {
                writeOnce.set(false)
                finish(false, e.message, null)
            }
        }

        val callback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "connected → discoverServices")
                        BleTransportManager.requestHighPerformance(g)
                        try {
                            g.discoverServices()
                        } catch (e: SecurityException) {
                            finish(false, e.message, null)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (finished.get()) return
                        val pending = pendingSuccessFinish
                        if (pending != null) {
                            mainHandler.removeCallbacks(pending)
                            pendingSuccessFinish = null
                            Log.d(TAG, "disconnect после подтверждения записи → успех")
                            finish(true, null, null)
                        } else if (drainActive) {
                            Log.d(TAG, "disconnect во время drain FromRadio → завершение с накопленным")
                            completeDrain()
                        } else {
                            Log.w(TAG, "disconnected early (status=$status)")
                            finish(false, "Отключено (GATT $status)", null)
                        }
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(false, "Сервисы: статус $status", null)
                    return
                }
                val service = g.getService(MESH_SERVICE_UUID.uuid)
                if (service == null) {
                    finish(false, "Сервис mesh не найден", null)
                    return
                }
                val toRadio = service.getCharacteristic(TORADIO_UUID)
                if (toRadio == null) {
                    finish(false, "Нет характеристики ToRadio", null)
                    return
                }
                if (drainFromRadio) {
                    pendingFromRadio = service.getCharacteristic(FROMRADIO_UUID)
                    if (pendingFromRadio == null) {
                        finish(false, "Нет характеристики FromRadio", null)
                        return
                    }
                }
                pendingGatt = g
                pendingChar = toRadio

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val mtuFb = Runnable {
                        pendingMtuFallback = null
                        if (!finished.get() && writeOnce.get().not()) {
                            Log.w(TAG, "MTU timeout — попытка записи (MTU мог не подняться)")
                            tryWrite()
                        }
                    }
                    pendingMtuFallback = mtuFb
                    mainHandler.postDelayed(mtuFb, 600L)
                    Log.d(TAG, "requestMtu(512)")
                    try {
                        g.requestMtu(512)
                    } catch (e: SecurityException) {
                        pendingMtuFallback?.let { mainHandler.removeCallbacks(it) }
                        pendingMtuFallback = null
                        finish(false, e.message, null)
                        return
                    }
                } else {
                    tryWrite()
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                BleTransportManager.recordMtuNegotiated(mtu, status)
                pendingMtuFallback?.let { mainHandler.removeCallbacks(it) }
                pendingMtuFallback = null
                Log.d(TAG, "MTU=$mtu status=$status")
                if (!finished.get()) {
                    mainHandler.postDelayed({
                        if (!finished.get()) tryWrite()
                    }, 55L)
                }
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid != TORADIO_UUID) return
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingSuccessFinish?.let { mainHandler.removeCallbacks(it) }
                    pendingSuccessFinish = null
                    if (drainFromRadio) {
                        Log.d(TAG, "ToRadio записан → drain FromRadio")
                        startDrain()
                    } else {
                        Log.d(TAG, "ToRadio записан, задержка перед disconnect")
                        val done = Runnable { finish(true, null, null) }
                        pendingSuccessFinish = done
                        mainHandler.postDelayed(done, 150L)
                    }
                } else {
                    finish(false, "Запись ToRadio: $status", null)
                }
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                handleFromRadioRead(characteristic, value, status)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                val v = characteristic.value ?: byteArrayOf()
                handleFromRadioRead(characteristic, v, status)
            }
        }

        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, callback)
            }
            Log.d(TAG, "connectGatt $deviceAddress")
        } catch (e: SecurityException) {
            finish(false, e.message, null)
        }
    }

    /**
     * Только чтение FromRadio (без записи ToRadio) — опрос входящих пакетов для чата.
     *
     * @param persistent если true — без лимита ~96 чтений и без таймаута 4.5 с (долгий фоновый опрос).
     * @param chatKeepAlive если true — дольше держим одну GATT-сессию и чаще читаем FromRadio (экран чата),
     *   чтобы входящие не ждали искусственной паузы и переподключения после каждого короткого цикла.
     * @param chatStopSignal если задан — один долгий цикл до [AtomicBoolean.get]==true (отмена корутины);
     *   совпадает с официальным клиентом: одно подключение на приём + очередь ToRadio без reconnect.
     */
    fun drainFromRadioOnly(
        deviceAddress: String,
        onFrame: (ByteArray) -> Unit,
        onComplete: (ok: Boolean, error: String?) -> Unit,
        persistent: Boolean = false,
        chatKeepAlive: Boolean = false,
        chatStopSignal: AtomicBoolean? = null,
    ) {
        closeQuietly()
        val norm = MeshNodeSyncMemoryStore.normalizeKey(deviceAddress)
        MeshDeviceTransport.parseTcp(norm)?.let { ep ->
            MeshStreamToRadio.drainFromRadioOnlyTcp(ep, onFrame, onComplete)
            return
        }
        MeshDeviceTransport.parseUsb(norm)?.let { ep ->
            MeshStreamToRadio.drainFromRadioOnlyUsb(appContext, ep, onFrame, onComplete)
            return
        }

        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            mainHandler.post { onComplete(false, "Bluetooth выключен") }
            return
        }
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (_: IllegalArgumentException) {
            mainHandler.post { onComplete(false, "Неверный MAC") }
            return
        }

        if (chatStopSignal != null) {
            meshSessionAddressNorm = norm
            meshChatSessionBootstrapping = true
        }

        val finished = AtomicBoolean(false)
        val drainKickoff = AtomicBoolean(false)
        val awaitingWantConfigKick = AtomicBoolean(false)
        val meshDrainHandshakeOnce = AtomicBoolean(false)
        var pendingMtuFallback: Runnable? = null
        var pendingHandshakeWatchdog: Runnable? = null
        var pendingDrainTimeout: Runnable? = null
        var pendingDrainReadDelay: Runnable? = null
        var drainActive = false
        var pendingFromRadio: BluetoothGattCharacteristic? = null
        var pendingToRadio: BluetoothGattCharacteristic? = null
        var drainReadCount = 0
        var drainEmptyStreak = 0
        lateinit var pendingGatt: BluetoothGatt
        val (maxDrainReads, drainIdleTimeoutMs, maxEmptyStreakBeforeComplete) = when {
            chatStopSignal != null -> Triple(Int.MAX_VALUE, 0L, Int.MAX_VALUE)
            persistent -> Triple(Int.MAX_VALUE, 0L, 50_000)
            chatKeepAlive -> Triple(512, 12_000L, 48)
            else -> Triple(96, 4500L, 18)
        }
        val firstReadDelayMs = when {
            chatStopSignal != null || chatKeepAlive -> BleTransportManager.firstReadDelayMsForChatDrain()
            else -> 90L
        }
        val interReadDelayMs = when {
            chatStopSignal != null || chatKeepAlive -> BleTransportManager.interReadDelayMsForChatDrain()
            else -> 42L
        }

        fun finish(ok: Boolean, err: String?) {
            if (!finished.compareAndSet(false, true)) return
            pendingMtuFallback?.let { mainHandler.removeCallbacks(it) }
            pendingMtuFallback = null
            pendingHandshakeWatchdog?.let { mainHandler.removeCallbacks(it) }
            pendingHandshakeWatchdog = null
            pendingDrainTimeout?.let { mainHandler.removeCallbacks(it) }
            pendingDrainTimeout = null
            pendingDrainReadDelay?.let { mainHandler.removeCallbacks(it) }
            pendingDrainReadDelay = null
            drainActive = false
            awaitingWantConfigKick.set(false)
            mainHandler.post {
                onComplete(ok, err)
                closeQuietly()
            }
        }

        fun completeDrain(err: String? = null) {
            if (!drainActive) return
            drainActive = false
            pendingDrainTimeout?.let { mainHandler.removeCallbacks(it) }
            pendingDrainTimeout = null
            pendingDrainReadDelay?.let { mainHandler.removeCallbacks(it) }
            pendingDrainReadDelay = null
            if (finished.get()) return
            if (err != null) finish(false, err)
            else finish(true, null)
        }

        fun issueFromRadioRead(fr: BluetoothGattCharacteristic) {
            if (!drainActive || finished.get()) return
            if (chatStopSignal?.get() == true) {
                completeDrain()
                return
            }
            if (drainReadCount >= maxDrainReads) {
                completeDrain()
                return
            }
            try {
                @Suppress("DEPRECATION")
                if (!pendingGatt.readCharacteristic(fr)) {
                    completeDrain("FromRadio read false")
                }
            } catch (e: SecurityException) {
                completeDrain(e.message)
            }
        }

        fun startDrain() {
            val fr = pendingFromRadio ?: run {
                finish(false, "Нет FromRadio")
                return
            }
            pendingHandshakeWatchdog?.let { mainHandler.removeCallbacks(it) }
            pendingHandshakeWatchdog = null
            drainActive = true
            drainReadCount = 0
            drainEmptyStreak = 0
            if (chatStopSignal != null) {
                meshDrainReadyForUserToradio = true
                mainHandler.post { pumpUserToradioWrites() }
            }
            if (drainIdleTimeoutMs > 0L) {
                val timeout = Runnable {
                    if (finished.get()) return@Runnable
                    completeDrain()
                }
                pendingDrainTimeout = timeout
                mainHandler.postDelayed(timeout, drainIdleTimeoutMs)
            }
            val first = Runnable {
                pendingDrainReadDelay = null
                if (chatStopSignal?.get() == true) {
                    completeDrain()
                    return@Runnable
                }
                issueFromRadioRead(fr)
            }
            pendingDrainReadDelay = first
            mainHandler.postDelayed(first, firstReadDelayMs)
        }

        fun kickDrain() {
            if (!drainKickoff.compareAndSet(false, true)) return
            startDrain()
        }

        fun sendWantConfigThenKick(g: BluetoothGatt) {
            if (finished.get()) return
            val to = pendingToRadio
            if (to == null) {
                Log.w(TAG, "drainFromRadio: нет ToRadio — опрос без want_config")
                kickDrain()
                return
            }
            awaitingWantConfigKick.set(true)
            val packet = MeshWireWantConfigHandshake.encodeToRadioWantConfigId(
                MeshWireWantConfigHandshake.CONFIG_NONCE,
            )
            try {
                val wt = toRadioWriteTypeForCharacteristic(to)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val r = g.writeCharacteristic(
                        to,
                        packet,
                        wt,
                    )
                    if (r != BluetoothGatt.GATT_SUCCESS) {
                        awaitingWantConfigKick.set(false)
                        Log.w(TAG, "drain want_config writeCharacteristic $r")
                        kickDrain()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    to.writeType = wt
                    @Suppress("DEPRECATION")
                    to.value = packet
                    @Suppress("DEPRECATION")
                    if (!g.writeCharacteristic(to)) {
                        awaitingWantConfigKick.set(false)
                        kickDrain()
                    }
                }
            } catch (e: SecurityException) {
                awaitingWantConfigKick.set(false)
                kickDrain()
            }
        }

        fun beginMeshDrainHandshake(g: BluetoothGatt) {
            if (finished.get()) return
            if (!meshDrainHandshakeOnce.compareAndSet(false, true)) return
            val wd = Runnable {
                pendingHandshakeWatchdog = null
                if (finished.get()) return@Runnable
                if (!drainKickoff.get()) {
                    Log.w(TAG, "drainFromRadio: таймаут handshake — запуск опроса FromRadio")
                    kickDrain()
                }
            }
            pendingHandshakeWatchdog = wd
            mainHandler.postDelayed(wd, 3000L)
            val service = g.getService(MESH_SERVICE_UUID.uuid)
            if (service == null) {
                Log.w(TAG, "drainFromRadio: сервис не найден при handshake")
                sendWantConfigThenKick(g)
                return
            }
            pendingToRadio = service.getCharacteristic(TORADIO_UUID)
            val fromNum = service.getCharacteristic(FROMNUM_UUID)
            if (fromNum == null) {
                Log.w(TAG, "drainFromRadio: нет FROMNUM — want_config и опрос")
                sendWantConfigThenKick(g)
                return
            }
            try {
                if (!g.setCharacteristicNotification(fromNum, true)) {
                    Log.w(TAG, "drainFromRadio: setCharacteristicNotification(FROMNUM) false")
                    sendWantConfigThenKick(g)
                    return
                }
            } catch (e: SecurityException) {
                sendWantConfigThenKick(g)
                return
            }
            val desc = fromNum.getDescriptor(DRAIN_CCCD_UUID)
            if (desc == null) {
                Log.w(TAG, "drainFromRadio: нет CCCD у FROMNUM")
                sendWantConfigThenKick(g)
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val r = g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    if (r != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "drainFromRadio writeDescriptor CCCD $r")
                        sendWantConfigThenKick(g)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    if (!g.writeDescriptor(desc)) {
                        sendWantConfigThenKick(g)
                    }
                }
            } catch (e: SecurityException) {
                sendWantConfigThenKick(g)
            }
        }

        fun handleFromRadioRead(fr: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (!drainActive) return
            if (fr.uuid != FROMRADIO_UUID) return
            if (chatStopSignal?.get() == true) {
                completeDrain()
                return
            }
            pendingDrainReadDelay?.let { mainHandler.removeCallbacks(it) }
            pendingDrainReadDelay = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completeDrain("FromRadio: статус $status")
                return
            }
            if (value.isEmpty()) {
                if (chatStopSignal != null) {
                    drainEmptyStreak++
                    if (drainEmptyStreak >= 48) {
                        drainEmptyStreak = 0
                    }
                    val r = Runnable {
                        pendingDrainReadDelay = null
                        if (chatStopSignal.get()) {
                            completeDrain()
                            return@Runnable
                        }
                        issueFromRadioRead(fr)
                    }
                    pendingDrainReadDelay = r
                    mainHandler.postDelayed(r, interReadDelayMs)
                    return
                }
                drainEmptyStreak++
                if (drainEmptyStreak >= maxEmptyStreakBeforeComplete) {
                    completeDrain()
                    return
                }
                val r = Runnable {
                    pendingDrainReadDelay = null
                    issueFromRadioRead(fr)
                }
                pendingDrainReadDelay = r
                mainHandler.postDelayed(r, interReadDelayMs)
                return
            }
            drainEmptyStreak = 0
            drainReadCount++
            mainHandler.post { onFrame(value) }
            val r = Runnable {
                pendingDrainReadDelay = null
                if (chatStopSignal?.get() == true) {
                    completeDrain()
                    return@Runnable
                }
                issueFromRadioRead(fr)
            }
            pendingDrainReadDelay = r
            mainHandler.postDelayed(r, interReadDelayMs)
        }

        val callback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        BleTransportManager.requestHighPerformance(g)
                        try {
                            g.discoverServices()
                        } catch (e: SecurityException) {
                            finish(false, e.message)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (finished.get()) return
                        if (drainActive) {
                            completeDrain()
                        } else {
                            finish(false, "Отключено (GATT $status)")
                        }
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(false, "Сервисы: статус $status")
                    return
                }
                val service = g.getService(MESH_SERVICE_UUID.uuid)
                if (service == null) {
                    finish(false, "Сервис mesh не найден")
                    return
                }
                pendingFromRadio = service.getCharacteristic(FROMRADIO_UUID)
                if (pendingFromRadio == null) {
                    finish(false, "Нет характеристики FromRadio")
                    return
                }
                pendingToRadio = service.getCharacteristic(TORADIO_UUID)
                pendingGatt = g
                if (chatStopSignal != null) {
                    sessionToRadioChar = pendingToRadio
                    meshSessionAddressNorm = norm
                    meshChatSessionActive = true
                    meshChatSessionBootstrapping = false
                    meshDrainReadyForUserToradio = false
                    mainHandler.post { pumpUserToradioWrites() }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val mtuFb = Runnable {
                        pendingMtuFallback = null
                        if (!finished.get()) beginMeshDrainHandshake(g)
                    }
                    pendingMtuFallback = mtuFb
                    mainHandler.postDelayed(mtuFb, 600L)
                    try {
                        Log.d(TAG, "drainFromRadioOnly requestMtu(512)")
                        g.requestMtu(512)
                    } catch (e: SecurityException) {
                        pendingMtuFallback?.let { mainHandler.removeCallbacks(it) }
                        pendingMtuFallback = null
                        finish(false, e.message)
                        return
                    }
                } else {
                    beginMeshDrainHandshake(g)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                BleTransportManager.recordMtuNegotiated(mtu, status)
                pendingMtuFallback?.let { mainHandler.removeCallbacks(it) }
                pendingMtuFallback = null
                Log.d(TAG, "drainFromRadioOnly MTU=$mtu status=$status")
                if (!finished.get()) {
                    mainHandler.postDelayed(
                        {
                            if (!finished.get()) beginMeshDrainHandshake(g)
                        },
                        55L,
                    )
                }
            }

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (descriptor.uuid != DRAIN_CCCD_UUID ||
                    descriptor.characteristic?.uuid != FROMNUM_UUID
                ) {
                    return
                }
                if (finished.get()) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "drainFromRadio FROMNUM CCCD status=$status — всё равно want_config")
                }
                mainHandler.postDelayed(
                    {
                        if (!finished.get()) sendWantConfigThenKick(g)
                    },
                    DRAIN_CCCD_SETTLE_MS,
                )
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (characteristic.uuid != TORADIO_UUID) return
                if (awaitingWantConfigKick.compareAndSet(true, false)) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "drain want_config ToRadio status=$status")
                    } else {
                        Log.d(TAG, "drain want_config OK → опрос FromRadio")
                    }
                    mainHandler.postDelayed(
                        {
                            if (!finished.get()) kickDrain()
                        },
                        85L,
                    )
                    return
                }
                val userCb = pendingUserToradioComplete
                if (userCb != null) {
                    pendingUserToradioComplete = null
                    userToradioWriteInFlight = false
                    mainHandler.post {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            userToradioWriteLegacyRetryCount = 0
                            userToradioWriteApiFailRetries = 0
                            userCb(true, null)
                        } else {
                            userCb(false, "Запись ToRadio: $status")
                        }
                        pumpUserToradioWrites()
                    }
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (characteristic.uuid != FROMNUM_UUID) return
                if (finished.get()) return
                val fr = pendingFromRadio ?: return
                if (!drainActive) {
                    kickDrain()
                }
                pendingDrainReadDelay?.let { mainHandler.removeCallbacks(it) }
                pendingDrainReadDelay = null
                if (drainActive) {
                    issueFromRadioRead(fr)
                }
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                handleFromRadioRead(characteristic, value, status)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                val v = characteristic.value ?: byteArrayOf()
                handleFromRadioRead(characteristic, v, status)
            }
        }

        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, callback)
            }
            Log.d(TAG, "drainFromRadioOnly connect $deviceAddress")
            if (chatStopSignal != null) {
                mainHandler.post { pumpUserToradioWrites() }
            }
        } catch (e: SecurityException) {
            finish(false, e.message)
        }
    }

    /** Принудительно закрыть GATT (остановка фонового опроса). */
    fun disconnectGatt() {
        closeQuietly()
    }

    private fun closeQuietly() {
        clearMeshBleSessionState()
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
        }
        try {
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
    }
}
