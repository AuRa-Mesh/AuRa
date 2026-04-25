package com.example.aura.preferences

import android.content.Context

object ChatPollVotePreferences {
    private const val PREFS = "chat_poll_votes_prefs"
    private const val KEY_PREFIX = "poll_vote_"

    fun getSelectedOptions(context: Context, pollStableId: String): Set<Int> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyFor(pollStableId), "")
            .orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 0 }
            .toSet()
    }

    fun setSelectedOptions(context: Context, pollStableId: String, options: Set<Int>) {
        val encoded = options.sorted().joinToString(",")
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(keyFor(pollStableId), encoded)
            .apply()
    }

    private fun keyFor(stableId: String): String {
        val safeId = stableId.replace(Regex("[^A-Za-z0-9_]"), "_")
        return KEY_PREFIX + safeId
    }
}
