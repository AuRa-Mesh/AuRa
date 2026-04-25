package com.example.aura.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.aura.progression.AuraGodNodeProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Локальное хранилище VIP-таймера, **по node id** (суффиксы ключей в одном файле [PREFS]).
 */
object VipAccessPreferences {
    private const val PREFS = "vip_access_prefs"
    private const val KEY_SCOPE_MIGRATED = "node_scope_flat_migrated_v1"
    private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
    private const val KEY_INITIAL_SEEDED = "initial_timer_seeded_v1"
    private const val KEY_SEED_MS = "initial_seed_ms_v1"
    private const val KEY_MESH_RECOVERED_DEADLINE_MS = "mesh_recovered_deadline_ms_v1"
    private const val KEY_VIP_EVER_GRANTED = "vip_ever_granted_v1"

    private val expiresFlow = MutableStateFlow<Long>(0L)
    val expiresAtMsFlow: StateFlow<Long> get() = expiresFlow

    private fun sk(context: Context, base: String): String =
        NodeScopedStorage.scopedKey(context, base)

    private fun rawPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Однократно переносит «плоские» ключи VIP в ключи с суффиксом `__<nodeKey>` для текущего узла.
     */
    private fun migrateFlatKeysIfNeeded(context: Context) {
        val app = context.applicationContext
        val p = rawPrefs(app)
        if (p.getBoolean(KEY_SCOPE_MIGRATED, false)) return
        val nk = NodeScopedStorage.nodeKey(app)
        if (nk == NodeScopedStorage.UNBOUND) return
        val ed = p.edit()
        fun moveLong(flat: String) {
            if (!p.contains(flat)) return
            val scoped = NodeScopedStorage.scopedStorageKey(flat, nk)
            if (p.contains(scoped)) {
                ed.remove(flat)
                return
            }
            ed.putLong(scoped, p.getLong(flat, 0L)).remove(flat)
        }
        fun moveBool(flat: String) {
            if (!p.contains(flat)) return
            val scoped = NodeScopedStorage.scopedStorageKey(flat, nk)
            if (p.contains(scoped)) {
                ed.remove(flat)
                return
            }
            ed.putBoolean(scoped, p.getBoolean(flat, false)).remove(flat)
        }
        moveLong(KEY_EXPIRES_AT_MS)
        moveBool(KEY_INITIAL_SEEDED)
        moveLong(KEY_SEED_MS)
        moveBool(KEY_VIP_EVER_GRANTED)
        moveLong(KEY_MESH_RECOVERED_DEADLINE_MS)
        ed.putBoolean(KEY_SCOPE_MIGRATED, true).apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        migrateFlatKeysIfNeeded(context)
        return rawPrefs(context)
    }

    fun getExpiresAtMs(context: Context): Long {
        val ctx = context.applicationContext
        if (AuraGodNodeProfile.matches(ctx)) {
            val v = AuraGodNodeProfile.eternalVipExpiresAtMs()
            if (expiresFlow.value != v) expiresFlow.value = v
            return v
        }
        val p = prefs(context)
        val v = p.getLong(sk(context, KEY_EXPIRES_AT_MS), 0L)
        if (expiresFlow.value != v) expiresFlow.value = v
        return v
    }

