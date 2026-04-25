package com.example.aura.meshwire

/**
 * `AdminMessage.set_module_config` с вариантом [ModuleConfig.telemetry] (поле 6).
 */
object MeshWireTelemetryToRadioEncoder {

    private fun encodeTelemetryConfigInner(state: MeshWireTelemetryPushState): ByteArray =
        MeshWireProtobufWriter().apply {
            writeUInt32Field(1, state.deviceUpdateIntervalSecs)
            writeUInt32Field(2, state.environmentUpdateIntervalSecs)
            writeBoolField(3, state.environmentMeasurementEnabled)
            writeBoolField(4, state.environmentScreenEnabled)
            writeBoolField(5, state.environmentDisplayFahrenheit)
            writeBoolField(6, state.airQualityEnabled)
            writeUInt32Field(7, state.airQualityIntervalSecs)
            writeBoolField(8, state.powerMeasurementEnabled)
            writeUInt32Field(9, state.powerUpdateIntervalSecs)
            writeBoolField(10, state.powerScreenEnabled)
            writeBoolField(11, state.healthMeasurementEnabled)
            writeUInt32Field(12, state.healthUpdateIntervalSecs)
            writeBoolField(13, state.healthScreenEnabled)
            writeBoolField(14, state.deviceTelemetryEnabled)
            writeBoolField(15, state.airQualityScreenEnabled)
        }.toByteArray()

    fun encodeModuleConfigWithTelemetry(state: MeshWireTelemetryPushState): ByteArray {
        val inner = encodeTelemetryConfigInner(state)
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(6, inner)
        }.toByteArray()
    }

    fun encodeAdminSetModuleConfigTelemetry(state: MeshWireTelemetryPushState): ByteArray {
        val moduleBytes = encodeModuleConfigWithTelemetry(state)
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(35, moduleBytes)
        }.toByteArray()
    }

    fun encodeTelemetrySetModuleConfigTransaction(
        state: MeshWireTelemetryPushState,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
    ): List<ByteArray> {
        val begin = MeshWireProtobufWriter().apply { writeBoolField(64, true) }.toByteArray()
        val commit = MeshWireProtobufWriter().apply { writeBoolField(65, true) }.toByteArray()
        return listOf(
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(begin, device),
            MeshWireLoRaToRadioEncoder.encodeAdminAppMeshToRadio(
                encodeAdminSetModuleConfigTelemetry(state),
                device,
            ),
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(commit, device),
        )
    }
}
