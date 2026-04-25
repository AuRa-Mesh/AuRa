package com.example.aura.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.example.aura.chat.ChannelImageAttachment
import com.example.aura.chat.ChannelVoiceAttachment
import com.example.aura.chat.ChatMessageDeliveryStatus
import com.example.aura.chat.ChatProtobufDebugLog
import com.example.aura.chat.MeshWireStatusMapper
import com.example.aura.chat.MeshWireStatusMapper.MeshRoutingUiResult
import com.example.aura.chat.history.ChatHistoryFileStore
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
import com.example.aura.mesh.PacketDispatcher
import com.example.aura.mesh.repository.MeshIncomingChatRepository
import com.example.aura.history.MessageHistoryRecorder
import com.example.aura.mesh.repository.MessageRepository
import com.example.aura.mesh.transport.BleTransportManager
import com.example.aura.voice.VoiceLoRaFragmentCodec
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.random.Random
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "DirectChatManager"

/**
 * Личные сообщения на [peerNodeNumLong] (индекс LoRa — [meshChannel.index], обычно 0).
 * Входящие тексты пишет [com.example.aura.mesh.repository.MeshIncomingChatRepository]; здесь — та же
 * схема, что в [ChannelChatManager]: ROUTING, реакции, tapback, голос/картинки, очередь ToRadio.
 */
