package com.example.aura.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.aura.R
import com.example.aura.chat.IncomingMessagePreviewFormatter
import com.example.aura.data.local.DmThreadSummaryRow
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.preferences.ChatChannelNotificationPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Секция «Личные сообщения» под папкой «Группы», над списком каналов.
 * У каждой карточки диалога — «не беспокоить» только для этого узла; превью, время, badge непрочитанных.
 * Данные из Room (observeDmThreadSummaries), на вкладке списка к ним подмешивается последнее голос/фото из файлов.
 */
@Composable
fun DirectMessagesSection(
    threads: List<DmThreadSummaryRow>,
    nodes: List<MeshWireNodeSummary>,
    /** Адрес BLE-ноды — для «не беспокоить» по личным (как у каналов). */
    deviceAddress: String?,
    onThreadClick: (peerNodeNum: Long) -> Unit,
    /** Долгое нажатие на строку диалога — вибрация и запрос очистки истории (как у канала). */
    onThreadLongPress: (peerNodeNum: Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (threads.isEmpty()) return
    val timeFmt = rememberThreadTimeFormat()
    val beaconPreviewLabel = stringResource(R.string.channel_preview_beacon)
    val pollVotePreviewLabel = stringResource(R.string.preview_poll_vote_recorded)
    val checklistTogglePreviewLabel = stringResource(R.string.preview_checklist_choice_made)
    val muteAddr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Личные сообщения",
            color = Color(0xFF8CB0BF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        for (row in threads) {
            DmThreadRow(
                peerNodeNum = row.dmPeerNodeNum,
                deviceAddressForMute = muteAddr,
                title = displayTitleForDmPeer(row.dmPeerNodeNum, nodes),
                preview = IncomingMessagePreviewFormatter.previewLabel(
                    row.lastPreview,
                    beaconPreviewLabel,
                    pollVotePreviewLabel,
                    checklistTogglePreviewLabel,
                )
                    .ifBlank { "—" },
                timeLabel = timeFmt.format(Date(row.lastMsgAt)),
                unreadCount = row.unreadCount,
                onClick = { onThreadClick(row.dmPeerNodeNum) },
                onLongPress = { onThreadLongPress(row.dmPeerNodeNum) },
            )
        }
    }
}

@Composable
private fun rememberThreadTimeFormat(): SimpleDateFormat {
    return remember {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    }
}

@Composable
private fun DmThreadRow(
    peerNodeNum: Long,
    deviceAddressForMute: String?,
    title: String,
    preview: String,
    timeLabel: String,
    unreadCount: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val peerKey = peerNodeNum and 0xFFFF_FFFFL
    var threadMuted by remember(deviceAddressForMute, peerKey) {
        mutableStateOf(
            ChatChannelNotificationPreferences.isDirectThreadMuted(
                context,
                deviceAddressForMute,
                peerKey,
            ),
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.ContextClick) }
                    onLongPress()
                },
            ),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0A2036).copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color(0xFF42E6FF).copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF112033)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = Color(0xFF6DEBFF),
                        modifier = Modifier.size(26.dp),
                    )
                }
                if (unreadCount > 0) {
                    val label = if (unreadCount > 99) "99+" else unreadCount.toString()
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        color = Color(0xFFE7FCFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (deviceAddressForMute != null) {
                        IconButton(
                            onClick = {
                                val next = !threadMuted
                                threadMuted = next
                                ChatChannelNotificationPreferences.setDirectThreadMuted(
                                    context,
                                    deviceAddressForMute,
                                    peerKey,
                                    next,
                                )
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = if (threadMuted) {
                                    Icons.Filled.NotificationsOff
                                } else {
                                    Icons.Filled.Notifications
                                },
                                contentDescription = if (threadMuted) {
                                    "Включить уведомления для этого диалога"
                                } else {
                                    "Отключить уведомления для этого диалога"
                                },
                                tint = if (threadMuted) {
                                    Color(0xFFFF8A80)
                                } else {
                                    Color(0xFF7DA8C6)
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Text(
                    text = preview,
                    color = Color(0xFF8CB0BF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                )
                Text(
                    text = timeLabel,
                    color = Color(0xFF6B8A9A),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
