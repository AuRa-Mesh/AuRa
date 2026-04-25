package com.example.aura.meshwire

import java.nio.charset.StandardCharsets

/**
 * Распарсенный `MeshPacket.decoded` (portnum + payload) с маршрутом, для чата по каналу.
 */
data class ParsedMeshDataPayload(
    val packetId: UInt?,
    val from: UInt?,
    val to: UInt?,
    /** Индекс канала на ноде (`MeshPacket.channel`), 0 = primary если поле отсутствует. */
    val channel: UInt?,
    val portnum: Int,
    val payload: ByteArray,
    /** [MeshPacket.rx_snr] — для строки «Сигнал/шум» в UI. */
    val rxSnr: Float? = null,
    /** [MeshPacket.rx_rssi]. */
    val rxRssi: Int? = null,
    /** [MeshPacket.hop_limit]. */
    val hopLimit: UInt? = null,
    /** [MeshPacket.hop_start]; ретрансляции ≈ hop_start − hop_limit. */
    val hopStart: UInt? = null,
    /** [MeshPacket.via_mqtt]. */
    val viaMqtt: Boolean = false,
    /** [MeshPacket.rx_time], секунды с 1970 (от ноды). */
    val rxTimeSec: UInt? = null,
    /** [Data.request_id] — для ROUTING_APP: id исходного пакета (ACK имеет свой MeshPacket.id). */
    val dataRequestId: UInt? = null,
    /** [Data.reply_id] (tapback / ответ на сообщение с этим MeshPacket.id у автора). */
    val dataReplyId: UInt? = null,
    /** [Data.emoji] — неноль = реакция-эмодзи (mesh.proto). */
    val dataEmoji: UInt? = null,
    /** [Data.dest] (fixed32) — редко; маршрутизация. */
    val dataDest: UInt? = null,
    /**
     * [Data.source] (fixed32) — **оригинальный отправитель** при multihop (reliable);
     * иначе null и автор = [from] у [MeshPacket].
     */
    val dataSource: UInt? = null,
    /**
     * После PKI-расшифровки прошивка копирует сюда 32 байта public key отправителя (mesh.proto [MeshPacket.public_key]).
     */
    val meshSenderPublicKey: ByteArray? = null,
    /** mesh.proto MeshPacket.pki_encrypted — пакет расшифрован по PKI. */
    val meshPkiEncrypted: Boolean = false,
) {
    /** Автор содержимого: [dataSource] если задан, иначе [from]. */
    fun logicalFrom(): UInt? {
        val s = dataSource
        if (s != null && s != 0u) return s
        return from
    }

    fun dedupKey(): String =
        "${packetId}_${from}_${to}_${channel}_${portnum}_${payload.contentHashCode()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedMeshDataPayload) return false
        return packetId == other.packetId &&
            from == other.from &&
            to == other.to &&
            channel == other.channel &&
            portnum == other.portnum &&
            payload.contentEquals(other.payload) &&
            rxSnr == other.rxSnr &&
            rxRssi == other.rxRssi &&
            hopLimit == other.hopLimit &&
            hopStart == other.hopStart &&
            viaMqtt == other.viaMqtt &&
            rxTimeSec == other.rxTimeSec &&
            dataRequestId == other.dataRequestId &&
            dataReplyId == other.dataReplyId &&
            dataEmoji == other.dataEmoji &&
            dataDest == other.dataDest &&
            dataSource == other.dataSource &&
            meshPkiEncrypted == other.meshPkiEncrypted &&
            (meshSenderPublicKey == null && other.meshSenderPublicKey == null ||
                meshSenderPublicKey != null && other.meshSenderPublicKey != null &&
                meshSenderPublicKey.contentEquals(other.meshSenderPublicKey))
    }

    override fun hashCode(): Int {
        var h = 31 * (31 * (31 * (31 * (packetId?.hashCode() ?: 0) + (from?.hashCode() ?: 0)) +
            (to?.hashCode() ?: 0)) + (channel?.hashCode() ?: 0)) + portnum + payload.contentHashCode()
        h = 31 * h + (rxSnr?.hashCode() ?: 0)
        h = 31 * h + (rxRssi ?: 0)
        h = 31 * h + (hopLimit?.hashCode() ?: 0)
        h = 31 * h + (hopStart?.hashCode() ?: 0)
        h = 31 * h + viaMqtt.hashCode()
        h = 31 * h + (rxTimeSec?.hashCode() ?: 0)
        h = 31 * h + (dataRequestId?.hashCode() ?: 0)
        h = 31 * h + (dataReplyId?.hashCode() ?: 0)
        h = 31 * h + (dataEmoji?.hashCode() ?: 0)
        h = 31 * h + (dataDest?.hashCode() ?: 0)
        h = 31 * h + (dataSource?.hashCode() ?: 0)
        h = 31 * h + meshPkiEncrypted.hashCode()
        h = 31 * h + (meshSenderPublicKey?.contentHashCode() ?: 0)
        return h
    }
}

