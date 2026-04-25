package com.example.aura.mesh.nodedb

import androidx.annotation.RawRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aura.R
import java.util.Locale

/**
 * Встроенные в APK аватары, жёстко привязанные к node id.
 *
 * Такой аватар имеет приоритет над пользовательским (`cst_`) и сгенерированным (`gen_`),
 * поэтому одинаково отображается у всех пользователей и не может быть перезаписан сетью.
 * Файл лежит в `res/raw`, чтобы aapt не пережимал формат (GIF остаётся анимированным).
 */
object BundledNodeAvatars {

    private val byKey: Map<String, Int> = mapOf(
        "2acbcf40" to R.raw.avatar_bundled_2acbcf40,
        "9492ba7c" to R.raw.avatar_bundled_9492ba7c,
    )

    /**
     * Подпись на табличке рамки ([com.example.aura.ui.vip.VipAvatarFrame]) для узлов с
     * особой привязкой. Если id нет в карте — рамка покажет стандартное «VIP».
     */
    private val frameLabelByKey: Map<String, String> = mapOf(
        "2acbcf40" to "GOD",
        "9492ba7c" to "KBV",
    )

    @RawRes
    fun rawResForNodeId(nodeIdHex: String): Int? {
        val key = NodeAvatarStore.sanitizeKey(nodeIdHex).lowercase(Locale.ROOT)
        return byKey[key]
    }

    fun hasBundled(nodeIdHex: String): Boolean = rawResForNodeId(nodeIdHex) != null

    fun frameLabelForNodeId(nodeIdHex: String?): String? {
        if (nodeIdHex.isNullOrBlank()) return null
        val key = NodeAvatarStore.sanitizeKey(nodeIdHex).lowercase(Locale.ROOT)
        return frameLabelByKey[key]
    }
}

/**
 * Анимированный рендер встроенного аватара: сам GIF из `res/raw` проигрывается через Coil
 * (`ImageDecoderDecoder` / `GifDecoder` зарегистрированы в [com.example.aura.AuraApplication]),
 * плюс поверх — вращающийся световой ободок и лёгкая пульсация масштаба.
 */
@Composable
fun BundledNodeAvatarView(
    @RawRes rawRes: Int,
    modifier: Modifier = Modifier.size(52.dp),
    contentDescription: String? = null,
    contentAlpha: Float = 1f,
) {
    val ctx = LocalContext.current
    val transition = rememberInfiniteTransition(label = "bundled-avatar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo-angle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .drawBehind {
                val stroke = size.minDimension * 0.06f
                val ringSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val center = Offset(size.width / 2f, size.height / 2f)
                val brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF2BD6),
                        Color(0xFF6A5CFF),
                        Color(0xFF00E5FF),
                        Color(0xFFFF2BD6),
                    ),
                    center = center,
                )
                rotate(degrees = angle, pivot = center) {
                    drawArc(
                        brush = brush,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = ringSize,
                        style = Stroke(width = stroke),
                        alpha = 0.85f,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(Color(0xFF0D1A2E)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(rawRes)
                    .crossfade(false)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = contentAlpha,
            )
        }
    }
}
