package com.example.aura.progression

import android.content.Context
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.vip.VipStatusStore

/**
 * Локальные подсказки для полоски медалей в **чужом** профиле — только то, что уже есть в памяти
 * / SharedPreferences от **пассивного** трафика (без отдельных запросов «достижений» по сети).
 *
 * - [HallOfFameKeys.ENG_TOTAL_XP_MIRROR] — самоотчёт пира из VIP-broadcast (поле 6), см. [VipStatusStore].
 * - [HallOfFameKeys.SIG_UPTIME_APP_MS] — аптайм приложения, если узел прислал его по Aura и запись свежая.
 *
 * Полоски категорий (иконки и звёзды) при наличии кэша берутся из полей 7–10 того же VIP-broadcast
 * ([VipStatusStore.peerHallOfFameCategoryCounts]) в UI [com.example.aura.ui.components.ProfileProgressionTrackIconsAround].
 *
 * Остальные счётчики в карту не попадают (0): для них трек остаётся приглушённым до появления данных.
 */
object PeerProfileHallOfFameHints {

    /** Не показываем чужой аптайм приложения старше этого окна (сек). */
    private const val PEER_UPTIME_MAX_AGE_SEC: Long = 7L * 24L * 60L * 60L

    fun buildMedalStats(context: Context, node: MeshWireNodeSummary): Map<String, Long> {
        VipStatusStore.ensureLoaded(context)
        val dest = (node.nodeNum and 0xFFFF_FFFFL).toUInt()
        val out = LinkedHashMap<String, Long>()
        val xp = VipStatusStore.peerLifetimeExperienceHint(dest)
        if (xp > 0L) out[HallOfFameKeys.ENG_TOTAL_XP_MIRROR] = xp

        val upSec = node.peerReportedUptimeSec
        val recv = node.peerUptimeReceivedEpochSec
        val nowSec = System.currentTimeMillis() / 1000L
        if (upSec != null && upSec > 0L && recv != null && recv > 0L && nowSec - recv <= PEER_UPTIME_MAX_AGE_SEC) {
            out[HallOfFameKeys.SIG_UPTIME_APP_MS] = upSec * 1000L
        }
        return out
    }
}
