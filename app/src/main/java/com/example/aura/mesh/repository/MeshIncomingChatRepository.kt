package com.example.aura.mesh.repository

import android.util.Log
import com.example.aura.AuraApplication
import com.example.aura.app.AppUptimeTracker
import com.example.aura.bluetooth.MeshChannelMessaging
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.MeshImageChunkCodec
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.chat.ChannelImageAttachment
import com.example.aura.chat.ChannelVoiceAttachment
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.chat.history.ChatHistoryFileStore
import com.example.aura.history.MessageHistoryRecorder
import com.example.aura.data.local.ChannelChatMessageDao
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.ParsedMeshDataPayload
import com.example.aura.mesh.PacketDispatcher
import com.example.aura.mesh.incoming.persistInboundChannelTextIfNew
import com.example.aura.mesh.incoming.persistInboundDirectTextIfNew
import com.example.aura.mesh.incoming.resolveInboundDmPeerUInt
import com.example.aura.mesh.nodedb.MeshNodeDbRepository
import com.example.aura.mesh.transport.BleTransportManager
import com.example.aura.mesh.uptime.MeshUptimeRoutingWaiter
import com.example.aura.meshwire.MeshWireAuraPeerUptimeCodec
import com.example.aura.meshwire.MeshWireAuraVipCodec
import com.example.aura.meshwire.MeshWireAuraVipRecoveryCodec
import com.example.aura.meshwire.MeshWireAuraVipUsedCodesCodec
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.security.VipExtensionUsedCodes
import com.example.aura.vip.VipStatusStore
import com.example.aura.vip.VipUsedCodesMeshStore
import com.example.aura.notifications.MeshNotificationDispatcher
import com.example.aura.security.NodeAuthStore
import com.example.aura.ui.map.BeaconTransportLabel
import com.example.aura.ui.map.MapBeaconSyncRepository
import com.example.aura.voice.VoiceLoRaFragmentCodec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MeshIncomingChat"

/** Событие: в БД записано новое входящее текстовое сообщение канала. */
data class IncomingMeshChatMessage(val entity: ChannelChatMessageEntity)

/**
 * Собранное входящее голосовое (файл записан во внутреннее хранилище канала; см. [ChatHistoryFileStore]).
 * [ChannelChatManager] подписывается, чтобы обновить UI, когда открыт экран чата.
 */
data class IncomingMeshVoice(
    val deviceMacNorm: String,
    val channelIndex: Int,
    val attachment: ChannelVoiceAttachment,
    /** Не null — голос в личке от этого узла (папка [ChatHistoryFileStore.directThreadFolderName]). */
    val dmPeerNodeNum: Long? = null,
)

/** Собранное входящее фото в тред лички (сохранено в private_chats/…). */
data class IncomingMeshDirectImage(
    val deviceMacNorm: String,
    val channelIndex: Int,
    val dmPeerNodeNum: Long,
    val attachment: ChannelImageAttachment,
)

/**
 * Глобальный «packet listener» для чата из FromRadio: Room + [incomingMessages].
 * Каналы: [com.example.aura.mesh.incoming.persistInboundChannelTextIfNew].
 * Личные сообщения (TEXT на наш NodeID, не broadcast): [com.example.aura.mesh.incoming.persistInboundDirectTextIfNew]
 * — тред появляется в списке автоматически при первом сообщении.
 *
 * Голос (PRIVATE / Codec2 фрагменты) обрабатывается здесь же — иначе при закрытом чате
 * [ChannelChatManager] не слушает FromRadio и фрагменты теряются.
 *
 * Слушатель GATT регистрируется из [install]; не зависит от открытого экрана чата.
 * Работает в фоне, пока [com.example.aura.mesh.MessageResilientService] держит BLE и процесс жив
 * (см. также AlarmManager / WorkManager для перезапуска).
 */
object MeshIncomingChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingMessages = MutableSharedFlow<IncomingMeshChatMessage>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingMessages: SharedFlow<IncomingMeshChatMessage> = _incomingMessages.asSharedFlow()

    private val _incomingVoice = MutableSharedFlow<IncomingMeshVoice>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingVoice: SharedFlow<IncomingMeshVoice> = _incomingVoice.asSharedFlow()

    private val _incomingDirectImages = MutableSharedFlow<IncomingMeshDirectImage>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingDirectImages: SharedFlow<IncomingMeshDirectImage> = _incomingDirectImages.asSharedFlow()

    /**
     * Локально сохранён голос/картинка в личке (исходящие) — обновить превью в списке «Личные».
     */
    private val _dmThreadListPreviewRefresh = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val dmThreadListPreviewRefresh: SharedFlow<Unit> = _dmThreadListPreviewRefresh.asSharedFlow()

    fun notifyDirectThreadListPreviewRefresh() {
        _dmThreadListPreviewRefresh.tryEmit(Unit)
    }

    /** По паре устройство + индекс канала — независимо от экрана чата. */
    private val voiceReassemblers = ConcurrentHashMap<String, VoiceLoRaFragmentCodec.Reassembler>()

    /** Входящие чанки AUR1 для лички (ключ: mac|dmi|ch|from). */
    private val directImageReassemblers = ConcurrentHashMap<String, MeshImageChunkCodec.Reassembler>()

    @Volatile
    private var installed = false

    private lateinit var app: AuraApplication
    private lateinit var messageRepository: MessageRepository

    private val frameListener: (ByteArray) -> Unit = { frame ->
        scope.launch { processFromRadioFrame(frame) }
    }

    fun install(application: AuraApplication) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            app = application
            messageRepository = application.channelMessageRepository
            NodeGattConnection.addFrameListener(frameListener)
            Log.d(TAG, "install: global FromRadio listener registered")
        }
    }

    /**
     * Кадр FromRadio с TCP/USB (долгоживущий поток). Уведомления — только при свёрнутом приложении.
     */
    fun dispatchStreamFrame(
        deviceMacNorm: String,
        bytes: ByteArray,
        label: MeshNotificationDispatcher.TransportLabel,
    ) {
        scope.launch { processStreamFrame(deviceMacNorm, bytes, label) }
    }

    /**
     * FromRadio с GATT: [NodeGattConnection.targetDevice] + полная [NodeAuthStore.load] дают MAC,
     * иначе (нет пароля в сессии, racy GATT) — [loadDeviceAddressForPrefetch], [loadStoredIdentity],
     * [peekBleMacAfterUserDisconnect]. TCP/USB из prefs пропускаем — путь предназначен для BLE.
     */
    private fun resolveDeviceAddressForFromRadioGatt(ctx: android.content.Context): String? {
        val candidates: List<() -> String?> = listOf(
            {
                try {
                    NodeGattConnection.targetDevice?.address?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: SecurityException) {
                    null
                }
            },
            { NodeAuthStore.load(ctx)?.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } },
            { NodeAuthStore.loadDeviceAddressForPrefetch(ctx) },
            { NodeAuthStore.loadStoredIdentity(ctx)?.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } },
            { NodeAuthStore.peekBleMacAfterUserDisconnect(ctx) },
        )
        for (c in candidates) {
            val raw = c() ?: continue
            val k = MeshNodeSyncMemoryStore.normalizeKey(raw)
            if (MeshDeviceTransport.isTcpAddress(k) || MeshDeviceTransport.isUsbAddress(k)) continue
            return raw
        }
        return null
    }

    private suspend fun processFromRadioFrame(bytes: ByteArray) {
        // Маячки карты: единая точка приёма BLE FromRadio (раньше был отдельный [NodeGattConnection.addFrameListener]).
        MapBeaconSyncRepository.consumeStreamFrame(bytes, BeaconTransportLabel.BLE)
        val addr = resolveDeviceAddressForFromRadioGatt(app.applicationContext) ?: return
        val deviceMacNorm = MeshNodeSyncMemoryStore.normalizeKey(addr)
        val localNodeNum = NodeGattConnection.myNodeNum.value

        MeshNotificationDispatcher.dispatchFromRadioFrame(
            app.applicationContext,
            deviceMacNorm,
            bytes,
            localNodeNum,
            MeshNotificationDispatcher.TransportLabel.NONE,
            onlyNotifyWhenBackground = true,
        )

        val rawPayloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(bytes)
        if (rawPayloads.isEmpty() && bytes.size > 12) {
            BleTransportManager.recordParseFailure(bytes)
        }
        val payloads = PacketDispatcher.prioritizeMeshPayloads(rawPayloads)
        val dao = app.chatDatabase.channelChatMessageDao()
        for (p in payloads) {
            when (p.portnum) {
                MeshWireFromRadioMeshPacketParser.PORTNUM_ROUTING_APP -> {
                    MeshUptimeRoutingWaiter.deliverRouting(p)
                }
                MeshWireAuraPeerUptimeCodec.PORTNUM -> {
                    ingestAuraPeerUptime(deviceMacNorm, p)
                }
                MeshWireAuraVipCodec.PORTNUM -> {
                    ingestAuraVip(localNodeNum, p)
                }
                MeshWireAuraVipRecoveryCodec.PORTNUM -> {
                    ingestAuraVipRecovery(localNodeNum, p)
                }
                MeshWireAuraVipUsedCodesCodec.PORTNUM -> {
                    ingestAuraVipUsedCodes(localNodeNum, p)
                }
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_APP,
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_COMPRESSED_APP,
                -> {
                    ingestInboundTextMessage(dao, deviceMacNorm, localNodeNum, p)
                }
                MeshWireFromRadioMeshPacketParser.PORTNUM_PRIVATE_APP -> {
                    handleInboundVoice(deviceMacNorm, localNodeNum, p)
                }
            }
        }
        BleTransportManager.noteFromRadioConsumed()
    }

    private suspend fun ingestInboundTextMessage(
        dao: ChannelChatMessageDao,
        deviceMacNorm: String,
        localNodeNum: UInt?,
        p: ParsedMeshDataPayload,
    ) {
        if (tryIngestDirectDmImageChunk(deviceMacNorm, localNodeNum, p)) return
        val channelRow = persistInboundChannelTextIfNew(
            dao,
            messageRepository,
            deviceMacNorm,
            localNodeNum,
            p,
        )
        val inserted = channelRow ?: persistInboundDirectTextIfNew(
            dao,
            messageRepository,
            deviceMacNorm,
            app.applicationContext,
            localNodeNum,
            p,
        )
        if (inserted != null) {
            if (inserted.dmPeerNodeNum != null) {
                appendDirectChatHistoryFile(deviceMacNorm, inserted)
            }
            _incomingMessages.emit(IncomingMeshChatMessage(inserted))
        }
    }

    private fun directThreadFolderForPeer(deviceMacNorm: String, peerLong: Long): String =
        ChatHistoryFileStore.directThreadFolderNameForPeer(app.applicationContext, deviceMacNorm, peerLong)

    /**
     * Чанки AUR1 в адресованный на нас DM (не широковещание по каналу).
     * @return true если пакет обработан как чанк картинки (в т.ч. неполная сборка).
     */
    private suspend fun tryIngestDirectDmImageChunk(
        deviceMacNorm: String,
        localNodeNum: UInt?,
        p: ParsedMeshDataPayload,
    ): Boolean {
        val local = localNodeNum ?: return false
        val to = p.to ?: return false
        if (to != local || MeshChannelMessaging.isLikelyChannelMeshTraffic(p)) return false
        val fromWire = p.logicalFrom() ?: return false
        val from = resolveInboundDmPeerUInt(deviceMacNorm, app.applicationContext, local, p).takeIf { it != 0u } ?: fromWire
        val raw = MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload) ?: return false
        if (!raw.startsWith(MeshImageChunkCodec.PREFIX)) return false
        val channelIndex = (p.channel ?: 0u).toInt()
        val peerLong = from.toLong() and 0xFFFF_FFFFL
        if (MeshNodeListDiskCache.isPeerIgnoredInCache(app.applicationContext, deviceMacNorm, peerLong)) return true
        val rKey = "$deviceMacNorm|dmi|$channelIndex|${from.toLong() and 0xFFFFFFFFL}"
        val reassembler = directImageReassemblers.getOrPut(rKey) { MeshImageChunkCodec.Reassembler() }
        val line = MeshImageChunkCodec.tryParseLine(raw) ?: return true
        val jpeg = reassembler.ingest(line) ?: return true
        val mine = from == local
        val attach = ChannelImageAttachment(
            stableId = "${line.sessionId}_img_${jpeg.contentHashCode()}",
            from = from,
            jpeg = jpeg,
            mine = mine,
            timeMs = p.rxTimeSec?.let { it.toLong() * 1000L } ?: System.currentTimeMillis(),
        )
        val nid = NodeAuthStore.load(app.applicationContext)?.nodeId
            ?.let { ChatHistoryFileStore.normalizeNodeIdHex(it) }
            .orEmpty()
        val folder = directThreadFolderForPeer(deviceMacNorm, peerLong)
        withContext(Dispatchers.IO) {
            ChatHistoryFileStore.saveImageAttachmentDirect(
                app.applicationContext,
                nid.ifEmpty { null },
                folder,
                attach,
            )
        }
        val ev = IncomingMeshDirectImage(deviceMacNorm, channelIndex, peerLong, attach)
        if (!_incomingDirectImages.tryEmit(ev)) {
            Log.w(TAG, "incomingDirectImages buffer full stableId=${attach.stableId}")
        }
        return true
    }

    private suspend fun appendDirectChatHistoryFile(deviceMacNorm: String, entity: ChannelChatMessageEntity) {
        val peer = entity.dmPeerNodeNum ?: return
        val nid = NodeAuthStore.load(app.applicationContext)?.nodeId
            ?.let { ChatHistoryFileStore.normalizeNodeIdHex(it) }
            .orEmpty()
        val nodes = MeshNodeListDiskCache.load(app.applicationContext, deviceMacNorm)
        val label = nodes?.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == (peer and 0xFFFF_FFFFL) }?.displayLongName()
            ?: MeshWireNodeNum.formatHex((peer and 0xFFFF_FFFFL).toUInt())
        val folder = ChatHistoryFileStore.directThreadFolderName(label, peer and 0xFFFF_FFFFL)
        ChatHistoryFileStore.appendDirectTextMessage(
            app.applicationContext,
            deviceMacNorm,
            entity.channelIndex,
            entity,
            nid.ifEmpty { null },
            folder,
        )
    }

    private suspend fun processStreamFrame(
        deviceMacNorm: String,
        bytes: ByteArray,
        label: MeshNotificationDispatcher.TransportLabel,
    ) {
        val beaconTransport = when (label) {
            MeshNotificationDispatcher.TransportLabel.WIFI -> BeaconTransportLabel.WIFI
            MeshNotificationDispatcher.TransportLabel.USB -> BeaconTransportLabel.USB
            MeshNotificationDispatcher.TransportLabel.NONE -> BeaconTransportLabel.UNKNOWN
        }
        MapBeaconSyncRepository.consumeStreamFrame(bytes, beaconTransport)
        val localNodeNum = NodeAuthStore.load(app.applicationContext)?.nodeId?.let { MeshWireNodeNum.parseToUInt(it) }
        MeshNotificationDispatcher.dispatchFromRadioFrame(
            app.applicationContext,
            deviceMacNorm,
            bytes,
            localNodeNum,
            label,
            onlyNotifyWhenBackground = true,
        )
        val rawPayloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(bytes)
        if (rawPayloads.isEmpty() && bytes.size > 12) {
            BleTransportManager.recordParseFailure(bytes)
        }
        val payloads = PacketDispatcher.prioritizeMeshPayloads(rawPayloads)
        val dao = app.chatDatabase.channelChatMessageDao()
        for (p in payloads) {
            when (p.portnum) {
                MeshWireFromRadioMeshPacketParser.PORTNUM_ROUTING_APP -> {
                    MeshUptimeRoutingWaiter.deliverRouting(p)
                }
                MeshWireAuraPeerUptimeCodec.PORTNUM -> {
                    ingestAuraPeerUptime(deviceMacNorm, p)
                }
                MeshWireAuraVipCodec.PORTNUM -> {
                    ingestAuraVip(localNodeNum, p)
                }
                MeshWireAuraVipRecoveryCodec.PORTNUM -> {
                    ingestAuraVipRecovery(localNodeNum, p)
                }
                MeshWireAuraVipUsedCodesCodec.PORTNUM -> {
                    ingestAuraVipUsedCodes(localNodeNum, p)
                }
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_APP,
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_COMPRESSED_APP,
                -> {
                    ingestInboundTextMessage(dao, deviceMacNorm, localNodeNum, p)
                }
                MeshWireFromRadioMeshPacketParser.PORTNUM_PRIVATE_APP -> {
                    handleInboundVoice(deviceMacNorm, localNodeNum, p)
                }
            }
        }
    }

    /**
     * Приём VIP-статуса Aura (portnum 503) — обновляем [VipStatusStore]. Только для **других** узлов.
     * Если заявленный `node_num` в payload отличается от логического `from` в MeshPacket — пакет
     * игнорируется как подозрительный.
     */
    private fun ingestAuraVip(localNodeNum: UInt?, p: ParsedMeshDataPayload) {
        val from = p.logicalFrom() ?: return
        if (localNodeNum != null && from == localNodeNum) return
        val payload = MeshWireAuraVipCodec.parsePayload(p.payload) ?: return
        val declared = payload.nodeNum
        if ((declared.toLong() and 0xFFFF_FFFFL) != (from.toLong() and 0xFFFF_FFFFL)) return
        VipStatusStore.applyBroadcast(
            context = app.applicationContext,
            nodeNum = from,
            active = payload.active,
            validForSec = payload.validForSec,
            remainingSec = payload.remainingSec,
        )
    }

    /**
     * Приём запросов и ответов восстановления VIP-таймера (portnum 504).
     *
     *  • **Запрос** (`is_response = false`): широковещание от свежеустановленного клиента.
     *    Сверяем `subject_node_num` с логическим `from` — если совпадает и у нас есть что
     *    сказать ([VipStatusStore.peerTimerSnapshot] и/или [MeshNodeDbRepository.peerLastKnownUptimeSecForNode])
     *    — шлём unicast-ответ обратно (VIP-поля могут быть пустыми, если помним только аптайм).
     *  • **Ответ** (`is_response = true`): unicast нам. Если `subject == myNodeNum` — при наличии
     *    VIP в ответе сохраняем кандидата в [VipAccessPreferences.applyMeshRecoveredDeadline];
     *    при `app_uptime_sec` > 0 — [AppUptimeTracker.applyMeshRecoveredUptimeSec].
     */
    private fun ingestAuraVipRecovery(localNodeNum: UInt?, p: ParsedMeshDataPayload) {
        val payload = MeshWireAuraVipRecoveryCodec.parsePayload(p.payload) ?: return
        val from = p.logicalFrom() ?: return
        val ctx = app.applicationContext
        if (!payload.isResponse) {
            // Пришёл запрос от `from`. Запрос должен быть «про себя»: subject == from.
            if ((payload.subjectNodeNum.toLong() and 0xFFFF_FFFFL) != (from.toLong() and 0xFFFF_FFFFL)) return
            // Свои запросы игнорируем (на всякий случай, если пакет вернулся нам же).
            if (localNodeNum != null && from == localNodeNum) return
            val snapshot = VipStatusStore.peerTimerSnapshot(from)
            val uptimeSec = MeshNodeDbRepository.peerLastKnownUptimeSecForNode(from)
            if (snapshot == null && uptimeSec <= 0L) {
                return
            }
            // Сами не активны в эфире — ответить невозможно, промолчим.
            if (!com.example.aura.bluetooth.NodeGattConnection.isReady) return
            val reply = MeshWireLoRaToRadioEncoder.encodeAuraVipRecoveryResponse(
                subjectNodeNum = from,
                requesterNodeNum = from,
                remainingSec = snapshot?.remainingSec ?: 0u,
                unlockedForever = false,
                appUptimeSec = uptimeSec,
            )
            com.example.aura.bluetooth.NodeGattConnection.sendToRadio(reply) { ok, err ->
                if (!ok) Log.w(TAG, "vip-recovery reply failed: ${err ?: "?"}")
            }
        } else {
            // Ответ. Интересует только тот, что адресован нам.
            val my = localNodeNum ?: return
            if ((payload.subjectNodeNum.toLong() and 0xFFFF_FFFFL) != (my.toLong() and 0xFFFF_FFFFL)) return
            // Нельзя принимать «восстановление от самого себя» (анти-луп).
            if (from == my) return
            if (payload.remainingSec > 0u) {
                val nowMs = System.currentTimeMillis()
                val deadlineMs = nowMs + (payload.remainingSec.toLong() * 1000L)
                VipAccessPreferences.applyMeshRecoveredDeadline(
                    context = ctx,
                    candidateDeadlineMs = deadlineMs,
                    unlockedForever = false,
                )
            }
            if (payload.appUptimeSec > 0L) {
                AppUptimeTracker.applyMeshRecoveredUptimeSec(ctx, payload.appUptimeSec)
            }
        }
    }

    /**
     * Приём анонсов/запросов/ответов по использованным VIP-кодам (portnum 505).
     *
     *  • **ANNOUNCE** — сосед только что потратил код. Запоминаем хэши в
     *    [VipUsedCodesMeshStore], чтобы позже вернуть ему по запросу.
     *  • **REQUEST** — сосед (subject == from) просит всё, что мы про него помним. Шлём
     *    unicast-ответ; собственные запросы игнорируем.
     *  • **RESPONSE** — нам ответили про наш собственный subject. Добавляем хэши в локальный
     *    хэш-регистр [VipExtensionUsedCodes.acceptMeshHashes] — повторный ввод того же кода
     *    теперь будет заблокирован.
     */
    private fun ingestAuraVipUsedCodes(localNodeNum: UInt?, p: ParsedMeshDataPayload) {
        val payload = MeshWireAuraVipUsedCodesCodec.parsePayload(p.payload) ?: return
        val from = p.logicalFrom() ?: return
        val ctx = app.applicationContext
        when (payload.kind) {
            MeshWireAuraVipUsedCodesCodec.Kind.ANNOUNCE -> {
                // Анонс должен быть «про себя»: subject == from.
                if ((payload.subjectNodeNum.toLong() and 0xFFFF_FFFFL) != (from.toLong() and 0xFFFF_FFFFL)) return
                if (payload.hashes.isEmpty()) return
                VipUsedCodesMeshStore.remember(ctx, from, payload.hashes)
            }
            MeshWireAuraVipUsedCodesCodec.Kind.REQUEST -> {
                if ((payload.subjectNodeNum.toLong() and 0xFFFF_FFFFL) != (from.toLong() and 0xFFFF_FFFFL)) return
                if (localNodeNum != null && from == localNodeNum) return
                val hashes = VipUsedCodesMeshStore.snapshot(ctx, from)
                if (hashes.isEmpty()) return
                if (!com.example.aura.bluetooth.NodeGattConnection.isReady) return
                val reply = MeshWireLoRaToRadioEncoder.encodeAuraVipUsedCodesResponse(
                    subjectNodeNum = from,
                    requesterNodeNum = from,
                    hashes = hashes,
                )
                com.example.aura.bluetooth.NodeGattConnection.sendToRadio(reply) { ok, err ->
                    if (!ok) Log.w(TAG, "vip-used-codes reply failed: ${err ?: "?"}")
                }
            }
            MeshWireAuraVipUsedCodesCodec.Kind.RESPONSE -> {
                val my = localNodeNum ?: return
                if ((payload.subjectNodeNum.toLong() and 0xFFFF_FFFFL) != (my.toLong() and 0xFFFF_FFFFL)) return
                if (from == my) return
                if (payload.hashes.isEmpty()) return
                VipExtensionUsedCodes.acceptMeshHashes(ctx, payload.hashes)
                // Ретроактивная отмена: если мы уже успели применить один из этих кодов
                // (до того, как mesh-ответ дошёл) — вычитаем добавленные секунды.
                val rollbacks = VipExtensionUsedCodes.consumeMatchingPendingRedeems(ctx, payload.hashes)
                if (rollbacks.isNotEmpty()) {
                    val totalSeconds = rollbacks.sumOf { it.secondsAdded }
                    VipAccessPreferences.rollbackSeconds(ctx, totalSeconds)
                    Log.i(
                        TAG,
                        "vip-used-codes: revoked ${rollbacks.size} recent redeem(s), " +
                            "rolled back ${totalSeconds}s",
                    )
                }
            }
        }
    }

    private fun ingestAuraPeerUptime(deviceMacNorm: String, p: ParsedMeshDataPayload) {
        val from = p.logicalFrom() ?: return
        val parsed = MeshWireAuraPeerUptimeCodec.parsePayload(p.payload) ?: return
        val fromNum = from.toLong() and 0xFFFF_FFFFL
        val declared = parsed.first.toLong() and 0xFFFF_FFFFL
        if (declared != fromNum) return
        val recvSec = System.currentTimeMillis() / 1000L
        MeshNodeDbRepository.applyPeerUptimeFromNetwork(deviceMacNorm, fromNum, parsed.second, recvSec)
    }

    private suspend fun handleInboundVoice(
        deviceMacNorm: String,
        localNodeNum: UInt?,
        p: ParsedMeshDataPayload,
    ) {
        val raw = p.payload
        val frag = VoiceLoRaFragmentCodec.tryParsePacket(raw) ?: return
        if (!MeshChannelMessaging.allowsInboundChannelVoice(p, localNodeNum)) return
        val channelIndex = (p.channel ?: 0u).toInt()
        val directDm = localNodeNum != null && p.to == localNodeNum &&
            !MeshChannelMessaging.isLikelyChannelMeshTraffic(p)
        val fromWire = p.logicalFrom() ?: return
        val from =
            if (directDm) {
                val loc = localNodeNum ?: return
                resolveInboundDmPeerUInt(deviceMacNorm, app.applicationContext, loc, p).takeIf { it != 0u } ?: fromWire
            } else {
                fromWire
            }
        val peerLong = from.toLong() and 0xFFFF_FFFFL
        if (directDm && MeshNodeListDiskCache.isPeerIgnoredInCache(app.applicationContext, deviceMacNorm, peerLong)) return
        val key = if (directDm) {
            "$deviceMacNorm:dmv:$channelIndex:$peerLong"
        } else {
            "$deviceMacNorm:$channelIndex"
        }
        val reassembler = voiceReassemblers.getOrPut(key) { VoiceLoRaFragmentCodec.Reassembler() }
        val full = reassembler.ingest(from, frag) ?: return
        val mine = localNodeNum != null && from == localNodeNum
        val estDur = (full.size.coerceAtLeast(1) * 1000L / 400L).coerceIn(200L, 12_000L)
        val attach = ChannelVoiceAttachment(
            stableId = "v_${from.toLong() and 0xFFFFFFFFL}_${frag.recordId}_${full.contentHashCode()}",
            from = from,
            codecPayload = full,
            mine = mine,
            timeMs = p.rxTimeSec?.let { it.toLong() * 1000L } ?: System.currentTimeMillis(),
            durationMs = estDur,
            meshPacketId = p.packetId,
            voiceRecordId = frag.recordId,
        )
        if (directDm) {
            val nid = NodeAuthStore.load(app.applicationContext)?.nodeId
                ?.let { ChatHistoryFileStore.normalizeNodeIdHex(it) }
                .orEmpty()
            val folder = directThreadFolderForPeer(deviceMacNorm, peerLong)
            withContext(Dispatchers.IO) {
                ChatHistoryFileStore.saveVoiceAttachmentDirect(
                    app.applicationContext,
                    nid.ifEmpty { null },
                    folder,
                    attach,
                )
                if (!mine) {
                    MessageHistoryRecorder.repository?.recordVoiceMessage(
                        deviceMacNorm,
                        channelIndex,
                        attach,
                    )
                }
            }
            val ev = IncomingMeshVoice(deviceMacNorm, channelIndex, attach, dmPeerNodeNum = peerLong)
            if (!_incomingVoice.tryEmit(ev)) {
                Log.w(TAG, "incomingVoice buffer full; DM voice stableId=${attach.stableId}")
            }
        } else {
            withContext(Dispatchers.IO) {
                ChatHistoryFileStore.saveVoiceAttachment(app.applicationContext, deviceMacNorm, channelIndex, attach, null)
                if (!mine) {
                    MessageHistoryRecorder.repository?.recordVoiceMessage(deviceMacNorm, channelIndex, attach)
                }
            }
            val ev = IncomingMeshVoice(deviceMacNorm, channelIndex, attach, dmPeerNodeNum = null)
            if (!_incomingVoice.tryEmit(ev)) {
                Log.w(TAG, "incomingVoice buffer full; attachment saved to disk stableId=${attach.stableId}")
            }
        }
    }
}
