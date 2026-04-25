package com.example.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireDeviceConfig
import com.example.aura.meshwire.MeshWireDeviceConfigLogic
import com.example.aura.meshwire.MeshWireDevicePushState
import com.example.aura.meshwire.toMeshWirePosixTzString
import com.example.aura.meshwire.MeshWireDeviceRoleOptions
import com.example.aura.meshwire.MeshWireDeviceToRadioEncoder
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeInfoIntervalOptions
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWireRebroadcastOptions
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.settingsFeedbackMessageColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst
import java.time.ZoneId

/** Экран «Устройство»: опции LoRa, аппаратные переключатели, POSIX tz, GPIO и запись [Config.device] по BLE. */
private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsContent(
    padding: PaddingValues,
    deviceAddress: String? = null,
    nodeId: String = "",
    bootstrap: MeshWireDevicePushState? = null,
    onBootstrapConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(MeshWireDevicePushState.initial()) }
    var baseline by remember { mutableStateOf(MeshWireDevicePushState.initial()) }
    var loadHint by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableStateOf<Int?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var roleMenu by remember { mutableStateOf(false) }
    var rebroadcastMenu by remember { mutableStateOf(false) }
    var intervalMenu by remember { mutableStateOf(false) }
    var pendingInfrastructureRoleWire by remember { mutableStateOf<Int?>(null) }

    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }

    var skipNextRemoteFetch by remember { mutableStateOf(false) }

    LaunchedEffect(deviceAddress, bootstrap) {
        val a = addr
        val boot = bootstrap
        if (a == null) {
            loadHint = "Привяжите устройство по Bluetooth, чтобы прочитать настройки с ноды."
            loadProgress = null
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        if (boot != null) {
            val clamped = MeshWireDeviceConfigLogic.clampDeviceState(boot)
            state = clamped
            baseline = clamped
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
        fetchMeshWireDeviceConfig(
            context.applicationContext,
            a,
            onSyncProgress = { loadProgress = it },
            localNodeNum = nodeNum,
        ) { s, err ->
            if (s != null) {
                val clamped = MeshWireDeviceConfigLogic.clampDeviceState(s)
                state = clamped
                baseline = clamped
                loadHint = null
                loadProgress = null
                MeshNodeSyncMemoryStore.putDevice(a, clamped)
            } else {
                loadProgress = null
                loadHint = err?.takeIf { it.isNotBlank() }
                    ?: "Не удалось прочитать настройки устройства по BLE."
            }
        }
    }

    val roleSel = remember(state.roleWire) { MeshWireDeviceRoleOptions.findByWire(state.roleWire) }
    val rbSel = remember(state.rebroadcastModeWire) {
        MeshWireRebroadcastOptions.findByWire(state.rebroadcastModeWire)
    }
    val intervalSel = remember(state.nodeInfoBroadcastSecs) {
        MeshWireNodeInfoIntervalOptions.findNearest(state.nodeInfoBroadcastSecs)
    }

    val focusManager = LocalFocusManager.current
    val controlsEnabled = addr != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.bg)
            .padding(padding)
            .padding(horizontal = 16.dp),
    ) {
        pendingInfrastructureRoleWire?.let { roleWire ->
            RouterRoleConfirmationDialog(
                onDismiss = { pendingInfrastructureRoleWire = null },
                onConfirm = {
                    state = state.copy(roleWire = roleWire)
                    pendingInfrastructureRoleWire = null
                },
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            loadProgress?.let { p ->
                item {
                    SettingsTabLoadProgressBar(percent = p)
                }
            }
            item {
                Text(
                    "Опции",
                    color = Mst.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = roleMenu,
                                onExpandedChange = { roleMenu = it },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = roleSel.apiName.uppercase(Locale.US),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Роль устройства", color = Mst.accent) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleMenu)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = devDropdownFieldColors(),
                                    singleLine = true,
                                )
                                ExposedDropdownMenu(
                                    expanded = roleMenu,
                                    onDismissRequest = { roleMenu = false },
                                    containerColor = Mst.cancelBtn,
                                ) {
                                    MeshWireDeviceRoleOptions.DROPDOWN.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    opt.apiName.uppercase(Locale.US),
                                                    color = Mst.text,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            },
                                            onClick = {
                                                if (MeshWireDeviceConfigLogic.isInfrastructureRoleWire(opt.wireOrdinal)) {
                                                    pendingInfrastructureRoleWire = opt.wireOrdinal
                                                } else {
                                                    state = state.copy(roleWire = opt.wireOrdinal)
                                                }
                                                roleMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            Text(
                                roleSel.descriptionRu,
                                color = Mst.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))

                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = rebroadcastMenu,
                                onExpandedChange = { rebroadcastMenu = it },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = rbSel.apiName.uppercase(Locale.US),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Режим ретрансляции", color = Mst.accent) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = rebroadcastMenu)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = devDropdownFieldColors(),
                                    singleLine = true,
                                )
                                ExposedDropdownMenu(
                                    expanded = rebroadcastMenu,
                                    onDismissRequest = { rebroadcastMenu = false },
                                    containerColor = Mst.cancelBtn,
                                ) {
                                    MeshWireRebroadcastOptions.ALL.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    opt.apiName.uppercase(Locale.US),
                                                    color = Mst.text,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            },
                                            onClick = {
                                                state = state.copy(rebroadcastModeWire = opt.wireOrdinal)
                                                rebroadcastMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            Text(
                                rbSel.descriptionRu,
                                color = Mst.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))

                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = intervalMenu,
                                onExpandedChange = { intervalMenu = it },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = intervalSel.labelRu,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = {
                                        Text(
                                            "Интервал вещания передачи информации об узле",
                                            color = Mst.accent,
                                        )
                                    },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalMenu)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = devDropdownFieldColors(),
                                    singleLine = true,
                                )
                                ExposedDropdownMenu(
                                    expanded = intervalMenu,
                                    onDismissRequest = { intervalMenu = false },
                                    containerColor = Mst.cancelBtn,
                                ) {
                                    MeshWireNodeInfoIntervalOptions.ALL.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.labelRu, color = Mst.text) },
                                            onClick = {
                                                state = state.copy(nodeInfoBroadcastSecs = opt.seconds)
                                                intervalMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Аппаратное обеспечение",
                    color = Mst.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        deviceSwitchRow(
                            title = "Двойное касание как нажатие кнопки",
                            summary = "На поддерживаемых акселерометрах двойной тап обрабатывается как нажатие кнопки.",
                            checked = state.doubleTapAsButtonPress,
                            enabled = controlsEnabled,
                            onCheckedChange = { state = state.copy(doubleTapAsButtonPress = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        deviceSwitchRow(
                            title = "Тройной клик — ad-hoc пинг",
                            summary = "Тройное нажатие кнопки переключает GPS (если не отключено).",
                            checked = !state.disableTripleClick,
                            enabled = controlsEnabled,
                            onCheckedChange = { state = state.copy(disableTripleClick = !it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        deviceSwitchRow(
                            title = "Пульсация LED",
                            summary = "Мигание индикатора по умолчанию (LED_PIN).",
                            checked = !state.ledHeartbeatDisabled,
                            enabled = controlsEnabled,
                            onCheckedChange = { state = state.copy(ledHeartbeatDisabled = !it) },
                        )
                    }
                }
            }

            item {
                Text(
                    "Часовой пояс",
                    color = Mst.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        OutlinedTextField(
                            value = state.tzdef,
                            onValueChange = { if (it.length <= 64) state = state.copy(tzdef = it) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = controlsEnabled,
                            label = { Text("POSIX tzdef", color = Mst.accent) },
                            placeholder = { Text("напр. CET-1CEST,M3.5.0,M10.5.0/3", color = Mst.muted) },
                            shape = FieldShape,
                            colors = devDropdownFieldColors(),
                            minLines = 2,
                            maxLines = 3,
                            trailingIcon = {
                                if (state.tzdef.isNotEmpty()) {
                                    IconButton(
                                        onClick = { state = state.copy(tzdef = "") },
                                        enabled = controlsEnabled,
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = Mst.text)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        )
                        Text(
                            "Строка часового пояса в формате POSIX (см. прошивку / документацию mesh).",
                            color = Mst.muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TextButton(
                            onClick = {
                                state = state.copy(tzdef = ZoneId.systemDefault().toMeshWirePosixTzString())
                            },
                            enabled = controlsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = Mst.accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Подставить часовой пояс телефона", color = Mst.accent)
                        }
                    }
                }
            }

            item {
                Text(
                    "GPIO",
                    color = Mst.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        OutlinedTextField(
                            value = state.buttonGpio.toString(),
                            onValueChange = { s ->
                                state = state.copy(buttonGpio = parseUint32Digits(s))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = controlsEnabled,
                            label = { Text("Кнопка (GPIO)", color = Mst.accent) },
                            shape = FieldShape,
                            colors = devDropdownFieldColors(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.buzzerGpio.toString(),
                            onValueChange = { s ->
                                state = state.copy(buzzerGpio = parseUint32Digits(s))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = controlsEnabled,
                            label = { Text("Пищалка (GPIO)", color = Mst.accent) },
                            shape = FieldShape,
                            colors = devDropdownFieldColors(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        )
                    }
                }
            }

            item {
                loadHint?.let {
                    Text(it, color = Mst.muted.copy(alpha = 0.95f), fontSize = 13.sp)
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
                    val payloads = MeshWireDeviceToRadioEncoder.encodeDeviceSetConfigTransaction(
                        MeshWireDeviceConfigLogic.clampDeviceState(state),
                        dev,
                    )
                    MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
                        deviceAddress = addr,
                        payloads = payloads,
                        delayBetweenWritesMs = 235L,
                    ) { ok, err ->
                        saving = false
                        if (ok) {
                            notifyNodeConfigWrite?.invoke()
                            actionHint = "Сохранено на ноде"
                            val saved = MeshWireDeviceConfigLogic.clampDeviceState(state)
                            state = saved
                            baseline = saved.copy()
                            scope.launch {
                                delay(1_000)
                                fetchMeshWireDeviceConfig(
                                    context.applicationContext,
                                    addr,
                                    onSyncProgress = null,
                                    localNodeNum = nodeNum,
                                ) { s, _ ->
                                    if (s != null) {
                                        val c = MeshWireDeviceConfigLogic.clampDeviceState(s)
                                        state = c
                                        baseline = c.copy()
                                        MeshNodeSyncMemoryStore.putDevice(addr, c)
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

private fun parseUint32Digits(s: String): UInt {
    val d = s.filter { it.isDigit() }.take(10)
    if (d.isEmpty()) return 0u
    return (d.toULongOrNull() ?: 0uL).coerceAtMost(4294967295uL).toUInt()
}

@Composable
private fun deviceSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Mst.text, fontSize = 16.sp)
            Text(
                summary,
                color = Mst.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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
private fun RouterRoleConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var confirmed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вы уверены?", color = Mst.text) },
        text = {
            Column {
                Text(
                    "Я прочитал документацию о ролях устройств и материал о выборе правильной роли " +
                        "(meshwire.org/docs/configuration/radio/device/#roles).",
                    color = Mst.muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable { confirmed = !confirmed },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("Я знаю, что делаю.", color = Mst.text, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmed) {
                Text("Принять", color = Mst.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Mst.text) }
        },
        containerColor = Mst.card,
    )
}

@Composable
private fun devDropdownFieldColors() = OutlinedTextFieldDefaults.colors(
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
