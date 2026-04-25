package com.example.aura.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.bluetooth.NodeConnectionState
import com.example.aura.bluetooth.NodeSyncStep

private val CARD = Color(0xFF0D2535)
private val ACCENT = Color(0xFF00D4FF)
private val TEXT_SEC = Color(0xFF8A9AAA)
private val TEXT_DIM = Color(0xFF3A4A5A)
private val GREEN = Color(0xFF4CAF50)

private val SYNC_STEPS_ORDERED = listOf(
    NodeSyncStep.CONNECTING,
    NodeSyncStep.DISCOVERING,
    NodeSyncStep.MTU,
    NodeSyncStep.SUBSCRIBING,
    NodeSyncStep.WANT_CONFIG,
    NodeSyncStep.SYNCING,
    NodeSyncStep.RECEIVING,
    NodeSyncStep.READY,
)

/**
 * Блок «Синхронизация» + шкала этапов — как в [MeshBluetoothScanDialog].
 */
@Composable
fun GattConnectionSyncProgressSection(
    syncStep: NodeSyncStep,
    connState: NodeConnectionState,
    modifier: Modifier = Modifier,
) {
    val statusLabel = syncStep.label
    val statusIsReady = syncStep == NodeSyncStep.READY
    val statusIsError = syncStep == NodeSyncStep.DISCONNECTED || syncStep == NodeSyncStep.IDLE
    val statusIsProgress = !statusIsReady && !statusIsError && statusLabel.isNotBlank()
    val labelShown = when {
        statusLabel.isNotBlank() -> statusLabel
        statusIsReady -> statusLabel
        else -> "Подключение…"
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Синхронизация",
            color = TEXT_SEC,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                statusIsReady -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GREEN),
                    )
                }
                statusIsProgress ||
                    connState == NodeConnectionState.CONNECTING ||
                    connState == NodeConnectionState.HANDSHAKING ||
                    connState == NodeConnectionState.RECONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        color = ACCENT,
                        strokeWidth = 1.5.dp,
                    )
                }
            }
            Text(
                labelShown,
                color = if (statusIsReady) GREEN else ACCENT,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        SyncStepsRow(current = syncStep)
    }
}

@Composable
private fun SyncStepsRow(current: NodeSyncStep) {
    val currentIndex = SYNC_STEPS_ORDERED.indexOf(current).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CARD),
        ) {
            val fraction = if (current == NodeSyncStep.READY) 1f
            else (currentIndex + 1).toFloat() / SYNC_STEPS_ORDERED.size
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (current == NodeSyncStep.READY) GREEN else ACCENT),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SYNC_STEPS_ORDERED.forEachIndexed { idx, step ->
                val done = idx <= currentIndex
                Text(
                    text = when (step) {
                        NodeSyncStep.CONNECTING -> "Связь"
                        NodeSyncStep.DISCOVERING -> "Сервисы"
                        NodeSyncStep.MTU -> "MTU"
                        NodeSyncStep.SUBSCRIBING -> "CCCD"
                        NodeSyncStep.WANT_CONFIG -> "Запрос"
                        NodeSyncStep.SYNCING -> "Синхр."
                        NodeSyncStep.RECEIVING -> "Данные"
                        NodeSyncStep.READY -> "Готово"
                        else -> ""
                    },
                    color = when {
                        step == NodeSyncStep.READY && done -> GREEN
                        done -> ACCENT
                        else -> TEXT_DIM
                    },
                    fontSize = 8.sp,
                    fontWeight = if (idx == currentIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
