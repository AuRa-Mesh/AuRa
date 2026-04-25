package com.example.aura.preferences

import android.content.Context
import java.util.Locale

object ChatChannelNotificationPreferences {
    private const val PREFS = "chat_channel_notification_prefs"
    private const val KEY_PREFIX = "mute_"

    fun isChannelMuted(context: Context, deviceAddress: String?, channelIndex: Int): Boolean {
        if (deviceAddress.isNullOrBlank()) return false
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(keyFor(deviceAddress, channelIndex), false)
    }

    fun setChannelMuted(context: Context, deviceAddress: String?, channelIndex: Int, muted: Boolean) {
        if (deviceAddress.isNullOrBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(keyFor(deviceAddress, channelIndex), muted)
            .apply()
    }

    /** «Не беспокоить» для личного диалога с узлом [peerNodeNum] (на этом BLE-устройстве). */
    fun isDirectThreadMuted(context: Context, deviceAddress: String?, peerNodeNum: Long): Boolean {
        if (deviceAddress.isNullOrBlank()) return false
        val peer = peerNodeNum and 0xFFFF_FFFFL
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(keyForDirectThread(deviceAddress, peer), false)
    }

    fun setDirectThreadMuted(context: Context, deviceAddress: String?, peerNodeNum: Long, muted: Boolean) {
        if (deviceAddress.isNullOrBlank()) return
        val peer = peerNodeNum and 0xFFFF_FFFFL
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(keyForDirectThread(deviceAddress, peer), muted)
            .apply()
    }

    private fun keyFor(deviceAddress: String, channelIndex: Int): String {
        val normalized = deviceAddress.trim().lowercase(Locale.ROOT)
        val safe = normalized.replace(Regex("[^a-z0-9:_-]"), "_")
        return "${KEY_PREFIX}${safe}_$channelIndex"
    }

    private fun keyForDirectThread(deviceAddress: String, peerNodeNum: Long): String {
        val normalized = deviceAddress.trim().lowercase(Locale.ROOT)
        val safe = normalized.replace(Regex("[^a-z0-9:_-]"), "_")
        return "${KEY_PREFIX}${safe}_dm_$peerNodeNum"
    }
}
