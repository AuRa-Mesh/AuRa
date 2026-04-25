package com.example.aura.meshwire

/**
 * Интервалы как в [org.meshwire.feature.settings.util.FixedUpdateIntervals] /
 * [IntervalConfiguration] официального MeshWire Android (PositionConfigScreen).
 */
object MeshWireFixedIntervals {

    /**
     * Все значения [FixedUpdateIntervals] из Meshtastic-Android (как `fromValue` в прошивке/UI).
     */
    val ALL_KNOWN_SECONDS: Set<Long> = setOf(
        0L,
        1L, 2L, 3L, 4L, 5L, 8L, 10L, 15L, 20L, 30L, 40L, 45L, 60L, 80L, 90L, 120L, 300L, 600L, 900L,
        1800L, 3600L, 7200L, 10800L, 14400L, 18000L, 21600L, 43200L, 64800L, 86400L,
        129600L, 172800L, 259200L,
        Int.MAX_VALUE.toLong(),
    )

    data class SecLabel(val seconds: UInt, val labelRu: String)

    /** [IntervalConfiguration.POSITION_BROADCAST] */
    val POSITION_BROADCAST: List<SecLabel> = listOf(
        SecLabel(0u, "Сброс (по умолчанию)"),
        SecLabel(60u, "1 мин"),
        SecLabel(90u, "1,5 мин"),
        SecLabel(300u, "5 мин"),
        SecLabel(900u, "15 мин"),
        SecLabel(3600u, "1 ч"),
        SecLabel(7200u, "2 ч"),
        SecLabel(10800u, "3 ч"),
        SecLabel(14400u, "4 ч"),
        SecLabel(18000u, "5 ч"),
        SecLabel(21600u, "6 ч"),
        SecLabel(43200u, "12 ч"),
        SecLabel(64800u, "18 ч"),
        SecLabel(86400u, "24 ч"),
        SecLabel(129600u, "36 ч"),
        SecLabel(172800u, "48 ч"),
        SecLabel(259200u, "72 ч"),
    )

    /** [IntervalConfiguration.SMART_BROADCAST_MINIMUM] */
    val SMART_BROADCAST_MINIMUM: List<SecLabel> = listOf(
        SecLabel(15u, "15 с"),
        SecLabel(30u, "30 с"),
        SecLabel(45u, "45 с"),
        SecLabel(60u, "1 мин"),
        SecLabel(300u, "5 мин"),
        SecLabel(600u, "10 мин"),
        SecLabel(900u, "15 мин"),
        SecLabel(1800u, "30 мин"),
        SecLabel(3600u, "1 ч"),
    )

    /** [IntervalConfiguration.GPS_UPDATE] */
    val GPS_UPDATE: List<SecLabel> = listOf(
        SecLabel(0u, "По умолчанию (~30 с)"),
        SecLabel(8u, "8 с"),
        SecLabel(20u, "20 с"),
        SecLabel(40u, "40 с"),
        SecLabel(60u, "1 мин"),
        SecLabel(80u, "80 с"),
        SecLabel(120u, "2 мин"),
        SecLabel(300u, "5 мин"),
        SecLabel(600u, "10 мин"),
        SecLabel(900u, "15 мин"),
        SecLabel(1800u, "30 мин"),
        SecLabel(3600u, "1 ч"),
        SecLabel(21600u, "6 ч"),
        SecLabel(43200u, "12 ч"),
        SecLabel(86400u, "24 ч"),
    )

    /** Как в PositionConfigScreen: `POSITION.allowedIntervals.first()` = 1 с. */
    private val POSITION_FIRST_SEC: UInt = 1u

    /**
     * Санитизация как [PositionConfigScreenCommon] (sanitizedPositionConfig).
     */
    fun sanitizeLikeMeshWireApp(state: MeshWirePositionPushState): MeshWirePositionPushState {
        var u = state
        val pb = u.positionBroadcastSecs.toLong()
        if (ALL_KNOWN_SECONDS.none { it == pb }) {
            u = u.copy(positionBroadcastSecs = POSITION_FIRST_SEC)
        }
        val sm = u.broadcastSmartMinimumIntervalSecs.toLong()
        if (ALL_KNOWN_SECONDS.none { it == sm }) {
            u = u.copy(broadcastSmartMinimumIntervalSecs = SMART_BROADCAST_MINIMUM.first().seconds)
        }
        val gps = u.gpsUpdateIntervalSecs
        val gpsL = gps.toLong()
        // «Только при загрузке» — UInt.MAX_VALUE; не входит в enum, сохраняем как в прошивке.
        if (gps != UInt.MAX_VALUE && ALL_KNOWN_SECONDS.none { it == gpsL }) {
            u = u.copy(gpsUpdateIntervalSecs = POSITION_FIRST_SEC)
        }
        return u
    }

    fun labelForBroadcast(seconds: UInt): String =
        POSITION_BROADCAST.firstOrNull { it.seconds == seconds }?.labelRu
            ?: POSITION_BROADCAST.first().labelRu

    fun labelForSmartMinimum(seconds: UInt): String =
        SMART_BROADCAST_MINIMUM.firstOrNull { it.seconds == seconds }?.labelRu
            ?: SMART_BROADCAST_MINIMUM.first().labelRu

    fun labelForGpsUpdate(seconds: UInt): String =
        GPS_UPDATE.firstOrNull { it.seconds == seconds }?.labelRu
            ?: GPS_UPDATE.first().labelRu
}
