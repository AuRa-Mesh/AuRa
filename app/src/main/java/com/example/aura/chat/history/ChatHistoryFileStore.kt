package com.example.aura.chat.history

import android.content.Context
import android.util.Log
import com.example.aura.chat.ChannelImageAttachment
import com.example.aura.chat.ChannelVoiceAttachment
import com.example.aura.chat.ChatMessageDeliveryStatus
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.data.local.ChatMessageReactionsJson
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.security.NodeAuthStore
import com.example.aura.util.NodeIdHex
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

private const val TAG = "ChatHistoryFileStore"
private const val LEGACY_ROOT = "chat_history"
private const val ARCHIVE_PREFIX = "ChatHistory_"
private const val TEXT_LOG = "text_messages.jsonl"
private const val VOICE_INDEX = "voices_index.jsonl"
private const val IMAGE_INDEX = "images_index.jsonl"
private const val UI_CLEARED_AT_FILE = ".ui_cleared_at"
private const val PRIVATE_CHATS = "private_chats"

/**
 * Локальные файлы канала: [filesDir]/[ARCHIVE_PREFIX][NodeId]/channels/… — jsonl и вложения (зеркало для UI и очистки).
 * Без [nodeIdHex] используется путь [LEGACY_ROOT] (обратная совместимость).
 */
object ChatHistoryFileStore {

    private val locks = ConcurrentHashMap<String, Any>()

