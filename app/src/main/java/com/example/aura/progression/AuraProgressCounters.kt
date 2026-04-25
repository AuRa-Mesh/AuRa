package com.example.aura.progression

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.aura.preferences.NodeScopedStorage
import java.util.concurrent.TimeUnit

/** Счётчики «Аллеи славы» и онбординга, разделённые по [NodeScopedStorage.nodeKey]. */
object AuraProgressCounters {
    private const val PREFS = "aura_progress_counters_v1"
    private const val KEY_PROFILE = "onboard_profile_ok"
    private const val KEY_AVATAR = "onboard_avatar_ok"
    private const val KEY_MAP = "onboard_map_ok"
    private const val KEY_OUT_MESSAGES = "stat_out_messages"
    private const val KEY_HOP_SUM = "stat_hop_sum"
    private const val KEY_DM_OPENS = "stat_dm_opens"
    private const val KEY_NO_GPS_MAP = "stat_no_gps_map_secs"
    private const val KEY_ESP32_CONTACTS = "stat_esp32_nodes"
    private const val KEY_M5_CONTACTS = "stat_m5_nodes"
    private const val KEY_MESH_RECOVERY = "stat_mesh_recovery_merges"
    /** Якорь для карточек «стаж установки» в аллее славы: отдельно на mesh-узел. */
    private const val KEY_LOYALTY_ANCHOR_MS = "hof_loyalty_anchor_ms_v1"
    /**
     * Однократно на устройстве: первому **привязанному** узлу даём непрерывность со дня
     * установки пакета; следующие узлы считают стаж с момента первого запроса.
     */
    private const val KEY_DEVICE_LOYALTY_SEEDED = "hof_device_loyalty_seeded_v1"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun scoped(ctx: Context, base: String) = NodeScopedStorage.scopedKey(ctx, base)

    private fun migrateFlatLongIfPresent(ctx: Context, base: String) {
        val p = prefs(ctx)
        val sk = scoped(ctx, base)
        if (p.contains(sk)) return
        if (!p.contains(base)) return
        val v = p.getLong(base, 0L)
        p.edit().putLong(sk, v).remove(base).apply()
    }

    private fun migrateFlatBoolIfPresent(ctx: Context, base: String) {
        val p = prefs(ctx)
        val sk = scoped(ctx, base)
        if (p.contains(sk)) return
        if (!p.contains(base)) return
        p.edit().putBoolean(sk, p.getBoolean(base, false)).remove(base).apply()
    }

    private fun getLong(ctx: Context, base: String): Long {
        migrateFlatLongIfPresent(ctx, base)
        return prefs(ctx).getLong(scoped(ctx, base), 0L)
    }

    private fun putLong(ctx: Context, base: String, value: Long) {
        prefs(ctx).edit().putLong(scoped(ctx, base), value).apply()
    }

    private fun getBool(ctx: Context, base: String): Boolean {
        migrateFlatBoolIfPresent(ctx, base)
        return prefs(ctx).getBoolean(scoped(ctx, base), false)
    }

    private fun putBool(ctx: Context, base: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(scoped(ctx, base), value).apply()
    }

    fun markProfileFilled(ctx: Context) {
        putBool(ctx, KEY_PROFILE, true)
    }

    fun markAvatarSet(ctx: Context) {
        putBool(ctx, KEY_AVATAR, true)
    }

    fun markMapVisited(ctx: Context) {
        putBool(ctx, KEY_MAP, true)
    }

    fun isOnboardingReady(ctx: Context): Boolean {
        return getBool(ctx, KEY_PROFILE) && getBool(ctx, KEY_AVATAR) && getBool(ctx, KEY_MAP)
    }

    /** Отмечено посещение вкладки «Карта» (онбординг / треки прогрессии). */
    fun isMapVisitMarked(ctx: Context): Boolean = getBool(ctx, KEY_MAP)

    fun incrementOutgoingMessages(ctx: Context, delta: Int = 1) {
        val base = KEY_OUT_MESSAGES
        putLong(ctx, base, getLong(ctx, base) + delta)
    }

    fun addHopObservation(ctx: Context, hops: Int) {
        if (hops <= 0) return
        val base = KEY_HOP_SUM
        putLong(ctx, base, getLong(ctx, base) + hops)
    }

