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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.R
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireExternalNotificationConfig
import com.example.aura.meshwire.MeshWireExternalNotificationPushState
import com.example.aura.meshwire.MeshWireExternalNotificationToRadioEncoder
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.settingsFeedbackMessageColor
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalNotificationsSettingsContent(
    padding: PaddingValues,
    deviceAddress: String? = null,
    nodeId: String = "",
    bootstrap: MeshWireExternalNotificationPushState? = null,
    onBootstrapConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()

    var nodeState by remember { mutableStateOf(MeshWireExternalNotificationPushState.initial()) }
    var baseline by remember { mutableStateOf(MeshWireExternalNotificationPushState.initial()) }
    var loadHint by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableStateOf<Int?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }
    var skipNextRemoteFetch by remember { mutableStateOf(false) }

    val unsetLabel = stringResource(R.string.ext_notif_value_unset)
    val gpioOptions = remember {
        (0u..39u).map { pin -> pin to "Pin ${pin.toInt()}" }
    }
    val outputMsOptions = remember(unsetLabel) {
        listOf(
            0u to unsetLabel,
            100u to "100 ms",
            250u to "250 ms",
            500u to "500 ms",
            750u to "750 ms",
            1000u to "1000 ms",
            1500u to "1500 ms",
            2000u to "2000 ms",
            3000u to "3000 ms",
            5000u to "5000 ms",
            10000u to "10000 ms",
        )
    }
    val nagOptions = remember(unsetLabel) {
        listOf(
            0u to unsetLabel,
            5u to "5 s",
            10u to "10 s",
            15u to "15 s",
            20u to "20 s",
            30u to "30 s",
            45u to "45 s",
            60u to "60 s",
            90u to "90 s",
            120u to "120 s",
            180u to "180 s",
            240u to "240 s",
            300u to "300 s",
        )
    }

    LaunchedEffect(deviceAddress, bootstrap) {
        val a = addr
        val boot = bootstrap
        if (a == null) {
            loadHint = context.getString(R.string.notifications_node_load_need_device)
            loadProgress = null
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        if (boot != null) {
            nodeState = boot
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
        fetchMeshWireExternalNotificationConfig(
            context.applicationContext,
            a,
            onSyncProgress = { loadProgress = it },
            localNodeNum = nodeNum,
        ) { s, err ->
            if (s != null) {
                nodeState = s
                baseline = s
                loadHint = null
                loadProgress = null
                MeshNodeSyncMemoryStore.putExternalNotification(a, s)
            } else {
                loadProgress = null
                loadHint = err?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.notifications_node_sync_failed)
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
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                loadProgress?.let { p ->
                    SettingsTabLoadProgressBar(
                        percent = p,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                loadHint?.let {
                    Text(
                        it,
                        color = if (it == context.getString(R.string.notifications_node_load_need_device)) {
                            Mst.muted
                        } else {
                            Color(0xFFFF8A80)
                        },
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.ext_notif_section_main),
                    color = Mst.muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    extSwitchRow(
                        label = stringResource(R.string.ext_notif_master_enabled),
                        checked = nodeState.enabled,
                        onChecked = { nodeState = nodeState.copy(enabled = it) },
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.ext_notif_section_message),
                    color = Mst.muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column {
                        extSwitchRow(
                            label = stringResource(R.string.ext_notif_msg_led),
                            checked = nodeState.alertMessage,
                            onChecked = { nodeState = nodeState.copy(alertMessage = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard)
                        extSwitchRow(
                            label = stringResource(R.string.ext_notif_msg_sound),
                            checked = nodeState.alertMessageBuzzer,
                            onChecked = { nodeState = nodeState.copy(alertMessageBuzzer = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard)
                        extSwitchRow(
                            label = stringResource(R.string.ext_notif_msg_vibra),
                            checked = nodeState.alertMessageVibra,
                            onChecked = { nodeState = nodeState.copy(alertMessageVibra = it) },
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.ext_notif_section_alert),
                    color = Mst.muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column {
                        extSwitchRow(
                            label = stringResource(R.string.ext_notif_alert_led),
                            checked = nodeState.alertBell,
                            onChecked = { nodeState = nodeState.copy(alertBell = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard)
                        extSwitchRow(
                            label = stringResource(R.string.ext_notif_alert_buzzer),
                            checked = nodeState.alertBellBuzzer,
                            onChecked = { nodeState = nodeState.copy(alertBellBuzzer = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard)
                        extSwitchRow(
                            label = stringResource(R.string.ext_notif_alert_vibra),
                            checked = nodeState.alertBellVibra,
                            onChecked = { nodeState = nodeState.copy(alertBellVibra = it) },
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.ext_notif_section_advanced),
                    color = Mst.muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        UIntDropdownRow(
                            label = stringResource(R.string.ext_notif_gpio_led),
                            options = gpioOptions,
                            value = nodeState.output,
                            onSelect = { nodeState = nodeState.copy(output = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, modifier = Modifier.padding(horizontal = 12.dp))
                        UIntDropdownRow(
                            label = stringResource(R.string.ext_notif_gpio_buzzer),
                            options = gpioOptions,
                            value = nodeState.outputBuzzer,
                            onSelect = { nodeState = nodeState.copy(outputBuzzer = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, modifier = Modifier.padding(horizontal = 12.dp))
                        UIntDropdownRow(
                            label = stringResource(R.string.ext_notif_gpio_vibra),
                            options = gpioOptions,
                            value = nodeState.outputVibra,
                            onSelect = { nodeState = nodeState.copy(outputVibra = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, modifier = Modifier.padding(horizontal = 12.dp))
                        UIntDropdownRow(
                            label = stringResource(R.string.ext_notif_output_ms),
                            options = outputMsOptions,
                            value = nodeState.outputMs,
                            onSelect = { nodeState = nodeState.copy(outputMs = it) },
                        )
                        HorizontalDivider(color = Mst.dividerInCard, modifier = Modifier.padding(horizontal = 12.dp))
                        UIntDropdownRow(
                            label = stringResource(R.string.ext_notif_nag_timeout),
                            options = nagOptions,
                            value = nodeState.nagTimeout,
                            onSelect = { nodeState = nodeState.copy(nagTimeout = it) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Mst.card),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    extSwitchRow(
                        label = stringResource(R.string.ext_notif_i2s_as_buzzer),
                        checked = nodeState.useI2sAsBuzzer,
                        onChecked = { nodeState = nodeState.copy(useI2sAsBuzzer = it) },
                    )
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
                    nodeState = baseline.copy()
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
            ) { Text(stringResource(R.string.action_cancel)) }

            Button(
                onClick = {
                    if (addr == null) {
                        actionHint = context.getString(R.string.notifications_err_no_ble)
                        return@Button
                    }
                    if (nodeNum == null) {
                        actionHint = context.getString(R.string.notifications_err_no_node_id)
                        return@Button
                    }
                    saving = true
                    actionHint = null
                    val dev = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = nodeNum)
                    val payloads =
                        MeshWireExternalNotificationToRadioEncoder.encodeExternalNotificationSetModuleConfigTransaction(
                            nodeState,
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
                            actionHint = context.getString(R.string.notifications_saved_node)
                            val saved = nodeState.copy()
                            nodeState = saved
                            baseline = saved.copy()
                            MeshNodeSyncMemoryStore.putExternalNotification(addr, saved)
                            scope.launch {
                                delay(1_000)
                                fetchMeshWireExternalNotificationConfig(
                                    context.applicationContext,
                                    addr,
                                    onSyncProgress = null,
                                    localNodeNum = nodeNum,
                                ) { s, _ ->
                                    if (s != null) {
                                        nodeState = s
                                        baseline = s.copy()
                                        MeshNodeSyncMemoryStore.putExternalNotification(addr, s)
                                    }
                                }
                            }
                        } else {
                            actionHint = context.getString(R.string.notifications_save_err, err ?: "BLE")
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
                Text(
                    if (saving) stringResource(R.string.notifications_saving) else stringResource(R.string.notifications_save_node),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun extSwitchRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Mst.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mst.accent,
                checkedTrackColor = Mst.accent.copy(alpha = 0.45f),
                uncheckedThumbColor = Mst.switchThumbUnchecked,
                uncheckedTrackColor = Mst.switchTrackUnchecked,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UIntDropdownRow(
    label: String,
    options: List<Pair<UInt, String>>,
    value: UInt,
    onSelect: (UInt) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val labelText = options.firstOrNull { it.first == value }?.second ?: options.first().second
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = labelText,
                onValueChange = {},
                readOnly = true,
                label = { Text(label, color = Mst.accent, fontSize = 13.sp) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                shape = FieldShape,
                colors = extFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = Mst.cancelBtn,
            ) {
                options.forEach { (u, title) ->
                    DropdownMenuItem(
                        text = { Text(title, color = Mst.text) },
                        onClick = {
                            onSelect(u)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun extFieldColors() = OutlinedTextFieldDefaults.colors(
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
