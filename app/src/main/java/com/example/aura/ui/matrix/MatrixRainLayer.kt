package com.example.aura.ui.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

/** Фон панели вкладки: слегка прозрачный, чтобы просвечивала матрица. */
fun matrixPanelBackground(matrixBackdropActive: Boolean): Color =
    if (matrixBackdropActive) Mst.mainTabBackground.copy(alpha = 0.88f) else Mst.mainTabBackground

@Composable
fun MatrixRainLayer(
    densityMultiplier: Float,
    speedMultiplier: Float,
    dimOverlayAlpha: Float,
    modifier: Modifier = Modifier,
    rainAnimationEnabled: Boolean = true,
) {
    val dim = dimOverlayAlpha.coerceIn(0f, 1f)
    Box(modifier = modifier) {
        MatrixRainBackground(
            modifier = Modifier.fillMaxSize(),
            densityMultiplier = densityMultiplier,
            speedMultiplier = speedMultiplier,
            animationEnabled = rainAnimationEnabled,
        )
        if (dim > 0.004f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim)),
            )
        }
    }
}
