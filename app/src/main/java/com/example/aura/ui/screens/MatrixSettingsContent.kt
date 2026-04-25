package com.example.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.example.aura.R
import com.example.aura.preferences.MatrixRainPreferences
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

private const val MatrixDialStartAngle = 120f
private const val MatrixDialSweepAngle = 300f

@Composable
fun MatrixSettingsContent(
    padding: PaddingValues,
    density: Float,
    speed: Float,
    dim: Float,
    onValuesChange: (density: Float, speed: Float, dim: Float) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.mainTabBackground)
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MatrixCircularSettingDial(
                title = stringResource(R.string.matrix_setting_density),
                value = density,
                onValueChange = { onValuesChange(it, speed, dim) },
                valueRange = MatrixRainPreferences.DENSITY_MIN..MatrixRainPreferences.DENSITY_MAX,
                valueText = "%.02f".format(density),
            )
        }
        item {
            MatrixCircularSettingDial(
                title = stringResource(R.string.matrix_setting_speed),
                value = speed,
                onValueChange = { onValuesChange(density, it, dim) },
                valueRange = MatrixRainPreferences.SPEED_MIN..MatrixRainPreferences.SPEED_MAX,
                valueText = "%.02f".format(speed),
            )
        }
        item {
            MatrixCircularSettingDial(
                title = stringResource(R.string.matrix_setting_dim),
                value = dim,
                onValueChange = { onValuesChange(density, speed, it) },
                valueRange = MatrixRainPreferences.DIM_MIN..MatrixRainPreferences.DIM_MAX,
                valueText = "%.0f %%".format(dim * 100f),
            )
        }
    }
}

@Composable
private fun MatrixCircularSettingDial(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val progress = (clamped - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val strokeWidth = 14.dp

    fun valueFromTouch(offset: Offset, widthPx: Float, heightPx: Float): Float {
        val centerX = widthPx / 2f
        val centerY = heightPx / 2f
        val angle = Math.toDegrees(
            atan2((offset.y - centerY).toDouble(), (offset.x - centerX).toDouble()),
        ).toFloat()
        val relative = ((angle - MatrixDialStartAngle) % 360f + 360f) % 360f
        val progressRaw = when {
            relative <= MatrixDialSweepAngle -> relative / MatrixDialSweepAngle
            relative - MatrixDialSweepAngle < 360f - relative -> 1f
            else -> 0f
        }
        return (valueRange.start + progressRaw * (valueRange.endInclusive - valueRange.start))
            .coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1A2E)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color(0xFFB8C4D0), fontSize = 13.sp)
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .pointerInput(valueRange) {
                        detectTapGestures { tap ->
                            onValueChange(valueFromTouch(tap, size.width.toFloat(), size.height.toFloat()))
                        }
                    }
                    .pointerInput(valueRange) {
                        detectDragGestures(
                            onDragStart = { start ->
                                onValueChange(
                                    valueFromTouch(start, size.width.toFloat(), size.height.toFloat()),
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                onValueChange(
                                    valueFromTouch(
                                        change.position,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                    ),
                                )
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val ringWidth = strokeWidth.toPx()
                    val radius = (size.minDimension / 2f) - ringWidth
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        color = Color(0xFF2A3A4A),
                        startAngle = MatrixDialStartAngle,
                        sweepAngle = MatrixDialSweepAngle,
                        useCenter = false,
                        style = Stroke(width = ringWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = Color(0xFF00CC66),
                        startAngle = MatrixDialStartAngle,
                        sweepAngle = MatrixDialSweepAngle * progress,
                        useCenter = false,
                        style = Stroke(width = ringWidth, cap = StrokeCap.Round),
                    )
                    val thumbAngleRad = Math.toRadians(
                        (MatrixDialStartAngle + MatrixDialSweepAngle * progress).toDouble(),
                    )
                    val thumbCenter = Offset(
                        x = center.x + cos(thumbAngleRad).toFloat() * radius,
                        y = center.y + sin(thumbAngleRad).toFloat() * radius,
                    )
                    drawCircle(color = Color(0xFF00FF88), radius = 10.dp.toPx(), center = thumbCenter)
                    drawCircle(color = Color(0xFF071320), radius = 7.dp.toPx(), center = thumbCenter)
                }
                Text(valueText, color = Color(0xFFDDE8F0), fontSize = 15.sp)
            }
        }
    }
}
