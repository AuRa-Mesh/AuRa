@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.aura.ui.vip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.aura.mesh.nodedb.BundledNodeAvatars
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.vip.VipStatusStore
import kotlinx.coroutines.delay

/**
 * Множитель [VipAvatarFrame.frameScaleMultiplier] для вкладки «Узлы».
 * От базовой рамки: +100% (`2f`), затем −10% (`1.8`), затем −5% → `1.8 × 0.95 = 1.71`.
 */
const val VipAvatarFrameNodesTabScaleMultiplier = 1.71f

private const val ProfileExperienceLevelRingBadgeSizeFrac = 0.28f

/** Размер контейнера для inline-рамки: запас по краям и снизу под табличку VIP (не обрезается родителем). */
private fun vipInlineFrameTotalSize(avatarSize: Dp, frameScaleMultiplier: Float): Pair<Dp, Dp> {
    val plateH = avatarSize * (0.24f * 1.2f)
    val ringStroke = avatarSize * 0.065f
    val ringCanvas = avatarSize + ringStroke * 2
    val fs = (0.5776f * frameScaleMultiplier).coerceAtLeast(0.01f)
    val scaledSide = ringCanvas.value * fs
    val overscanPx = ((scaledSide - avatarSize.value) / 2f).coerceAtLeast(0f)
    val overscan = overscanPx.dp
    val badgeTail = plateH * 0.35f + plateH + 8.dp
    val totalW = avatarSize + overscan * 2
    val totalH = avatarSize + overscan * 2 + badgeTail
    return totalW to totalH
}

/**
 * Рамка у **нашего** аватара — пока действует VIP по времени ([VipAccessPreferences.isVipTimerActive]).
 */
@Composable
fun rememberLocalVipActive(): Boolean {
    val context = LocalContext.current
    val expires by VipAccessPreferences.expiresAtMsFlow.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expires) {
        if (!VipAccessPreferences.isInitialTimerSeeded(context)) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            val exp = VipAccessPreferences.getExpiresAtMs(context)
            if (exp <= nowMs) break
            delay(1000L)
        }
        nowMs = System.currentTimeMillis()
    }
    return VipAccessPreferences.isVipTimerActive(context, nowMs)
}

/**
 * Есть ли VIP-тариф у другого узла (по его nodeNum).
 */
@Composable
fun rememberPeerVipActive(nodeNum: Long?): Boolean {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { VipStatusStore.ensureLoaded(ctx) }
    val rev by VipStatusStore.revision.collectAsState()
    return remember(rev, nodeNum) { VipStatusStore.hasVip(nodeNum) }
}

/**
 * Обёртка вокруг аватара. Если [active] = true — поверх него рисуется золотое кольцо и табличка
 * «VIP» с камнями. Если [active] = false — обёртка визуально и по разметке прозрачна.
 *
 * Декорации по умолчанию выносятся в [Popup] (`useWindowPopupOverlay = true`) — поверх всего
 * окна приложения. Для вкладки «Узлы» задайте **`useWindowPopupOverlay = false`**: рамка
 * остаётся в том же дереве композиции, что и аватар — **выше содержимого «пузыря»** (sibling
 * под ней в [Box]), **скроллится вместе с карточкой** и не перекрывает верхнюю/нижнюю
 * шапку [Scaffold], в отличие от оконного [Popup].
 *
 * [avatarSize] должен совпадать с фактическим размером аватара, который рисует [content].
 *
 * @param frameScaleMultiplier множитель к базовому масштабу декораций (1f — главный экран и
 *   прочие места). На вкладке «Узлы» используйте [VipAvatarFrameNodesTabScaleMultiplier].
 * @param onAvatarClick вызывается при нажатии на аватар или на рамку (в т.ч. табличку VIP).
 *   При оконном [Popup] иначе касания над аватаром не доходили бы до родителя.
 * @param onAvatarLongClick длинное нажатие (например удаление фото в профиле).
 * @param onAvatarHorizontalSwipe свайп влево/вправо по рамке или аватару (например QR узла).
 * @param horizontalSwipeThresholdPx порог свайпа в пикселях; по умолчанию 56 dp.
 * @param useWindowPopupOverlay `true` — [Popup] по всему окну; `false` — рамка в составе
 *   родителя (например строка узла), см. описание выше.
 * @param bottomAvatarBadge опционально: круг уровня; при активной рамке VIP — частично на пластине
 *   (~30% высоты круга выше нижней кромки таблички). Без VIP — у нижнего края аватара.
 *   Имеет смысл при [useWindowPopupOverlay] = false; при оконном popup не рисуется.
 */
