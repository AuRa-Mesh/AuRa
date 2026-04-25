package com.example.aura.meshwire

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Собирает все [NodeInfo] (FromRadio поле 4) после want_config — как начальное наполнение
 * списка узлов в типичном mesh-клиенте до длинного жизненного цикла сервиса.
 */
internal class MeshWireNodeListAccumulator {
    var myNodeNum: Long? = null

    /**
     * Устарело: NodeInfo обрабатываются всегда (как потоковое наполнение NodeDB в официальном клиенте).
     * Оставлено для совместимости со старым вызовом [fetchMeshWireNodes].
     */
    var acceptNodeInfo: Boolean = true

    /**
     * Пока `false`, в last_heard берём время приёма кадра на телефоне (RTC ноды может быть в 1970).
     * После `set_time_only` репозиторий выставляет `true` — тогда мержим last_heard / rx_time / time из Position.
     */
    var trustNodeLastHeardTimestamps: Boolean = false

    /** Последний [FromRadio.config_complete_id] в этом кадре; сбрасывается в [pollConfigCompleteId]. */
    private var lastConfigCompleteId: Long? = null

    private val byNum = linkedMapOf<Long, NodeUserFields>()

    fun consumeFromRadio(
        bytes: ByteArray,
        receiveEpochSec: Long = System.currentTimeMillis() / 1000L,
        appContext: Context? = null,
    ) {
        parseFromRadioTop(bytes, receiveEpochSec, appContext)
        ingestDecodedMeshActivityPackets(bytes, receiveEpochSec, appContext)
    }

    fun pollConfigCompleteId(): Long? {
        val v = lastConfigCompleteId
        lastConfigCompleteId = null
        return v
    }

    fun toSummaries(): List<MeshWireNodeSummary> =
        byNum.entries
            .sortedBy { it.key }
            .map { (num, u) -> entryToSummary(num, u) }

    /** Убрать узел из локального снимка (кэш приложения; эфирный NodeDB на радио не трогаем). */
    fun removeNodeNum(nodeNum: Long) {
        byNum.remove(nodeNum)
    }

    /** Восстановление NodeDB из диска при старте сессии (до прихода свежих кадров). */
    fun seedFromSummaries(summaries: List<MeshWireNodeSummary>) {
        for (s in summaries) {
            val delta = summaryToNodeUserFields(s)
            val prev = byNum[s.nodeNum]
            byNum[s.nodeNum] =
                when {
                    prev == null -> delta
                    myNodeNum != null && s.nodeNum == myNodeNum -> mergeSelfNodeFromRemoteNodeInfo(prev, delta)
                    else -> mergeNodeFields(prev, delta)
                }
        }
    }

    private fun summaryToNodeUserFields(s: MeshWireNodeSummary): NodeUserFields {
        val hex = s.nodeIdHex
        val longN = s.longName.trim().takeIf { it.isNotEmpty() && !it.equals(hex, ignoreCase = true) }
        return NodeUserFields(
            longName = longN,
            shortName = s.shortName.trim().takeIf { it.isNotEmpty() && it != "?" },
            userId = s.userId,
            hwModel = MeshWireHardwareModel.nameToWireCodeOrNull(s.hardwareModel),
            role = deviceRoleLabelToWire(s.roleLabel),
            latitude = s.latitude,
            longitude = s.longitude,
            altitudeMeters = s.altitudeMeters,
            snr = s.lastSnrDb,
            lastHeardEpochSec = s.lastSeenEpochSec,
            batteryPercent = s.batteryPercent,
            voltage = s.batteryVoltage,
            isCharging = s.isCharging,
            channelUtil = s.channelUtilization,
            airUtilTx = s.airUtilTx,
            channel = s.channel,
            viaMqtt = s.viaMqtt,
            hopsAway = s.hopsAway,
            meshHopLimit = s.meshHopLimit,
            meshHopStart = s.meshHopStart,
            isFavorite = s.isFavorite,
            isIgnored = s.isIgnored,
            publicKey = s.publicKeyB64?.let { b64 ->
                try {
                    Base64.decode(b64, Base64.DEFAULT).takeIf { d -> d.size == 32 }
                } catch (_: IllegalArgumentException) {
                    null
                }
            },
        )
    }