class DirectChatManager(
    private val app: Context,
    db: AuraDatabase,
    deviceAddress: String,
    private val meshChannel: MeshStoredChannel,
    private val peerNodeNumLong: Long,
    /** Подпапка в [ChatHistoryFileStore] `private_chats/`. */
    private val peerHistoryFolderName: String,
    private val localNodeNum: UInt?,
    private val historyNodeIdHex: String?,
    private val messageRepository: MessageRepository,
    /** Подпись беседы для [MessageHistoryRecorder] (как [MeshStoredChannel.name] в канале). */
    private val conversationDisplayName: String? = null,
) : MeshTextChatSession {

    private val historyTitle: String
        get() = conversationDisplayName?.trim()?.takeIf { it.isNotEmpty() } ?: meshChannel.name

    private val dao = db.channelChatMessageDao()
    private val deviceMacNorm = MeshNodeSyncMemoryStore.normalizeKey(deviceAddress)
    private val peerToRadio: UInt = (peerNodeNumLong and 0xFFFF_FFFFL).toUInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingAckByPacketId = ConcurrentHashMap<UInt, PendingOutgoingAck>()
    private val outgoing = Channel<OutgoingSendJob>(Channel.UNLIMITED)
    private val jobs = mutableListOf<Job>()
    private val historyRecordedOutgoingTextRowIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private val historyRecordedOutgoingVoiceStableIds = ConcurrentHashMap.newKeySet<String>()

    private val frameListener: (ByteArray) -> Unit = { frame ->
        scope.launch { processFromRadioBytes(frame) }
    }

    override val messages = dao.observeDirectThread(deviceMacNorm, peerNodeNumLong)

    private val _imageAttachments = MutableStateFlow<List<ChannelImageAttachment>>(emptyList())
    override val imageAttachments: StateFlow<List<ChannelImageAttachment>> = _imageAttachments.asStateFlow()

    private val _voiceAttachments = MutableStateFlow<List<ChannelVoiceAttachment>>(emptyList())
    override val voiceAttachments: StateFlow<List<ChannelVoiceAttachment>> = _voiceAttachments.asStateFlow()

    private data class OutgoingSendJob(
        val text: String,
        val rowId: Long,
        val replyToPacketId: UInt? = null,
    )

    private sealed class PendingOutgoingAck {
        /** Для лички [broadcastChannel] всегда false; поле — как в канале, для единой логики [handleRouting]. */
        data class Text(val rowId: Long, val broadcastChannel: Boolean = false) : PendingOutgoingAck()
        data class Voice(val stableId: String, val broadcastChannel: Boolean = false) : PendingOutgoingAck()
    }

    override fun start() {
        stop()
        NodeGattConnection.addFrameListener(frameListener)
        jobs += scope.launch { sendLoop() }
        jobs += scope.launch {
            val v = withContext(Dispatchers.IO) {
                ChatHistoryFileStore.loadVoiceAttachmentsDirectWithFallback(
                    app,
                    historyNodeIdHex,
                    peerHistoryFolderName,
                )
            }
            val imgs = withContext(Dispatchers.IO) {
                ChatHistoryFileStore.loadImageAttachmentsDirectWithFallback(
                    app,
                    historyNodeIdHex,
                    peerHistoryFolderName,
                )
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
                if (ev.deviceMacNorm != deviceMacNorm || ev.dmPeerNodeNum != peerNodeNumLong) return@collect
                val cur = _voiceAttachments.value
                if (cur.any { it.stableId == ev.attachment.stableId }) return@collect
                _voiceAttachments.value = (cur + ev.attachment).sortedBy { it.timeMs }
            }
        }
        jobs += scope.launch {
            MeshIncomingChatRepository.incomingDirectImages.collect { ev ->
                if (ev.deviceMacNorm != deviceMacNorm || ev.dmPeerNodeNum != peerNodeNumLong) return@collect
                val cur = _imageAttachments.value
                if (cur.any { it.stableId == ev.attachment.stableId }) return@collect
                _imageAttachments.value = (cur + ev.attachment).sortedBy { it.timeMs }
            }
        }
        Log.d(TAG, "start mac=$deviceMacNorm peer=$peerNodeNumLong ch=${meshChannel.index}")
    }

    override fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        NodeGattConnection.removeFrameListener(frameListener)
        pendingAckByPacketId.clear()
        Log.d(TAG, "stop")
    }

    override fun clearLocalChatUiAfterHistoryClear() {
        _voiceAttachments.value = emptyList()
        _imageAttachments.value = emptyList()
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
                    replyToPacketId = replyToPacketId,
                    replyToFromNodeNum = replyToFromNodeNum,
                    replyPreviewText = replyPreviewText,
                )
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage", e)
            }
        }
    }

    /**
     * Как [ChannelChatManager.sendTapback]: [targetPacketId] — mesh id сообщения, [targetMessageFrom] — автор.
     */
    fun sendTapback(emoji: String, targetMessageFrom: UInt, targetPacketId: UInt) {
        scope.launch {
            try {
                sendTapbackBle(emoji, targetMessageFrom, targetPacketId)
            } catch (e: Exception) {
                Log.e(TAG, "sendTapback", e)
            }
        }
    }

    override fun sendReaction(messageId: Long, emojiIndex: Int) {
        scope.launch {
            try {
                sendReactionBle(messageId, emojiIndex)
            } catch (e: Exception) {
                Log.e(TAG, "sendReaction", e)
            }
        }
    }

    override fun sendReactionUpdate(messageId: Long, emojiIndex: Int, isAdding: Boolean) {
        scope.launch {
            try {
                sendReactionUpdateBle(messageId, emojiIndex, isAdding)
            } catch (e: Exception) {
                Log.e(TAG, "sendReactionUpdate", e)
            }
        }
    }

    override fun retryFailedOutgoingText(rowId: Long) {
        scope.launch {
            try {
                retryFailedOutgoingTextSuspend(rowId)
            } catch (e: Exception) {
                Log.e(TAG, "retryFailedOutgoingText", e)
            }
        }
    }

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

    override suspend fun sendImageLines(lines: List<String>): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            MeshChannelMessaging.sendImageLinesDirect(
                app.applicationContext,
                deviceMacNorm,
                meshChannel.index,
                peerToRadio,
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
            ChatHistoryFileStore.saveImageAttachmentDirect(
                app,
                historyNodeIdHex,
                peerHistoryFolderName,
                attach,
            )
            MeshIncomingChatRepository.notifyDirectThreadListPreviewRefresh()
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

    override suspend fun removeImageAttachmentFromChat(stableId: String) = withContext(Dispatchers.IO) {
        try {
            ChatHistoryFileStore.removeImageAttachmentDirect(
                app,
                historyNodeIdHex,
                peerHistoryFolderName,
                stableId,
            )
        } catch (e: Exception) {
            Log.e(TAG, "removeImageAttachmentFromChat disk", e)
        }
        _imageAttachments.value = _imageAttachments.value.filter { it.stableId != stableId }
    }

    override suspend fun removeVoiceAttachmentFromChat(stableId: String) = withContext(Dispatchers.IO) {
        try {
            ChatHistoryFileStore.removeVoiceAttachmentDirect(
                app,
                historyNodeIdHex,
                peerHistoryFolderName,
                stableId,
            )
        } catch (e: Exception) {
            Log.e(TAG, "removeVoiceAttachmentFromChat disk", e)
        }
        _voiceAttachments.value = _voiceAttachments.value.filter { it.stableId != stableId }
    }

    override suspend fun removeTextMessageFromChatPreservingHistory(rowId: Long) = withContext(Dispatchers.IO) {
        val row = dao.getByIdDirectThread(deviceMacNorm, rowId, peerNodeNumLong) ?: return@withContext
        ChatHistoryFileStore.syncMissingDirectTextMessagesToArchive(
            app.applicationContext,
            deviceMacNorm,
            meshChannel.index,
            peerHistoryFolderName,
            listOf(row),
            historyNodeIdHex,
        )
        dao.deleteByIdDirectThread(deviceMacNorm, rowId, peerNodeNumLong)
    }

    private fun appendLocalVoiceAttachment(
        codecPayload: ByteArray,
        durationMs: Long,
        stableKey: String,
        deliveryStatus: ChatMessageDeliveryStatus,
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
            ChatHistoryFileStore.saveVoiceAttachmentDirect(
                app,
                historyNodeIdHex,
                peerHistoryFolderName,
                attach,
            )
            MeshIncomingChatRepository.notifyDirectThreadListPreviewRefresh()
        }
    }

    private fun updateVoiceDelivery(stableId: String, status: ChatMessageDeliveryStatus) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(deliveryStatus = status.code) else a
        }
        val updated = _voiceAttachments.value.find { it.stableId == stableId }
        if (updated != null) {
            scope.launch(Dispatchers.IO) {
                ChatHistoryFileStore.saveVoiceAttachmentDirect(
                    app,
                    historyNodeIdHex,
                    peerHistoryFolderName,
                    updated,
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
                meshChannel.index,
                a,
                channelDisplayName = historyTitle,
            )
        }
    }

    private fun updateVoiceMeshPacketId(stableId: String, meshPacketId: UInt) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(meshPacketId = meshPacketId) else a
        }
        val a = _voiceAttachments.value.find { it.stableId == stableId } ?: return
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveVoiceAttachmentDirect(
                app,
                historyNodeIdHex,
                peerHistoryFolderName,
                a,
            )
        }
    }

    private fun updateVoiceRecordId(stableId: String, voiceRecordId: UInt) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(voiceRecordId = voiceRecordId) else a
        }
        val a = _voiceAttachments.value.find { it.stableId == stableId } ?: return
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveVoiceAttachmentDirect(
                app,
                historyNodeIdHex,
                peerHistoryFolderName,
                a,
            )
        }
    }

    private fun updateVoiceReactionsJson(stableId: String, json: String) {
        _voiceAttachments.value = _voiceAttachments.value.map { a ->
            if (a.stableId == stableId) a.copy(reactionsJson = json) else a
        }
        val a = _voiceAttachments.value.find { it.stableId == stableId } ?: return
        scope.launch(Dispatchers.IO) {
            ChatHistoryFileStore.saveVoiceAttachmentDirect(
                app,
                historyNodeIdHex,
                peerHistoryFolderName,
                a,
            )
        }
    }

    private fun findVoiceAttachmentByMeshPacketId(packetLong: Long): ChannelVoiceAttachment? =
        _voiceAttachments.value.find {
            it.meshPacketId != null &&
                (it.meshPacketId!!.toLong() and 0xFFFF_FFFFL) == packetLong
        }

    private suspend fun sendVoicePacketsInternal(
        packets: List<ByteArray>,
        stableKey: String,
    ): Pair<Boolean, String?> {
        var lastPktId: UInt? = null
        return try {
            withTimeout(45_000L) {
                suspendCancellableCoroutine { cont ->
                    MeshChannelMessaging.sendVoicePacketsDirect(
                        app.applicationContext,
                        deviceMacNorm,
                        meshChannel.index,
                        peerToRadio,
                        packets,
                        onBeforeQueue = { pktId ->
                            lastPktId = pktId
                            pendingAckByPacketId[pktId] = PendingOutgoingAck.Voice(stableKey, broadcastChannel = false)
                            updateVoiceMeshPacketId(stableKey, pktId)
                            updateVoiceDelivery(stableKey, ChatMessageDeliveryStatus.SENT_TO_NODE)
                        },
                    ) { ok, err ->
                        val pid = lastPktId
                        if (!ok) {
                            if (pid != null) pendingAckByPacketId.remove(pid)
                            updateVoiceDelivery(stableKey, ChatMessageDeliveryStatus.FAILED)
                        }
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

    private suspend fun sendTapbackBle(emoji: String, targetMessageFrom: UInt, targetPacketId: UInt) {
        val trimmed = emoji.trim()
        if (trimmed.isEmpty()) return
        if (!isTransportReady()) return
        val targetPacketLong = targetPacketId.toLong() and 0xFFFF_FFFFL
        val targetRow = dao.getByMeshPacketIdDirectThread(deviceMacNorm, peerNodeNumLong, targetPacketLong)
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
        val tPid = targetRow?.meshPacketId?.toUInt() ?: voice?.meshPacketId ?: return
        val bytes = if (beforeMine) {
            MeshWireLoRaToRadioEncoder.encodeReactionRevokeToRadio(
                tPid,
                meshChannel.index.toUInt(),
                to = peerToRadio,
            )
        } else {
            if (utf8.isEmpty()) return
            MeshWireLoRaToRadioEncoder.encodeTapbackToRadio(
                utf8,
                tPid,
                meshChannel.index.toUInt(),
                to = peerToRadio,
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

    /** Как [ChannelChatManager.sendReaction]: toggle → тот же путь, что [sendReactionUpdate]. */
    private suspend fun sendReactionBle(messageId: Long, emojiListIndex: Int) {
        val trimmed = MeshReactionEmojiRegistry.emojiAtListIndex(emojiListIndex)?.trim() ?: return
        if (trimmed.isEmpty()) return
        if (!isTransportReady()) return
        val targetPacketLong = messageId and 0xFFFF_FFFFL
        val targetRow = dao.getByMeshPacketIdDirectThread(deviceMacNorm, peerNodeNumLong, targetPacketLong)
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
        val targetRow = dao.getByMeshPacketIdDirectThread(deviceMacNorm, peerNodeNumLong, targetPacketLong)
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
            meshChannel.index.toUInt(),
            to = peerToRadio,
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

    private suspend fun retryFailedOutgoingTextSuspend(rowId: Long) {
        val row = dao.getByIdDirectThread(deviceMacNorm, rowId, peerNodeNumLong) ?: return
        if (!row.isOutgoing || row.deliveryStatus != ChatMessageDeliveryStatus.FAILED.code) return
        if (!isTransportReady()) {
            Log.w(TAG, "retryFailedOutgoingText: транспорт недоступен")
            return
        }
        row.meshPacketId?.let { pendingAckByPacketId.remove((it and 0xFFFF_FFFFL).toUInt()) }
        dao.clearMeshPacketIdForDirectThreadRow(deviceMacNorm, rowId, peerNodeNumLong)
        dao.updateDelivery(rowId, ChatMessageDeliveryStatus.SENDING.code, null)
        val replyTo = row.replyToPacketId?.let { (it and 0xFFFF_FFFFL).toUInt() }
        outgoing.send(OutgoingSendJob(row.text, rowId, replyTo))
    }

    private suspend fun enqueueOutgoing(
        trimmedInput: String,
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
        val dest = peerNodeNumLong and 0xFFFF_FFFFL
        val row = ChannelChatMessageEntity(
            deviceMac = deviceMacNorm,
            channelIndex = meshChannel.index,
            dmPeerNodeNum = peerNodeNumLong,
            dedupKey = "dm_out_${UUID.randomUUID()}",
            isOutgoing = true,
            text = trimmed,
            fromNodeNum = my,
            toNodeNum = dest,
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
                ChatHistoryFileStore.appendDirectTextMessage(
                    app,
                    deviceMacNorm,
                    meshChannel.index,
                    row.copy(id = rowId),
                    historyNodeIdHex,
                    peerHistoryFolderName,
                )
            }
        }
        outgoing.send(OutgoingSendJob(trimmed, rowId, replyToPacketId))
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
        val toRadio = peerToRadio
        val replyId = job.replyToPacketId
        val (bytes, pktId) = if (replyId != null) {
            MeshWireLoRaToRadioEncoder.encodeTextReplyMessageToRadioWithId(
                textUtf8 = utf8,
                channelIndex = meshChannel.index.toUInt(),
                replyToMeshPacketId = replyId,
                to = toRadio,
                wantAck = true,
            )
        } else {
            MeshWireLoRaToRadioEncoder.encodeTextMessageToRadioWithId(
                textUtf8 = utf8,
                channelIndex = meshChannel.index.toUInt(),
                to = toRadio,
                wantAck = true,
            )
        }
        ChatProtobufDebugLog.hexDump("ToRadio DM text", bytes)
        // Сразу после известного pktId: иначе параллельный processFromRadioBytes может
        // обработать ROUTING во время suspend у dao.updateMeshPacketId и не найти pending.
        pendingAckByPacketId[pktId] = PendingOutgoingAck.Text(job.rowId, broadcastChannel = false)
        dao.updateMeshPacketId(job.rowId, pktId.toLong() and 0xFFFF_FFFFL)
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

    private suspend fun processFromRadioBytes(bytes: ByteArray) {
        ChatProtobufDebugLog.hexDump("FromRadio frame (DM)", bytes)
        val rawPayloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(bytes)
        val payloads = PacketDispatcher.prioritizeMeshPayloads(rawPayloads)
        for (p in payloads) {
            when (p.portnum) {
                MeshWireFromRadioMeshPacketParser.PORTNUM_ROUTING_APP -> handleRouting(p)
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_APP,
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_COMPRESSED_APP,
                -> handleDirectAuxText(p)
                else -> Unit
            }
        }
        BleTransportManager.noteFromRadioConsumed()
    }

    private suspend fun handleDirectAuxText(p: ParsedMeshDataPayload) {
        val local = localNodeNum ?: return
        val to = p.to ?: return
        if (to != local || MeshChannelMessaging.isLikelyChannelMeshTraffic(p)) return
        if (!MeshChannelMessaging.matchesChannel(p, meshChannel)) return
        if (MeshWireFromRadioMeshPacketParser.isReactionRevokePayload(p)) {
            handleDirectReactionRevoke(p)
            return
        }
        if (MeshWireFromRadioMeshPacketParser.isTapbackPayload(p)) {
            when {
                p.payload.size == 2 -> {
                    val wid = p.payload[0].toInt() and 0xFF
                    val adding = p.payload[1] != 0.toByte()
                    val emojiStr = MeshReactionEmojiRegistry.emojiForWireId(wid)
                        ?: MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)?.trim().orEmpty()
                    if (emojiStr.isEmpty()) return
                    if (!adding) {
                        handleDirectIncomingWireRemove(p, emojiStr)
                        return
                    }
                    handleDirectTapbackReceived(p, emojiStr)
                    return
                }
                p.payload.size == 1 -> {
                    val wid = p.payload[0].toInt() and 0xFF
                    val emojiStr = MeshReactionEmojiRegistry.emojiForWireId(wid)
                        ?: MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)?.trim().orEmpty()
                    if (emojiStr.isEmpty()) return
                    handleDirectTapbackReceived(p, emojiStr)
                    return
                }
                else -> {
                    val emoji = MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)?.trim().orEmpty()
                    handleDirectTapbackReceived(p, emoji)
                    return
                }
            }
        }
    }

    private suspend fun handleDirectTapbackReceived(p: ParsedMeshDataPayload, emoji: String) {
        val replyId = p.dataReplyId ?: return
        val reactor = p.logicalFrom() ?: return
        val text = emoji.trim()
        val atMillis = p.rxTimeSec?.let { it.toLong() * 1000L } ?: System.currentTimeMillis()
        val tid = replyId.toLong() and 0xFFFF_FFFFL
        val reactorL = reactor.toLong() and 0xFFFF_FFFFL
        if (text.isEmpty()) {
            if (!messageRepository.clearAllReactionsFromSenderOnMessageDirect(
                    deviceMacNorm,
                    tid,
                    reactorL,
                )
            ) {
                clearDirectVoiceReactionsFromSender(tid, reactorL)
            }
            return
        }
        if (!messageRepository.applyReactionTapOrToggleDirect(
                deviceMacNorm,
                tid,
                reactorL,
                text,
                atMillis,
            )
        ) {
            applyDirectVoiceReactionTapOrToggle(tid, reactorL, text, atMillis)
        }
    }

    private suspend fun handleDirectReactionRevoke(p: ParsedMeshDataPayload) {
        val replyId = p.dataReplyId ?: return
        val reactor = p.logicalFrom() ?: return
        val tid = replyId.toLong() and 0xFFFF_FFFFL
        val reactorL = reactor.toLong() and 0xFFFF_FFFFL
        if (!messageRepository.clearAllReactionsFromSenderOnMessageDirect(
                deviceMacNorm,
                tid,
                reactorL,
            )
        ) {
            clearDirectVoiceReactionsFromSender(tid, reactorL)
        }
    }

    private suspend fun handleDirectIncomingWireRemove(p: ParsedMeshDataPayload, emoji: String) {
        val replyId = p.dataReplyId ?: return
        val reactor = p.logicalFrom() ?: return
        val tid = replyId.toLong() and 0xFFFF_FFFFL
        val reactorL = reactor.toLong() and 0xFFFF_FFFFL
        val em = emoji.trim()
        if (em.isEmpty()) return
        if (!messageRepository.removeSenderEmojiIfPresentDirect(
                deviceMacNorm,
                tid,
                reactorL,
                em,
            )
        ) {
            removeDirectVoiceSenderEmojiIfPresent(tid, reactorL, em)
        }
    }

    private fun applyDirectVoiceReactionTapOrToggle(
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

    private fun clearDirectVoiceReactionsFromSender(tid: Long, reactorL: Long) {
        val a = findVoiceAttachmentByMeshPacketId(tid) ?: return
        val reactorKey = reactorL.toString()
        val nextJson = ChatMessageReactionsJson.removeAllFromSender(a.reactionsJson, reactorKey)
        if (nextJson == a.reactionsJson) return
        updateVoiceReactionsJson(a.stableId, nextJson)
    }

    private fun removeDirectVoiceSenderEmojiIfPresent(tid: Long, reactorL: Long, emoji: String) {
        val a = findVoiceAttachmentByMeshPacketId(tid) ?: return
        val reactorKey = reactorL.toString()
        val nextJson = ChatMessageReactionsJson.removeSenderEmojiIfPresent(a.reactionsJson, reactorKey, emoji)
        if (nextJson == a.reactionsJson) return
        updateVoiceReactionsJson(a.stableId, nextJson)
    }

    private suspend fun handleRouting(p: ParsedMeshDataPayload) {
        val pid = p.dataRequestId
            ?: p.packetId?.takeIf { pendingAckByPacketId.containsKey(it) }
            ?: run {
                Log.v(
                    TAG,
                    "ROUTING DM без request_id / нет pending: meshPacketId=${p.packetId} dataRequestId=${p.dataRequestId}",
                )
                return
            }
        val pending = pendingAckByPacketId[pid] ?: run {
            Log.v(
                TAG,
                "ROUTING DM без pending ackFor=$pid meshPacketId=${p.packetId} dataRequestId=${p.dataRequestId}",
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

    private suspend fun applyRoutingSuccessFirstOrSecond(pid: UInt, pending: PendingOutgoingAck) {
        removePendingAck(pid)
    }

    private suspend fun maybeRecordOutgoingTextToMessageHistory(rowId: Long) {
        val row = dao.getByIdDirectThread(deviceMacNorm, rowId, peerNodeNumLong) ?: return
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
            meshChannel.index,
            row,
            channelDisplayName = historyTitle,
        )
    }

    private fun removePendingAck(pid: UInt) {
        pendingAckByPacketId.remove(pid)
    }

    private fun meshReplyPreviewSnippet(body: String): String {
        val oneLine = body.replace("\r\n", "\n").replace("\n", " ").trim()
        return if (oneLine.length > 200) oneLine.take(197) + "…" else oneLine
    }

    private fun isTransportReady(): Boolean {
        if (MeshDeviceTransport.isTcpAddress(deviceMacNorm) ||
            MeshDeviceTransport.isUsbAddress(deviceMacNorm)
        ) return true
        val mgr = app.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter?.isEnabled == true
    }
}
