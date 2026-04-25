package com.example.aura.meshwire

import java.util.UUID

/**
 * Снимок канала с ноды или подготовленный к записи (protobuf [Channel] / [ChannelSettings]).
 *
 * [role]: `0` = DISABLED, `1` = PRIMARY, `2` = SECONDARY (как в meshwire/channel.proto).
 */
data class MeshStoredChannel(
    val rowKey: String,
    val index: Int,
    val role: Int,
    val name: String,
    val psk: ByteArray,
    val settingsId: UInt,
    val uplinkEnabled: Boolean,
    val downlinkEnabled: Boolean,
    /** [ModuleSettings.position_precision](https://github.com/meshtastic/protobufs); 0 = без передачи координат в канале. */
    val positionPrecision: UInt = 0U,
) {
    fun copyForEdit(
        index: Int = this.index,
        role: Int = this.role,
        name: String = this.name,
        psk: ByteArray = this.psk,
        settingsId: UInt = this.settingsId,
        uplinkEnabled: Boolean = this.uplinkEnabled,
        downlinkEnabled: Boolean = this.downlinkEnabled,
        positionPrecision: UInt = this.positionPrecision,
    ): MeshStoredChannel = copy(
        index = index,
        role = role,
        name = name,
        psk = psk.copyOf(),
        settingsId = settingsId,
        uplinkEnabled = uplinkEnabled,
        downlinkEnabled = downlinkEnabled,
        positionPrecision = positionPrecision,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshStoredChannel) return false
        return rowKey == other.rowKey &&
            index == other.index &&
            role == other.role &&
            name == other.name &&
            psk.contentEquals(other.psk) &&
            settingsId == other.settingsId &&
            uplinkEnabled == other.uplinkEnabled &&
            downlinkEnabled == other.downlinkEnabled &&
            positionPrecision == other.positionPrecision
    }

    override fun hashCode(): Int {
        var result = rowKey.hashCode()
        result = 31 * result + index
        result = 31 * result + role
        result = 31 * result + name.hashCode()
        result = 31 * result + psk.contentHashCode()
        result = 31 * result + settingsId.hashCode()
        result = 31 * result + uplinkEnabled.hashCode()
        result = 31 * result + downlinkEnabled.hashCode()
        result = 31 * result + positionPrecision.hashCode()
        return result
    }

    companion object {
        const val ROLE_DISABLED: Int = 0
        const val ROLE_PRIMARY: Int = 1
        const val ROLE_SECONDARY: Int = 2

        const val MAX_CHANNELS: Int = 8

        /** Ключ PSK «по умолчанию» из channel.proto (1 байт = default AES ключ). */
        val DEFAULT_PSK: ByteArray = byteArrayOf(1)

        fun newBlank(
            name: String,
            psk: ByteArray = DEFAULT_PSK,
        ): MeshStoredChannel {
            val rid = UUID.randomUUID().toString()
            val sid = kotlin.random.Random.Default.nextInt().toUInt()
            return MeshStoredChannel(
                rowKey = "n-$rid",
                index = 0,
                role = ROLE_SECONDARY,
                name = name.trim(),
                psk = psk.copyOf(),
                settingsId = sid,
                uplinkEnabled = false,
                downlinkEnabled = false,
                positionPrecision = 0U,
            )
        }
    }
}

/**
 * Подпись канала в списке чата и шапке: как во вкладке «Каналы» — пустое имя и плейсхолдеры
 * вида «ch 1» / «Ch.0» заменяются на [MeshWireModemPreset.defaultChannelNameForEmpty] из LoRa.
 */
fun meshChannelDisplayTitle(
    ch: MeshStoredChannel,
    modemPresetForEmptyName: MeshWireModemPreset?,
): String {
    val fallback = modemPresetForEmptyName?.defaultChannelNameForEmpty()
        ?: MeshWireModemPreset.LONG_FAST.defaultChannelNameForEmpty()
    val t = ch.name.trim()
    if (t.isEmpty()) return fallback
    if (t.matches(Regex("(?i)ch\\.?\\s*\\d+"))) return fallback
    return t
}

/**
 * PSK «по умолчанию» Meshtastic: один байт `0x01` (см. [MeshStoredChannel.DEFAULT_PSK]).
 * В Base64 (NO_WRAP) это ровно **`AQ==`** — такой ключ часто называют «дефолтным» в приложении.
 *
 * На карте меток каналы с этим PSK **не показываются** в выпадающем списке и **нельзя** ставить
 * mesh-метки (в т.ч. при режиме «Локально», где под капотом подставляется первый реальный слот).
 */
fun MeshStoredChannel.isDefaultPskBlockedOnMapBeacons(): Boolean =
    psk.contentEquals(MeshStoredChannel.DEFAULT_PSK)

data class MeshWireChannelsSyncResult(
    val channels: List<MeshStoredChannel>,
    /** Из [Config.lora] при разборе FromRadio (поле 14 — MHz, если задано). */
    val loraFrequencyMhz: Float?,
    /** `LoRaConfig.channel_num` (слот частоты в регионе). */
    val loraChannelNum: UInt?,
    /**
     * Максимальный `Channel.index`, встреченный в потоке FromRadio до отбрасывания пустых вторичных слотов.
     * Нужен для `buildChannelPushSequence` (очистка «хвоста» слотов на ноде).
     */
    val rawMaxChannelIndex: Int,
)
