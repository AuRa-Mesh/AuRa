package com.example.aura.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.example.aura.chat.ChannelImageAttachment
import com.example.aura.chat.ChannelVoiceAttachment
import com.example.aura.chat.history.ChatHistoryFileStore
import com.example.aura.history.MessageHistoryRecorder
import com.example.aura.chat.ChatMessageDeliveryStatus
import com.example.aura.chat.ChatProtobufDebugLog
import com.example.aura.chat.MeshWireStatusMapper
import com.example.aura.chat.MeshWireStatusMapper.MeshRoutingUiResult
import com.example.aura.data.local.AuraDatabase
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.data.local.ChatMessageReactionsJson
import com.example.aura.meshwire.MeshReactionEmojiRegistry
import com.example.aura.meshwire.MeshStoredChannel
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireReadReceiptCodec
import com.example.aura.meshwire.MeshWireRoutingPayloadParser
import com.example.aura.meshwire.ParsedMeshDataPayload
import com.example.aura.app.AppForegroundState
import com.example.aura.mesh.PacketDispatcher
import com.example.aura.mesh.incoming.persistInboundChannelTextIfNew
import com.example.aura.mesh.repository.MeshIncomingChatRepository
import com.example.aura.mesh.repository.MessageRepository
import com.example.aura.mesh.transport.BleTransportManager
import com.example.aura.voice.VoiceLoRaFragmentCodec
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val TAG = "ChannelChatManager"

/**
 * Управляет отправкой/приёмом сообщений канала через [NodeGattConnection].
 *
 * Вместо собственного GATT-соединения теперь использует единственное постоянное
 * соединение [NodeGattConnection]:
 * - Регистрирует слушатель входящих кадров в [start].
 * - Ставит исходящие пакеты в очередь через [NodeGattConnection.sendToRadio].
 *
 * LoRa AES: выполняется прошивкой по PSK канала; в ToRadio уходит decoded с plaintext.
 */
