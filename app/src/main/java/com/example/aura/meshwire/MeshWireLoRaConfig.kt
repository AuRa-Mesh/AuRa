package com.example.aura.meshwire

/**
 * Поля и смысл совпадают с `Config.LoRaConfig` (protobuf).
 * @see [config.proto](https://github.com/meshtastic/protobufs/blob/master/meshtastic/config.proto)
 */
enum class MeshWireModemPreset(
    /** Номер варианта в protobuf ( ModemPreset ). */
    val wireOrdinal: Int,
    val labelRu: String,
    val deprecated: Boolean = false,
) {
    LONG_FAST(0, "Большая дальность – Быстрый"),
    LONG_SLOW(1, "Большая дальность – Медленно", deprecated = true),
    VERY_LONG_SLOW(2, "Очень большая дальность – Медленно", deprecated = true),
    MEDIUM_SLOW(3, "Средняя дальность – Медленно"),
    MEDIUM_FAST(4, "Средняя дальность – Быстро"),
    SHORT_SLOW(5, "Короткая дальность – Медленно"),
    SHORT_FAST(6, "Короткая дальность – Быстро"),
    LONG_MODERATE(7, "Большая дальность – Умеренно"),
    SHORT_TURBO(8, "Короткая дальность – Турбо"),
    LONG_TURBO(9, "Большая дальность – Турбо"),
    ;

    val menuTitle: String
        get() = if (deprecated) "$labelRu (устар.)" else labelRu

    /**
     * Подстановка для пустого имени канала при `use_preset == true`, как в
     * [org.meshwire.core.model.Channel.name](Meshtastic-Android `Channel.kt`).
     */
    fun defaultChannelNameForEmpty(): String = when (this) {
        SHORT_TURBO -> "ShortTurbo"
        SHORT_FAST -> "ShortFast"
        SHORT_SLOW -> "ShortSlow"
        MEDIUM_FAST -> "MediumFast"
        MEDIUM_SLOW -> "MediumSlow"
        LONG_FAST -> "LongFast"
        LONG_SLOW -> "LongSlow"
        LONG_MODERATE -> "LongMod"
        VERY_LONG_SLOW -> "VLongSlow"
        LONG_TURBO -> "LongTurbo"
    }

    companion object {
        fun fromWireOrdinal(ordinal: Int): MeshWireModemPreset =
            entries.firstOrNull { it.wireOrdinal == ordinal } ?: LONG_FAST

        /** Как в документации MeshWire: от более быстрых к более дальним. */
        val UI_ORDER: List<MeshWireModemPreset> = listOf(
            SHORT_TURBO,
            SHORT_FAST,
            SHORT_SLOW,
            MEDIUM_FAST,
            MEDIUM_SLOW,
            LONG_FAST,
            LONG_MODERATE,
            LONG_TURBO,
            LONG_SLOW,
            VERY_LONG_SLOW,
        )
    }
}

object MeshWireLoRaConfigLogic {

    /** `hop_limit` в LoRaConfig: как в приложении MeshWire, 0–7 (0 — без ретрансляции / особый режим). */
    const val HOP_LIMIT_DEFAULT = 3
    const val HOP_LIMIT_MIN = 0
    const val HOP_LIMIT_MAX = 7

    fun clampHopLimit(value: Int): Int =
        value.coerceIn(HOP_LIMIT_MIN, HOP_LIMIT_MAX)

    /**
     * `channel_num`: 0 — хэш от имени канала (старое поведение); иначе слот 1…NUM_CHANNELS.
     */
    fun sanitizeChannelNumInput(raw: String): String =
        raw.filter { it.isDigit() }.take(10)

    fun parseChannelNumForProto(text: String): UInt =
        text.filter { it.isDigit() }.toULongOrNull()?.toUInt() ?: 0u

    /**
     * `tx_power`: 0 — «как по умолчанию для железа/региона» (config.proto).
     */
    fun sanitizeIntSigned(raw: String, allowLeadingMinus: Boolean = false): String {
        if (raw.isEmpty()) return ""
        val first = if (allowLeadingMinus && raw.first() == '-') "-" else ""
        val rest = if (allowLeadingMinus && raw.first() == '-') raw.drop(1) else raw
        val digits = rest.filter { it.isDigit() }
        return first + digits
    }

    fun parseTxPowerForProto(text: String): Int {
        val t = text.trim()
        if (t.isEmpty()) return 0
        return t.toIntOrNull() ?: 0
    }

    fun parseOverrideFrequencyMhz(text: String): Float {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return 0f
        return t.toFloatOrNull() ?: 0f
    }

    /** `LoRaConfig.bandwidth` / `spread_factor` / `coding_rate` — uint32 в protobuf (как в MeshWire Android). */
    fun sanitizeUInt32Input(raw: String): String =
        raw.filter { it.isDigit() }.take(10)

    fun parseUInt32ForProto(text: String): UInt =
        text.filter { it.isDigit() }.toULongOrNull()?.toUInt() ?: 0u
}

/**
 * Снимок полей LoRa для protobuf и для отслеживания изменений (debounce → BLE).
 */
data class MeshWireLoRaPushState(
    val region: MeshWireLoRaRegion,
    val usePreset: Boolean,
    val modemPreset: MeshWireModemPreset,
    /** Поля 3–5 `LoRaConfig`, если `use_preset == false` (ручной модем вместо пресета). */
    val bandwidthText: String,
    val spreadFactorText: String,
    val codingRateText: String,
    val ignoreMqtt: Boolean,
    val configOkToMqtt: Boolean,
    val txEnabled: Boolean,
    val overrideDutyCycle: Boolean,
    val hopLimit: Int,
    val channelNumText: String,
    val sx126xRxBoostedGain: Boolean,
    val overrideFrequencyMhzText: String,
    val txPowerDbmText: String,
    val paFanDisabled: Boolean,
) {
    companion object {
        fun initial(): MeshWireLoRaPushState {
            val r = MeshWireLoRaRegions.defaultRegion()
            return MeshWireLoRaPushState(
                region = r,
                usePreset = true,
                modemPreset = MeshWireModemPreset.LONG_FAST,
                bandwidthText = "0",
                spreadFactorText = "0",
                codingRateText = "0",
                ignoreMqtt = false,
                configOkToMqtt = false,
                txEnabled = true,
                overrideDutyCycle = false,
                hopLimit = MeshWireLoRaConfigLogic.HOP_LIMIT_DEFAULT,
                channelNumText = "0",
                sx126xRxBoostedGain = false,
                overrideFrequencyMhzText = r.suggestMidFrequencyMhz() ?: "0",
                txPowerDbmText = "0",
                paFanDisabled = false,
            )
        }
    }
}
