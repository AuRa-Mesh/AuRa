package com.example.aura.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.NodeConnectionState
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.bluetooth.isMeshNodeBluetoothLinked
import com.example.aura.R
import com.example.aura.security.NodePasswordGenerator
import com.example.aura.security.rememberBruteForceGuard
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.aura.ui.components.MeshBackground
import com.example.aura.ui.components.MeshBluetoothScanDialog

private data class PasswordBleGattSnapshot(
    val state: NodeConnectionState,
    val nodeNum: UInt?,
    val targetAddress: String?,
    val meshAddress: String?,
)

private enum class PasswordSessionTransport { Ble, Wifi, Usb }

/** По строке сессии: BLE MAC, `TCP:…` или `USB:…`. */
private fun passwordSessionTransport(meshDeviceAddress: String?): PasswordSessionTransport {
    val key = meshDeviceAddress?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { MeshNodeSyncMemoryStore.normalizeKey(it) } ?: return PasswordSessionTransport.Ble
    return when {
        MeshDeviceTransport.isTcpAddress(key) -> PasswordSessionTransport.Wifi
        MeshDeviceTransport.isUsbAddress(key) -> PasswordSessionTransport.Usb
        else -> PasswordSessionTransport.Ble
    }
}

@Composable
fun PasswordScreen(
    nodeId: String = "",
    savedPassword: String = "",
    savedDeviceAddress: String? = null,
    onAuthenticated: (nodeId: String, password: String, deviceAddress: String?) -> Unit
) {
    var currentNodeId by remember { mutableStateOf(nodeId) }
    var passwordInput by remember { mutableStateOf(savedPassword) }
    var meshDeviceAddress by remember { mutableStateOf(savedDeviceAddress) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(nodeId, savedPassword, savedDeviceAddress) {
        currentNodeId = nodeId
        passwordInput = savedPassword
        meshDeviceAddress = savedDeviceAddress
        isError = false
    }
    var showHelpDialog by remember { mutableStateOf(false) }

    var showDeviceDialog by remember { mutableStateOf(false) }

    val ctx = LocalContext.current

    val isNodeConnected = remember(meshDeviceAddress, showDeviceDialog, currentNodeId) {
        isMeshNodeBluetoothLinked(ctx, meshDeviceAddress)
    }

    fun onNodeIdRowTap() {
        showDeviceDialog = true
    }

    MeshBluetoothScanDialog(
        visible = showDeviceDialog,
        onDismissRequest = { showDeviceDialog = false },
        sessionNodeId = currentNodeId,
        sessionMeshAddress = meshDeviceAddress,
        onSessionNodeIdChange = { currentNodeId = it },
        onSessionMeshAddressChange = { meshDeviceAddress = it },
        autoBleScanOnOpen = true,
    )

    // Актуальный Node ID с BLE-ноды при смене подключения (TCP/USB не трогаем).
    LaunchedEffect(Unit) {
        combine(
            NodeGattConnection.connectionState,
            NodeGattConnection.myNodeNum,
            snapshotFlow { NodeGattConnection.targetDevice?.address },
            snapshotFlow { meshDeviceAddress },
        ) { state, num, targetAddr, meshRaw ->
            PasswordBleGattSnapshot(state, num, targetAddr, meshRaw)
        }
            .distinctUntilChanged()
            .collect { snap ->
                val state = snap.state
                val num = snap.nodeNum
                val targetAddr = snap.targetAddress
                val meshRaw = snap.meshAddress
                if (state != NodeConnectionState.READY || num == null || num == 0u) return@collect
                val tgt = targetAddr ?: return@collect
                val normTgt = MeshNodeSyncMemoryStore.normalizeKey(tgt)
                val normMesh = meshRaw?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { MeshNodeSyncMemoryStore.normalizeKey(it) }
                if (normMesh != null) {
                    if (MeshDeviceTransport.isTcpAddress(normMesh) || MeshDeviceTransport.isUsbAddress(normMesh)) {
                        return@collect
                    }
                    if (normMesh != normTgt) return@collect
                }
                val hex = num.toString(16).padStart(8, '0').uppercase()
                if (currentNodeId != hex) currentNodeId = hex
            }
    }

    // Анимации
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulseAlpha"
    )
    val btRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing), RepeatMode.Restart
        ), label = "btRotation"
    )
    // Масштаб иконки BT в покое — мягкий пульс
    val btScale by infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "btScale"
    )

    val nodeRowActive = showDeviceDialog
    var nodeNotConnectedError by remember { mutableStateOf(false) }
    val guard = rememberBruteForceGuard(scope = "login")

    fun tryLogin() {
        if (!isNodeConnected) {
            nodeNotConnectedError = true
            return
        }
        nodeNotConnectedError = false
        if (guard.isLocked) {
            // Пользователь попал на защиту от перебора — сообщение рисуется ниже,
            // здесь просто не даём проверять пароль.
            isError = false
            return
        }
        if (NodePasswordGenerator.verify(currentNodeId, passwordInput)) {
            guard.reset(ctx)
            isError = false
            onAuthenticated(currentNodeId, passwordInput, meshDeviceAddress)
        } else {
            guard.registerFailure(ctx)
            isError = true
        }
    }

    // ── Диалог "?" ────────────────────────────────────────────────────────────
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Контакты", fontWeight = FontWeight.Bold) },
            text = { Text("По всем вопросам в ТГ @Aura_Mesh") },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("OK") }
            },
            containerColor = Color(0xFF0D1A2E),
            titleContentColor = Color(0xFF00D4FF),
            textContentColor = Color(0xFFDDE8F0)
        )
    }

    // ── Основной UI ───────────────────────────────────────────────────────────
    MeshBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp)
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Пульсирующий значок замка
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha * 0.15f),
                            radius = size.minDimension / 2 + 16.dp.toPx()
                        )
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha * 0.35f),
                            style = Stroke(width = 1.dp.toPx()),
                            radius = size.minDimension / 2 - 10.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF).copy(alpha = alpha),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "АВТОРИЗАЦИЯ",
                color = Color.White.copy(alpha = alpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp
            )
            Text(
                text = "узла Meshtastic",
                color = Color(0xFF00D4FF).copy(alpha = 0.6f),
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Блок Node ID — всё поле кликабельно ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = when {
                            nodeRowActive -> Color(0xFF00D4FF).copy(alpha = alpha)
                            currentNodeId.isNotEmpty() -> Color(0xFF4CAF50).copy(alpha = 0.6f)
                            else -> Color(0xFF00D4FF).copy(alpha = 0.35f)
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(Color(0xFF0D1A2E).copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onNodeIdRowTap()
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transport = passwordSessionTransport(meshDeviceAddress)
                val showBleSearchAnimation = nodeRowActive && transport == PasswordSessionTransport.Ble
                val iconVector: ImageVector = when {
                    showBleSearchAnimation -> Icons.Default.BluetoothSearching
                    transport == PasswordSessionTransport.Wifi -> Icons.Default.Wifi
                    transport == PasswordSessionTransport.Usb -> Icons.Default.Usb
                    else -> Icons.Default.Bluetooth
                }
                val iconDescription = when (transport) {
                    PasswordSessionTransport.Ble -> "Bluetooth"
                    PasswordSessionTransport.Wifi -> "Wi‑Fi"
                    PasswordSessionTransport.Usb -> "USB"
                }
                // Иконка транспорта: BLE (как раньше), Wi‑Fi или USB по строке сессии
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            if (!nodeRowActive) {
                                drawCircle(
                                    color = Color(0xFF00D4FF).copy(alpha = (btScale - 0.88f) / 0.24f * 0.12f),
                                    radius = 14.dp.toPx() * btScale
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = iconDescription,
                        tint = when {
                            nodeRowActive -> Color(0xFF00D4FF).copy(alpha = alpha)
                            currentNodeId.isNotEmpty() -> Color(0xFF4CAF50)
                            else -> Color(0xFF00D4FF).copy(alpha = 0.85f)
                        },
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                when {
                                    showBleSearchAnimation -> {
                                        rotationZ = btRotation
                                        scaleX = 1f; scaleY = 1f
                                    }
                                    else -> {
                                        scaleX = btScale
                                        scaleY = btScale
                                    }
                                }
                            }
                    )
                }

                if (currentNodeId.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val hex = currentNodeId.trim().removePrefix("!")
                            val toCopy = "!$hex"
                            cm.setPrimaryClip(ClipData.newPlainText("node id", toCopy))
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.msg_menu_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Копировать Node ID",
                            tint = Color(0xFF00D4FF).copy(alpha = alpha),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NODE ID",
                        color = Color(0xFF4A5A6A),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = when {
                            showDeviceDialog -> "Выберите устройство в окне…"
                            currentNodeId.isNotEmpty() -> "!$currentNodeId"
                            else -> "Нажмите для выбора способа"
                        },
                        color = when {
                            nodeRowActive -> Color(0xFF00D4FF).copy(alpha = alpha)
                            currentNodeId.isNotEmpty() -> Color(0xFF00D4FF)
                            else -> Color(0xFF4A5A6A)
                        },
                        fontSize = if (currentNodeId.isEmpty() && !nodeRowActive)
                            12.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (currentNodeId.isNotEmpty()) FontFamily.Monospace
                                     else FontFamily.Default,
                        letterSpacing = if (currentNodeId.isNotEmpty()) 1.sp else 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Индикатор статуса
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                nodeRowActive ->
                                    Color(0xFFFFAA00).copy(alpha = alpha)
                                currentNodeId.isNotEmpty() -> Color(0xFF4CAF50)
                                else -> Color(0xFFFF5252).copy(alpha = 0.6f)
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Разделитель
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF1E2D40)))
                Text("пароль", color = Color(0xFF2A3A4A), fontSize = 11.sp, letterSpacing = 2.sp)
                Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF1E2D40)))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Поле пароля
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it; isError = false },
                enabled = !guard.isLocked,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "XXXX-XXXX",
                        color = Color(0xFF2A3A4A),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color(0xFF4A5A6A)
                        )
                    }
                },
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { tryLogin() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00D4FF),
                    unfocusedBorderColor = Color(0xFF1E2D40),
                    errorBorderColor = Color(0xFFFF5252),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFCCDDEE),
                    cursorColor = Color(0xFF00D4FF),
                    focusedContainerColor = Color(0xFF0D1A2E),
                    unfocusedContainerColor = Color(0xFF0D1A2E),
                    errorContainerColor = Color(0xFF1A0D0D),
                    errorCursorColor = Color(0xFFFF5252)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            when {
                guard.isLocked -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Слишком много попыток. Повторите через ${guard.remainingLabel}",
                        color = Color(0xFFFFC107),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                isError -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Неверный пароль. Проверьте данные узла.",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Кнопка входа
            val loginEnabled = isNodeConnected && !guard.isLocked
            Button(
                onClick = { tryLogin() },
                enabled = loginEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (loginEnabled) Color(0xFF00D4FF) else Color(0xFF1E2D40),
                    contentColor = if (loginEnabled) Color(0xFF070B18) else Color(0xFFFF5252),
                    disabledContainerColor = Color(0xFF1E2D40),
                    disabledContentColor = Color(0xFFFF5252),
                )
            ) {
                val label = when {
                    !isNodeConnected -> "ПОДКЛЮЧИТЕ НОДУ"
                    guard.isLocked -> "ПОДОЖДИТЕ ${guard.remainingLabel}"
                    else -> "Введите пароль входа"
                }
                Text(
                    label,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 4.sp
                )
            }
        }

        // Кнопка "?" внизу
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D1A2E))
                .drawBehind {
                    drawCircle(
                        color = Color(0xFF00D4FF).copy(alpha = 0.55f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showHelpDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = Color(0xFF00D4FF), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        }
    }
}
