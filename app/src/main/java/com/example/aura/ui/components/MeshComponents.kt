package com.example.aura.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Полноэкранный фон — тёмный радиальный градиент + сетка из точек */
@Composable
fun MeshBackground(content: @Composable BoxScope.() -> Unit = {}) {
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
                val dotColor = Color(0xFF00D4FF).copy(alpha = 0.06f)
                val spacing = 48.dp.toPx()
                val dotRadius = 1.5.dp.toPx()
                var x = spacing / 2
                while (x < size.width) {
                    var y = spacing / 2
                    while (y < size.height) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(x, y)
                        )
                        y += spacing
                    }
                    x += spacing
                }
            }
    ) {
        content()
    }
}
