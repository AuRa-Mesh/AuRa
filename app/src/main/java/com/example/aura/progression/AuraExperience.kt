package com.example.aura.progression

import android.content.Context
import com.example.aura.app.AppUptimeTracker
import com.example.aura.preferences.NodeScopedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Прогрессия Aura по **node id**: суммарные ОП и уровень в Room ([HallOfFameRepository]),
 * курсор аптайма — в prefs ([NodeScopedStorage]).
 *
 * Линейно: **100 ОП на уровень** — `level = totalXp / 100`, прогресс внутри уровня `totalXp % 100`.
 */
object AuraExperience {

    private const val PREFS = "aura_experience_v1"
    private const val KEY_XP_UPTIME_CURSOR_MS = "xp_uptime_cursor_ms_v1"

    private const val MS_PER_HOUR: Long = 3_600_000L
    /** Защита от аномально большого дельта за один проход синхронизации. */
    private const val MAX_HOURS_PER_SYNC: Long = 24_000L

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun notifyRevision() {
        _revision.value = _revision.value + 1L
    }

    /** Вызывается из [HallOfFameRepository] при изменении ОП/медалей — обновить подписчиков UI. */
    fun notifyStatsChanged() {
        notifyRevision()
    }

    /**
     * @param totalXp суммарные ОП из Room.
     * @return Triple(уровень = totalXp/100, ОП внутри текущего сегмента, ОП до следующего уровня = 100)
     */
    fun levelProgressFromTotalXp(totalXp: Long): Triple<Int, Long, Long> {
        val xp = totalXp.coerceAtLeast(0L)
        val seg = HallOfFameRepository.XP_PER_LEVEL
        val level = (xp / seg).toInt().coerceIn(0, 999_999)
        val into = xp % seg
        return Triple(level, into, seg)
    }

    /** Уровень по суммарным ОП (0 при 0…99 ОП, 1 при 100…199 и т.д.). */
    fun levelFromTotalXp(totalXp: Long): Int = levelProgressFromTotalXp(totalXp).first

    data class Snapshot(
        val lifetimeExperience: Long,
        val level: Int,
        val experienceIntoLevel: Long,
        val experienceNeededForNext: Long,
        val levelProgress: Float,
    )

    fun snapshot(ctx: Context): Snapshot {
        val life = HallOfFameRepository.totalXpBlocking(ctx.applicationContext)
        val (_, into, need) = levelProgressFromTotalXp(life)
        val prog = (into.toFloat() / need.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        return Snapshot(
            lifetimeExperience = life,
            level = levelFromTotalXp(life),
            experienceIntoLevel = into,
            experienceNeededForNext = need,
            levelProgress = prog,
        )
    }

    /**
     * За каждый полный час [AppUptimeTracker] начисляет ОП через [HallOfFameRepository.onUptimeHours]
     * (5 ОП/ч по ТЗ). Вызывать после [HallOfFameRepository.install] и сборки БД.
     */
    fun syncExperienceFromAppUptime(ctx: Context) {
        val appCtx = ctx.applicationContext
        val totalUptime = AppUptimeTracker.uptimeMs()
        val key = NodeScopedStorage.scopedKey(appCtx, KEY_XP_UPTIME_CURSOR_MS)
        val last = prefs(appCtx).getLong(key, 0L)
        if (totalUptime <= last) return
        val deltaMs = totalUptime - last
        var hours = deltaMs / MS_PER_HOUR
        if (hours <= 0L) return
        hours = hours.coerceAtMost(MAX_HOURS_PER_SYNC)
        runBlocking {
            HallOfFameRepository.onUptimeHours(appCtx, hours)
        }
        prefs(appCtx).edit().putLong(key, last + hours * MS_PER_HOUR).apply()
    }

    fun reconcile(ctx: Context) {
        HallOfFameRepository.scheduleDerivedCountersRefresh(ctx.applicationContext)
        notifyRevision()
    }
}
