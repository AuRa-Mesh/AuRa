package com.example.aura.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.R
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.progression.AuraProgressCounters
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireUserProfile
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWireNodeUserProfile
import com.example.aura.ui.theme.MeshCard
import com.example.aura.ui.theme.MeshCyan
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.settingsFeedbackMessageColor
import com.example.aura.ui.theme.MeshTextPrimary
import com.example.aura.ui.theme.MeshTextSecondary
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

private val UserConfigCardShape = RoundedCornerShape(16.dp)

@Immutable
data class MeshWireUserConfigPalette(
    val text: Color,
    val muted: Color,
    val card: Color,
    val dividerInCard: Color,
    val dividerOuter: Color,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        val Settings = MeshWireUserConfigPalette(
            text = Mst.text,
            muted = Mst.muted,
            card = Mst.card,
            dividerInCard = Mst.dividerInCard,
            dividerOuter = Mst.dividerOuter,
            accent = Mst.accent,
            onAccent = Mst.onAccent,
        )

        val NodeProfile = MeshWireUserConfigPalette(
            text = MeshTextPrimary,
            muted = MeshTextSecondary,
            card = MeshCard,
            dividerInCard = Color(0xFF2A3544),
            dividerOuter = Color(0xFF3D4F5C),
            accent = MeshCyan,
            onAccent = Color(0xFF0A1620),
        )
    }
}

/**
 * Блок «пользователь / владелец радио» по смыслу mesh [UserConfigScreen]:
 * ID узла, long/short name, сохранение на ноду, модель железа (read-only).
 */