@Composable
fun VipAvatarFrame(
    active: Boolean,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    frameScaleMultiplier: Float = 1f,
    onAvatarClick: (() -> Unit)? = null,
    onAvatarLongClick: (() -> Unit)? = null,
    onAvatarHorizontalSwipe: (() -> Unit)? = null,
    horizontalSwipeThresholdPx: Float = Float.NaN,
    useWindowPopupOverlay: Boolean = true,
    nodeIdHex: String? = null,
    bottomAvatarBadge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val plateLabel = remember(nodeIdHex) {
        BundledNodeAvatars.frameLabelForNodeId(nodeIdHex) ?: "VIP"
    }
    val density = LocalDensity.current
    val swipeThresholdPx = if (horizontalSwipeThresholdPx.isNaN()) {
        with(density) { 56.dp.toPx() }
    } else {
        horizontalSwipeThresholdPx
    }
    val overlayGestureModifier = vipFrameOverlayGestures(
        onAvatarClick = onAvatarClick,
        onAvatarLongClick = onAvatarLongClick,
        onAvatarHorizontalSwipe = onAvatarHorizontalSwipe,
        swipeThresholdPx = swipeThresholdPx,
    )

    if (!active) {
        val gestureOrNone = onAvatarHorizontalSwipe != null || onAvatarClick != null || onAvatarLongClick != null
        val baseModifier = when {
            bottomAvatarBadge != null ->
                modifier.size(avatarSize).then(if (gestureOrNone) overlayGestureModifier else Modifier)
            gestureOrNone ->
                modifier.size(avatarSize).then(overlayGestureModifier)
            else ->
                modifier
        }
        Box(baseModifier) {
            content()
            if (bottomAvatarBadge != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -avatarSize * 0.11f),
                ) {
                    bottomAvatarBadge()
                }
            }
        }
        return
    }
    // При активной рамке и оконном [Popup] касания на слое popup; при вложении в [IconButton]
    // с тем же onClick внешний дублирующий clickable не добавляем (см. ветку выше).
    if (useWindowPopupOverlay) {
        Box(
            modifier = modifier.size(avatarSize),
            contentAlignment = Alignment.Center,
        ) {
            content()
            VipAvatarFramePopup(
                avatarSize = avatarSize,
                frameScaleMultiplier = frameScaleMultiplier,
                overlayGestureModifier = overlayGestureModifier,
                plateLabel = plateLabel,
                bottomAvatarBadge = null,
            )
        }
    } else {
        val (totalW, totalH) = vipInlineFrameTotalSize(avatarSize, frameScaleMultiplier)
        Box(
            modifier = modifier
                .size(totalW, totalH)
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(avatarSize)) { content() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                VipAvatarFrameDecorations(
                    avatarSize = avatarSize,
                    frameScaleMultiplier = frameScaleMultiplier,
                    gestureModifier = overlayGestureModifier,
                    plateLabel = plateLabel,
                    bottomAvatarBadge = bottomAvatarBadge,
                )
            }
        }
    }
}

