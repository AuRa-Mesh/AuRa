package com.example.aura.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshLocationPreferences
import com.example.aura.bluetooth.MeshPhoneLocationToMeshSender
import com.example.aura.bluetooth.fetchMeshWirePositionConfig
import com.example.aura.meshwire.MeshWireFixedIntervals
import com.example.aura.meshwire.MeshWireGpsModeOptions
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.MeshWirePositionFlags
import com.example.aura.meshwire.MeshWirePositionPushState
import com.example.aura.meshwire.MeshWirePositionToRadioEncoder
import com.example.aura.ui.LocalNotifyNodeConfigWrite
import com.example.aura.ui.settingsFeedbackMessageColor
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

private val CardShape = RoundedCornerShape(16.dp)
private val FieldShape = RoundedCornerShape(12.dp)

private val PIN_PRESETS: List<UInt> = (0..48).map { it.toUInt() }

private fun Double?.eqEpsilon(other: Double?): Boolean {
    if (this == null && other == null) return true
    if (this == null || other == null) return false
    return abs(this - other) < 1e-7
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsContent(
    padding: PaddingValues,
    deviceAddress: String? = null,
    nodeId: String = "",
    bootstrap: MeshWirePositionPushState? = null,
    onBootstrapConsumed: () -> Unit = {},
    /** Как `node` в типичном mesh-клиенте PositionConfigScreen — начальные координаты для fixed (из NodeDB). */
    fixedSeedLat: Double? = null,
    fixedSeedLon: Double? = null,
    fixedSeedAltMeters: Int? = null,
) {
    val context = LocalContext.current
    val notifyNodeConfigWrite = LocalNotifyNodeConfigWrite.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(MeshWireFixedIntervals.sanitizeLikeMeshWireApp(MeshWirePositionPushState.initial())) }
    var baseline by remember { mutableStateOf(MeshWireFixedIntervals.sanitizeLikeMeshWireApp(MeshWirePositionPushState.initial())) }
    var loadHint by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableStateOf<Int?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val seedLat = remember(fixedSeedLat) { fixedSeedLat ?: 0.0 }
    val seedLon = remember(fixedSeedLon) { fixedSeedLon ?: 0.0 }
    val seedAlt = remember(fixedSeedAltMeters) { fixedSeedAltMeters ?: 0 }

    var fixedLatText by remember { mutableStateOf("") }
    var fixedLonText by remember { mutableStateOf("") }
    var fixedAltText by remember { mutableStateOf("") }

    var broadcastMenu by remember { mutableStateOf(false) }
    var smartIntMenu by remember { mutableStateOf(false) }
    var gpsModeMenu by remember { mutableStateOf(false) }
    var gpsPollMenu by remember { mutableStateOf(false) }
    var rxMenu by remember { mutableStateOf(false) }
    var txMenu by remember { mutableStateOf(false) }
    var enMenu by remember { mutableStateOf(false) }

    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
    val nodeNum = remember(nodeId) { MeshWireNodeNum.parseToUInt(nodeId) }

    var skipNextRemoteFetch by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (!granted) {
                actionHint = "Нужно разрешение на геолокацию"
                return@rememberLauncherForActivityResult
            }
            val loc = MeshPhoneLocationToMeshSender.lastKnownLocationOrNull(context.applicationContext)
            if (loc == null) {
                actionHint = "Нет последней точки GPS"
                return@rememberLauncherForActivityResult
            }
            fixedLatText = loc.latitude.toString()
            fixedLonText = loc.longitude.toString()
            fixedAltText = if (loc.hasAltitude()) loc.altitude.toInt().toString() else ""
            actionHint = null
        },
    )

    fun applyLoaded(s: MeshWirePositionPushState) {
        val sanitized = MeshWireFixedIntervals.sanitizeLikeMeshWireApp(s)
        state = sanitized
        baseline = sanitized
        val sl = fixedSeedLat ?: 0.0
        val so = fixedSeedLon ?: 0.0
        val sa = fixedSeedAltMeters ?: 0
        fixedLatText = sl.toString()
        fixedLonText = so.toString()
        fixedAltText = sa.toString()
        loadHint = null
        loadProgress = null
        actionHint = null
    }

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
            applyLoaded(boot)
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
        fetchMeshWirePositionConfig(
            context.applicationContext,
            a,
            onSyncProgress = { loadProgress = it },
            localNodeNum = nodeNum,
        ) { s, err ->
            if (s != null) {
                applyLoaded(s)
                loadProgress = null
            } else {
                loadProgress = null
                loadHint = err?.takeIf { it.isNotBlank() }
                    ?: "Не удалось прочитать настройки местоположения по BLE."
            }
        }
    }

    val broadcastSelLabel = remember(state.positionBroadcastSecs) {
        MeshWireFixedIntervals.labelForBroadcast(state.positionBroadcastSecs)
    }
    val smartIntSelLabel = remember(state.broadcastSmartMinimumIntervalSecs) {
        MeshWireFixedIntervals.labelForSmartMinimum(state.broadcastSmartMinimumIntervalSecs)
    }
    val gpsPollSelLabel = remember(state.gpsUpdateIntervalSecs) {
        MeshWireFixedIntervals.labelForGpsUpdate(state.gpsUpdateIntervalSecs)
    }
    val gpsModeSel = remember(state.gpsModeWire) {
        MeshWireGpsModeOptions.findByWire(state.gpsModeWire)
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
                Text(
                    "Пакет позиции",
                    color = Mst.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
                                expanded = broadcastMenu,
                                onExpandedChange = { broadcastMenu = it },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = broadcastSelLabel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Период трансляции", color = Mst.accent) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = broadcastMenu)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                        .fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = locFieldColors(),
                                    singleLine = true,
                                )
                                ExposedDropdownMenu(
                                    expanded = broadcastMenu,
                                    onDismissRequest = { broadcastMenu = false },
                                    containerColor = Mst.cancelBtn,
                                ) {
                                    MeshWireFixedIntervals.POSITION_BROADCAST.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.labelRu, color = Mst.text) },
                                            onClick = {
                                                state = state.copy(positionBroadcastSecs = opt.seconds)
                                                broadcastMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            Text(
                                "Максимальный интервал без передачи позиций узлов (как в типичном mesh-клиенте).",
                                color = Mst.muted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Умная позиция", color = Mst.text, fontSize = 16.sp)
                            Switch(
                                checked = state.positionBroadcastSmartEnabled,
                                onCheckedChange = { state = state.copy(positionBroadcastSmartEnabled = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Mst.accent,
                                    checkedTrackColor = Mst.accent.copy(alpha = 0.45f),
                                    uncheckedThumbColor = Mst.switchThumbUnchecked,
                                    uncheckedTrackColor = Mst.switchTrackUnchecked,
                                ),
                            )
                        }

                        if (state.positionBroadcastSmartEnabled) {
                            HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))

                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = smartIntMenu,
                                    onExpandedChange = { smartIntMenu = it },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    OutlinedTextField(
                                        value = smartIntSelLabel,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Минимальный интервал", color = Mst.accent) },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = smartIntMenu)
                                        },
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                            .fillMaxWidth(),
                                        shape = FieldShape,
                                        colors = locFieldColors(),
                                        singleLine = true,
                                    )
                                    ExposedDropdownMenu(
                                        expanded = smartIntMenu,
                                        onDismissRequest = { smartIntMenu = false },
                                        containerColor = Mst.cancelBtn,
                                    ) {
                                        MeshWireFixedIntervals.SMART_BROADCAST_MINIMUM.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt.labelRu, color = Mst.text) },
                                                onClick = {
                                                    state = state.copy(
                                                        broadcastSmartMinimumIntervalSecs = opt.seconds,
                                                    )
                                                    smartIntMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Минимальный интервал между отправками при умной позиции (mesh).",
                                    color = Mst.muted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }

                            HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))

                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                OutlinedTextField(
                                    value = state.broadcastSmartMinimumDistance.toString(),
                                    onValueChange = { t ->
                                        if (t.isEmpty() || t.all { it.isDigit() }) {
                                            if (t.isEmpty()) {
                                                state = state.copy(broadcastSmartMinimumDistance = 0u)
                                            } else {
                                                t.toUIntOrNull()?.let { u ->
                                                    state = state.copy(broadcastSmartMinimumDistance = u)
                                                }
                                            }
                                        }
                                    },
                                    label = { Text("Минимальная дистанция (м)", color = Mst.accent) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = locFieldColors(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Text(
                                    "Минимальное смещение в метрах для умной трансляции.",
                                    color = Mst.muted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "GPS устройства",
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
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Фиксированное положение", color = Mst.text, fontSize = 16.sp)
                            Switch(
                                checked = state.fixedPosition,
                                onCheckedChange = { state = state.copy(fixedPosition = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Mst.accent,
                                    checkedTrackColor = Mst.accent.copy(alpha = 0.45f),
                                    uncheckedThumbColor = Mst.switchThumbUnchecked,
                                    uncheckedTrackColor = Mst.switchTrackUnchecked,
                                ),
                            )
                        }

                        if (state.fixedPosition) {
                            HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                OutlinedTextField(
                                    value = fixedLatText,
                                    onValueChange = { fixedLatText = it },
                                    label = { Text("Широта", color = Mst.accent) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = FieldShape,
                                    colors = locFieldColors(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                OutlinedTextField(
                                    value = fixedLonText,
                                    onValueChange = { fixedLonText = it },
                                    label = { Text("Долгота", color = Mst.accent) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = FieldShape,
                                    colors = locFieldColors(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                OutlinedTextField(
                                    value = fixedAltText,
                                    onValueChange = { t ->
                                        if (t.isEmpty() || t.all { it.isDigit() || it == '-' }) {
                                            fixedAltText = t
                                        }
                                    },
                                    label = { Text("Высота (м)", color = Mst.accent) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = FieldShape,
                                    colors = locFieldColors(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    } else {
                                        val loc = MeshPhoneLocationToMeshSender.lastKnownLocationOrNull(
                                            context.applicationContext,
                                        )
                                        if (loc == null) {
                                            actionHint = "Нет последней точки GPS"
                                        } else {
                                            fixedLatText = loc.latitude.toString()
                                            fixedLonText = loc.longitude.toString()
                                            fixedAltText = if (loc.hasAltitude()) loc.altitude.toInt().toString() else ""
                                            actionHint = null
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Text("Позиция с телефона", color = Mst.accent)
                            }
                        } else {
                            HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))

                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = gpsModeMenu,
                                    onExpandedChange = { gpsModeMenu = it },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    OutlinedTextField(
                                        value = gpsModeSel.second,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = {
                                            Text(
                                                "Режим GPS",
                                                color = Mst.accent,
                                            )
                                        },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = gpsModeMenu)
                                        },
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                            .fillMaxWidth(),
                                        shape = FieldShape,
                                        colors = locFieldColors(),
                                        singleLine = true,
                                    )
                                    ExposedDropdownMenu(
                                        expanded = gpsModeMenu,
                                        onDismissRequest = { gpsModeMenu = false },
                                        containerColor = Mst.cancelBtn,
                                    ) {
                                        MeshWireGpsModeOptions.ALL.forEach { (wire, name) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        name,
                                                        color = Mst.text,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                },
                                                onClick = {
                                                    state = state.copy(gpsModeWire = wire)
                                                    gpsModeMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))

                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = gpsPollMenu,
                                    onExpandedChange = { gpsPollMenu = it },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    OutlinedTextField(
                                        value = gpsPollSelLabel,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Интервал обновления", color = Mst.accent) },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = gpsPollMenu)
                                        },
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                            .fillMaxWidth(),
                                        shape = FieldShape,
                                        colors = locFieldColors(),
                                        singleLine = true,
                                    )
                                    ExposedDropdownMenu(
                                        expanded = gpsPollMenu,
                                        onDismissRequest = { gpsPollMenu = false },
                                        containerColor = Mst.cancelBtn,
                                    ) {
                                        MeshWireFixedIntervals.GPS_UPDATE.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt.labelRu, color = Mst.text) },
                                                onClick = {
                                                    state = state.copy(gpsUpdateIntervalSecs = opt.seconds)
                                                    gpsPollMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Как часто опрашивать GPS, 0 — по умолчанию прошивки (часто ~30 с).",
                                    color = Mst.muted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Флаги позиции",
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
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            "Необязательные поля в пакете POSITION; больше полей — длиннее эфирное время.",
                            color = Mst.muted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        MeshWirePositionFlags.ALL.forEach { flag ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(flag.labelRu, color = Mst.text, fontSize = 15.sp)
                                Switch(
                                    checked = (state.positionFlags and flag.mask) != 0u,
                                    onCheckedChange = { on ->
                                        state = state.copy(
                                            positionFlags = MeshWirePositionFlags.withToggled(
                                                state.positionFlags,
                                                flag,
                                                on,
                                            ),
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Mst.accent,
                                        checkedTrackColor = Mst.accent.copy(alpha = 0.45f),
                                        uncheckedThumbColor = Mst.switchThumbUnchecked,
                                        uncheckedTrackColor = Mst.switchTrackUnchecked,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Расширенное GPS устройства",
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
                        gpioDropdown(
                            label = "GPIO приёма GPS",
                            expanded = rxMenu,
                            onExpandedChange = { rxMenu = it },
                            valuePin = state.rxGpio,
                            onSelect = { state = state.copy(rxGpio = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        gpioDropdown(
                            label = "GPIO передачи GPS",
                            expanded = txMenu,
                            onExpandedChange = { txMenu = it },
                            valuePin = state.txGpio,
                            onSelect = { state = state.copy(txGpio = it) },
                        )
                        HorizontalDivider(color = Mst.dividerOuter.copy(alpha = 0.5f))
                        gpioDropdown(
                            label = "GPIO EN GPS",
                            expanded = enMenu,
                            onExpandedChange = { enMenu = it },
                            valuePin = state.gpsEnGpio,
                            onSelect = { state = state.copy(gpsEnGpio = it) },
                        )
                    }
                }
            }

            item {
                loadProgress?.let { p ->
                    SettingsTabLoadProgressBar(percent = p)
                }
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
                    state = MeshWireFixedIntervals.sanitizeLikeMeshWireApp(baseline.copy())
                    fixedLatText = seedLat.toString()
                    fixedLonText = seedLon.toString()
                    fixedAltText = seedAlt.toString()
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
                    if (MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext)) {
                        actionHint = "Передача координат отключена: выключите «Скрыть координаты» в профиле узла"
                        Toast.makeText(
                            context.applicationContext,
                            "Передача координат отключена в профиле (узел → Местоположение)",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@Button
                    }
                    if (addr == null) {
                        actionHint = "Нет BLE-адреса"
                        return@Button
                    }
                    if (nodeNum == null) {
                        actionHint = "Нужен Node ID (!xxxxxxxx)"
                        return@Button
                    }
                    val latParsed = fixedLatText.toDoubleOrNull()
                    val lonParsed = fixedLonText.toDoubleOrNull()
                    if (state.fixedPosition) {
                        if (latParsed == null || lonParsed == null) {
                            actionHint = "Укажите широту и долготу"
                            return@Button
                        }
                        if (latParsed < -90.0 || latParsed > 90.0 || lonParsed < -180.0 || lonParsed > 180.0) {
                            actionHint = "Недопустимые координаты"
                            return@Button
                        }
                    }
                    saving = true
                    actionHint = null
                    val dev = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(destinationNodeNum = nodeNum)
                    val pre = ArrayList<ByteArray>()
                    if (!state.fixedPosition && baseline.fixedPosition) {
                        pre.add(MeshWireLoRaToRadioEncoder.encodeAdminRemoveFixedPositionToRadio(dev))
                    }
                    if (state.fixedPosition && latParsed != null && lonParsed != null) {
                        val altParsed = fixedAltText.trim().toIntOrNull()
                        val altCmp = altParsed ?: 0
                        val coordsDifferFromSeed =
                            !latParsed.eqEpsilon(seedLat) ||
                                !lonParsed.eqEpsilon(seedLon) ||
                                altCmp != seedAlt
                        if (coordsDifferFromSeed) {
                            pre.add(
                                MeshWireLoRaToRadioEncoder.encodeAdminSetFixedPositionToRadio(
                                    latitudeDeg = latParsed,
                                    longitudeDeg = lonParsed,
                                    altitudeMeters = altParsed,
                                    device = dev,
                                ),
                            )
                        }
                    }
                    val tx = MeshWirePositionToRadioEncoder.encodePositionSetConfigTransaction(state, dev)
                    val payloads = mutableListOf<ByteArray>()
                    payloads.addAll(pre)
                    payloads.addAll(tx)
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
                            state = MeshWireFixedIntervals.sanitizeLikeMeshWireApp(saved)
                            baseline = state.copy()
                            scope.launch {
                                delay(1_000)
                                fetchMeshWirePositionConfig(
                                    context.applicationContext,
                                    addr,
                                    onSyncProgress = null,
                                    localNodeNum = nodeNum,
                                ) { s, _ ->
                                    if (s != null) {
                                        applyLoaded(s)
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
private fun gpioDropdown(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    valuePin: UInt,
    onSelect: (UInt) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = "Pin ${valuePin.toInt()}",
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
                colors = locFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                containerColor = Mst.cancelBtn,
            ) {
                PIN_PRESETS.forEach { pin ->
                    DropdownMenuItem(
                        text = { Text("Pin ${pin.toInt()}", color = Mst.text) },
                        onClick = {
                            onSelect(pin)
                            onExpandedChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun locFieldColors() = OutlinedTextFieldDefaults.colors(
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
