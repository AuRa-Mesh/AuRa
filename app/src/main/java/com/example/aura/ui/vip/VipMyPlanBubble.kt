package com.example.aura.ui.vip

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.progression.AuraGodNodeProfile
import com.example.aura.security.NodeAuthStore
import com.example.aura.security.VipExtensionCodeRedeemer
import com.example.aura.security.rememberBruteForceGuard
import kotlinx.coroutines.delay

/**
 * Бабл «Мой тариф» с живым обратным отсчётом формата `DD / HH / MM` до конца VIP-тарифа.
 *
 * Значения берутся из [VipAccessPreferences.expiresAtMsFlow]; бабл сам тикает раз в секунду,
 * поэтому вызывающему нужно только открыть его и закрывать по [onDismiss]. Если таймер уже
 * истёк ([VipAccessPreferences.isVipInRestrictedMode]) — показываем «Тариф исчерпан» вместо «У Вас VIP тариф».
 */
@Composable
fun VipMyPlanBubble(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isGodProfile = AuraGodNodeProfile.matches(context.applicationContext)
    val expires by VipAccessPreferences.expiresAtMsFlow.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expires, isGodProfile) {
        if (isGodProfile) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            if (expires <= 0L || nowMs >= expires) break
            delay(1_000L)
        }
    }
    val expired = !isGodProfile && VipAccessPreferences.isVipInRestrictedMode(context, nowMs)
    val leftMs = (expires - nowMs).coerceAtLeast(0L)
    val (days, hours, minutes) = splitDaysHoursMinutes(leftMs)
    var showExtendDialog by remember { mutableStateOf(false) }

    if (showExtendDialog) {
        VipExtensionRedeemDialog(
            onDismiss = { showExtendDialog = false },
            onRedeemed = {
                showExtendDialog = false
                onDismiss()
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            // Клик по затемнённому фону закрывает бабл (как у диалога «О приложении»).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .widthIn(max = 380.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0A2036).copy(alpha = 0.96f),
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.45f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (expired) "VIP тариф исчерпан" else "У Вас VIP тариф",
                        color = if (expired) Color(0xFFFF8A80) else Color(0xFFFFC107),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!expired && isGodProfile) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Бессрочный доступ · без ограничения по времени",
                            color = Color(0xFFDDE8F0),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!expired && !isGodProfile) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Тариф закончится через:",
                            color = Color(0xFFDDE8F0),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        CountdownRow(days = days, hours = hours, minutes = minutes)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!expired && !isGodProfile && expires > 0L) {
                        Button(
                            onClick = { showExtendDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFC107),
                                contentColor = Color(0xFF001018),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Продлить тариф", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = Color(0xFF00D4FF))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownRow(days: Long, hours: Long, minutes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountdownCell(value = days, label = "дн")
        Separator()
        CountdownCell(value = hours, label = "ч")
        Separator()
        CountdownCell(value = minutes, label = "мин")
    }
}

@Composable
private fun CountdownCell(value: Long, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 6.dp),
    ) {
        Text(
            text = "%02d".format(value),
            color = Color(0xFFFFE082),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color(0xFF8CB0BF),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Separator() {
    Text(
        text = "/",
        color = Color(0xFF5C7A8E),
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

private fun splitDaysHoursMinutes(leftMs: Long): Triple<Long, Long, Long> {
    if (leftMs <= 0L) return Triple(0L, 0L, 0L)
    val totalMinutes = leftMs / 60_000L
    val days = totalMinutes / (60L * 24L)
    val hours = (totalMinutes / 60L) % 24L
    val minutes = totalMinutes % 60L
    return Triple(days, hours, minutes)
}

/**
 * Диалог ввода одноразового кода продления VIP-тарифа (формат `XXXX-XXXX-XXXX`).
 *
 * Отдаёт код на проверку [VipExtensionCodeRedeemer] с node id, авторизованным в приложении;
 * при успехе закрывается через [onRedeemed], внешний sentinel и SharedPrefs обновляются.
 */
@Composable
fun VipExtensionRedeemDialog(
    onDismiss: () -> Unit,
    onRedeemed: () -> Unit,
) {
    val context = LocalContext.current
    var codeField by remember { mutableStateOf(TextFieldValue("")) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successDaysApplied by remember { mutableStateOf<Int?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    val guard = rememberBruteForceGuard(scope = "vip_extension")

    successDaysApplied?.let { days ->
        VipExtensionSuccessDialog(
            daysApplied = days,
            onDismiss = {
                successDaysApplied = null
                onRedeemed()
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F1927),
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                Text(
                    "Продление VIP",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Введите одноразовый код формата XXXX-XXXX-XXXX.",
                    color = Color(0xFFB0BEC5),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = codeField,
                    onValueChange = {
                        codeField = it
                        errorText = null
                    },
                    singleLine = true,
                    enabled = !guard.isLocked,
                    label = { Text("Код") },
                    placeholder = { Text("XXXX-XXXX-XXXX", color = Color(0xFF4A5A6A)) },
                    isError = errorText != null,
                    supportingText = {
                        val e = errorText
                        when {
                            guard.isLocked -> Text(
                                "Слишком много попыток. Повторите через ${guard.remainingLabel}",
                                color = Color(0xFFFFC107),
                            )
                            e != null -> Text(e, color = Color(0xFFFF5252))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0B1420),
                        unfocusedContainerColor = Color(0xFF0B1420),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF9FB3C8),
                        unfocusedLabelColor = Color(0xFF9FB3C8),
                        cursorColor = Color(0xFF00D4FF),
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D1A2E))
                            .border(
                                width = 1.dp,
                                color = Color(0xFF00D4FF).copy(alpha = 0.55f),
                                shape = CircleShape,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showHelpDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "?",
                            color = Color(0xFF00D4FF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = Color(0xFFB0BEC5))
                    }
                    TextButton(
                        enabled = !guard.isLocked,
                        onClick = {
                            if (guard.isLocked) return@TextButton
                            val nodeId = NodeAuthStore.loadStoredIdentity(context)?.nodeId.orEmpty()
                            if (nodeId.isBlank()) {
                                errorText = "Сначала авторизуйтесь в узле"
                                return@TextButton
                            }
                            when (val r = VipExtensionCodeRedeemer.redeem(context, nodeId, codeField.text)) {
                                is VipExtensionCodeRedeemer.RedeemResult.Success -> {
                                    guard.reset(context)
                                    errorText = null
                                    codeField = TextFieldValue("")
                                    successDaysApplied = r.daysApplied
                                }
                                VipExtensionCodeRedeemer.RedeemResult.AlreadyUsed ->
                                    errorText = "Этот код уже был использован"
                                VipExtensionCodeRedeemer.RedeemResult.WrongNodeOrTampered -> {
                                    guard.registerFailure(context)
                                    errorText = if (guard.isLocked) {
                                        "Слишком много попыток. Повторите через ${guard.remainingLabel}"
                                    } else {
                                        "Код не подходит для этого узла"
                                    }
                                }
                                VipExtensionCodeRedeemer.RedeemResult.InvalidFormat ->
                                    errorText = "Неверный формат кода"
                            }
                        },
                    ) {
                        Text(
                            "Применить",
                            color = if (guard.isLocked) Color(0xFF546E7A) else Color(0xFF00D4FF),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
