package com.example.aura.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.aura.mesh.nodedb.MeshNodeDbRepository
import com.example.aura.mesh.transport.BleTransportManager
import com.example.aura.meshwire.MeshWireFromRadioMyNodeNumParser
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireWantConfigHandshake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "NodeGatt"

/** API 33+: [BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY] — нельзя писать, пока не завершена другая GATT-операция. */
private fun isGattWriteRequestBusyReturn(r: Int): Boolean =
    r == 201 ||
        (Build.VERSION.SDK_INT >= 33 && r == BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY)

enum class NodeConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,  // want_config sent, draining initial flood
    READY,
    RECONNECTING,
}

/** Детальный этап синхронизации — отображается в диалоге подключения. */
enum class NodeSyncStep(val label: String) {
    IDLE(""),
    CONNECTING("Подключение..."),
    DISCOVERING("Поиск сервисов..."),
    MTU("Согласование MTU..."),
    SUBSCRIBING("Подписка на уведомления..."),
    WANT_CONFIG("Отправка конфигурации..."),
    SYNCING("Синхронизация..."),
    RECEIVING("Получение данных..."),
    READY("Подключено"),
    RECONNECTING("Переподключение..."),
    DISCONNECTED(""),
}

/**
 * Единственное постоянное GATT-соединение с нодой mesh (ToRadio / FromRadio / FromNum).
 *
 * **Phase 1 — Physical (CONNECTING → MTU):** connect, discover, `requestMtu(512)`, подписка FromNum
 * только после **успешного** [onMtuChanged] (без обходного «want_config без MTU»).
 *
 * **Phase 2 — Initial dump (WANT_CONFIG → SYNCING → READY):** [ToRadio.want_config_id] с nonce
 * [MeshWireWantConfigHandshake.CONFIG_NONCE]; по FromNum агрессивно читаем FromRadio до пустого кадра;
 * парсинг protobuf вне main; при [FromRadio.config_complete_id] == nonce → [initialWantConfigAcknowledged]
 * и состояние READY.
 *
 * **Phase 3:** [MeshNodePassiveFromRadioSink] пишет Config/ModuleConfig/Channel в [MeshNodeSyncMemoryStore]
 * по мере FromRadio (ещё до ack). Резерв — [MeshNodeFullSettingsPrefetcher]; GET — [NodeAdminApi] при READY.
 */
