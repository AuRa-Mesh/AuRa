package com.example.aura.meshwire

/**
 * Снимок настроек из бинарного DeviceProfile ([MeshWireDeviceProfileWireParser]) или иных источников.
 */
data class MeshWireImportedConfig(
    val longName: String? = null,
    val shortName: String? = null,
    val channelsFromUrl: MeshWireChannelsSyncResult? = null,
    val device: MeshWireDevicePushState? = null,
    val lora: MeshWireLoRaPushState? = null,
    val position: MeshWirePositionPushState? = null,
    val rootFixedPosition: MeshWirePositionPushState? = null,
    val mqtt: MeshWireMqttPushState? = null,
    val externalNotification: MeshWireExternalNotificationPushState? = null,
    val telemetry: MeshWireTelemetryPushState? = null,
    val security: MeshWireSecurityPushState? = null,
    /** Широта, долгота, высота (м), как в protobuf Position. */
    val fixedPositionFromProfile: Triple<Double, Double, Int?>? = null,
)
