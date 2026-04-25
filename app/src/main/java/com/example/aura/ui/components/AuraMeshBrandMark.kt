package com.example.aura.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Белый, синий, красный — три полосы флага РФ для букв R, u, s. */
private val RussiaFlagWhite = Color(0xFFFFFFFF)
private val RussiaFlagBlue = Color(0xFF0039A6)
private val RussiaFlagRed = Color(0xFFD52B1E)

/**
 * Логотип «Aura» + «— MESH —» как на [com.example.aura.ui.screens.SplashScreen].
 * [compact] — уменьшенный вариант для кружка карточки узла.
 */
@Composable
fun AuraMeshBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    titleAlpha: Float = 1f,
    meshAlpha: Float = 1f,
    titleOverride: String? = null,
) {
    val titleSize = if (compact) 9.sp else 40.sp
    val meshSize = if (compact) 4.5.sp else 18.sp
    val titleLetter = if (compact) 1.sp else 5.sp
    val meshLetter = if (compact) 0.65.sp else 8.sp
    val auRusTitle = if (titleOverride != null) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White.copy(alpha = titleAlpha))) {
                append(titleOverride)
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White.copy(alpha = titleAlpha))) {
                append("Au")
            }
            withStyle(SpanStyle(color = RussiaFlagWhite.copy(alpha = titleAlpha))) {
                append("R")
            }
            withStyle(SpanStyle(color = RussiaFlagBlue.copy(alpha = titleAlpha))) {
                append("u")
            }
            withStyle(SpanStyle(color = RussiaFlagRed.copy(alpha = titleAlpha))) {
                append("s")
            }
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = auRusTitle,
            fontSize = titleSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = titleLetter,
            maxLines = 1,
        )
        Text(
            text = "— MESH —",
            color = Color(0xFF00D4FF).copy(alpha = meshAlpha),
            fontSize = meshSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = meshLetter,
            maxLines = 1,
        )
    }
}
