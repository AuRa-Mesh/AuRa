package com.example.aura.mesh.nodedb

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.progression.AuraProgressCounters
import com.example.aura.vip.VipStatusStore
import com.example.aura.ui.components.LocalProfileAvatarCircle
import com.example.aura.ui.vip.VipAvatarFrame
import com.example.aura.ui.vip.VipAvatarFrameNodesTabScaleMultiplier
import com.example.aura.ui.vip.rememberLocalVipActive
import com.example.aura.ui.vip.rememberPeerVipActive
import java.util.Locale

private val ChromeText = Color(0xFFE8EAED)
private val ChromeMuted = Color(0xFF7A8088)
private val SnrBad = Color(0xFFFF4D6A)
private val SnrMid = Color(0xFFFFD166)
private val SnrGood = Color(0xFF39FFB6)
private val NodesCardTitleWhite = Color(0xFFFFFFFF)
private val NodesCardTextPrimary = Color(0xFFE8EAED)
private val NodesCardTextSecondary = Color(0xFF9AA0A8)
private val NodesCardTextTertiary = Color(0xFF6B7280)

private fun nodeCardBackground(nodeNum: Long): Color {
    val alt = (nodeNum xor (nodeNum ushr 17)) and 1L
    return if (alt == 0L) Color(0xFF0A0F18) else Color(0xFF0E1522)
}

/** Как в типичном mesh-клиенте: доля 0..1 или уже проценты. */
private fun formatTelemetryPercent(value: Float?): String? {
    if (value == null) return null
    val pct = if (value <= 1.5f) value * 100f else value
    return String.format(Locale.getDefault(), "%.1f%%", pct)
}

private fun row3MetricsLine(n: MeshWireNodeSummary): String {
    val chUtil = formatTelemetryPercent(n.channelUtilization)
    val airTx = formatTelemetryPercent(n.airUtilTx)
    return buildString {
        if (chUtil != null || airTx != null) {
            if (chUtil != null) {
                append("ChUtil ")
                append(chUtil)
            }
            if (airTx != null) {
                if (isNotEmpty()) append(' ')
                append("AirUtilTX ")
                append(airTx)
            }
        } else {
            n.channel?.let { ch ->
                append("ch:")
                append(ch)
            }
            val hops = n.relayHopsCount()
            if (hops != null) {
                if (isNotEmpty()) append(' ')
                append("Прыжки: ")
                append(hops)
            }
            if (isEmpty()) append("—")
        }
    }
}

