package com.example.aura.meshwire

/**
 * Разбор [FromRadio] при want_config: только `config` (поле 5) → `lora` (поле 6) и `config_complete` (7).
 */
internal class MeshWireLoRaSyncAccumulator {
    var sawLoRa: Boolean = false
    var configComplete: Boolean = false

    private var usePreset: Boolean? = null
    private var modemPresetOrdinal: Int? = null
    private var regionCode: Int? = null
    private var hopLimit: Int? = null
    private var txEnabled: Boolean? = null
    private var txPower: Int? = null
    private var channelNum: UInt? = null
    private var overrideDutyCycle: Boolean? = null
    private var sx126xRxBoostedGain: Boolean? = null
    private var overrideFreqMhz: Float? = null
    private var ignoreMqtt: Boolean? = null
    private var configOkToMqtt: Boolean? = null
    private var bandwidth: UInt? = null
    private var spreadFactor: UInt? = null
    private var codingRate: UInt? = null
    private var paFanDisabled: Boolean? = null

    fun consumeFromRadio(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    if (fieldNum == 7 && v == MeshWireWantConfigHandshake.CONFIG_NONCE.toLong()) {
                        configComplete = true
                    }
                }
                5 -> i = minOf(i + 4, bytes.size)
                1 -> i = minOf(i + 8, bytes.size)
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        5 -> parseConfigEnvelope(sub)
                        2, 1 -> tryParseAdminConfigResponse(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun tryParseAdminConfigResponse(meshPacketBytes: ByteArray) {
        val admin = MeshWireAdminResponseParser.extractAdminPayloadFromMeshPacket(meshPacketBytes) ?: return
        val config = MeshWireAdminResponseParser.extractConfigResponse(admin) ?: return
        parseConfigEnvelope(config)
        configComplete = true
    }

    fun shouldFinish(): Boolean = configComplete

    fun toPushState(): MeshWireLoRaPushState {
        val region = regionCode?.let { MeshWireLoRaRegions.fromProtoCode(it) }
            ?: MeshWireLoRaRegions.fromProtoCode(0)
        val modem = modemPresetOrdinal?.let { MeshWireModemPreset.fromWireOrdinal(it) }
            ?: MeshWireModemPreset.LONG_FAST
        val hop = hopLimit?.let { MeshWireLoRaConfigLogic.clampHopLimit(it) } ?: 0
        val chText = channelNum?.toString() ?: "0"
        val freqTxt = when {
            overrideFreqMhz != null && overrideFreqMhz!! != 0f ->
                String.format(java.util.Locale.US, "%.3f", overrideFreqMhz!!)
            else -> "0"
        }
        val pwrTxt = txPower?.toString() ?: "0"
        val bwTxt = bandwidth?.toString() ?: "0"
        val sfTxt = spreadFactor?.toString() ?: "0"
        val crTxt = codingRate?.toString() ?: "0"
        return MeshWireLoRaPushState(
            region = region,
            usePreset = usePreset ?: false,
            modemPreset = modem,
            bandwidthText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(bwTxt).ifEmpty { "0" },
            spreadFactorText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(sfTxt).ifEmpty { "0" },
            codingRateText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(crTxt).ifEmpty { "0" },
            ignoreMqtt = ignoreMqtt ?: false,
            configOkToMqtt = configOkToMqtt ?: false,
            txEnabled = txEnabled ?: false,
            overrideDutyCycle = overrideDutyCycle ?: false,
            hopLimit = hop,
            channelNumText = MeshWireLoRaConfigLogic.sanitizeChannelNumInput(chText).ifEmpty { "0" },
            sx126xRxBoostedGain = sx126xRxBoostedGain ?: false,
            overrideFrequencyMhzText = freqTxt,
            txPowerDbmText = MeshWireLoRaConfigLogic.sanitizeIntSigned(pwrTxt),
            paFanDisabled = paFanDisabled ?: false,
        )
    }

    private fun parseConfigEnvelope(configBytes: ByteArray) {
        var i = 0
        while (i < configBytes.size) {
            val tag = configBytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(configBytes, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(configBytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > configBytes.size) break
                    val sub = configBytes.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 6) {
                        sawLoRa = true
                        parseLoRaConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseLoRaConfigBytes(lora: ByteArray) {
        var i = 0
        while (i < lora.size) {
            val tag = lora[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(lora, i)
                    i += n
                    when (fieldNum) {
                        1 -> usePreset = v != 0L
                        2 -> modemPresetOrdinal = v.toInt()
                        3 -> bandwidth = v.toUInt()
                        4 -> spreadFactor = v.toUInt()
                        5 -> codingRate = v.toUInt()
                        7 -> regionCode = v.toInt()
                        8 -> hopLimit = v.toInt().coerceIn(0, 7)
                        9 -> txEnabled = v != 0L
                        10 -> txPower = decodeInt32Varint(v)
                        11 -> channelNum = v.toUInt()
                        12 -> overrideDutyCycle = v != 0L
                        13 -> sx126xRxBoostedGain = v != 0L
                        15 -> paFanDisabled = v != 0L
                        104 -> ignoreMqtt = v != 0L
                        105 -> configOkToMqtt = v != 0L
                    }
                }
                5 -> {
                    if (fieldNum == 14 && i + 4 <= lora.size) {
                        val bits = (lora[i].toInt() and 0xFF) or
                            ((lora[i + 1].toInt() and 0xFF) shl 8) or
                            ((lora[i + 2].toInt() and 0xFF) shl 16) or
                            ((lora[i + 3].toInt() and 0xFF) shl 24)
                        overrideFreqMhz = java.lang.Float.intBitsToFloat(bits)
                    }
                    i += 4
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(lora, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
    }

    /** Protobuf `int32` как varint: для положительных мощностей совпадает с обычным varint. */
    private fun decodeInt32Varint(v: Long): Int {
        if (v <= Int.MAX_VALUE.toLong() && v >= Int.MIN_VALUE.toLong()) return v.toInt()
        return 0
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            shift += 7
            if ((b and 0x80) == 0) break
        }
        return result to (i - start)
    }
}