@Composable
fun MeshWireUserConfigBlock(
    modifier: Modifier = Modifier,
    nodeId: String,
    deviceAddress: String?,
    bootstrap: MeshWireNodeUserProfile? = null,
    onBootstrapConsumed: () -> Unit = {},
    palette: MeshWireUserConfigPalette,
    /** Если false — поля и кнопка сохранения отключены (нет BLE, как в типичном mesh-клиенте при disconnect). */
    fieldsEnabled: Boolean = true,
    /** Обёртка Card как на экране настроек; на вкладке профиля обычно false (карточка снаружи). */
    elevatedCard: Boolean = true,
    /** Прогресс и подсказки BLE как на экране «Настройки пользователя». */
    showSettingsSyncUi: Boolean = false,
    onUserProfileChanged: (MeshWireNodeUserProfile) -> Unit = {},
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()
    var fullName by remember { mutableStateOf("") }
    var shortName by remember { mutableStateOf("") }
    var hardwareModel by remember { mutableStateOf("—") }
    var saveHint by remember { mutableStateOf<String?>(null) }
    var savingToDevice by remember { mutableStateOf(false) }
    var syncHint by remember { mutableStateOf<String?>(null) }
    var syncLoadProgress by remember { mutableStateOf<Int?>(null) }

    val displayNodeId = remember(nodeId) { formatMeshWireNodeIdLine(nodeId) }
    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }

    var skipNextRemoteFetch by remember { mutableStateOf(false) }

    fun applyProfile(p: MeshWireNodeUserProfile) {
        fullName = p.longName
        shortName = p.shortName
        hardwareModel = p.hardwareModel
        onUserProfileChanged(p)
    }

    LaunchedEffect(deviceAddress, bootstrap) {
        val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        val boot = bootstrap
        if (addr == null) {
            if (showSettingsSyncUi) {
                syncHint = "Привяжите устройство по Bluetooth, чтобы подтянуть имя и модель с ноды."
                syncLoadProgress = null
            } else {
                syncHint = null
                syncLoadProgress = null
            }
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        if (boot != null) {
            applyProfile(boot)
            saveHint = null
            syncHint = null
            syncLoadProgress = null
            onBootstrapConsumed()
            skipNextRemoteFetch = true
            return@LaunchedEffect
        }
        if (skipNextRemoteFetch) {
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        saveHint = null
        syncHint = null
        syncLoadProgress = if (showSettingsSyncUi) 0 else null
        val numHint = MeshWireNodeNum.parseToUInt(nodeId)
        fetchMeshWireUserProfile(
            context.applicationContext,
            addr,
            expectedNodeNum = numHint,
            onSyncProgress = if (showSettingsSyncUi) {
                { syncLoadProgress = it }
            } else {
                null
            },
            localNodeNum = numHint,
        ) { profile, err ->
            if (profile != null) {
                applyProfile(profile)
                syncHint = null
                syncLoadProgress = null
                MeshNodeSyncMemoryStore.putUser(addr, profile)
            } else {
                syncLoadProgress = null
                if (showSettingsSyncUi) {
                    syncHint = err?.takeIf { it.isNotBlank() }
                        ?: "Не удалось прочитать User / DeviceMetadata по BLE."
                }
            }
        }
    }

    val inner: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (elevatedCard) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ID узла", color = palette.muted, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayNodeId,
                        color = palette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                    )
                    if (nodeId.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val hex = nodeId.trim().removePrefix("!")
                                cm.setPrimaryClip(ClipData.newPlainText("node id", "!$hex"))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.msg_menu_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Копировать Node ID",
                                tint = palette.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = palette.dividerInCard, thickness = 1.dp)

            UserConfigOutlineField(
                palette = palette,
                label = "Полное имя (до ${MeshWireLoRaToRadioEncoder.USER_LONG_NAME_MAX_LEN} симв.)",
                value = fullName,
                enabled = fieldsEnabled,
                onValueChange = { v ->
                    fullName = v.take(MeshWireLoRaToRadioEncoder.USER_LONG_NAME_MAX_LEN)
                },
            )
            Spacer(Modifier.height(4.dp))
            UserConfigOutlineField(
                palette = palette,
                label = "Короткое имя (до ${MeshWireLoRaToRadioEncoder.USER_SHORT_NAME_MAX_LEN} симв.)",
                value = shortName,
                enabled = fieldsEnabled,
                onValueChange = { v ->
                    shortName = v.take(MeshWireLoRaToRadioEncoder.USER_SHORT_NAME_MAX_LEN)
                },
            )

            Spacer(Modifier.height(8.dp))
            saveHint?.let { msg ->
                Text(
                    text = msg,
                    color = settingsFeedbackMessageColor(msg, neutralColor = palette.accent),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
            val validNames = fullName.isNotBlank() && shortName.isNotBlank()
            val canPush =
                addr != null && nodeNum != null && !savingToDevice && fieldsEnabled && validNames
            Button(
                onClick = {
                    if (addr == null || nodeNum == null) return@Button
                    saveHint = null
                    savingToDevice = true
                    val payload = MeshWireLoRaToRadioEncoder.encodeSetOwnerToRadio(
                        longName = fullName,
                        shortName = shortName,
                        ownerNodeNum = nodeNum,
                        device = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(
                            destinationNodeNum = nodeNum,
                        ),
                    )
                    MeshGattToRadioWriter(context.applicationContext).writeToradio(addr, payload) { ok, err ->
                        savingToDevice = false
                        if (ok) {
                            notifyNodeConfigWrite?.invoke()
                            AuraProgressCounters.markProfileFilled(context.applicationContext)
                            saveHint = "Имя отправлено на ноду"
                            val hintNum = nodeNum
                            scope.launch {
                                delay(900)
                                fetchMeshWireUserProfile(
                                    context.applicationContext,
                                    addr,
                                    expectedNodeNum = hintNum,
                                    onSyncProgress = null,
                                    localNodeNum = hintNum,
                                ) { profile, _ ->
                                    if (profile != null) {
                                        applyProfile(profile)
                                        MeshNodeSyncMemoryStore.putUser(addr, profile)
                                    }
                                }
                            }
                        } else {
                            saveHint = "Ошибка: ${err ?: "запись ToRadio"}"
                        }
                    }
                },
                enabled = canPush,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    disabledContainerColor = palette.dividerOuter,
                    disabledContentColor = palette.muted,
                ),
            ) {
                Text(
                    text = if (savingToDevice) "Запись…" else "Сохранить на ноду",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (addr == null) {
                Text(
                    text = "Сохранение доступно после привязки устройства по Bluetooth.",
                    color = palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else if (nodeNum == null) {
                Text(
                    text = "Нужен корректный Node ID в сессии (!xxxxxxxx), иначе адресата пакета не определить.",
                    color = palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else if (!fieldsEnabled) {
                Text(
                    text = "Подключите Bluetooth к ноде, чтобы менять имя.",
                    color = palette.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            HorizontalDivider(
                color = palette.dividerInCard,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                text = "Модель оборудования",
                color = palette.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp, top = 4.dp),
            )
            Text(
                text = hardwareModel,
                color = palette.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }

    when {
        showSettingsSyncUi && elevatedCard -> {
            Column(modifier.fillMaxWidth()) {
                UserConfigSettingsSyncHeader(
                    syncLoadProgress = syncLoadProgress,
                    syncHint = syncHint,
                    palette = palette,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = UserConfigCardShape,
                    colors = CardDefaults.cardColors(containerColor = palette.card),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    inner()
                }
            }
        }
        elevatedCard -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = UserConfigCardShape,
                colors = CardDefaults.cardColors(containerColor = palette.card),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                inner()
            }
        }
        showSettingsSyncUi -> {
            Column(modifier.fillMaxWidth()) {
                UserConfigSettingsSyncHeader(
                    syncLoadProgress = syncLoadProgress,
                    syncHint = syncHint,
                    palette = palette,
                )
                inner()
            }
        }
        else -> {
            Column(modifier.fillMaxWidth()) {
                inner()
            }
        }
    }
}

@Composable
private fun UserConfigSettingsSyncHeader(
    syncLoadProgress: Int?,
    syncHint: String?,
    palette: MeshWireUserConfigPalette,
) {
    syncLoadProgress?.let { p ->
        SettingsTabLoadProgressBar(
            percent = p,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
    syncHint?.let { hint ->
        Text(
            text = hint,
            color = if (hint.contains("Bluetooth", ignoreCase = true)) {
                palette.muted
            } else {
                Color(0xFFFF8A80)
            },
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
}

fun formatMeshWireNodeIdLine(nodeId: String): String {
    val parsed = MeshWireNodeNum.parseToUInt(nodeId)
    if (parsed != null) return MeshWireNodeNum.formatHex(parsed)
    val hex = nodeId.trim().removePrefix("!")
        .filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    if (hex.isEmpty()) return "—"
    val core = when {
        hex.length >= 8 -> hex.takeLast(8)
        else -> hex.padStart(8, '0')
    }
    return "!${core.uppercase(Locale.US)}"
}

@Composable
private fun UserConfigOutlineField(
    palette: MeshWireUserConfigPalette,
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            disabledTextColor = palette.muted,
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = palette.dividerOuter,
            disabledBorderColor = palette.dividerOuter,
            focusedLabelColor = palette.muted,
            unfocusedLabelColor = palette.muted,
            disabledLabelColor = palette.muted,
            cursorColor = palette.accent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(10.dp),
    )
}
