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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireTelemetryConfig
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWireTelemetryPushState
import com.example.aura.meshwire.MeshWireTelemetryToRadioEncoder
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.settingsFeedbackMessageColor
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

private data class TelemetryIntervalOption(val seconds: UInt, val labelRu: String)

private val INTERVAL_PRESETS: List<TelemetryIntervalOption> = listOf(
    TelemetryIntervalOption(0u, "выкл"),
    TelemetryIntervalOption(60u, "1 мин"),
    TelemetryIntervalOption(120u, "2 мин"),
    TelemetryIntervalOption(300u, "5 мин"),
    TelemetryIntervalOption(600u, "10 мин"),
    TelemetryIntervalOption(900u, "15 мин"),
    TelemetryIntervalOption(1800u, "30 мин"),
    TelemetryIntervalOption(3600u, "1 ч"),
    TelemetryIntervalOption(7200u, "2 ч"),
    TelemetryIntervalOption(14400u, "4 ч"),
    TelemetryIntervalOption(86400u, "24 ч"),
)

private fun nearestInterval(seconds: UInt): TelemetryIntervalOption {
    val match = INTERVAL_PRESETS.firstOrNull { it.seconds == seconds }
    if (match != null) return match
    return INTERVAL_PRESETS.minBy { abs(it.seconds.toLong() - seconds.toLong()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetrySettingsContent(
    padding: PaddingValues,
    deviceAddress: String? = null,
    nodeId: String = "",
    bootstrap: MeshWireTelemetryPushState? = null,
    onBootstrapConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(MeshWireTelemetryPushState.initial()) }
    var baseline by remember { mutableStateOf(MeshWireTelemetryPushState.initial()) }
    var loadHint by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableStateOf<Int?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var deviceMenu by remember { mutableStateOf(false) }
    var envMenu by remember { mutableStateOf(false) }
    var airMenu by remember { mutableStateOf(false) }
    var powerMenu by remember { mutableStateOf(false) }
    var healthMenu by remember { mutableStateOf(false) }

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
            state = boot
            baseline = boot
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
        fetchMeshWireTelemetryConfig(
            context.applicationContext,
            a,
            onSyncProgress = { loadProgress = it },
            localNodeNum = nodeNum,
        ) { s, err ->
            if (s != null) {
                state = s
                baseline = s
                loadHint = null
                loadProgress = null
                a.let { MeshNodeSyncMemoryStore.putTelemetry(it, s) }
            } else {
                loadProgress = null
                loadHint = err?.takeIf { it.isNotBlank() }
                    ?: "Не удалось прочитать настройки телеметрии по BLE."
            }
        }
    }

    val deviceSel = remember(state.deviceUpdateIntervalSecs) { nearestInterval(state.deviceUpdateIntervalSecs) }
    val envSel = remember(state.environmentUpdateIntervalSecs) { nearestInterval(state.environmentUpdateIntervalSecs) }
    val airSel = remember(state.airQualityIntervalSecs) { nearestInterval(state.airQualityIntervalSecs) }
    val powerSel = remember(state.powerUpdateIntervalSecs) { nearestInterval(state.powerUpdateIntervalSecs) }
    val healthSel = remember(state.healthUpdateIntervalSecs) { nearestInterval(state.healthUpdateIntervalSecs) }

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
                    "Настройка телеметрии",
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
                        telemToggleRow(
                            "Отправлять телеметрию устройства",
                            state.deviceTelemetryEnabled,
                        ) { state = state.copy(deviceTelemetryEnabled = it) }
                        Text(
                            "Включите/выключите модуль телеметрии устройства, чтобы отправлять показатели в сеть. " +
                                "Это номинальные значения. Перегруженные сети будут автоматически масштабироваться " +
                                "на более длительные интервалы в зависимости от количества подключенных узлов.",
                            color = Mst.muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        intervalDropdown(
                            label = "Интервал обновления метрик устройства",
                            selected = deviceSel,
                            expanded = deviceMenu,
                            onExpandedChange = { deviceMenu = it },
                            onSelect = { state = state.copy(deviceUpdateIntervalSecs = it.seconds) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Модуль метрик окружения включен",
                            state.environmentMeasurementEnabled,
                        ) { state = state.copy(environmentMeasurementEnabled = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        intervalDropdown(
                            label = "Интервал обновления метрик среды",
                            selected = envSel,
                            expanded = envMenu,
                            onExpandedChange = { envMenu = it },
                            onSelect = { state = state.copy(environmentUpdateIntervalSecs = it.seconds) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Показатели окружения на экране включены",
                            state.environmentScreenEnabled,
                        ) { state = state.copy(environmentScreenEnabled = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Использовать метрику окружения в Fahrenheit",
                            state.environmentDisplayFahrenheit,
                        ) { state = state.copy(environmentDisplayFahrenheit = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Модуль измерения качества воздуха включен",
                            state.airQualityEnabled,
                        ) { state = state.copy(airQualityEnabled = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        intervalDropdown(
                            label = "Интервал обновления данных качества воздуха",
                            selected = airSel,
                            expanded = airMenu,
                            onExpandedChange = { airMenu = it },
                            onSelect = { state = state.copy(airQualityIntervalSecs = it.seconds) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Показатели качества воздуха на экране",
                            state.airQualityScreenEnabled,
                        ) { state = state.copy(airQualityScreenEnabled = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Модуль метрик питания включен",
                            state.powerMeasurementEnabled,
                        ) { state = state.copy(powerMeasurementEnabled = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        intervalDropdown(
                            label = "Интервал обновления метрик питания",
                            selected = powerSel,
                            expanded = powerMenu,
                            onExpandedChange = { powerMenu = it },
                            onSelect = { state = state.copy(powerUpdateIntervalSecs = it.seconds) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Включить метрики питания на экране",
                            state.powerScreenEnabled,
                        ) { state = state.copy(powerScreenEnabled = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Модуль метрик здоровья включен",
                            state.healthMeasurementEnabled,
                        ) { state = state.copy(healthMeasurementEnabled = it) }
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        intervalDropdown(
                            label = "Интервал обновления метрик здоровья",
                            selected = healthSel,
                            expanded = healthMenu,
                            onExpandedChange = { healthMenu = it },
                            onSelect = { state = state.copy(healthUpdateIntervalSecs = it.seconds) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        telemToggleRow(
                            "Метрики здоровья на экране",
                            state.healthScreenEnabled,
                        ) { state = state.copy(healthScreenEnabled = it) }
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
                    val payloads = MeshWireTelemetryToRadioEncoder.encodeTelemetrySetModuleConfigTransaction(state, dev)
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
                            addr.let { MeshNodeSyncMemoryStore.putTelemetry(it, saved) }
                            scope.launch {
                                delay(1_000)
                                fetchMeshWireTelemetryConfig(
                                    context.applicationContext,
                                    addr,
                                    onSyncProgress = null,
                                    localNodeNum = nodeNum,
                                ) { s, _ ->
                                    if (s != null) {
                                        state = s
                                        baseline = s.copy()
                                        MeshNodeSyncMemoryStore.putTelemetry(addr, s)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun intervalDropdown(
    label: String,
    selected: TelemetryIntervalOption,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (TelemetryIntervalOption) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = selected.labelRu,
                onValueChange = {},
                readOnly = true,
                label = { Text(label, color = Mst.accent) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                shape = FieldShape,
                colors = telemFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                containerColor = Mst.cancelBtn,
            ) {
                INTERVAL_PRESETS.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.labelRu, color = Mst.text) },
                        onClick = {
                            onSelect(opt)
                            onExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun telemToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Mst.text, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
private fun telemFieldColors() = OutlinedTextFieldDefaults.colors(
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
