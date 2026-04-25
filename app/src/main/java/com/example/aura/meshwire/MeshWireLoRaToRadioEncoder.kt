package com.example.aura.meshwire

import java.nio.charset.StandardCharsets
import kotlin.random.Random

/**
 * Сборка `ToRadio { packet = MeshPacket { decoded { portnum=ADMIN_APP, payload=AdminMessage{set_config{lora}}} } }`.
 *
 * Поля совпадают с [meshtastic mesh.proto](https://github.com/meshtastic/protobufs) и
 * [admin.proto](https://github.com/meshtastic/protobufs/blob/master/meshtastic/admin.proto).
 */
object MeshWireLoRaToRadioEncoder {

    /** Широковещание на канал (`MeshPacket.to`), см. mesh.proto. */
    val BROADCAST_NODE_NUM: UInt = 0xFFFF_FFFFu

    const val PORTNUM_TEXT_MESSAGE_APP: Int = 1
    /** mesh.proto PortNum.TEXT_MESSAGE_COMPRESSED_APP (Unishox2). */
    const val PORTNUM_TEXT_MESSAGE_COMPRESSED_APP: Int = 7
    /** mesh.proto PortNum.PRIVATE_APP — бинарный голос (фрагменты Aura). */
    const val PORTNUM_PRIVATE_APP: Int = 256
    /** mesh.proto PortNum.NODEINFO_APP — запрос карточки узла / User (как «Request user info» в типичном mesh-клиенте). */
    const val PORTNUM_NODEINFO_APP: Int = 4
    /** mesh.proto PortNum.POSITION_APP — payload [Position]. */
    const val PORTNUM_POSITION_APP: Int = 3
    /** mesh.proto PortNum.WAYPOINT_APP (8) — официальные waypoint mesh; метки Aura идут через [PORTNUM_PRIVATE_APP]. */
    const val PORTNUM_WAYPOINT_APP: Int = 8
    /** mesh.proto PortNum.ROUTING_APP — ACK/NAK маршрутизации. */
    const val PORTNUM_ROUTING_APP: Int = 5

    const val PORTNUM_ADMIN_APP: Int = 6
    const val PORTNUM_TELEMETRY_APP: Int = 67
    const val PORTNUM_TRACEROUTE_APP: Int = 70
    const val PORTNUM_NEIGHBORINFO_APP: Int = 71
    /** Aura: обмен «аптаймом приложения» (только между клиентами Aura). */
    const val PORTNUM_AURA_PEER_UPTIME_APP: Int = 502
    /** Aura: VIP-статус клиента (широковещательно, broadcast-only; см. [MeshWireAuraVipCodec]). */
    const val PORTNUM_AURA_VIP_APP: Int = 503
    /**
     * Aura: восстановление VIP-таймера через mesh (запрос/ответ; см. [MeshWireAuraVipRecoveryCodec]).
     * Запросы широковещательные, ответы — unicast обратно инициатору.
     */
    const val PORTNUM_AURA_VIP_RECOVERY_APP: Int = 504
    /**
     * Aura: синхронизация использованных VIP-кодов продления через mesh (см.
     * [MeshWireAuraVipUsedCodesCodec]). Гарантирует одноразовость кода после переустановки.
     */
    const val PORTNUM_AURA_VIP_USED_CODES_APP: Int = 505

    private const val MESH_PACKET_HOP_LIMIT_DEFAULT: UInt = 3U

    /**
     * Максимум байт UTF-8 полезной нагрузки TEXT до передачи в ToRadio (клиентская обрезка в [truncateMeshUtf8]).
     * Итоговый размер кадра ограничен прошивкой и LoRa.
     */
    const val MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES: Int = 220

    /** mesh.Packet.Priority.RELIABLE = 70 (как у `sendData` в Python-клиенте). */
    private const val PRIORITY_RELIABLE: Int = 70

    data class LoRaDeviceParams(
        /** `MeshPacket.to` — nodenum локальной ноды (0x… из `!xxxxxxxx`). */
        val destinationNodeNum: UInt,
        /** Индекс канала для admin (как в типичном mesh-клиенте; часто 0, если «Admin» не выделен). */
        val channelIndex: UInt = 0U,
        /** Уникальный id пакета (0 = сгенерировать). */
        val packetId: UInt = 0U,
    )

