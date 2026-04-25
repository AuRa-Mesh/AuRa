package com.example.aura.ui.screens

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireLoRaConfig
import com.example.aura.bluetooth.fetchMeshWireMqttConfig
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireMqttPushState
import com.example.aura.meshwire.MeshWireMqttToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.settingsFeedbackMessageColor
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

/** Подмешивает `LoRaConfig.config_ok_to_mqtt` в [MeshWireMqttPushState.configOkToMqtt]. */
private suspend fun awaitMqttMergedWithLoraOk(
    appCtx: Context,
    deviceAddress: String,
    mqtt: MeshWireMqttPushState,
    localNodeNum: UInt?,
): MeshWireMqttPushState = withContext(Dispatchers.IO) {
    MeshNodeSyncMemoryStore.getLora(deviceAddress)?.let { lora ->
        return@withContext mqtt.copy(configOkToMqtt = lora.configOkToMqtt)
    }
    suspendCancellableCoroutine { cont ->
        fetchMeshWireLoRaConfig(appCtx, deviceAddress, null, localNodeNum) { lora, _ ->
            if (lora != null) {
                MeshNodeSyncMemoryStore.putLora(deviceAddress, lora)
            }
            if (cont.isActive) {
                cont.resume(mqtt.copy(configOkToMqtt = lora?.configOkToMqtt ?: false))
            }
        }
    }
}

/** Запись `config_ok_to_mqtt` через Admin LoRa-транзакцию; возвращает проверенный LoRa с ноды. */
private suspend fun writeOkToMqttViaLoRaAdmin(
    appCtx: Context,
    deviceAddress: String,
    destinationNodeNum: UInt,
    wantOkToMqtt: Boolean,
): MeshWireLoRaPushState = withContext(Dispatchers.IO) {
    var lora = MeshNodeSyncMemoryStore.getLora(deviceAddress)
    if (lora == null) {
        lora = suspendCancellableCoroutine { cont ->
            fetchMeshWireLoRaConfig(appCtx, deviceAddress, null, destinationNodeNum) { s, _ ->
                if (s != null) MeshNodeSyncMemoryStore.putLora(deviceAddress, s)
                if (cont.isActive) cont.resume(s)
            }
        }
    }
    checkNotNull(lora) { "Не удалось прочитать LoRa с ноды" }
    val updated = lora.copy(configOkToMqtt = wantOkToMqtt)
    val dev = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = destinationNodeNum)
    val payloads = MeshWireLoRaToRadioEncoder.encodeLoraSetConfigTransaction(updated, dev)
    val bleOk = suspendCancellableCoroutine { cont ->
        MeshGattToRadioWriter(appCtx).writeToradioQueue(
            deviceAddress = deviceAddress,
            payloads = payloads,
            delayBetweenWritesMs = 235L,
        ) { ok, _ ->
            if (cont.isActive) cont.resume(ok)
        }
    }
    check(bleOk) { "Ошибка записи ToRadio" }
    delay(450)
    val verified = suspendCancellableCoroutine<MeshWireLoRaPushState?> { cont ->
        fetchMeshWireLoRaConfig(appCtx, deviceAddress, null, destinationNodeNum) { s, _ ->
            if (s != null) MeshNodeSyncMemoryStore.putLora(deviceAddress, s)
            if (cont.isActive) cont.resume(s)
        }
    }
    check(verified != null && verified.configOkToMqtt == wantOkToMqtt) {
        "Нода не подтвердила OK в MQTT"
    }
    verified!!
}