    private fun entryToSummary(num: Long, u: NodeUserFields): MeshWireNodeSummary {
        val hw = MeshWireHardwareModel.wireCodeToName(u.hwModel ?: 0)
        val idHex = "!%08x".format(num)
        val nowSec = System.currentTimeMillis() / 1000L
        val self = myNodeNum != null && num == myNodeNum
        // Своя нода в NodeDB часто без last_heard; для счётчика «онлайн» считаем «слышали сейчас».
        val lastSeen = when {
            self && (u.lastHeardEpochSec == null || u.lastHeardEpochSec <= 0L) -> nowSec
            else -> u.lastHeardEpochSec
        }
        val lastLabel = when {
            self -> "сейчас"
            lastSeen == null || lastSeen <= 0L -> "—"
            else -> formatAgo(nowSec - lastSeen)
        }
        val longNm = u.longName?.trim()?.takeIf { it.isNotEmpty() }
        val shortNm = u.shortName?.trim()?.takeIf { it.isNotEmpty() } ?: "?"
        return MeshWireNodeSummary(
            nodeNum = num,
            nodeIdHex = idHex,
            longName = longNm ?: idHex,
            shortName = shortNm,
            hardwareModel = hw,
            roleLabel = deviceRoleWireToLabel(u.role),
            userId = u.userId?.ifBlank { null },
            lastHeardLabel = lastLabel,
            lastSeenEpochSec = lastSeen,
            latitude = u.latitude,
            longitude = u.longitude,
            altitudeMeters = u.altitudeMeters,
            batteryPercent = u.batteryPercent,
            batteryVoltage = u.voltage,
            isCharging = u.isCharging,
            channelUtilization = u.channelUtil,
            airUtilTx = u.airUtilTx,
            channel = u.channel,
            hopsAway = u.hopsAway,
            meshHopLimit = u.meshHopLimit,
            meshHopStart = u.meshHopStart,
            viaMqtt = u.viaMqtt == true,
            isFavorite = u.isFavorite == true,
            isIgnored = u.isIgnored == true,
            lastSnrDb = u.snr,
            publicKeyB64 = u.publicKey?.takeIf { it.size == 32 }?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
        )
    }

    private fun formatAgo(deltaSec: Long): String =
        when {
            deltaSec < 60L -> "${deltaSec} с"
            deltaSec < 3600L -> "${deltaSec / 60} мин"
            deltaSec < 86400L -> "${deltaSec / 3600} ч"
            else -> "${deltaSec / 86400} д"
        }