/**
 * Разбор кадров FromRadio с [MeshPacket] (`packet` = поле 2 в mesh.proto; поле 1 — `id`).
 * Для старых прошивок допускаем length-delimited поле 1 как MeshPacket.
 */
object MeshWireFromRadioMeshPacketParser {

    const val PORTNUM_TEXT_MESSAGE_APP: Int = 1
    /** mesh.proto PortNum.PRIVATE_APP — бинарные данные приложения (голос Aura). */
    const val PORTNUM_PRIVATE_APP: Int = 256
    /** mesh.proto Portnums.POSITION_APP. */
    const val PORTNUM_POSITION_APP: Int = 3
    /** mesh.proto Portnums.NODEINFO_APP — payload это protobuf User, не NodeInfo. */
    const val PORTNUM_NODEINFO_APP: Int = 4
    const val PORTNUM_ROUTING_APP: Int = 5
    const val PORTNUM_TEXT_MESSAGE_COMPRESSED_APP: Int = 7
    const val PORTNUM_ADMIN: Int = 6
    const val PORTNUM_TELEMETRY: Int = 67
    const val PORTNUM_TRACEROUTE: Int = 70
    const val PORTNUM_NEIGHBORINFO: Int = 71

    /** Краткая строка для Toast / UI или null, если кадр не интересен. */
    fun summarizeFromRadio(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        var i = 0
        var out: String? = null
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(bytes, i)
                    i += n
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
                    if (fieldNum == 2 || fieldNum == 1) {
                        summarizeMeshPacket(sub)?.let { s ->
                            out = if (out == null) s else "$out\n$s"
                        }
                    }
                }
                else -> break
            }
        }
        return out
    }

    /**
     * Достаёт [0..100] из Telemetry → DeviceMetrics (field 2) внутри [telemetryPayload] (decoded Data).
     */
    fun parseTelemetryBatteryPercent(telemetryPayload: ByteArray): Int? {
        var i = 0
        while (i < telemetryPayload.size) {
            val tag = telemetryPayload[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                2 -> {
                    val (len, lb) = readVarint(telemetryPayload, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > telemetryPayload.size) break
                    val sub = telemetryPayload.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 2) return parseDeviceMetricsBatteryOnly(sub)
                }
                0 -> {
                    val (_, n) = readVarint(telemetryPayload, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                else -> break
            }
        }
        return null
    }

    private fun parseDeviceMetricsBatteryOnly(dm: ByteArray): Int? {
        var i = 0
        while (i < dm.size) {
            val tag = dm[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(dm, i)
                    i += n
                    if (fieldNum == 1) return v.toInt().coerceIn(0, 100)
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(dm, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        return null
    }

    /**
     * Все `MeshPacket` с полем `decoded` из одного кадра FromRadio (верхнеуровневое поле `packet` = 2).
     */
    fun extractDataPayloadsFromFromRadio(bytes: ByteArray): List<ParsedMeshDataPayload> {
        if (bytes.isEmpty()) return emptyList()
        val out = ArrayList<ParsedMeshDataPayload>()
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
                5 -> i = minOf(i + 4, bytes.size)
                1 -> i = minOf(i + 8, bytes.size)
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 2 || fieldNum == 1) {
                        parseMeshPacketDecoded(sub)?.let { out.add(it) }
                    }
                }
                else -> break
            }
        }
        return out
    }

    /**
     * Текст для portnum 1 (UTF-8) и 7 (Unishox2, пресет как у прошивки mesh → затем UTF-8).
     */
    fun payloadAsUtf8Text(portnum: Int, payload: ByteArray): String? {
        val utf8Bytes =
            when (portnum) {
                PORTNUM_TEXT_MESSAGE_APP -> payload
                PORTNUM_TEXT_MESSAGE_COMPRESSED_APP ->
                    Unishox2Native.decompressMeshTextIfAvailable(payload) ?: return null
                else -> return null
            }
        return runCatching { String(utf8Bytes, StandardCharsets.UTF_8) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseMeshPacketDecoded(packet: ByteArray): ParsedMeshDataPayload? {
        var i = 0
        var decoded: ByteArray? = null
        var toNum: UInt? = null
        var fromNum: UInt? = null
        var channel: UInt? = null
        var packetId: UInt? = null
        var rxSnr: Float? = null
        var rxRssi: Int? = null
        var hopLimit: UInt? = null
        var hopStart: UInt? = null
        var viaMqtt = false
        var rxTimeSec: UInt? = null
        var meshPk: ByteArray? = null
        var meshPki = false
        while (i < packet.size) {
            val tag = packet[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(packet, i)
                    i += n
                    when (fieldNum) {
                        3 -> channel = v.toUInt()
                        9 -> hopLimit = v.toUInt()
                        12 -> rxRssi = v.toInt()
                        14 -> viaMqtt = v != 0L
                        15 -> hopStart = v.toUInt()
                        17 -> meshPki = v != 0L
                    }
                }
                5 -> {
                    if (i + 4 <= packet.size) {
                        val v = readFixed32(packet, i)
                        when (fieldNum) {
                            1 -> fromNum = v
                            2 -> toNum = v
                            6 -> packetId = v
                            7 -> rxTimeSec = v
                            8 -> rxSnr = Float.fromBits(v.toInt())
                        }
                    }
                    i = minOf(i + 4, packet.size)
                }
                1 -> i = minOf(i + 8, packet.size)
                2 -> {
                    val (len, lb) = readVarint(packet, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > packet.size) break
                    val sub = packet.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        4 -> decoded = sub
                        16 ->
                            if (ln >= 32) {
                                meshPk = sub.copyOfRange(0, 32)
                            }
                    }
                }
                else -> {
                    val ni = skipProtobufField(packet, i, wire) ?: break
                    i = ni
                }
            }
        }
        val data = decoded ?: return null
        val portnum = readDataPortnum(data) ?: return null
        val payload = readDataPayload(data) ?: return null
        val wire = readDataWireFields(data)
        return ParsedMeshDataPayload(
            packetId,
            fromNum,
            toNum,
            channel,
            portnum,
            payload,
            rxSnr,
            rxRssi,
            hopLimit,
            hopStart,
            viaMqtt,
            rxTimeSec,
            dataRequestId = wire.requestId,
            dataReplyId = wire.replyId,
            dataEmoji = wire.emoji,
            dataDest = wire.dest,
            dataSource = wire.source,
            meshSenderPublicKey = meshPk,
            meshPkiEncrypted = meshPki,
        )
    }

    /** int32 из protobuf varint (mesh.proto MeshPacket.rx_rssi). */
    private fun int32FromUnsignedVarint(v: Long): Int =
        (v and 0xFFFF_FFFFL).toInt()

    private fun summarizeMeshPacket(packet: ByteArray): String? {
        var i = 0
        var decoded: ByteArray? = null
        var toNum: UInt? = null
        var fromNum: UInt? = null
        while (i < packet.size) {
            val tag = packet[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(packet, i)
                    i += n
                }
                5 -> {
                    if (fieldNum == 1 || fieldNum == 2) {
                        if (i + 4 <= packet.size) {
                            val v = readFixed32(packet, i)
                            if (fieldNum == 1) fromNum = v
                            if (fieldNum == 2) toNum = v
                        }
                    }
                    i = minOf(i + 4, packet.size)
                }
                1 -> i = minOf(i + 8, packet.size)
                2 -> {
                    val (len, lb) = readVarint(packet, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > packet.size) break
                    val sub = packet.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 4) decoded = sub
                }
                else -> break
            }
        }
        val data = decoded ?: return null
        val portnum = readDataPortnum(data) ?: return null
        val payload = readDataPayload(data) ?: return null
        val route = formatRouteHint(fromNum, toNum)
        return when (portnum) {
            PORTNUM_ADMIN -> summarizeAdmin(payload)?.let { "$route$it" }
            PORTNUM_TRACEROUTE -> summarizeTraceroute(payload)?.let { "$route$it" }
            PORTNUM_NEIGHBORINFO -> summarizeNeighborInfo(payload)?.let { "$route$it" }
            PORTNUM_TELEMETRY -> summarizeTelemetry(payload)?.let { "$route$it" }
            else -> null
        }
    }

    private fun formatRouteHint(from: UInt?, to: UInt?): String {
        val f = from?.let { MeshWireNodeNum.formatHex(it) } ?: "?"
        val t = to?.let { MeshWireNodeNum.formatHex(it) } ?: "?"
        return "$f → $t: "
    }

    private fun readDataPortnum(data: ByteArray): Int? {
        var i = 0
        while (i < data.size) {
            val tag = data[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(data, i)
                    i += n
                    if (fieldNum == 1) return v.toInt()
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(data, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> return null
            }
        }
        return null
    }

    private fun readDataPayload(data: ByteArray): ByteArray? {
        var i = 0
        while (i < data.size) {
            val tag = data[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(data, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(data, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > data.size) return null
                    if (fieldNum == 2) return data.copyOfRange(i, i + ln)
                    i += ln
                }
                else -> return null
            }
        }
        return null
    }

    private data class DataWireFields(
        val dest: UInt?,
        val source: UInt?,
        val requestId: UInt?,
        val replyId: UInt?,
        val emoji: UInt?,
    )

    /**
     * [Data] fixed32: dest (4), source (5), request_id (6), reply_id (7), emoji (8).
     * [Data.source] — оригинальный отправитель при multihop; 6–8 иногда как varint; 5 — редко varint.
     */
    private fun readDataWireFields(data: ByteArray): DataWireFields {
        var i = 0
        var dest: UInt? = null
        var source: UInt? = null
        var requestId: UInt? = null
        var replyId: UInt? = null
        var emoji: UInt? = null
        while (i < data.size) {
            val tag = data[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(data, i)
                    i += n
                    when (fieldNum) {
                        /** Редко: `source` как varint вместо fixed32 — иначе теряем автора при multihop. */
                        5 -> if (source == null) source = v.toUInt()
                        6 -> if (requestId == null) requestId = v.toUInt()
                        7 -> if (replyId == null) replyId = v.toUInt()
                        8 -> if (emoji == null) emoji = v.toUInt()
                    }
                }
                5 -> {
                    if (i + 4 <= data.size) {
                        val v = readFixed32(data, i)
                        when (fieldNum) {
                            4 -> dest = v
                            5 -> source = v
                            6 -> requestId = v
                            7 -> replyId = v
                            8 -> emoji = v
                        }
                    }
                    i = minOf(i + 4, data.size)
                }
                1 -> i = minOf(i + 8, data.size)
                2 -> {
                    val (len, lb) = readVarint(data, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > data.size) break
                    i += ln
                }
                else -> {
                    val ni = skipProtobufField(data, i, wire) ?: break
                    i = ni
                }
            }
        }
        return DataWireFields(dest, source, requestId, replyId, emoji)
    }

    /** Реакция mesh (tapback): TEXT (+ сжатый TEXT) + [dataEmoji] != 0 + [dataReplyId]. */
    fun isTapbackPayload(p: ParsedMeshDataPayload): Boolean {
        if (p.portnum != PORTNUM_TEXT_MESSAGE_APP &&
            p.portnum != PORTNUM_TEXT_MESSAGE_COMPRESSED_APP
        ) {
            return false
        }
        val emoji = p.dataEmoji ?: return false
        if (emoji == 0u) return false
        return p.dataReplyId != null
    }

    /**
     * Снятие реакции: тот же канал, есть [Data.reply_id], [Data.emoji] нет или 0, полезная нагрузка пустая
     * (не обычный ответ с текстом).
     */
    fun isReactionRevokePayload(p: ParsedMeshDataPayload): Boolean {
        if (p.portnum != PORTNUM_TEXT_MESSAGE_APP &&
            p.portnum != PORTNUM_TEXT_MESSAGE_COMPRESSED_APP
        ) {
            return false
        }
        if (p.dataReplyId == null) return false
        if (isTapbackPayload(p)) return false
        val raw = payloadAsUtf8Text(p.portnum, p.payload)?.trim().orEmpty()
        if (raw.isNotEmpty()) return false
        val em = p.dataEmoji
        return em == null || em == 0u
    }

    private fun summarizeAdmin(adminBytes: ByteArray): String? {
        var i = 0
        while (i < adminBytes.size) {
            val tag = adminBytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(adminBytes, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(adminBytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > adminBytes.size) break
                    val sub = adminBytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        4 -> summarizeUser(sub)?.let { return "User: $it" }
                        13 -> summarizeDeviceMetadata(sub)?.let { return "У-во: $it" }
                        6 -> return "Admin: config (fragment)"
                        8 -> return "Admin: module_config (fragment)"
                    }
                }
                else -> break
            }
        }
        return null
    }

    private fun summarizeUser(user: ByteArray): String? {
        var longN: String? = null
        var shortN: String? = null
        var i = 0
        while (i < user.size) {
            val tag = user[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(user, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(user, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > user.size) break
                    val raw = user.copyOfRange(i, i + ln)
                    i += ln
                    val s = runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
                    when (fieldNum) {
                        2 -> longN = s
                        3 -> shortN = s
                    }
                }
                else -> break
            }
        }
        if (longN == null && shortN == null) return null
        return "${longN.orEmpty()} (${shortN.orEmpty()})".trim()
    }

    private fun summarizeDeviceMetadata(meta: ByteArray): String? {
        var i = 0
        while (i < meta.size) {
            val tag = meta[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(meta, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(meta, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > meta.size) break
                    val raw = meta.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 1) {
                        return runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { null }
                    }
                }
                else -> break
            }
        }
        return null
    }

    private fun summarizeTraceroute(rd: ByteArray): String? {
        val hops = mutableListOf<UInt>()
        var i = 0
        while (i < rd.size) {
            val tag = rd[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                5 -> {
                    if (fieldNum == 1 && i + 4 <= rd.size) {
                        hops.add(readFixed32(rd, i))
                    }
                    i += 4
                }
                2 -> {
                    val (len, lb) = readVarint(rd, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                0 -> {
                    val (_, n) = readVarint(rd, i)
                    i += n
                }
                1 -> i += 8
                else -> break
            }
        }
        if (hops.isEmpty()) return "Traceroute (пустой марш até ответа)"
        return "Traceroute: " + hops.joinToString(" → ") { MeshWireNodeNum.formatHex(it) }
    }

    private fun summarizeNeighborInfo(nb: ByteArray): String? {
        var nodeId: UInt? = null
        var neigh = 0
        var i = 0
        while (i < nb.size) {
            val tag = nb[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(nb, i)
                    i += n
                    if (fieldNum == 1) nodeId = v.toUInt()
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(nb, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > nb.size) break
                    val sub = nb.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 4) neigh++
                }
                else -> break
            }
        }
        val idStr = nodeId?.let { MeshWireNodeNum.formatHex(it) } ?: "?"
        return "NeighborInfo от $idStr, рёбер: $neigh"
    }

    private fun summarizeTelemetry(tel: ByteArray): String? {
        // Telemetry: oneof — ищем вложенные message по типичным тегам
        var i = 0
        while (i < tel.size) {
            val tag = tel[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                2 -> {
                    val (len, lb) = readVarint(tel, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > tel.size) break
                    val sub = tel.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        2 -> summarizeDeviceMetrics(sub)?.let { return "Телеметрия: $it" }
                        3 -> summarizeEnvironmentMetrics(sub)?.let { return "Окружение: $it" }
                        4 -> return "Air quality (пакет ${sub.size} B)"
                        5 -> return "Power metrics (пакет ${sub.size} B)"
                        6 -> summarizeLocalStats(sub)?.let { return "Локал. стата: $it" }
                    }
                }
                0 -> {
                    val (_, n) = readVarint(tel, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                else -> break
            }
        }
        return null
    }

    private fun summarizeDeviceMetrics(dm: ByteArray): String? {
        var bat: UInt? = null
        var volt: Float? = null
        var i = 0
        while (i < dm.size) {
            val tag = dm[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(dm, i)
                    i += n
                    if (fieldNum == 1) bat = v.toInt().toUInt()
                }
                5 -> {
                    if (fieldNum == 2 && i + 4 <= dm.size) {
                        val bits = readFixed32(dm, i).toInt()
                        volt = Float.fromBits(bits)
                    }
                    i += 4
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(dm, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        val parts = mutableListOf<String>()
        bat?.let { parts.add("бат. $it%") }
        volt?.let { parts.add(String.format("%.2f В", it)) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun summarizeEnvironmentMetrics(em: ByteArray): String? {
        var temp: Float? = null
        var hum: Float? = null
        var i = 0
        while (i < em.size) {
            val tag = em[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                5 -> {
                    if (i + 4 <= em.size) {
                        val bits = readFixed32(em, i)
                        val f = Float.fromBits(bits.toInt())
                        when (fieldNum) {
                            1 -> temp = f
                            2 -> hum = f
                        }
                    }
                    i += 4
                }
                0 -> {
                    val (_, n) = readVarint(em, i)
                    i += n
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(em, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        val parts = mutableListOf<String>()
        temp?.let { parts.add(String.format("%.1f°C", it)) }
        hum?.let { parts.add(String.format("%.0f%% влажн.", it)) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun summarizeLocalStats(ls: ByteArray): String? {
        var chUtil: Float? = null
        var airTx: Float? = null
        var i = 0
        while (i < ls.size) {
            val tag = ls[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                5 -> {
                    if (i + 4 <= ls.size) {
                        val bits = readFixed32(ls, i)
                        val f = Float.fromBits(bits.toInt())
                        when (fieldNum) {
                            2 -> chUtil = f
                            3 -> airTx = f
                        }
                    }
                    i += 4
                }
                0 -> {
                    val (_, n) = readVarint(ls, i)
                    i += n
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(ls, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        val parts = mutableListOf<String>()
        chUtil?.let { parts.add(String.format("ChUtil %.1f%%", it)) }
        airTx?.let { parts.add(String.format("AirTX %.1f%%", it)) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun readFixed32(bytes: ByteArray, i: Int): UInt =
        (bytes[i].toInt() and 0xFF or
            ((bytes[i + 1].toInt() and 0xFF) shl 8) or
            ((bytes[i + 2].toInt() and 0xFF) shl 16) or
            ((bytes[i + 3].toInt() and 0xFF) shl 24)).toUInt()

    /**
     * Пропуск одного значения неизвестного поля protobuf после тега.
     * Без этого цикл разбора мог оборваться до полей (например [Data.source]), если встречался неподдерживаемый wire type.
     */
    private fun skipProtobufField(bytes: ByteArray, i: Int, wire: Int): Int? =
        when (wire) {
            0 -> {
                val (_, n) = readVarint(bytes, i)
                i + n
            }
            1 -> if (i + 8 <= bytes.size) i + 8 else null
            2 -> {
                val (len, lb) = readVarint(bytes, i)
                val i2 = i + lb
                val ln = len.toInt()
                if (ln < 0 || i2 + ln > bytes.size) null else i2 + ln
            }
            5 -> if (i + 4 <= bytes.size) i + 4 else null
            3, 4 -> null
            else -> null
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
     * mesh.proto [FromRadio] поле `node_info` (4): ответ на on-demand NodeInfo с телефона часто идёт сюда,
     * без вложенного [MeshPacket] в поле `packet` (2) — см. [extractDataPayloadsFromFromRadio].
     */
    fun extractLegacyNodeInfoNodeNumsFromFromRadio(bytes: ByteArray): List<Long> {
        if (bytes.isEmpty()) return emptyList()
        val out = ArrayList<Long>(2)
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
                5 -> i = minOf(i + 4, bytes.size)
                1 -> i = minOf(i + 8, bytes.size)
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 4) {
                        parseNodeInfoProtobufNodeNumOnly(sub)?.let { out.add(it) }
                    }
                }
                else -> break
            }
        }
        return out
    }

    /** Только [NodeInfo.num] (поле 1) из тела protobuf NodeInfo. */
    private fun parseNodeInfoProtobufNodeNumOnly(bytes: ByteArray): Long? {
        var i = 0
        var num: Long? = null
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    if (fieldNum == 1) num = v
                }
                5 -> i = minOf(i + 4, bytes.size)
                1 -> i = minOf(i + 8, bytes.size)
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    i += ln
                }
                else -> {
                    val ni = skipProtobufField(bytes, i, wire) ?: break
                    i = ni
                }
            }
        }
        val n = num ?: return null
        return n and 0xFFFF_FFFFL
    }
}
