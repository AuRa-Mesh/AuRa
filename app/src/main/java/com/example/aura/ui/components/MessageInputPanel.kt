package com.example.aura.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.R
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.ui.map.BEACON_SHARE_SCHEME
import com.example.aura.ui.vip.VipRestrictedToast
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RefIconTint = Color(0xFF8E8E93)
private val InputFill = Color(0xFF1C1C1E)
private val InputStroke = Color(0xFF3A3A3C)
private val InputStrokeFocused = Color(0xFF5A5A5E)
private val PlaceholderRef = Color(0xFF8E8E93)

/** Красная кнопка записи голоса (Material red). */
private val VoiceRecordRed = Color(0xFFE53935)
private val SendCircleGreen = Color(0xFF00E676)
private val SendIconDark = Color(0xFF042818)
private val GroupLockCursorGreen = Color(0xFF4AF263)

private val BubbleLight = Color(0xFFF0F4F8)

/** Быстрые ответы: размер на 20% меньше базового. */
private const val QuickReplySizeScale = 0.8f

/** Пауза перед анимацией заполнения поля (срочное сообщение). */
private const val UrgentHoldBeforeFillMs = 700L

/** Длительность красного заполнения поля справа налево. */
private const val UrgentFillDurationMs = 700L

/** Как в Telegram: превью цитируемого сообщения (без вставки «>» в текст). */
private val TelegramReplyAccentDark = Color(0xFF5A9EEF)
private val TelegramReplyAccentLight = Color(0xFF2481CC)

/** Цитата для ответа в канале (MeshPacket.id + автор + сниппет для превью). */
data class MessageReplyDraft(
    val replyToPacketId: UInt,
    val replyToFromNodeNum: UInt,
    val authorName: String,
    val quotedSnippet: String,
)

/** Удержание: вниз — старт, вверх — lock, отпускание — конец жеста. */
sealed interface VoiceMicEvent {
    data object Down : VoiceMicEvent
    data class Up(val locked: Boolean) : VoiceMicEvent
    data object LockEngaged : VoiceMicEvent
}

private fun clampDraftUtf8(s: String): String =
    MeshWireLoRaToRadioEncoder.truncateMeshUtf8(
        s,
        MeshWireLoRaToRadioEncoder.MESH_TEXT_PAYLOAD_MAX_UTF8_BYTES,
    )

private fun autoCapitalizeSentences(text: String): String {
    if (text.isEmpty()) return text
    // Иначе первая «a» в aura://… станет «A» — Uri не распознает схему, ссылка уходит как обычный текст.
    val head = text.trimStart()
    if (head.length >= 2 && head.startsWith("$BEACON_SHARE_SCHEME://", ignoreCase = true)) {
        return text
    }
    val out = StringBuilder(text.length)
    var shouldCapitalize = true
    for (ch in text) {
        if (shouldCapitalize && ch.isLetter()) {
            out.append(ch.uppercaseChar())
            shouldCapitalize = false
            continue
        }
        out.append(ch)
        if (ch == '.' || ch == '?' || ch == '!') {
            shouldCapitalize = true
        } else if (ch.isLetter()) {
            shouldCapitalize = false
        }
    }
    return out.toString()
}

/**
 * Нижняя полоса: скрепка (меню вложений), поле текста, при [byteCounterText] — счётчик внизу справа,
 * отправка (при тексте), красная кнопка записи голоса (удержание).
 */
