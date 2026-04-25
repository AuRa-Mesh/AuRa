package com.example.aura.preferences

import android.content.Context
import com.example.aura.security.NodeAuthStore
import java.util.Locale

/**
 * Ключ профиля mesh-узла для разделения локальных данных (VIP, аптайм, опыт) по [node id].
 * Использует тот же источник, что префетч BLE: [NodeAuthStore.loadNodeIdForPrefetch].
 */
object NodeScopedStorage {

    const val UNBOUND: String = "unbound"

    /**
     * Стабильный короткий идентификатор для суффиксов ключей SharedPreferences (a-z0-9_).
     */
    fun nodeKey(context: Context): String {
        val raw = NodeAuthStore.loadNodeIdForPrefetch(context).trim()
        if (raw.isEmpty()) return UNBOUND
        return raw.lowercase(Locale.ROOT)
            .removePrefix("!")
            .replace(Regex("[^a-z0-9]"), "_")
            .trim('_')
            .take(96)
            .ifEmpty { UNBOUND }
    }

    /** Суффикс ключа: `base__<nodeKey>`. */
    fun scopedKey(context: Context, baseKey: String): String =
        "${baseKey}__${nodeKey(context)}"

    /** Для записи по уже известному [nodeKey] (смена сессии в памяти). */
    fun scopedStorageKey(baseKey: String, nodeKey: String): String =
        "${baseKey}__$nodeKey"
}
