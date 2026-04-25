package com.example.aura.security

import android.content.Context
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore

data class SavedAuth(
    val nodeId: String,
    val password: String,
    /** MAC выбранной при сопряжении ноды (для проверки BLE). */
    val deviceAddress: String? = null
)

object NodeAuthStore {
    private const val PREFS = "aura_auth"
    private const val KEY_NODE_ID = "authenticated_node_id"
    private const val KEY_PASSWORD = "authenticated_password"
    private const val KEY_DEVICE_ADDRESS = "mesh_device_address"
    /** BLE MAC до «Отключиться» в диалоге — для повторного подключения по тапу на бейдж ноды. */
    private const val KEY_BLE_MAC_AFTER_USER_DISCONNECT = "ble_mac_after_user_disconnect"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, nodeId: String, password: String, deviceAddress: String? = null) {
        val e = prefs(context).edit()
            .putString(KEY_NODE_ID, nodeId)
            .putString(KEY_PASSWORD, password)
        if (!deviceAddress.isNullOrBlank()) e.putString(KEY_DEVICE_ADDRESS, deviceAddress)
        else e.remove(KEY_DEVICE_ADDRESS)
        e.apply()
    }

    /**
     * Полная сессия: есть node id и непустой пароль (чат, фоновый BLE, автоподключение).
     */
    fun load(context: Context): SavedAuth? {
        val p = prefs(context)
        val nodeId = p.getString(KEY_NODE_ID, null)?.takeIf { it.isNotEmpty() }
        val password = p.getString(KEY_PASSWORD, null)?.takeIf { it.isNotEmpty() }
        if (nodeId == null || password == null) return null
        val addr = p.getString(KEY_DEVICE_ADDRESS, null)?.takeIf { it.isNotEmpty() }
        return SavedAuth(nodeId, password, addr)
    }

    /**
     * Сохранённый node id и MAC (пароль может быть пустым) — для экрана пароля после «Отвязать ноду».
     */
    fun loadStoredIdentity(context: Context): SavedAuth? {
        val p = prefs(context)
        val nodeId = p.getString(KEY_NODE_ID, null)?.takeIf { it.isNotEmpty() } ?: return null
        val password = p.getString(KEY_PASSWORD, null).orEmpty()
        val addr = p.getString(KEY_DEVICE_ADDRESS, null)?.takeIf { it.isNotEmpty() }
        return SavedAuth(nodeId, password, addr)
    }

    /** Удаляет только пароль (node id и MAC остаются) — быстрый повторный ввод пароля. */
    fun clearPasswordOnly(context: Context) {
        prefs(context).edit().remove(KEY_PASSWORD).apply()
    }

    /**
     * MAC привязанной ноды для BLE-префетча: только из prefs, **без** требования непустого пароля
     * (достаточно сохранённого `KEY_DEVICE_ADDRESS`, как после «Отвязать пароль»).
     */
    fun loadDeviceAddressForPrefetch(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_ADDRESS, null)?.trim()?.takeIf { it.isNotEmpty() }

    /** Node id для подсказки префетча (пароль может быть пустым). */
    fun loadNodeIdForPrefetch(context: Context): String {
        load(context)?.nodeId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return loadStoredIdentity(context)?.nodeId?.trim().orEmpty()
    }

    /**
     * Сохранить BLE MAC перед сбросом [KEY_DEVICE_ADDRESS] (только для BLE, не TCP/USB).
     */
    fun rememberBleMacAfterUserDisconnect(context: Context, rawMac: String?) {
        val e = prefs(context).edit()
        if (rawMac.isNullOrBlank()) {
            e.remove(KEY_BLE_MAC_AFTER_USER_DISCONNECT)
        } else {
            val norm = MeshNodeSyncMemoryStore.normalizeKey(rawMac.trim())
            if (!MeshDeviceTransport.isTcpAddress(norm) && !MeshDeviceTransport.isUsbAddress(norm)) {
                e.putString(KEY_BLE_MAC_AFTER_USER_DISCONNECT, rawMac.trim())
            } else {
                e.remove(KEY_BLE_MAC_AFTER_USER_DISCONNECT)
            }
        }
        e.apply()
    }

    fun peekBleMacAfterUserDisconnect(context: Context): String? =
        prefs(context).getString(KEY_BLE_MAC_AFTER_USER_DISCONNECT, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun clearBleMacAfterUserDisconnect(context: Context) {
        prefs(context).edit().remove(KEY_BLE_MAC_AFTER_USER_DISCONNECT).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_NODE_ID)
            .remove(KEY_PASSWORD)
            .remove(KEY_DEVICE_ADDRESS)
            .remove(KEY_BLE_MAC_AFTER_USER_DISCONNECT)
            .apply()
    }
}
