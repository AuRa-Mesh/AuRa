package com.example.aura.history

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.chat.ChannelVoiceAttachment
import com.example.aura.data.local.ChannelChatMessageEntity
import com.example.aura.data.local.MessageHistoryDao
import com.example.aura.data.local.MessageHistoryEntryEntity
import com.example.aura.data.local.MessageHistoryGroupEntity
import com.example.aura.data.local.MessageHistoryType
import com.example.aura.meshwire.MeshWireModemPreset
import com.example.aura.meshwire.meshChannelDisplayTitle
import com.example.aura.voice.Codec2Bridge
import com.example.aura.voice.Pcm16MonoWavWriter
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Унифицированная история: Room + файлы голоса во [Context.getFilesDir]/message_history_audio/.
 */
class MessageHistoryRepository(
    private val appContext: Context,
    private val dao: MessageHistoryDao,
) {

    private val audioDir: File
        get() = File(appContext.filesDir, "message_history_audio").also { it.mkdirs() }

    fun observeGroups(): Flow<List<MessageHistoryGroupEntity>> = dao.observeGroups()

    fun observeMessages(groupId: String): Flow<List<MessageHistoryEntryEntity>> =
        dao.observeMessagesForGroup(groupId)

    companion object {
        fun groupId(deviceMacNorm: String, channelIndex: Int): String =
            "${deviceMacNorm}_ch$channelIndex"

        /** Подпись канала для UI: кэш в группе, иначе снимок из [MeshNodeSyncMemoryStore], иначе индекс. */
        fun displayTitleForGroup(g: MessageHistoryGroupEntity): String =
            resolveChannelDisplayName(g.deviceMac, g.channelIndex, g.title)
                ?: fallbackChannelDisplayName(g.deviceMac, g.channelIndex)
    }

    private suspend fun touchGroupKeepMessages(group: MessageHistoryGroupEntity) {
        val n = dao.updateGroupTimestampAndTitle(
            group.groupId,
            group.lastMessageAtMs,
            group.title,
        )
        if (n == 0) {
            dao.insertGroupIgnore(group)
            dao.updateGroupTimestampAndTitle(
                group.groupId,
                group.lastMessageAtMs,
                group.title,
            )
        }
    }

    suspend fun upsertGroupTitle(
        deviceMacNorm: String,
        channelIndex: Int,
        title: String?,
        lastAt: Long,
    ) = withContext(Dispatchers.IO) {
        val gid = groupId(deviceMacNorm, channelIndex)
        touchGroupKeepMessages(
            MessageHistoryGroupEntity(
                groupId = gid,
                deviceMac = deviceMacNorm,
                channelIndex = channelIndex,
                lastMessageAtMs = lastAt,
                title = title?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    suspend fun recordTextMessage(
        deviceMacNorm: String,
        channelIndex: Int,
        row: ChannelChatMessageEntity,
        channelDisplayName: String? = null,
    ) = withContext(Dispatchers.IO) {
        val gid = groupId(deviceMacNorm, channelIndex)
        val t = row.createdAtMs
        val title = resolveChannelDisplayName(deviceMacNorm, channelIndex, channelDisplayName)
        touchGroupKeepMessages(
            MessageHistoryGroupEntity(
                groupId = gid,
                deviceMac = deviceMacNorm,
                channelIndex = channelIndex,
                lastMessageAtMs = t,
                title = title,
            ),
        )
        dao.insertMessage(
            MessageHistoryEntryEntity(
                groupId = gid,
                type = MessageHistoryType.TEXT.code,
                createdAtEpochMs = t,
                textBody = row.text,
                isOutgoing = row.isOutgoing,
                fromNodeNum = row.fromNodeNum,
                dedupKey = row.dedupKey,
                deliveryStatus = row.deliveryStatus,
            ),
        )
    }

    suspend fun recordVoiceMessage(
        deviceMacNorm: String,
        channelIndex: Int,
        attach: ChannelVoiceAttachment,
        channelDisplayName: String? = null,
    ) = withContext(Dispatchers.IO) {
        val gid = groupId(deviceMacNorm, channelIndex)
        val t = attach.timeMs
        val name = "v_${attach.stableId}_${t}.wav"
        val rel = "message_history_audio/$name"
        val outFile = File(appContext.filesDir, rel)
        val pcm = Codec2Bridge.decodeToPcm8kMono(attach.codecPayload)
        if (pcm.isEmpty()) return@withContext
        Pcm16MonoWavWriter.write(outFile, pcm, sampleRateHz = 8000)
        val title = resolveChannelDisplayName(deviceMacNorm, channelIndex, channelDisplayName)
        touchGroupKeepMessages(
            MessageHistoryGroupEntity(
                groupId = gid,
                deviceMac = deviceMacNorm,
                channelIndex = channelIndex,
                lastMessageAtMs = t,
                title = title,
            ),
        )
        dao.insertMessage(
            MessageHistoryEntryEntity(
                groupId = gid,
                type = MessageHistoryType.VOICE.code,
                createdAtEpochMs = t,
                isOutgoing = attach.mine,
                fromNodeNum = attach.from?.toLong() ?: 0L,
                dedupKey = attach.stableId,
                voiceFileRelativePath = rel,
                voiceDurationMs = attach.durationMs,
                deliveryStatus = attach.deliveryStatus,
            ),
        )
    }

    suspend fun recordCoordinates(
        deviceMacNorm: String,
        channelIndex: Int,
        lat: Double,
        lon: Double,
        atMs: Long,
        isOutgoing: Boolean,
        fromNodeNum: Long,
    ) = withContext(Dispatchers.IO) {
        val gid = groupId(deviceMacNorm, channelIndex)
        val title = resolveChannelDisplayName(deviceMacNorm, channelIndex, null)
        touchGroupKeepMessages(
            MessageHistoryGroupEntity(
                groupId = gid,
                deviceMac = deviceMacNorm,
                channelIndex = channelIndex,
                lastMessageAtMs = atMs,
                title = title,
            ),
        )
        dao.insertMessage(
            MessageHistoryEntryEntity(
                groupId = gid,
                type = MessageHistoryType.COORDINATES.code,
                createdAtEpochMs = atMs,
                latitude = lat,
                longitude = lon,
                isOutgoing = isOutgoing,
                fromNodeNum = fromNodeNum,
            ),
        )
    }

    suspend fun recordServiceMessage(
        deviceMacNorm: String,
        channelIndex: Int,
        kind: String,
        payloadJson: String?,
        atMs: Long,
    ) = withContext(Dispatchers.IO) {
        val gid = groupId(deviceMacNorm, channelIndex)
        val title = resolveChannelDisplayName(deviceMacNorm, channelIndex, null)
        touchGroupKeepMessages(
            MessageHistoryGroupEntity(
                groupId = gid,
                deviceMac = deviceMacNorm,
                channelIndex = channelIndex,
                lastMessageAtMs = atMs,
                title = title,
            ),
        )
        dao.insertMessage(
            MessageHistoryEntryEntity(
                groupId = gid,
                type = MessageHistoryType.SERVICE.code,
                createdAtEpochMs = atMs,
                serviceKind = kind,
                servicePayloadJson = payloadJson,
                isOutgoing = false,
                fromNodeNum = 0L,
            ),
        )
    }

    /** Экспорт в JSON в кэш и URI через FileProvider. */
    suspend fun exportHistory(): Uri = withContext(Dispatchers.IO) {
        val groups = dao.getGroupsSnapshot()
        val arr = JSONArray()
        for (g in groups) {
            val msgs = dao.getMessagesForGroup(g.groupId)
            val jo = JSONObject()
            jo.put("groupId", g.groupId)
            jo.put("deviceMac", g.deviceMac)
            jo.put("channelIndex", g.channelIndex)
            jo.put("title", g.title)
            val ja = JSONArray()
            for (m in msgs) {
                val jm = JSONObject()
                jm.put("id", m.id)
                jm.put("type", m.type)
                jm.put("createdAtEpochMs", m.createdAtEpochMs)
                jm.put("textBody", m.textBody)
                jm.put("latitude", m.latitude)
                jm.put("longitude", m.longitude)
                jm.put("serviceKind", m.serviceKind)
                jm.put("voiceFileRelativePath", m.voiceFileRelativePath)
                jm.put("isOutgoing", m.isOutgoing)
                ja.put(jm)
            }
            jo.put("messages", ja)
            arr.put(jo)
        }
        val exportDir = File(appContext.cacheDir, "message_history_export").also { it.mkdirs() }
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(
            Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime(),
        )
        val f = File(exportDir, "history_export_$stamp.json")
        f.writeText(arr.toString(2), StandardCharsets.UTF_8)
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            f,
        )
    }

    suspend fun clearHistory(groupId: String? = null) = withContext(Dispatchers.IO) {
        if (groupId == null) {
            val groups = dao.getGroupsSnapshot()
            for (g in groups) {
                deleteAudioFilesForGroup(g.groupId)
            }
            dao.clearAllHistory()
        } else {
            deleteAudioFilesForGroup(groupId)
            dao.deleteMessagesInGroup(groupId)
            dao.deleteGroup(groupId)
        }
    }

    private suspend fun deleteAudioFilesForGroup(groupId: String) {
        val msgs = dao.getMessagesForGroup(groupId)
        for (m in msgs) {
            m.voiceFileRelativePath?.let { rel ->
                File(appContext.filesDir, rel).delete()
            }
            m.imageFileRelativePath?.let { rel ->
                File(appContext.filesDir, rel).delete()
            }
        }
    }

    fun absoluteFileForRelative(relative: String): File = File(appContext.filesDir, relative)
}

private fun resolveChannelDisplayName(
    deviceMacNorm: String,
    channelIndex: Int,
    explicit: String?,
): String? {
    val fromLookup = lookupChannelName(deviceMacNorm, channelIndex)
    if (!fromLookup.isNullOrBlank()) return fromLookup
    val fromExplicit = explicit?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (fromExplicit.matches(Regex("(?i)ch\\.?\\s*\\d+"))) return null
    return fromExplicit
}

private fun lookupChannelName(deviceMacNorm: String, channelIndex: Int): String? =
    MeshNodeSyncMemoryStore.getChannels(deviceMacNorm)?.channels
        ?.firstOrNull { it.index == channelIndex }
        ?.let { ch ->
            val preset = MeshNodeSyncMemoryStore.getLora(deviceMacNorm)?.modemPreset
            meshChannelDisplayTitle(ch, preset).trim().takeIf { it.isNotEmpty() }
        }

private fun fallbackChannelDisplayName(deviceMacNorm: String, channelIndex: Int): String {
    val preset = MeshNodeSyncMemoryStore.getLora(deviceMacNorm)?.modemPreset
        ?: MeshWireModemPreset.LONG_FAST
    return preset.defaultChannelNameForEmpty().ifBlank { "Канал $channelIndex" }
}
