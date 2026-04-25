package com.example.aura.passwordgen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.annotation.Keep
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ─── Алгоритм (дублируется для автономности модуля — держите в синхроне с NodePasswordGenerator) ─

private const val HMAC_SECRET = "AuRusMesh_NodeKey_v1_2024"
private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** Как MeshtasticNodeNum.parseToUInt → 8 hex для HMAC. */
@Keep
private fun canonicalNodeIdHexForHmac(nodeId: String): String {
    var s = nodeId.trim()
    if (s.startsWith("0x", ignoreCase = true)) s = s.drop(2)
    s = s.removePrefix("!")
    val hex = s.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    if (hex.isEmpty()) return ""
    val core = when {
        hex.length > 8 -> hex.takeLast(8)
        hex.length < 8 -> hex.padStart(8, '0')
        else -> hex
    }
    return core.uppercase(Locale.US)
}

@Keep
fun generatePassword(nodeId: String): String {
    val normalized = canonicalNodeIdHexForHmac(nodeId)
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val hash = mac.doFinal(normalized.toByteArray(Charsets.UTF_8))
    return buildString {
        for (i in 0 until 8) {
            if (i == 4) append('-')
            append(ALPHABET[(hash[i].toInt() and 0xFF) % ALPHABET.length])
        }
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00D4FF),
                    background = Color(0xFF070B18),
                    surface = Color(0xFF0D1525)
                )
            ) {
                PasswordGenScreen()
            }
        }
    }
}

// ─── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun PasswordGenScreen() {
    val context = LocalContext.current
    var nodeIdInput by remember { mutableStateOf("") }
    var generatedPassword by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    fun generate() {
        val id = nodeIdInput.trim()
        if (id.isNotEmpty()) {
            generatedPassword = generatePassword(id)
        }
    }

    // Фон с mesh-сеткой
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0D1B2E), Color(0xFF070B18)),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = maxOf(size.width, size.height) * 0.75f
                    )
                )
                val dotColor = Color(0xFF00D4FF).copy(alpha = 0.055f)
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
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            // Иконка
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha * 0.13f),
                            radius = size.minDimension / 2 + 14.dp.toPx()
                        )
                        drawCircle(
                            color = Color(0xFF00D4FF).copy(alpha = alpha),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF).copy(alpha = alpha),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Aura-MESH",
                color = Color.White.copy(alpha = alpha),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                text = "генератор паролей",
                color = Color(0xFF00D4FF).copy(alpha = 0.6f),
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Поле ввода Node ID
            OutlinedTextField(
                value = nodeIdInput,
                onValueChange = { nodeIdInput = it; generatedPassword = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Node ID", color = Color(0xFF4A5A6A)) },
                placeholder = {
                    Text(
                        text = "!a1b2c3d4",
                        color = Color(0xFF2A3A4A),
                        fontFamily = FontFamily.Monospace
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = Color(0xFF4A5A6A),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (nodeIdInput.isNotEmpty()) {
                        IconButton(onClick = { nodeIdInput = ""; generatedPassword = null }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Очистить",
                                tint = Color(0xFF4A5A6A)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { generate() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00D4FF),
                    unfocusedBorderColor = Color(0xFF1E2D40),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFCCDDEE),
                    cursorColor = Color(0xFF00D4FF),
                    focusedContainerColor = Color(0xFF0D1A2E),
                    unfocusedContainerColor = Color(0xFF0D1A2E),
                    focusedLabelColor = Color(0xFF00D4FF)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Кнопка генерации
            Button(
                onClick = { generate() },
                enabled = nodeIdInput.trim().isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4FF),
                    contentColor = Color(0xFF070B18),
                    disabledContainerColor = Color(0xFF1E2D40),
                    disabledContentColor = Color(0xFF3A4A5A)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "СГЕНЕРИРОВАТЬ ПАРОЛЬ",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }

            // Пространство между кнопкой и паролем / копированием —
            // пароль центрируется ровно посередине этого пространства
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (generatedPassword != null) {
                    Text(
                        text = generatedPassword ?: "",
                        color = Color(0xFF00D4FF),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Кнопка копирования — прижата к самому низу Column
            AnimatedVisibility(
                visible = generatedPassword != null,
                enter = fadeIn()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D1A2E))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF00D4FF).copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Aura Node Password", generatedPassword ?: "")
                            )
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Копировать",
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Скопировать",
                        color = Color(0xFF00D4FF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Кнопка в правом верхнем углу — переход на экран генератора VIP-кодов продления.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 12.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1300))
                .border(
                    width = 1.dp,
                    color = Color(0xFFFFC107).copy(alpha = 0.6f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    context.startActivity(Intent(context, VipExtensionActivity::class.java))
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = "Aura-VIP",
                tint = Color(0xFFFFC107),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
