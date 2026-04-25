package com.example.aura.meshwire

/**
 * Состояние [ModuleConfig.ExternalNotificationConfig] для UI и записи через Admin.
 * @see [module_config.proto](https://github.com/meshtastic/protobufs/blob/master/meshtastic/module_config.proto)
 */
data class MeshWireExternalNotificationPushState(
    val enabled: Boolean = false,
    val outputMs: UInt = 1000u,
    val output: UInt = 0u,
    val outputVibra: UInt = 0u,
    val outputBuzzer: UInt = 0u,
    val active: Boolean = true,
    val alertMessage: Boolean = false,
    val alertMessageVibra: Boolean = false,
    val alertMessageBuzzer: Boolean = false,
    val alertBell: Boolean = false,
    val alertBellVibra: Boolean = false,
    val alertBellBuzzer: Boolean = false,
    val usePwm: Boolean = false,
    val nagTimeout: UInt = 0u,
    val useI2sAsBuzzer: Boolean = false,
) {
    companion object {
        fun initial() = MeshWireExternalNotificationPushState()
    }
}
