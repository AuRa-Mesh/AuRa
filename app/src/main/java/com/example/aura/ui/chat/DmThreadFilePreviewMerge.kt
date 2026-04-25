package com.example.aura.ui.chat

import android.content.Context
import com.example.aura.chat.history.ChatHistoryFileStore
import com.example.aura.data.local.DmThreadSummaryRow

/**
 * Список «Личные» из Room: [DmThreadSummaryRow] по последним строкам.
 * К голосу/фото в [ChatHistoryFileStore] подмешиваем (как в карточках каналов) и
 * **добавляем** треды, где есть только файлы (без текста в БД) — иначе исходящий голос
 * не отображается в списке после ухода на главную.
 */
fun mergeDmThreadSummariesWithFileLastMedia(
    context: Context,
    deviceMacNorm: String,
    nodeIdHex: String?,
    threads: List<DmThreadSummaryRow>,
    voiceLabel: String,
    imageLabel: String,
    /** Сразу после «Очистить» Room ещё может не успеть обновиться; исключаем пира из итогового списка. */
    forceExcludeDirectPeer: Long? = null,
): List<DmThreadSummaryRow> {
    val appContext = context.applicationContext
    val pEx = forceExcludeDirectPeer?.and(0xFFFF_FFFFL)
    val threads2 =
        if (pEx == null) threads
        else threads.filter { (it.dmPeerNodeNum and 0xFFFF_FFFFL) != pEx }
    val fromRoom: MutableMap<Long, DmThreadSummaryRow> = linkedMapOf()
    for (t in threads2) {
        val k = t.dmPeerNodeNum and 0xFFFF_FFFFL
        fromRoom[k] = t
    }
    val onDisk = ChatHistoryFileStore.listPrivateChatThreadPeersWithFileMedia(appContext, nodeIdHex)
    for (peer in onDisk) {
        if (pEx != null && (peer and 0xFFFF_FFFFL) == pEx) continue
        if (peer !in fromRoom) {
            val folder = ChatHistoryFileStore.directThreadFolderNameForPeer(
                appContext,
                deviceMacNorm,
                peer,
            )
            val voices = ChatHistoryFileStore.loadVoiceAttachmentsDirectWithFallback(
                appContext,
                nodeIdHex,
                folder,
            )
            val imgs = ChatHistoryFileStore.loadImageAttachmentsDirectWithFallback(
                appContext,
                nodeIdHex,
                folder,
            )
            if (voices.isEmpty() && imgs.isEmpty()) continue
            val vMax = voices.maxByOrNull { it.timeMs }?.let { it.timeMs to voiceLabel }
            val iMax = imgs.maxByOrNull { it.timeMs }?.let { it.timeMs to imageLabel }
            val (at, preview) = when {
                vMax == null -> iMax
                iMax == null -> vMax
                else -> if (vMax.first >= iMax.first) vMax else iMax
            } ?: continue
            fromRoom[peer] = DmThreadSummaryRow(
                dmPeerNodeNum = peer,
                lastMsgAt = at,
                lastPreview = preview,
                unreadCount = 0,
            )
        }
    }
    val updated = fromRoom.values.map { row ->
        val peer = row.dmPeerNodeNum and 0xFFFF_FFFFL
        val folder = ChatHistoryFileStore.directThreadFolderNameForPeer(
            appContext,
            deviceMacNorm,
            peer,
        )
        val voices = ChatHistoryFileStore.loadVoiceAttachmentsDirectWithFallback(
            appContext,
            nodeIdHex,
            folder,
        )
        val imgs = ChatHistoryFileStore.loadImageAttachmentsDirectWithFallback(
            appContext,
            nodeIdHex,
            folder,
        )
        val vMax = voices.maxByOrNull { it.timeMs }?.let { it.timeMs to voiceLabel }
        val iMax = imgs.maxByOrNull { it.timeMs }?.let { it.timeMs to imageLabel }
        val fileWin = when {
            vMax == null -> iMax
            iMax == null -> vMax
            else -> if (vMax.first >= iMax.first) vMax else iMax
        } ?: return@map row
        if (fileWin.first > row.lastMsgAt) {
            row.copy(
                lastMsgAt = fileWin.first,
                lastPreview = fileWin.second,
            )
        } else {
            row
        }
    }
    return updated.sortedByDescending { it.lastMsgAt }
}
