package com.example.aura.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.aura.meshwire.MeshWireChannelsSyncResult
import com.example.aura.meshwire.MeshWireDevicePushState
import com.example.aura.meshwire.MeshWireExternalNotificationPushState
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.meshwire.MeshWireMqttPushState
import com.example.aura.meshwire.MeshWireNodeListAccumulator
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWireNodeUserProfile
import com.example.aura.meshwire.MeshWirePositionPushState
import com.example.aura.meshwire.MeshWireSecurityPushState
import com.example.aura.meshwire.MeshWireTelemetryPushState
import com.example.aura.mesh.nodedb.MeshNodeDbRepository
import com.example.aura.meshwire.MeshWireWantConfigHandshake
import com.example.aura.security.NodeAuthStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "MeshBLE"

val meshBleMainHandler = Handler(Looper.getMainLooper())

// ─── Официальные BLE UUID mesh ──────────────────────────────────────────

val MESH_SERVICE_UUID: ParcelUuid =
    ParcelUuid.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")

val TORADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
val FROMRADIO_UUID: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
val FROMNUM_UUID: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")

// ─── Модель устройства ────────────────────────────────────────────────────────

data class MeshDevice(
    val name: String,
    val address: String,
    val bluetoothDevice: BluetoothDevice,
    val isBonded: Boolean = false,
    val isMeshWire: Boolean = true,
) {
    val tentativeNodeId: String = run {
        val suffix = name.substringAfter("_", "").trim()
        if (suffix.matches(Regex("[0-9A-Fa-f]{4,8}"))) suffix.uppercase()
        else address.replace(":", "").takeLast(8).uppercase()
    }
}

// ─── Сканер ───────────────────────────────────────────────────────────────────

class MeshBleScanner(private val context: Context) {

    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val adapter get() = bluetoothManager?.adapter

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    fun isLocationEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private var activeCallback: ScanCallback? = null

    fun getBondedMeshDevices(): List<MeshDevice> {
        return try {
            adapter?.bondedDevices
                ?.filter { dev ->
                    try {
                        val n = dev.name ?: ""
                        n.contains("meshwire", ignoreCase = true)
                    } catch (_: SecurityException) { false }
                }
                ?.map { dev ->
                    val name = try { dev.name ?: "mesh" } catch (_: SecurityException) { "mesh" }
                    MeshDevice(name, dev.address, dev, isBonded = true)
                } ?: emptyList()
        } catch (_: SecurityException) { emptyList() }
    }