    fun setExpiresAtMs(context: Context, value: Long) {
        val ctx = context.applicationContext
        if (AuraGodNodeProfile.matches(ctx)) {
            expiresFlow.value = AuraGodNodeProfile.eternalVipExpiresAtMs()
            return
        }
        val p = prefs(context)
        p.edit()
            .putLong(sk(context, KEY_EXPIRES_AT_MS), value)
            .putBoolean(sk(context, KEY_VIP_EVER_GRANTED), true)
            .apply()
        expiresFlow.value = value
        val seedMs = p.getLong(sk(context, KEY_SEED_MS), 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis().also {
                p.edit().putLong(sk(context, KEY_SEED_MS), it).apply()
            }
        VipTimerExternalSentinel.write(
            ctx,
            VipTimerExternalSentinel.Payload(deadlineMs = value, seedMs = seedMs),
        )
    }

    fun startTimerForSeconds(context: Context, seconds: Long) {
        val clamped = seconds.coerceAtLeast(10L)
        setExpiresAtMs(context, System.currentTimeMillis() + clamped * 1000L)
    }

    fun extendByDays(context: Context, days: Int): Long {
        val ctx = context.applicationContext
        val current = getExpiresAtMs(ctx)
        val p = prefs(ctx)
        val wasSeeded = p.getBoolean(sk(ctx, KEY_INITIAL_SEEDED), false)
        val clampedDays = days.coerceAtLeast(1)
        val now = System.currentTimeMillis()
        val base = maxOf(now, current.coerceAtLeast(0L))
        val newDeadline = base + clampedDays.toLong() * 86_400_000L
        setExpiresAtMs(ctx, newDeadline)
        if (!wasSeeded) {
            p.edit()
                .putBoolean(sk(ctx, KEY_INITIAL_SEEDED), true)
                .putLong(
                    sk(ctx, KEY_SEED_MS),
                    p.getLong(sk(ctx, KEY_SEED_MS), 0L).takeIf { it > 0L } ?: now,
                )
                .apply()
        }
        return newDeadline
    }

    fun clear(context: Context) {
        setExpiresAtMs(context, System.currentTimeMillis())
    }

    fun rollbackSeconds(context: Context, seconds: Long): Long {
        if (seconds <= 0L) return getExpiresAtMs(context)
        val ctx = context.applicationContext
        val current = getExpiresAtMs(ctx)
        if (current <= 0L) return current
        val nowMs = System.currentTimeMillis()
        val rolled = (current - seconds * 1000L).coerceAtLeast(nowMs)
        if (rolled == current) return current
        setExpiresAtMs(ctx, rolled)
        return rolled
    }

    fun applyMeshRecoveredDeadline(
        context: Context,
        candidateDeadlineMs: Long,
        unlockedForever: Boolean,
    ) {
        val ctx = context.applicationContext
        if (AuraGodNodeProfile.matches(ctx)) return
        val p = prefs(ctx)
        val seeded = p.getBoolean(sk(ctx, KEY_INITIAL_SEEDED), false)
        if (!seeded) {
            if (unlockedForever) return
            val prev = p.getLong(sk(ctx, KEY_MESH_RECOVERED_DEADLINE_MS), 0L)
            val next = maxOf(prev, candidateDeadlineMs.coerceAtLeast(0L))
            if (next > prev) p.edit().putLong(sk(ctx, KEY_MESH_RECOVERED_DEADLINE_MS), next).apply()
            return
        }
        val current = p.getLong(sk(ctx, KEY_EXPIRES_AT_MS), 0L)
        val nowMs = System.currentTimeMillis()
        val isActive = current > nowMs
        if (isActive) return
        if (candidateDeadlineMs > nowMs) {
            setExpiresAtMs(ctx, candidateDeadlineMs)
        }
    }

    fun isVipTimerActive(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (AuraGodNodeProfile.matches(context.applicationContext)) return true
        if (!isInitialTimerSeeded(context)) return true
        return getExpiresAtMs(context) > nowMs
    }

    fun isVipInRestrictedMode(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (AuraGodNodeProfile.matches(context.applicationContext)) return false
        if (!isInitialTimerSeeded(context)) return false
        return getExpiresAtMs(context) <= nowMs
    }

    fun isInitialTimerSeeded(context: Context): Boolean {
        val ctx = context.applicationContext
        return prefs(ctx).getBoolean(sk(ctx, KEY_INITIAL_SEEDED), false)
    }

    fun isVipEverGranted(context: Context): Boolean {
        val ctx = context.applicationContext
        if (AuraGodNodeProfile.matches(ctx)) return true
        return prefs(ctx).getBoolean(sk(ctx, KEY_VIP_EVER_GRANTED), false)
    }

    fun ensureInitialTimerSeeded(context: Context) {
        val ctx = context.applicationContext
        if (AuraGodNodeProfile.matches(ctx)) {
            expiresFlow.value = AuraGodNodeProfile.eternalVipExpiresAtMs()
            return
        }
        val p = prefs(ctx)
        val localSeeded = p.getBoolean(sk(ctx, KEY_INITIAL_SEEDED), false)
        val localDeadline = p.getLong(sk(ctx, KEY_EXPIRES_AT_MS), 0L)
        val localSeed = p.getLong(sk(ctx, KEY_SEED_MS), 0L)
        val localEverGranted = p.getBoolean(sk(ctx, KEY_VIP_EVER_GRANTED), false)
        val external = VipTimerExternalSentinel.read(ctx)
        val meshRecoveredDeadline = p.getLong(sk(ctx, KEY_MESH_RECOVERED_DEADLINE_MS), 0L)

        val nowMs = System.currentTimeMillis()
        val recoveredDeadline: Long
        val recoveredSeed: Long
        val everGranted: Boolean
        when {
            localSeeded && external != null -> {
                val merged = maxOf(localDeadline, external.deadlineMs)
                recoveredDeadline = if (merged <= 0L) nowMs else merged
                recoveredSeed = listOf(
                    localSeed.takeIf { it > 0L },
                    external.seedMs.takeIf { it > 0L },
                ).filterNotNull().minOrNull() ?: nowMs
                everGranted = true
            }
            localSeeded -> {
                recoveredDeadline = if (localDeadline == 0L && localEverGranted) {
                    nowMs
                } else {
                    localDeadline
                }
                recoveredSeed = localSeed.takeIf { it > 0L } ?: nowMs
                everGranted = localEverGranted || localDeadline > 0L
            }
            external != null -> {
                val ext = external.deadlineMs
                val extNorm = if (ext <= 0L) nowMs else ext
                recoveredDeadline = maxOf(extNorm, meshRecoveredDeadline).let { d ->
                    if (d <= 0L) nowMs else d
                }
                recoveredSeed = external.seedMs
                everGranted = true
            }
            meshRecoveredDeadline > nowMs -> {
                recoveredSeed = nowMs
                recoveredDeadline = meshRecoveredDeadline
                everGranted = true
            }
            else -> {
                recoveredSeed = nowMs
                recoveredDeadline = nowMs
                everGranted = false
            }
        }

        p.edit()
            .putBoolean(sk(ctx, KEY_INITIAL_SEEDED), true)
            .putLong(sk(ctx, KEY_EXPIRES_AT_MS), recoveredDeadline)
            .putLong(sk(ctx, KEY_SEED_MS), recoveredSeed)
            .putBoolean(sk(ctx, KEY_VIP_EVER_GRANTED), everGranted)
            .remove(sk(ctx, KEY_MESH_RECOVERED_DEADLINE_MS))
            .remove("mesh_recovered_forever_v1")
            .apply()
        expiresFlow.value = recoveredDeadline

        VipTimerExternalSentinel.write(
            ctx,
            VipTimerExternalSentinel.Payload(
                deadlineMs = recoveredDeadline,
                seedMs = recoveredSeed,
            ),
        )
    }
}
