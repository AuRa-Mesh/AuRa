package com.example.aura.meshwire

/**
 * `AdminMessage.set_config` с вариантом [Config.position] (поле 2 в [Config]).
 */
object MeshWirePositionToRadioEncoder {

    private fun encodePositionConfigInner(state: MeshWirePositionPushState): ByteArray =
        MeshWireProtobufWriter().apply {
            writeUInt32Field(1, state.positionBroadcastSecs)
            writeBoolField(2, state.positionBroadcastSmartEnabled)
            writeBoolField(3, state.fixedPosition)
            writeUInt32Field(5, state.gpsUpdateIntervalSecs)
            writeUInt32Field(7, state.positionFlags)
            writeUInt32Field(8, state.rxGpio)
            writeUInt32Field(9, state.txGpio)
            writeUInt32Field(10, state.broadcastSmartMinimumDistance)
            writeUInt32Field(11, state.broadcastSmartMinimumIntervalSecs)
            writeUInt32Field(12, state.gpsEnGpio)
            writeEnumField(13, state.gpsModeWire)
        }.toByteArray()

    fun encodeAdminSetConfigPosition(state: MeshWirePositionPushState): ByteArray {
        val positionBytes = encodePositionConfigInner(state)
        val configBytes = MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(2, positionBytes)
        }.toByteArray()
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(34, configBytes)
        }.toByteArray()
    }

    fun encodePositionSetConfigTransaction(
        state: MeshWirePositionPushState,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
    ): List<ByteArray> {
        val begin = MeshWireProtobufWriter().apply { writeBoolField(64, true) }.toByteArray()
        val commit = MeshWireProtobufWriter().apply { writeBoolField(65, true) }.toByteArray()
        return listOf(
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(begin, device),
            MeshWireLoRaToRadioEncoder.encodeAdminAppMeshToRadio(
                encodeAdminSetConfigPosition(state),
                device,
            ),
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(commit, device),
        )
    }
}
