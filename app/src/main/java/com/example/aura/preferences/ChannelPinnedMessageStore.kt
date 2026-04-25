package com.example.aura.preferences

import android.content.Context
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import org.json.JSONObject

/**
 * Локально одно закреплённое сообщение на канал (как в Telegram), без синхронизации по эфиру.
 */
object ChannelPinnedMessageStore {
    private const val PREFS = "channel_pinned_messages"

    private fun prefKey(deviceMac: String, channelRowKey: String): String =
        "${MeshNodeSyncMemoryStore.normalizeKey(deviceMac)}_$channelRowKey"

    fun read(context: Context, deviceMac: String, channelRowKey: String): ChannelPinnedSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(prefKey(deviceMac, channelRowKey), null) ?: return null
        return runCatching { parse(raw) }.getOrNull()
    }

    fun write(context: Context, deviceMac: String, channelRowKey: String, snapshot: ChannelPinnedSnapshot?) {
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val key = prefKey(deviceMac, channelRowKey)
        if (snapshot == null) {
            ed.remove(key)
        } else {
            ed.putString(key, snapshot.toJsonString())
        }
        ed.apply()
    }

    /** Снять закрепы для всех каналов данного BLE-устройства (нормализованный MAC в ключе). */
    fun clearAllForDeviceMac(context: Context, deviceMac: String) {
        val prefix = "${MeshNodeSyncMemoryStore.normalizeKey(deviceMac)}_"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        val ed = prefs.edit()
        for (k in keys) ed.remove(k)
        ed.apply()
    }

    private fun parse(json: String): ChannelPinnedSnapshot {
        val o = JSONObject(json)
        return ChannelPinnedSnapshot(
            stableId = o.getString("stableId"),
            kind = o.optString("kind", "text"),
            authorLabel = o.optString("author", ""),
            snippet = o.optString("snippet", ""),
            timeMs = o.optLong("timeMs", 0L),
            fromNodeNum = if (o.has("fromNodeNum") && !o.isNull("fromNodeNum")) o.getLong("fromNodeNum") else null,
        )
    }
}

data class ChannelPinnedSnapshot(
    val stableId: String,
    val kind: String,
    val authorLabel: String,
    val snippet: String,
    val timeMs: Long,
    /** Для отображения shortName при обновлении узлов; null у старых закрепов. */
    val fromNodeNum: Long? = null,
) {
    fun toJsonString(): String = JSONObject().apply {
        put("stableId", stableId)
        put("kind", kind)
        put("author", authorLabel)
        put("snippet", snippet)
        put("timeMs", timeMs)
        if (fromNodeNum != null) put("fromNodeNum", fromNodeNum)
    }.toString()
}