@Composable
private fun vipFrameOverlayGestures(
    onAvatarClick: (() -> Unit)?,
    onAvatarLongClick: (() -> Unit)?,
    onAvatarHorizontalSwipe: (() -> Unit)?,
    swipeThresholdPx: Float,
): Modifier {
    val swipeUpdated = rememberUpdatedState(onAvatarHorizontalSwipe)
    val swipeModifier = if (onAvatarHorizontalSwipe != null) {
        Modifier.pointerInput(swipeThresholdPx) {
            var accum = 0f
            detectHorizontalDragGestures(
                onDragStart = { accum = 0f },
                onHorizontalDrag = { _, dx -> accum += dx },
                onDragEnd = {
                    if (kotlin.math.abs(accum) > swipeThresholdPx) {
                        swipeUpdated.value?.invoke()
                    }
                    accum = 0f
                },
            )
        }
    } else {
        Modifier
    }
    val clickModifier = if (onAvatarClick != null || onAvatarLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onAvatarClick?.invoke() },
            onLongClick = onAvatarLongClick,
        )
    } else {
        Modifier
    }
    return swipeModifier.then(clickModifier)
}

@Composable
private fun VipAvatarFrameDecorations(
    avatarSize: Dp,
    frameScaleMultiplier: Float,
    gestureModifier: Modifier,
    plateLabel: String = "VIP",
    bottomAvatarBadge: (@Composable () -> Unit)? = null,
) {
    val plateHeight: Dp = avatarSize * (0.24f * 1.2f)
    val plateWidth: Dp = avatarSize * (0.62f * 1.2f)
    val gemSize: Dp = avatarSize * (0.085f * 1.2f)
    val fontSize = (avatarSize.value * (0.17f * 1.2f)).sp

    val ringStrokeDp: Dp = avatarSize * 0.065f
    val ringCanvasDp: Dp = avatarSize + ringStrokeDp * 2

    val frameScale = 0.5776f * frameScaleMultiplier.coerceAtLeast(0.01f)

    Box(
        modifier = Modifier
            .size(ringCanvasDp)
            .graphicsLayer {
                scaleX = frameScale
                scaleY = frameScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                // Иначе табличка «VIP» (ниже кольца) обрезается границами слоя — в шапке чата
                // это менее заметно из‑за [Popup](clippingEnabled=false), на узлах — заметно.
                clip = false
            }
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(ringCanvasDp)) {
            val stroke = ringStrokeDp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val brush = Brush.sweepGradient(
                listOf(
                    Color(0xFFFFE68A),
                    Color(0xFFFFD24A),
                    Color(0xFFFFB300),
                    Color(0xFFB8861B),
                    Color(0xFFFFD24A),
                    Color(0xFFFFE68A),
                ),
                center = Offset(size.width / 2f, size.height / 2f),
            )
            drawCircle(
                brush = brush,
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = stroke),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = plateHeight * 0.35f - ringStrokeDp)
                .size(width = plateWidth, height = plateHeight)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFB8861B),
                            Color(0xFFFFE68A),
                            Color(0xFFFFC107),
                            Color(0xFFFFE68A),
                            Color(0xFFB8861B),
                        ),
                    ),
                )
                .border(0.8.dp, Color(0xFF6B4A00), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(plateWidth * 0.06f),
            ) {
                VipGem(size = gemSize, color = Color(0xFFE53935))
                Box(
                    modifier = Modifier.offset(y = -gemSize * 0.12f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = plateLabel,
                        color = Color(0xFF2B1600),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Black,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                    if (plateLabel != "VIP") {
                        TextSparkleOverlay(
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                VipGem(size = gemSize, color = Color(0xFF1E88E5))
            }
        }
        if (bottomAvatarBadge != null) {
            val plateOffsetDown = plateHeight * 0.35f - ringStrokeDp
            val plateBottomY = ringCanvasDp + plateOffsetDown
            val badgeSize = avatarSize * ProfileExperienceLevelRingBadgeSizeFrac
            // ~30% диаметра круга заходит на табличку/нижнюю часть «рамки» от нижней кромки пластины.
            val overlapOnPlateFrac = 0.3f
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (ringCanvasDp - badgeSize) / 2f,
                        y = plateBottomY - badgeSize * overlapOnPlateFrac,
                    ),
            ) {
                bottomAvatarBadge()
            }
        }
    }
}

