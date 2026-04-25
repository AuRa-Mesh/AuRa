package com.example.aura.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.ui.components.AuraMeshBrandMark
import com.example.aura.ui.components.MeshBackground
import com.example.aura.security.SavedAuth

/**
 * Первый экран после полного запуска приложения: без автоподключения к ноде.
 * Тап: при полной сессии — переход в чат ([onNavigateToChat]), иначе — экран пароля ([onNavigateToPassword]);
 * подключение к ноде запускается уже из [com.example.aura.MainActivity] на экране чата.
 */
@Composable
fun SplashScreen(
    /** Полная сессия (node id + пароль) — тап ведёт в чат; иначе — на экран пароля. */
    fullSessionAuth: SavedAuth?,
    /** Переход в чат (нижняя вкладка: 0 сообщения, 1 ноды, 2 карта, 3 настройки). */
    onNavigateToChat: (bottomTabIndex: Int) -> Unit,
    onNavigateToPassword: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    MeshBackground {
        // Весь экран кликабелен (кроме кнопки "?")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (fullSessionAuth != null) {
                        onNavigateToChat(0)
                    } else {
                        onNavigateToPassword()
                    }
                }
        ) {
            // Центральный круг с названием приложения
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha * 0.12f),
                            radius = size.minDimension / 2 + 28.dp.toPx()
                        )
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha * 0.07f),
                            radius = size.minDimension / 2 + 50.dp.toPx()
                        )
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha * 0.35f),
                            style = Stroke(width = 1.dp.toPx()),
                            radius = size.minDimension / 2 - 14.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AuraMeshBrandMark(
                    compact = false,
                    titleAlpha = alpha,
                    meshAlpha = alpha,
                    titleOverride = "Aura",
                )
            }

            // Кнопка "?" внизу по центру
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
                    ) { showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Color(0xFF00D4FF),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "Контакты", fontWeight = FontWeight.Bold) },
            text = { Text(text = "По всем вопросам в ТГ @Aura_Mesh") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("OK") }
            },
            containerColor = Color(0xFF0D1A2E),
            titleContentColor = Color(0xFF00D4FF),
            textContentColor = Color(0xFFDDE8F0)
        )
    }
}
