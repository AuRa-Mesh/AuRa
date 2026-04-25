package com.example.aura.mesh.nodedb

import com.example.aura.meshwire.MeshWireGeo
import com.example.aura.meshwire.MeshWireNodeSummary

/** Публичное имя модели данных узла (совпадает с [MeshWireNodeSummary]). */
typealias MeshNode = MeshWireNodeSummary

/** Активность узла по [MeshWireNodeSummary.lastSeenEpochSec] и текущему времени. */
enum class NodePresenceLevel {
    /** ≤ 15 мин. */
    ONLINE,
    /** 15 мин … 1 ч. */
    RECENT,
    /** > 1 ч или неизвестно. */
    OFFLINE,
}

enum class BatteryTintTier {
    Good,
    Mid,
    Bad,
}

/**
 * Готовые к отображению поля вкладки «Узлы» (расчёты вне Composable / ViewModel).
 */
data class MeshNodeListRowUi(
    val node: MeshWireNodeSummary,
    val longNameBold: String,
    /** Short | Hardware | nodeId */
    val subtitleLine: String,
    val lastSeenLine: String,
    val distanceLine: String?,
    /** Азимут «с себя к узлу» для стрелки, градусы [0,360), или null. */
    val bearingDegrees: Double?,
    val relayHopsText: String?,
    /** 0f..1f для шкалы SNR. */
    val snrBarFraction: Float,
    /** «-- dB» или «-12.5 dB». */
    val snrDisplayText: String,
    val batteryPercentText: String?,
    val batteryTintTier: BatteryTintTier,
    /** «Online» или «5 мин назад». */
    val statusCompact: String,
    /** Имя яркое только для «онлайн» в пределах окна. */
    val nameBright: Boolean,
    val isSelf: Boolean,
    val presence: NodePresenceLevel,
)

object MeshNodeListRowFormatter {

    private const val PRESENCE_ONLINE_SEC = 15 * 60L
    private const val PRESENCE_RECENT_SEC = 60 * 60L

    fun presenceLevel(lastSeenEpochSec: Long?, nowEpochSec: Long): NodePresenceLevel {
        val t = lastSeenEpochSec ?: return NodePresenceLevel.OFFLINE
        if (t <= 0L) return NodePresenceLevel.OFFLINE
        val age = (nowEpochSec - t).coerceAtLeast(0L)
        return when {
            age <= PRESENCE_ONLINE_SEC -> NodePresenceLevel.ONLINE
            age <= PRESENCE_RECENT_SEC -> NodePresenceLevel.RECENT
            else -> NodePresenceLevel.OFFLINE
        }
    }

    fun formatLastSeenAbsolute(epochSec: Long?, nowEpochSec: Long): String =
        when {
            epochSec == null || epochSec <= 0L -> "Последний контакт: —"
            else -> {
                val delta = (nowEpochSec - epochSec).coerceAtLeast(0L)
                val rel = formatRelativeShort(delta)
                "Последний контакт: $rel"
            }
        }

    fun formatRelativeShort(deltaSec: Long): String =
        when {
            deltaSec < 60L -> "$deltaSec с назад"
            deltaSec < 3600L -> "${deltaSec / 60} мин назад"
            deltaSec < 86400L -> "${deltaSec / 3600} ч назад"
            else -> "${deltaSec / 86400} д назад"
        }

    fun snrToBarFraction(snrDb: Float?): Float {
        if (snrDb == null) return 0f
        return ((snrDb + 8f) / 22f).coerceIn(0f, 1f)
    }

    fun snrDisplayText(snrDb: Float?): String =
        if (snrDb == null) "-- dB" else String.format("%.1f dB", snrDb)

    fun batteryTintTier(percent: Int?): BatteryTintTier =
        when {
            percent == null -> BatteryTintTier.Mid
            percent >= 50 -> BatteryTintTier.Good
            percent >= 20 -> BatteryTintTier.Mid
            else -> BatteryTintTier.Bad
        }

    fun statusCompact(presence: NodePresenceLevel, lastSeenEpochSec: Long?, nowEpochSec: Long): String =
        when {
            presence == NodePresenceLevel.ONLINE -> "Online"
            lastSeenEpochSec == null || lastSeenEpochSec <= 0L -> "—"
            else -> formatRelativeShort((nowEpochSec - lastSeenEpochSec).coerceAtLeast(0L))
        }

    fun buildRows(
        sorted: List<MeshWireNodeSummary>,
        self: MeshWireNodeSummary?,
        nowEpochSec: Long,
    ): List<MeshNodeListRowUi> {
        return sorted.map { n ->
            val dist = MeshNodeListSorter.distanceMetersOrNull(self, n)
            val distStr = dist?.let { MeshWireGeo.formatDistanceMeters(it) }
            val heard = n.lastSeenEpochSec
            val presence = presenceLevel(heard, nowEpochSec)
            val bright = presence == NodePresenceLevel.ONLINE ||
                (self != null && (n.nodeNum and 0xFFFF_FFFFL) == (self.nodeNum and 0xFFFF_FFFFL))
            val la = self?.latitude
            val lo = self?.longitude
            val nla = n.latitude
            val nlo = n.longitude
            val bearing =
                if (la != null && lo != null && nla != null && nlo != null) {
                    MeshWireGeo.initialBearingDegrees(la, lo, nla, nlo)
                } else {
                    null
                }
            val hops = n.relayHopsCount()
            val hopsLine = hops?.let { h -> "Прыжки: $h" }
            MeshNodeListRowUi(
                node = n,
                longNameBold = n.displayLongName(),
                subtitleLine = buildString {
                    append(n.hardwareModel)
                    append(" · ")
                    append(n.nodeIdHex)
                },
                lastSeenLine = formatLastSeenAbsolute(heard, nowEpochSec),
                distanceLine = distStr?.let { "Расстояние: $it" },
                bearingDegrees = bearing,
                relayHopsText = hopsLine,
                snrBarFraction = snrToBarFraction(n.lastSnrDb),
                snrDisplayText = snrDisplayText(n.lastSnrDb),
                batteryPercentText = n.batteryPercent?.let { "$it%" },
                batteryTintTier = batteryTintTier(n.batteryPercent),
                statusCompact = statusCompact(presence, heard, nowEpochSec),
                nameBright = bright,
                isSelf = self != null && (n.nodeNum and 0xFFFF_FFFFL) == (self.nodeNum and 0xFFFF_FFFFL),
                presence = presence,
            )
        }
    }
}
