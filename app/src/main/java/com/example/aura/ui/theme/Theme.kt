package com.example.aura.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val MeshColorScheme = darkColorScheme(
    primary = MeshCyan,
    onPrimary = MeshBackground,
    secondary = MeshCyanDim,
    onSecondary = MeshTextPrimary,
    background = MeshBackground,
    onBackground = MeshTextPrimary,
    surface = MeshSurface,
    onSurface = MeshTextPrimary,
    surfaceVariant = MeshCard,
    onSurfaceVariant = MeshTextSecondary,
    outline = MeshBorder,
    error = MeshError
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> MeshColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}