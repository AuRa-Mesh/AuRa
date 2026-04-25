package com.example.aura.bluetooth

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.aura.meshwire.MeshStoredChannel
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.ParsedMeshDataPayload
import java.nio.charset.StandardCharsets
import java.util.Locale

object MeshChannelMessaging {

    private val mainHandler = Handler(Looper.getMainLooper())

    val BROADCAST_TO: UInt = MeshWireLoRaToRadioEncoder.BROADCAST_NODE_NUM

    fun matchesChannel(p: ParsedMeshDataPayload, ch: MeshStoredChannel): Boolean {
        val want = ch.index.toUInt()
        val got = p.channel
        return when {
            want == 0U -> got == null || got == 0U
            else -> got == want
        }
    }

    /**
     * Трафик «по каналу», а не DM на конкретный nodenum.
     * Broadcast = [BROADCAST_TO]; `to == 0` на практике тоже «как ещё не DM» (см. MeshService handleToRadio в прошивке).
     */
    fun isLikelyChannelMeshTraffic(p: ParsedMeshDataPayload): Boolean {
        val to = p.to ?: return true
        if (to == 0u) return true
        return to == BROADCAST_TO
    }

    /**
     * Входящий голос по каналу: кроме broadcast / to=0, пакет иногда приходит с [ParsedMeshDataPayload.to]
     * равным nodenum **получателя** (так отдаёт прошивка / ретрансляция), иначе [isLikelyChannelMeshTraffic] отбросит его.
     */
    fun allowsInboundChannelVoice(p: ParsedMeshDataPayload, localNodeNum: UInt?): Boolean {
        if (isLikelyChannelMeshTraffic(p)) return true
        val to = p.to ?: return false
        return localNodeNum != null && to == localNodeNum
    }

    fun sendChannelText(
        context: Context,
        deviceAddress: String,
        channelIndex: Int,
        text: String,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        val trimmed = MeshWireLoRaToRadioEncoder.truncateMeshUtf8(
            text.trim(),
            MeshWireLoRaToRadioEncoder.MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES,
        )
        if (trimmed.isEmpty()) {
            mainHandler.post { onDone(false, "Пустое сообщение") }
            return
        }
        val utf8 = trimmed.toByteArray(StandardCharsets.UTF_8)
        val (port, body) = MeshWireLoRaToRadioEncoder.meshTextPortAndPayload(utf8)
        val payload = MeshWireLoRaToRadioEncoder.encodeDecodedDataPacketToRadio(
            portnum = port,
            payload = body,
            to = BROADCAST_TO,
            channelIndex = channelIndex.toUInt(),
            wantAck = false,
        )
        val appCtx = context.applicationContext
        MeshGattToRadioWriter(appCtx).writeToradio(deviceAddress, payload) { ok, err ->
            mainHandler.post { onDone(ok, err) }
        }
    }

    /**
     * Личное сообщение на узел [targetNodeId] (nodenum): `MeshPacket.to = targetNodeId`, [want_ack] = true.
     * Шифрование — на стороне прошивки (PKI); клиент отдаёт decoded Data.
     */
    fun sendDirectMessage(
        context: Context,
        deviceAddress: String,
        channelIndex: Int,
        targetNodeId: UInt,
        text: String,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        sendDirectTextToNode(context, deviceAddress, channelIndex, targetNodeId, text, onDone)
    }

    /**
     * Текст в личку на [toNodeNum] (ответ на DM из уведомления).
     */
    fun sendDirectTextToNode(
        context: Context,
        deviceAddress: String,
        channelIndex: Int,
        toNodeNum: UInt,
        text: String,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        val trimmed = MeshWireLoRaToRadioEncoder.truncateMeshUtf8(
            text.trim(),
            MeshWireLoRaToRadioEncoder.MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES,
        )
        if (trimmed.isEmpty()) {
            mainHandler.post { onDone(false, "Пустое сообщение") }
            return
        }
        val utf8 = trimmed.toByteArray(StandardCharsets.UTF_8)
        val (port, body) = MeshWireLoRaToRadioEncoder.meshTextPortAndPayload(utf8)
        val (bytes, _) = MeshWireLoRaToRadioEncoder.encodeTextMessageToRadioWithIdAndPort(
            portnum = port,
            payload = body,
            channelIndex = channelIndex.toUInt(),
            to = toNodeNum,
            wantAck = true,
        )
        val appCtx = context.applicationContext
        MeshGattToRadioWriter(appCtx).writeToradio(deviceAddress, bytes) { ok, err ->
            mainHandler.post { onDone(ok, err) }
        }
    }