@Composable
fun MessageInputPanel(
    modifier: Modifier = Modifier,
    useDarkTheme: Boolean = true,
    text: String,
    onTextChange: (String) -> Unit,
    /** [urgent]==true после полного красного заполнения поля (удержание кнопки отправки ~1,4 с). */
    onSend: (urgent: Boolean) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenPoll: () -> Unit,
    onOpenList: () -> Unit,
    onOpenLocation: () -> Unit,
    /** false — пункт «Местоположение» неактивен (например, глобальный запрет передачи координат). */
    locationAttachEnabled: Boolean = true,
    onOpenContact: () -> Unit,
    /** false — меню «скрепки» полностью недоступно (VIP-ограничение): иконка гаснет, пункты игнорируются. */
    attachmentsEnabled: Boolean = true,
    /**
     * true — long-press на кнопке отправки формирует срочное сообщение (⚡URGENT) — в каналах
     * и в личных (DM) одинаково; [vipRestricted] выше по приоритету.
     */
    urgentLongPressEnabled: Boolean = true,
    /**
     * true — VIP-тариф исчерпан: скрываются быстрые ответы, голосовая кнопка и срочный
     * long-press на «Отправить». Остаётся только обычная отправка текста.
     */
    vipRestricted: Boolean = false,
    onVoiceClick: () -> Unit = {},
    onVoiceMicEvent: ((VoiceMicEvent) -> Unit)? = null,
    reply: MessageReplyDraft? = null,
    onDismissReply: () -> Unit = {},
    enabled: Boolean = true,
    sending: Boolean = false,
    sendCooldownSecondsLeft: Int = 0,
    cursorColor: Color = GroupLockCursorGreen,
    byteCounterText: String? = null,
    onInputFocusChange: (Boolean) -> Unit = {},
    showQuickReplies: Boolean = false,
    quickRepliesEnabled: Boolean = true,
    onQuickReply: (String) -> Unit = {},
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    val composeScope = rememberCoroutineScope()
    val urgentFillAnim = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(focused) {
        onInputFocusChange(focused)
    }
    val textLatest by rememberUpdatedState(text)
    /** Стабильная ссылка: иначе [pointerInput](onSend) сбрасывается на каждом рекомпоузе — удержание «срочно» обрывается (заметно в личке). */
    val onSendUpdated by rememberUpdatedState(onSend)

    val textPrimary = if (useDarkTheme) Color.White else Color(0xFF0A1628)
    val placeholderColor = if (useDarkTheme) PlaceholderRef else Color(0xFF6B7A8E)
    val borderColor = if (useDarkTheme) {
        if (focused && enabled) InputStrokeFocused else InputStroke
    } else {
        if (focused && enabled) Color(0xFF8A9A8E) else Color(0xFFD0D8E0)
    }

    val textStyle = TextStyle(
        color = textPrimary,
        fontSize = (16f * 0.8f).sp,
        lineHeight = (22f * 0.8f).sp,
        fontFamily = FontFamily.SansSerif,
    )

    val maxFieldHeight = 120.dp
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val lockThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }

    val inputBg = if (useDarkTheme) InputFill else Color(0xFFF5F7FA)

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        reply?.let { r ->
            val accent = if (useDarkTheme) TelegramReplyAccentDark else TelegramReplyAccentLight
            val replyBarBg = if (useDarkTheme) Color(0xFF252528) else Color(0xFFE3EDF7)
            val snippetColor = if (useDarkTheme) Color(0xFFB0B0B5) else Color(0xFF5C6B7A)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(replyBarBg)
                    .height(IntrinsicSize.Min)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accent),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = r.authorName.ifBlank { "\u200B" },
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = r.quotedSnippet,
                        color = snippetColor,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onDismissReply,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Отменить ответ", tint = RefIconTint)
                }
            }
        }
        val bubbleShape = RoundedCornerShape(28.dp)
        val bubbleBg = if (useDarkTheme) Color(0xFF1E1E22) else Color(0xFFF3F5F8)
        val bubbleStroke = if (useDarkTheme) {
            Color.White.copy(alpha = 0.1f)
        } else {
            Color(0xFFD0D8E0)
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = 6.dp,
                    bottom = 0.dp,
                ),
            shape = bubbleShape,
            color = bubbleBg,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, bubbleStroke),
        ) {
            val context = LocalContext.current
            val quickReplyScroll = rememberScrollState()
            Column(Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = showQuickReplies && !vipRestricted,
                    enter = slideInVertically(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        initialOffsetY = { -it },
                    ) + fadeIn(tween(200)),
                    exit = slideOutVertically(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        targetOffsetY = { it },
                    ) + fadeOut(tween(200)),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(quickReplyScroll)
                                .padding(
                                    start = (8f * QuickReplySizeScale).dp,
                                    end = (8f * QuickReplySizeScale).dp,
                                    top = (6f * QuickReplySizeScale).dp,
                                    bottom = (4f * QuickReplySizeScale).dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy((8f * QuickReplySizeScale).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val quickReplyIds = listOf(
                                R.string.chat_quick_reply_ack,
                                R.string.chat_quick_reply_abort,
                                R.string.chat_quick_reply_no,
                                R.string.chat_quick_reply_moving,
                                R.string.chat_quick_reply_confirm,
                            )
                            for (resId in quickReplyIds) {
                                AssistChip(
                                    onClick = { onQuickReply(context.getString(resId)) },
                                    enabled = quickRepliesEnabled,
                                    label = {
                                        Text(
                                            stringResource(resId),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = (13f * QuickReplySizeScale).sp,
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color(0xFF2A2A2C),
                                        labelColor = Color.White.copy(alpha = 0.92f),
                                        disabledContainerColor = Color(0xFF2A2A2C).copy(alpha = 0.45f),
                                        disabledLabelColor = Color.White.copy(alpha = 0.38f),
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        enabled = quickRepliesEnabled,
                                        borderColor = Color.White.copy(alpha = 0.12f),
                                    ),
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                Box {
                    val attachIconEnabled = enabled && attachmentsEnabled
                    // В VIP-restricted иконку «скрепки» делаем кликабельной, но вместо меню
                    // показываем объясняющий Toast — чтобы тап не казался «сломанным».
                    val restrictedAttachTap = enabled && !attachmentsEnabled && vipRestricted
                    IconButton(
                        onClick = {
                            when {
                                attachmentsEnabled -> showAttachMenu = true
                                restrictedAttachTap -> VipRestrictedToast.show(context)
                            }
                        },
                        enabled = attachIconEnabled || restrictedAttachTap,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = "Вложение",
                            tint = if (attachIconEnabled) RefIconTint else RefIconTint.copy(alpha = 0.35f),
                        )
                    }
                    DropdownMenu(
                        expanded = showAttachMenu && attachmentsEnabled,
                        onDismissRequest = { showAttachMenu = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = if (useDarkTheme) Color(0xFF2C2C2E) else Color(0xFFF0F0F0),
                        shadowElevation = 8.dp,
                    ) {
                        DropdownMenuItem(
                            text = { Text("GIF") },
                            onClick = {
                                showAttachMenu = false
                                if (attachmentsEnabled) onOpenFile()
                            },
                            enabled = attachmentsEnabled,
                        )
                        DropdownMenuItem(
                            text = { Text("Опрос") },
                            onClick = {
                                showAttachMenu = false
                                if (attachmentsEnabled) onOpenPoll()
                            },
                            enabled = attachmentsEnabled,
                        )
                        DropdownMenuItem(
                            text = { Text("Список") },
                            onClick = {
                                showAttachMenu = false
                                if (attachmentsEnabled) onOpenList()
                            },
                            enabled = attachmentsEnabled,
                        )
                        DropdownMenuItem(
                            text = { Text("Местоположение") },
                            onClick = {
                                showAttachMenu = false
                                if (attachmentsEnabled && locationAttachEnabled) onOpenLocation()
                            },
                            enabled = attachmentsEnabled && locationAttachEnabled,
                        )
                        DropdownMenuItem(
                            text = { Text("Контакт") },
                            onClick = {
                                showAttachMenu = false
                                if (attachmentsEnabled) onOpenContact()
                            },
                            enabled = attachmentsEnabled,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = inputBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = maxFieldHeight + 24.dp),
                ) {
                    val padFieldTop = 8.dp
                    val padFieldBottom = if (byteCounterText != null) 2.dp else 8.dp
                    val fillP = urgentFillAnim.value
                    Column(
                        modifier = Modifier
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 4.dp,
                                bottom = 0.dp,
                            )
                            .drawBehind {
                                if (fillP <= 0.001f) return@drawBehind
                                val wPx = size.width * fillP
                                val left = size.width - wPx
                                val cy = size.height * 0.5f
                                val r = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0x55FFAB91),
                                            Color(0xCCFF5252),
                                            Color(0xEEF44336),
                                        ),
                                        start = Offset(left, cy),
                                        end = Offset(size.width, cy),
                                    ),
                                    topLeft = Offset(left, 0f),
                                    size = Size(wPx, size.height),
                                    cornerRadius = r,
                                )
                            },
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Сообщение",
                                    style = textStyle.copy(color = placeholderColor),
                                    modifier = Modifier
                                        .padding(top = padFieldTop, bottom = padFieldBottom)
                                        .align(Alignment.CenterStart),
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = {
                                    onTextChange(
                                        clampDraftUtf8(
                                            autoCapitalizeSentences(it),
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 24.dp, max = maxFieldHeight)
                                    .verticalScroll(scrollState)
                                    .padding(top = padFieldTop, bottom = padFieldBottom),
                                enabled = enabled,
                                textStyle = textStyle,
                                minLines = 1,
                                maxLines = 6,
                                interactionSource = interactionSource,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                cursorBrush = SolidColor(
                                    if (focused && enabled) cursorColor else Color.Transparent,
                                ),
                            )
                        }
                        if (byteCounterText != null) {
                            Text(
                                text = byteCounterText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 0.dp, bottom = 3.dp),
                                color = RefIconTint,
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }

                val hasDraft = text.isNotBlank()
                val canSend = hasDraft && enabled && !sending && sendCooldownSecondsLeft <= 0
                val showSendControl = hasDraft || sendCooldownSecondsLeft > 0
                val urgentByHoldAvailable = urgentLongPressEnabled && !vipRestricted

                if (showSendControl) {
                    val sendBtnSize = 48.dp * 0.85f
                    val sendIconSize = 26.dp * 0.85f
                    Box(
                        modifier = Modifier
                            .size(sendBtnSize)
                            .clip(CircleShape)
                            .background(
                                if (canSend) SendCircleGreen else Color(0xFF5A5A5E),
                            )
                            .then(
                                if (canSend && sendCooldownSecondsLeft <= 0 && !urgentByHoldAvailable) {
                                    // Только обычная отправка: VIP или [urgentLongPressEnabled] == false.
                                    Modifier.clickable { onSendUpdated(false) }
                                } else if (canSend && sendCooldownSecondsLeft <= 0) {
                                    // Без onSend в ключе — иначе смена лямбды у родителя сбрасывает жест.
                                    Modifier.pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            val pressed = AtomicBoolean(true)
                                            val urgentSent = AtomicBoolean(false)
                                            val holdJob = composeScope.launch {
                                                delay(UrgentHoldBeforeFillMs)
                                                if (!pressed.get()) return@launch
                                                urgentFillAnim.snapTo(0f)
                                                try {
                                                    urgentFillAnim.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = tween(
                                                            durationMillis = UrgentFillDurationMs.toInt(),
                                                            easing = FastOutSlowInEasing,
                                                        ),
                                                    )
                                                } catch (_: CancellationException) {
                                                    urgentFillAnim.snapTo(0f)
                                                    return@launch
                                                }
                                                if (!pressed.get()) {
                                                    urgentFillAnim.snapTo(0f)
                                                    return@launch
                                                }
                                                onSendUpdated(true)
                                                urgentSent.set(true)
                                                urgentFillAnim.snapTo(0f)
                                            }
                                            try {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                                    if (event.changes.all { !it.pressed }) break
                                                }
                                            } finally {
                                                pressed.set(false)
                                                holdJob.cancel()
                                                composeScope.launch {
                                                    urgentFillAnim.stop()
                                                    urgentFillAnim.snapTo(0f)
                                                }
                                                if (!urgentSent.get() && textLatest.isNotBlank()) {
                                                    onSendUpdated(false)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sendCooldownSecondsLeft > 0) {
                            Text(
                                text = sendCooldownSecondsLeft.toString(),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(
                                    if (urgentByHoldAvailable) {
                                        R.string.message_input_send_desc_urgent
                                    } else {
                                        R.string.message_input_send_desc
                                    },
                                ),
                                tint = SendIconDark,
                                modifier = Modifier.size(sendIconSize),
                            )
                        }
                    }
                } else {
                    val voiceModifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (vipRestricted) Color(0xFF5A5A5E) else VoiceRecordRed)
                        .then(
                            if (vipRestricted) {
                                Modifier.clickable { VipRestrictedToast.show(context) }
                            } else if (onVoiceMicEvent != null && enabled && !sending) {
                                Modifier.pointerInput(onVoiceMicEvent, lockThresholdPx) {
                                    awaitEachGesture {
                                        val handler = onVoiceMicEvent ?: return@awaitEachGesture
                                        awaitFirstDown(requireUnconsumed = false)
                                        handler(VoiceMicEvent.Down)
                                        var locked = false
                                        var dragY = 0f
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                                                for (change in event.changes) {
                                                    if (change.pressed) {
                                                        dragY += change.positionChange().y
                                                    }
                                                }
                                                if (dragY < -lockThresholdPx && !locked) {
                                                    locked = true
                                                    handler(VoiceMicEvent.LockEngaged)
                                                }
                                                if (event.changes.all { !it.pressed }) break
                                            }
                                        } finally {
                                            handler(VoiceMicEvent.Up(locked))
                                        }
                                    }
                                }
                            } else {
                                Modifier.clickable(enabled = enabled && !sending) { onVoiceClick() }
                            },
                        )
                    Box(
                        modifier = voiceModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Запись голоса",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                }
            }
        }
    }
}
