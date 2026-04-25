package com.example.aura.meshwire

/**
 * Состояние [ModuleConfig.MQTTConfig] для UI и записи через Admin.
 *
 * [configOkToMqtt] в протоколе mesh относится к **LoRaConfig.config_ok_to_mqtt** (bool, поле 105),
 * а не к `MQTTConfig` — его показываем на экране MQTT и пишем через `AdminMessage.set_config { lora }`.
 */
data class MeshWireMqttPushState(
    val enabled: Boolean = false,
    val address: String = "mqtt.meshtastic.org",
    val username: String = "meshdev",
    val password: String = "",
    val encryptionEnabled: Boolean = true,
    val jsonEnabled: Boolean = false,
    val tlsEnabled: Boolean = false,
    val root: String = "msh",
    val proxyToClientEnabled: Boolean = false,
    val mapReportingEnabled: Boolean = false,
    val mapPublishIntervalSecs: UInt = 0u,
    val mapPositionPrecision: UInt = 32u,
    val mapShouldReportLocation: Boolean = false,
    /** См. [MeshWireMqttPushState] KDoc — значение с ноды из LoRa, для отображения на вкладке MQTT. */
    val configOkToMqtt: Boolean = false,
) {
    companion object {
        fun initial() = MeshWireMqttPushState()
    }
}
