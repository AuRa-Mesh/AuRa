package com.example.aura.ui.map

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.aura.R
import com.example.aura.data.local.MapBeaconDao
import com.example.aura.bluetooth.MeshGattToRadioWriter
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.ParsedMeshDataPayload
import com.example.aura.security.NodeAuthStore
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.round
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Текущая версия протокола меток Aura (только PRIVATE_APP + JSON). */
private const val BEACON_SYNC_PROTOCOL_V2 = "beacon_v2"
/** Старые клиенты — при приёме учитываем. */
private const val BEACON_SYNC_PROTOCOL_V1 = "beacon_v1"

/**
 * Компактный JSON для эфира: полный verbose-объект часто > лимита полезной нагрузки mesh (~237 B на Data).
 * Ключ [BEACON_WIRE_COMPACT] — признак короткого формата.
 */
private const val BEACON_WIRE_COMPACT = "c2"
/** Заголовок метки в эфире (UTF-8), чтобы уложиться в лимит пакета. */
private const val BEACON_WIRE_TITLE_MAX_BYTES = 56

private fun isAuraBeaconProtocol(aura: String): Boolean =
    aura == BEACON_SYNC_PROTOCOL_V2 || aura == BEACON_SYNC_PROTOCOL_V1
private const val BEACON_NOTIF_CHANNEL_ID = "map_beacon_sync"
private const val BEACON_NOTIF_ID_BASE = 884_000

enum class BeaconTransportLabel {
    BLE,
    WIFI,
    USB,
    UNKNOWN,
}

sealed interface BeaconSyncEvent {
    data class Add(val beacon: MapBeacon, val transport: BeaconTransportLabel) : BeaconSyncEvent
    data class Remove(val id: Long, val channelId: String, val channelIndex: Int) : BeaconSyncEvent
}

/**
 * Синхронизация меток карты по mesh (PRIVATE_APP + JSON).
 *
 * **Канал:** индекс слота LoRa из выпадающего меню карты ([MapBeaconActiveChannelStore]) попадает в
 * [MapBeacon.channelIndex], в JSON поле `c` и в поле `channel` пакета ToRadio — те же, что у текстов
 * канала: метку на эфире видят (и расшифровывают) только узлы на **этом** канале.
 *
 * **Приём:** все add/remove пишутся в Room; уведомление/подсветка для UI add — только если открыт
 * тот же индекс в сторе (чтобы не разворачивать список от чужого канала). Событие remove шлётся
 * всегда — чтобы снять выделение и подсказки, даже если выпадающий канал сейчас другой.
 */
object MapBeaconSyncRepository {
    private val eventFlow = MutableSharedFlow<BeaconSyncEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<BeaconSyncEvent> = eventFlow.asSharedFlow()

    @Volatile
    private var installed = false
    private lateinit var app: Application
    private lateinit var dao: MapBeaconDao
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Монотонный номер мутации метки на этом устройстве (add/remove).
     * На приёмнике отбрасываются пакеты с rev ≤ последнего (mesh не гарантирует порядок).
     */
    private val beaconMutationSeq = AtomicLong(System.currentTimeMillis())

    /** Ключ: channelIndex_id_creatorNodeNum — последняя применённая ревизия. */
    private val lastAppliedBeaconRevision = ConcurrentHashMap<String, Long>()

    private fun nextBeaconRevision(): Long = beaconMutationSeq.incrementAndGet()

    private fun beaconLwwKey(channelIndex: Int, id: Long, creatorNodeNum: Long): String =
        "${channelIndex}_${id}_${creatorNodeNum}"

    private fun isStaleBeaconRevision(key: String, revisionSeq: Long?): Boolean {
        if (revisionSeq == null) return false
        val last = lastAppliedBeaconRevision[key] ?: 0L
        return revisionSeq <= last
    }

    private fun commitBeaconRevision(key: String, revisionSeq: Long?) {
        if (revisionSeq == null) return
        lastAppliedBeaconRevision[key] = revisionSeq
    }