object NodeGattConnection {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Тяжёлый разбор FromRadio — вне main (несколько потоков под нагрузкой парсинга). */
    private val fromRadioParseExecutor: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "Aura-FromRadio-parse").apply { isDaemon = true }
    }

    @Volatile
    private var fromRadioParseGeneration: Int = 0

    // ── Состояние ─────────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(NodeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<NodeConnectionState> = _state.asStateFlow()
    val isReady: Boolean get() = _state.value == NodeConnectionState.READY
    val isAlive: Boolean get() = _state.value == NodeConnectionState.CONNECTING ||
            _state.value == NodeConnectionState.HANDSHAKING ||
            _state.value == NodeConnectionState.READY

    /** Детальный текущий этап синхронизации для UI. */
    private val _syncStep = MutableStateFlow(NodeSyncStep.IDLE)
    val syncStep: StateFlow<NodeSyncStep> = _syncStep.asStateFlow()

    /** Node ID ноды — становится известен из потока want_config сразу после подключения. */
    private val _myNodeNum = MutableStateFlow<UInt?>(null)
    val myNodeNum: StateFlow<UInt?> = _myNodeNum.asStateFlow()

    /**
     * Как [meshtastic/web MeshDevice + decodePacket](https://github.com/meshtastic/web/blob/main/packages/core/src/meshDevice.ts):
     * после [ToRadio.want_config_id] приходит [FromRadio.config_complete_id] с тем же nonce —
     * только тогда клиент считает дамп конфигурации завершённым.
     */
    private val _initialWantConfigAcknowledged = MutableStateFlow(false)
    val initialWantConfigAcknowledged: StateFlow<Boolean> = _initialWantConfigAcknowledged.asStateFlow()

    // ── GATT ──────────────────────────────────────────────────────────────────
    private var gatt: BluetoothGatt? = null
    private var toRadioChar: BluetoothGattCharacteristic? = null
    /** Как [Meshtastic-Android] Nordic BLE: [PROPERTY_WRITE_NO_RESPONSE] → [WRITE_TYPE_NO_RESPONSE], иначе default. */
    private var toRadioWriteType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    private var fromRadioChar: BluetoothGattCharacteristic? = null

    // ── Устройство ────────────────────────────────────────────────────────────
    private var appContext: Context? = null

    @Volatile
    var targetDevice: MeshDevice? = null
        private set

    // ── Очередь записи ────────────────────────────────────────────────────────
    private data class WriteItem(
        val bytes: ByteArray,
        val delayAfterMs: Long = 0L,
        val onComplete: ((ok: Boolean, error: String?) -> Unit)?,
    )

    private val writeQueue = ArrayDeque<WriteItem>()
    private var writeInFlight = false
    /**
     * API 33+: повторы при [BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY] (201) и прочих «занятый/не сейчас» кодах
     * из-за read FromRadio / соседних операций. Разные OEM возвращают не только 201.
     */
    private var pumpWriteBusyRetries = 0
    private var wantConfigGattBusyRetries = 0

    // ── Слушатели входящих кадров ─────────────────────────────────────────────
    private val frameListeners = CopyOnWriteArrayList<(ByteArray) -> Unit>()

    // ── Переподключение ───────────────────────────────────────────────────────
    private var reconnectDelayMs = 2_000L
    private var reconnectRunnable: Runnable? = null

    // ── Рукопожатие want_config ───────────────────────────────────────────────
    private var wantConfigSent = false
    private var pollingActive = false
    private var pollRunnable: Runnable? = null
    private var postReadyDrainRunnable: Runnable? = null
    private var postReadyDrainRemaining = 0
    private var emptyReadStreak = 0
    private const val READY_EMPTY_THRESHOLD = 3

    /** После READY нода может ещё слать NodeDB; короткий опрос FromRadio, пока не полагаемся только на FromNum. */
    private const val POST_READY_DRAIN_INTERVAL_MS = 40L
    private const val POST_READY_DRAIN_MAX_READS = 55
    /** После READY — пауза перед `set_time_only`, чтобы стек GATT/ToRadio был готов. */
    private const val POST_READY_SET_TIME_DELAY_MS = 400L

    /**
     * После [READY_EMPTY_THRESHOLD] пустых кадров подряд не переходим в READY, пока не получен
     * [config_complete_id] в потоке want_config — иначе можно прекратить опрос до кадра с ack.
     * После [MAX_HANDSHAKE_POLLS_WITHOUT_CONFIG] дополнительных циклов «3 пустых → ждём» — fallback как для старых прошивок.
     */
    private var handshakePollsWaitingForConfigComplete = 0
    private const val MAX_HANDSHAKE_POLLS_WITHOUT_CONFIG = 240

    /** Момент старта опроса FromRadio при want_config — для таймаута READY, если нет 3 пустых подряд. */
    private var handshakeWallStartedMs: Long = 0L

    private const val HANDSHAKE_ABSOLUTE_MAX_MS = 45_000L

    /** Повторы sendWantConfig при гонке с MTU / пока toRadio не готов. */
    private var wantConfigMtuRetries = 0
    private var wantConfigToradioRetries = 0
    private const val WANT_CONFIG_MTU_RETRY_MAX = 50
    private const val WANT_CONFIG_TORADIO_RETRY_MAX = 40

    /** После уведомления FromNum: вычитываем FromRadio подряд, пока нода не отдаст пустой кадр. */
    @Volatile
    private var fromRadioDrainUntilEmpty = false

    /**
     * want_config_id отправляем только после завершения фазы MTU ([onMtuChanged] уже вызван):
     * при [BluetoothGatt.GATT_SUCCESS] — сразу; при ошибке MTU — после короткой задержки (стек без реального MTU);
     * если колбэк не пришёл — через [MTU_WATCHDOG_MS] (watchdog).
     */
    @Volatile
    private var wantConfigAllowed = false

    private var mtuWatchdogRunnable: Runnable? = null

    @Volatile
    private var subscribeHandshakeStarted = false

    private const val MTU_WATCHDOG_MS = 8_000L

    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Инициализирует соединение с [device].
     * Если к тому же устройству уже подключено/подключается — ничего не делает.
     */
    fun connect(device: MeshDevice, context: Context) {
        appContext = context.applicationContext
        val sameDevice = targetDevice?.bluetoothDevice?.address == device.bluetoothDevice.address
        if (sameDevice && isAlive) {
            Log.d(TAG, "connect: уже подключено/подключается к ${device.address}")
            return
        }
        targetDevice = device
        reconnectDelayMs = 2_000L
        mainHandler.post { doConnect() }
    }

    /** Явно разрывает соединение и останавливает переподключение. */
    fun disconnect() {
        mainHandler.post {
            targetDevice = null
            cancelReconnect()
            closeGatt()
            setState(NodeConnectionState.DISCONNECTED)
        }
    }

    /**
     * Ставит [bytes] в очередь ToRadio.
     * [delayAfterMs] — пауза между текущей записью и следующей из очереди.
     * [onComplete] вызывается на main-потоке после подтверждения GATT-записи.
     */
    fun sendToRadio(
        bytes: ByteArray,
        delayAfterMs: Long = 0L,
        onComplete: ((ok: Boolean, error: String?) -> Unit)? = null,
    ) {
        mainHandler.post {
            writeQueue.addLast(WriteItem(bytes, delayAfterMs, onComplete))
            pumpQueue()
        }
    }

    /** Регистрирует слушатель входящих кадров FromRadio (вызывается на main-потоке). */
    fun addFrameListener(listener: (ByteArray) -> Unit) {
        frameListeners.add(listener)
    }

    /** Снимает слушатель. */
    fun removeFrameListener(listener: (ByteArray) -> Unit) {
        frameListeners.remove(listener)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Внутренняя логика
    // ─────────────────────────────────────────────────────────────────────────

    private fun setState(newState: NodeConnectionState) {
        if (_state.value == newState) return
        if (newState != NodeConnectionState.READY) {
            postReadySetTimePosted = false
        }
        Log.d(TAG, "state ${_state.value} → $newState")
        _state.value = newState
        _syncStep.value = when (newState) {
            NodeConnectionState.CONNECTING   -> NodeSyncStep.CONNECTING
            NodeConnectionState.HANDSHAKING  -> NodeSyncStep.DISCOVERING
            NodeConnectionState.READY        -> NodeSyncStep.READY
            NodeConnectionState.RECONNECTING -> NodeSyncStep.RECONNECTING
            NodeConnectionState.DISCONNECTED -> NodeSyncStep.DISCONNECTED
        }
    }

    private fun setSyncStep(step: NodeSyncStep) {
        _syncStep.value = step
    }

    private fun cancelMtuWatchdog() {
        mtuWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        mtuWatchdogRunnable = null
    }

    /**
     * FromRadio: парсинг protobuf и снимок слушателей — в [fromRadioParseExecutor];
     * обновление StateFlow, очереди и следующий GATT read — на main.
     */
    private fun dispatchGattFromRadioRead(g: BluetoothGatt, bytes: ByteArray, status: Int) {
        if (gatt !== g) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            mainHandler.post { Log.w(TAG, "FROMRADIO read status=$status") }
            return
        }
        val gen = fromRadioParseGeneration
        val copy = bytes.copyOf()
        val gRef = g
        fromRadioParseExecutor.execute {
            if (gen != fromRadioParseGeneration || gatt !== gRef) return@execute
            val discoveredMy = MeshWireFromRadioMyNodeNumParser.parseMyNodeNum(copy)
                ?.takeIf { it != 0L }?.toUInt()
            val nonce = MeshWireWantConfigHandshake.parseConfigCompleteNonceOrNull(copy)
            val configAck = nonce == MeshWireWantConfigHandshake.CONFIG_NONCE
            val wasNonEmpty = copy.isNotEmpty()
            if (wasNonEmpty) {
                val mac = try {
                    gRef.device.address?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: SecurityException) {
                    null
                }
                if (mac != null) {
                    MeshNodePassiveFromRadioSink.consumeFrame(mac, copy)
                    // NodeDB / карточки узлов: MeshPacket (Telemetry, Position, …) разбираются в [MeshNodeDbRepository] / [MeshWireNodeListAccumulator].
                    MeshNodeDbRepository.ingestFromRadioFrame(mac, copy)
                }
            }
            val snapshot = frameListeners.toTypedArray()
            mainHandler.post {
                if (gen != fromRadioParseGeneration || gatt !== gRef) return@post
                applyFromRadioPostParseMain(gRef, discoveredMy, configAck, wasNonEmpty, snapshot, copy)
            }
        }
    }

    private fun readFromRadioCharacteristicNow(g: BluetoothGatt) {
        if (gatt !== g) return
        val fr = fromRadioChar ?: return
        try {
            @Suppress("DEPRECATION")
            g.readCharacteristic(fr)
        } catch (_: SecurityException) {
        }
    }

    private fun scheduleNextPoll(g: BluetoothGatt) {
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            pollRunnable = null
            if (gatt !== g) return@Runnable
            val fr = fromRadioChar ?: return@Runnable
            try {
                @Suppress("DEPRECATION")
                g.readCharacteristic(fr)
            } catch (_: SecurityException) {
            }
        }
        pollRunnable = r
        mainHandler.postDelayed(r, 25L)
    }

    private fun startPolling(g: BluetoothGatt) {
        pollingActive = true
        emptyReadStreak = 0
        handshakePollsWaitingForConfigComplete = 0
        handshakeWallStartedMs = SystemClock.uptimeMillis()
        scheduleNextPoll(g)
    }

    /**
     * @param allowReadyWithoutConfigAck только аварийный путь (абсолютный таймаут рукопожатия),
     * иначе READY только при уже выставленном [initialWantConfigAcknowledged] (nonce в Phase 2).
     */
    private fun finalizeHandshakeReady(reason: String, allowReadyWithoutConfigAck: Boolean = false) {
        if (_state.value == NodeConnectionState.READY) return
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        pollingActive = false
        handshakePollsWaitingForConfigComplete = 0
        handshakeWallStartedMs = 0L
        if (!_initialWantConfigAcknowledged.value) {
            if (!allowReadyWithoutConfigAck) {
                Log.w(TAG, "finalizeHandshakeReady: нет config_complete_id — пропуск ($reason)")
                return
            }
            Log.w(TAG, "READY без config_complete_id (аварийный путь): $reason")
            _initialWantConfigAcknowledged.value = true
        }
        Log.d(TAG, "want_config → READY ($reason)")
        setState(NodeConnectionState.READY)
        pumpQueue()
        schedulePostReadyFromRadioDrain()
        schedulePostReadySetTimeOnce()
    }

    private var postReadySetTimePosted: Boolean = false

    private fun schedulePostReadySetTimeOnce() {
        if (postReadySetTimePosted) return
        postReadySetTimePosted = true
        mainHandler.postDelayed({
            if (_state.value != NodeConnectionState.READY) return@postDelayed
            val unix = System.currentTimeMillis() / 1000L
            val pkt = MeshWireLoRaToRadioEncoder.encodeSetTimeOnlyToRadio(unix)
            sendToRadio(pkt) { ok, err ->
                if (ok) {
                    Log.d(TAG, "set_time_only sent ($unix)")
                    MeshNodeDbRepository.onNodeTimeSyncSent()
                } else {
                    Log.w(TAG, "set_time_only send failed: $err")
                }
            }
        }, POST_READY_SET_TIME_DELAY_MS)
    }

    private fun schedulePostReadyFromRadioDrain() {
        postReadyDrainRunnable?.let { mainHandler.removeCallbacks(it) }
        val g = gatt ?: return
        postReadyDrainRemaining = POST_READY_DRAIN_MAX_READS
        val r = object : Runnable {
            override fun run() {
                postReadyDrainRunnable = null
                val gg = gatt
                if (gg == null || _state.value != NodeConnectionState.READY || postReadyDrainRemaining <= 0) {
                    return
                }
                postReadyDrainRemaining--
                readFromRadioCharacteristicNow(gg)
                postReadyDrainRunnable = this
                mainHandler.postDelayed(this, POST_READY_DRAIN_INTERVAL_MS)
            }
        }
        postReadyDrainRunnable = r
        mainHandler.postDelayed(r, POST_READY_DRAIN_INTERVAL_MS)
    }

    private fun applyFromRadioPostParseMain(
        g: BluetoothGatt,
        discoveredMy: UInt?,
        configAck: Boolean,
        wasNonEmpty: Boolean,
        snapshot: Array<(ByteArray) -> Unit>,
        copy: ByteArray,
    ) {
        if (gatt !== g) return
        if (wasNonEmpty) {
            emptyReadStreak = 0
            handshakePollsWaitingForConfigComplete = 0
            if (discoveredMy != null && _myNodeNum.value == null) {
                _myNodeNum.value = discoveredMy
                Log.d(TAG, "my_node_num = 0x${discoveredMy.toString(16)}")
            }
            if (configAck) {
                _initialWantConfigAcknowledged.value = true
                Log.d(TAG, "config_complete_id=${MeshWireWantConfigHandshake.CONFIG_NONCE} (want_config dump OK)")
            }
            if (_state.value != NodeConnectionState.READY) {
                setSyncStep(NodeSyncStep.RECEIVING)
            }
        }
        for (listener in snapshot) {
            try {
                listener(copy)
            } catch (_: Exception) {
            }
        }
        // Phase 2: как только пришёл config_complete с нашим nonce — сразу READY (не ждём «3× пустых»).
        if (configAck && _state.value == NodeConnectionState.HANDSHAKING) {
            finalizeHandshakeReady("config_complete_id==nonce", allowReadyWithoutConfigAck = false)
        }
        if (wasNonEmpty) {
            readFromRadioCharacteristicNow(g)
        } else {
            handleFromRadioEmptyRead(g)
        }
    }

    private fun handleFromRadioEmptyRead(g: BluetoothGatt) {
        if (gatt !== g) return
        if (fromRadioDrainUntilEmpty) {
            fromRadioDrainUntilEmpty = false
            Log.d(TAG, "FROMNUM → FromRadio: буфер вычитан до пустого кадра")
        }
        emptyReadStreak++
        if (!pollingActive || _state.value == NodeConnectionState.READY) {
            return
        }
        val wallMs = if (handshakeWallStartedMs > 0L) {
            SystemClock.uptimeMillis() - handshakeWallStartedMs
        } else {
            0L
        }
        val acked = _initialWantConfigAcknowledged.value
        val deadlineExceeded = wallMs >= HANDSHAKE_ABSOLUTE_MAX_MS

        if (emptyReadStreak < READY_EMPTY_THRESHOLD) {
            scheduleNextPoll(g)
            return
        }

        if (!acked && handshakePollsWaitingForConfigComplete < MAX_HANDSHAKE_POLLS_WITHOUT_CONFIG && !deadlineExceeded) {
            handshakePollsWaitingForConfigComplete++
            Log.d(
                TAG,
                "want_config: ${READY_EMPTY_THRESHOLD} пустых подряд — ждём config_complete_id " +
                    "(цикл $handshakePollsWaitingForConfigComplete/$MAX_HANDSHAKE_POLLS_WITHOUT_CONFIG, ${wallMs}ms)",
            )
            emptyReadStreak = 0
            scheduleNextPoll(g)
            return
        }

        if (deadlineExceeded) {
            Log.e(TAG, "want_config: абсолютный таймаут ${wallMs}ms — READY (аварий)")
            finalizeHandshakeReady("абсолютный таймаут", allowReadyWithoutConfigAck = true)
            return
        }
        if (!acked) {
            Log.w(TAG, "want_config: config_complete_id не пришёл за лимит циклов — READY (аварий)")
            finalizeHandshakeReady("лимит циклов без ack", allowReadyWithoutConfigAck = true)
            return
        }
    }

    private fun doConnect() {
        closeGatt()
        cancelReconnect()
        val ctx = appContext ?: return
        val device = targetDevice ?: return
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "BT выключен — scheduleReconnect")
            setState(NodeConnectionState.RECONNECTING)
            scheduleReconnect()
            return
        }
        setState(NodeConnectionState.CONNECTING)
        wantConfigSent = false
        _initialWantConfigAcknowledged.value = false
        pollingActive = false
        emptyReadStreak = 0
        try {
            gatt = device.bluetoothDevice.connectGatt(
                ctx,
                false,
                buildCallback(),
                BluetoothDevice.TRANSPORT_LE,
            )
            Log.d(TAG, "connectGatt → ${device.address}")
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt: ${e.message}")
            setState(NodeConnectionState.RECONNECTING)
            scheduleReconnect()
        }
    }

    private fun buildCallback() = object : BluetoothGattCallback() {

        /** Проверяем, что этот callback всё ещё актуален (gatt не заменён). */
        private fun BluetoothGatt.isStale() = gatt !== this

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected → discoverServices")
                    mainHandler.post {
                        if (g.isStale()) return@post
                        BleTransportManager.requestHighPerformance(g)
                        reconnectDelayMs = 2_000L
                        setState(NodeConnectionState.HANDSHAKING)
                        // HANDSHAKING → setState уже выставил DISCOVERING, сразу уточняем
                        setSyncStep(NodeSyncStep.DISCOVERING)
                    }
                    try { g.discoverServices() }
                    catch (e: SecurityException) {
                        mainHandler.post { if (!g.isStale()) onFail(g, e.message) }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "GATT disconnected (status=$status)")
                    mainHandler.post {
                        closeGattRefs(g)
                        MeshNodeDbRepository.onGattSessionEnded()
                        if (targetDevice != null) {
                            setState(NodeConnectionState.RECONNECTING)
                            scheduleReconnect()
                        } else {
                            setState(NodeConnectionState.DISCONNECTED)
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            mainHandler.post {
                cancelMtuWatchdog()
                if (g.isStale()) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) { onFail(g, "services status=$status"); return@post }
                val svc = g.getService(MESH_SERVICE_UUID.uuid)
                if (svc == null) { onFail(g, "mesh service not found"); return@post }
                val tor = svc.getCharacteristic(TORADIO_UUID)
                if (tor != null) {
                    val p = tor.properties
                    toRadioWriteType = if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    Log.d(TAG, "ToRadio properties=0x${p.toString(16)} writeType=$toRadioWriteType")
                } else {
                    toRadioWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                toRadioChar = tor
                fromRadioChar = svc.getCharacteristic(FROMRADIO_UUID)
                Log.d(TAG, "services OK → requestMtu (подписка только после onMtuChanged)")
                setSyncStep(NodeSyncStep.MTU)
                val wd = Runnable {
                    mtuWatchdogRunnable = null
                    if (g.isStale() || subscribeHandshakeStarted) return@Runnable
                    Log.e(TAG, "onMtuChanged не вызван за ${MTU_WATCHDOG_MS}мс — разрыв (Phase 1)")
                    onFail(g, "MTU: нет onMtuChanged")
                }
                mtuWatchdogRunnable = wd
                mainHandler.postDelayed(wd, MTU_WATCHDOG_MS)
                try { g.requestMtu(512) }
                catch (e: SecurityException) {
                    cancelMtuWatchdog()
                    onFail(g, e.message)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            mainHandler.post {
                cancelMtuWatchdog()
                BleTransportManager.recordMtuNegotiated(mtu, status)
                Log.d(TAG, "MTU=$mtu status=$status")
                if (g.isStale()) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    wantConfigAllowed = true
                    setSyncStep(NodeSyncStep.SUBSCRIBING)
                    subscribeAndHandshake(g)
                } else {
                    Log.e(TAG, "requestMtu не GATT_SUCCESS (status=$status) — Phase 1 провалена")
                    onFail(g, "MTU status=$status")
                }
            }
        }

        private fun subscribeAndHandshake(g: BluetoothGatt) {
            if (g.isStale()) return
            if (subscribeHandshakeStarted) return
            subscribeHandshakeStarted = true
            val svc = g.getService(MESH_SERVICE_UUID.uuid) ?: run { onFail(g, "svc gone"); return }
            val fromNum = svc.getCharacteristic(FROMNUM_UUID)
            if (fromNum != null) {
                try {
                    g.setCharacteristicNotification(fromNum, true)
                    val desc = fromNum.getDescriptor(CCCD_UUID)
                    if (desc != null) {
                        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            if (g.writeDescriptor(desc)) BluetoothGatt.GATT_SUCCESS else -1
                        }
                        if (r == BluetoothGatt.GATT_SUCCESS) return // ждём onDescriptorWrite
                    }
                } catch (_: SecurityException) {}
            }
            // Без FROMNUM сразу шлём want_config
            mainHandler.postDelayed({ if (!g.isStale()) sendWantConfig(g) }, 20L)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.characteristic?.uuid != FROMNUM_UUID) return
            mainHandler.post {
                if (g.isStale()) return@post
                Log.d(TAG, "FROMNUM CCCD written status=$status → want_config")
                mainHandler.postDelayed({ if (!g.isStale()) sendWantConfig(g) }, 20L)
            }
        }

        private fun sendWantConfig(g: BluetoothGatt) {
            if (wantConfigSent) return
            if (!wantConfigAllowed) {
                if (wantConfigMtuRetries >= WANT_CONFIG_MTU_RETRY_MAX) {
                    Log.e(TAG, "want_config: MTU не готов после $WANT_CONFIG_MTU_RETRY_MAX попыток — переподключение")
                    wantConfigMtuRetries = 0
                    onFail(g, "want_config: MTU не разрешён")
                    return
                }
                wantConfigMtuRetries++
                Log.w(TAG, "want_config: MTU ещё не готов — повтор через 100мс ($wantConfigMtuRetries)")
                mainHandler.postDelayed({ if (!g.isStale() && !wantConfigSent) sendWantConfig(g) }, 100L)
                return
            }
            val ch = toRadioChar
            if (ch == null) {
                if (wantConfigToradioRetries >= WANT_CONFIG_TORADIO_RETRY_MAX) {
                    Log.e(TAG, "want_config: ToRadio null после $WANT_CONFIG_TORADIO_RETRY_MAX попыток")
                    wantConfigToradioRetries = 0
                    wantConfigMtuRetries = 0
                    onFail(g, "ToRadio characteristic null")
                    return
                }
                wantConfigToradioRetries++
                Log.w(TAG, "want_config: ToRadio ещё null — повтор через 50мс ($wantConfigToradioRetries)")
                mainHandler.postDelayed({ if (!g.isStale() && !wantConfigSent) sendWantConfig(g) }, 50L)
                return
            }
            wantConfigMtuRetries = 0
            wantConfigToradioRetries = 0
            wantConfigSent = true
            _initialWantConfigAcknowledged.value = false
            setSyncStep(NodeSyncStep.WANT_CONFIG)
            val pkt = MeshWireWantConfigHandshake.encodeToRadioWantConfigId(
                MeshWireWantConfigHandshake.CONFIG_NONCE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val r = g.writeCharacteristic(ch, pkt, toRadioWriteType)
                    when {
                        r == BluetoothGatt.GATT_SUCCESS -> {
                            wantConfigGattBusyRetries = 0
                        }
                        isGattWriteRequestBusyReturn(r) -> {
                            wantConfigSent = false
                            wantConfigGattBusyRetries++
                            if (wantConfigGattBusyRetries > 120) {
                                wantConfigGattBusyRetries = 0
                                Log.w(TAG, "want_config: GATT занят (201) — слишком много повторов")
                                onFail(g, "want_config: GATT занят")
                                return
                            }
                            mainHandler.postDelayed({ if (!g.isStale()) sendWantConfig(g) }, 40L)
                            return
                        }
                        else -> {
                            wantConfigSent = false
                            Log.w(TAG, "want_config writeCharacteristic не принят: $r")
                            onFail(g, "want_config write: $r")
                            return
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        ch.writeType = toRadioWriteType
                        ch.value = pkt
                        if (!g.writeCharacteristic(ch)) {
                            wantConfigSent = false
                            onFail(g, "want_config write false")
                            return
                        }
                    }
                }
                Log.d(TAG, "want_config sent")
            } catch (e: SecurityException) {
                wantConfigSent = false
                onFail(g, e.message)
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (ch.uuid != TORADIO_UUID) return
            mainHandler.post {
                if (g.isStale()) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "ToRadio write status=$status")
                    if (_state.value != NodeConnectionState.READY) {
                        // Ошибка во время handshake — всё равно пробуем читать FromRadio
                        mainHandler.postDelayed({ if (!g.isStale()) startPolling(g) }, 25L)
                    } else {
                        writeInFlight = false
                        val item = writeQueue.pollFirst()
                        item?.onComplete?.invoke(false, "BLE write status=$status")
                        pumpQueue()
                    }
                    return@post
                }
                if (_state.value != NodeConnectionState.READY) {
                    // want_config записан — начинаем читать поток FromRadio
                    wantConfigGattBusyRetries = 0
                    setSyncStep(NodeSyncStep.SYNCING)
                    mainHandler.postDelayed({ if (!g.isStale()) startPolling(g) }, 20L)
                } else {
                    // Пользовательский пакет подтверждён
                    pumpWriteBusyRetries = 0
                    val item = writeQueue.pollFirst()
                    val delay = item?.delayAfterMs ?: 0L
                    item?.onComplete?.invoke(true, null)
                    writeInFlight = false
                    if (delay > 0L) {
                        mainHandler.postDelayed({ pumpQueue() }, delay)
                    } else {
                        pumpQueue()
                    }
                }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid != FROMNUM_UUID) return
            mainHandler.post {
                if (g.isStale()) return@post
                Log.d(TAG, "FROMNUM changed → вычитывание FromRadio до пустого кадра")
                fromRadioDrainUntilEmpty = true
                this@NodeGattConnection.readFromRadioCharacteristicNow(g)
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleRead(g, ch, value, status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int,
        ) {
            handleRead(g, ch, ch.value ?: byteArrayOf(), status)
        }

        private fun handleRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, bytes: ByteArray, status: Int) {
            if (ch.uuid != FROMRADIO_UUID) return
            this@NodeGattConnection.dispatchGattFromRadioRead(g, bytes, status)
        }

        private fun onFail(g: BluetoothGatt, msg: String?) {
            Log.w(TAG, "NodeGatt fail: $msg")
            if (g.isStale()) return
            closeGatt()
            if (targetDevice != null) {
                setState(NodeConnectionState.RECONNECTING)
                scheduleReconnect()
            } else {
                setState(NodeConnectionState.DISCONNECTED)
            }
        }
    }

    // ── Очередь записи ────────────────────────────────────────────────────────

    private fun pumpQueue() {
        if (writeInFlight) return
        if (_state.value != NodeConnectionState.READY) return
        val g = gatt ?: return
        val ch = toRadioChar ?: return
        val item = writeQueue.firstOrNull() ?: return
        writeInFlight = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val r = g.writeCharacteristic(ch, item.bytes, toRadioWriteType)
                when {
                    r == BluetoothGatt.GATT_SUCCESS -> {
                        pumpWriteBusyRetries = 0
                    }
                    isGattWriteRequestBusyReturn(r) -> {
                        writeInFlight = false
                        pumpWriteBusyRetries++
                        if (pumpWriteBusyRetries > 220) {
                            pumpWriteBusyRetries = 0
                            writeQueue.pollFirst()?.onComplete?.invoke(
                                false,
                                "writeCharacteristic: занято (201), превышено число повторов",
                            )
                            pumpQueue()
                        } else {
                            mainHandler.postDelayed({ pumpQueue() }, 40L)
                        }
                    }
                    else -> {
                        // Как 201: часто краткоживущий отказ, пока GATT читает/пишет другое
                        writeInFlight = false
                        pumpWriteBusyRetries++
                        if (pumpWriteBusyRetries > 220) {
                            pumpWriteBusyRetries = 0
                            writeQueue.pollFirst()?.onComplete?.invoke(
                                false,
                                "writeCharacteristic: $r (исчерпаны повторы)",
                            )
                            pumpQueue()
                        } else {
                            mainHandler.postDelayed({ pumpQueue() }, 40L)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = toRadioWriteType
                ch.value = item.bytes
                if (!g.writeCharacteristic(ch)) {
                    // До API 33: boolean false часто означает «сейчас нельзя писать» (как 201), а не потерю пакета;
                    // при длинной очереди (каналы: begin_edit…commit_edit) нельзя снимать кадр с очереди.
                    writeInFlight = false
                    pumpWriteBusyRetries++
                    if (pumpWriteBusyRetries > 300) {
                        pumpWriteBusyRetries = 0
                        writeQueue.pollFirst()?.onComplete?.invoke(false, "writeCharacteristic false")
                        pumpQueue()
                    } else {
                        mainHandler.postDelayed({ pumpQueue() }, 45L)
                    }
                }
            }
        } catch (e: SecurityException) {
            writeInFlight = false
            writeQueue.pollFirst()?.onComplete?.invoke(false, e.message)
            pumpQueue()
        }
    }

    // ── Переподключение ───────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        cancelReconnect()
        val delay = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(30_000L)
        val r = Runnable {
            reconnectRunnable = null
            if (targetDevice != null) doConnect()
        }
        reconnectRunnable = r
        mainHandler.postDelayed(r, delay)
        Log.d(TAG, "reconnect через ${delay}мс")
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    // ── Закрытие GATT ─────────────────────────────────────────────────────────

    private fun closeGatt() {
        cancelMtuWatchdog()
        fromRadioParseGeneration++
        wantConfigAllowed = false
        subscribeHandshakeStarted = false
        fromRadioDrainUntilEmpty = false
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        postReadyDrainRunnable?.let { mainHandler.removeCallbacks(it) }
        postReadyDrainRunnable = null
        postReadyDrainRemaining = 0
        pollingActive = false
        writeInFlight = false
        toRadioChar = null
        toRadioWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        fromRadioChar = null
        wantConfigSent = false
        emptyReadStreak = 0
        handshakePollsWaitingForConfigComplete = 0
        handshakeWallStartedMs = 0L
        wantConfigMtuRetries = 0
        wantConfigToradioRetries = 0
        _myNodeNum.value = null
        _initialWantConfigAcknowledged.value = false
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
        }
        try {
            gatt?.close()
        } catch (_: SecurityException) {
        }
        gatt = null
        MeshNodePassiveFromRadioSink.resetSession()
        failPendingWrites()
    }

    private fun closeGattRefs(g: BluetoothGatt) {
        if (gatt !== g) return
        cancelMtuWatchdog()
        fromRadioParseGeneration++
        wantConfigAllowed = false
        subscribeHandshakeStarted = false
        fromRadioDrainUntilEmpty = false
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        postReadyDrainRunnable?.let { mainHandler.removeCallbacks(it) }
        postReadyDrainRunnable = null
        postReadyDrainRemaining = 0
        pollingActive = false
        writeInFlight = false
        toRadioChar = null
        toRadioWriteType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        fromRadioChar = null
        wantConfigSent = false
        emptyReadStreak = 0
        handshakePollsWaitingForConfigComplete = 0
        handshakeWallStartedMs = 0L
        wantConfigMtuRetries = 0
        wantConfigToradioRetries = 0
        _initialWantConfigAcknowledged.value = false
        try {
            g.close()
        } catch (_: SecurityException) {
        }
        gatt = null
        MeshNodePassiveFromRadioSink.resetSession()
        failPendingWrites()
    }

    private fun failPendingWrites() {
        while (writeQueue.isNotEmpty()) {
            writeQueue.pollFirst()?.onComplete?.invoke(false, "Соединение разорвано")
        }
    }
}
