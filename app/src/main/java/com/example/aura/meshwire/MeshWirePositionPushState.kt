package com.example.aura.meshwire

/**
 * Снимок [Config.PositionConfig] для UI и `AdminMessage.set_config` → `Config.position` (поле 2).
 */
data class MeshWirePositionPushState(
    /** [PositionConfig.position_broadcast_secs] — период трансляции позиции, сек. */
    val positionBroadcastSecs: UInt,
    /** [PositionConfig.position_broadcast_smart_enabled] — «умная позиция». */
    val positionBroadcastSmartEnabled: Boolean,
    /** [PositionConfig.fixed_position] — фиксированные координаты. */
    val fixedPosition: Boolean,
    /**
     * [PositionConfig.gps_update_interval] — как часто опрашивать GPS, сек.;
     * 0 = по умолчанию прошивки (часто 30 с); [UInt.MAX_VALUE] — только при загрузке.
     */
    val gpsUpdateIntervalSecs: UInt,
    /** [PositionConfig.position_flags] — битовая маска [PositionFlags]. */
    val positionFlags: UInt,
    val rxGpio: UInt,
    val txGpio: UInt,
    val gpsEnGpio: UInt,
    /** [PositionConfig.broadcast_smart_minimum_distance] — метры. */
    val broadcastSmartMinimumDistance: UInt,
    /** [PositionConfig.broadcast_smart_minimum_interval_secs]. */
    val broadcastSmartMinimumIntervalSecs: UInt,
    /** [PositionConfig.gps_mode], wire-ordinal GpsMode. */
    val gpsModeWire: Int,
) {
    companion object {
        fun initial(): MeshWirePositionPushState = MeshWirePositionPushState(
            positionBroadcastSecs = 3600u,
            positionBroadcastSmartEnabled = true,
            fixedPosition = false,
            gpsUpdateIntervalSecs = 0u,
            positionFlags = 811u,
            rxGpio = 0u,
            txGpio = 0u,
            gpsEnGpio = 0u,
            broadcastSmartMinimumDistance = 100u,
            broadcastSmartMinimumIntervalSecs = 120u,
            gpsModeWire = 2,
        )
    }
}

/** [Config.PositionConfig.GpsMode] */
object MeshWireGpsModeOptions {
    val ALL: List<Pair<Int, String>> = listOf(
        0 to "DISABLED",
        1 to "ENABLED",
        2 to "NOT_PRESENT",
    )

    fun findByWire(w: Int): Pair<Int, String> =
        ALL.firstOrNull { it.first == w } ?: ALL[2]
}