@Composable
private fun VipAvatarFramePopup(
    avatarSize: Dp,
    frameScaleMultiplier: Float,
    overlayGestureModifier: Modifier,
    plateLabel: String = "VIP",
    bottomAvatarBadge: (@Composable () -> Unit)? = null,
) {
    val ringStrokeDp: Dp = avatarSize * 0.065f
    val ringCanvasDp: Dp = avatarSize + ringStrokeDp * 2

    // Центрируем содержимое popup (размер = ringCanvasDp) по центру анкера, независимо
    // от того, какие bounds сообщит родитель. Так аватар оказывается ровно в центре кольца.
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val cx = anchorBounds.left + anchorBounds.width / 2
                val cy = anchorBounds.top + anchorBounds.height / 2
                return IntOffset(
                    cx - popupContentSize.width / 2,
                    cy - popupContentSize.height / 2,
                )
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            clippingEnabled = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        VipAvatarFrameDecorations(
            avatarSize = avatarSize,
            frameScaleMultiplier = frameScaleMultiplier,
            gestureModifier = overlayGestureModifier,
            plateLabel = plateLabel,
            bottomAvatarBadge = bottomAvatarBadge,
        )
    }
}

/**
 * Набор из нескольких «искр», расположенных в нормированных координатах (0..1) внутри
 * области букв. Каждая искра пульсирует со своим периодом и фазой — получается ощущение
 * случайного бриллиантового блеска, без синхронности.
 */
private data class SparkleSpec(
    val xFrac: Float,
    val yFrac: Float,
    val periodMs: Int,
    val phaseMs: Int,
    val radiusFrac: Float,
)

private val GOD_SPARKLES = listOf(
    SparkleSpec(xFrac = 0.14f, yFrac = 0.32f, periodMs = 1700, phaseMs = 0,    radiusFrac = 0.11f),
    SparkleSpec(xFrac = 0.47f, yFrac = 0.22f, periodMs = 2100, phaseMs = 650,  radiusFrac = 0.09f),
    SparkleSpec(xFrac = 0.82f, yFrac = 0.35f, periodMs = 1950, phaseMs = 1200, radiusFrac = 0.12f),
    SparkleSpec(xFrac = 0.28f, yFrac = 0.72f, periodMs = 2300, phaseMs = 300,  radiusFrac = 0.08f),
    SparkleSpec(xFrac = 0.65f, yFrac = 0.75f, periodMs = 1850, phaseMs = 900,  radiusFrac = 0.10f),
    SparkleSpec(xFrac = 0.92f, yFrac = 0.62f, periodMs = 2200, phaseMs = 1500, radiusFrac = 0.07f),
)

@Composable
private fun TextSparkleOverlay(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFF8E1),
) {
    val transition = rememberInfiniteTransition(label = "sparkle")
    // Один непрерывный счётчик фазы в диапазоне [0..totalMs); каждая искра берёт своё
    // окно пульсации по собственному периоду и сдвигу.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
        ),
        label = "sparkle-phase",
    )
    Canvas(modifier = modifier) {
        val nowMs = phase * 6000f
        for (s in GOD_SPARKLES) {
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

/**
 * Резкий «всплеск»: ноль почти всё время, затем быстрый подъём-спад в середине периода.
 * `sin(π·t)^6` даёт узкий, но плавный пик со значениями 0..1.
 */
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

@Composable
private fun VipGem(size: Dp, color: Color) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.9f),
                        color,
                        color.copy(alpha = 0.6f),
                    ),
                ),
            )
            .border(0.4.dp, Color.White.copy(alpha = 0.7f), CircleShape),
    )
}