class ChannelChatManager(
    private val app: Context,
    db: AuraDatabase,
    deviceAddress: String,
    private val channel: MeshStoredChannel,
    private val localNodeNum: UInt?,
    messageRepository: MessageRepository,
    /** 8 hex — папка [ChatHistoryFileStore] для активной ноды; null — только legacy-путь. */
    private val historyNodeIdHex: String? = null,
) : MeshTextChatSession {

    private val dao = db.channelChatMessageDao()
    private val messageRepository = messageRepository
    private val deviceMacNorm = MeshNodeSyncMemoryStore.normalizeKey(deviceAddress)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pendingAckByPacketId = ConcurrentHashMap<UInt, PendingOutgoingAck>()
    /** Исключаем повторную запись в историю при повторных колбэках доставки. */
    private val historyRecordedOutgoingTextRowIds = ConcurrentHashMap.newKeySet<Long>()
    private val historyRecordedOutgoingVoiceStableIds = ConcurrentHashMap.newKeySet<String>()
    private val outgoing = Channel<OutgoingSendJob>(Channel.UNLIMITED)

    private val jobs = mutableListOf<Job>()

    private val imgReassembler = MeshImageChunkCodec.Reassembler()

    private val _imageAttachments = MutableStateFlow<List<ChannelImageAttachment>>(emptyList())
    override val imageAttachments: StateFlow<List<ChannelImageAttachment>> = _imageAttachments.asStateFlow()

    private val _voiceAttachments = MutableStateFlow<List<ChannelVoiceAttachment>>(emptyList())
    override val voiceAttachments: StateFlow<List<ChannelVoiceAttachment>> = _voiceAttachments.asStateFlow()

    override val messages = dao.observe(deviceMacNorm, channel.index)

    fun reactionStorageKey(targetMessageFrom: UInt, targetPacketId: UInt): String =
        "${targetMessageFrom.toLong() and 0xFFFFFFFFL}_${targetPacketId.toLong() and 0xFFFFFFFFL}"

    data class OutgoingSendJob(
        val text: String,
        val destNodeNum: Long,
        val rowId: Long,
        val replyToPacketId: UInt? = null,
    )

    private sealed class PendingOutgoingAck {
        data class Text(val rowId: Long, val broadcastChannel: Boolean) : PendingOutgoingAck()
        data class Voice(val stableId: String, val broadcastChannel: Boolean) : PendingOutgoingAck()
    }

    // ── Слушатель входящих кадров ─────────────────────────────────────────────

    private val frameListener: (ByteArray) -> Unit = { frame ->
        scope.launch { processFromRadioBytes(frame) }
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun start() {
        stop()
        NodeGattConnection.addFrameListener(frameListener)
        jobs += scope.launch { sendLoop() }
        jobs += scope.launch {
            val v = withContext(Dispatchers.IO) {
                ChatHistoryFileStore.loadVoiceAttachments(app, deviceMacNorm, channel.index, historyNodeIdHex)
            }
            val imgs = withContext(Dispatchers.IO) {
                ChatHistoryFileStore.loadImageAttachments(app, deviceMacNorm, channel.index, historyNodeIdHex)
            }
            _voiceAttachments.value = v
                .groupBy { it.stableId }
                .values
                .map { it.last() }
                .sortedBy { it.timeMs }
            _imageAttachments.value = imgs
                .distinctBy { it.stableId }
                .sortedBy { it.timeMs }
            MeshIncomingChatRepository.incomingVoice.collect { ev ->
                if (ev.deviceMacNorm != deviceMacNorm || ev.channelIndex != channel.index) return@collect
                val cur = _voiceAttachments.value
                if (cur.any { it.stableId == ev.attachment.stableId }) return@collect
                _voiceAttachments.value = (cur + ev.attachment).sortedBy { it.timeMs }
            }
        }
        Log.d(TAG, "start mac=$deviceMacNorm ch=${channel.index}")
    }

    override fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        NodeGattConnection.removeFrameListener(frameListener)
        pendingAckByPacketId.clear()
        Log.d(TAG, "stop")
    }

    /** После очистки истории в БД: убрать голос/картинки из ленты (файлы архива не удаляются). */
    override fun clearLocalChatUiAfterHistoryClear() {
        _voiceAttachments.value = emptyList()
        _imageAttachments.value = emptyList()
    }

    private fun removePendingAck(pid: UInt) {
        pendingAckByPacketId.remove(pid)
    }

    /** ROUTING с mesh больше не сдвигает «галки» — серая до квитанции, две синие = прочтение в Aura у собеседника. */
    private suspend fun applyRoutingSuccessFirstOrSecond(pid: UInt, pending: PendingOutgoingAck) {
        removePendingAck(pid)
    }

    override fun sendMessage(
        text: String,
        destId: Long,
        replyToPacketId: UInt?,
        replyToFromNodeNum: UInt?,
        replyPreviewText: String?,
    ) {
        scope.launch {
            try {
                enqueueOutgoing(
                    trimmedInput = text,
                    destId = destId,
                    replyToPacketId = replyToPacketId,
                    replyToFromNodeNum = replyToFromNodeNum,
                    replyPreviewText = replyPreviewText,
                )
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage", e)
            }
        }
    }

    fun sendTapback(emoji: String, targetMessageFrom: UInt, targetPacketId: UInt) {
        scope.launch {
            try {
                sendTapbackBle(emoji, targetMessageFrom, targetPacketId)
            } catch (e: Exception) {
                Log.e(TAG, "sendTapback", e)
            }
        }
    }

    /**
     * Реакция по [messageId] = [ChannelChatMessageEntity.meshPacketId] (как в Meshtastic),
     * [emojiIndex] — индекс в [MeshReactionEmojiRegistry.ALL_EMOJIS] (0-based).
     * Меню: toggle как раньше (добавить / снять ту же эмодзи).
     */
    override fun sendReaction(messageId: Long, emojiIndex: Int) {
        scope.launch {
            try {
                sendReactionBle(messageId, emojiIndex)
            } catch (e: Exception) {
                Log.e(TAG, "sendReaction", e)
            }
        }
    }

    /**
     * Явное добавление или снятие реакции по индексу эмодзи.
     * По эфиру: **2 байта** [wireId][isAdding: 0/1] — флаг в одном бите полезной нагрузки.
     */
    override fun sendReactionUpdate(messageId: Long, emojiIndex: Int, isAdding: Boolean) {
        scope.launch {
            try {
                sendReactionUpdateBle(messageId, emojiIndex, isAdding)
            } catch (e: Exception) {
                Log.e(TAG, "sendReactionUpdate", e)
            }
        }
    }

    private suspend fun sendTapbackBle(emoji: String, targetMessageFrom: UInt, targetPacketId: UInt) {
        val trimmed = emoji.trim()
        if (trimmed.isEmpty()) return
        if (!isTransportReady()) return
        val targetPacketLong = targetPacketId.toLong() and 0xFFFF_FFFFL
        val targetRow = dao.getByMeshPacketIdChannelOnly(deviceMacNorm, channel.index, targetPacketLong)
        val voice = if (targetRow == null) findVoiceAttachmentByMeshPacketId(targetPacketLong) else null
        if (targetRow == null && voice == null) return
        val expectedAuthor = targetMessageFrom.toLong() and 0xFFFF_FFFFL
        when {
            targetRow != null -> {
                if ((targetRow.fromNodeNum and 0xFFFF_FFFFL) != expectedAuthor) {
                    Log.w(TAG, "tapback: автор сообщения не совпадает с целью packet=$targetPacketId")
                    return
                }
            }
            else -> {
                val fromL = voice!!.from?.toLong() ?: 0L
                if ((fromL and 0xFFFF_FFFFL) != expectedAuthor) {
                    Log.w(TAG, "tapback: автор голосового не совпадает с целью packet=$targetPacketId")
                    return
                }
            }
        }
        val my = (localNodeNum?.toLong() ?: 0L) and 0xFFFF_FFFFL
        val myKey = my.toString()
        val prevJson = targetRow?.reactionsJson ?: voice!!.reactionsJson
        val beforeMine = ChatMessageReactionsJson.parseList(prevJson).any { it.senderId == myKey && it.emoji == trimmed }
        val nextJson = ChatMessageReactionsJson.applyTapOrToggle(prevJson, myKey, trimmed, System.currentTimeMillis())
        if (nextJson != prevJson) {
            if (targetRow != null) {
                dao.updateReactionsJson(targetRow.id, nextJson)
            } else {
                updateVoiceReactionsJson(voice!!.stableId, nextJson)
            }
        }
        val utf8 = trimmed.toByteArray(StandardCharsets.UTF_8)
        val bytes = if (beforeMine) {
            MeshWireLoRaToRadioEncoder.encodeReactionRevokeToRadio(
                targetPacketId,
                channel.index.toUInt(),
            )
        } else {
            if (utf8.isEmpty()) return
            MeshWireLoRaToRadioEncoder.encodeTapbackToRadio(
                utf8,
                targetPacketId,
                channel.index.toUInt(),
            )
        }
        suspendCancellableCoroutine { cont ->
            NodeGattConnection.sendToRadio(bytes) { ok, _ ->
                if (!ok) {
                    scope.launch {
                        if (targetRow != null) {
                            dao.updateReactionsJson(targetRow.id, prevJson)
                        } else {
                            updateVoiceReactionsJson(voice!!.stableId, prevJson)
                        }
                    }
                }
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    /** Меню реакций: как раньше — toggle по текущему состоянию (до нажатия). */
    private suspend fun sendReactionBle(messageId: Long, emojiListIndex: Int) {
        val trimmed = MeshReactionEmojiRegistry.emojiAtListIndex(emojiListIndex)?.trim() ?: return
        if (trimmed.isEmpty()) return
        if (!isTransportReady()) return
        val targetPacketLong = messageId and 0xFFFF_FFFFL
        val targetRow = dao.getByMeshPacketIdChannelOnly(deviceMacNorm, channel.index, targetPacketLong)
        val voice = if (targetRow == null) findVoiceAttachmentByMeshPacketId(targetPacketLong) else null
        if (targetRow == null && voice == null) return
        val prevJson = targetRow?.reactionsJson ?: voice!!.reactionsJson
        val my = (localNodeNum?.toLong() ?: 0L) and 0xFFFF_FFFFL
        val myKey = my.toString()
        val beforeMine = ChatMessageReactionsJson.parseList(prevJson).any { it.senderId == myKey && it.emoji == trimmed }
        sendReactionUpdateBle(messageId, emojiListIndex, isAdding = !beforeMine)
    }

    private suspend fun sendReactionUpdateBle(messageId: Long, emojiListIndex: Int, isAdding: Boolean) {
        val wireId = MeshReactionEmojiRegistry.wireIdForListIndex(emojiListIndex) ?: return
        val trimmed = MeshReactionEmojiRegistry.emojiAtListIndex(emojiListIndex)?.trim() ?: return
        if (trimmed.isEmpty()) return
        if (!isTransportReady()) return
        val targetPacketLong = messageId and 0xFFFF_FFFFL
        val targetRow = dao.getByMeshPacketIdChannelOnly(deviceMacNorm, channel.index, targetPacketLong)
        val voice = if (targetRow == null) findVoiceAttachmentByMeshPacketId(targetPacketLong) else null
        if (targetRow == null && voice == null) return
        val targetPacketId = targetRow?.meshPacketId?.toUInt() ?: voice?.meshPacketId ?: return
        val my = (localNodeNum?.toLong() ?: 0L) and 0xFFFF_FFFFL
        val myKey = my.toString()
        val prevJson = targetRow?.reactionsJson ?: voice!!.reactionsJson
        val beforeMine = ChatMessageReactionsJson.parseList(prevJson).any { it.senderId == myKey && it.emoji == trimmed }
        if (isAdding && beforeMine) return
        if (!isAdding && !beforeMine) return
        val nextJson = ChatMessageReactionsJson.applyTapOrToggle(prevJson, myKey, trimmed, System.currentTimeMillis())
        if (nextJson != prevJson) {
            if (targetRow != null) {
                dao.updateReactionsJson(targetRow.id, nextJson)
            } else {
                updateVoiceReactionsJson(voice!!.stableId, nextJson)
            }
        }
        val payload = byteArrayOf(wireId.toByte(), if (isAdding) 1 else 0)
        val bytes = MeshWireLoRaToRadioEncoder.encodeTapbackToRadio(
            payload,
            targetPacketId,
            channel.index.toUInt(),
        )
        suspendCancellableCoroutine { cont ->
            NodeGattConnection.sendToRadio(bytes) { ok, _ ->
                if (!ok) {
                    scope.launch {
                        if (targetRow != null) {
                            dao.updateReactionsJson(targetRow.id, prevJson)
                        } else {
                            updateVoiceReactionsJson(voice!!.stableId, prevJson)
                        }
                    }
                }
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    override suspend fun sendImageLines(lines: List<String>): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            MeshChannelMessaging.sendImageLines(
                app.applicationContext,
                deviceMacNorm,
                channel.index,
                lines,
            ) { ok, err ->
                if (cont.isActive) cont.resume(ok to err)
            }
        }
    }

    override fun appendLocalImageAttachment(jpeg: ByteArray, sessionKey: String) {
        val attach = ChannelImageAttachment(
            stableId = sessionKey,
            from = localNodeNum,
            jpeg = jpeg.copyOf(),
            mine = true,
            timeMs = System.currentTimeMillis(),
        )
        _imageAttachments.value = _imageAttachments.value + attach
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveImageAttachment(app, deviceMacNorm, channel.index, attach, historyNodeIdHex)
        }
    }

    fun appendLocalVoiceAttachment(
        codecPayload: ByteArray,
        durationMs: Long,
        stableKey: String,
        deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT_TO_NODE,
        voiceRecordId: UInt = 0u,
    ) {
        val attach = ChannelVoiceAttachment(
            stableId = stableKey,
            from = localNodeNum,
            codecPayload = codecPayload.copyOf(),
            mine = true,
            timeMs = System.currentTimeMillis(),
            durationMs = durationMs,
            deliveryStatus = deliveryStatus.code,
            voiceRecordId = voiceRecordId,
        )
        _voiceAttachments.value = _voiceAttachments.value + attach
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveVoiceAttachment(app, deviceMacNorm, channel.index, attach, historyNodeIdHex)
        }
    }

    private fun updateVoiceDelivery(stableId: String, status: ChatMessageDeliveryStatus) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(deliveryStatus = status.code) else a
        }
        val updated = _voiceAttachments.value.find { it.stableId == stableId }
        if (updated != null) {
            scope.launch(Dispatchers.IO) {
                ChatHistoryFileStore.saveVoiceAttachment(
                    app,
                    deviceMacNorm,
                    channel.index,
                    updated,
                    historyNodeIdHex,
                )
            }
        }
        if (status != ChatMessageDeliveryStatus.SENT_TO_NODE) return
        val a = updated ?: return
        if (!a.mine) return
        if (!historyRecordedOutgoingVoiceStableIds.add(stableId)) return
        scope.launch(Dispatchers.IO) {
            MessageHistoryRecorder.repository?.recordVoiceMessage(
                deviceMacNorm,
                channel.index,
                a,
                channelDisplayName = channel.name,
            )
        }
    }

    private suspend fun maybeRecordOutgoingTextToMessageHistory(rowId: Long) {
        val row = dao.getById(deviceMacNorm, channel.index, rowId) ?: return
        if (!row.isOutgoing) return
        if (ChatMessageDeliveryStatus.fromCode(row.deliveryStatus) != ChatMessageDeliveryStatus.SENT_TO_NODE) return
        if (MeshWireReadReceiptCodec.shouldHideReadReceiptInChatList(
                text = row.text,
                isOutgoing = true,
                replyToPacketId = row.replyToPacketId?.toUInt(),
            )
        ) return
        if (!historyRecordedOutgoingTextRowIds.add(rowId)) return
        MessageHistoryRecorder.repository?.recordTextMessage(
            deviceMacNorm,
            channel.index,
            row,
            channelDisplayName = channel.name,
        )
    }

    private fun findVoiceAttachmentByMeshPacketId(packetLong: Long): ChannelVoiceAttachment? =
        _voiceAttachments.value.find {
            it.meshPacketId != null &&
                (it.meshPacketId!!.toLong() and 0xFFFF_FFFFL) == packetLong
        }

    private fun updateVoiceMeshPacketId(stableId: String, meshPacketId: UInt) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(meshPacketId = meshPacketId) else a
        }
        val a = _voiceAttachments.value.find { it.stableId == stableId } ?: return
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveVoiceAttachment(app, deviceMacNorm, channel.index, a, historyNodeIdHex)
        }
    }

    private fun updateVoiceRecordId(stableId: String, voiceRecordId: UInt) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(voiceRecordId = voiceRecordId) else a
        }
        val a = _voiceAttachments.value.find { it.stableId == stableId } ?: return
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveVoiceAttachment(app, deviceMacNorm, channel.index, a, historyNodeIdHex)
        }
    }

    private fun updateVoiceReactionsJson(stableId: String, json: String) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(reactionsJson = json) else a
        }
        val a = _voiceAttachments.value.find { it.stableId == stableId } ?: return
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveVoiceAttachment(app, deviceMacNorm, channel.index, a, historyNodeIdHex)
        }
    }

    override suspend fun sendVoicePayload(encoded: ByteArray, durationMs: Long): Pair<Boolean, String?> {
        var stableKey: String? = null
        return try {
            if (encoded.isEmpty()) return false to "Пустая запись"
            if (BleTransportManager.isChannelAirBusyForVoice()) {
                return false to "Сеть занята, попробуйте позже"
            }
            if (!isTransportReady()) return false to "Транспорт недоступен"
            val recordId = Random.nextInt().toUInt()
            val packets = VoiceLoRaFragmentCodec.buildPackets(recordId, encoded)
            if (packets.isEmpty()) return false to "Нет пакетов"
            stableKey = "v_out_${recordId.toLong() and 0xFFFFFFFFL}_${System.currentTimeMillis()}"
            appendLocalVoiceAttachment(
                encoded,
                durationMs,
                stableKey,
                ChatMessageDeliveryStatus.SENDING,
                voiceRecordId = recordId,
            )
            sendVoicePacketsInternal(packets, stableKey)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "sendVoicePayload failed", t)
            stableKey?.let { updateVoiceDelivery(it, ChatMessageDeliveryStatus.FAILED) }
            false to (t.message ?: "Ошибка отправки голоса")
        }
    }

    /**
     * Повторная отправка того же голосового вложения после [ChatMessageDeliveryStatus.FAILED].
     */
    override suspend fun retryFailedVoice(stableId: String): Pair<Boolean, String?> {
        return try {
            val attach = _voiceAttachments.value.find { it.stableId == stableId } ?: return false to "Сообщение не найдено"
            if (!attach.mine || attach.deliveryStatus != ChatMessageDeliveryStatus.FAILED.code) {
                return false to "Повтор недоступен"
            }
            if (BleTransportManager.isChannelAirBusyForVoice()) {
                return false to "Сеть занята, попробуйте позже"
            }
            if (!isTransportReady()) return false to "Транспорт недоступен"
            val recordId = Random.nextInt().toUInt()
            val packets = VoiceLoRaFragmentCodec.buildPackets(recordId, attach.codecPayload)
            if (packets.isEmpty()) return false to "Нет пакетов"
            updateVoiceRecordId(stableId, recordId)
            updateVoiceDelivery(stableId, ChatMessageDeliveryStatus.SENDING)
            sendVoicePacketsInternal(packets, stableId)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "retryFailedVoice failed", t)
            updateVoiceDelivery(stableId, ChatMessageDeliveryStatus.FAILED)
            false to (t.message ?: "Ошибка повторной отправки голоса")
        }
    }

    private suspend fun sendVoicePacketsInternal(
        packets: List<ByteArray>,
        stableKey: String,
    ): Pair<Boolean, String?> {
        var lastPktId: UInt? = null
        return try {
            withTimeout(45_000L) {
                suspendCancellableCoroutine { cont ->
                    MeshChannelMessaging.sendVoicePackets(
                        app.applicationContext,
                        deviceMacNorm,
                        channel.index,
                        packets,
                        onBeforeQueue = { pktId ->
                            lastPktId = pktId
                            pendingAckByPacketId[pktId] =
                                PendingOutgoingAck.Voice(stableKey, broadcastChannel = true)
                            updateVoiceMeshPacketId(stableKey, pktId)
                            updateVoiceDelivery(stableKey, ChatMessageDeliveryStatus.SENT_TO_NODE)
                        },
                    ) { ok, err ->
                        val pid = lastPktId
                        if (!ok) {
                            if (pid != null) pendingAckByPacketId.remove(pid)
                            updateVoiceDelivery(stableKey, ChatMessageDeliveryStatus.FAILED)
                        }
                        // При успехе BLE голос остаётся SENT_TO_NODE до ROUTING (как текст).
                        if (cont.isActive) cont.resume(ok to err)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            lastPktId?.let { pendingAckByPacketId.remove(it) }
            updateVoiceDelivery(stableKey, ChatMessageDeliveryStatus.FAILED)
            false to "Таймаут ожидания BLE"
        }
    }

    /** Повторная отправка того же текстового сообщения после [ChatMessageDeliveryStatus.FAILED]. */
    override fun retryFailedOutgoingText(rowId: Long) {
        scope.launch {
            try {
                retryFailedOutgoingTextSuspend(rowId)
            } catch (e: Exception) {
                Log.e(TAG, "retryFailedOutgoingText", e)
            }
        }
    }

    private suspend fun retryFailedOutgoingTextSuspend(rowId: Long) {
        val row = dao.getById(deviceMacNorm, channel.index, rowId) ?: return
        if (!row.isOutgoing || row.deliveryStatus != ChatMessageDeliveryStatus.FAILED.code) return
        if (!isTransportReady()) {
            Log.w(TAG, "retryFailedOutgoingText: транспорт недоступен")
            return
        }
        row.meshPacketId?.let { pendingAckByPacketId.remove((it and 0xFFFF_FFFFL).toUInt()) }
        dao.clearMeshPacketIdForRow(deviceMacNorm, channel.index, rowId)
        dao.updateDelivery(rowId, ChatMessageDeliveryStatus.SENDING.code, null)
        val replyTo = row.replyToPacketId?.let { (it and 0xFFFF_FFFFL).toUInt() }
        outgoing.send(OutgoingSendJob(row.text, row.toNodeNum, rowId, replyTo))
    }

    /**
     * Удаляет текст из ленты чата: при необходимости дописывает в файловый архив канала, затем строку из БД.
     * Записи экрана «История сообщений» ([MessageHistoryRepository]) не затрагиваются.
     */
    override suspend fun removeTextMessageFromChatPreservingHistory(rowId: Long) = withContext(Dispatchers.IO) {
        val row = dao.getById(deviceMacNorm, channel.index, rowId) ?: return@withContext
        ChatHistoryFileStore.syncMissingTextMessagesToArchive(
            app.applicationContext,
            deviceMacNorm,
            channel.index,
            listOf(row),
            historyNodeIdHex,
        )
        dao.deleteById(deviceMacNorm, channel.index, rowId)
    }

    /** Удаляет вложение из ленты и из jsonl/файлов канала, иначе после повторного входа снова подгрузится. */
    override suspend fun removeImageAttachmentFromChat(stableId: String) = withContext(Dispatchers.IO) {
        try {
            ChatHistoryFileStore.removeImageAttachment(
                app,
                deviceMacNorm,
                channel.index,
                stableId,
                historyNodeIdHex,
            )
        } catch (e: Exception) {
            Log.e(TAG, "removeImageAttachmentFromChat disk", e)
        }
        _imageAttachments.value = _imageAttachments.value.filter { it.stableId != stableId }
    }

    /** Удаляет голос из ленты и из индекса/файла, иначе после повторного входа снова подгрузится. */
    override suspend fun removeVoiceAttachmentFromChat(stableId: String) = withContext(Dispatchers.IO) {
        try {
            ChatHistoryFileStore.removeVoiceAttachment(
                app,
                deviceMacNorm,
                channel.index,
                stableId,
                historyNodeIdHex,
            )
        } catch (e: Exception) {
            Log.e(TAG, "removeVoiceAttachmentFromChat disk", e)
        }
        _voiceAttachments.value = _voiceAttachments.value.filter { it.stableId != stableId }
    }

    // ── Очередь отправки ──────────────────────────────────────────────────────

    private suspend fun enqueueOutgoing(
        trimmedInput: String,
        destId: Long,
        replyToPacketId: UInt? = null,
        replyToFromNodeNum: UInt? = null,
        replyPreviewText: String? = null,
    ) {
        val trimmed = MeshWireLoRaToRadioEncoder.truncateMeshUtf8(
            trimmedInput.trim(),
            MeshWireLoRaToRadioEncoder.MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES,
        )
        if (trimmed.isEmpty()) return
        if (!isTransportReady()) {
            Log.w(TAG, "Транспорт недоступен")
            return
        }
        val my = (localNodeNum?.toLong() ?: 0L) and 0xFFFF_FFFFL
        val replyPidLong = replyToPacketId?.let { it.toLong() and 0xFFFF_FFFFL }
        val replyFromLong = replyToFromNodeNum?.let { it.toLong() and 0xFFFF_FFFFL }
        val replyPreview = replyPreviewText?.let { meshReplyPreviewSnippet(it) }
        val row = ChannelChatMessageEntity(
            deviceMac = deviceMacNorm,
            channelIndex = channel.index,
            dedupKey = "out_${UUID.randomUUID()}",
            isOutgoing = true,
            text = trimmed,
            fromNodeNum = my,
            toNodeNum = destId,
            meshPacketId = null,
            deliveryStatus = ChatMessageDeliveryStatus.SENDING.code,
            createdAtMs = System.currentTimeMillis(),
            rxTimeSec = null,
            rxSnr = null,
            rxRssi = null,
            relayHops = null,
            viaMqtt = false,
            lastError = null,
            replyToPacketId = replyPidLong,
            replyToFromNodeNum = replyFromLong,
            replyPreviewText = replyPreview,
            isRead = true,
        )
        val rowId = dao.insert(row)
        if (rowId < 0) {
            Log.w(TAG, "insert outgoing ignored (conflict?)")
            return
        }
        val isReadReceipt = MeshWireReadReceiptCodec.shouldHideReadReceiptInChatList(
            text = trimmed,
            isOutgoing = true,
            replyToPacketId = replyToPacketId,
        )
        if (!isReadReceipt) {
            withContext(Dispatchers.IO) {
                ChatHistoryFileStore.appendTextMessage(app, deviceMacNorm, channel.index, row.copy(id = rowId), historyNodeIdHex)
            }
        }
        outgoing.send(OutgoingSendJob(trimmed, destId, rowId, replyToPacketId))
    }

    private suspend fun sendLoop() {
        for (job in outgoing) {
            try {
                sendOneBle(job)
            } catch (e: Exception) {
                Log.e(TAG, "sendLoop", e)
                dao.updateDelivery(job.rowId, ChatMessageDeliveryStatus.FAILED.code, e.message)
            }
        }
    }

    private suspend fun sendOneBle(job: OutgoingSendJob) {
        val utf8 = job.text.toByteArray(StandardCharsets.UTF_8)
        val toRadio = toRadioUInt(job.destNodeNum)
        val replyId = job.replyToPacketId
        val (bytes, pktId) = if (replyId != null) {
            MeshWireLoRaToRadioEncoder.encodeTextReplyMessageToRadioWithId(
                textUtf8 = utf8,
                channelIndex = channel.index.toUInt(),
                replyToMeshPacketId = replyId,
                to = toRadio,
                wantAck = true,
            )
        } else {
            MeshWireLoRaToRadioEncoder.encodeTextMessageToRadioWithId(
                textUtf8 = utf8,
                channelIndex = channel.index.toUInt(),
                to = toRadio,
                wantAck = true,
            )
        }
        ChatProtobufDebugLog.hexDump("ToRadio text", bytes)
        val broadcastChannel = isBroadcastChannelDest(job.destNodeNum)
        // До любого suspend у DAO — иначе ROUTING из FromRadio может прийти раньше pending.
        pendingAckByPacketId[pktId] = PendingOutgoingAck.Text(job.rowId, broadcastChannel)
        dao.updateMeshPacketId(job.rowId, pktId.toLong() and 0xFFFF_FFFFL)

        // Статус «отправляется»: обновляем до BLE-подтверждения
        dao.updateDelivery(job.rowId, ChatMessageDeliveryStatus.SENT_TO_NODE.code, null)

        val bleReported = AtomicBoolean(false)
        try {
            withTimeout(45_000L) {
                suspendCancellableCoroutine { cont ->
                    NodeGattConnection.sendToRadio(bytes) { ok, err ->
                        runBlocking(Dispatchers.IO) {
                            if (!ok) {
                                pendingAckByPacketId.remove(pktId)
                                dao.updateDelivery(
                                    job.rowId,
                                    ChatMessageDeliveryStatus.FAILED.code,
                                    err ?: "Ошибка записи ToRadio",
                                )
                            } else {
                                scope.launch {
                                    try {
                                        maybeRecordOutgoingTextToMessageHistory(job.rowId)
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                        bleReported.set(true)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            if (bleReported.compareAndSet(false, true)) {
                runBlocking(Dispatchers.IO) {
                    pendingAckByPacketId.remove(pktId)
                    dao.updateDelivery(
                        job.rowId,
                        ChatMessageDeliveryStatus.FAILED.code,
                        "Таймаут ожидания BLE",
                    )
                }
            }
        }
    }

    // ── Обработка входящих ────────────────────────────────────────────────────

    private suspend fun processFromRadioBytes(bytes: ByteArray) {
        ChatProtobufDebugLog.hexDump("FromRadio frame", bytes)
        val rawPayloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(bytes)
        val payloads = PacketDispatcher.prioritizeMeshPayloads(rawPayloads)
        for (p in payloads) {
            when (p.portnum) {
                MeshWireFromRadioMeshPacketParser.PORTNUM_ROUTING_APP -> handleRouting(p)
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_APP,
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_COMPRESSED_APP,
                -> handleChannelText(p)
                // PORTNUM_PRIVATE_APP (голос) обрабатывается в MeshIncomingChatRepository всегда
            }
        }
        BleTransportManager.noteFromRadioConsumed()
    }

    private suspend fun handleRouting(p: ParsedMeshDataPayload) {
        // ACK ROUTING_APP: у ответа свой MeshPacket.id; исходный packet id — в Data.request_id (mesh.proto).
        // Нельзя подставлять packetId ответа как ключ: он не совпадает с id исходящего пакета (см. MeshModule::allocAckNak).
        val pid = p.dataRequestId
            ?: p.packetId?.takeIf { pendingAckByPacketId.containsKey(it) }
            ?: run {
                Log.v(
                    TAG,
                    "ROUTING без request_id / нет pending: meshPacketId=${p.packetId} dataRequestId=${p.dataRequestId}",
                )
                return
            }
        val pending = pendingAckByPacketId[pid] ?: run {
            Log.v(
                TAG,
                "ROUTING без pending ackFor=$pid meshPacketId=${p.packetId} dataRequestId=${p.dataRequestId}",
            )
            return
        }
        val broadcastChannel = when (pending) {
            is PendingOutgoingAck.Text -> pending.broadcastChannel
            is PendingOutgoingAck.Voice -> pending.broadcastChannel
        }
        val errCode = MeshWireRoutingPayloadParser.parseErrorReason(p.payload) ?: 0
        val mapped = MeshWireStatusMapper.mapRoutingError(errCode)
        val logRef = when (pending) {
            is PendingOutgoingAck.Text -> "row=${pending.rowId}"
            is PendingOutgoingAck.Voice -> "voice=${pending.stableId}"
        }
        Log.d(TAG, "ROUTING id=$pid $logRef err=$errCode -> $mapped broadcast=$broadcastChannel")
        when {
            mapped == MeshRoutingUiResult.Success -> {
                applyRoutingSuccessFirstOrSecond(pid, pending)
            }
            mapped == MeshRoutingUiResult.UnknownCode -> {
                // Код не в enum клиента (новая прошивка / дырка в номерах): не оставляем серую галочку без причины.
                Log.w(TAG, "ROUTING неизвестный enum err=$errCode $logRef — принимаем как доставку на ноду")
                applyRoutingSuccessFirstOrSecond(pid, pending)
            }
            broadcastChannel -> {
                removePendingAck(pid)
            }
            else -> {
                val msg = MeshWireStatusMapper.labelRussian(mapped)
                when (pending) {
                    is PendingOutgoingAck.Text ->
                        dao.updateDelivery(pending.rowId, ChatMessageDeliveryStatus.FAILED.code, msg)
                    is PendingOutgoingAck.Voice ->
                        updateVoiceDelivery(pending.stableId, ChatMessageDeliveryStatus.FAILED)
                }
                removePendingAck(pid)
            }
        }
    }

    private suspend fun handleChannelText(p: ParsedMeshDataPayload) {
        if (!MeshChannelMessaging.isLikelyChannelMeshTraffic(p)) return
        if (!MeshChannelMessaging.matchesChannel(p, channel)) return
        val from = p.logicalFrom() ?: return
        if (MeshWireFromRadioMeshPacketParser.isReactionRevokePayload(p)) {
            handleReactionRevoke(p)
            return
        }
        // Tapback до UTF-8: компактный wire-ID — один байт 0x80..0xFF не декодируется как UTF-8 и даст null.
        if (MeshWireFromRadioMeshPacketParser.isTapbackPayload(p)) {
            when {
                p.payload.size == 2 -> {
                    val wid = p.payload[0].toInt() and 0xFF
                    val adding = p.payload[1] != 0.toByte()
                    val emojiStr = MeshReactionEmojiRegistry.emojiForWireId(wid)
                        ?: MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)?.trim().orEmpty()
                    if (emojiStr.isEmpty()) return
                    if (!adding) {
                        handleIncomingWireRemove(p, emojiStr)
                        return
                    }
                    handleTapbackReceived(p, emojiStr)
                    return
                }
                p.payload.size == 1 -> {
                    val wid = p.payload[0].toInt() and 0xFF
                    val emojiStr = MeshReactionEmojiRegistry.emojiForWireId(wid)
                        ?: MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)?.trim().orEmpty()
                    if (emojiStr.isEmpty()) return
                    handleTapbackReceived(p, emojiStr)
                    return
                }
                else -> {
                    val emoji = MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)?.trim().orEmpty()
                    handleTapbackReceived(p, emoji)
                    return
                }
            }
        }
        val raw = MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)
            ?: return
        if (localNodeNum != null && from == localNodeNum && !raw.startsWith(MeshImageChunkCodec.PREFIX)) {
            return
        }
        if (raw.startsWith(MeshImageChunkCodec.PREFIX)) {
            val line = MeshImageChunkCodec.tryParseLine(raw) ?: return
            val jpeg = imgReassembler.ingest(line) ?: return
            val mine = localNodeNum != null && from == localNodeNum
            val attach = ChannelImageAttachment(
                stableId = "${line.sessionId}_img_${jpeg.contentHashCode()}",
                from = from,
                jpeg = jpeg,
                mine = mine,
                timeMs = p.rxTimeSec?.let { it.toLong() * 1000L } ?: System.currentTimeMillis(),
            )
            _imageAttachments.value = _imageAttachments.value + attach
            withContext(Dispatchers.IO) {
                ChatHistoryFileStore.saveImageAttachment(app, deviceMacNorm, channel.index, attach, historyNodeIdHex)
            }
            return
        }
        val inserted = persistInboundChannelTextIfNew(
            dao,
            messageRepository,
            deviceMacNorm,
            localNodeNum,
            p,
        )
        if (inserted != null) {
            withContext(Dispatchers.IO) {
                ChatHistoryFileStore.appendTextMessage(app, deviceMacNorm, channel.index, inserted, historyNodeIdHex)
            }
        }
    }

    private suspend fun handleTapbackReceived(p: ParsedMeshDataPayload, emoji: String) {
        val replyId = p.dataReplyId ?: return
        val reactor = p.logicalFrom() ?: return
        val text = emoji.trim()
        val atMillis = p.rxTimeSec?.let { it.toLong() * 1000L } ?: System.currentTimeMillis()
        val tid = replyId.toLong() and 0xFFFF_FFFFL
        val reactorL = reactor.toLong() and 0xFFFF_FFFFL
        if (text.isEmpty()) {
            if (!messageRepository.clearAllReactionsFromSenderOnMessage(
                    deviceMacNorm,
                    channel.index,
                    tid,
                    reactorL,
                )
            ) {
                clearVoiceReactionsFromSender(tid, reactorL)
            }
            return
        }
        if (!messageRepository.applyReactionTapOrToggle(
                deviceMacNorm,
                channel.index,
                tid,
                reactorL,
                text,
                atMillis,
            )
        ) {
            applyVoiceReactionTapOrToggle(tid, reactorL, text, atMillis)
        }
    }

    private suspend fun handleReactionRevoke(p: ParsedMeshDataPayload) {
        val replyId = p.dataReplyId ?: return
        val reactor = p.logicalFrom() ?: return
        val tid = replyId.toLong() and 0xFFFF_FFFFL
        val reactorL = reactor.toLong() and 0xFFFF_FFFFL
        if (!messageRepository.clearAllReactionsFromSenderOnMessage(
                deviceMacNorm,
                channel.index,
                tid,
                reactorL,
            )
        ) {
            clearVoiceReactionsFromSender(tid, reactorL)
        }
    }

    private fun applyVoiceReactionTapOrToggle(
        tid: Long,
        reactorL: Long,
        text: String,
        atMillis: Long,
    ) {
        val a = findVoiceAttachmentByMeshPacketId(tid) ?: return
        val reactorKey = reactorL.toString()
        val nextJson = ChatMessageReactionsJson.applyTapOrToggle(a.reactionsJson, reactorKey, text, atMillis)
        if (nextJson == a.reactionsJson) return
        updateVoiceReactionsJson(a.stableId, nextJson)
    }

    private fun clearVoiceReactionsFromSender(tid: Long, reactorL: Long) {
        val a = findVoiceAttachmentByMeshPacketId(tid) ?: return
        val reactorKey = reactorL.toString()
        val nextJson = ChatMessageReactionsJson.removeAllFromSender(a.reactionsJson, reactorKey)
        if (nextJson == a.reactionsJson) return
        updateVoiceReactionsJson(a.stableId, nextJson)
    }

    private fun removeVoiceSenderEmojiIfPresent(tid: Long, reactorL: Long, emoji: String) {
        val a = findVoiceAttachmentByMeshPacketId(tid) ?: return
        val reactorKey = reactorL.toString()
        val nextJson = ChatMessageReactionsJson.removeSenderEmojiIfPresent(a.reactionsJson, reactorKey, emoji)
        if (nextJson == a.reactionsJson) return
        updateVoiceReactionsJson(a.stableId, nextJson)
    }

    private suspend fun handleIncomingWireRemove(p: ParsedMeshDataPayload, emoji: String) {
        val replyId = p.dataReplyId ?: return
        val reactor = p.logicalFrom() ?: return
        val tid = replyId.toLong() and 0xFFFF_FFFFL
        val reactorL = reactor.toLong() and 0xFFFF_FFFFL
        val em = emoji.trim()
        if (em.isEmpty()) return
        if (!messageRepository.removeSenderEmojiIfPresent(
                deviceMacNorm,
                channel.index,
                tid,
                reactorL,
                em,
            )
        ) {
            removeVoiceSenderEmojiIfPresent(tid, reactorL, em)
        }
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private fun meshReplyPreviewSnippet(body: String): String {
        val oneLine = body.replace("\r\n", "\n").replace("\n", " ").trim()
        return if (oneLine.length > 200) oneLine.take(197) + "…" else oneLine
    }

    private fun isBroadcastChannelDest(destId: Long): Boolean =
        destId == 0xFFFFFFFFL || destId < 0

    private fun toRadioUInt(destId: Long): UInt =
        if (isBroadcastChannelDest(destId)) MeshWireLoRaToRadioEncoder.BROADCAST_NODE_NUM
        else (destId and 0xFFFF_FFFFL).toUInt()

    private fun isTransportReady(): Boolean {
        if (MeshDeviceTransport.isTcpAddress(deviceMacNorm) ||
            MeshDeviceTransport.isUsbAddress(deviceMacNorm)
        ) return true
        val mgr = app.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as?
                BluetoothManager
        return mgr?.adapter?.isEnabled == true
    }
}
