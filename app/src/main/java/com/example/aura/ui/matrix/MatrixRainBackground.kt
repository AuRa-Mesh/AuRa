package com.example.aura.ui.matrix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.isActive

private val MatrixGreenBright = Color(0xFF00FF41)
private val MatrixGreenMid = Color(0xFF00CC33)
private val MatrixGreenDim = Color(0xFF006611)

private const val CHARSET =
    "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789ABCDEF"

private fun charsetRandom(r: Random): Char = CHARSET[r.nextInt(CHARSET.length)]

private data class RainColumn(
    val x: Float,
    var headY: Float,
    val speed: Float,
    val trailLen: Int,
    val charStride: Float,
    val symbols: CharArray,
    var symbolTick: Int = 0,
) {
    fun step(dtSec: Float, height: Float, speedMul: Float, random: Random) {
        headY += speed * dtSec * 140f * speedMul
        symbolTick++
        if (symbolTick >= 4) {
            symbolTick = 0
            symbols[random.nextInt(symbols.size)] = charsetRandom(random)
        }
        if (headY - trailLen * charStride > height + charStride * 6) {
            headY = random.nextFloat() * -height * 0.6f
            for (i in symbols.indices) symbols[i] = charsetRandom(random)
        }
    }
}

/**
 * Общий (process-wide) стейт матрицы.
 * Нужен, чтобы при переходе между экранами (чат ↔ профиль) дождь не перезапускался с нуля.
 */
private object MatrixRainRetainedState {
    var initializedWidth: Int = 0
    var initializedDensityKey: Int = -1
    val columns = mutableStateListOf<RainColumn>()
    val random = Random(7)
}

/**
 * @param densityMultiplier чем больше, тем больше столбцов (плотность символов).
 * @param speedMultiplier множитель скорости падения.
 * @param animationEnabled если false — столбцы рисуются, но позиции/символы не обновляются по кадрам.
 */
@Composable
fun MatrixRainBackground(
    modifier: Modifier = Modifier,
    densityMultiplier: Float = 1f,
    speedMultiplier: Float = 1f,
    animationEnabled: Boolean = true,
) {
    val density = densityMultiplier.coerceIn(0.2f, 2.5f)
    val speedMul = speedMultiplier.coerceIn(0.15f, 3f)
    val retained = MatrixRainRetainedState
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var frameTick by remember { mutableIntStateOf(0) }
    val columns = retained.columns
    val random = retained.random

    LaunchedEffect(viewSize, density) {
        val w = viewSize.width
        val h = viewSize.height
        if (w <= 0 || h <= 0) return@LaunchedEffect
        val densityKey = (density * 1000f).toInt()
        val shouldRebuild =
            columns.isEmpty() || w != retained.initializedWidth || densityKey != retained.initializedDensityKey
        if (!shouldRebuild) return@LaunchedEffect
        retained.initializedWidth = w
        retained.initializedDensityKey = densityKey
        columns.clear()
        val cell = (13f * (w / 400f).coerceIn(0.85f, 1.4f)).coerceIn(11f, 19f)
        val baseN = max(10, (w / cell).toInt())
        val n = max(6, (baseN * density).toInt().coerceAtMost(96))
        repeat(n) { i ->
            val x = (i + 0.5f) * (w / n.toFloat())
            val trail = random.nextInt(14, 32)
            columns.add(
                RainColumn(
                    x = x,
                    headY = random.nextFloat() * -h * 1.2f,
                    speed = random.nextFloat() * 0.9f + 0.4f,
                    trailLen = trail,
                    charStride = cell,
                    symbols = CharArray(trail) { charsetRandom(random) },
                ),
            )
        }
    }

    LaunchedEffect(viewSize, speedMul, animationEnabled) {
        if (!animationEnabled || viewSize.width <= 0) return@LaunchedEffect
        var prevNanos = 0L
        while (isActive) {
            withFrameNanos { t ->
                if (prevNanos == 0L) {
                    prevNanos = t
                    frameTick++
                    return@withFrameNanos
                }
                val dt = ((t - prevNanos).coerceAtMost(80_000_000)) / 1_000_000_000f
                prevNanos = t
                val h = viewSize.height.toFloat()
                if (h > 0f && columns.isNotEmpty()) {
                    for (c in columns) c.step(dt, h, speedMul, random)
                }
                frameTick++
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        drawRect(Color.Black.copy(alpha = 1f + frameTick * 0f))

        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = android.graphics.Paint.Align.CENTER
        }

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (col in columns) {
                for (i in 0 until col.trailLen) {
                    val y = col.headY - i * col.charStride
                    if (y < -col.charStride * 2 || y > h + col.charStride * 2) continue
                    val t = i / col.trailLen.toFloat()
                    val color = when {
                        i == 0 -> MatrixGreenBright
                        t < 0.12f -> MatrixGreenBright
                        t < 0.4f -> MatrixGreenMid
                        else -> MatrixGreenDim
                    }
                    val alpha = (1f - t * 0.94f).coerceIn(0.06f, 1f)
                    textPaint.color = android.graphics.Color.argb(
                        (alpha * 255).toInt().coerceIn(25, 255),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt(),
                    )
                    textPaint.textSize = col.charStride * 0.9f
                    nc.drawText(
                        col.symbols[i].toString(),
                        col.x,
                        y,
                        textPaint,
                    )
                }
            }
        }
    }
}
