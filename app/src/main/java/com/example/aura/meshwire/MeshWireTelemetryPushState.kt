package com.example.aura.meshwire

/**
 * Состояние [ModuleConfig.TelemetryConfig] для UI и записи через Admin.
 */
data class MeshWireTelemetryPushState(
    val deviceUpdateIntervalSecs: UInt = 300u,
    val environmentUpdateIntervalSecs: UInt = 300u,
    val environmentMeasurementEnabled: Boolean = false,
    val environmentScreenEnabled: Boolean = false,
    val environmentDisplayFahrenheit: Boolean = false,
    val airQualityEnabled: Boolean = false,
    val airQualityIntervalSecs: UInt = 300u,
    val powerMeasurementEnabled: Boolean = false,
    val powerUpdateIntervalSecs: UInt = 300u,
    val powerScreenEnabled: Boolean = false,
    val healthMeasurementEnabled: Boolean = false,
    val healthUpdateIntervalSecs: UInt = 300u,
    val healthScreenEnabled: Boolean = false,
    val deviceTelemetryEnabled: Boolean = false,
    val airQualityScreenEnabled: Boolean = false,
) {
    companion object {
        fun initial() = MeshWireTelemetryPushState()
    }
}