    private fun parseFromRadioTop(bytes: ByteArray, receiveEpochSec: Long, appContext: Context?) {
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    if (fieldNum == 7) {
                        lastConfigCompleteId = v
                        if (v == MeshWireWantConfigHandshake.CONFIG_NONCE.toLong()) {
                            acceptNodeInfo = true
                        }
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
                    when (fieldNum) {
                        3 -> parseMyInfo(sub)
                        4 -> parseNodeInfoAny(sub, receiveEpochSec, appContext)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseMyInfo(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            if (fieldNum == 1 && wire == 0) {
                val (v, n) = readVarint(bytes, i)
                myNodeNum = v
                i += n
                continue
            }
            when (wire) {
                0 -> {
                    while (i < bytes.size && (bytes[i++].toInt() and 0x80) != 0) { }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    i += ln
                }
                else -> break
            }
        }
    }

    private data class NodeInfoWireParts(
        val num: Long,
        val userSub: ByteArray?,
        val positionSub: ByteArray?,
        val snr: Float?,
        val lastHeardSec: Long?,
        val metricsSub: ByteArray?,
        val channel: UInt?,
        val viaMqtt: Boolean?,
        val hopsAway: UInt?,
        val isFavorite: Boolean?,
        val isIgnored: Boolean?,
    )

    /** Разбор тела mesh.proto [NodeInfo] (в т.ч. вложенный [User] с public_key). */
    private fun parseNodeInfoWireBytes(bytes: ByteArray): NodeInfoWireParts? {
        var i = 0
        var num: Long? = null
        var userSub: ByteArray? = null
        var positionSub: ByteArray? = null
        var snr: Float? = null
        var lastHeardSec: Long? = null
        var metricsSub: ByteArray? = null
        var channel: UInt? = null
        var viaMqtt: Boolean? = null
        var hopsAway: UInt? = null
        var isFavorite: Boolean? = null
        var isIgnored: Boolean? = null
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    when (fieldNum) {
                        1 -> num = v
                        // Редкие/старые прошивки могли слать last_heard как uint32 (varint), не fixed32.
                        5 -> lastHeardSec = coalesceLastHeardEpochSec(lastHeardSec, v)
                        7 -> channel = v.toUInt()
                        8 -> viaMqtt = v != 0L
                        9 -> hopsAway = v.toUInt()
                        10 -> isFavorite = v != 0L
                        11 -> isIgnored = v != 0L
                    }
                }
                5 -> {
                    if (i + 4 > bytes.size) break
                    when (fieldNum) {
                        4 -> snr = readFloat32(bytes, i)
                        5 -> lastHeardSec = coalesceLastHeardEpochSec(lastHeardSec, readFixed32UInt(bytes, i))
                    }
                    i += 4
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        2 -> userSub = sub
                        3 -> positionSub = sub
                        6 -> metricsSub = sub
                    }
                }
                else -> break
            }
        }
        val n = num ?: return null
        return NodeInfoWireParts(
            num = n and 0xFFFF_FFFFL,
            userSub = userSub,
            positionSub = positionSub,
            snr = snr,
            lastHeardSec = lastHeardSec,
            metricsSub = metricsSub,
            channel = channel,
            viaMqtt = viaMqtt,
            hopsAway = hopsAway,
            isFavorite = isFavorite,
            isIgnored = isIgnored,
        )
    }

    /**
     * @return `true` если запись в NodeDB обновлена; `false` — вызывающий может разобрать payload иначе (например User в NODEINFO_APP).
     */
    private fun ingestNodeInfoFromWire(
        parts: NodeInfoWireParts,
        receiveEpochSec: Long,
        envelope: ParsedMeshDataPayload?,
        appContext: Context?,
    ): Boolean {
        val nFromParts = parts.num and 0xFFFF_FFFFL
        // В эфире NODEINFO_APP — это protobuf User; первый varint в буфере часто даёт ложный num=0 — не мержим как NodeInfo.
        if (envelope != null && nFromParts == 0L) {
            return false
        }
        val nFromEnvelope = envelope?.logicalFrom()?.toLong()?.let { it and 0xFFFF_FFFFL }
        val n =
            when {
                nFromEnvelope != null && nFromEnvelope != 0L -> nFromEnvelope
                else -> nFromParts
            }
        if (n == 0L) return false
        val pos = parts.positionSub?.let { parsePosition(it) }
        val dm = parts.metricsSub?.let { parseDeviceMetrics(it) }
        val userPart = parts.userSub?.let { parseUserFields(it) } ?: NodeUserFields()
        val rxSec = envelope?.rxTimeSec?.toLong()?.takeIf { it > 0L }?.let { it and 0xFFFF_FFFFL }
        val syncedParts = if (trustNodeLastHeardTimestamps) {
            listOfNotNull(
                parts.lastHeardSec,
                rxSec,
                userPart.lastHeardEpochSec?.takeIf { it > 0L },
                pos?.timeEpochSec?.takeIf { it > 0L },
            )
        } else {
            emptyList()
        }
        val lastHeardMerged = (listOf(receiveEpochSec) + syncedParts).maxOrNull() ?: receiveEpochSec
        val delta = userPart.copy(
            latitude = pos?.latitude ?: userPart.latitude,
            longitude = pos?.longitude ?: userPart.longitude,
            altitudeMeters = pos?.altitudeMeters ?: userPart.altitudeMeters,
            snr = envelope?.rxSnr ?: parts.snr ?: userPart.snr,
            lastHeardEpochSec = lastHeardMerged,
            batteryPercent = dm?.battery ?: userPart.batteryPercent,
            voltage = dm?.voltage ?: userPart.voltage,
            isCharging = dm?.isCharging ?: userPart.isCharging,
            channelUtil = dm?.channelUtil ?: userPart.channelUtil,
            airUtilTx = dm?.airUtilTx ?: userPart.airUtilTx,
            channel = envelope?.channel ?: parts.channel ?: userPart.channel,
            viaMqtt = if (envelope?.viaMqtt == true) true else parts.viaMqtt ?: userPart.viaMqtt,
            hopsAway = parts.hopsAway ?: userPart.hopsAway,
            isFavorite = parts.isFavorite ?: userPart.isFavorite,
            isIgnored = parts.isIgnored ?: userPart.isIgnored,
            meshHopLimit = envelope?.hopLimit ?: userPart.meshHopLimit,
            meshHopStart = envelope?.hopStart ?: userPart.meshHopStart,
        )
        val prev = byNum[n]
        byNum[n] =
            when {
                prev == null -> delta
                myNodeNum != null && n == myNodeNum -> mergeSelfNodeFromRemoteNodeInfo(prev, delta)
                else -> mergeNodeFields(prev, delta)
            }
        return true
    }

    private fun parseNodeInfoAny(bytes: ByteArray, receiveEpochSec: Long, appContext: Context?) {
        val parts = parseNodeInfoWireBytes(bytes) ?: return
        ingestNodeInfoFromWire(parts, receiveEpochSec, envelope = null, appContext)
    }

    /**
     * `MeshPacket.decoded` из FromRadio: NODEINFO / POSITION / TELEMETRY — обновление last_heard по [from].
     * ADMIN_APP не трогаем — конфиг в [com.example.aura.bluetooth.MeshNodePassiveFromRadioSink].
     */
    private fun ingestDecodedMeshActivityPackets(frame: ByteArray, receiveEpochSec: Long, appContext: Context?) {
        val payloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(frame)
        for (p in payloads) {
            when (p.portnum) {
                MeshWireFromRadioMeshPacketParser.PORTNUM_NODEINFO_APP ->
                    applyNodeInfoAppMeshPacket(p, receiveEpochSec, appContext)
                MeshWireFromRadioMeshPacketParser.PORTNUM_POSITION_APP ->
                    applyMeshPacketPosition(p, receiveEpochSec)
                MeshWireFromRadioMeshPacketParser.PORTNUM_TELEMETRY ->
                    applyMeshPacketTelemetry(p, receiveEpochSec)
                else ->
                    if (p.logicalFrom() != null) applyGenericMeshPacketBump(p, receiveEpochSec)
            }
        }
    }

    /**
     * NODEINFO_APP: в эфире payload — protobuf User, номер узла — logicalFrom у пакета.
     * Полный NodeInfo (num + user + …) на всякий случай разбираем первым.
     */
    private fun applyNodeInfoAppMeshPacket(p: ParsedMeshDataPayload, receiveEpochSec: Long, appContext: Context?) {
        val wireParts = parseNodeInfoWireBytes(p.payload)
        if (wireParts != null && ingestNodeInfoFromWire(wireParts, receiveEpochSec, envelope = p, appContext)) {
            return
        }
        val from = p.logicalFrom() ?: return
        val n = from.toLong() and 0xFFFF_FFFFL
        if (n == 0L) return
        val userPart = parseUserFields(p.payload)
        if (!nodeUserFieldsHasProfileSignal(userPart)) {
            applyGenericMeshPacketBump(p, receiveEpochSec)
            return
        }
        ingestUserMeshEnvelope(n, userPart, receiveEpochSec, envelope = p, appContext)
    }

    private fun nodeUserFieldsHasProfileSignal(u: NodeUserFields): Boolean =
        !u.longName.isNullOrBlank() ||
            !u.shortName.isNullOrBlank() ||
            !u.userId.isNullOrBlank() ||
            u.hwModel != null ||
            u.role != null ||
            u.publicKey != null

    /** User + поля приёма из MeshPacket — поведение как у типичного mesh-клиента для NODEINFO_APP. */
    private fun ingestUserMeshEnvelope(
        nodeNum: Long,
        userPart: NodeUserFields,
        receiveEpochSec: Long,
        envelope: ParsedMeshDataPayload,
        appContext: Context?,
    ) {
        if (nodeNum == 0L) return
        val rxSec = envelope.rxTimeSec?.toLong()?.takeIf { it > 0L }?.let { it and 0xFFFF_FFFFL }
        val syncedParts =
            if (trustNodeLastHeardTimestamps) {
                listOfNotNull(rxSec, userPart.lastHeardEpochSec?.takeIf { it > 0L })
            } else {
                emptyList()
            }
        val lastHeardMerged = (listOf(receiveEpochSec) + syncedParts).maxOrNull() ?: receiveEpochSec
        val delta =
            userPart.copy(
                snr = envelope.rxSnr ?: userPart.snr,
                lastHeardEpochSec = lastHeardMerged,
                channel = envelope.channel ?: userPart.channel,
                viaMqtt = if (envelope.viaMqtt) true else userPart.viaMqtt,
                meshHopLimit = envelope.hopLimit ?: userPart.meshHopLimit,
                meshHopStart = envelope.hopStart ?: userPart.meshHopStart,
            )
        val prev = byNum[nodeNum]
        byNum[nodeNum] =
            when {
                prev == null -> delta
                myNodeNum != null && nodeNum == myNodeNum -> mergeSelfNodeFromRemoteNodeInfo(prev, delta)
                else -> mergeNodeFields(prev, delta)
            }
    }

    private fun mergeLastHeardFromMeshPacket(p: ParsedMeshDataPayload, receiveEpochSec: Long, positionTime: Long?): Long {
        val rxSec = if (trustNodeLastHeardTimestamps) {
            p.rxTimeSec?.toLong()?.takeIf { it > 0L }?.let { it and 0xFFFF_FFFFL }
        } else {
            null
        }
        return listOfNotNull(receiveEpochSec, rxSec, positionTime).maxOrNull() ?: receiveEpochSec
    }

    /** POSITION_APP — координаты и контакт; дистанция считается в UI от «себя». */
    private fun applyMeshPacketPosition(p: ParsedMeshDataPayload, receiveEpochSec: Long) {
        val from = p.logicalFrom() ?: return
        val n = from.toLong() and 0xFFFF_FFFFL
        if (n == 0L) return
        val pos = parsePosition(p.payload)
        val posTime = if (trustNodeLastHeardTimestamps) pos?.timeEpochSec?.takeIf { it > 0L } else null
        val merged = mergeLastHeardFromMeshPacket(p, receiveEpochSec, posTime)
        val delta = NodeUserFields(
            lastHeardEpochSec = merged,
            snr = p.rxSnr,
            latitude = pos?.latitude,
            longitude = pos?.longitude,
            altitudeMeters = pos?.altitudeMeters,
            channel = p.channel,
            viaMqtt = if (p.viaMqtt) true else null,
            meshHopLimit = p.hopLimit,
            meshHopStart = p.hopStart,
        )
        upsertMeshDelta(n, delta)
    }

    /** TELEMETRY_APP — только батарея/напряжение/заряд из DeviceMetrics; контакт и SNR с пакета. */
    private fun applyMeshPacketTelemetry(p: ParsedMeshDataPayload, receiveEpochSec: Long) {
        val from = p.logicalFrom() ?: return
        val n = from.toLong() and 0xFFFF_FFFFL
        if (n == 0L) return
        val dm = parseTelemetryDeviceMetricsPayload(p.payload)
        val merged = mergeLastHeardFromMeshPacket(p, receiveEpochSec, null)
        val delta = NodeUserFields(
            lastHeardEpochSec = merged,
            snr = p.rxSnr,
            batteryPercent = dm?.battery,
            voltage = dm?.voltage,
            isCharging = dm?.isCharging,
            channel = p.channel,
            viaMqtt = if (p.viaMqtt) true else null,
            meshHopLimit = p.hopLimit,
            meshHopStart = p.hopStart,
        )
        upsertMeshDelta(n, delta)
    }

    /** Любой другой portnum с [from] — обновляем lastSeen и SNR. */
    private fun applyGenericMeshPacketBump(p: ParsedMeshDataPayload, receiveEpochSec: Long) {
        val from = p.logicalFrom() ?: return
        val n = from.toLong() and 0xFFFF_FFFFL
        if (n == 0L) return
        val merged = mergeLastHeardFromMeshPacket(p, receiveEpochSec, null)
        val delta = NodeUserFields(
            lastHeardEpochSec = merged,
            snr = p.rxSnr,
            channel = p.channel,
            viaMqtt = if (p.viaMqtt) true else null,
            meshHopLimit = p.hopLimit,
            meshHopStart = p.hopStart,
        )
        upsertMeshDelta(n, delta)
    }

    private fun upsertMeshDelta(n: Long, delta: NodeUserFields) {
        if (n == 0L) return
        val prev = byNum[n]
        byNum[n] =
            when {
                prev == null -> delta
                myNodeNum != null && n == myNodeNum -> mergeSelfNodeFromRemoteNodeInfo(prev, delta)
                else -> mergeNodeFields(prev, delta)
            }
    }

    /** Telemetry oneof: вложенное device_metrics (поле 2). */
    private fun parseTelemetryDeviceMetricsPayload(tel: ByteArray): DeviceMetricsParsed? {
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
                    if (fieldNum == 2) return parseDeviceMetrics(sub)
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

    /**
     * Своя нода: не даём «чужому» дампу NodeDB затереть идентичность из MyNodeInfo/локального профиля;
     * метрики (SNR, last heard, батарея) с сети — можно обновлять.
     */
    private fun mergeSelfNodeFromRemoteNodeInfo(prev: NodeUserFields, remote: NodeUserFields): NodeUserFields =
        prev.copy(
            longName = prev.longName ?: remote.longName,
            shortName = prev.shortName ?: remote.shortName,
            userId = prev.userId ?: remote.userId,
            hwModel = prev.hwModel ?: remote.hwModel,
            role = prev.role ?: remote.role,
            latitude = remote.latitude ?: prev.latitude,
            longitude = remote.longitude ?: prev.longitude,
            altitudeMeters = remote.altitudeMeters ?: prev.altitudeMeters,
            snr = remote.snr ?: prev.snr,
            lastHeardEpochSec = listOfNotNull(remote.lastHeardEpochSec, prev.lastHeardEpochSec).maxOrNull(),
            batteryPercent = remote.batteryPercent ?: prev.batteryPercent,
            voltage = remote.voltage ?: prev.voltage,
            isCharging = remote.isCharging ?: prev.isCharging,
            channelUtil = remote.channelUtil ?: prev.channelUtil,
            airUtilTx = remote.airUtilTx ?: prev.airUtilTx,
            channel = remote.channel ?: prev.channel,
            viaMqtt = remote.viaMqtt ?: prev.viaMqtt,
            hopsAway = remote.hopsAway ?: prev.hopsAway,
            meshHopLimit = remote.meshHopLimit ?: prev.meshHopLimit,
            meshHopStart = remote.meshHopStart ?: prev.meshHopStart,
            isFavorite = prev.isFavorite ?: remote.isFavorite,
            isIgnored = prev.isIgnored ?: remote.isIgnored,
            publicKey = prev.publicKey ?: remote.publicKey,
        )

    private fun coalesceLastHeardEpochSec(prev: Long?, raw: Long): Long? {
        val cand = raw and 0xFFFF_FFFFL
        if (cand <= 0L) return prev
        val p = prev?.takeIf { it > 0L } ?: return cand
        return maxOf(p, cand)
    }

    private fun mergeNodeFields(prev: NodeUserFields, u: NodeUserFields): NodeUserFields =
        prev.copy(
            longName = u.longName ?: prev.longName,
            shortName = u.shortName ?: prev.shortName,
            userId = u.userId ?: prev.userId,
            hwModel = u.hwModel ?: prev.hwModel,
            role = u.role ?: prev.role,
            latitude = u.latitude ?: prev.latitude,
            longitude = u.longitude ?: prev.longitude,
            altitudeMeters = u.altitudeMeters ?: prev.altitudeMeters,
            snr = u.snr ?: prev.snr,
            lastHeardEpochSec = listOfNotNull(u.lastHeardEpochSec, prev.lastHeardEpochSec).maxOrNull(),
            batteryPercent = u.batteryPercent ?: prev.batteryPercent,
            voltage = u.voltage ?: prev.voltage,
            isCharging = u.isCharging ?: prev.isCharging,
            channelUtil = u.channelUtil ?: prev.channelUtil,
            airUtilTx = u.airUtilTx ?: prev.airUtilTx,
            channel = u.channel ?: prev.channel,
            viaMqtt = u.viaMqtt ?: prev.viaMqtt,
            hopsAway = u.hopsAway ?: prev.hopsAway,
            meshHopLimit = u.meshHopLimit ?: prev.meshHopLimit,
            meshHopStart = u.meshHopStart ?: prev.meshHopStart,
            isFavorite = u.isFavorite ?: prev.isFavorite,
            isIgnored = u.isIgnored ?: prev.isIgnored,
            publicKey = u.publicKey ?: prev.publicKey,
        )

    private data class NodeUserFields(
        val longName: String? = null,
        val shortName: String? = null,
        val userId: String? = null,
        val hwModel: Int? = null,
        val role: Int? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val altitudeMeters: Int? = null,
        val snr: Float? = null,
        val lastHeardEpochSec: Long? = null,
        val batteryPercent: Int? = null,
        val voltage: Float? = null,
        val isCharging: Boolean? = null,
        val channelUtil: Float? = null,
        val airUtilTx: Float? = null,
        val channel: UInt? = null,
        val viaMqtt: Boolean? = null,
        val hopsAway: UInt? = null,
        val meshHopLimit: UInt? = null,
        val meshHopStart: UInt? = null,
        val isFavorite: Boolean? = null,
        val isIgnored: Boolean? = null,
        /** User.public_key (32 bytes X25519), из NodeInfo.user. */
        val publicKey: ByteArray? = null,
    )

    private data class ParsedPosition(
        val latitude: Double?,
        val longitude: Double?,
        val altitudeMeters: Int?,
        /** Position.time — секунды с 1970 (mesh.proto), подсказка для «последний раз», если last_heard пустой. */
        val timeEpochSec: Long? = null,
    )

    private fun parsePosition(bytes: ByteArray): ParsedPosition? {
        var latI: Int? = null
        var lonI: Int? = null
        var altitude: Int? = null
        var timeSec: Long? = null
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                5 -> {
                    if (i + 4 > bytes.size) break
                    when (fieldNum) {
                        1 -> latI = readLeInt32(bytes, i)
                        2 -> lonI = readLeInt32(bytes, i)
                        4 -> timeSec = readFixed32UInt(bytes, i)
                    }
                    i += 4
                }
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    if (fieldNum == 3) {
                        altitude = (v shl 32 shr 32).toInt()
                    }
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    i += ln
                }
                else -> break
            }
        }
        val lat = latI?.let { it * 1e-7 }
        val lon = lonI?.let { it * 1e-7 }
        if (lat == null || lon == null) {
            if (altitude == null && timeSec == null) return null
            return ParsedPosition(lat, lon, altitude, timeSec)
        }
        return ParsedPosition(lat, lon, altitude, timeSec)
    }

    private data class DeviceMetricsParsed(
        val battery: Int?,
        val voltage: Float?,
        val channelUtil: Float?,
        val airUtilTx: Float?,
        /** `battery_level` > 100 на эфире mesh = внешнее питание. */
        val isCharging: Boolean? = null,
    )

    private fun parseDeviceMetrics(bytes: ByteArray): DeviceMetricsParsed {
        var bat: Int? = null
        var chg: Boolean? = null
        var voltage: Float? = null
        var chU: Float? = null
        var air: Float? = null
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    if (fieldNum == 1) {
                        val raw = v.toInt()
                        bat = raw.coerceAtMost(100).coerceAtLeast(0)
                        chg = raw > 100
                    }
                }
                5 -> {
                    if (i + 4 > bytes.size) break
                    val f = readFloat32(bytes, i)
                    i += 4
                    when (fieldNum) {
                        2 -> if (voltage == null) voltage = f
                        3 -> chU = f
                        4 -> air = f
                        5 -> voltage = f
                    }
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        return DeviceMetricsParsed(bat, voltage, chU, air, chg)
    }

    private fun parseUserFields(bytes: ByteArray): NodeUserFields {
        var longName: String? = null
        var shortName: String? = null
        var userId: String? = null
        var hwModel: Int? = null
        var role: Int? = null
        var publicKey: ByteArray? = null
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
                        5 -> hwModel = v.toInt()
                        6 -> { /* is_licensed */ }
                        7 -> role = v.toInt()
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val raw = bytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        1 -> userId = runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
                        2 -> longName = runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
                        3 -> shortName = runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
                        8 ->
                            if (ln >= 32) {
                                publicKey = raw.copyOfRange(0, 32)
                            }
                    }
                }
                else -> break
            }
        }
        return NodeUserFields(
            longName,
            shortName,
            userId,
            hwModel,
            role,
            publicKey = publicKey,
        )
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

    private fun readLeInt32(bytes: ByteArray, i: Int): Int =
        (bytes[i].toInt() and 0xFF) or
            ((bytes[i + 1].toInt() and 0xFF) shl 8) or
            ((bytes[i + 2].toInt() and 0xFF) shl 16) or
            ((bytes[i + 3].toInt() and 0xFF) shl 24)

    private fun readFloat32(bytes: ByteArray, i: Int): Float =
        Float.fromBits(readLeInt32(bytes, i))

    private fun readFixed32UInt(bytes: ByteArray, i: Int): Long =
        readLeInt32(bytes, i).toLong() and 0xFFFFFFFFL
}

private fun deviceRoleLabelToWire(label: String): Int? =
    when (label.trim()) {
        "CLIENT" -> 0
        "CLIENT_MUTE" -> 1
        "CLIENT_HIDDEN" -> 2
        "TRACKER" -> 3
        "SENSOR" -> 4
        "TAK" -> 5
        "CLIENT_BASE" -> 6
        "ROUTER" -> 7
        "ROUTER_LATE" -> 8
        "REPEATER" -> 9
        "—", "" -> null
        else -> null
    }

/** Значения [Config.DeviceConfig.Role] (mesh.proto), упрощённо. */
private fun deviceRoleWireToLabel(code: Int?): String {
    if (code == null) return "—"
    return when (code) {
        0 -> "CLIENT"
        1 -> "CLIENT_MUTE"
        2 -> "CLIENT_HIDDEN"
        3 -> "TRACKER"
        4 -> "SENSOR"
        5 -> "TAK"
        6 -> "CLIENT_BASE"
        7 -> "ROUTER"
        8 -> "ROUTER_LATE"
        9 -> "REPEATER"
        else -> "ROLE_$code"
    }
}