    /**
     * Кодирует полный `ToRadio` для записи в характеристику TORADIO.
     */
    fun encodeSetLoraToRadio(
        lora: MeshWireLoRaPushState,
        device: LoRaDeviceParams,
    ): ByteArray {
        val adminPayload = encodeAdminSetConfigLora(lora)
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /**
     * Как в приложении mesh: транзакция `begin_edit_settings` → `set_config { lora }` → `commit_edit_settings`.
     */
    fun encodeLoraSetConfigTransaction(
        lora: MeshWireLoRaPushState,
        device: LoRaDeviceParams,
    ): List<ByteArray> {
        val begin = MeshWireProtobufWriter().apply { writeBoolField(64, true) }.toByteArray()
        val commit = MeshWireProtobufWriter().apply { writeBoolField(65, true) }.toByteArray()
        return listOf(
            encodeAdminOneofToRadio(begin, device),
            encodeSetLoraToRadio(lora, device),
            encodeAdminOneofToRadio(commit, device),
        )
    }

    /** Лимиты имён как у прошивки / приложения mesh. */
    const val USER_LONG_NAME_MAX_LEN: Int = 36
    const val USER_SHORT_NAME_MAX_LEN: Int = 4

    fun sanitizeOwnerNames(longName: String, shortName: String): Pair<String, String> {
        val ln = longName.trim().take(USER_LONG_NAME_MAX_LEN)
        val sn = shortName.trim().take(USER_SHORT_NAME_MAX_LEN)
        return ln to sn
    }

    /**
     * `AdminMessage { set_owner = User { id?, long_name, short_name } }` → полный `ToRadio.packet`.
     * [ownerNodeNum] — при известном nodenum задаётся `User.id` вида `!xxxxxxxx`.
     */
    /**
     * [AdminMessage.set_fixed_position] (поле 41) — задать координаты и включить fixed на ноде.
     * [Position.location_source] = LOC_MANUAL (1).
     */
    fun encodeAdminSetFixedPositionToRadio(
        latitudeDeg: Double,
        longitudeDeg: Double,
        altitudeMeters: Int?,
        device: LoRaDeviceParams,
    ): ByteArray {
        val posBytes = encodePositionForAdminFixed(latitudeDeg, longitudeDeg, altitudeMeters)
        val adminPayload = MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(41, posBytes)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /** [AdminMessage.remove_fixed_position = true] (поле 42). */
    fun encodeAdminRemoveFixedPositionToRadio(device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeBoolField(42, true)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    private fun encodePositionForAdminFixed(
        latitudeDeg: Double,
        longitudeDeg: Double,
        altitudeMeters: Int?,
    ): ByteArray {
        val timeSec = (System.currentTimeMillis() / 1000L).toUInt().takeIf { it != 0U }
            ?: 1u
        return MeshWireProtobufWriter().apply {
            val latI = (latitudeDeg * 1e7).toInt()
            val lonI = (longitudeDeg * 1e7).toInt()
            writeFixed32Field(1, latI.toUInt())
            writeFixed32Field(2, lonI.toUInt())
            altitudeMeters?.let { writeInt32Field(3, it) }
            writeFixed32Field(4, timeSec)
            writeEnumField(5, 1) // LocSource.LOC_MANUAL
        }.toByteArray()
    }

    fun encodeSetOwnerToRadio(
        longName: String,
        shortName: String,
        ownerNodeNum: UInt?,
        device: LoRaDeviceParams,
    ): ByteArray {
        val (ln, sn) = sanitizeOwnerNames(longName, shortName)
        val userBytes = encodeUserOwnerPayload(ln, sn, ownerNodeNum)
        val adminPayload = MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(32, userBytes)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    private fun encodeUserOwnerPayload(
        longName: String,
        shortName: String,
        ownerNodeNum: UInt?,
    ): ByteArray = MeshWireProtobufWriter().apply {
        if (ownerNodeNum != null) {
            val id = "!%08x".format(ownerNodeNum.toLong() and 0xFFFFFFFFL)
            writeLengthDelimitedField(1, id.toByteArray(StandardCharsets.UTF_8))
        }
        writeLengthDelimitedField(2, longName.toByteArray(StandardCharsets.UTF_8))
        writeLengthDelimitedField(3, shortName.toByteArray(StandardCharsets.UTF_8))
    }.toByteArray()

    /** Полный `ToRadio.packet` с `decoded.portnum = ADMIN` и телом [adminPayload] (одно поле oneof AdminMessage). */
    fun encodeAdminOneofToRadio(
        adminPayload: ByteArray,
        device: LoRaDeviceParams,
    ): ByteArray = encodeAdminAppMeshToRadio(adminPayload, device)

    internal fun encodeAdminAppMeshToRadio(
        adminPayload: ByteArray,
        device: LoRaDeviceParams,
    ): ByteArray {
        val dataPayload = encodeDataPortnumPayload(PORTNUM_ADMIN_APP, adminPayload)
        val meshPacket = encodeMeshPacket(
            to = device.destinationNodeNum,
            from = 0U,
            channel = device.channelIndex,
            packetId = if (device.packetId == 0U) randomPacketId() else device.packetId,
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = true,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket)
    }

    private fun randomPacketId(): UInt {
        val low = Random.nextInt(0, 1024)
        val hi = Random.nextInt(0, 0x3FFFFF) shl 10
        return (low or hi).toUInt() and 0xFFFFFFFFU
    }

    internal fun encodeAdminSetConfigLora(lora: MeshWireLoRaPushState): ByteArray {
        val loraBytes = encodeLoRaConfig(lora)
        val configBytes = MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(6, loraBytes)
        }.toByteArray()
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(34, configBytes)
        }.toByteArray()
    }

    private fun encodeLoRaConfig(lora: MeshWireLoRaPushState): ByteArray =
        MeshWireProtobufWriter().apply {
            writeBoolField(1, lora.usePreset)
            if (lora.usePreset) {
                writeEnumField(2, lora.modemPreset.wireOrdinal)
            } else {
                val bw = MeshWireLoRaConfigLogic.parseUInt32ForProto(lora.bandwidthText)
                val sf = MeshWireLoRaConfigLogic.parseUInt32ForProto(lora.spreadFactorText)
                val cr = MeshWireLoRaConfigLogic.parseUInt32ForProto(lora.codingRateText)
                writeUInt32Field(3, bw)
                writeUInt32Field(4, sf)
                writeUInt32Field(5, cr)
            }
            writeEnumField(7, lora.region.toProtoRegionCode())
            writeUInt32Field(8, lora.hopLimit.toUInt().coerceIn(0u, 7u))
            writeBoolField(9, lora.txEnabled)
            writeInt32Field(10, MeshWireLoRaConfigLogic.parseTxPowerForProto(lora.txPowerDbmText))
            writeUInt32Field(
                11,
                MeshWireLoRaConfigLogic.parseChannelNumForProto(lora.channelNumText)
            )
            writeBoolField(12, lora.overrideDutyCycle)
            writeBoolField(13, lora.sx126xRxBoostedGain)
            val overrideHz = MeshWireLoRaConfigLogic.parseOverrideFrequencyMhz(lora.overrideFrequencyMhzText)
            writeFloatField(14, overrideHz)
            writeBoolField(15, lora.paFanDisabled)
            writeBoolField(104, lora.ignoreMqtt)
            writeBoolField(105, lora.configOkToMqtt)
        }.toByteArray()

    /**
     * Тело [Data]: portnum, payload; при [wantResponse] — [Data.want_response] (поле 3 в mesh.proto).
     */
    private fun encodeDataPortnumPayload(
        portnum: Int,
        payload: ByteArray,
        wantResponse: Boolean = false,
    ): ByteArray =
        MeshWireProtobufWriter().apply {
            writeEnumField(1, portnum)
            writeLengthDelimitedField(2, payload)
            if (wantResponse) writeBoolField(3, true)
        }.toByteArray()

    /**
     * Tapback / реакция: [Data] с portnum TEXT, payload = UTF-8 эмодзи, [reply_id] = MeshPacket.id цели,
     * [emoji] = неноль (как в mesh.proto и типичном Android mesh-клиенте).
     */
    private fun encodeDataTapbackPayload(
        emojiUtf8: ByteArray,
        replyPacketId: UInt,
        emojiField: UInt,
    ): ByteArray =
        MeshWireProtobufWriter().apply {
            writeEnumField(1, PORTNUM_TEXT_MESSAGE_APP)
            writeLengthDelimitedField(2, emojiUtf8)
            writeFixed32Field(7, replyPacketId)
            writeFixed32Field(8, emojiField)
        }.toByteArray()

    /**
     * @param emojiUtf8 UTF-8 эмодзи; **1 байт** — только wire ID (legacy add); **2 байта** — [wireId][≠0 add | 0 remove]
     *   (флаг «добавить» в одном бите полезной нагрузки второго байта).
     */
    fun encodeTapbackToRadio(
        emojiUtf8: ByteArray,
        replyMeshPacketId: UInt,
        channelIndex: UInt,
        emojiProtobufField: UInt = 1U,
        /** Широковещание по каналу или адресная доставка (личка). */
        to: UInt = BROADCAST_NODE_NUM,
    ): ByteArray {
        val dataPayload = encodeDataTapbackPayload(emojiUtf8, replyMeshPacketId, emojiProtobufField)
        val meshPacket = encodeMeshPacket(
            to = to,
            from = 0U,
            channel = channelIndex,
            packetId = randomPacketId(),
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = false,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket)
    }

    /**
     * Снятие реакции по эфиру: пустой TEXT payload, [reply_id] = цель, [Data.emoji] = 0
     * (см. [com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser.isReactionRevokePayload]).
     */
    fun encodeReactionRevokeToRadio(
        replyMeshPacketId: UInt,
        channelIndex: UInt,
        to: UInt = BROADCAST_NODE_NUM,
    ): ByteArray {
        val dataPayload = MeshWireProtobufWriter().apply {
            writeEnumField(1, PORTNUM_TEXT_MESSAGE_APP)
            writeLengthDelimitedField(2, ByteArray(0))
            writeFixed32Field(7, replyMeshPacketId)
            writeFixed32Field(8, 0U)
        }.toByteArray()
        val meshPacket = encodeMeshPacket(
            to = to,
            from = 0U,
            channel = channelIndex,
            packetId = randomPacketId(),
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = false,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket)
    }

    private fun encodeMeshPacket(
        to: UInt,
        from: UInt,
        channel: UInt,
        packetId: UInt,
        hopLimit: UInt,
        wantAck: Boolean,
        decodedPayload: ByteArray,
    ): ByteArray = MeshWireProtobufWriter().apply {
        if (from != 0U) {
            writeFixed32Field(1, from)
        }
        writeFixed32Field(2, to)
        // Всегда задаём индекс канала (0 = primary), как в типичном mesh-клиенте Python sendData / meshPacket.channel.
        writeUInt32Field(3, channel)
        writeLengthDelimitedField(4, decodedPayload)
        writeFixed32Field(6, packetId)
        writeUInt32Field(9, hopLimit)
        writeBoolField(10, wantAck)
        writeEnumField(11, PRIORITY_RELIABLE)
    }.toByteArray()

    private fun encodeToRadioPacket(meshPacket: ByteArray): ByteArray =
        MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(1, meshPacket)
        }.toByteArray()

    /**
     * `AdminMessage.set_time_only` (admin.proto поле 43, **fixed32**, секунды Unix) — выставить время на ноде после BLE READY.
     */
    fun encodeSetTimeOnlyToRadio(unixEpochSeconds: Long): ByteArray {
        val u = unixEpochSeconds.coerceIn(0L, 0xFFFF_FFFFL).toUInt()
        val adminPayload = MeshWireProtobufWriter().apply {
            writeFixed32Field(43, u)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(
            adminPayload,
            LoRaDeviceParams(
                destinationNodeNum = BROADCAST_NODE_NUM,
                channelIndex = 0U,
                packetId = 0U,
            ),
        )
    }

    /**
     * Позиция с телефона в эфир (primary channel), как «Предоставление местоположения для сети» в типичном mesh-клиенте Android.
     */
    fun encodePhonePositionToRadio(
        positionPayload: ByteArray,
        channelIndex: UInt = 0U,
    ): ByteArray = encodeDecodedDataPacketToRadio(
        portnum = PORTNUM_POSITION_APP,
        payload = positionPayload,
        to = BROADCAST_NODE_NUM,
        channelIndex = channelIndex,
        wantAck = false,
    )

    /**
     * Текст в широковещательный пакет на [channelIndex] (0 = primary).
     * Длину UTF-8 перед вызовом ограничивайте [MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES].
     */
    fun encodeChannelTextMessageToRadio(
        textUtf8: ByteArray,
        channelIndex: UInt = 0U,
    ): ByteArray = encodeDecodedDataPacketToRadio(
        portnum = PORTNUM_TEXT_MESSAGE_APP,
        payload = textUtf8,
        to = BROADCAST_NODE_NUM,
        channelIndex = channelIndex,
        // Как sendText() в типичном mesh-клиенте Python: wantAck по умолчанию false для эфира.
        wantAck = false,
    )

    fun truncateMeshUtf8(text: String, maxBytes: Int): String {
        val b = text.toByteArray(StandardCharsets.UTF_8)
        if (b.size <= maxBytes) return text
        var n = maxBytes
        while (n > 0 && (b[n - 1].toInt() and 0xC0) == 0x80) n--
        return String(b, 0, n, StandardCharsets.UTF_8)
    }

    /**
     * Выбор порта TEXT (1) или TEXT_COMPRESSED (7): как в прошивке mesh, сжимаем только если результат короче.
     */
    fun meshTextPortAndPayload(textUtf8: ByteArray): Pair<Int, ByteArray> {
        val compressed = Unishox2Native.compressMeshTextIfAvailable(textUtf8)
            ?: return PORTNUM_TEXT_MESSAGE_APP to textUtf8
        return if (compressed.isNotEmpty() && compressed.size < textUtf8.size) {
            PORTNUM_TEXT_MESSAGE_COMPRESSED_APP to compressed
        } else {
            PORTNUM_TEXT_MESSAGE_APP to textUtf8
        }
    }

    /** Текстовое сообщение с выбором порта 1/7 и фиксированным [packetId]. */
    fun encodeTextMessageToRadioWithIdAndPort(
        portnum: Int,
        payload: ByteArray,
        channelIndex: UInt,
        to: UInt = BROADCAST_NODE_NUM,
        wantAck: Boolean = true,
        packetId: UInt = randomPacketId(),
    ): Pair<ByteArray, UInt> {
        val dataPayload = encodeDataPortnumPayload(portnum, payload)
        val meshPacket = encodeMeshPacket(
            to = to,
            from = 0U,
            channel = channelIndex,
            packetId = packetId,
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = wantAck,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket) to packetId
    }

    fun encodeDecodedDataPacketToRadio(
        portnum: Int,
        payload: ByteArray,
        to: UInt,
        channelIndex: UInt = 0U,
        wantAck: Boolean = true,
        /** [Data.want_response] — запрос ответа «в том же духе» на стороне получателя. */
        dataWantResponse: Boolean = false,
    ): ByteArray {
        val dataPayload = encodeDataPortnumPayload(portnum, payload, wantResponse = dataWantResponse)
        val meshPacket = encodeMeshPacket(
            to = to,
            from = 0U,
            channel = channelIndex,
            packetId = randomPacketId(),
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = wantAck,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket)
    }

    /**
     * Любой DATA-порт (например [PORTNUM_PRIVATE_APP]) с фиксированным [packetId] и [wantAck] для связки с ROUTING на FromRadio.
     */
    fun encodeDecodedDataPacketToRadioWithId(
        portnum: Int,
        payload: ByteArray,
        to: UInt,
        channelIndex: UInt = 0U,
        wantAck: Boolean = true,
        packetId: UInt = randomPacketId(),
        dataWantResponse: Boolean = false,
    ): Pair<ByteArray, UInt> {
        val dataPayload = encodeDataPortnumPayload(portnum, payload, wantResponse = dataWantResponse)
        val meshPacket = encodeMeshPacket(
            to = to,
            from = 0U,
            channel = channelIndex,
            packetId = packetId,
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = wantAck,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket) to packetId
    }

    /**
     * Текстовое сообщение с фиксированным [packetId] и [wantAck] для связки с ROUTING на FromRadio.
     * AES на эфире — на стороне прошивки (PSK канала); сюда передаётся только plaintext decoded.
     */
    fun encodeTextMessageToRadioWithId(
        textUtf8: ByteArray,
        channelIndex: UInt,
        to: UInt = BROADCAST_NODE_NUM,
        wantAck: Boolean = true,
        packetId: UInt = randomPacketId(),
    ): Pair<ByteArray, UInt> {
        val dataPayload = encodeDataPortnumPayload(PORTNUM_TEXT_MESSAGE_APP, textUtf8)
        val meshPacket = encodeMeshPacket(
            to = to,
            from = 0U,
            channel = channelIndex,
            packetId = packetId,
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = wantAck,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket) to packetId
    }

    /**
     * Ответ на сообщение: `Data { portnum TEXT, payload, reply_id }` без поля `emoji` (как в типичном mesh-клиенте).
     */
    fun encodeTextReplyMessageToRadioWithId(
        textUtf8: ByteArray,
        channelIndex: UInt,
        replyToMeshPacketId: UInt,
        to: UInt = BROADCAST_NODE_NUM,
        wantAck: Boolean = true,
        packetId: UInt = randomPacketId(),
    ): Pair<ByteArray, UInt> {
        val dataPayload = MeshWireProtobufWriter().apply {
            writeEnumField(1, PORTNUM_TEXT_MESSAGE_APP)
            writeLengthDelimitedField(2, textUtf8)
            writeFixed32Field(7, replyToMeshPacketId)
        }.toByteArray()
        val meshPacket = encodeMeshPacket(
            to = to,
            from = 0U,
            channel = channelIndex,
            packetId = packetId,
            hopLimit = MESH_PACKET_HOP_LIMIT_DEFAULT,
            wantAck = wantAck,
            decodedPayload = dataPayload,
        )
        return encodeToRadioPacket(meshPacket) to packetId
    }

    /** Traceroute: пустой [RouteDiscovery] в portnum 70. */
    fun encodeTracerouteRequestToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_TRACEROUTE_APP, byteArrayOf(), targetNodeNum)

    /**
     * Запрос NodeInfo/User на целевой узел (пустой payload — как в типичном mesh-клиенте).
     * Ставим `Data.want_response = true`, чтобы удалённая нода вернула актуальные данные профиля.
     */
    fun encodeNodeInfoRequestToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_NODEINFO_APP,
            payload = byteArrayOf(),
            to = targetNodeNum,
            wantAck = true,
            dataWantResponse = true,
        )

