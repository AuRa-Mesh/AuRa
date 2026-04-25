package com.example.aura.app

import android.content.Context
import android.os.SystemClock
import com.example.aura.preferences.NodeScopedStorage

/**
 * Накопление времени в фоне по **node id** (отдельные ключи prefs на узел).
 */
object AuraBackgroundUptimeTracker {
    private const val PREFS = "aura_bg_uptime_v1"
    private const val KEY_BANKED_MS = "banked_bg_ms"
    private const val KEY_SEGMENT_START_ELAPSED = "segment_start_elapsed"
    private const val KEY_LAST_AWARDED_BG_MS = "last_awarded_bg_ms"

    private val lock = Any()
    private var segmentStartElapsed: Long = 0L

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sk(ctx: Context, base: String) = NodeScopedStorage.scopedKey(ctx, base)

    private fun migrateFlatLong(ctx: Context, base: String) {
        val p = prefs(ctx)
        val s = sk(ctx, base)
        if (p.contains(s)) return
        if (!p.contains(base)) return
        p.edit().putLong(s, p.getLong(base, 0L)).remove(base).apply()
    }

    private fun getBanked(ctx: Context): Long {
        migrateFlatLong(ctx, KEY_BANKED_MS)
        return prefs(ctx).getLong(sk(ctx, KEY_BANKED_MS), 0L)
    }

    private fun putBanked(ctx: Context, value: Long) {
        prefs(ctx).edit().putLong(sk(ctx, KEY_BANKED_MS), value).apply()
    }

    fun onAppBackgrounded(ctx: Context) {
        synchronized(lock) {
            segmentStartElapsed = SystemClock.elapsedRealtime()
            prefs(ctx).edit().putLong(sk(ctx, KEY_SEGMENT_START_ELAPSED), segmentStartElapsed).apply()
        }
    }

    fun onAppForegrounded(ctx: Context) {
        synchronized(lock) {
            val p = prefs(ctx)
            migrateFlatLong(ctx, KEY_BANKED_MS)
            migrateFlatLong(ctx, KEY_SEGMENT_START_ELAPSED)
            val segKey = sk(ctx, KEY_SEGMENT_START_ELAPSED)
            val start = segmentStartElapsed.takeIf { it > 0L }
                ?: p.getLong(segKey, 0L).takeIf { it > 0L } ?: return
            val now = SystemClock.elapsedRealtime()
            val delta = if (now >= start) (now - start).coerceAtLeast(0L) else 0L
            val banked = getBanked(ctx) + delta
            p.edit()
                .putLong(sk(ctx, KEY_BANKED_MS), banked)
                .remove(segKey)
                .apply()
            segmentStartElapsed = 0L
        }
    }

    fun totalBackgroundMs(ctx: Context): Long {
        synchronized(lock) {
            migrateFlatLong(ctx, KEY_BANKED_MS)
            migrateFlatLong(ctx, KEY_SEGMENT_START_ELAPSED)
            val p = prefs(ctx)
            val banked = p.getLong(sk(ctx, KEY_BANKED_MS), 0L)
            val storedStart = p.getLong(sk(ctx, KEY_SEGMENT_START_ELAPSED), 0L)
            val start = when {
                segmentStartElapsed > 0L -> segmentStartElapsed
                storedStart > 0L -> storedStart
                else -> 0L
            }
            if (start <= 0L) return banked
            val now = SystemClock.elapsedRealtime()
            return banked + (now - start).coerceAtLeast(0L)
        }
    }

    fun lastAwardedBackgroundMs(ctx: Context): Long {
        migrateFlatLong(ctx, KEY_LAST_AWARDED_BG_MS)
        return prefs(ctx).getLong(sk(ctx, KEY_LAST_AWARDED_BG_MS), 0L)
    }

    fun advanceAwardedBaseline(ctx: Context, addMs: Long) {
        migrateFlatLong(ctx, KEY_LAST_AWARDED_BG_MS)
        val p = prefs(ctx)
        val k = sk(ctx, KEY_LAST_AWARDED_BG_MS)
        val next = p.getLong(k, 0L) + addMs
        p.edit().putLong(k, next).apply()
    }
}