    fun startScan(
        onDeviceFound: (MeshDevice) -> Unit,
        onError: (String) -> Unit,
        meshOnly: Boolean = true,
    ) {
        if (!isBluetoothEnabled) { onError("Bluetooth выключен"); return }
        if (!isLocationEnabled()) {
            onError("Включите геолокацию — нужна для BLE на Android ≤ 11")
            return
        }
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) { onError("BLE-сканер недоступен"); return }
        stopScan()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        val found = mutableSetOf<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleResult(result, found, onDeviceFound, meshOnly)
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleResult(it, found, onDeviceFound, meshOnly) }
            }
            override fun onScanFailed(errorCode: Int) {
                val msg = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> {
                        stopScan(); onError("Сканирование уже запущено — нажмите ещё раз"); return
                    }
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE не поддерживается на устройстве"
                    SCAN_FAILED_INTERNAL_ERROR -> "Внутренняя ошибка BLE (перезагрузите BT)"
                    else -> "Ошибка BLE (код $errorCode)"
                }
                onError(msg)
            }
        }
        activeCallback = cb
        try {
            scanner.startScan(null, settings, cb)
            Log.d(TAG, "BLE scan started meshOnly=$meshOnly")
        } catch (e: SecurityException) {
            onError("Нет разрешения Bluetooth: ${e.message}")
        }
    }

    private fun handleResult(
        result: ScanResult,
        found: MutableSet<String>,
        onDeviceFound: (MeshDevice) -> Unit,
        meshOnly: Boolean,
    ) {
        val addr = result.device.address
        if (addr in found) return
        val recordName = result.scanRecord?.deviceName
        val deviceName = recordName ?: try { result.device.name } catch (_: SecurityException) { null }
        val serviceUuids = result.scanRecord?.serviceUuids
        val rssi = result.rssi
        val isMeshWire =
            deviceName?.contains("meshwire", ignoreCase = true) == true ||
            serviceUuids?.contains(MESH_SERVICE_UUID) == true
        Log.d(TAG, "BLE [$rssi dBm] $addr name='$deviceName' mesh=$isMeshWire services=$serviceUuids")
        if (meshOnly && !isMeshWire) return
        found.add(addr)
        val finalName = when {
            !deviceName.isNullOrBlank() -> deviceName
            isMeshWire -> "MeshWire_${addr.replace(":", "").takeLast(4)}"
            else -> "Unknown [${addr.takeLast(5)}]"
        }
        onDeviceFound(MeshDevice(finalName, addr, result.device, isMeshWire = isMeshWire))
    }

    fun stopScan() {
        val cb = activeCallback ?: return
        try {
            adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: SecurityException) {
        }
        activeCallback = null
        Log.d(TAG, "BLE scan stopped")
    }

    fun unbindDevice(address: String): Boolean {
        return try {
            val device = adapter?.getRemoteDevice(address) ?: return false
            device.javaClass.getMethod("removeBond").invoke(device) as? Boolean ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove bond for $address: ${e.message}")
            false
        }
    }
}

// ─── Проверка BT-связи ────────────────────────────────────────────────────────

fun isMeshNodeBluetoothLinked(context: Context, deviceAddress: String): Boolean {
    return try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: return false
        if (!adapter.isEnabled) return false
        val device = adapter.getRemoteDevice(deviceAddress)
        val bonded = try {
            device.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: SecurityException) {
            false
        }
        bonded || NodeGattConnection.isAlive
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}

// ─── Мост: fetchMeshWireXxx → NodeAdminApi ──────────────────────────────────
//
// Все функции ниже перенаправляют запросы в NodeAdminApi через постоянное
// соединение NodeGattConnection. Сигнатуры не изменились — вызывающий код не требует правок.

/**
 * Мгновенный снимок (без ожидания) — только для быстрых проверок.
 * Для admin/get настройки нужен [awaitResolvableNodeNumSuspend]: пока идёт handshake,
 * [NodeGattConnection.myNodeNum] ещё null, а NodeAuthStore/UI могут дать неверный nodenum.
 */
private fun resolveNodeNum(context: Context, localNodeNum: UInt?): UInt? {
    val live = NodeGattConnection.myNodeNum.value
    if (live != null && live != 0u) return live
    NodeAuthStore.load(context)?.nodeId
        ?.let { MeshWireNodeNum.parseToUInt(it) }
        ?.let { return it }
    NodeAuthStore.loadStoredIdentity(context)?.nodeId?.takeIf { it.isNotBlank() }
        ?.let { MeshWireNodeNum.parseToUInt(it) }
        ?.let { return it }
    return localNodeNum
}

/**
 * Ожидание завершения начального want_config-дампа (как `configCompleteId` в
 * [meshtastic/web decodePacket](https://github.com/meshtastic/web/blob/main/packages/core/src/utils/transform/decodePacket.ts)).
 */
private const val AWAIT_WANT_CONFIG_ACK_MS = 20_000L

private suspend fun awaitInitialWantConfigAckIfNeeded() {
    if (!NodeGattConnection.initialWantConfigAcknowledged.value && NodeGattConnection.isAlive) {
        val ok = withTimeoutOrNull(AWAIT_WANT_CONFIG_ACK_MS) {
            NodeGattConnection.initialWantConfigAcknowledged
                .filter { it }
                .first()
        }
        if (ok == null) {
            Log.w(
                "MeshBleScanner",
                "awaitInitialWantConfigAckIfNeeded: таймаут ${AWAIT_WANT_CONFIG_ACK_MS}ms — продолжаем без ack",
            )
        }
    }
}