@Composable
fun NodeCard(
    row: MeshNodeListRowUi,
    selfNode: MeshWireNodeSummary?,
    /** Тот же файл, что в экране «Профиль» / шапке чата — для строки «свой» узел. */
    profileAvatarPath: String?,
    onClick: () -> Unit,
) {
    val n = row.node
    val appCtx = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        VipStatusStore.ensureLoaded(appCtx)
    }
    LaunchedEffect(n.nodeNum, n.hardwareModel) {
        AuraProgressCounters.notePeerHardwareOnce(appCtx, n.nodeNum, n.hardwareModel)
    }
    val cardBg = nodeCardBackground(n.nodeNum)
    val titleColor = NodesCardTitleWhite
    val lockAndSignalTint = if (row.presence == NodePresenceLevel.OFFLINE) ChromeMuted else SnrGood
    val cardAlpha = when (row.presence) {
        NodePresenceLevel.ONLINE -> 1f
        NodePresenceLevel.RECENT -> 0.7f
        NodePresenceLevel.OFFLINE -> 0.5f
    }
    val iconTint = when (row.presence) {
        NodePresenceLevel.OFFLINE -> ChromeMuted
        else -> ChromeText
    }
    val batteryTint = when (row.batteryTintTier) {
        BatteryTintTier.Good -> SnrGood
        BatteryTintTier.Mid -> SnrMid
        BatteryTintTier.Bad -> SnrBad
    }
    val batteryIconTint = if (row.presence == NodePresenceLevel.OFFLINE) ChromeMuted else batteryTint
    val row3Text = remember(n.nodeNum, n.channelUtilization, n.airUtilTx, n.channel, n.meshHopLimit, n.meshHopStart) {
        row3MetricsLine(n)
    }
    val cardShape = RoundedCornerShape(16.dp)
    // [Box] + фон/тень без clip у контейнера — табличка VIP может выходить за скругления «пузыря».
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .graphicsLayer { alpha = cardAlpha }
            .shadow(elevation = 1.dp, shape = cardShape, clip = false)
            .background(color = cardBg, shape = cardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Левый блок: аватар узла (локально / детерминированно по id)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .wrapContentWidth()
                    .widthIn(min = 56.dp),
            ) {
                val avatarAlpha = when (row.presence) {
                    NodePresenceLevel.ONLINE -> 1f
                    NodePresenceLevel.RECENT -> 0.82f
                    NodePresenceLevel.OFFLINE -> 0.45f
                }
                if (row.isSelf) {
                    val photo = profileAvatarPath?.trim()?.takeIf { it.isNotEmpty() }
                    VipAvatarFrame(
                        active = rememberLocalVipActive(),
                        avatarSize = 52.dp,
                        frameScaleMultiplier = VipAvatarFrameNodesTabScaleMultiplier,
                        useWindowPopupOverlay = false,
                        onAvatarClick = onClick,
                        nodeIdHex = n.nodeIdHex,
                    ) {
                        if (photo != null) {
                            LocalProfileAvatarCircle(
                                filePath = photo,
                                size = 52.dp,
                                placeholderBackground = Color(0xFF1A2535),
                                placeholderIconTint = Color(0xFF8FB8C8),
                                modifier = Modifier.graphicsLayer { alpha = avatarAlpha },
                                onClick = null,
                                contentDescription = n.displayLongName(),
                            )
                        } else {
                            MeshNodeAvatar(
                                node = n,
                                modifier = Modifier
                                    .size(52.dp)
                                    .graphicsLayer { alpha = avatarAlpha },
                                contentAlpha = 1f,
                            )
                        }
                    }
                } else {
                    VipAvatarFrame(
                        active = rememberPeerVipActive(n.nodeNum),
                        avatarSize = 52.dp,
                        frameScaleMultiplier = VipAvatarFrameNodesTabScaleMultiplier,
                        useWindowPopupOverlay = false,
                        onAvatarClick = onClick,
                        nodeIdHex = n.nodeIdHex,
                    ) {
                        MeshNodeAvatar(
                            node = n,
                            modifier = Modifier.size(52.dp),
                            contentAlpha = avatarAlpha,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.width(8.dp))
            // Правая колонка: 4 строки как на референс-скриншоте.
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Строка 1: замок + long name | mesh-сигнал + «Online» / относительное время
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = lockAndSignalTint,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = row.longNameBold,
                            color = titleColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = lockAndSignalTint,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = row.statusCompact,
                            color = NodesCardTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
                // Строка 2: батарея % + напряжение | MQTT облако или высота MSL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.BatteryStd,
                            contentDescription = null,
                            tint = batteryIconTint,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = buildString {
                                append(row.batteryPercentText ?: "—")
                                if (n.isCharging == true) append(" ⚡")
                                n.batteryVoltage?.let { v ->
                                    append(' ')
                                    append(String.format(Locale.getDefault(), "%.2f В", v))
                                }
                            },
                            color = NodesCardTextPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        when {
                            n.viaMqtt ->
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = "MQTT",
                                    tint = if (row.presence == NodePresenceLevel.OFFLINE) ChromeMuted else SnrMid,
                                    modifier = Modifier.size(18.dp),
                                )
                            n.altitudeMeters != null ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Landscape,
                                        contentDescription = null,
                                        tint = if (row.presence == NodePresenceLevel.OFFLINE) ChromeMuted else NodesCardTextSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "${n.altitudeMeters} m MSL",
                                        color = NodesCardTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            else ->
                                Text(
                                    text = "—",
                                    color = NodesCardTextTertiary,
                                    fontSize = 11.sp,
                                )
                        }
                    }
                }
                // Строка 3: ChUtil / AirUtilTX или ch + прыжки
                Text(
                    text = row3Text,
                    color = NodesCardTextTertiary,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Строка 4: модель | роль | node id
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = n.hardwareModel,
                        color = NodesCardTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = n.roleLabel.uppercase(Locale.getDefault()),
                        color = NodesCardTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = n.nodeIdHex,
                        color = NodesCardTextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