    /**
     * Пропускаем только **собственное эхо** mesh-синхронизации метки (FromRadio после нашей же отправки).
     *
     * Раньше отсекали по `logicalFrom() == myNodeNum`, но на части цепочек `from`/`dataSource` в заголовке
     * [ParsedMeshDataPayload] может совпадать с **локальным** nodenum, хотя автор метки в JSON (`u`) — другой
     * узел; тогда чужие метки молча выбрасывались и на карте были видны только свои.
     */
    private fun shouldSkipOwnBeaconMeshEcho(
        my: UInt,
        parsed: ParsedBeaconSync,
        p: ParsedMeshDataPayload,
    ): Boolean {
        val myL = my.toLong() and 0xFFFF_FFFFL
        return when (val e = parsed.event) {
            is BeaconSyncEvent.Add ->
                (e.beacon.creatorNodeNum and 0xFFFF_FFFFL) == myL
            is BeaconSyncEvent.Remove -> {
                val c = parsed.removeCreatorFromJson?.let { it and 0xFFFF_FFFFL }
                    ?: (p.logicalFrom()?.toLong()?.and(0xFFFF_FFFFL) ?: return false)
                c == myL
            }
        }
    }

    /** JSON тела канального сообщения с меткой карты (для краткого превью в списке чатов). */
    fun isAuraBeaconChatWireText(text: String): Boolean {
        val trimmed = text.trimStart('\uFEFF').trim()
        if (!trimmed.startsWith('{')) return false
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return false
        if (obj.optString("k") == BEACON_WIRE_COMPACT) return true
        return isAuraBeaconProtocol(obj.optString("aura"))
    }

    /** TCP/USB: [com.example.aura.mesh.repository.MeshIncomingChatRepository.dispatchStreamFrame] → сюда. BLE: [MeshIncomingChatRepository] в начале [processFromRadioFrame]. */
    fun consumeStreamFrame(frame: ByteArray, transport: BeaconTransportLabel) {
        val payloads = MeshWireFromRadioMeshPacketParser.extractDataPayloadsFromFromRadio(frame)
        consumeParsedPayloads(payloads, transport)
    }