/** Ждём BLE, my_node_num, завершение want_config, затем store / подсказку из UI. */
private suspend fun awaitResolvableNodeNumSuspend(context: Context, localNodeNum: UInt?): UInt? {
    if (!NodeGattConnection.isAlive) {
        withTimeoutOrNull(20_000L) {
            NodeGattConnection.connectionState
                .filter {
                    it == NodeConnectionState.CONNECTING ||
                        it == NodeConnectionState.HANDSHAKING ||
                        it == NodeConnectionState.READY ||
                        it == NodeConnectionState.RECONNECTING
                }
                .first()
        }
    }
    if (NodeGattConnection.isAlive) {
        withTimeoutOrNull(15_000L) {
            NodeGattConnection.myNodeNum
                .filter { it != null && it != 0u }
                .first()
        }
    }
    awaitInitialWantConfigAckIfNeeded()
    NodeGattConnection.myNodeNum.value?.takeIf { it != 0u }?.let { return it }
    NodeAuthStore.load(context)?.nodeId
        ?.let { MeshWireNodeNum.parseToUInt(it) }
        ?.let { return it }
    NodeAuthStore.loadStoredIdentity(context)?.nodeId?.takeIf { it.isNotBlank() }
        ?.let { MeshWireNodeNum.parseToUInt(it) }
        ?.let { return it }
    return localNodeNum
}

fun fetchMeshWireNodeNumUInt(
    context: Context,
    deviceAddress: String,
    onResult: (UInt?) -> Unit,
) {
    // Сначала проверяем кэшированный NodeAuthStore
    val cached = resolveNodeNum(context, null)
    if (cached != null) {
        meshBleMainHandler.post { onResult(cached) }
        return
    }
    // Иначе ждём my_node_num из потока want_config NodeGattConnection
    val current = NodeGattConnection.myNodeNum.value
    if (current != null && current != 0u) {
        meshBleMainHandler.post { onResult(current) }
        return
    }
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        val num = withTimeoutOrNull(15_000L) {
            NodeGattConnection.myNodeNum
                .filter { it != null && it != 0u }
                .first()
        }
        meshBleMainHandler.post {
            onResult(num)
            scope.cancel()
        }
    }
}

fun fetchMeshWireUserProfile(
    context: Context,
    deviceAddress: String,
    expectedNodeNum: UInt? = null,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireNodeUserProfile?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getUserProfile(num, nodeNumHint = expectedNodeNum)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить профиль")
            scope.cancel()
        }
    }
}

fun fetchMeshWireChannels(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireChannelsSyncResult?, String?) -> Unit,
) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        withTimeoutOrNull(20_000L) {
            if (!NodeGattConnection.isReady) {
                withTimeoutOrNull(15_000L) {
                    NodeGattConnection.connectionState
                        .filter { it == NodeConnectionState.READY }
                        .first()
                }
            }
        }
        awaitInitialWantConfigAckIfNeeded()
        meshBleMainHandler.post { onSyncProgress?.invoke(20) }
        val result = NodeAdminApi.getChannels()
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить каналы")
            scope.cancel()
        }
    }
}

fun fetchMeshWireLoRaConfig(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireLoRaPushState?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getLoRaConfig(num)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить LoRa")
            scope.cancel()
        }
    }
}

fun fetchMeshWireDeviceConfig(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireDevicePushState?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getDeviceConfig(num)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить Device Config")
            scope.cancel()
        }
    }
}

fun fetchMeshWirePositionConfig(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWirePositionPushState?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getPositionConfig(num)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить Position Config")
            scope.cancel()
        }
    }
}

fun fetchMeshWireSecurityConfig(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireSecurityPushState?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getSecurityConfig(num)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить Security Config")
            scope.cancel()
        }
    }
}

