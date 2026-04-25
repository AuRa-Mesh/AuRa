package com.example.aura.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.R
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireSecurityConfig
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWireSecurityPushState
import com.example.aura.meshwire.MeshWireSecurityToRadioEncoder
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.settingsFeedbackMessageColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst
import org.json.JSONObject

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

private const val HELP_PUBLIC =
    "Сгенерирован с вашего открытого ключа и отправлен на другие узлы сетки, чтобы они могли вычислить общий секретный ключ."

private const val HELP_PRIVATE =
    "Используется для создания общего ключа с удалённым устройством. Должно быть ровно 32 байта в Base64."

private const val HELP_ADMIN =
    "До трёх публичных ключей администратора (по 32 байта в Base64). Пустые поля игнорируются."

private const val HELP_SERIAL =
    "Включить доступ к последовательному порту устройства."

private const val HELP_DEBUG_LOG =
    "Разрешить отладочные логи через API."

private const val HELP_MANAGED =
    "Режим управления: узел принимает удалённое администрирование только с указанных ключей."

private const val HELP_LEGACY_ADMIN =
    "Устаревший канал администратора (режим совместимости)."

@Composable
fun SecuritySettingsContent(
    padding: PaddingValues,
    deviceAddress: String? = null,
    nodeId: String = "",
    bootstrap: MeshWireSecurityPushState? = null,
    onBootstrapConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()

    var sec by remember { mutableStateOf(MeshWireSecurityPushState.initial()) }
    var baseline by remember { mutableStateOf(MeshWireSecurityPushState.initial()) }
    var loadHint by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableStateOf<Int?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val connected = addr != null && nodeNum != null

    var skipNextRemoteFetch by remember { mutableStateOf(false) }

    val displayPublicKeyB64 = remember(sec.privateKeyB64, baseline) {
        if (sec.privateKeyB64.trim() == baseline.privateKeyB64.trim()) {
            baseline.publicKeyB64
        } else {
            ""
        }
    }

    fun copyToClipboard(label: String, text: String) {
        if (text.isBlank()) {
            Toast.makeText(context, "Нечего копировать", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            val payload = buildSecurityExportJson(sec)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("Не удалось открыть файл")
        }.onSuccess {
            Toast.makeText(context, "Ключи экспортированы", Toast.LENGTH_SHORT).show()
        }.onFailure {
            actionHint = it.message ?: "Ошибка экспорта"
        }
    }

    LaunchedEffect(addr, bootstrap) {
        val boot = bootstrap
        if (addr == null) {
            loadHint = "Привяжите устройство по Bluetooth."
            loadProgress = null
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        if (boot != null) {
            sec = boot
            baseline = boot.copy()
            loadHint = null
            loadProgress = null
            actionHint = null
            onBootstrapConsumed()
            skipNextRemoteFetch = true
            return@LaunchedEffect
        }
        if (skipNextRemoteFetch) {
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        loadHint = null
        loadProgress = 0
        actionHint = null
        fetchMeshWireSecurityConfig(
            context.applicationContext,
            addr,
            onSyncProgress = { loadProgress = it },
            localNodeNum = nodeNum,
        ) { state, err ->
            when {
                state != null -> {
                    sec = state
                    baseline = state.copy()
                    loadHint = null
                    loadProgress = null
                    MeshNodeSyncMemoryStore.putSecurity(addr, state)
                }
                else -> {
                    loadProgress = null
                    loadHint = err?.takeIf { it.isNotBlank() }
                        ?: "Не удалось загрузить безопасность."
                }
            }
        }
    }

    fun buildPayloadForSave(): MeshWireSecurityPushState? {
        val priv = sec.privateKeyB64.trim()
        if (MeshWireSecurityToRadioEncoder.parseBase64Key32OrNull(priv) == null) {
            actionHint = "Приватный ключ: нужна корректная Base64 строка на 32 байта."
            return null
        }
        val admins = sec.adminKeysB64.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        for (a in admins) {
            if (MeshWireSecurityToRadioEncoder.parseBase64Key32OrNull(a) == null) {
                actionHint = "Ключ администратора: некорректная Base64 (32 байта)."
                return null
            }
        }
        val privMatch = priv == baseline.privateKeyB64.trim()
        val hasAdmins = admins.isNotEmpty()
        return sec.copy(
            publicKeyB64 = if (privMatch) baseline.publicKeyB64 else "",
            privateKeyB64 = priv,
            adminKeysB64 = admins,
            isManaged = if (hasAdmins) sec.isManaged else false,
        )
    }

    fun pushSecurityState(
        push: MeshWireSecurityPushState,
        onDone: (Boolean) -> Unit,
    ) {
        if (addr == null || nodeNum == null) {
            actionHint = "Нужны BLE и Node ID"
            onDone(false)
            return
        }
        saving = true
        actionHint = null
        val privateKeyChanged = push.privateKeyB64.trim() != baseline.privateKeyB64.trim()
        val dev = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = nodeNum)
        val payloads = MeshWireSecurityToRadioEncoder.encodeSecuritySetConfigTransaction(
            push,
            device = dev,
            omitPrivateKey = false,
        )
        val toradioQueue =
            if (privateKeyChanged) {
                payloads + listOf(MeshWireLoRaToRadioEncoder.encodeNodeInfoBroadcastWantResponseToRadio())
            } else {
                payloads
            }
        MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
            deviceAddress = addr,
            payloads = toradioQueue,
            delayBetweenWritesMs = 235L,
        ) { ok, err ->
            saving = false
            if (ok) {
                notifyNodeConfigWrite?.invoke()
                actionHint = if (privateKeyChanged) {
                    "Сохранено на ноде\n\n${context.getString(R.string.security_dm_tofu_after_key_change)}"
                } else {
                    "Сохранено на ноде"
                }
                scope.launch {
                    delay(1_100)
                    fetchMeshWireSecurityConfig(
                        context.applicationContext,
                        addr,
                        onSyncProgress = null,
                        localNodeNum = nodeNum,
                    ) { s, _ ->
                        if (s != null) {
                            sec = s
                            baseline = s.copy()
                            MeshNodeSyncMemoryStore.putSecurity(addr, s)
                        }
                    }
                }
                onDone(true)
            } else {
                actionHint = err ?: "Ошибка BLE"
                onDone(false)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.bg)
            .padding(padding)
            .padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.weight(1f),
        ) {
            loadProgress?.let { p ->
                item {
                    SettingsTabLoadProgressBar(percent = p)
                }
            }
            loadHint?.let { hint ->
                item {
                    Text(
                        hint,
                        color = Mst.muted.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            item {
                SecSectionTitle("Ключ прямого сообщения")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        KeyTextField(
                            label = "Публичный ключ",
                            value = displayPublicKeyB64.ifBlank { "—" },
                            readOnly = true,
                            enabled = true,
                            onCopy = { copyToClipboard("public", displayPublicKeyB64) },
                        )
                        SecSmallHelp(HELP_PUBLIC)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = sec.privateKeyB64,
                            onValueChange = { sec = sec.copy(privateKeyB64 = it) },
                            readOnly = false,
                            enabled = connected,
                            label = { Text("Приватный ключ", color = Mst.muted) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { copyToClipboard("private", sec.privateKeyB64) },
                                    enabled = sec.privateKeyB64.isNotBlank(),
                                ) {
                                    Icon(Icons.Default.ContentCopy, "Копировать", tint = Mst.accent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            shape = FieldShape,
                            colors = secFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        )
                        SecSmallHelp(HELP_PRIVATE)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showRegenerateDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected && !saving,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Mst.accent,
                                contentColor = Mst.onAccent,
                            ),
                        ) {
                            Icon(Icons.Default.Warning, null, Modifier.padding(end = 8.dp))
                            Text("Пересоздать приватный ключ", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected && !saving,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Mst.accent,
                                contentColor = Mst.onAccent,
                            ),
                        ) {
                            Icon(Icons.Default.Warning, null, Modifier.padding(end = 8.dp))
                            Text("Экспортировать ключи", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                SecSectionTitle("Ключи администратора")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        for (slot in 0 until 3) {
                            if (slot > 0) Spacer(Modifier.height(10.dp))
                            val v = sec.adminKeysB64.getOrNull(slot) ?: ""
                            OutlinedTextField(
                                value = v,
                                onValueChange = { nv ->
                                    val list = sec.adminKeysB64.toMutableList()
                                    while (list.size <= slot) list.add("")
                                    list[slot] = nv
                                    sec = sec.copy(adminKeysB64 = list.take(3))
                                },
                                readOnly = false,
                                enabled = connected,
                                label = {
                                    Text(
                                        "Ключ администратора ${slot + 1}",
                                        color = Mst.muted,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = FieldShape,
                                colors = secFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            )
                        }
                        SecSmallHelp(HELP_ADMIN)
                    }
                }
            }

            item {
                SecSectionTitle("Логи")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column {
                        SecSwitchRow(
                            label = "Последовательный порт",
                            checked = sec.serialEnabled,
                            enabled = connected && !saving,
                            onCheckedChange = { sec = sec.copy(serialEnabled = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        SecSwitchRow(
                            label = "Отладочные логи API",
                            checked = sec.debugLogApiEnabled,
                            enabled = connected && !saving,
                            onCheckedChange = { sec = sec.copy(debugLogApiEnabled = it) },
                        )
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SecSmallHelp(HELP_SERIAL)
                            Spacer(Modifier.height(4.dp))
                            SecSmallHelp(HELP_DEBUG_LOG)
                        }
                    }
                }
            }

            item {
                SecSectionTitle("Администрирование")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column {
                        val hasAdminKeys = sec.adminKeysB64.any {
                            it.isNotBlank() &&
                                MeshWireSecurityToRadioEncoder.parseBase64Key32OrNull(it.trim()) != null
                        }
                        SecSwitchRow(
                            label = "Режим управления (managed)",
                            checked = sec.isManaged,
                            enabled = connected && !saving && hasAdminKeys,
                            onCheckedChange = { sec = sec.copy(isManaged = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        SecSwitchRow(
                            label = "Устаревший канал администратора",
                            checked = sec.adminChannelEnabled,
                            enabled = connected && !saving,
                            onCheckedChange = { sec = sec.copy(adminChannelEnabled = it) },
                        )
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SecSmallHelp(HELP_MANAGED)
                            Spacer(Modifier.height(4.dp))
                            SecSmallHelp(HELP_LEGACY_ADMIN)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Mst.dividerOuter, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        actionHint?.let {
            Text(
                it,
                color = settingsFeedbackMessageColor(it),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    sec = baseline.copy()
                    actionHint = null
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mst.cancelBtn,
                    contentColor = Mst.text,
                ),
            ) { Text("Отмена") }

            Button(
                onClick = {
                    val payload = buildPayloadForSave() ?: return@Button
                    pushSecurityState(payload) { }
                },
                enabled = !saving && connected,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mst.accent,
                    contentColor = Mst.onAccent,
                    disabledContainerColor = Mst.dividerOuter,
                ),
            ) {
                Text(if (saving) "Сохранение…" else "Сохранить", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            containerColor = Mst.card,
            titleContentColor = Mst.text,
            textContentColor = Mst.muted,
            title = { Text("Пересоздать приватный ключ?") },
            text = {
                Text(stringResource(R.string.security_regenerate_dialog_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateDialog = false
                        if (addr == null || nodeNum == null) {
                            actionHint = "Нужны BLE и Node ID"
                            return@TextButton
                        }
                        val f = ByteArray(32).apply { Random.nextBytes(this) }
                        f[0] = (f[0].toInt() and 0xF8).toByte()
                        f[31] = ((f[31].toInt() and 0x7F) or 0x40).toByte()
                        val newPriv = Base64.encodeToString(f, Base64.NO_WRAP)
                        val push = sec.copy(
                            publicKeyB64 = "",
                            privateKeyB64 = newPriv,
                        )
                        pushSecurityState(push) { }
                    },
                ) { Text("Пересоздать", color = Mst.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text("Отмена", color = Mst.muted)
                }
            },
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Mst.card,
            titleContentColor = Mst.text,
            textContentColor = Mst.muted,
            title = { Text("Экспортировать ключи") },
            text = {
                Text("Будет создан JSON-файл с публичным и приватным ключом.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportDialog = false
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(
                                Intent.EXTRA_TITLE,
                                "security_keys_${System.currentTimeMillis()}.json",
                            )
                        }
                        exportLauncher.launch(intent)
                    },
                ) { Text("ОК", color = Mst.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Отмена", color = Mst.muted)
                }
            },
        )
    }
}

private fun buildSecurityExportJson(state: MeshWireSecurityPushState): String {
    val o = JSONObject()
    o.put("timestamp", System.currentTimeMillis())
    o.put("public_key", state.publicKeyB64)
    o.put("private_key", state.privateKeyB64)
    return o.toString()
}

@Composable
private fun SecSectionTitle(text: String) {
    Text(
        text,
        color = Mst.text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
    )
}

@Composable
private fun SecSmallHelp(text: String) {
    Text(
        text,
        color = Mst.muted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SecSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Mst.text,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mst.accent,
                checkedTrackColor = Mst.accent.copy(alpha = 0.45f),
                uncheckedThumbColor = Mst.switchThumbUnchecked,
                uncheckedTrackColor = Mst.switchTrackUnchecked,
            ),
        )
    }
}

@Composable
private fun KeyTextField(
    label: String,
    value: String,
    readOnly: Boolean,
    enabled: Boolean,
    onCopy: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = readOnly,
        enabled = enabled,
        label = { Text(label, color = Mst.muted) },
        trailingIcon = {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, "Копировать", tint = Mst.accent)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4,
        shape = FieldShape,
        colors = secFieldColors(),
    )
}

@Composable
private fun secFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Mst.text,
    unfocusedTextColor = Mst.text,
    focusedBorderColor = Mst.accent,
    unfocusedBorderColor = Mst.dividerOuter,
    focusedLabelColor = Mst.muted,
    unfocusedLabelColor = Mst.muted,
    cursorColor = Mst.accent,
    focusedContainerColor = Mst.card,
    unfocusedContainerColor = Mst.card,
)
