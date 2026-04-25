package com.example.aura.ui.vip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.security.NodeAuthStore
import com.example.aura.security.VipExtensionCodeRedeemer
import com.example.aura.security.rememberBruteForceGuard
import kotlinx.coroutines.delay

/**
 * Полноэкранный оверлей: показывается, когда VIP-таймер истёк.
 *
 * Поле принимает одноразовый код продления VIP (см. [VipExtensionCodeRedeemer]).
 * На неверные коды и попытки подбора действует прогрессивная блокировка
 * ([com.example.aura.security.BruteForceGuard], scope `vip_extension`) —
 * счётчик общий с [VipExtensionRedeemDialog] в активном режиме.
 *
 * Кнопка «Отмена» закрывает оверлей, но приложение остаётся в ограниченном режиме.
 */
@Composable
fun VipExpiredOverlay(
    onPasswordAccepted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var passwordField by remember { mutableStateOf(TextFieldValue("")) }
    var attemptFailed by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("Неверный код") }
    var showHelpDialog by remember { mutableStateOf(false) }
    var successDaysApplied by remember { mutableStateOf<Int?>(null) }
    val guard = rememberBruteForceGuard(scope = "vip_extension")
    // Разный заголовок для демо (пользователю никогда не выдавался VIP) и для истёкшего VIP.
    val isDemoMode = remember { !VipAccessPreferences.isVipEverGranted(context) }
    val overlayTitle = if (isDemoMode) "Демо-режим" else "VIP тариф исчерпан"
    val overlayMessage = if (isDemoMode) {
        "Приложение работает в демо-режиме: возможности ограничены. Чтобы активировать VIP-тариф, введите код"
    } else {
        "VIP тариф исчерпан, возможности приложения ограничены. Для продления VIP тарифа введите код"
    }

    successDaysApplied?.let { days ->
        VipExtensionSuccessDialog(
            daysApplied = days,
            onDismiss = {
                successDaysApplied = null
                onPasswordAccepted()
            },
        )
    }

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
            textContentColor = Color(0xFFDDE8F0),
        )
    }

    Dialog(
        onDismissRequest = { /* нельзя закрыть касанием вне окна */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050A12))
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = overlayTitle,
                    color = Color(0xFFFFC107),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = overlayMessage,
                    color = Color(0xFFE0E4EA),
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = passwordField,
                    onValueChange = {
                        passwordField = it
                        attemptFailed = false
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Код") },
                    supportingText = {
                        when {
                            guard.isLocked -> Text(
                                "Слишком много попыток. Повторите через ${guard.remainingLabel}",
                                color = Color(0xFFFFC107),
                            )
                            attemptFailed -> Text(errorText, color = Color(0xFFFF5252))
                        }
                    },
                    isError = attemptFailed,
                    enabled = !guard.isLocked,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F1927),
                        unfocusedContainerColor = Color(0xFF0F1927),
                        disabledContainerColor = Color(0xFF0F1927),
                        errorContainerColor = Color(0xFF0F1927),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF9FB3C8),
                        unfocusedLabelColor = Color(0xFF9FB3C8),
                        cursorColor = Color(0xFF00D4FF),
                    ),
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (guard.isLocked) {
                            attemptFailed = true
                            errorText = "Слишком много попыток. Повторите через ${guard.remainingLabel}"
                            return@Button
                        }
                        val raw = passwordField.text.trim()
                        val nodeId = NodeAuthStore.loadStoredIdentity(context)?.nodeId.orEmpty()
                        if (nodeId.isBlank()) {
                            attemptFailed = true
                            errorText = "Сначала авторизуйтесь в узле"
                            return@Button
                        }
                        when (val r = VipExtensionCodeRedeemer.redeem(context, nodeId, raw)) {
                            is VipExtensionCodeRedeemer.RedeemResult.Success -> {
                                guard.reset(context)
                                attemptFailed = false
                                passwordField = TextFieldValue("")
                                successDaysApplied = r.daysApplied
                            }
                            VipExtensionCodeRedeemer.RedeemResult.AlreadyUsed -> {
                                // Не засчитываем в счётчик подбора: код технически валиден.
                                attemptFailed = true
                                errorText = "Этот код уже был использован"
                            }
                            VipExtensionCodeRedeemer.RedeemResult.WrongNodeOrTampered -> {
                                guard.registerFailure(context)
                                attemptFailed = true
                                errorText = if (guard.isLocked) {
                                    "Слишком много попыток. Повторите через ${guard.remainingLabel}"
                                } else {
                                    "Код не подходит для этого узла"
                                }
                            }
                            VipExtensionCodeRedeemer.RedeemResult.InvalidFormat -> {
                                // Пустой/недозаполненный ввод — обычная опечатка, не подбор.
                                attemptFailed = true
                                errorText = "Неверный формат кода"
                            }
                        }
                    },
                    enabled = !guard.isLocked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D4FF),
                        contentColor = Color(0xFF001018),
                    ),
                ) {
                    Text("Продлить", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Отмена", color = Color(0xFFB0BEC5))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0D1A2E))
                    .drawBehind {
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = 0.55f),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showHelpDialog = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "?",
                    color = Color(0xFF00D4FF),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Диалог VIP-таймера: только 10 секунд и подтверждение. Показывается при 5 тапах по логотипу на главном экране. */
@Composable
fun VipTimerSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color(0xFF0F1927),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCFD8E3),
        title = { Text("VIP-таймер", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Установить VIP на 10 секунд?",
                color = Color(0xFFB0BEC5),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Подтвердить", color = Color(0xFF00D4FF), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color(0xFFB0BEC5))
            }
        },
    )
}

/**
 * Тикающий флаг «VIP истёк» — перечитывает [VipAccessPreferences.getExpiresAtMs] и текущее время каждые 500 мс.
 */
@Composable
fun rememberVipRestricted(): Boolean {
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            restricted = VipAccessPreferences.isVipInRestrictedMode(context)
            delay(500L)
        }
    }
    return restricted
}
