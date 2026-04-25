package com.example.aura.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.aura.preferences.NodeScopedStorage

/**
 * Накопленное время работы процесса приложения по **node id** (отдельные ключи в [PREFS]).
 */
object AppUptimeTracker {
    private const val PREFS = "aura_app_uptime_prefs"
    private const val KEY_BANKED_MS = "banked_ms"
    private const val KEY_MESH_UPTIME_RECOVERY_EXHAUSTED = "mesh_uptime_recovery_exhausted_v1"
    private const val CHECKPOINT_INTERVAL_MS = 15_000L

    private const val MESH_UPTIME_RECOVERY_THRESHOLD_MS: Long = 120_000L

    private val lock = Any()
    private var appContext: Context? = null
    private var bankedMs: Long = 0L
    private var sessionStartElapsed: Long = 0L
    private var initialized = false
    private var pendingMeshUptimeSec: Long? = null

    /** Текущий узел, для которого в RAM накоплен [bankedMs]. */
    private var cachedNodeKey: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val periodicCheckpoint = object : Runnable {
        override fun run() {
            flushToDisk()
            handler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
        }
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sk(ctx: Context, base: String) = NodeScopedStorage.scopedKey(ctx, base)

    private fun migrateFlatBanked(ctx: Context) {
        val p = prefs(ctx)
        val s = sk(ctx, KEY_BANKED_MS)
        if (p.contains(s)) return
        if (!p.contains(KEY_BANKED_MS)) return
        p.edit().putLong(s, p.getLong(KEY_BANKED_MS, 0L)).remove(KEY_BANKED_MS).apply()
    }

    private fun migrateFlatMeshExhausted(ctx: Context) {
        val p = prefs(ctx)
        val s = sk(ctx, KEY_MESH_UPTIME_RECOVERY_EXHAUSTED)
        if (p.contains(s)) return
        if (!p.contains(KEY_MESH_UPTIME_RECOVERY_EXHAUSTED)) return
        p.edit()
            .putBoolean(s, p.getBoolean(KEY_MESH_UPTIME_RECOVERY_EXHAUSTED, false))
            .remove(KEY_MESH_UPTIME_RECOVERY_EXHAUSTED)
            .apply()
    }

    /**
     * Если сменился node id — сохраняем накопленное время предыдущего узла и подгружаем счётчик нового.
     */
    private fun ensureNodeIdentity(ctx: Context) {
        val nk = NodeScopedStorage.nodeKey(ctx)
        if (nk == cachedNodeKey) return
        if (cachedNodeKey.isNotEmpty()) {
            val now = SystemClock.elapsedRealtime()
            if (now >= sessionStartElapsed) {
                bankedMs += (now - sessionStartElapsed)
            }
            sessionStartElapsed = now
            val p = prefs(ctx)
            p.edit().putLong(NodeScopedStorage.scopedStorageKey(KEY_BANKED_MS, cachedNodeKey), bankedMs).apply()
        }
        cachedNodeKey = nk
        migrateFlatBanked(ctx)
        bankedMs = prefs(ctx).getLong(sk(ctx, KEY_BANKED_MS), 0L)
        sessionStartElapsed = SystemClock.elapsedRealtime()
    }

    fun init(application: Application) {
        synchronized(lock) {
            if (initialized) return
            val ctx = application.applicationContext
            appContext = ctx
            migrateFlatBanked(ctx)
            cachedNodeKey = NodeScopedStorage.nodeKey(ctx)
            bankedMs = prefs(ctx).getLong(sk(ctx, KEY_BANKED_MS), 0L)
            sessionStartElapsed = SystemClock.elapsedRealtime()
            initialized = true
            pendingMeshUptimeSec?.let { sec ->
                pendingMeshUptimeSec = null
                applyMeshRecoveredUptimeSecLocked(sec)
            }
        }
        flushToDisk()
        application.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {}

            override fun onLowMemory() {
                flushToDisk()
            }

            override fun onTrimMemory(level: Int) {
                when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
                    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> flushToDisk()
                }
            }
        })
        handler.postDelayed(periodicCheckpoint, CHECKPOINT_INTERVAL_MS)
    }

    fun uptimeMs(): Long {
        synchronized(lock) {
            if (!initialized) return 0L
            return uptimeMsLocked()
        }
    }

    fun checkpoint() {
        flushToDisk()
    }

    fun wantsMeshUptimeRecovery(context: Context): Boolean {
        migrateFlatMeshExhausted(context)
        val prefs = prefs(context)
        if (prefs.getBoolean(sk(context, KEY_MESH_UPTIME_RECOVERY_EXHAUSTED), false)) return false
        synchronized(lock) {
            if (!initialized) return false
            return uptimeMsLocked() < MESH_UPTIME_RECOVERY_THRESHOLD_MS
        }
    }

    fun markMeshUptimeRecoveryExhausted(context: Context) {
        migrateFlatMeshExhausted(context)
        prefs(context).edit().putBoolean(sk(context, KEY_MESH_UPTIME_RECOVERY_EXHAUSTED), true).apply()
    }

    fun applyMeshRecoveredUptimeSec(context: Context, totalSec: Long) {
        if (totalSec <= 0L) return
        synchronized(lock) {
            if (!initialized) {
                pendingMeshUptimeSec = maxOf(pendingMeshUptimeSec ?: 0L, totalSec)
                return
            }
            applyMeshRecoveredUptimeSecLocked(totalSec)
        }
        flushToDisk()
    }

    private fun applyMeshRecoveredUptimeSecLocked(totalSec: Long) {
        val recoveredMs = totalSec.coerceIn(0L, Long.MAX_VALUE / 1000L) * 1000L
        val current = uptimeMsLocked()
        if (recoveredMs <= current) return
        val now = SystemClock.elapsedRealtime()
        if (now < sessionStartElapsed) {
            sessionStartElapsed = now
        } else {
            bankedMs += (now - sessionStartElapsed)
            sessionStartElapsed = now
        }
        bankedMs = maxOf(bankedMs, recoveredMs)
    }

    private fun uptimeMsLocked(): Long {
        val now = SystemClock.elapsedRealtime()
        if (now < sessionStartElapsed) {
            sessionStartElapsed = now
            return bankedMs
        }
        return bankedMs + (now - sessionStartElapsed)
    }

    private fun flushToDisk() {
        var ctx: Context? = null
        synchronized(lock) {
            if (!initialized) return
            val c = appContext ?: return
            ensureNodeIdentity(c)
            val now = SystemClock.elapsedRealtime()
            if (now < sessionStartElapsed) {
                sessionStartElapsed = now
            } else {
                val delta = now - sessionStartElapsed
                if (delta > 0) {
                    bankedMs += delta
                    sessionStartElapsed = now
                }
            }
            prefs(c).edit()
                .putLong(sk(c, KEY_BANKED_MS), bankedMs)
                .apply()
            ctx = c
        }
    }
}
