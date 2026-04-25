package com.example.aura.meshwire

/**
 * `AdminMessage.set_config` с вариантом [Config.device] (поле 1 в [Config]).
 * Транзакция как у LoRa/безопасности: begin_edit → set_config → commit_edit.
 */
object MeshWireDeviceToRadioEncoder {

    private fun encodeDeviceConfigInner(state: MeshWireDevicePushState): ByteArray =
        MeshWireProtobufWriter().apply {
            writeEnumField(1, state.roleWire)
            writeUInt32Field(4, state.buttonGpio)
            writeUInt32Field(5, state.buzzerGpio)
            writeEnumField(6, state.rebroadcastModeWire)
            writeUInt32Field(7, state.nodeInfoBroadcastSecs)
            writeBoolField(8, state.doubleTapAsButtonPress)
            writeBoolField(10, state.disableTripleClick)
            writeStringField(11, state.tzdef)
            writeBoolField(12, state.ledHeartbeatDisabled)
            writeEnumField(13, state.buzzerModeWire)
        }.toByteArray()

    /** Полное тело AdminMessage: только `set_config` с вложенным Config { device { … } }. */
    fun encodeAdminSetConfigDevice(state: MeshWireDevicePushState): ByteArray {
        val deviceBytes = encodeDeviceConfigInner(state)
        val configBytes = MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(1, deviceBytes)
        }.toByteArray()
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(34, configBytes)
        }.toByteArray()
    }

    fun encodeDeviceSetConfigTransaction(
        state: MeshWireDevicePushState,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
    ): List<ByteArray> {
        val begin = MeshWireProtobufWriter().apply { writeBoolField(64, true) }.toByteArray()
        val commit = MeshWireProtobufWriter().apply { writeBoolField(65, true) }.toByteArray()
        return listOf(
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(begin, device),
            MeshWireLoRaToRadioEncoder.encodeAdminAppMeshToRadio(
                encodeAdminSetConfigDevice(state),
                device,
            ),
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(commit, device),
        )
    }
}
