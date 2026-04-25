package com.example.aura.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.aura.bluetooth.MeshChannelMessaging

/**
 * Быстрый ответ из уведомления о сообщении ([MeshNotificationDispatcher]).
 */
class MessageQuickReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        try {
            if (intent == null) return
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val text = remoteInput?.getCharSequence(MeshNotificationDispatcher.KEY_TEXT_REPLY)?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return
            val deviceMac = intent.getStringExtra(MeshNotificationDispatcher.EXTRA_REPLY_DEVICE_MAC) ?: return
            val channelIndex = intent.getIntExtra(MeshNotificationDispatcher.EXTRA_REPLY_CHANNEL_INDEX, 0)
            val isDm = intent.getBooleanExtra(MeshNotificationDispatcher.EXTRA_REPLY_IS_DM, false)
            val fromNodeLong = intent.getLongExtra(MeshNotificationDispatcher.EXTRA_REPLY_FROM_NODE_NUM, -1L)
            if (isDm && fromNodeLong >= 0) {
                MeshChannelMessaging.sendDirectTextToNode(
                    context.applicationContext,
                    deviceMac,
                    channelIndex,
                    fromNodeLong.toUInt(),
                    text,
                ) { _, _ -> }
            } else {
                MeshChannelMessaging.sendChannelText(
                    context.applicationContext,
                    deviceMac,
                    channelIndex,
                    text,
                ) { _, _ -> }
            }
        } finally {
            pending.finish()
        }
    }
}