    /**
     * NODEINFO_APP на широковещание с [Data.want_response] = true: получатели могут ответить
     * своим NodeInfo/данными по правилам прошивки (после смены ключа DM — ускорить обновление карточек в эфире).
     */
    fun encodeNodeInfoBroadcastWantResponseToRadio(channelIndex: UInt = 0U): ByteArray =
        encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_NODEINFO_APP,
            payload = byteArrayOf(),
            to = BROADCAST_NODE_NUM,
            channelIndex = channelIndex,
            wantAck = false,
            dataWantResponse = true,
        )

    /** Запрос актуальной позиции (POSITION_APP, пустое тело). */
    fun encodePositionRequestToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_POSITION_APP, byteArrayOf(), targetNodeNum)

    /** Запрос NeighborInfo (71), пустое тело — по поведению прошивки. */
    fun encodeNeighborInfoRequestToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_NEIGHBORINFO_APP, byteArrayOf(), targetNodeNum)

    /**
     * Широковещательный пакет на основной канал с protobuf-телом [node_num fixed32][uptime_sec int64].
     * @return ToRadio bytes и [MeshPacket.id] для сопоставления с [ROUTING_APP].
     */
    fun encodeAuraPeerUptimeBroadcast(
        senderNodeNum: UInt,
        uptimeSeconds: Long,
        channelIndex: UInt = 0U,
    ): Pair<ByteArray, UInt> {
        val inner = MeshWireProtobufWriter().apply {
            writeFixed32Field(1, senderNodeNum)
            writeInt64Field(2, uptimeSeconds)
        }.toByteArray()
        return encodeDecodedDataPacketToRadioWithId(
            portnum = PORTNUM_AURA_PEER_UPTIME_APP,
            payload = inner,
            to = BROADCAST_NODE_NUM,
            channelIndex = channelIndex,
            wantAck = true,
        )
    }

    /**
     * Широковещание VIP-статуса Aura: один маленький protobuf на primary-канал, без [want_ack]
     * (чтобы не загружать эфир и маршрутизацию повторами). Схема — см. [MeshWireAuraVipCodec].
     *
     * Вызывать **редко** (on change + heartbeat раз в десятки минут). Приём — на стороне
     * приложения [MeshWireAuraVipCodec.PORTNUM].
     */
    fun encodeAuraVipBroadcast(
        senderNodeNum: UInt,
        active: Boolean,
        validForSec: UInt,
        /** Самоотчёт: сколько секунд VIP ещё осталось у отправителя. 0 = не передавать поле. */
        remainingSec: UInt = 0u,
        /** Передать флаг «ограничения сняты навсегда» (пароль разблокировки). */
        unlockedForever: Boolean = false,
        channelIndex: UInt = 0U,
    ): ByteArray {
        val inner = MeshWireProtobufWriter().apply {
            writeFixed32Field(1, senderNodeNum)
            writeBoolField(2, active)
            if (validForSec > 0u) writeInt32Field(3, validForSec.toInt())
            if (remainingSec > 0u) writeUInt32Field(4, remainingSec)
            if (unlockedForever) writeBoolField(5, true)
        }.toByteArray()
        return encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_AURA_VIP_APP,
            payload = inner,
            to = BROADCAST_NODE_NUM,
            channelIndex = channelIndex,
            wantAck = false,
        )
    }

    /**
     * Запрос восстановления VIP-таймера по mesh: широковещание на primary-канал, без want_ack.
     * Все Aura-клиенты, помнящие последний `remaining_sec` для [senderNodeNum], отвечают unicast
     * (см. [encodeAuraVipRecoveryResponse]). Схема payload — [MeshWireAuraVipRecoveryCodec].
     *
     * Вызывать **только на свежей установке** и редко (1–3 попытки с интервалом в минуту):
     * эфир дорогой, дубли бесполезны.
     */
    fun encodeAuraVipRecoveryRequest(
        senderNodeNum: UInt,
        channelIndex: UInt = 0U,
    ): ByteArray {
        val inner = MeshWireProtobufWriter().apply {
            writeFixed32Field(1, senderNodeNum) // subject = мы сами
            writeBoolField(2, false)            // is_response = false
        }.toByteArray()
        return encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_AURA_VIP_RECOVERY_APP,
            payload = inner,
            to = BROADCAST_NODE_NUM,
            channelIndex = channelIndex,
            wantAck = false,
        )
    }

    /**
     * Unicast-ответ на запрос восстановления VIP: возвращаем инициатору последние известные
     * нам данные его таймера. Отправляется только если у нас в [com.example.aura.vip.VipStatusStore]
     * действительно есть такая запись.
     *
     * Адресация: `to = requesterNodeNum` (то же значение, что `subject`, поскольку узел
     * запрашивает данные о себе).
     */
    fun encodeAuraVipRecoveryResponse(
        subjectNodeNum: UInt,
        requesterNodeNum: UInt,
        remainingSec: UInt,
        unlockedForever: Boolean,
        /** Последний известный суммарный аптайм приложения subject (сек), см. [MeshWireAuraPeerUptimeCodec]. */
        appUptimeSec: Long = 0L,
        channelIndex: UInt = 0U,
    ): ByteArray {
        val inner = MeshWireProtobufWriter().apply {
            writeFixed32Field(1, subjectNodeNum)
            writeBoolField(2, true) // is_response = true
            if (remainingSec > 0u) writeUInt32Field(3, remainingSec)
            if (unlockedForever) writeBoolField(4, true)
            if (appUptimeSec > 0L) writeInt64Field(5, appUptimeSec)
        }.toByteArray()
        return encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_AURA_VIP_RECOVERY_APP,
            payload = inner,
            to = requesterNodeNum,
            channelIndex = channelIndex,
            wantAck = false,
        )
    }

    /**
     * Анонс факта использования одного или нескольких VIP-кодов (широковещательно, без want_ack).
     * Соседи сохраняют хэши в [com.example.aura.vip.VipUsedCodesMeshStore] и позже могут вернуть
     * их по запросу восстановления ([encodeAuraVipUsedCodesRequest]).
     *
     * Каждый `hash` — ровно [MeshWireAuraVipUsedCodesCodec.HASH_LEN] = 8 байт. Большие наборы
     * разбивайте на пачки не более [MeshWireAuraVipUsedCodesCodec.MAX_HASHES_PER_PACKET].
     */
    fun encodeAuraVipUsedCodesAnnounce(
        senderNodeNum: UInt,
        hashes: List<ByteArray>,
        channelIndex: UInt = 0U,
    ): ByteArray {
        val inner = buildUsedCodesPayload(
            subjectNodeNum = senderNodeNum,
            kind = MeshWireAuraVipUsedCodesCodec.Kind.ANNOUNCE,
            hashes = hashes,
        )
        return encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_AURA_VIP_USED_CODES_APP,
            payload = inner,
            to = BROADCAST_NODE_NUM,
            channelIndex = channelIndex,
            wantAck = false,
        )
    }

    /**
     * Широковещательный запрос «верните мне все хэши моих использованных кодов» от свежей установки.
     * Соседи отвечают unicast-ом через [encodeAuraVipUsedCodesResponse].
     */
    fun encodeAuraVipUsedCodesRequest(
        senderNodeNum: UInt,
        channelIndex: UInt = 0U,
    ): ByteArray {
        val inner = buildUsedCodesPayload(
            subjectNodeNum = senderNodeNum,
            kind = MeshWireAuraVipUsedCodesCodec.Kind.REQUEST,
            hashes = emptyList(),
        )
        return encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_AURA_VIP_USED_CODES_APP,
            payload = inner,
            to = BROADCAST_NODE_NUM,
            channelIndex = channelIndex,
            wantAck = false,
        )
    }

    /**
     * Unicast-ответ на запрос восстановления использованных кодов. `subject` — node_num, о котором
     * говорим (= `requester`), `hashes` — всё, что мы помним (до
     * [MeshWireAuraVipUsedCodesCodec.MAX_HASHES_PER_PACKET] штук).
     */
    fun encodeAuraVipUsedCodesResponse(
        subjectNodeNum: UInt,
        requesterNodeNum: UInt,
        hashes: List<ByteArray>,
        channelIndex: UInt = 0U,
    ): ByteArray {
        val inner = buildUsedCodesPayload(
            subjectNodeNum = subjectNodeNum,
            kind = MeshWireAuraVipUsedCodesCodec.Kind.RESPONSE,
            hashes = hashes,
        )
        return encodeDecodedDataPacketToRadio(
            portnum = PORTNUM_AURA_VIP_USED_CODES_APP,
            payload = inner,
            to = requesterNodeNum,
            channelIndex = channelIndex,
            wantAck = false,
        )
    }

    private fun buildUsedCodesPayload(
        subjectNodeNum: UInt,
        kind: MeshWireAuraVipUsedCodesCodec.Kind,
        hashes: List<ByteArray>,
    ): ByteArray {
        val valid = hashes.filter { it.size == MeshWireAuraVipUsedCodesCodec.HASH_LEN }
            .take(MeshWireAuraVipUsedCodesCodec.MAX_HASHES_PER_PACKET)
        return MeshWireProtobufWriter().apply {
            writeFixed32Field(1, subjectNodeNum)
            writeUInt32Field(2, kind.wire.toUInt())
            for (h in valid) writeLengthDelimitedField(3, h)
        }.toByteArray()
    }

    /** Telemetry: пустой вложенный oneof-variant (DeviceMetrics, LocalStats, …). */
    private fun encodeTelemetryVariantOnly(variantFieldNum: Int): ByteArray =
        MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(variantFieldNum, byteArrayOf())
        }.toByteArray()

    fun encodeTelemetryDeviceMetricsProbeToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_TELEMETRY_APP, encodeTelemetryVariantOnly(2), targetNodeNum)

    fun encodeTelemetryEnvironmentProbeToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_TELEMETRY_APP, encodeTelemetryVariantOnly(3), targetNodeNum)

    fun encodeTelemetryAirQualityProbeToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_TELEMETRY_APP, encodeTelemetryVariantOnly(4), targetNodeNum)

    fun encodeTelemetryPowerProbeToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_TELEMETRY_APP, encodeTelemetryVariantOnly(5), targetNodeNum)

    fun encodeTelemetryLocalStatsProbeToRadio(targetNodeNum: UInt): ByteArray =
        encodeDecodedDataPacketToRadio(PORTNUM_TELEMETRY_APP, encodeTelemetryVariantOnly(6), targetNodeNum)

    // ── AdminMessage.ConfigType enum (admin.proto) ────────────────────────────
    const val ADMIN_CONFIG_DEVICE: Int = 0
    const val ADMIN_CONFIG_POSITION: Int = 1
    const val ADMIN_CONFIG_LORA: Int = 5
    const val ADMIN_CONFIG_SECURITY: Int = 7

    // ── AdminMessage.ModuleConfigType enum (admin.proto) ────────────────────────
    const val ADMIN_MODULE_MQTT: Int = 0
    const val ADMIN_MODULE_EXTNOTIF: Int = 2
    const val ADMIN_MODULE_TELEMETRY: Int = 5

    /** AdminMessage.get_config_request (поле 5, enum ConfigType). */
    fun encodeAdminGetConfigRequest(configType: Int, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeEnumField(5, configType)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /** AdminMessage.get_module_config_request (поле 7, enum ModuleConfigType). */
    fun encodeAdminGetModuleConfigRequest(moduleConfigType: Int, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeEnumField(7, moduleConfigType)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /** AdminMessage.get_channel_request (поле 1, uint32 index). */
    fun encodeAdminGetChannelRequest(channelIndex: UInt, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeUInt32Field(1, channelIndex)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /** Admin на привязанную ноду: [get_device_metadata_request](https://github.com/meshtastic/protobufs/blob/master/meshtastic/admin.proto). */
    fun encodeAdminGetDeviceMetadataToRadio(device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeBoolField(12, true)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    fun encodeAdminGetOwnerToRadio(device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeBoolField(3, true)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    fun encodeAdminRemoveNodeByNum(nodeNum: UInt, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeUInt32Field(38, nodeNum)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    fun encodeAdminSetFavoriteNode(nodeNum: UInt, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeUInt32Field(39, nodeNum)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    fun encodeAdminSetIgnoredNode(nodeNum: UInt, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeUInt32Field(47, nodeNum)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    fun encodeAdminRemoveIgnoredNode(nodeNum: UInt, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeUInt32Field(48, nodeNum)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /**
     * [AdminMessage.nodedb_reset](https://github.com/meshtastic/protobufs/blob/master/meshtastic/admin.proto):
     * при [preserveFavorites] == true избранные узлы сохраняются при сбросе NodeDB.
     */
    fun encodeAdminNodedbReset(preserveFavorites: Boolean, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeBoolField(100, preserveFavorites)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /**
     * [AdminMessage.ota_request] — перезагрузка в режим OTA по BLE (ESP32), как в типичном mesh-клиенте Android.
     * [ota_hash] — 32 байта SHA-256 прошивки.
     */
    fun encodeAdminOtaBleRequest(otaHashSha256: ByteArray, device: LoRaDeviceParams): ByteArray {
        require(otaHashSha256.size == 32) { "ota_hash must be 32 bytes" }
        val adminPayload = MeshWireProtobufWriter().apply {
            writeEmbeddedMessage(102) {
                writeEnumField(1, 1)
                writeLengthDelimitedField(2, otaHashSha256)
            }
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /** [AdminMessage.reboot_seconds] (97) — перезагрузка через [seconds] сек. (отрицательное значение отменяет). */
    fun encodeAdminRebootSeconds(seconds: Int, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeInt32Field(97, seconds)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /** [AdminMessage.shutdown_seconds] (98) — выключение через [seconds] сек. (отрицательное значение отменяет). */
    fun encodeAdminShutdownSeconds(seconds: Int, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeInt32Field(98, seconds)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /**
     * [AdminMessage.factory_reset_device] (94) — полный сброс, включая BLE-связки.
     * Значение int32 как в прошивке mesh (обычно 0).
     */
    fun encodeAdminFactoryResetDevice(arg: Int, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeInt32Field(94, arg)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }

    /**
     * [AdminMessage.factory_reset_config] (99) — сброс конфигурации, BLE-связки сохраняются.
     */
    fun encodeAdminFactoryResetConfig(arg: Int, device: LoRaDeviceParams): ByteArray {
        val adminPayload = MeshWireProtobufWriter().apply {
            writeInt32Field(99, arg)
        }.toByteArray()
        return encodeAdminAppMeshToRadio(adminPayload, device)
    }
}
