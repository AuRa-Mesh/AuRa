package com.example.aura.meshwire

import java.nio.charset.StandardCharsets

/**
 * Собирает [FromRadio] при want_config: каналы (поле 10), кусок LoRa из Config (поле 5), config_complete (7).
 */
internal class MeshWireChannelsSyncAccumulator {
    private val byIndex = linkedMapOf<Int, MeshStoredChannel>()
    var loraFrequencyMhz: Float? = null
    var loraChannelNum: UInt? = null
    var configComplete: Boolean = false
    /** Макс. индекс из любого принятого `Channel`, до фильтрации пустых вторичных. */
    var maxSeenChannelIndex: Int = -1
        private set
    /** Сколько admin-ответов `get_channel_response` получено (для итеративного режима). */
    var adminChannelResponseCount: Int = 0
        private set
    /** Роль последнего канала, полученного через admin-ответ. */
    var lastAdminChannelRole: Int = MeshStoredChannel.ROLE_DISABLED
        private set

    fun consumeFromRadio(bytes: ByteArray) {
        parseFromRadio(bytes)
    }

    fun shouldFinish(): Boolean = configComplete

    fun toResult(): MeshWireChannelsSyncResult {
        val rawMax = maxOf(
            maxSeenChannelIndex,
            byIndex.keys.maxOrNull() ?: -1,
        )
        val list = byIndex.values
            .asSequence()
            .filter { it.role != MeshStoredChannel.ROLE_DISABLED }
            .filterNot { it.isUnconfiguredSecondaryGhost() }
            .sortedBy { it.index }
            .map { it.copyForEdit() }
            .toList()
        return MeshWireChannelsSyncResult(
            channels = list,
            loraFrequencyMhz = loraFrequencyMhz,
            loraChannelNum = loraChannelNum,
            rawMaxChannelIndex = rawMax,
        )
    }

    private fun parseFromRadio(bytes: ByteArray) {
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
                5 -> {
                    if (i + 4 <= bytes.size && fieldNum == 14) {
                        // не ожидаем fixed32 на верхнем уровне FromRadio
                    }
                    i = minOf(i + 4, bytes.size)
                }
                1 -> i = minOf(i + 8, bytes.size)
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        5 -> parseConfigForLora(sub)
                        10 -> parseChannelMessage(sub)
                        2, 1 -> tryParseAdminChannelResponse(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun tryParseAdminChannelResponse(meshPacketBytes: ByteArray) {
        val admin = MeshWireAdminResponseParser.extractAdminPayloadFromMeshPacket(meshPacketBytes) ?: return
        val channelBytes = MeshWireAdminResponseParser.extractChannelResponse(admin) ?: return
        parseChannelMessage(channelBytes)
        adminChannelResponseCount++
        val latest = byIndex[maxSeenChannelIndex]
        lastAdminChannelRole = latest?.role ?: MeshStoredChannel.ROLE_DISABLED
    }

    private fun parseConfigForLora(configBytes: ByteArray) {
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
                    if (fieldNum == 6) parseLoRaConfigSnippet(sub)
                }
                else -> break
            }
        }
    }

    private fun parseLoRaConfigSnippet(lora: ByteArray) {
        var i = 0
        while (i < lora.size) {
            val tag = lora[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(lora, i)
                    i += n
                    if (fieldNum == 11) loraChannelNum = v.toUInt()
                }
                5 -> {
                    if (fieldNum == 14 && i + 4 <= lora.size) {
                        val bits = (lora[i].toInt() and 0xFF) or
                            ((lora[i + 1].toInt() and 0xFF) shl 8) or
                            ((lora[i + 2].toInt() and 0xFF) shl 16) or
                            ((lora[i + 3].toInt() and 0xFF) shl 24)
                        loraFrequencyMhz = java.lang.Float.intBitsToFloat(bits)
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

    private fun parseChannelMessage(bytes: ByteArray) {
        var idx = 0
        var role = MeshStoredChannel.ROLE_DISABLED
        var settings: ByteArray? = null
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    when (fieldNum) {
                        1 -> idx = v.toInt()
                        3 -> role = v.toInt().coerceIn(0, 2)
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 2) settings = sub
                }
                else -> break
            }
        }
        val st = settings ?: return
        val parsed = parseChannelSettings(st)
        val rowKey = "d-$idx-${parsed.id}"
        val ch = MeshStoredChannel(
            rowKey = rowKey,
            index = idx,
            role = role,
            name = parsed.name,
            psk = parsed.psk,
            settingsId = parsed.id,
            uplinkEnabled = parsed.uplink,
            downlinkEnabled = parsed.downlink,
            positionPrecision = parsed.positionPrecision,
        )
        byIndex[idx] = ch
        maxSeenChannelIndex = maxOf(maxSeenChannelIndex, idx)
    }

    /**
     * Пустой вторичный слот — settingsId == 0 и тривиальный контент.
     * Слоты с ненулевым settingsId ВСЕГДА сохраняются, даже если имя пустое
     * и PSK дефолтный (пользователь мог создать такой канал намеренно).
     *
     * Предпочтительная фильтрация — по `role != DISABLED`, а этот фильтр
     * оставлен только как страховка для прошивок, которые шлют role=0
     * вместе с непустым settings (не по спеке).
     */
    private fun MeshStoredChannel.isUnconfiguredSecondaryGhost(): Boolean {
        if (role != MeshStoredChannel.ROLE_SECONDARY) return false
        if (settingsId != 0U) return false
        if (name.isNotBlank()) return false
        val trivialPsk = psk.isEmpty() || psk.contentEquals(byteArrayOf(0)) ||
            psk.contentEquals(MeshStoredChannel.DEFAULT_PSK)
        if (!trivialPsk) return false
        return true
    }

    private data class SettingsParsed(
        val name: String,
        val psk: ByteArray,
        val id: UInt,
        val uplink: Boolean,
        val downlink: Boolean,
        val positionPrecision: UInt,
    )

    private fun parseChannelSettings(bytes: ByteArray): SettingsParsed {
        var name = ""
        var psk = byteArrayOf()
        var id: UInt = 0U
        var uplink = false
        var downlink = false
        var positionPrecision: UInt = 0U
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    when (fieldNum) {
                        5 -> uplink = v != 0L
                        6 -> downlink = v != 0L
                    }
                }
                5 -> {
                    if (fieldNum == 4 && i + 4 <= bytes.size) {
                        id = (bytes[i].toInt() and 0xFF).toUInt() or
                            ((bytes[i + 1].toInt() and 0xFF).toUInt() shl 8) or
                            ((bytes[i + 2].toInt() and 0xFF).toUInt() shl 16) or
                            ((bytes[i + 3].toInt() and 0xFF).toUInt() shl 24)
                    }
                    i += 4
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val raw = bytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        2 -> psk = raw
                        3 -> name = runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
                        7 -> positionPrecision = parseModuleSettingsPositionPrecision(raw)
                    }
                }
                else -> break
            }
        }
        return SettingsParsed(name, psk, id, uplink, downlink, positionPrecision)
    }

    private fun parseModuleSettingsPositionPrecision(bytes: ByteArray): UInt {
        var precision: UInt = 0U
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    if (fieldNum == 1) precision = (v and 0xFFFFFFFFL).toUInt()
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        return precision
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

    /**
     * Сырой protobuf [ChannelSet] из share link `meshtastic.org/e/#…` (repeated Channel = 1).
     */
    fun ingestChannelSet(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(bytes, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 1) parseChannelMessage(sub)
                }
                else -> break
            }
        }
    }
}