    fun incrementDmOpens(ctx: Context) {
        val base = KEY_DM_OPENS
        putLong(ctx, base, getLong(ctx, base) + 1L)
    }

    fun addNoGpsMapSeconds(ctx: Context, sec: Int) {
        if (sec <= 0) return
        val base = KEY_NO_GPS_MAP
        putLong(ctx, base, getLong(ctx, base) + sec)
    }

    fun noteHardwareModelSeen(ctx: Context, hardwareModel: String) {
        val h = hardwareModel.uppercase()
        val baseEsp = KEY_ESP32_CONTACTS
        val baseM5 = KEY_M5_CONTACTS
        if (h.contains("ESP32")) {
            putLong(ctx, baseEsp, getLong(ctx, baseEsp) + 1L)
        }
        if (h.contains("M5") || h.contains("T-BEAM") || h.contains("TBEAM")) {
            putLong(ctx, baseM5, getLong(ctx, baseM5) + 1L)
        }
    }

    fun notePeerHardwareOnce(ctx: Context, nodeNumMasked: Long, hardwareModel: String) {
        val onceKey = "hw_once_${nodeNumMasked and 0xFFFF_FFFFL}"
        val p = prefs(ctx)
        val sk = scoped(ctx, onceKey)
        if (p.getBoolean(sk, false)) return
        p.edit().putBoolean(sk, true).apply()
        noteHardwareModelSeen(ctx, hardwareModel)
    }

    fun incrementMeshRecoveryMerge(ctx: Context) {
        val base = KEY_MESH_RECOVERY
        putLong(ctx, base, getLong(ctx, base) + 1L)
    }

    fun outgoingMessages(ctx: Context) = getLong(ctx, KEY_OUT_MESSAGES)
    fun hopSum(ctx: Context) = getLong(ctx, KEY_HOP_SUM)
    fun dmOpens(ctx: Context) = getLong(ctx, KEY_DM_OPENS)
    fun noGpsMapSeconds(ctx: Context) = getLong(ctx, KEY_NO_GPS_MAP)
    fun esp32Contacts(ctx: Context) = getLong(ctx, KEY_ESP32_CONTACTS)
    fun m5Contacts(ctx: Context) = getLong(ctx, KEY_M5_CONTACTS)
    fun meshRecoveryMerges(ctx: Context) = getLong(ctx, KEY_MESH_RECOVERY)

    /**
     * Полные сутки с якоря лояльности для текущего узла (карточки LOYALTY в аллее славы).
     * Не использует [PackageInfo.firstInstallTime] напрямую — только как одноразовый сид
     * для первого связанного узла на устройстве после обновления.
     */
    fun installLoyaltyDays(ctx: Context): Int {
        val anchor = loyaltyAnchorEpochMs(ctx)
        val days = TimeUnit.MILLISECONDS.toDays(
            (System.currentTimeMillis() - anchor).coerceAtLeast(0L),
        ).toInt()
        return days.coerceAtMost(10_000)
    }

    private fun loyaltyAnchorEpochMs(ctx: Context): Long {
        val app = ctx.applicationContext
        val p = prefs(app)
        val sk = scoped(app, KEY_LOYALTY_ANCHOR_MS)
        val existing = p.getLong(sk, 0L)
        if (existing > 0L) return existing
        val nk = NodeScopedStorage.nodeKey(app)
        val anchor = when {
            nk == NodeScopedStorage.UNBOUND -> System.currentTimeMillis()
            !p.getBoolean(KEY_DEVICE_LOYALTY_SEEDED, false) -> {
                p.edit().putBoolean(KEY_DEVICE_LOYALTY_SEEDED, true).apply()
                readPackageFirstInstallMs(app)
            }
            else -> System.currentTimeMillis()
        }
        p.edit().putLong(sk, anchor).apply()
        return anchor
    }

    private fun readPackageFirstInstallMs(ctx: Context): Long {
        return try {
            val pm = ctx.packageManager
            val pkg = ctx.packageName
            val first = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).firstInstallTime
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).firstInstallTime
            }
            first.coerceAtLeast(0L).takeIf { it > 0L } ?: System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }
}