    /**
     * Голос: бинарные фрагменты на [MeshWireLoRaToRadioEncoder.PORTNUM_PRIVATE_APP].
     * [onBeforeQueue] вызывается с id **последнего** фрагмента до записи в ToRadio — для связи с ROUTING_APP (статусы доставки).
     */
    fun sendVoicePackets(
        context: Context,
        deviceAddress: String,
        channelIndex: Int,
        frames: List<ByteArray>,
        onBeforeQueue: (lastPacketId: UInt) -> Unit,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        if (frames.isEmpty()) {
            mainHandler.post { onDone(false, "Нет голосовых данных") }
            return
        }
        val pairs = frames.map { frame ->
            MeshWireLoRaToRadioEncoder.encodeDecodedDataPacketToRadioWithId(
                portnum = MeshWireLoRaToRadioEncoder.PORTNUM_PRIVATE_APP,
                payload = frame,
                to = BROADCAST_TO,
                channelIndex = channelIndex.toUInt(),
                wantAck = true,
            )
        }
        val payloads = pairs.map { it.first }
        val lastPacketId = pairs.last().second
        onBeforeQueue(lastPacketId)
        MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
            deviceAddress,
            payloads,
            delayBetweenWritesMs = 180L,
        ) { ok, err ->
            mainHandler.post { onDone(ok, err) }
        }
    }

    /** Голосовые фрагменты в личку на [toNodeNum] (не broadcast). */
    fun sendVoicePacketsDirect(
        context: Context,
        deviceAddress: String,
        channelIndex: Int,
        toNodeNum: UInt,
        frames: List<ByteArray>,
        onBeforeQueue: (lastPacketId: UInt) -> Unit,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        if (frames.isEmpty()) {
            mainHandler.post { onDone(false, "Нет голосовых данных") }
            return
        }
        val ch = channelIndex.toUInt()
        val pairs = frames.map { frame ->
            MeshWireLoRaToRadioEncoder.encodeDecodedDataPacketToRadioWithId(
                portnum = MeshWireLoRaToRadioEncoder.PORTNUM_PRIVATE_APP,
                payload = frame,
                to = toNodeNum,
                channelIndex = ch,
                wantAck = true,
            )
        }
        val payloads = pairs.map { it.first }
        val lastPacketId = pairs.last().second
        onBeforeQueue(lastPacketId)
        MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
            deviceAddress,
            payloads,
            delayBetweenWritesMs = 180L,
        ) { ok, err ->
            mainHandler.post { onDone(ok, err) }
        }
    }

    fun sendImageLines(
        context: Context,
        deviceAddress: String,
        channelIndex: Int,
        lines: List<String>,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        if (lines.isEmpty()) {
            mainHandler.post { onDone(false, "Нет данных для отправки") }
            return
        }
        val payloads = lines.map { line ->
            MeshWireLoRaToRadioEncoder.encodeChannelTextMessageToRadio(
                line.toByteArray(StandardCharsets.UTF_8),
                channelIndex.toUInt(),
            )
        }
        MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
            deviceAddress,
            payloads,
            delayBetweenWritesMs = 150L,
        ) { ok, err ->
            mainHandler.post { onDone(ok, err) }
        }
    }

    /**
     * Чанки картинки (TEXT) на узел [toNodeNum] — как личный текст, с wantAck.
     */
    fun sendImageLinesDirect(
        context: Context,
        deviceAddress: String,
        channelIndex: Int,
        toNodeNum: UInt,
        lines: List<String>,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        if (lines.isEmpty()) {
            mainHandler.post { onDone(false, "Нет данных для отправки") }
            return
        }
        val ch = channelIndex.toUInt()
        val to = toNodeNum
        val pairs = lines.map { line ->
            MeshWireLoRaToRadioEncoder.encodeTextMessageToRadioWithId(
                textUtf8 = line.toByteArray(StandardCharsets.UTF_8),
                channelIndex = ch,
                to = to,
                wantAck = true,
            )
        }
        val payloads = pairs.map { it.first }
        MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
            deviceAddress,
            payloads,
            delayBetweenWritesMs = 150L,
        ) { ok, err ->
            mainHandler.post { onDone(ok, err) }
        }
    }

    fun pollInboundPayloads(
        context: Context,
        deviceAddress: String,
        onChunk: (List<ParsedMeshDataPayload>) -> Unit,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        MeshGattToRadioWriter(context.applicationContext).drainFromRadioOnly(
            deviceAddress,
            onFrame = { frame ->
                val list = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(frame)
                if (list.isNotEmpty()) {
                    mainHandler.post { onChunk(list) }
                }
            },
            onComplete = { ok, err ->
                mainHandler.post { onDone(ok, err) }
            },
        )
    }
}