fun fetchMeshWireMqttConfig(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireMqttPushState?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getMqttConfig(num)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить MQTT Config")
            scope.cancel()
        }
    }
}

fun fetchMeshWireExternalNotificationConfig(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireExternalNotificationPushState?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getExternalNotificationConfig(num)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить External Notification Config")
            scope.cancel()
        }
    }
}

fun fetchMeshWireTelemetryConfig(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    localNodeNum: UInt? = null,
    onResult: (MeshWireTelemetryPushState?, String?) -> Unit,
) {
    val appCtx = context.applicationContext
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post { onSyncProgress?.invoke(0) }
        val num = awaitResolvableNodeNumSuspend(appCtx, localNodeNum) ?: run {
            meshBleMainHandler.post { onResult(null, "Node ID неизвестен") }
            scope.cancel()
            return@launch
        }
        meshBleMainHandler.post { onSyncProgress?.invoke(30) }
        val result = NodeAdminApi.getTelemetryConfig(num)
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (result != null) onResult(result, null)
            else onResult(null, "Не удалось загрузить Telemetry Config")
            scope.cancel()
        }
    }
}

fun fetchMeshWireNodes(
    context: Context,
    deviceAddress: String,
    onSyncProgress: ((Int) -> Unit)? = null,
    onResult: (List<MeshWireNodeSummary>?, String?) -> Unit,
) {
    // Список узлов накапливается из want_config потока через NodeAdminApi.getChannels()
    // Для узлов используем отдельный want_config-дамп через NodeGattConnection
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    scope.launch {
        meshBleMainHandler.post {
            onSyncProgress?.invoke(0)
            onSyncProgress?.invoke(20)
        }
        val wantConfigPkt = MeshWireWantConfigHandshake.encodeToRadioWantConfigId(
            MeshWireWantConfigHandshake.CONFIG_NONCE,
        )
        val acc = MeshWireNodeListAccumulator()
        var frames = 0
        var frameListener: ((ByteArray) -> Unit)? = null

        // Ждём READY, затем want_config; выход по config_complete, лимиту кадров или таймауту.
        // Раньше continuation не резюмировалась при успешной записи want_config (только после >500
        // кадров) — слушатель висел до таймаута и дублировался при повторных обновлениях.
        withTimeoutOrNull(20_000L) {
            if (!NodeGattConnection.isReady) {
                withTimeoutOrNull(12_000L) {
                    NodeGattConnection.connectionState
                        .filter { it == NodeConnectionState.READY }
                        .first()
                }
            }
            if (!NodeGattConnection.isReady) return@withTimeoutOrNull

            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val listener: (ByteArray) -> Unit = meshDump@{ bytes ->
                        acc.consumeFromRadio(bytes, System.currentTimeMillis() / 1000L, context.applicationContext)
                        frames++
                        val cc = acc.pollConfigCompleteId()
                        if (cc != null && cc == MeshWireWantConfigHandshake.CONFIG_NONCE.toLong()) {
                            if (cont.isActive) cont.resume(Unit)
                            return@meshDump
                        }
                        if (frames >= 500 && cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                    frameListener = listener
                    cont.invokeOnCancellation { NodeGattConnection.removeFrameListener(listener) }
                    NodeGattConnection.addFrameListener(listener)
                    NodeGattConnection.sendToRadio(wantConfigPkt) { ok, _ ->
                        if (!ok && cont.isActive) cont.resume(Unit)
                    }
                }
            } finally {
                frameListener?.let { runCatching { NodeGattConnection.removeFrameListener(it) } }
                frameListener = null
            }
        }

        val summaries = acc.toSummaries()
        val snapAddr = deviceAddress.trim()
        if (summaries.isNotEmpty()) {
            MeshNodeDbRepository.mergeFullSnapshot(snapAddr, summaries)
        }
        meshBleMainHandler.post {
            onSyncProgress?.invoke(100)
            if (summaries.isEmpty()) onResult(null, "Не удалось получить список узлов")
            else onResult(summaries, null)
            scope.cancel()
        }
    }
}
