package com.example.aura.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.example.aura.security.NodeAuthStore
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "MeshBleAutoConnect"
private const val AUTO_SCAN_WINDOW_MS = 12_000L
private val autoScanRunning = AtomicBoolean(false)

/**
 * MAC последней BLE-ноды: сначала из сессии (если это не TCP/USB), иначе из [NodeAuthStore]
 * (в т.ч. только адрес без пароля после «Отвязать пароль»).
 */
fun lastSavedBleMacForAutoConnect(context: Context, sessionMeshAddress: String?): String? {
    val fromSession = sessionMeshAddress?.trim()?.takeIf { it.isNotEmpty() }
    if (fromSession != null) {
        val norm = MeshNodeSyncMemoryStore.normalizeKey(fromSession)
        if (!MeshDeviceTransport.isTcpAddress(norm) && !MeshDeviceTransport.isUsbAddress(norm)) {
            return fromSession
        }
    }
    val fromStore = NodeAuthStore.loadDeviceAddressForPrefetch(context)?.trim()?.takeIf { it.isNotEmpty() }
    if (fromStore != null) {
        val n2 = MeshNodeSyncMemoryStore.normalizeKey(fromStore)
        if (!MeshDeviceTransport.isTcpAddress(n2) && !MeshDeviceTransport.isUsbAddress(n2)) {
            return fromStore
        }
    }
    val peek = NodeAuthStore.peekBleMacAfterUserDisconnect(context.applicationContext)?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val n3 = MeshNodeSyncMemoryStore.normalizeKey(peek)
    if (MeshDeviceTransport.isTcpAddress(n3) || MeshDeviceTransport.isUsbAddress(n3)) return null
    return peek
}

/** [MeshDevice] по MAC для GATT (сканирование не обязательно). */
fun meshDeviceFromBleMac(context: Context, rawAddress: String): MeshDevice? {
    val raw = rawAddress.trim().takeIf { it.isNotEmpty() } ?: return null
    val bm = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter ?: run {
        Log.w(TAG, "Bluetooth недоступен")
        return null
    }
    if (!adapter.isEnabled) {
        Log.w(TAG, "Bluetooth выключен")
        return null
    }
    val btDevice = try {
        adapter.getRemoteDevice(raw)
    } catch (_: IllegalArgumentException) {
        Log.w(TAG, "Некорректный MAC: $raw")
        return null
    } catch (_: Exception) {
        Log.w(TAG, "getRemoteDevice не удалось")
        return null
    }
    val name = try {
        btDevice.name?.takeIf { it.isNotBlank() } ?: "mesh"
    } catch (_: SecurityException) {
        "mesh"
    }
    return MeshDevice(name, raw, btDevice)
}

/**
 * Восстанавливает BLE GATT к последней ноде из [NodeAuthStore] (только MAC, не TCP/USB).
 * Безопасно вызывать из main thread после остановки foreground-сервиса.
 */
fun tryAutoConnectSavedBleNode(context: Context) {
    val auth = NodeAuthStore.load(context.applicationContext) ?: return
    val raw = auth.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val norm = MeshNodeSyncMemoryStore.normalizeKey(raw)
    if (MeshDeviceTransport.isTcpAddress(norm) || MeshDeviceTransport.isUsbAddress(norm)) {
        Log.d(TAG, "Пропуск автоподключения: не BLE ($norm)")
        return
    }
    val meshDevice = meshDeviceFromBleMac(context.applicationContext, raw) ?: return
    Log.d(TAG, "Автоподключение к ${meshDevice.address}")
    NodeGattConnection.connect(meshDevice, context.applicationContext)
}

/**
 * Фоновый автоскан BLE к последней ноде. Если устройство найдено — сразу [NodeGattConnection.connect].
 * Используется как fallback, когда прямой connect по MAC не поднимает сессию.
 */
fun tryAutoScanAndConnectSavedBleNode(context: Context) {
    val app = context.applicationContext
    val auth = NodeAuthStore.load(app) ?: return
    val raw = auth.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val norm = MeshNodeSyncMemoryStore.normalizeKey(raw)
    if (MeshDeviceTransport.isTcpAddress(norm) || MeshDeviceTransport.isUsbAddress(norm)) return
    if (NodeGattConnection.isAlive || NodeGattConnection.isReady) return
    if (!autoScanRunning.compareAndSet(false, true)) return

    val scanner = MeshBleScanner(app)
    val targetNorm = MeshNodeSyncMemoryStore.normalizeKey(raw)
    val stopScan = Runnable {
        runCatching { scanner.stopScan() }
        autoScanRunning.set(false)
    }

    scanner.startScan(
        onDeviceFound = { dev ->
            val gotNorm = MeshNodeSyncMemoryStore.normalizeKey(dev.address)
            if (gotNorm != targetNorm) return@startScan
            meshBleMainHandler.removeCallbacks(stopScan)
            scanner.stopScan()
            autoScanRunning.set(false)
            Log.d(TAG, "Автоскан нашёл последнюю ноду: ${dev.address}, подключаемся")
            NodeGattConnection.connect(dev, app)
        },
        onError = { err ->
            Log.w(TAG, "Автоскан BLE: $err")
            autoScanRunning.set(false)
            // Если скан невозможен (например, локация выключена), остаётся прямой reconnect по MAC.
        },
        meshOnly = false,
    )

    meshBleMainHandler.postDelayed(stopScan, AUTO_SCAN_WINDOW_MS)
}
