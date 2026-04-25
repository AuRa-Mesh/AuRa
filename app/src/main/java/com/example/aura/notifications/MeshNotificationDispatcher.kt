package com.example.aura.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.aura.MainActivity
import com.example.aura.R
import com.example.aura.bluetooth.MeshChannelMessaging
import com.example.aura.app.AppForegroundState
import com.example.aura.bluetooth.MeshImageChunkCodec
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.ParsedMeshDataPayload
import com.example.aura.mesh.incoming.resolveInboundDmPeerUInt
import com.example.aura.preferences.ChatChannelNotificationPreferences
import com.example.aura.chat.IncomingMessagePreviewFormatter
import java.util.ArrayDeque

/**
 * Показ системных уведомлений по кадрам FromRadio (TEXT_MESSAGE_APP, телеметрия батареи).
 */
object MeshNotificationDispatcher {

    enum class TransportLabel {
        NONE,
        WIFI,
        USB,
    }

    const val EXTRA_FROM_NODE_NUM = "mesh_notif_from_node_num"
    /** Открыть чат на канале (0 = primary). */
    const val EXTRA_OPEN_CHANNEL_INDEX = "mesh_notif_open_channel_index"

    const val KEY_TEXT_REPLY = "mesh_reply_text"

    const val EXTRA_REPLY_DEVICE_MAC = "mesh_reply_device_mac"
    const val EXTRA_REPLY_CHANNEL_INDEX = "mesh_reply_channel_index"
    const val EXTRA_REPLY_IS_DM = "mesh_reply_is_dm"
    const val EXTRA_REPLY_FROM_NODE_NUM = "mesh_reply_from_node_num"

    private const val CHANNEL_MESSAGES = "mesh_incoming_messages"
    private const val CHANNEL_ALERTS = "mesh_critical_alerts"
    private val defaultMessageVibrationPattern = longArrayOf(0L, 220L, 100L, 220L)
    private const val NOTIF_ID_MESSAGE_BASE = 71000
    private const val NOTIF_ID_BATTERY = 71001
    private const val DEDUP_MAX = 400

    private val recentDedup = ArrayDeque<String>(DEDUP_MAX)
    private val activeMessageNotifIds = ArrayDeque<Int>(DEDUP_MAX)

