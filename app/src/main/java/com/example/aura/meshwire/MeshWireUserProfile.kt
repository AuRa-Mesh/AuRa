package com.example.aura.meshwire

/** Данные пользователя с ноды (protobuf User + при необходимости DeviceMetadata). */
data class MeshWireNodeUserProfile(
    val longName: String,
    val shortName: String,
    val hardwareModel: String,
    /** [DeviceMetadata.firmware_version] при want_config. */
    val firmwareVersion: String? = null,
    /** [MyNodeInfo.pio_env] — PlatformIO target (как в типичном mesh-клиенте). */
    val pioEnv: String? = null,
)
