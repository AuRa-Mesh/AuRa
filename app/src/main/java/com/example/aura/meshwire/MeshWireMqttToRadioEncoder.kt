package com.example.aura.meshwire

import java.nio.charset.StandardCharsets

/**
 * `AdminMessage.set_module_config` с вариантом [ModuleConfig.mqtt].
 *
 * Поля только из `MQTTConfig`; [MeshWireMqttPushState.configOkToMqtt] сюда не входит
 * (в прошивке это `LoRaConfig.config_ok_to_mqtt`, пишется через [MeshWireLoRaToRadioEncoder]).
 */
object MeshWireMqttToRadioEncoder {

    private fun encodeMqttConfigInner(state: MeshWireMqttPushState): ByteArray =
        MeshWireProtobufWriter().apply {
            writeBoolField(1, state.enabled)
            writeLengthDelimitedField(2, state.address.toByteArray(StandardCharsets.UTF_8))
            writeLengthDelimitedField(3, state.username.toByteArray(StandardCharsets.UTF_8))
            writeLengthDelimitedField(4, state.password.toByteArray(StandardCharsets.UTF_8))
            writeBoolField(5, state.encryptionEnabled)
            writeBoolField(6, state.jsonEnabled)
            writeBoolField(7, state.tlsEnabled)
            writeLengthDelimitedField(8, state.root.toByteArray(StandardCharsets.UTF_8))
            writeBoolField(9, state.proxyToClientEnabled)
            writeBoolField(10, state.mapReportingEnabled)
            writeEmbeddedMessage(11) {
                writeUInt32Field(1, state.mapPublishIntervalSecs)
                writeUInt32Field(2, state.mapPositionPrecision)
                writeBoolField(3, state.mapShouldReportLocation)
            }
        }.toByteArray()

    /** Полный [ModuleConfig] с заполненным oneof `mqtt`. */
    fun encodeModuleConfigWithMqtt(state: MeshWireMqttPushState): ByteArray {
        val mqttInner = encodeMqttConfigInner(state)
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(1, mqttInner)
        }.toByteArray()
    }

    fun encodeAdminSetModuleConfigMqtt(state: MeshWireMqttPushState): ByteArray {
        val moduleBytes = encodeModuleConfigWithMqtt(state)
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(35, moduleBytes)
        }.toByteArray()
    }

    fun encodeMqttSetModuleConfigTransaction(
        state: MeshWireMqttPushState,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
    ): List<ByteArray> {
        val begin = MeshWireProtobufWriter().apply { writeBoolField(64, true) }.toByteArray()
        val commit = MeshWireProtobufWriter().apply { writeBoolField(65, true) }.toByteArray()
        return listOf(
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(begin, device),
            MeshWireLoRaToRadioEncoder.encodeAdminAppMeshToRadio(
                encodeAdminSetModuleConfigMqtt(state),
                device,
            ),
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(commit, device),
        )
    }
}
