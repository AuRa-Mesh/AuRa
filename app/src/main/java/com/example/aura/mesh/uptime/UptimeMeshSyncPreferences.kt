package com.example.aura.mesh.uptime

import android.content.Context

/**
 * Расписание синхронизации аптайма: успешная отправка не чаще чем раз в 3 ч;
 * при ошибке / NACK — режим повтора каждые 10 минут до успеха.
 */
object UptimeMeshSyncPreferences {

    private const val PREFS = "aura_uptime_mesh_sync"
    private const val KEY_LAST_SUCCESS_WALL_MS = "last_success_wall_ms"
    private const val KEY_AGGRESSIVE_RETRY = "aggressive_retry"
    private const val KEY_LAST_ATTEMPT_WALL_MS = "last_attempt_wall_ms"

    private const val THREE_HOURS_MS = 3L * 60L * 60L * 1000L
    private const val TEN_MIN_MS = 10L * 60L * 1000L

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun shouldAttemptSend(ctx: Context): Boolean {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val lastOk = p.getLong(KEY_LAST_SUCCESS_WALL_MS, 0L)
        val aggressive = p.getBoolean(KEY_AGGRESSIVE_RETRY, false)
        val lastAttempt = p.getLong(KEY_LAST_ATTEMPT_WALL_MS, 0L)
        return if (aggressive) {
            now - lastAttempt >= TEN_MIN_MS
        } else {
            now - lastOk >= THREE_HOURS_MS
        }
    }

    fun markAttemptNow(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_LAST_ATTEMPT_WALL_MS, System.currentTimeMillis()).apply()
    }

    fun markSendSuccess(ctx: Context) {
        val now = System.currentTimeMillis()
        prefs(ctx).edit()
            .putLong(KEY_LAST_SUCCESS_WALL_MS, now)
            .putBoolean(KEY_AGGRESSIVE_RETRY, false)
            .apply()
    }

    fun markSendFailure(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean(KEY_AGGRESSIVE_RETRY, true)
            .apply()
    }
}