@Composable
fun MqttSettingsContent(
    padding: PaddingValues,
    deviceAddress: String? = null,
    nodeId: String = "",
    bootstrap: MeshWireMqttPushState? = null,
    onBootstrapConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(MeshWireMqttPushState.initial()) }
    var baseline by remember { mutableStateOf(MeshWireMqttPushState.initial()) }
    var loadHint by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableStateOf<Int?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var okToMqttWriteInFlight by remember { mutableStateOf(false) }
    var mqttOkBindingReady by remember { mutableStateOf(false) }

    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }

    LaunchedEffect(deviceAddress, nodeId, bootstrap) {
        mqttOkBindingReady = false
        val a = addr
        val boot = bootstrap
        val appCtx = context.applicationContext
        if (a == null) {
            loadHint = "Привяжите устройство по Bluetooth, чтобы прочитать настройки с ноды."
            loadProgress = null
            mqttOkBindingReady = true
            return@LaunchedEffect
        }
        if (boot != null) {
            onBootstrapConsumed()
        }
        loadHint = null
        loadProgress = 0
        actionHint = null
        val (s, err) = suspendCancellableCoroutine<Pair<MeshWireMqttPushState?, String?>> { cont ->
            fetchMeshWireMqttConfig(
                appCtx,
                a,
                onSyncProgress = { loadProgress = it },
                localNodeNum = nodeNum,
            ) { mqtt, e ->
                if (cont.isActive) cont.resume(mqtt to e)
            }
        }
        if (s != null) {
            MeshNodeSyncMemoryStore.putMqtt(a, s)
        }
        val baseForOk = s ?: boot ?: MeshNodeSyncMemoryStore.getMqtt(a)
        if (baseForOk == null) {
            loadProgress = null
            loadHint = err?.takeIf { it.isNotBlank() }
                ?: "Не удалось прочитать настройки MQTT по BLE."
            mqttOkBindingReady = true
            return@LaunchedEffect
        }
        val merged = awaitMqttMergedWithLoraOk(appCtx, a, baseForOk, nodeNum)
        state = merged
        baseline = merged
        MeshNodeSyncMemoryStore.putMqtt(a, merged)
        if (s == null) {
            loadHint = err?.takeIf { it.isNotBlank() }
                ?: "Не удалось прочитать настройки MQTT по BLE."
        } else {
            loadHint = null
        }
        loadProgress = null
        mqttOkBindingReady = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.bg)
            .padding(padding)
            .padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                loadProgress?.let { p ->
                    SettingsTabLoadProgressBar(
                        percent = p,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                loadHint?.let {
                    Text(
                        it,
                        color = Mst.muted.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                Text(
                    "Настройка MQTT",
                    color = Mst.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        mqttToggleRow(
                            title = "MQTT включен",
                            checked = state.enabled,
                            onCheckedChange = { state = state.copy(enabled = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttToggleRow(
                            title = "OK в MQTT",
                            checked = state.configOkToMqtt,
                            enabled = mqttOkBindingReady &&
                                !okToMqttWriteInFlight &&
                                addr != null &&
                                nodeNum != null,
                            onCheckedChange = { want ->
                                val a = addr
                                val num = nodeNum
                                if (a == null || num == null) return@mqttToggleRow
                                val previous = state.configOkToMqtt
                                state = state.copy(configOkToMqtt = want)
                                okToMqttWriteInFlight = true
                                actionHint = null
                                scope.launch {
                                    try {
                                        withTimeout(25_000L) {
                                            val verified = writeOkToMqttViaLoRaAdmin(
                                                appCtx = context.applicationContext,
                                                deviceAddress = a,
                                                destinationNodeNum = num,
                                                wantOkToMqtt = want,
                                            )
                                            MeshNodeSyncMemoryStore.putLora(a, verified)
                                            val saved = state.copy(configOkToMqtt = verified.configOkToMqtt)
                                            state = saved
                                            baseline = saved
                                            MeshNodeSyncMemoryStore.putMqtt(a, saved)
                                            notifyNodeConfigWrite?.invoke()
                                        }
                                    } catch (_: TimeoutCancellationException) {
                                        state = state.copy(configOkToMqtt = previous)
                                        actionHint = "Таймаут при записи OK в MQTT"
                                    } catch (e: Exception) {
                                        state = state.copy(configOkToMqtt = previous)
                                        actionHint = e.message?.takeIf { it.isNotBlank() }
                                            ?: "Ошибка записи OK в MQTT"
                                    } finally {
                                        okToMqttWriteInFlight = false
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttOutlinedField(
                            label = "Адрес",
                            value = state.address,
                            onValueChange = { state = state.copy(address = it) },
                            keyboardType = KeyboardType.Uri,
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttOutlinedField(
                            label = "Имя пользователя",
                            value = state.username,
                            onValueChange = { state = state.copy(username = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = { state = state.copy(password = it) },
                                label = { Text("Пароль", color = Mst.accent) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = FieldShape,
                                colors = mqttFieldColors(),
                                singleLine = true,
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = Mst.accent,
                                        )
                                    }
                                },
                            )
                        }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttToggleRow(
                            title = "Шифрование включено",
                            checked = state.encryptionEnabled,
                            onCheckedChange = { state = state.copy(encryptionEnabled = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttToggleRow(
                            title = "Вывод JSON включен",
                            checked = state.jsonEnabled,
                            onCheckedChange = { state = state.copy(jsonEnabled = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttToggleRow(
                            title = "TLS включен",
                            checked = state.tlsEnabled,
                            onCheckedChange = { state = state.copy(tlsEnabled = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttOutlinedField(
                            label = "Корневая тема",
                            value = state.root,
                            onValueChange = { state = state.copy(root = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        mqttToggleRow(
                            title = "Прокси клиенту включен",
                            checked = state.proxyToClientEnabled,
                            onCheckedChange = { state = state.copy(proxyToClientEnabled = it) },
                        )
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    state = baseline.copy()
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
                    if (addr == null) {
                        actionHint = "Нет BLE-адреса"
                        return@Button
                    }
                    if (nodeNum == null) {
                        actionHint = "Нужен Node ID (!xxxxxxxx)"
                        return@Button
                    }
                    saving = true
                    actionHint = null
                    val dev = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = nodeNum)
                    val payloads = MeshWireMqttToRadioEncoder.encodeMqttSetModuleConfigTransaction(state, dev)
                    MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
                        deviceAddress = addr,
                        payloads = payloads,
                        delayBetweenWritesMs = 235L,
                    ) { ok, err ->
                        saving = false
                        if (ok) {
                            notifyNodeConfigWrite?.invoke()
                            actionHint = "Сохранено на ноде"
                            val saved = state.copy()
                            state = saved
                            baseline = saved.copy()
                            addr.let { MeshNodeSyncMemoryStore.putMqtt(it, saved) }
                            scope.launch {
                                delay(1_000)
                                fetchMeshWireMqttConfig(
                                    context.applicationContext,
                                    addr,
                                    onSyncProgress = null,
                                    localNodeNum = nodeNum,
                                ) { s, _ ->
                                    if (s != null) {
                                        scope.launch {
                                            val merged = awaitMqttMergedWithLoraOk(
                                                context.applicationContext,
                                                addr,
                                                s,
                                                nodeNum,
                                            )
                                            state = merged
                                            baseline = merged.copy()
                                            MeshNodeSyncMemoryStore.putMqtt(addr, merged)
                                        }
                                    }
                                }
                            }
                        } else {
                            actionHint = "Ошибка: ${err ?: "BLE"}"
                        }
                    }
                },
                enabled = !saving && addr != null && nodeNum != null,
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
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun mqttToggleRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Mst.text, fontSize = 16.sp)
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
private fun mqttOutlinedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = Mst.accent) },
            modifier = Modifier.fillMaxWidth(),
            shape = FieldShape,
            colors = mqttFieldColors(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

@Composable
private fun mqttFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Mst.text,
    unfocusedTextColor = Mst.text,
    focusedBorderColor = Mst.accent,
    unfocusedBorderColor = Mst.dividerOuter,
    focusedLabelColor = Mst.accent,
    unfocusedLabelColor = Mst.accent,
    cursorColor = Mst.accent,
    focusedContainerColor = Mst.card,
    unfocusedContainerColor = Mst.card,
)
