package com.example.aura.preferences

import android.content.Context
import java.util.Locale

object ChatChannelOrderPreferences {
    private const val PREFS = "chat_channel_order_prefs"
    private const val KEY_PREFIX = "order_"

    fun getOrderedChannelIndexes(context: Context, deviceAddress: String?): List<Int> {
        if (deviceAddress.isNullOrBlank()) return emptyList()
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyFor(deviceAddress), null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .map { it.coerceAtLeast(0) }
            .distinct()
    }

    fun setOrderedChannelIndexes(context: Context, deviceAddress: String?, orderedIndexes: List<Int>) {
        if (deviceAddress.isNullOrBlank()) return
        val serialized = orderedIndexes
            .map { it.coerceAtLeast(0) }
            .distinct()
            .joinToString(",")
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(keyFor(deviceAddress), serialized)
            .apply()
    }

    private fun keyFor(deviceAddress: String): String {
        val normalized = deviceAddress.trim().lowercase(Locale.ROOT)
        val safe = normalized.replace(Regex("[^a-z0-9:_-]"), "_")
        return "$KEY_PREFIX$safe"
    }
}

