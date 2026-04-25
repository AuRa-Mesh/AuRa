package com.example.aura.ui.screens

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.security.SecureRandom
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.fetchMeshWireChannels
import com.example.aura.ui.settingsFeedbackMessageColor
import com.example.aura.meshwire.MeshWireChannelsSyncResult
import com.example.aura.meshwire.MeshStoredChannel
import com.example.aura.meshwire.MeshWireChannelToRadioEncoder
import com.example.aura.meshwire.MeshWireLoRaConfigLogic
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWireModemPreset
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.meshwire.meshChannelDisplayTitle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.aura.ui.screens.MeshWireSettingsScreenColors as Mst

private val ChFabShape = RoundedCornerShape(18.dp)
private val ChCardShape = RoundedCornerShape(16.dp)
private val ChButtonShape = RoundedCornerShape(18.dp)

private val EncryptDialogBg = Color(0xFFF2F1EC)
private val EncryptDialogText = Color(0xFF1A1A1A)
private val EncryptDialogMuted = Color(0xFF6B6B6B)
private val EncryptDialogOutline = Color(0xFFC4C3BC)
private val EncryptSaveGreen = Color(0xFF2A4D3F)
private val EncryptCancelGreen = Color(0xFF3D8B67)

/** Как в Meshtastic `PositionPrecisionPreference`: выкл / 10–19 / 32 (полная). */
private const val POSITION_DISABLED = 0
private const val POSITION_FULL_BITS = 32

/** После commit_edit_settings нода перезагружается — пауза перед want_config / чтением каналов. */
private const val CHANNEL_PUSH_REBOOT_WAIT_MS = 6500L

private fun pskBytesToDisplay(psk: ByteArray): String =
    if (psk.isEmpty()) "" else Base64.encodeToString(psk, Base64.NO_WRAP)

