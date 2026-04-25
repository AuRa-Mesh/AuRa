package com.example.aura.passwordgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.SecureRandom

/**
 * Генератор одноразовых VIP-кодов продления тарифа.
 *
 * Три строки: Node ID, количество дней (1..1024), сгенерированный код `XXXX-XXXX-XXXX`.
 * Код привязан к узлу (HMAC) и содержит случайный 20-битный nonce — каждый запуск генерации
 * выдаёт новый код, даже для одинаковых `(nodeId, days)`.
 */
class VipExtensionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFC107),
                    background = Color(0xFF070B18),
                    surface = Color(0xFF0D1525),
                ),
            ) {
                VipExtensionGenScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun VipExtensionGenScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var nodeIdInput by remember { mutableStateOf("") }
    var daysInput by remember { mutableStateOf("30") }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    val secureRandom = remember { SecureRandom() }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    fun generate() {
        val id = nodeIdInput.trim()
        val daysParsed = daysInput.trim().toIntOrNull()
        if (id.isEmpty() || daysParsed == null) return
        val clampedDays = daysParsed.coerceIn(VipExtensionCodec.MIN_DAYS, VipExtensionCodec.MAX_DAYS)
        val nonce = secureRandom.nextInt(1 shl 20)
        generatedCode = VipExtensionCodec.encode(id, clampedDays, nonce)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1A1300), Color(0xFF070B18)),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = maxOf(size.width, size.height) * 0.75f,
                    ),
                )
                val dotColor = Color(0xFFFFC107).copy(alpha = 0.05f)
                val spacing = 48.dp.toPx()
                val dotRadius = 1.5.dp.toPx()
                var x = spacing / 2
                while (x < size.width) {
                    var y = spacing / 2
                    while (y < size.height) {
                        drawCircle(dotColor, dotRadius, Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color(0xFFFFC107).copy(alpha = alpha * 0.13f),
                            radius = size.minDimension / 2 + 14.dp.toPx(),
                        )
                        drawCircle(
                            color = Color(0xFFFFC107).copy(alpha = alpha),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFFFC107).copy(alpha = alpha),
                    modifier = Modifier.size(30.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Aura-VIP",
                color = Color.White.copy(alpha = alpha),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
            )
            Text(
                text = "коды продления тарифа",
                color = Color(0xFFFFC107).copy(alpha = 0.6f),
                fontSize = 12.sp,
                letterSpacing = 2.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 1. Node ID
            OutlinedTextField(
                value = nodeIdInput,
                onValueChange = { nodeIdInput = it; generatedCode = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Node ID", color = Color(0xFF4A5A6A)) },
                placeholder = {
                    Text(
                        "!a1b2c3d4",
                        color = Color(0xFF2A3A4A),
                        fontFamily = FontFamily.Monospace,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = Color(0xFF4A5A6A),
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (nodeIdInput.isNotEmpty()) {
                        IconButton(onClick = { nodeIdInput = ""; generatedCode = null }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Очистить",
                                tint = Color(0xFF4A5A6A),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
                colors = vipOutlinedColors(),
                shape = RoundedCornerShape(10.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Количество дней
            OutlinedTextField(
                value = daysInput,
                onValueChange = { v ->
                    val digits = v.filter { it.isDigit() }.take(4)
                    daysInput = digits
                    generatedCode = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Количество дней (1..${VipExtensionCodec.MAX_DAYS})", color = Color(0xFF4A5A6A)) },
                placeholder = {
                    Text(
                        "30",
                        color = Color(0xFF2A3A4A),
                        fontFamily = FontFamily.Monospace,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color(0xFF4A5A6A),
                        modifier = Modifier.size(20.dp),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { generate() }),
                colors = vipOutlinedColors(),
                shape = RoundedCornerShape(10.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { generate() },
                enabled = nodeIdInput.trim().isNotEmpty() &&
                    (daysInput.trim().toIntOrNull() ?: 0) in VipExtensionCodec.MIN_DAYS..VipExtensionCodec.MAX_DAYS,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFC107),
                    contentColor = Color(0xFF001018),
                    disabledContainerColor = Color(0xFF1E2D40),
                    disabledContentColor = Color(0xFF3A4A5A),
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "СГЕНЕРИРОВАТЬ КОД",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                )
            }

            // 3. Сгенерированный код
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (generatedCode != null) {
                    Text(
                        text = generatedCode ?: "",
                        color = Color(0xFFFFC107),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AnimatedVisibility(
                visible = generatedCode != null,
                enter = fadeIn(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1300))
                        .border(
                            width = 1.dp,
                            color = Color(0xFFFFC107).copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE,
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Aura VIP Extension Code", generatedCode ?: ""),
                            )
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Копировать",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Скопировать",
                        color = Color(0xFFFFC107),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }

        // Кнопка в правом верхнем углу — возврат на главный экран (генератор пароля по node id).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 12.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D1A2E))
                .border(
                    width = 1.dp,
                    color = Color(0xFF00D4FF).copy(alpha = 0.6f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = "Aura-MESH",
                tint = Color(0xFF00D4FF),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun vipOutlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFFFC107),
    unfocusedBorderColor = Color(0xFF40331A),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color(0xFFEEDDAA),
    cursorColor = Color(0xFFFFC107),
    focusedContainerColor = Color(0xFF1A1300),
    unfocusedContainerColor = Color(0xFF1A1300),
    focusedLabelColor = Color(0xFFFFC107),
)
