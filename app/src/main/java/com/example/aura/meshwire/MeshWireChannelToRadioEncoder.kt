package com.example.aura.meshwire

import java.nio.charset.StandardCharsets

/**
 * [AdminMessage.set_channel] (поле 33) и транзакция begin/commit edit (64/65), как в meshwire/admin.proto.
 */
object MeshWireChannelToRadioEncoder {

    /**
     * Одно сообщение [Channel] (как в [channel.proto]) — для [ChannelSet] и ссылок meshtastic.org/e/#….
     */
    fun encodeChannelMessage(ch: MeshStoredChannel): ByteArray = encodeChannelProtobuf(ch)

    /**
     * [ChannelSet](https://github.com/meshtastic/protobufs/blob/master/meshtastic/channel.proto) —
     * `repeated Channel channels = 1` для QR/URL обмена каналами, как в типичном mesh-клиенте Android.
     */
    fun encodeChannelSetForShare(channels: List<MeshStoredChannel>): ByteArray {
        val w = MeshWireProtobufWriter()
        for (ch in channels) {
            w.writeLengthDelimitedField(1, encodeChannelProtobuf(ch))
        }
        return w.toByteArray()
    }

    private fun encodeChannelProtobuf(ch: MeshStoredChannel): ByteArray {
        if (ch.role == MeshStoredChannel.ROLE_DISABLED) {
            return MeshWireProtobufWriter().apply {
                writeInt32Field(1, ch.index)
                writeEnumField(3, MeshStoredChannel.ROLE_DISABLED)
            }.toByteArray()
        }
        val settings = MeshWireProtobufWriter().apply {
            if (ch.psk.isNotEmpty()) {
                writeLengthDelimitedField(2, ch.psk)
            }
            writeLengthDelimitedField(3, ch.name.toByteArray(StandardCharsets.UTF_8))
            if (ch.settingsId != 0U) {
                writeFixed32Field(4, ch.settingsId)
            }
            writeBoolField(5, ch.uplinkEnabled)
            writeBoolField(6, ch.downlinkEnabled)
            writeEmbeddedMessage(7) {
                writeUInt32Field(1, ch.positionPrecision)
            }
        }.toByteArray()
        return MeshWireProtobufWriter().apply {
            writeInt32Field(1, ch.index)
            writeLengthDelimitedField(2, settings)
            writeEnumField(3, ch.role)
        }.toByteArray()
    }

    private fun encodeAdminSetChannel(ch: MeshStoredChannel): ByteArray =
        MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(33, encodeChannelProtobuf(ch))
        }.toByteArray()

    private fun encodeBeginEdit(): ByteArray =
        MeshWireProtobufWriter().apply {
            writeBoolField(64, true)
        }.toByteArray()

    private fun encodeCommitEdit(): ByteArray =
        MeshWireProtobufWriter().apply {
            writeBoolField(65, true)
        }.toByteArray()

    fun encodeDisableChannelSlot(
        index: Int,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
    ): ByteArray {
        val ch = MeshStoredChannel(
            rowKey = "off-$index",
            index = index,
            role = MeshStoredChannel.ROLE_DISABLED,
            name = "",
            psk = byteArrayOf(),
            settingsId = 0U,
            uplinkEnabled = false,
            downlinkEnabled = false,
            positionPrecision = 0U,
        )
        return MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(
            encodeAdminSetChannel(ch),
            device,
        )
    }

    /**
     * Собирает последовательность записей в ToRadio для каналов.
     *
     * По умолчанию **без** `begin_edit_settings` / `commit_edit_settings`: в прошивке mesh
     * [commit_edit_settings] вызывает [AdminModule.saveChanges] с `shouldReboot = true`, из‑за чего нода
     * перезагружается. Отдельные `set_channel` идут через [handleSetChannel] → `saveChanges(SEGMENT_CHANNELS, false)`
     * и применяют конфиг «на лету» без reboot.
     *
     * @param wrapInEditTransaction если true — старый путь begin → set… → commit (может инициировать перезагрузку).
     * @param syncedMaxIndex максимальный индекс канала на ноде до правок (для очистки «хвоста» слотов).
     */
    fun buildChannelPushSequence(
        orderedChannels: List<MeshStoredChannel>,
        syncedMaxIndex: Int,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
        wrapInEditTransaction: Boolean = false,
    ): List<ByteArray> {
        val prepared = orderedChannels.mapIndexed { i, c ->
            c.copyForEdit(
                index = i,
                role = if (i == 0) MeshStoredChannel.ROLE_PRIMARY else MeshStoredChannel.ROLE_SECONDARY,
            )
        }
        val tailDisables = (syncedMaxIndex - (prepared.size - 1).coerceAtLeast(0)).coerceAtLeast(0)
        val reserve = prepared.size + tailDisables + if (wrapInEditTransaction) 2 else 0
        val out = ArrayList<ByteArray>(reserve)
        if (wrapInEditTransaction) {
            out.add(
                MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(encodeBeginEdit(), device),
            )
        }
        for (ch in prepared) {
            out.add(
                MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(
                    encodeAdminSetChannel(ch),
                    device,
                ),
            )
        }
        val newLast = (prepared.size - 1).coerceAtLeast(0)
        for (free in (newLast + 1)..syncedMaxIndex) {
            out.add(encodeDisableChannelSlot(free, device))
        }
        if (wrapInEditTransaction) {
            out.add(
                MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(encodeCommitEdit(), device),
            )
        }
        return out
    }
}
