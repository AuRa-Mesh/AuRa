package com.example.aura.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

/**
 * Признак «нода на связи по Bluetooth» для индикатора в UI.
 *
 * Только [BluetoothProfile.STATE_CONNECTED] по GATT почти всегда false, если ни одно приложение
 * не держит открытый GATT — после обмена данными система помечает устройство как disconnected,
 * хотя нода сопряжена и доступна. Поэтому дополнительно учитываем сопряжение (bond).
 */
fun isMeshNodeBluetoothLinked(context: Context, address: String?): Boolean {
    if (address.isNullOrBlank()) return false
    val key = MeshNodeSyncMemoryStore.normalizeKey(address)
    if (MeshDeviceTransport.isTcpAddress(key) || MeshDeviceTransport.isUsbAddress(key)) return true
    return try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return false
        val adapter = bm.adapter ?: return false
        if (!adapter.isEnabled) return false
        val device = adapter.getRemoteDevice(address)
        if (bm.getConnectionState(device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED) {
            return true
        }
        device.bondState == BluetoothDevice.BOND_BONDED
    } catch (_: SecurityException) {
        false
    }
}

/**
 * Готовность сессии в том же смысле, что строка «Подключено» в диалоге «Соединения»:
 * для BLE — [NodeConnectionState.READY], [NodeSyncStep.READY] и GATT к тому же MAC, что в сессии;
 * для TCP/USB — не-BLE транспорт, считаем готовым при непустом адресе (как [isMeshNodeBluetoothLinked]).
 */
fun isMeshNodeSessionAppReady(
    sessionMeshAddress: String?,
    gattState: NodeConnectionState,
    syncStep: NodeSyncStep,
    gattTargetAddress: String?,
): Boolean {
    val key = sessionMeshAddress?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { MeshNodeSyncMemoryStore.normalizeKey(it) }
        ?: return false
    if (MeshDeviceTransport.isTcpAddress(key) || MeshDeviceTransport.isUsbAddress(key)) {
        return true
    }
    if (gattState != NodeConnectionState.READY) return false
    if (syncStep != NodeSyncStep.READY) return false
    val sess = sessionMeshAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val tgt = gattTargetAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    return MeshNodeSyncMemoryStore.bleHardwareIdentityKey(tgt) ==
        MeshNodeSyncMemoryStore.bleHardwareIdentityKey(sess)
}
