package com.example.aura.meshwire

/**
 * `AdminMessage.set_module_config` с вариантом [ModuleConfig.external_notification] (поле 3).
 */
object MeshWireExternalNotificationToRadioEncoder {

    private fun encodeExternalNotificationInner(state: MeshWireExternalNotificationPushState): ByteArray =
        MeshWireProtobufWriter().apply {
            writeBoolField(1, state.enabled)
            writeUInt32Field(2, state.outputMs)
            writeUInt32Field(3, state.output)
            writeBoolField(4, state.active)
            writeBoolField(5, state.alertMessage)
            writeBoolField(6, state.alertBell)
            writeBoolField(7, state.usePwm)
            writeUInt32Field(8, state.outputVibra)
            writeUInt32Field(9, state.outputBuzzer)
            writeBoolField(10, state.alertMessageVibra)
            writeBoolField(11, state.alertMessageBuzzer)
            writeBoolField(12, state.alertBellVibra)
            writeBoolField(13, state.alertBellBuzzer)
            writeUInt32Field(14, state.nagTimeout)
            writeBoolField(15, state.useI2sAsBuzzer)
        }.toByteArray()

    fun encodeModuleConfigWithExternalNotification(state: MeshWireExternalNotificationPushState): ByteArray {
        val inner = encodeExternalNotificationInner(state)
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(3, inner)
        }.toByteArray()
    }

    fun encodeAdminSetModuleConfigExternalNotification(state: MeshWireExternalNotificationPushState): ByteArray {
        val moduleBytes = encodeModuleConfigWithExternalNotification(state)
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(35, moduleBytes)
        }.toByteArray()
    }

    fun encodeExternalNotificationSetModuleConfigTransaction(
        state: MeshWireExternalNotificationPushState,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
    ): List<ByteArray> {
        val begin = MeshWireProtobufWriter().apply { writeBoolField(64, true) }.toByteArray()
        val commit = MeshWireProtobufWriter().apply { writeBoolField(65, true) }.toByteArray()
        return listOf(
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(begin, device),
            MeshWireLoRaToRadioEncoder.encodeAdminAppMeshToRadio(
                encodeAdminSetModuleConfigExternalNotification(state),
                device,
            ),
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(commit, device),
        )
    }
}