    private fun consumeParsedPayloads(payloads: List<ParsedMeshDataPayload>, transport: BeaconTransportLabel) {
        payloads.forEach { p ->
            val active = MapBeaconActiveChannelStore.selection.value
            when (p.portnum) {
                MeshWireLoRaToRadioEncoder.PORTNUM_PRIVATE_APP -> {
                    val my = try {
                        NodeGattConnection.myNodeNum.value
                    } catch (_: Throwable) {
                        null
                    }
                    val rawParsed = parseBeaconSyncPayload(p.payload) ?: return@forEach
                    val parsed = alignParsedWithWireChannel(rawParsed, p.channel)
                    if (my != null && shouldSkipOwnBeaconMeshEcho(my, parsed, p)) return@forEach
                    val creatorForKey = when (val e = parsed.event) {
                        is BeaconSyncEvent.Add -> e.beacon.creatorNodeNum
                        is BeaconSyncEvent.Remove ->
                            parsed.removeCreatorFromJson
                                ?: (p.logicalFrom()?.toLong()?.and(0xFFFFFFFFL) ?: 0L)
                    }
                    val key = beaconLwwKey(
                        when (val e = parsed.event) {
                            is BeaconSyncEvent.Add -> e.beacon.channelIndex
                            is BeaconSyncEvent.Remove -> e.channelIndex
                        },
                        when (val e = parsed.event) {
                            is BeaconSyncEvent.Add -> e.beacon.id
                            is BeaconSyncEvent.Remove -> e.id
                        },
                        creatorForKey,
                    )
                    if (isStaleBeaconRevision(key, parsed.revision)) return@forEach
                    when (val sync = parsed.event) {
                        is BeaconSyncEvent.Add -> {
                            upsertBeacon(sync.beacon)
                            commitBeaconRevision(key, parsed.revision)
                            // Событие — всегда (как у remove): подписчики сами проверяют слот/канал.
                            eventFlow.tryEmit(BeaconSyncEvent.Add(sync.beacon, transport))
                            if (sync.beacon.channelIndex == active.channelIndex) {
                                notifyNewBeacon(sync.beacon)
                                vibrateLight(app.applicationContext)
                            }
                        }
                        is BeaconSyncEvent.Remove -> {
                            removeBeacon(sync.id, sync.channelIndex)
                            commitBeaconRevision(key, parsed.revision)
                            // Всегда уведомляем UI: иначе при другом канале в выпадающем списке не снимется
                            // выделение / подсказки, хотя строка уже удалена из БД.
                            eventFlow.tryEmit(sync)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    /**
     * Индекс в JSON `c`/`ch` должен совпадать с [ParsedMeshDataPayload.channel], по которому нода расшифровала
     * пакет. Если в заголовке MeshPacket другое значение — берём **канал с эфира** (иначе БД/фильтр UI
     * расходятся с реальностью, и «тот же канал» в выпадающем списке не показывает чужие маячки).
     */
    private fun alignParsedWithWireChannel(parsed: ParsedBeaconSync, wireChannel: UInt?): ParsedBeaconSync {
        val w = wireChannel?.toInt()?.coerceAtLeast(0) ?: return parsed
        return when (val e = parsed.event) {
            is BeaconSyncEvent.Add -> {
                val b = e.beacon
                if (b.channelIndex == w) parsed
                else ParsedBeaconSync(
                    event = BeaconSyncEvent.Add(
                        b.copy(channelId = mapChannelIdForIndex(w), channelIndex = w),
                        e.transport,
                    ),
                    revision = parsed.revision,
                    removeCreatorFromJson = parsed.removeCreatorFromJson,
                )
            }
            is BeaconSyncEvent.Remove -> {
                if (e.channelIndex == w) parsed
                else ParsedBeaconSync(
                    event = BeaconSyncEvent.Remove(e.id, mapChannelIdForIndex(w), w),
                    revision = parsed.revision,
                    removeCreatorFromJson = parsed.removeCreatorFromJson,
                )
            }
        }
    }

    fun install(application: Application) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            app = application
            val au = application as? com.example.aura.AuraApplication ?: return
            dao = au.chatDatabase.mapBeaconDao()
            ensureBeaconNotificationChannel(application.applicationContext)
            installed = true
        }
    }

    /**
     * Публикация добавления метки: [PORTNUM_PRIVATE_APP] + компактный JSON (`k=c2`, см. [parseBeaconSyncPayload]).
     * Метки «только локально» ([MapBeacon.localMapInstall]) в эфир не публикуются.
     */
    fun publishAdd(beacon: MapBeacon) {
        if (beacon.localMapInstall) return
        val ch = beacon.channelIndex.coerceAtLeast(0)
        val rev = nextBeaconRevision()
        val title = truncateWireTitle(beacon.title, BEACON_WIRE_TITLE_MAX_BYTES)
        val json = JSONObject()
            .put("k", BEACON_WIRE_COMPACT)
            .put("o", "a")
            .put("i", beacon.id)
            .put("t", title)
            .put("la", roundCoordForWire(beacon.latitude))
            .put("lo", roundCoordForWire(beacon.longitude))
            .put("c", ch)
            .put("l", beacon.ttlMs)
            .put("x", normalizeBeaconColorForStorage(beacon.color))
            .put("u", beacon.creatorNodeNum)
            .put("e", rev)
            .put("f", if (beacon.fromChatLink) 1 else 0)
        val payload = json.toString().toByteArray(StandardCharsets.UTF_8)
        // Широковещание: как [MeshWireLoRaToRadioEncoder.encodePhonePositionToRadio] / tapback — wantAck=false,
        // иначе прошивка может ждать ACK по эфиру и не отпускать пакет / не ретранслировать метку.
        val bytes = MeshWireLoRaToRadioEncoder.encodeDecodedDataPacketToRadio(
            portnum = MeshWireLoRaToRadioEncoder.PORTNUM_PRIVATE_APP,
            payload = payload,
            to = MeshWireLoRaToRadioEncoder.BROADCAST_NODE_NUM,
            channelIndex = ch.toUInt(),
            wantAck = false,
        )
        sendSyncPayload(bytes)
    }

    /**
     * Удаление метки в mesh. Для строк, которые никогда не синхронизировались ([localMapInstall]), не вызывать —
     * используйте [publishRemoveIfMeshVisible].
     */
    fun publishRemove(beacon: MapBeacon) {
        if (beacon.localMapInstall) return
        val ch = beacon.channelIndex.coerceAtLeast(0)
        val rev = nextBeaconRevision()
        val json = JSONObject()
            .put("k", BEACON_WIRE_COMPACT)
            .put("o", "r")
            .put("i", beacon.id)
            .put("c", ch)
            .put("u", beacon.creatorNodeNum)
            .put("e", rev)
        val payload = json.toString().toByteArray(StandardCharsets.UTF_8)
        val bytes = MeshWireLoRaToRadioEncoder.encodeDecodedDataPacketToRadio(
            portnum = MeshWireLoRaToRadioEncoder.PORTNUM_PRIVATE_APP,
            payload = payload,
            to = MeshWireLoRaToRadioEncoder.BROADCAST_NODE_NUM,
            channelIndex = ch.toUInt(),
            wantAck = false,
        )
        sendSyncPayload(bytes)
    }

    /** [publishRemove], только если метка не режим «только у себя» / «Локально». */
    fun publishRemoveIfMeshVisible(beacon: MapBeacon) {
        if (beacon.localMapInstall) return
        publishRemove(beacon)
    }

    private fun sendSyncPayload(bytes: ByteArray) {
        val deviceAddress = try {
            NodeGattConnection.targetDevice?.address?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: SecurityException) {
            null
        } ?: NodeAuthStore.load(app.applicationContext)?.deviceAddress?.trim()?.takeIf { it.isNotEmpty() }

        // BLE/TCP/USB: MeshGattToRadioWriter сам выбирает нужный транспорт.
        if (deviceAddress != null) {
            MeshGattToRadioWriter(app.applicationContext).writeToradio(deviceAddress, bytes) { _, _ -> }
        } else {
            NodeGattConnection.sendToRadio(bytes)
        }
    }

    private fun upsertBeacon(beacon: MapBeacon) {
        scope.launch {
            dao.upsert(beacon.toEntity())
            dao.deleteExpired(System.currentTimeMillis())
        }
    }

    private fun removeBeacon(id: Long, channelIndex: Int) {
        scope.launch {
            dao.removeByIdAndChannelIndex(id, channelIndex)
        }
    }

    private data class ParsedBeaconSync(
        val event: BeaconSyncEvent,
        val revision: Long?,
        /** Только для op=remove: creator из JSON; null — подставится from пакета в [consumeParsedPayloads]. */
        val removeCreatorFromJson: Long?,
    )

    /**
     * Разбирает JSON-пейлоад маячка Aura: компактный [BEACON_WIRE_COMPACT] или verbose `beacon_v1`/`beacon_v2`.
     *
     * Индекс канала LoRa — поле "ch" / "c" (шифрование на ноде по этому слоту).
     * [channelId] в БД — [mapChannelIdForIndex](ch).
     */
    private fun parseBeaconSyncPayload(payload: ByteArray): ParsedBeaconSync? {
        val text = runCatching { String(payload, StandardCharsets.UTF_8) }.getOrNull()
            ?.trimStart('\uFEFF')
            ?.trim()
            ?: return null
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (obj.optString("k") == BEACON_WIRE_COMPACT) {
            return parseCompactBeaconWire(obj)
        }
        if (!isAuraBeaconProtocol(obj.optString("aura"))) return null
        val rev = if (obj.has("rev")) obj.optLong("rev", 0L) else null
        return when (obj.optString("op")) {
            "add" -> {
                val id = obj.optLong("id", 0L)
                val title = obj.optString("title", "").trim()
                val lat = obj.optDouble("lat", Double.NaN)
                val lon = obj.optDouble("lon", Double.NaN)
                if (id == 0L || title.isEmpty() || lat.isNaN() || lon.isNaN()) return null
                if (obj.optBoolean("local_map", false)) return null
                val chIndex = obj.optInt("ch", 0).coerceAtLeast(0)
                val chId = mapChannelIdForIndex(chIndex)
                ParsedBeaconSync(
                    event = BeaconSyncEvent.Add(
                        MapBeacon(
                            id = id,
                            title = title,
                            latitude = lat,
                            longitude = lon,
                            timestampCreated = obj.optLong("ts", System.currentTimeMillis()),
                            creatorNodeNum = obj.optLong("creator", 0L),
                            creatorLongName = obj.optString("creator_name", ""),
                            channelId = chId,
                            channelIndex = chIndex,
                            ttlMs = obj.optLong("ttl_ms", MapBeaconViewModel.LEGACY_BEACON_TTL_MS),
                            color = normalizeBeaconColorForStorage(obj.optString("color", "#39E7FF")),
                            fromChatLink = obj.optBoolean("from_chat", false),
                            localMapInstall = false,
                        ),
                        transport = BeaconTransportLabel.UNKNOWN,
                    ),
                    revision = rev,
                    removeCreatorFromJson = null,
                )
            }
            "remove" -> {
                val id = obj.optLong("id", 0L)
                if (id == 0L) return null
                val chIndex = obj.optInt("ch", 0).coerceAtLeast(0)
                val chId = mapChannelIdForIndex(chIndex)
                val creatorFromJson = if (obj.has("creator")) obj.optLong("creator", 0L) else null
                ParsedBeaconSync(
                    event = BeaconSyncEvent.Remove(id, chId, chIndex),
                    revision = rev,
                    removeCreatorFromJson = creatorFromJson,
                )
            }
            else -> null
        }
    }

    /** k=c2: `o` a=add, r=remove; `e` rev; `u` creator; `la`/`lo` lat/lon. */
    private fun parseCompactBeaconWire(obj: JSONObject): ParsedBeaconSync? {
        val rev = if (obj.has("e")) obj.optLong("e", 0L) else null
        return when (obj.optString("o")) {
            "a" -> {
                val id = obj.optLong("i", 0L)
                val title = obj.optString("t", "").trim()
                val lat = obj.optDouble("la", Double.NaN)
                val lon = obj.optDouble("lo", Double.NaN)
                if (id == 0L || title.isEmpty() || lat.isNaN() || lon.isNaN()) return null
                val chIndex = obj.optInt("c", 0).coerceAtLeast(0)
                val chId = mapChannelIdForIndex(chIndex)
                ParsedBeaconSync(
                    event = BeaconSyncEvent.Add(
                        MapBeacon(
                            id = id,
                            title = title,
                            latitude = lat,
                            longitude = lon,
                            timestampCreated = obj.optLong("m", System.currentTimeMillis()),
                            creatorNodeNum = obj.optLong("u", 0L),
                            creatorLongName = "",
                            channelId = chId,
                            channelIndex = chIndex,
                            ttlMs = obj.optLong("l", MapBeaconViewModel.LEGACY_BEACON_TTL_MS),
                            color = normalizeBeaconColorForStorage(obj.optString("x", "#39E7FF")),
                            fromChatLink = obj.optInt("f", 0) != 0,
                            localMapInstall = false,
                        ),
                        transport = BeaconTransportLabel.UNKNOWN,
                    ),
                    revision = rev,
                    removeCreatorFromJson = null,
                )
            }
            "r" -> {
                val id = obj.optLong("i", 0L)
                if (id == 0L) return null
                val chIndex = obj.optInt("c", 0).coerceAtLeast(0)
                val chId = mapChannelIdForIndex(chIndex)
                val creatorFromJson = if (obj.has("u")) obj.optLong("u", 0L) else null
                ParsedBeaconSync(
                    event = BeaconSyncEvent.Remove(id, chId, chIndex),
                    revision = rev,
                    removeCreatorFromJson = creatorFromJson,
                )
            }
            else -> null
        }
    }

    private fun truncateWireTitle(title: String, maxBytes: Int): String {
        val b = title.toByteArray(StandardCharsets.UTF_8)
        if (b.size <= maxBytes) return title
        var n = maxBytes
        while (n > 0 && (b[n - 1].toInt() and 0xC0) == 0x80) n--
        return String(b, 0, n.coerceAtLeast(0), StandardCharsets.UTF_8)
    }

    private fun roundCoordForWire(v: Double): Double = round(v * 1_000_000.0) / 1_000_000.0

    private fun ensureBeaconNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            BEACON_NOTIF_CHANNEL_ID,
            "Маячки карты",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления о новых метках в активном канале"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun notifyNewBeacon(beacon: MapBeacon) {
        val context = app.applicationContext
        val nm = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val id = BEACON_NOTIF_ID_BASE + ((beacon.id and 0x7FFFFFFF).toInt() % 10_000)
        val nb = NotificationCompat.Builder(context, BEACON_NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_photo)
            .setContentTitle("Новая метка")
            .setContentText(beacon.title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        nm.notify(id, nb.build())
    }

    private fun vibrateLight(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager ?: return
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                @Suppress("DEPRECATION")
                v.vibrate(40L)
            }
        } catch (_: Throwable) {
        }
    }

}