private fun decodePskInput(text: String, ifBlankFallback: ByteArray): ByteArray {
    val t = text.trim()
    if (t.isEmpty()) return ifBlankFallback.copyOf()
    val raw = try {
        Base64.decode(t, Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Некорректный Base64")
    }
    require(raw.size in setOf(1, 16, 32)) {
        "PSK после Base64: 1, 16 или 32 байта"
    }
    return raw
}

/**
 * Как в Meshtastic [ChannelConfigScreen]: новый слот — только дефолтный PSK, пустое имя, остальное выкл.
 */
private fun buildAddChannelDraft(nextIndex: Int): MeshStoredChannel {
    val blank = MeshStoredChannel.newBlank("")
    return blank.copyForEdit(
        index = nextIndex,
        name = "",
        psk = MeshStoredChannel.DEFAULT_PSK,
        uplinkEnabled = false,
        downlinkEnabled = false,
        positionPrecision = 0U,
    )
}

private fun channelFromFormFields(
    template: MeshStoredChannel,
    nameText: String,
    pskText: String,
    uplinkEnabled: Boolean,
    downlinkEnabled: Boolean,
    positionPrecisionBits: UInt,
): MeshStoredChannel {
    val trimmedName = nameText.trim().take(11)
    var psk = decodePskInput(pskText, template.psk)
    val nameChanged = trimmedName != template.name.trim()
    // Как в Meshtastic: при первом имени слота с дефолтным PSK подставить случайный AES — только если
    // пользователь не задал свой ключ (иначе перезаписывали бы введённый PSK при любом изменении имени).
    if (nameChanged &&
        template.psk.contentEquals(MeshStoredChannel.DEFAULT_PSK) &&
        psk.contentEquals(MeshStoredChannel.DEFAULT_PSK)
    ) {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        psk = b
    }
    val pskChanged = !psk.contentEquals(template.psk)
    return template.copyForEdit(
        name = trimmedName,
        psk = psk,
        settingsId = if (pskChanged) {
            kotlin.random.Random.Default.nextInt().toUInt()
        } else {
            template.settingsId
        },
        uplinkEnabled = uplinkEnabled,
        downlinkEnabled = downlinkEnabled,
        positionPrecision = positionPrecisionBits,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsSettingsContent(
    padding: PaddingValues,
    nodeId: String,
    deviceAddress: String?,
    bootstrap: MeshWireChannelsSyncResult? = null,
    onBootstrapConsumed: () -> Unit = {},
    /** После «Да» в диалоге о смене порядка каналов — до отправки на ноду (очистка чатов на главном экране). */
    onReorderPushConfirmed: () -> Unit = {},
    /** После применения каналов в памяти приложения — обновить превью на вкладке «Сообщения». */
    onChannelsAppliedToNode: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var channels by remember { mutableStateOf<List<MeshStoredChannel>>(emptyList()) }
    var baselineChannels by remember { mutableStateOf<List<MeshStoredChannel>>(emptyList()) }
    var baselineMaxIndex by remember { mutableStateOf(-1) }
    var loraFreq by remember { mutableStateOf<Float?>(null) }
    var loraSlot by remember { mutableStateOf<UInt?>(null) }
    var actionHint by remember { mutableStateOf<String?>(null) }
    var pushing by remember { mutableStateOf(false) }
    /** После «Отправить»: ожидание перезагрузки ноды и запрос каналов с ноды. */
    var postPushReloading by remember { mutableStateOf(false) }
    var addChannelDraft by remember { mutableStateOf<MeshStoredChannel?>(null) }
    var addChannelSession by remember { mutableIntStateOf(0) }
    var showReorderSaveConfirm by remember { mutableStateOf(false) }
    var encryptEditChannel by remember { mutableStateOf<MeshStoredChannel?>(null) }
    var encryptDialogSession by remember { mutableIntStateOf(0) }
    /** Кэш LoRa для заголовка (частота/слот как в Meshtastic `ChannelConfigHeader`). */
    var cachedLora by remember { mutableStateOf<MeshWireLoRaPushState?>(null) }

    val nodeNum = remember(nodeId) { runCatching { MeshWireNodeNum.parseToUInt(nodeId) }.getOrNull() }
    val addr = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }

    val screenAlive = remember { booleanArrayOf(true) }
    DisposableEffect(Unit) {
        screenAlive[0] = true
        onDispose { screenAlive[0] = false }
    }

    var skipNextRemoteFetch by remember { mutableStateOf(false) }

    fun consumeChannelsSync(
        result: MeshWireChannelsSyncResult?,
        err: String?,
        errorsAsActionHint: Boolean,
        /** После успешной отправки на ноду: не очищать список и не затирать подсказку об успехе. */
        preserveLocalStateOnError: Boolean = false,
    ) {
        if (!screenAlive[0]) return
        runCatching {
            if (result != null) {
                baselineMaxIndex = result.rawMaxChannelIndex
                val stored = addr?.let { MeshNodeSyncMemoryStore.putChannels(it, result) } ?: result
                val list = stored.channels.map { it.copyForEdit() }
                channels = list
                baselineChannels = list.map { it.copyForEdit() }
                loraFreq = stored.loraFrequencyMhz
                loraSlot = stored.loraChannelNum
                cachedLora = addr?.let { MeshNodeSyncMemoryStore.getLora(it) }
            } else {
                if (preserveLocalStateOnError) {
                    return@runCatching
                }
                val msg = err?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить каналы."
                actionHint = msg
                if (!errorsAsActionHint) {
                    channels = emptyList()
                    baselineChannels = emptyList()
                }
            }
        }
    }

    fun pushChannelsToNode(ordered: List<MeshStoredChannel>) {
        if (addr == null) {
            actionHint = "Нет BLE-адреса"
            return
        }
        if (nodeNum == null) {
            actionHint = "Нужен Node ID (!xxxxxxxx)"
            return
        }
        if (ordered.isEmpty()) {
            actionHint = "Нет каналов для отправки"
            return
        }
        if (pushing || postPushReloading) return
        pushing = true
        actionHint = null
        val dev = MeshWireLoRaToRadioEncoder.LoRaDeviceParams(
            destinationNodeNum = nodeNum,
        )
        // false: без begin/commit — меньше ToRadio, set_channel применяются без reboot (см. encoder)
        val seq = MeshWireChannelToRadioEncoder.buildChannelPushSequence(
            orderedChannels = ordered,
            syncedMaxIndex = baselineMaxIndex,
            device = dev,
            wrapInEditTransaction = false,
        )
        MeshGattToRadioWriter(context.applicationContext).writeToradioQueue(
            deviceAddress = addr,
            payloads = seq,
            delayBetweenWritesMs = 200L,
        ) { ok, err ->
            if (!screenAlive[0]) return@writeToradioQueue
            pushing = false
            if (ok) {
                val reconciled = ordered.mapIndexed { i, c ->
                    c.copyForEdit(
                        index = i,
                        role = if (i == 0) {
                            MeshStoredChannel.ROLE_PRIMARY
                        } else {
                            MeshStoredChannel.ROLE_SECONDARY
                        },
                    )
                }
                channels = reconciled
                baselineChannels = reconciled.map { it.copyForEdit() }
                baselineMaxIndex = reconciled.lastIndex
                val cacheResult = MeshWireChannelsSyncResult(
                    channels = reconciled,
                    loraFrequencyMhz = loraFreq,
                    loraChannelNum = loraSlot,
                    rawMaxChannelIndex = reconciled.lastIndex,
                )
                MeshNodeSyncMemoryStore.putChannels(addr, cacheResult)
                actionHint = "Сохранено"
                onChannelsAppliedToNode()
                postPushReloading = true
                scope.launch {
                    delay(CHANNEL_PUSH_REBOOT_WAIT_MS)
                    if (!screenAlive[0]) {
                        postPushReloading = false
                        return@launch
                    }
                    fetchMeshWireChannels(
                        context.applicationContext,
                        addr,
                        onSyncProgress = null,
                    ) { result, err ->
                        if (!screenAlive[0]) return@fetchMeshWireChannels
                        postPushReloading = false
                        consumeChannelsSync(
                            result,
                            err,
                            errorsAsActionHint = true,
                            preserveLocalStateOnError = true,
                        )
                        if (result != null) {
                            val refreshedHint = "Список каналов обновлён с ноды"
                            actionHint = refreshedHint
                            onChannelsAppliedToNode()
                            scope.launch {
                                delay(3_000)
                                if (!screenAlive[0]) return@launch
                                if (actionHint == refreshedHint) {
                                    actionHint = null
                                }
                            }
                        } else {
                            actionHint =
                                "Каналы на ноде сохранены. Не удалось перечитать список: ${err ?: "ошибка"}"
                        }
                    }
                }
            } else {
                actionHint = "Ошибка: ${err ?: "BLE"}"
            }
        }
    }

    LaunchedEffect(addr, bootstrap) {
        val boot = bootstrap
        if (addr == null) {
            actionHint = "Привяжите устройство по Bluetooth."
            channels = emptyList()
            baselineChannels = emptyList()
            baselineMaxIndex = -1
            loraFreq = null
            loraSlot = null
            cachedLora = null
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        cachedLora = MeshNodeSyncMemoryStore.getLora(addr)
        if (boot != null) {
            consumeChannelsSync(boot, null, errorsAsActionHint = false)
            onBootstrapConsumed()
            skipNextRemoteFetch = true
            return@LaunchedEffect
        }
        if (skipNextRemoteFetch) {
            skipNextRemoteFetch = false
            return@LaunchedEffect
        }
        actionHint = null
        try {
            fetchMeshWireChannels(
                context.applicationContext,
                addr,
                onSyncProgress = null,
            ) { result, err ->
                consumeChannelsSync(result, err, errorsAsActionHint = false)
            }
        } catch (e: Throwable) {
            actionHint = e.message ?: "Ошибка загрузки"
            channels = emptyList()
            baselineChannels = emptyList()
        }
    }

    fun freqLine(): String {
        val overrideMhz = cachedLora?.let {
            MeshWireLoRaConfigLogic.parseOverrideFrequencyMhz(it.overrideFrequencyMhzText)
        } ?: 0f
        val f = if (overrideMhz > 0f) overrideMhz else loraFreq
        return if (f != null && f > 0f) {
            "Частота: ${"%.3f".format(f)}MHz"
        } else {
            "Частота: —"
        }
    }

    fun slotLine(): String {
        val cn = cachedLora?.let {
            MeshWireLoRaConfigLogic.parseChannelNumForProto(it.channelNumText)
        } ?: 0u
        val s = if (cn != 0u) cn else loraSlot
        return if (s != null) "Слот: $s" else "Слот: —"
    }

    /** Подсказка в диалоге шифрования: имя пресета при пустом поле имени канала. */
    val modemPresetTitle = remember(cachedLora) {
        cachedLora?.modemPreset?.defaultChannelNameForEmpty()
            ?: MeshWireModemPreset.LONG_FAST.defaultChannelNameForEmpty()
    }

    val isEditing = remember(channels, baselineChannels) {
        if (channels.size != baselineChannels.size) true
        else channels.indices.any { i -> channels[i] != baselineChannels[i] }
    }

    val channelOrderChangedFromBaseline = remember(channels, baselineChannels) {
        if (channels.size != baselineChannels.size) false
        else channels.map { it.rowKey } != baselineChannels.map { it.rowKey }
    }

    val canAddMoreSlots =
        addr != null && channels.size < MeshStoredChannel.MAX_CHANNELS

    fun openAddChannelDialog() {
        if (addr == null) return
        if (channels.size >= MeshStoredChannel.MAX_CHANNELS) {
            actionHint =
                "Уже ${MeshStoredChannel.MAX_CHANNELS} каналов. Удалите слот в списке."
            return
        }
        actionHint = null
        addChannelSession += 1
        addChannelDraft = buildAddChannelDraft(channels.size)
    }

    if (showReorderSaveConfirm) {
        val bubbleStroke = Color(0x6642E6FF)
        val bubbleFill = Color(0xFF152238)
        AlertDialog(
            onDismissRequest = {
                channels = baselineChannels.map { it.copyForEdit() }
                showReorderSaveConfirm = false
            },
            containerColor = Color(0xFF0E1F33),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Подтверждение",
                    color = Mst.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (17f * 0.8f).sp,
                )
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bubbleFill,
                    border = BorderStroke(1.dp, bubbleStroke),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "При изменении расположения каналов\n" +
                            "все чаты удалятся.",
                        color = Mst.text,
                        fontSize = (15f * 0.8f).sp,
                        lineHeight = (22f * 0.8f).sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        maxLines = 2,
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Подтвердить",
                        color = Mst.text,
                        fontSize = (15f * 0.8f).sp,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            channels = baselineChannels.map { it.copyForEdit() }
                            showReorderSaveConfirm = false
                        },
                    ) {
                        Text(
                            "Нет",
                            color = Mst.text.copy(alpha = 0.85f),
                            fontSize = (14f * 0.8f).sp,
                        )
                    }
                    TextButton(
                        onClick = {
                            showReorderSaveConfirm = false
                            onReorderPushConfirmed()
                            pushChannelsToNode(channels)
                        },
                    ) {
                        Text(
                            "Да",
                            color = Mst.accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (14f * 0.8f).sp,
                        )
                    }
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Mst.bg)
            .padding(padding)
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                freqLine(),
                                color = Mst.text.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                            )
                            Text(
                                slotLine(),
                                color = Mst.text.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Mst.card,
                                modifier = Modifier.size(26.dp),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = Mst.accent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Text(
                                "Первичный",
                                color = Mst.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "Вторичный",
                            color = Mst.text.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                items(
                    count = channels.size,
                    key = { channels[it].rowKey },
                ) { index ->
                    val ch = channels[index]
                    ChannelRowStable(
                        displayIndex = index,
                        displayTitle = meshChannelDisplayTitle(ch, cachedLora?.modemPreset),
                        channel = ch,
                        isPrimarySlot = ch.role == MeshStoredChannel.ROLE_PRIMARY,
                        canMoveUp = index > 0,
                        canMoveDown = index < channels.lastIndex,
                        onEditClick = {
                            encryptDialogSession += 1
                            encryptEditChannel = ch.copyForEdit()
                        },
                        onMoveUp = {
                            val i = index
                            if (i > 0) {
                                channels = channels.toMutableList().apply {
                                    val t = this[i]
                                    this[i] = this[i - 1]
                                    this[i - 1] = t
                                }
                                runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                            }
                        },
                        onMoveDown = {
                            val i = index
                            if (i < channels.lastIndex) {
                                channels = channels.toMutableList().apply {
                                    val t = this[i]
                                    this[i] = this[i + 1]
                                    this[i + 1] = t
                                }
                                runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                            }
                        },
                        onDelete = {
                            if (channels.size <= 1) {
                                actionHint = "Нужен хотя бы один канал"
                            } else {
                                channels = channels.filter { it.rowKey != ch.rowKey }
                                actionHint = null
                            }
                        },
                    )
                }

                item {
                    val ah = actionHint
                    if (!ah.isNullOrBlank()) {
                        Text(
                            ah,
                            color = if (ah.startsWith("Привяжите", ignoreCase = true)) {
                                Mst.muted
                            } else {
                                settingsFeedbackMessageColor(ah)
                            },
                            fontSize = 12.sp,
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                channels = baselineChannels.map { it.copyForEdit() }
                                actionHint = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Mst.cancelBtn,
                                contentColor = Mst.text,
                            ),
                            shape = ChButtonShape,
                        ) { Text("Отмена", fontWeight = FontWeight.Medium) }

                        Button(
                            onClick = {
                                if (channelOrderChangedFromBaseline) {
                                    showReorderSaveConfirm = true
                                } else {
                                    pushChannelsToNode(channels)
                                }
                            },
                            enabled = !pushing && !postPushReloading && addr != null && nodeNum != null &&
                                channels.isNotEmpty() && isEditing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Mst.accent,
                                contentColor = Mst.onAccent,
                                disabledContainerColor = Mst.dividerOuter,
                                disabledContentColor = Mst.muted,
                            ),
                            shape = ChButtonShape,
                        ) {
                            Text(
                                when {
                                    pushing -> "Отправка…"
                                    postPushReloading -> "Обновление с ноды…"
                                    else -> "Отправить"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (canAddMoreSlots) {
            FloatingActionButton(
                onClick = { openAddChannelDialog() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 16.dp),
                containerColor = Mst.accent,
                contentColor = Mst.onAccent,
                shape = ChFabShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp,
                ),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Добавить канал",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    addChannelDraft?.let { draftTemplate ->
        val live = channels.firstOrNull { it.rowKey == draftTemplate.rowKey } ?: draftTemplate
        ChannelEncryptionEditDialog(
            channel = live,
            modemPresetTitle = modemPresetTitle,
            resetSession = addChannelSession,
            onDismiss = { addChannelDraft = null },
            onSave = { updated ->
                val merged = if (channels.none { it.rowKey == updated.rowKey }) {
                    channels + updated.copyForEdit(index = channels.size)
                } else {
                    channels.map { row ->
                        if (row.rowKey == updated.rowKey) {
                            updated.copyForEdit(index = row.index)
                        } else {
                            row
                        }
                    }
                }
                channels = merged
                addChannelDraft = null
            },
        )
    }

    encryptEditChannel?.let { snap ->
        val live = channels.firstOrNull { it.rowKey == snap.rowKey } ?: snap
        ChannelEncryptionEditDialog(
            channel = live,
            modemPresetTitle = modemPresetTitle,
            resetSession = encryptDialogSession,
            onDismiss = { encryptEditChannel = null },
            onSave = { updated ->
                val merged = channels.map { row ->
                    if (row.rowKey == updated.rowKey) {
                        updated.copyForEdit(index = row.index)
                    } else {
                        row
                    }
                }
                channels = merged
                encryptEditChannel = null
            },
        )
    }
}

@Composable
private fun ChannelEncryptionEditDialog(
    channel: MeshStoredChannel,
    modemPresetTitle: String,
    resetSession: Int,
    onDismiss: () -> Unit,
    onSave: (MeshStoredChannel) -> Unit,
) {
    var nameText by remember(resetSession, channel.rowKey) {
        mutableStateOf(channel.name)
    }
    var pskText by remember(resetSession, channel.rowKey) {
        mutableStateOf(pskBytesToDisplay(channel.psk))
    }
    var uplinkEnabled by remember(resetSession, channel.rowKey) {
        mutableStateOf(channel.uplinkEnabled)
    }
    var downlinkEnabled by remember(resetSession, channel.rowKey) {
        mutableStateOf(channel.downlinkEnabled)
    }
    var posBits by remember(resetSession, channel.rowKey) {
        mutableStateOf(
            channel.positionPrecision.toInt().let { p ->
                when {
                    p in 1..9 -> 13
                    else -> p.coerceIn(0, 32)
                }
            },
        )
    }
    var fieldError by remember(resetSession, channel.rowKey) { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = EncryptDialogText,
        unfocusedTextColor = EncryptDialogText,
        focusedBorderColor = EncryptDialogOutline,
        unfocusedBorderColor = EncryptDialogOutline,
        focusedLabelColor = EncryptDialogMuted,
        unfocusedLabelColor = EncryptDialogMuted,
        cursorColor = EncryptSaveGreen,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = EncryptDialogBg,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it.take(11)
                        fieldError = null
                    },
                    label = { Text("Имя канала", fontSize = 14.sp) },
                    placeholder = {
                        Text(modemPresetTitle, fontSize = 14.sp, color = EncryptDialogMuted)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = pskText,
                    onValueChange = {
                        pskText = it
                        fieldError = null
                    },
                    label = { Text("PSK", fontSize = 14.sp) },
                    placeholder = { Text("", fontSize = 13.sp) },
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val b = ByteArray(16)
                                SecureRandom().nextBytes(b)
                                pskText = Base64.encodeToString(b, Base64.NO_WRAP)
                                fieldError = null
                            },
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Обновить ключ",
                                tint = EncryptDialogMuted,
                            )
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Uplink включен",
                        color = EncryptDialogText,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = uplinkEnabled,
                        onCheckedChange = {
                            uplinkEnabled = it
                            fieldError = null
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EncryptDialogText,
                            checkedTrackColor = EncryptCancelGreen.copy(alpha = 0.45f),
                            uncheckedThumbColor = EncryptDialogMuted,
                            uncheckedTrackColor = EncryptDialogOutline,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Downlink включен",
                        color = EncryptDialogText,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = downlinkEnabled,
                        onCheckedChange = {
                            downlinkEnabled = it
                            fieldError = null
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EncryptDialogText,
                            checkedTrackColor = EncryptCancelGreen.copy(alpha = 0.45f),
                            uncheckedThumbColor = EncryptDialogMuted,
                            uncheckedTrackColor = EncryptDialogOutline,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Местоположение",
                        color = EncryptDialogText,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = posBits != POSITION_DISABLED,
                        onCheckedChange = { on ->
                            posBits = if (on) POSITION_FULL_BITS else POSITION_DISABLED
                            fieldError = null
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EncryptDialogText,
                            checkedTrackColor = EncryptCancelGreen.copy(alpha = 0.45f),
                            uncheckedThumbColor = EncryptDialogMuted,
                            uncheckedTrackColor = EncryptDialogOutline,
                        ),
                    )
                }
                if (posBits != POSITION_DISABLED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Полная точность (32 бита)",
                            color = EncryptDialogText,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = posBits == POSITION_FULL_BITS,
                            onCheckedChange = { on ->
                                posBits = if (on) POSITION_FULL_BITS else 13
                                fieldError = null
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EncryptDialogText,
                                checkedTrackColor = EncryptCancelGreen.copy(alpha = 0.45f),
                                uncheckedThumbColor = EncryptDialogMuted,
                                uncheckedTrackColor = EncryptDialogOutline,
                            ),
                        )
                    }
                }
                if (posBits != POSITION_DISABLED && posBits != POSITION_FULL_BITS) {
                    Text(
                        "Точность (биты, 10–19)",
                        color = EncryptDialogMuted,
                        fontSize = 12.sp,
                    )
                    Slider(
                        value = posBits.coerceIn(10, 19).toFloat(),
                        onValueChange = {
                            posBits = it.roundToInt()
                            fieldError = null
                        },
                        valueRange = 10f..19f,
                        steps = 8,
                    )
                }

                fieldError?.let { err ->
                    Text(err, color = Color(0xFFC62828), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            "Отмена",
                            color = EncryptCancelGreen,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Button(
                        onClick = {
                            runCatching {
                                channelFromFormFields(
                                    template = channel,
                                    nameText = nameText,
                                    pskText = pskText,
                                    uplinkEnabled = uplinkEnabled,
                                    downlinkEnabled = downlinkEnabled,
                                    positionPrecisionBits = posBits.coerceIn(0, 32).toUInt(),
                                )
                            }.fold(
                                onSuccess = { onSave(it) },
                                onFailure = { e ->
                                    fieldError = e.message ?: "Неверный PSK"
                                },
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EncryptSaveGreen,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRowStable(
    displayIndex: Int,
    displayTitle: String,
    channel: MeshStoredChannel,
    isPrimarySlot: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEditClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val nameColor = if (isPrimarySlot) Mst.accent else Mst.text
    val namedLongFast = channel.name.trim().equals("LongFast", ignoreCase = true)
    val enc = !namedLongFast &&
        channel.psk.isNotEmpty() &&
        !channel.psk.contentEquals(byteArrayOf(0))
    val lockTint = when {
        !enc -> Mst.muted
        isPrimarySlot -> Mst.accent
        else -> Mst.secondaryLock
    }
    val lockIcon = if (enc) Icons.Filled.Lock else Icons.Outlined.LockOpen

    Surface(
        shape = ChCardShape,
        color = Mst.card,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 2.dp),
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Выше",
                        tint = if (canMoveUp) Mst.accent else Mst.muted.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Ниже",
                        tint = if (canMoveDown) Mst.accent else Mst.muted.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Mst.cancelBtn,
                modifier = Modifier.size(width = 36.dp, height = 32.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = displayIndex.toString(),
                        color = Mst.muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEditClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = displayTitle,
                    color = nameColor,
                    fontSize = 16.sp,
                    fontWeight = if (isPrimarySlot) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    lockIcon,
                    contentDescription = "Редактировать канал",
                    tint = lockTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Удалить",
                    tint = Mst.text,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
