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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.ui.settingsFeedbackMessageColor
import com.example.aura.bluetooth.fetchMeshWireLoRaConfig
import com.example.aura.meshwire.MeshWireLoRaConfigLogic
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireLoRaRegions
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireModemPreset
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

private const val HELP_REGION =
    "Регион, в котором вы будете использовать ваше радио."

private const val HELP_TEMPLATES =
    "Доступные пресеты модема, по умолчанию - Long Fast."

private const val HELP_BANDWIDTH =
    "Ширина канала в МГц (целое число). Особые значения, например 31 → 31.25 МГц, обрабатываются прошивкой как в типичном mesh-клиенте."

private const val HELP_SPREAD_FACTOR =
    "Число от 7 до 12: число чирпов на символ (2^SF). Только для режима без пресета."

private const val HELP_CODING_RATE =
    "Знаменатель кодовой скорости (например 5 для 4/5). Только для режима без пресета."

private const val HELP_PA_FAN =
    "Отключить вентилятор PA (если вывод RF95_FAN_EN задан в прошивке), как в config.proto."

private const val HELP_HOP_LIMIT =
    "Задаёт максимальное количество прыжков, по умолчанию – 3. Увеличение количества также увеличивает перегрузку и должно использоваться с осторожностью. Сообщения с 0 прыжков не будут получать подтверждения."