    /** Сюда не попадает [LeftCompositionCancellationException] и прочие отмены — их не логируем как I/O-ошибки. */
    private fun logEUnlessCancelled(message: String, e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, message, e)
    }

    private fun logWUnlessCancelled(message: String, e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, message, e)
    }

    private fun lockKey(nodeIdHex: String?, mac: String, ch: Int): String =
        "${nodeIdHex ?: "legacy"}|$mac|$ch"

    private fun lock(nodeIdHex: String?, mac: String, ch: Int): Any =
        locks.getOrPut(lockKey(nodeIdHex, mac, ch)) { Any() }

    fun normalizeNodeIdHex(raw: String): String = NodeIdHex.normalize(raw)

    fun legacyRootDir(context: Context): File =
        File(context.applicationContext.filesDir, LEGACY_ROOT).also { it.mkdirs() }

    /** Корень всех исторических данных (legacy). */
    fun rootDir(context: Context): File = legacyRootDir(context)

    fun archiveRootForNode(context: Context, nodeIdHex: String): File {
        val id = normalizeNodeIdHex(nodeIdHex).ifEmpty { return legacyRootDir(context) }
        return File(context.applicationContext.filesDir, "$ARCHIVE_PREFIX$id")
    }

    /**
     * Создаёт структуру: text_logs/, audio_messages/, channels/ (каналы пишутся отдельно).
     */
    fun ensureArchiveLayout(context: Context, nodeIdHex: String) {
        val id = normalizeNodeIdHex(nodeIdHex).ifEmpty { return }
        val root = archiveRootForNode(context, id)
        File(root, "text_logs").mkdirs()
        File(root, "audio_messages").mkdirs()
        File(root, "channels").mkdirs()
        File(root, PRIVATE_CHATS).mkdirs()
    }

    fun channelDir(context: Context, deviceMacNorm: String, channelIndex: Int, nodeIdHex: String? = null): File {
        val safeMac = deviceMacNorm.replace(Regex("[^0-9A-Fa-f]"), "_")
        val sub = "${safeMac}_ch$channelIndex"
        return if (nodeIdHex.isNullOrBlank()) {
            File(legacyRootDir(context), sub).also { it.mkdirs() }
        } else {
            val root = archiveRootForNode(context, nodeIdHex)
            File(root, "channels/$sub").also { it.mkdirs() }
        }
    }

    fun appendTextMessage(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        row: ChannelChatMessageEntity,
        nodeIdHex: String? = null,
    ) {
        synchronized(lock(nodeIdHex, deviceMacNorm, channelIndex)) {
            try {
                val dir = channelDir(context, deviceMacNorm, channelIndex, nodeIdHex)
                val jo = JSONObject()
                jo.put("type", "text")
                jo.put("id", row.id)
                jo.put("dedupKey", row.dedupKey)
                jo.put("out", row.isOutgoing)
                jo.put("from", row.fromNodeNum)
                jo.put("to", row.toNodeNum)
                jo.put("text", row.text)
                jo.put("ts", row.createdAtMs)
                row.meshPacketId?.let { jo.put("meshPacketId", it) }
                jo.put("delivery", row.deliveryStatus)
                row.replyToPacketId?.let { jo.put("replyToPacketId", it) }
                row.replyToFromNodeNum?.let { jo.put("replyToFromNodeNum", it) }
                row.replyPreviewText?.let { jo.put("replyPreviewText", it) }
                jo.put("viaMqtt", row.viaMqtt)
                appendLine(File(dir, TEXT_LOG), jo.toString())
            } catch (e: Exception) {
                logEUnlessCancelled("appendTextMessage", e)
            }
        }
    }

    /**
     * Имя папки в [PRIVATE_CHATS]: отображаемое имя + суффикс nodenum (hex), чтобы папки не пересекались при смене longName.
     */
    fun directThreadFolderName(displayLabel: String, peerNodeNum: Long): String {
        val segment = sanitizePrivateChatSegment(displayLabel).ifEmpty { "node" }
        val suffix = java.lang.Long.toHexString(peerNodeNum and 0xFFFF_FFFFL)
        return "${segment}_$suffix"
    }

    /**
     * Папка `private_chats/…` для пира: тот же алгоритм, что при приёме вложений
     * ([com.example.aura.mesh.repository.MeshIncomingChatRepository]).
     */
    fun directThreadFolderNameForPeer(
        context: Context,
        deviceMacNorm: String,
        peerNodeNumLong: Long,
    ): String {
        val ctx = context.applicationContext
        val p = peerNodeNumLong and 0xFFFF_FFFFL
        val nodes = MeshNodeListDiskCache.load(ctx, deviceMacNorm)
        val label = nodes?.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == p }?.displayLongName()
            ?: MeshWireNodeNum.formatHex(p.toUInt())
        return directThreadFolderName(label, p)
    }

    private fun sanitizePrivateChatSegment(s: String): String =
        s.trim().replace(Regex("""[\\/:*?"<>|#\n\r\t]"""), "_").take(72)

    fun privateDirectChatDir(context: Context, nodeIdHex: String, peerFolderName: String): File {
        val id = normalizeNodeIdHex(nodeIdHex).ifEmpty {
            return File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
        }
        val root = archiveRootForNode(context, id)
        return File(root, "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
    }

    private fun directLock(nodeIdHex: String?, peerFolderName: String): Any =
        locks.getOrPut("${nodeIdHex ?: "legacy"}|dm|$peerFolderName") { Any() }

    fun appendDirectTextMessage(
        context: Context,
        deviceMacNorm: String,
        meshChannelIndex: Int,
        row: ChannelChatMessageEntity,
        nodeIdHex: String?,
        peerFolderName: String,
    ) {
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            try {
                val dir = if (!nodeIdHex.isNullOrBlank()) {
                    privateDirectChatDir(context, nodeIdHex, peerFolderName)
                } else {
                    File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                }
                val jo = JSONObject()
                jo.put("type", "text_direct")
                jo.put("meshChannelIndex", meshChannelIndex)
                jo.put("mac", deviceMacNorm)
                jo.put("id", row.id)
                jo.put("dedupKey", row.dedupKey)
                jo.put("out", row.isOutgoing)
                jo.put("from", row.fromNodeNum)
                jo.put("to", row.toNodeNum)
                jo.put("text", row.text)
                jo.put("ts", row.createdAtMs)
                row.dmPeerNodeNum?.let { jo.put("dmPeerNodeNum", it) }
                row.meshPacketId?.let { jo.put("meshPacketId", it) }
                jo.put("delivery", row.deliveryStatus)
                row.replyToPacketId?.let { jo.put("replyToPacketId", it) }
                row.replyToFromNodeNum?.let { jo.put("replyToFromNodeNum", it) }
                row.replyPreviewText?.let { jo.put("replyPreviewText", it) }
                jo.put("viaMqtt", row.viaMqtt)
                appendLine(File(dir, TEXT_LOG), jo.toString())
            } catch (e: Exception) {
                logEUnlessCancelled("appendDirectTextMessage", e)
            }
        }
    }

    fun syncMissingDirectTextMessagesToArchive(
        context: Context,
        deviceMacNorm: String,
        meshChannelIndex: Int,
        peerFolderName: String,
        rows: List<ChannelChatMessageEntity>,
        nodeIdHex: String?,
    ) {
        if (rows.isEmpty()) return
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            val dir = if (!nodeIdHex.isNullOrBlank()) {
                privateDirectChatDir(context, nodeIdHex, peerFolderName)
            } else {
                File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
            }
            val textFile = File(dir, TEXT_LOG)
            val existingIds = HashSet<Long>()
            if (textFile.isFile) {
                try {
                    textFile.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            if (line.isBlank()) return@forEach
                            try {
                                val jo = JSONObject(line)
                                if (jo.optString("type", "") == "text_direct") {
                                    existingIds.add(jo.getLong("id"))
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (e: Exception) {
                    logEUnlessCancelled("syncMissingDirectTextMessagesToArchive read", e)
                }
            }
            for (row in rows) {
                if (row.id !in existingIds) {
                    appendDirectTextMessage(
                        context,
                        deviceMacNorm,
                        meshChannelIndex,
                        row,
                        nodeIdHex,
                        peerFolderName,
                    )
                    existingIds.add(row.id)
                }
            }
        }
    }

    /** Маркер очистки ленты личного чата в [privateDirectChatDir] (архив jsonl не удаляется). */
    fun markDirectThreadUiClearedNow(context: Context, nodeIdHex: String?, peerFolderName: String) {
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            try {
                val dir = if (!nodeIdHex.isNullOrBlank()) {
                    privateDirectChatDir(context, nodeIdHex, peerFolderName)
                } else {
                    File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                }
                dir.mkdirs()
                File(dir, UI_CLEARED_AT_FILE).writeText(System.currentTimeMillis().toString())
            } catch (e: Exception) {
                logEUnlessCancelled("markDirectThreadUiClearedNow", e)
            }
        }
    }

    fun readDirectThreadUiClearAtMs(context: Context, nodeIdHex: String?, peerFolderName: String): Long? {
        return try {
            val f = File(
                if (!nodeIdHex.isNullOrBlank()) {
                    privateDirectChatDir(context, nodeIdHex, peerFolderName)
                } else {
                    File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                },
                UI_CLEARED_AT_FILE,
            )
            if (!f.isFile) return null
            f.readText().trim().toLongOrNull()
        } catch (e: Exception) {
            logEUnlessCancelled("readDirectThreadUiClearAtMs", e)
            null
        }
    }

    /**
     * Маркер очистки UI может лежать под одним [nodeIdHex] (как в настройках), вложения от приёма — в архиве
     * по [NodeAuthStore] — смотрим все варианты, берём макс. время (самая «поздняя» граница среза).
     */
    fun readDirectThreadUiClearAtMsMaxAcrossRoots(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
    ): Long? = directThreadNodeIdReadRoots(context, nodeIdHex)
        .mapNotNull { readDirectThreadUiClearAtMs(context, it, peerFolderName) }
        .maxOrNull()

    /**
     * Удаляет из папки чата только голос/картинки и индексы (и маркер [.ui_cleared_at]).
     * [TEXT_LOG] с архивом текста не трогаем — при «Очистить историю» он уже дописан.
     */
    private fun purgeLocalMediaInChatDir(dir: File) {
        if (!dir.isDirectory) return
        try {
            File(dir, "voices").takeIf { it.isDirectory }?.listFiles()?.forEach { f -> f.delete() }
            File(dir, "images").takeIf { it.isDirectory }?.listFiles()?.forEach { f -> f.delete() }
            File(dir, VOICE_INDEX).delete()
            File(dir, IMAGE_INDEX).delete()
            File(dir, UI_CLEARED_AT_FILE).delete()
        } catch (e: Exception) {
            logEUnlessCancelled("purgeLocalMediaInChatDir", e)
        }
    }

    /**
     * Сброс голоса/фото в канале (и в папке по [nodeIdHex], и в legacy) — при полной/частичной
     * очистке истории, чтобы лента не «обрезалась» по времени, а реально соответствовала очищенной БД.
     */
    fun purgeAllChannelMediaForHistoryClear(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        nodeIdHex: String?,
    ) {
        val ctx = context.applicationContext
        val id = nodeIdHex?.trim()?.takeIf { it.isNotEmpty() }
        if (id != null) {
            synchronized(lock(id, deviceMacNorm, channelIndex)) {
                purgeLocalMediaInChatDir(channelDir(ctx, deviceMacNorm, channelIndex, id))
            }
        }
        synchronized(lock(null, deviceMacNorm, channelIndex)) {
            purgeLocalMediaInChatDir(channelDir(ctx, deviceMacNorm, channelIndex, null))
        }
    }

    /**
     * Сброс голоса/фото в личке по всем корням (UI, auth, legacy) — согласовано с [loadVoiceAttachmentsDirectWithFallback].
     */
    fun purgeAllDirectThreadMediaForHistoryClear(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
    ) {
        val ctx = context.applicationContext
        for (r in directThreadNodeIdReadRoots(ctx, nodeIdHex)) {
            synchronized(directLock(r, peerFolderName)) {
                val dir = if (!r.isNullOrBlank()) {
                    privateDirectChatDir(ctx, r, peerFolderName)
                } else {
                    File(legacyRootDir(ctx), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                }
                purgeLocalMediaInChatDir(dir)
            }
        }
    }

    /** Корни: нормализованный [nodeIdHex] из UI, [NodeAuthStore] (как в приёме), legacy. */
    private fun directThreadNodeIdReadRoots(
        context: Context,
        nodeIdHex: String?,
    ): List<String?> {
        val ctx = context.applicationContext
        val fromUi = nodeIdHex?.let { normalizeNodeIdHex(it) }?.ifEmpty { null } ?: null
        val fromAuth = NodeAuthStore.load(ctx)?.nodeId
            ?.let { normalizeNodeIdHex(it) }?.ifEmpty { null } ?: null
        return buildList {
            if (fromUi != null) add(fromUi)
            if (fromAuth != null && fromAuth != fromUi) add(fromAuth)
            add(null)
        }
    }

    /**
     * Nodenum из имени `private_chats/имя_xxxx` (суффикс [Long.toHexString] пира), как [directThreadFolderName].
     */
    private fun parseDirectThreadFolderPeerNodeNum(folderName: String): Long? {
        val s = folderName.substringAfterLast('_', missingDelimiterValue = "")
        if (s.isEmpty() || s.length > 8) return null
        if (!s.matches(Regex("(?i)[0-9a-f]{1,8}"))) return null
        return try {
            java.lang.Long.parseLong(s, 16) and 0xFFFF_FFFFL
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun hasNonEmptyDirectMediaInFolder(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (File(dir, VOICE_INDEX).isFile && File(dir, VOICE_INDEX).length() > 0L) return true
        if (File(dir, IMAGE_INDEX).isFile && File(dir, IMAGE_INDEX).length() > 0L) return true
        val vdir = File(dir, "voices")
        if (vdir.isDirectory) {
            if ((vdir.listFiles() ?: emptyArray()).any { it.isFile && it.length() > 0L }) {
                return true
            }
        }
        val idir = File(dir, "images")
        if (idir.isDirectory) {
            if ((idir.listFiles() ?: emptyArray()).any { it.isFile && it.length() > 0L }) {
                return true
            }
        }
        return false
    }

    /**
     * Пиры, у кого в `private_chats/…` на диске есть голос/фото.
     * Используется вместе с Room, чтобы тред появился, если в БД ещё нет **текста**
     * (только вложения — типично исходящее голосовое).
     */
    fun listPrivateChatThreadPeersWithFileMedia(
        context: Context,
        nodeIdHex: String?,
    ): Set<Long> {
        val out = HashSet<Long>()
        val ctx = context.applicationContext
        for (r in directThreadNodeIdReadRoots(ctx, nodeIdHex)) {
            val privateRoot: File = if (r.isNullOrBlank()) {
                File(legacyRootDir(ctx), PRIVATE_CHATS)
            } else {
                File(archiveRootForNode(ctx, r), PRIVATE_CHATS)
            }
            if (!privateRoot.isDirectory) continue
            for (child in privateRoot.listFiles() ?: emptyArray()) {
                if (!child.isDirectory) continue
                val peer = parseDirectThreadFolderPeerNodeNum(child.name) ?: continue
                if (hasNonEmptyDirectMediaInFolder(child)) {
                    out.add(peer)
                }
            }
        }
        return out
    }

    fun saveVoiceAttachmentDirect(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
        a: ChannelVoiceAttachment,
    ) {
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            try {
                val dir = if (!nodeIdHex.isNullOrBlank()) {
                    privateDirectChatDir(context, nodeIdHex, peerFolderName)
                } else {
                    File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                }
                val vdir = File(dir, "voices").also { it.mkdirs() }
                val fname = safeFileName(a.stableId) + ".voc"
                val rel = "voices/$fname"
                File(vdir, fname).writeBytes(a.codecPayload)
                val jo = JSONObject()
                jo.put("type", "voice")
                jo.put("stableId", a.stableId)
                a.from?.let { jo.put("from", it.toLong() and 0xFFFF_FFFFL) }
                jo.put("mine", a.mine)
                jo.put("timeMs", a.timeMs)
                jo.put("durationMs", a.durationMs)
                jo.put("delivery", a.deliveryStatus)
                a.meshPacketId?.let { jo.put("meshPacketId", it.toLong() and 0xFFFF_FFFFL) }
                if (a.voiceRecordId != 0u) {
                    jo.put("voiceRecordId", a.voiceRecordId.toLong() and 0xFFFF_FFFFL)
                }
                if (a.reactionsJson.isNotBlank() && a.reactionsJson != ChatMessageReactionsJson.EMPTY_ARRAY) {
                    jo.put("reactionsJson", a.reactionsJson)
                }
                jo.put("file", rel)
                appendLine(File(dir, VOICE_INDEX), jo.toString())
            } catch (e: Exception) {
                logEUnlessCancelled("saveVoiceAttachmentDirect", e)
            }
        }
    }

    fun saveImageAttachmentDirect(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
        a: ChannelImageAttachment,
    ) {
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            try {
                val dir = if (!nodeIdHex.isNullOrBlank()) {
                    privateDirectChatDir(context, nodeIdHex, peerFolderName)
                } else {
                    File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                }
                val idir = File(dir, "images").also { it.mkdirs() }
                val fname = safeFileName(a.stableId) + ".jpg"
                val rel = "images/$fname"
                File(idir, fname).writeBytes(a.jpeg)
                val jo = JSONObject()
                jo.put("type", "image")
                jo.put("stableId", a.stableId)
                a.from?.let { jo.put("from", it.toLong() and 0xFFFF_FFFFL) }
                jo.put("mine", a.mine)
                jo.put("timeMs", a.timeMs)
                jo.put("file", rel)
                appendLine(File(dir, IMAGE_INDEX), jo.toString())
            } catch (e: Exception) {
                logEUnlessCancelled("saveImageAttachmentDirect", e)
            }
        }
    }

    fun loadVoiceAttachmentsDirect(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
    ): List<ChannelVoiceAttachment> {
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            val dir = if (!nodeIdHex.isNullOrBlank()) {
                privateDirectChatDir(context, nodeIdHex, peerFolderName)
            } else {
                File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
            }
            return loadVoicesFromChannelDir(dir)
        }
    }

    /**
     * Слияние: при приёме [MeshIncomingChatRepository] пишет под [NodeAuthStore] nodeId, в чате передают
     * [historyNodeIdHex] из UI — пути разъезжаются; дедуп по [ChannelVoiceAttachment.stableId], последняя копия.
     */
    fun loadVoiceAttachmentsDirectWithFallback(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
    ): List<ChannelVoiceAttachment> {
        val merged = linkedMapOf<String, ChannelVoiceAttachment>()
        for (r in directThreadNodeIdReadRoots(context, nodeIdHex)) {
            for (a in loadVoiceAttachmentsDirect(context, r, peerFolderName)) {
                merged[a.stableId] = a
            }
        }
        return merged.values.sortedBy { it.timeMs }
    }

    fun loadImageAttachmentsDirect(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
    ): List<ChannelImageAttachment> {
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            val dir = if (!nodeIdHex.isNullOrBlank()) {
                privateDirectChatDir(context, nodeIdHex, peerFolderName)
            } else {
                File(legacyRootDir(context), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
            }
            return loadImagesFromChannelDir(dir)
        }
    }

    fun loadImageAttachmentsDirectWithFallback(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
    ): List<ChannelImageAttachment> {
        val merged = linkedMapOf<String, ChannelImageAttachment>()
        for (r in directThreadNodeIdReadRoots(context, nodeIdHex)) {
            for (a in loadImageAttachmentsDirect(context, r, peerFolderName)) {
                merged[a.stableId] = a
            }
        }
        return merged.values.sortedBy { it.timeMs }
    }

    fun removeVoiceAttachmentDirect(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
        stableId: String,
    ) {
        val ctx = context.applicationContext
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            try {
                val dir = if (!nodeIdHex.isNullOrBlank()) {
                    privateDirectChatDir(ctx, nodeIdHex, peerFolderName)
                } else {
                    File(legacyRootDir(ctx), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                }
                removeVoiceEntriesFromDir(dir, stableId)
            } catch (e: Exception) {
                logEUnlessCancelled("removeVoiceAttachmentDirect", e)
            }
        }
    }

    fun removeImageAttachmentDirect(
        context: Context,
        nodeIdHex: String?,
        peerFolderName: String,
        stableId: String,
    ) {
        val ctx = context.applicationContext
        synchronized(directLock(nodeIdHex, peerFolderName)) {
            try {
                val dir = if (!nodeIdHex.isNullOrBlank()) {
                    privateDirectChatDir(ctx, nodeIdHex, peerFolderName)
                } else {
                    File(legacyRootDir(ctx), "$PRIVATE_CHATS/$peerFolderName").also { it.mkdirs() }
                }
                removeImageEntriesFromDir(dir, stableId)
            } catch (e: Exception) {
                logEUnlessCancelled("removeImageAttachmentDirect", e)
            }
        }
    }

    /**
     * Маркер времени очистки UI (legacy). Сейчас при «Очистить историю» вызывается
     * [purgeAllChannelMediaForHistoryClear]; ленту больше не срезают по этому файлу.
     */
    fun markChannelUiClearedNow(context: Context, deviceMacNorm: String, channelIndex: Int, nodeIdHex: String?) {
        try {
            val dir = channelDir(context, deviceMacNorm, channelIndex, nodeIdHex)
            dir.mkdirs()
            File(dir, UI_CLEARED_AT_FILE).writeText(System.currentTimeMillis().toString())
        } catch (e: Exception) {
            logEUnlessCancelled("markChannelUiClearedNow", e)
        }
    }

    fun readChannelUiClearAtMs(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        nodeIdHex: String?,
    ): Long? {
        return try {
            val f = File(channelDir(context, deviceMacNorm, channelIndex, nodeIdHex), UI_CLEARED_AT_FILE)
            if (!f.isFile) return null
            f.readText().trim().toLongOrNull()
        } catch (e: Exception) {
            logEUnlessCancelled("readChannelUiClearAtMs", e)
            null
        }
    }

    /** Маркер очистки UI может лежать в папке архива (node id) или в legacy — читаем оба варианта. */
    fun readChannelUiClearAtMsMerged(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        nodeIdHex: String?,
    ): Long? {
        val id = nodeIdHex?.trim()?.takeIf { it.isNotEmpty() }
        if (id != null) {
            readChannelUiClearAtMs(context, deviceMacNorm, channelIndex, id)?.let { return it }
        }
        return readChannelUiClearAtMs(context, deviceMacNorm, channelIndex, null)
    }

    /**
     * Дописывает в jsonl только те текстовые сообщения, которых ещё нет (по id), чтобы перед очисткой БД
     * не потерять историю в архиве.
     */
    fun syncMissingTextMessagesToArchive(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        rows: List<ChannelChatMessageEntity>,
        nodeIdHex: String?,
    ) {
        if (rows.isEmpty()) return
        synchronized(lock(nodeIdHex, deviceMacNorm, channelIndex)) {
            val dir = channelDir(context, deviceMacNorm, channelIndex, nodeIdHex)
            val textFile = File(dir, TEXT_LOG)
            val existingIds = HashSet<Long>()
            if (textFile.isFile) {
                try {
                    textFile.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            if (line.isBlank()) return@forEach
                            try {
                                val jo = JSONObject(line)
                                if (jo.optString("type", "") == "text") {
                                    existingIds.add(jo.getLong("id"))
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (e: Exception) {
                    logEUnlessCancelled("syncMissingTextMessagesToArchive read", e)
                }
            }
            for (row in rows) {
                if (row.id !in existingIds) {
                    appendTextMessage(context, deviceMacNorm, channelIndex, row, nodeIdHex)
                    existingIds.add(row.id)
                }
            }
        }
    }

    fun saveVoiceAttachment(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        a: ChannelVoiceAttachment,
        nodeIdHex: String? = null,
    ) {
        synchronized(lock(nodeIdHex, deviceMacNorm, channelIndex)) {
            try {
                val dir = channelDir(context, deviceMacNorm, channelIndex, nodeIdHex)
                val vdir = File(dir, "voices").also { it.mkdirs() }
                val fname = safeFileName(a.stableId) + ".voc"
                val rel = "voices/$fname"
                File(vdir, fname).writeBytes(a.codecPayload)
                val jo = JSONObject()
                jo.put("type", "voice")
                jo.put("stableId", a.stableId)
                a.from?.let { jo.put("from", it.toLong() and 0xFFFF_FFFFL) }
                jo.put("mine", a.mine)
                jo.put("timeMs", a.timeMs)
                jo.put("durationMs", a.durationMs)
                jo.put("delivery", a.deliveryStatus)
                a.meshPacketId?.let { jo.put("meshPacketId", it.toLong() and 0xFFFF_FFFFL) }
                if (a.voiceRecordId != 0u) {
                    jo.put("voiceRecordId", a.voiceRecordId.toLong() and 0xFFFF_FFFFL)
                }
                if (a.reactionsJson.isNotBlank() && a.reactionsJson != ChatMessageReactionsJson.EMPTY_ARRAY) {
                    jo.put("reactionsJson", a.reactionsJson)
                }
                jo.put("file", rel)
                appendLine(File(dir, VOICE_INDEX), jo.toString())
            } catch (e: Exception) {
                logEUnlessCancelled("saveVoiceAttachment", e)
            }
        }
    }

    fun saveImageAttachment(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        a: ChannelImageAttachment,
        nodeIdHex: String? = null,
    ) {
        synchronized(lock(nodeIdHex, deviceMacNorm, channelIndex)) {
            try {
                val dir = channelDir(context, deviceMacNorm, channelIndex, nodeIdHex)
                val idir = File(dir, "images").also { it.mkdirs() }
                val fname = safeFileName(a.stableId) + ".jpg"
                val rel = "images/$fname"
                File(idir, fname).writeBytes(a.jpeg)
                val jo = JSONObject()
                jo.put("type", "image")
                jo.put("stableId", a.stableId)
                a.from?.let { jo.put("from", it.toLong() and 0xFFFF_FFFFL) }
                jo.put("mine", a.mine)
                jo.put("timeMs", a.timeMs)
                jo.put("file", rel)
                appendLine(File(dir, IMAGE_INDEX), jo.toString())
            } catch (e: Exception) {
                logEUnlessCancelled("saveImageAttachment", e)
            }
        }
    }

    fun loadVoiceAttachments(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        nodeIdHex: String? = null,
    ): List<ChannelVoiceAttachment> {
        synchronized(lock(nodeIdHex, deviceMacNorm, channelIndex)) {
            val primary = loadVoicesFromChannelDir(channelDir(context, deviceMacNorm, channelIndex, nodeIdHex))
            if (primary.isNotEmpty() || nodeIdHex.isNullOrBlank()) return primary
            return loadVoicesFromChannelDir(channelDir(context, deviceMacNorm, channelIndex, null))
        }
    }

    fun loadImageAttachments(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        nodeIdHex: String? = null,
    ): List<ChannelImageAttachment> {
        synchronized(lock(nodeIdHex, deviceMacNorm, channelIndex)) {
            val primary = loadImagesFromChannelDir(channelDir(context, deviceMacNorm, channelIndex, nodeIdHex))
            if (primary.isNotEmpty() || nodeIdHex.isNullOrBlank()) return primary
            return loadImagesFromChannelDir(channelDir(context, deviceMacNorm, channelIndex, null))
        }
    }

    /**
     * Удаляет голос из индекса и файла на диске (после «удалить из чата»), чтобы при следующем входе
     * в канал сообщение не подтягивалось снова из [loadVoiceAttachments].
     */
    fun removeVoiceAttachment(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        stableId: String,
        nodeIdHex: String?,
    ) {
        val ctx = context.applicationContext
        val id = nodeIdHex?.trim()?.takeIf { it.isNotEmpty() }
        if (id != null) {
            synchronized(lock(id, deviceMacNorm, channelIndex)) {
                removeVoiceEntriesFromDir(channelDir(ctx, deviceMacNorm, channelIndex, id), stableId)
            }
        }
        synchronized(lock(null, deviceMacNorm, channelIndex)) {
            removeVoiceEntriesFromDir(channelDir(ctx, deviceMacNorm, channelIndex, null), stableId)
        }
    }

    /**
     * То же для фото: иначе после выхода и входа вложение снова появится из [loadImageAttachments].
     */
    fun removeImageAttachment(
        context: Context,
        deviceMacNorm: String,
        channelIndex: Int,
        stableId: String,
        nodeIdHex: String?,
    ) {
        val ctx = context.applicationContext
        val id = nodeIdHex?.trim()?.takeIf { it.isNotEmpty() }
        if (id != null) {
            synchronized(lock(id, deviceMacNorm, channelIndex)) {
                removeImageEntriesFromDir(channelDir(ctx, deviceMacNorm, channelIndex, id), stableId)
            }
        }
        synchronized(lock(null, deviceMacNorm, channelIndex)) {
            removeImageEntriesFromDir(channelDir(ctx, deviceMacNorm, channelIndex, null), stableId)
        }
    }

    private fun removeVoiceEntriesFromDir(dir: File, stableId: String) {
        val idx = File(dir, VOICE_INDEX)
        if (!idx.isFile) return
        rewriteIndexRemovingStableId(idx, dir, stableId, type = "voice") { jo, baseDir ->
            val rel = jo.optString("file", "")
            if (rel.isNotEmpty()) File(baseDir, rel).delete()
        }
    }

    private fun removeImageEntriesFromDir(dir: File, stableId: String) {
        val idx = File(dir, IMAGE_INDEX)
        if (!idx.isFile) return
        rewriteIndexRemovingStableId(idx, dir, stableId, type = "image") { jo, baseDir ->
            val rel = jo.optString("file", "")
            if (rel.isNotEmpty()) File(baseDir, rel).delete()
        }
    }

    private fun rewriteIndexRemovingStableId(
        idx: File,
        baseDir: File,
        stableId: String,
        type: String,
        deletePayload: (JSONObject, File) -> Unit,
    ) {
        val lines = try {
            idx.readLines(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logEUnlessCancelled("read index ${idx.name}", e)
            return
        }
        var changed = false
        val kept = ArrayList<String>()
        for (line in lines) {
            if (line.isBlank()) continue
            try {
                val jo = JSONObject(line)
                if (jo.optString("type", "") != type) {
                    kept.add(line)
                    continue
                }
                if (jo.getString("stableId") != stableId) {
                    kept.add(line)
                    continue
                }
                changed = true
                deletePayload(jo, baseDir)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                kept.add(line)
            }
        }
        if (!changed) return
        try {
            if (kept.isEmpty()) {
                idx.delete()
            } else {
                idx.writeText(kept.joinToString("\n") + "\n", StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            logEUnlessCancelled("write index ${idx.name}", e)
        }
    }

    private fun loadVoicesFromChannelDir(dir: File): List<ChannelVoiceAttachment> {
        val idx = File(dir, VOICE_INDEX)
        if (!idx.isFile) return emptyList()
        val out = ArrayList<ChannelVoiceAttachment>()
        idx.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val jo = JSONObject(line)
                    if (jo.optString("type", "") != "voice") return@forEach
                    val rel = jo.optString("file", "")
                    if (rel.isEmpty()) return@forEach
                    val f = File(dir, rel)
                    if (!f.isFile) return@forEach
                    val fromU = if (jo.isNull("from")) null else (jo.getLong("from") and 0xFFFF_FFFFL).toUInt()
                    val meshPid = if (jo.isNull("meshPacketId")) {
                        null
                    } else {
                        (jo.getLong("meshPacketId") and 0xFFFF_FFFFL).toUInt()
                    }
                    val voiceRec = if (jo.isNull("voiceRecordId")) {
                        0u
                    } else {
                        (jo.getLong("voiceRecordId") and 0xFFFF_FFFFL).toUInt()
                    }
                    out.add(
                        ChannelVoiceAttachment(
                            stableId = jo.getString("stableId"),
                            from = fromU,
                            codecPayload = f.readBytes(),
                            mine = jo.optBoolean("mine", false),
                            timeMs = jo.getLong("timeMs"),
                            durationMs = jo.getLong("durationMs"),
                            deliveryStatus = jo.optInt("delivery", ChatMessageDeliveryStatus.SENT_TO_NODE.code),
                            meshPacketId = meshPid,
                            reactionsJson = jo.optString("reactionsJson", ChatMessageReactionsJson.EMPTY_ARRAY)
                                .ifEmpty { ChatMessageReactionsJson.EMPTY_ARRAY },
                            voiceRecordId = voiceRec,
                        ),
                    )
                } catch (e: Exception) {
                    logWUnlessCancelled("loadVoice line: $line", e)
                }
            }
        }
        // Несколько строк с одним stableId (повторные save) — берём последнюю по файлу.
        return out.groupBy { it.stableId }.values.map { it.last() }.sortedBy { it.timeMs }
    }

    private fun loadImagesFromChannelDir(dir: File): List<ChannelImageAttachment> {
        val idx = File(dir, IMAGE_INDEX)
        if (!idx.isFile) return emptyList()
        val out = ArrayList<ChannelImageAttachment>()
        idx.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                try {
                    val jo = JSONObject(line)
                    if (jo.optString("type", "") != "image") return@forEach
                    val rel = jo.optString("file", "")
                    if (rel.isEmpty()) return@forEach
                    val f = File(dir, rel)
                    if (!f.isFile) return@forEach
                    val fromU = if (jo.isNull("from")) null else (jo.getLong("from") and 0xFFFF_FFFFL).toUInt()
                    out.add(
                        ChannelImageAttachment(
                            stableId = jo.getString("stableId"),
                            from = fromU,
                            jpeg = f.readBytes(),
                            mine = jo.optBoolean("mine", false),
                            timeMs = jo.getLong("timeMs"),
                        ),
                    )
                } catch (e: Exception) {
                    logWUnlessCancelled("loadImage line: $line", e)
                }
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun appendLine(file: File, line: String) {
        FileOutputStream(file, true).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { w ->
                w.append(line)
                w.append('\n')
            }
        }
    }

    private fun safeFileName(s: String): String =
        s.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(96).ifBlank { "item" }
}
