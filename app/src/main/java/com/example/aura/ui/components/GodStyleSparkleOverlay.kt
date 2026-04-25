package com.example.aura.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

private data class SparkleSpec(
    val xFrac: Float,
    val yFrac: Float,
    val periodMs: Int,
    val phaseMs: Int,
    val radiusFrac: Float,
)

/** Те же нормированные «искры», что у подписи GOD на VIP-рамке аватара. */
private val PROFILE_TRACK_SPARKLES = listOf(
    SparkleSpec(0.14f, 0.32f, 1700, 0, 0.11f),
    SparkleSpec(0.47f, 0.22f, 2100, 650, 0.09f),
    SparkleSpec(0.82f, 0.35f, 1950, 1200, 0.12f),
    SparkleSpec(0.28f, 0.72f, 2300, 300, 0.08f),
    SparkleSpec(0.65f, 0.75f, 1850, 900, 0.10f),
    SparkleSpec(0.92f, 0.62f, 2200, 1500, 0.07f),
)

/**
 * Лёгкие блёстки поверх иконки трека профиля (как на слове GOD в [com.example.aura.ui.vip.VipAvatarFrame]).
 */
@Composable
fun GodStyleSparkleOverlay(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFF8E1),
) {
    val transition = rememberInfiniteTransition(label = "profile-track-sparkle")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
        ),
        label = "profile-track-sparkle-phase",
    )
    Canvas(modifier = modifier) {
        val nowMs = phase * 6000f
        for (s in PROFILE_TRACK_SPARKLES) {
            val localMs = ((nowMs + s.phaseMs) % s.periodMs) / s.periodMs.toFloat()
            val intensity = sparkleIntensity(localMs)
            if (intensity <= 0.01f) continue
            val cx = size.width * s.xFrac
            val cy = size.height * s.yFrac
            val r = size.minDimension * s.radiusFrac
            drawSparkle(cx = cx, cy = cy, radius = r, alpha = intensity, color = color)
        }
    }
}

private fun sparkleIntensity(t: Float): Float {
    val s = kotlin.math.sin(Math.PI * t).toFloat()
    val s2 = s * s
    return (s2 * s2 * s2).coerceIn(0f, 1f)
}

private fun DrawScope.drawSparkle(
    cx: Float,
    cy: Float,
    radius: Float,
    alpha: Float,
    color: Color,
) {
    val a = alpha.coerceIn(0f, 1f)
    val center = Offset(cx, cy)
    drawCircle(
        color = color.copy(alpha = a * 0.30f),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = color.copy(alpha = a),
        radius = radius * 0.28f,
        center = center,
    )
    val spike = radius * 1.7f
    val stroke = radius * 0.14f
    drawLine(
        color = color.copy(alpha = a * 0.9f),
        start = Offset(cx - spike, cy),
        end = Offset(cx + spike, cy),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color.copy(alpha = a * 0.9f),
        start = Offset(cx, cy - spike),
        end = Offset(cx, cy + spike),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}