    private fun notificationPreviewText(context: Context, raw: String): String =
        IncomingMessagePreviewFormatter.previewLabel(
            raw,
            context.getString(R.string.channel_preview_beacon),
            context.getString(R.string.preview_poll_vote_recorded),
            context.getString(R.string.preview_checklist_choice_made),
        )

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chMsg = android.app.NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.notif_channel_messages_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_messages_desc)
            enableVibration(true)
            vibrationPattern = defaultMessageVibrationPattern
        }
        val chAlert = android.app.NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.notif_channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_alerts_desc)
            enableVibration(true)
            vibrationPattern = defaultMessageVibrationPattern
        }
        nm.createNotificationChannel(chMsg)
        nm.createNotificationChannel(chAlert)
    }

    fun clearMessageNotifications(context: Context) {
        with(NotificationManagerCompat.from(context.applicationContext)) {
            synchronized(activeMessageNotifIds) {
                while (activeMessageNotifIds.isNotEmpty()) {
                    cancel(activeMessageNotifIds.removeFirst())
                }
            }
        }
    }

    fun dispatchFromRadioFrame(
        context: Context,
        deviceAddress: String,
        frame: ByteArray,
        localNodeNum: UInt?,
        transportLabel: TransportLabel = TransportLabel.NONE,
        onlyNotifyWhenBackground: Boolean = false,
    ) {
        val payloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(frame)
        for (p in payloads) {
            when (p.portnum) {
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_APP,
                MeshWireFromRadioMeshPacketParser.PORTNUM_TEXT_MESSAGE_COMPRESSED_APP,
                -> maybeNotifyText(
                    context,
                    deviceAddress,
                    p,
                    localNodeNum,
                    transportLabel,
                    onlyNotifyWhenBackground,
                )
                MeshWireFromRadioMeshPacketParser.PORTNUM_TELEMETRY ->
                    maybeNotifyBattery(
                        context,
                        p,
                        localNodeNum,
                        onlyNotifyWhenBackground,
                    )
            }
        }
    }

    private fun maybeNotifyBattery(
        context: Context,
        p: ParsedMeshDataPayload,
        localNodeNum: UInt?,
        onlyNotifyWhenBackground: Boolean = false,
    ) {
        if (onlyNotifyWhenBackground && AppForegroundState.isForeground) return
        if (!MeshNotificationPreferences.isMasterEnabled(context)) return
        val from = p.from ?: return
        if (localNodeNum == null || from != localNodeNum) return
        val bat = MeshWireFromRadioMeshPacketParser.parseTelemetryBatteryPercent(p.payload) ?: return
        if (bat > 10) return
        val now = System.currentTimeMillis()
        if (now - MeshNotificationPreferences.lastBatteryWarnEpochMs(context) < 30 * 60_000L) return
        MeshNotificationPreferences.setLastBatteryWarnEpochMs(context, now)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIF_ID_BATTERY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nb = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_photo)
            .setContentTitle(context.getString(R.string.notif_battery_low_title))
            .setContentText(context.getString(R.string.notif_battery_low_text, bat))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setVibrate(defaultMessageVibrationPattern)
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(NOTIF_ID_BATTERY, nb.build())
        }
    }

    private fun maybeNotifyText(
        context: Context,
        deviceAddress: String,
        p: ParsedMeshDataPayload,
        localNodeNum: UInt?,
        transportLabel: TransportLabel,
        onlyNotifyWhenBackground: Boolean,
    ) {
        if (onlyNotifyWhenBackground && AppForegroundState.isForeground) return
        if (!MeshNotificationPreferences.isMasterEnabled(context)) return
        if (MeshWireFromRadioMeshPacketParser.isTapbackPayload(p)) return
        val raw = MeshWireFromRadioMeshPacketParser.payloadAsUtf8Text(p.portnum, p.payload)
            ?: return
        if (raw.startsWith(MeshImageChunkCodec.PREFIX)) return

        val isChannel = MeshChannelMessaging.isLikelyChannelMeshTraffic(p)
        val isDm = !isChannel && localNodeNum != null && (p.to == localNodeNum)

        val fromWire = p.logicalFrom() ?: return
        if (localNodeNum != null && fromWire == localNodeNum) return
        val from =
            if (isDm) {
                val loc = localNodeNum ?: return
                resolveInboundDmPeerUInt(
                    MeshNodeSyncMemoryStore.normalizeKey(deviceAddress),
                    context.applicationContext,
                    loc,
                    p,
                ).takeIf { it != 0u } ?: fromWire
            } else {
                fromWire
            }
        if (localNodeNum != null && from == localNodeNum) return

        val channelIdx = (p.channel ?: 0u).toInt()
        if (isChannel && ChatChannelNotificationPreferences.isChannelMuted(context, deviceAddress, channelIdx)) {
            return
        }
        if (isDm) {
            val peer = from.toLong() and 0xFFFF_FFFFL
            if (ChatChannelNotificationPreferences.isDirectThreadMuted(context, deviceAddress, peer)) {
                return
            }
        }

        val wantDm = MeshNotificationPreferences.filterPrivateMessages(context)
        val wantCh = MeshNotificationPreferences.filterChannelMessages(context)
        val allow = when {
            isDm -> wantDm
            isChannel -> wantCh
            else -> false
        }
        if (!allow) return

        val dedup = p.packetId?.let { "pkt_${it}_${from}" }
            ?: "h_${from}_${raw.hashCode()}"
        synchronized(recentDedup) {
            if (recentDedup.contains(dedup)) return
            if (recentDedup.size >= DEDUP_MAX) recentDedup.removeFirst()
            recentDedup.addLast(dedup)
        }

        val addr = deviceAddress.trim()
        val nodes = MeshNodeListDiskCache.load(context.applicationContext, addr)
        val senderName = nodes?.firstOrNull { (it.nodeNum and 0xFFFF_FFFFL) == (from.toLong() and 0xFFFF_FFFFL) }
            ?.displayLongName()
            ?: MeshWireNodeNum.formatHex(from)

        val showPreview = MeshNotificationPreferences.showPreview(context)
        val smart = MeshNotificationPreferences.smartAlert(context)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_NODE_NUM, from.toLong() and 0xFFFF_FFFFL)
            putExtra(EXTRA_OPEN_CHANNEL_INDEX, channelIdx)
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIF_ID_MESSAGE_BASE + (from.toInt() and 0xFFF),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (showPreview) {
            notificationPreviewText(context, raw)
        } else {
            context.getString(R.string.notif_message_hidden_body)
        }

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.notif_quick_reply_label))
            .build()
        val replyIntent = Intent(context, MessageQuickReplyReceiver::class.java).apply {
            putExtra(EXTRA_REPLY_DEVICE_MAC, addr)
            putExtra(EXTRA_REPLY_CHANNEL_INDEX, channelIdx)
            putExtra(EXTRA_REPLY_IS_DM, isDm)
            putExtra(EXTRA_REPLY_FROM_NODE_NUM, from.toLong() and 0xFFFF_FFFFL)
        }
        val replyReq = (NOTIF_ID_MESSAGE_BASE + (p.packetId?.toInt() ?: raw.hashCode())) xor 0x10000
        val replyPi = PendingIntent.getBroadcast(
            context,
            replyReq and 0x7FFF_FFFF.toInt(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            context.getString(R.string.notif_quick_reply_label),
            replyPi,
        ).addRemoteInput(remoteInput).build()

        val prefix = when (transportLabel) {
            TransportLabel.WIFI -> "(Wi-Fi) "
            TransportLabel.USB -> "(USB) "
            TransportLabel.NONE -> ""
        }
        val title = "$prefix$senderName"

        val nb = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_photo)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(replyAction)
        nb.setVibrate(defaultMessageVibrationPattern)
        if (smart) {
            nb.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            nb.setSound(null)
        }

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val notifId = (NOTIF_ID_MESSAGE_BASE + (p.packetId?.toInt() ?: raw.hashCode())) and 0x7FFF
            notify(notifId, nb.build())
            synchronized(activeMessageNotifIds) {
                if (activeMessageNotifIds.size >= DEDUP_MAX) activeMessageNotifIds.removeFirst()
                activeMessageNotifIds.addLast(notifId)
            }
        }
    }

    /**
     * Стандартное уведомление о сообщении (для внешних вызовов / тестов).
     * [contentIntent] — переход при тапе; по умолчанию открывается [MainActivity].
     */
    fun showNotification(
        context: Context,
        sender: String,
        messageText: String,
        contentIntent: PendingIntent? = null,
    ) {
        ensureChannels(context)
        if (!MeshNotificationPreferences.isMasterEnabled(context)) return
        if (AppForegroundState.isForeground) return
        val pi = contentIntent ?: PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val id = NOTIF_ID_MESSAGE_BASE + (messageText.hashCode() and 0x7FFF)
        val nb = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_photo)
            .setContentTitle(sender)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(defaultMessageVibrationPattern)
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(id, nb.build())
            synchronized(activeMessageNotifIds) {
                if (activeMessageNotifIds.size >= DEDUP_MAX) activeMessageNotifIds.removeFirst()
                activeMessageNotifIds.addLast(id)
            }
        }
    }
}