private const val HELP_CHANNEL_SLOT =
    "Рабочая частота вашего узла рассчитывается на основе региона, настроек модема и этого поля. При значении 0 интервал автоматически рассчитывается на основе названия основного канала и изменяется с публичного интервала по умолчанию. Вернитесь к публичному интервалу по умолчанию, если настроены частный основной и общедоступный дополнительный каналы."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoRaSettingsContent(
    padding: PaddingValues,
    deviceAddress: String? = null,
    nodeId: String = "",
    bootstrap: MeshWireLoRaPushState? = null,
    onBootstrapConsumed: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()

    var lora by remember { mutableStateOf(MeshWireLoRaPushState.initial()) }
    var baseline by remember { mutableStateOf(MeshWireLoRaPushState.initial()) }
    var loadHint by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableStateOf<Int?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var regionMenu by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    var hopMenu by remember { mutableStateOf(false) }

    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }

    var skipNextRemoteFetch by remember { mutableStateOf(false) }

    LaunchedEffect(addr, bootstrap) {
        val boot = bootstrap
        if (addr == null) {
            loadHint = "Привяжите устройство по Bluetooth."
            loadProgress = null
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        if (boot != null) {
            lora = boot
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
        fetchMeshWireLoRaConfig(
            context.applicationContext,
            addr,
            onSyncProgress = { loadProgress = it },
            localNodeNum = nodeNum,
        ) { state, err ->
            when {
                state != null -> {
                    lora = state
                    baseline = state.copy()
                    loadHint = null
                    loadProgress = null
                    MeshNodeSyncMemoryStore.putLora(addr, state)
                }
                else -> {
                    loadProgress = null
                    loadHint = err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить LoRa."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.bg)
            .padding(padding)
            .padding(horizontal = 16.dp)
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
                        text = hint,
                        color = Mst.muted.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            item {
                SectionTitle("Опции")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = regionMenu,
                                onExpandedChange = { regionMenu = it },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = lora.region.description,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Регион / Страна", color = Mst.muted) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionMenu)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = loraFieldColors(),
                                    singleLine = true,
                                )
                                ExposedDropdownMenu(
                                    expanded = regionMenu,
                                    onDismissRequest = { regionMenu = false },
                                ) {
                                    MeshWireLoRaRegions.ALL.forEach { r ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${r.description} (${r.code})",
                                                    color = Mst.text,
                                                )
                                            },
                                            onClick = {
                                                lora = lora.copy(region = r)
                                                regionMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            SupportingText(HELP_REGION)
                        }

                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)

                        CardSwitchRow(
                            label = "Использовать шаблон",
                            checked = lora.usePreset,
                            onCheckedChange = { lora = lora.copy(usePreset = it) },
                        )

                        if (lora.usePreset) {
                            HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = presetMenu,
                                    onExpandedChange = { presetMenu = it },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    OutlinedTextField(
                                        value = lora.modemPreset.menuTitle,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Шаблоны", color = Mst.muted) },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetMenu)
                                        },
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                            .fillMaxWidth(),
                                        shape = FieldShape,
                                        colors = loraFieldColors(),
                                        singleLine = true,
                                    )
                                    ExposedDropdownMenu(
                                        expanded = presetMenu,
                                        onDismissRequest = { presetMenu = false },
                                    ) {
                                        MeshWireModemPreset.UI_ORDER.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p.menuTitle, color = Mst.text) },
                                                onClick = {
                                                    lora = lora.copy(modemPreset = p)
                                                    presetMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                                SupportingText(HELP_TEMPLATES)
                            }
                        } else {
                            HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                OutlinedTextField(
                                    value = lora.bandwidthText,
                                    onValueChange = {
                                        lora = lora.copy(
                                            bandwidthText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(it),
                                        )
                                    },
                                    label = { Text("Полоса (MHz)", color = Mst.muted) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = FieldShape,
                                    colors = loraFieldColors(),
                                )
                                SupportingText(HELP_BANDWIDTH)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = lora.spreadFactorText,
                                    onValueChange = {
                                        lora = lora.copy(
                                            spreadFactorText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(it),
                                        )
                                    },
                                    label = { Text("Spread factor", color = Mst.muted) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = FieldShape,
                                    colors = loraFieldColors(),
                                )
                                SupportingText(HELP_SPREAD_FACTOR)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = lora.codingRateText,
                                    onValueChange = {
                                        lora = lora.copy(
                                            codingRateText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(it),
                                        )
                                    },
                                    label = { Text("Coding rate", color = Mst.muted) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = FieldShape,
                                    colors = loraFieldColors(),
                                )
                                SupportingText(HELP_CODING_RATE)
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle("Расширенные")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        CardSwitchRow(
                            label = "Игнорировать MQTT",
                            checked = lora.ignoreMqtt,
                            onCheckedChange = { lora = lora.copy(ignoreMqtt = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        CardSwitchRow(
                            label = "OK в MQTT",
                            checked = lora.configOkToMqtt,
                            onCheckedChange = { lora = lora.copy(configOkToMqtt = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        CardSwitchRow(
                            label = "Передача включена",
                            checked = lora.txEnabled,
                            onCheckedChange = { lora = lora.copy(txEnabled = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        CardSwitchRow(
                            label = "Переопределить рабочий цикл",
                            checked = lora.overrideDutyCycle,
                            onCheckedChange = { lora = lora.copy(overrideDutyCycle = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            ExposedDropdownMenuBox(
                                expanded = hopMenu,
                                onExpandedChange = { hopMenu = it },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = "${lora.hopLimit}",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Количество прыжков", color = Mst.muted) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = hopMenu)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = loraFieldColors(),
                                    singleLine = true,
                                )
                                ExposedDropdownMenu(
                                    expanded = hopMenu,
                                    onDismissRequest = { hopMenu = false },
                                ) {
                                    (MeshWireLoRaConfigLogic.HOP_LIMIT_MIN..MeshWireLoRaConfigLogic.HOP_LIMIT_MAX)
                                        .forEach { h ->
                                            DropdownMenuItem(
                                                text = { Text("$h", color = Mst.text) },
                                                onClick = {
                                                    lora = lora.copy(
                                                        hopLimit = MeshWireLoRaConfigLogic.clampHopLimit(h),
                                                    )
                                                    hopMenu = false
                                                },
                                            )
                                        }
                                }
                            }
                            SupportingText(HELP_HOP_LIMIT)
                        }
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            OutlinedTextField(
                                value = lora.channelNumText,
                                onValueChange = { t ->
                                    lora = lora.copy(
                                        channelNumText = MeshWireLoRaConfigLogic.sanitizeChannelNumInput(t),
                                    )
                                },
                                label = { Text("Частота слота", color = Mst.muted) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = FieldShape,
                                colors = loraFieldColors(),
                            )
                            SupportingText(HELP_CHANNEL_SLOT)
                        }
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        CardSwitchRow(
                            label = "Усиление RX (SX126x)",
                            checked = lora.sx126xRxBoostedGain,
                            onCheckedChange = { lora = lora.copy(sx126xRxBoostedGain = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            OutlinedTextField(
                                value = lora.overrideFrequencyMhzText,
                                onValueChange = {
                                    lora = lora.copy(overrideFrequencyMhzText = it.replace(',', '.'))
                                },
                                label = { Text("Переопределить частоту", color = Mst.muted) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = FieldShape,
                                colors = loraFieldColors(),
                            )
                        }
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            OutlinedTextField(
                                value = lora.txPowerDbmText,
                                onValueChange = { t ->
                                    lora = lora.copy(
                                        txPowerDbmText = MeshWireLoRaConfigLogic.sanitizeIntSigned(
                                            t,
                                            allowLeadingMinus = true,
                                        ),
                                    )
                                },
                                label = { Text("Мощность передатчика", color = Mst.muted) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = FieldShape,
                                colors = loraFieldColors(),
                            )
                        }
                        HorizontalDivider(color = Mst.dividerInCard, thickness = 1.dp)
                        CardSwitchRow(
                            label = "Отключить вентилятор PA",
                            checked = lora.paFanDisabled,
                            onCheckedChange = { lora = lora.copy(paFanDisabled = it) },
                        )
                        Column(
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        ) {
                            SupportingText(HELP_PA_FAN)
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
                    lora = baseline.copy()
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
                    val payloads = MeshWireLoRaToRadioEncoder.encodeLoraSetConfigTransaction(lora, dev)
                    MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
                        deviceAddress = addr,
                        payloads = payloads,
                        delayBetweenWritesMs = 235L,
                    ) { ok, err ->
                        saving = false
                        if (ok) {
                            notifyNodeConfigWrite?.invoke()
                            actionHint = "Сохранено на ноде"
                            baseline = lora.copy()
                            scope.launch {
                                delay(1_000)
                                fetchMeshWireLoRaConfig(
                                    context.applicationContext,
                                    addr,
                                    onSyncProgress = null,
                                    localNodeNum = nodeNum,
                                ) { s, e ->
                                    if (s != null) {
                                        lora = s
                                        baseline = s.copy()
                                        MeshNodeSyncMemoryStore.putLora(addr, s)
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
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Mst.text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        color = Mst.muted,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun CardSwitchRow(
    label: String,
    checked: Boolean,
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
private fun loraFieldColors() = OutlinedTextFieldDefaults.colors(
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
