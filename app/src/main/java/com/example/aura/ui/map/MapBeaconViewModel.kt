package com.example.aura.ui.map

import android.app.Application
import android.graphics.Color as AndroidColor
import com.example.aura.AuraApplication
import com.example.aura.data.local.MapBeaconDao
import com.example.aura.data.local.MapBeaconEntity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Единый формат #RRGGBB для mesh, Room и UI — иначе на приёмнике [android.graphics.Color.parseColor]
 * даёт fallback, а фильтр цветов на панели «Маяки» не совпадает с пресетами при отличии регистра/префикса.
 */
internal fun normalizeBeaconColorForStorage(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "#39E7FF"
    val candidate = when {
        trimmed.startsWith('#') -> trimmed
        (trimmed.length == 6 || trimmed.length == 8) &&
            trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> "#$trimmed"
        trimmed.startsWith("0x", ignoreCase = true) -> {
            val h = trimmed.substring(2)
            if ((h.length == 6 || h.length == 8) &&
                h.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            ) {
                "#$h"
            } else {
                trimmed
            }
        }
        else -> trimmed
    }
    return try {
        val argb = AndroidColor.parseColor(candidate)
        String.format(Locale.US, "#%06X", 0xFFFFFF and argb)
    } catch (_: Throwable) {
        "#39E7FF"
    }
}

data class MapBeacon(
    val id: Long,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val timestampCreated: Long,
    val creatorNodeNum: Long,
    val creatorLongName: String,
    val channelId: String,
    val channelIndex: Int,
    val ttlMs: Long,
    val color: String = "#39E7FF",
    /** Импорт из чата (ссылка/координаты); для mesh по умолчанию false. */
    val fromChatLink: Boolean = false,
    /** Только на устройстве в списке «Локально»; в mesh не публикуется (перенос из карточки метки). */
    val localMapInstall: Boolean = false,
)

class MapBeaconViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: MapBeaconDao =
        (application as AuraApplication).chatDatabase.mapBeaconDao()
    private val _beacons = MutableStateFlow<List<MapBeacon>>(emptyList())
    val beacons: StateFlow<List<MapBeacon>> = _beacons.asStateFlow()
    private val _activeChannel = MutableStateFlow(MapBeaconActiveChannelStore.selection.value)
    val activeChannel: StateFlow<MapBeaconChannelSelection> = _activeChannel.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collectLatest { list ->
                _beacons.value = list.map { it.toUi() }.filterNotExpired()
            }
        }
        viewModelScope.launch {
            MapBeaconActiveChannelStore.selection.collect { sel ->
                _activeChannel.value = sel
            }
        }
        // Periodically delete expired beacons from the DB so the DAO flow re-emits
        // and the map removes them automatically without any user interaction.
        viewModelScope.launch {
            while (true) {
                delay(EXPIRY_CHECK_INTERVAL_MS)
                dao.deleteExpired(System.currentTimeMillis())
            }
        }
    }

    /**
     * Активные (не истёкшие по TTL) mesh-метки этого автора — для лимита [MAX_BEACONS_PER_CREATOR].
     * Метки «только локально на карте» ([MapBeacon.localMapInstall]) не учитываются.
     */
    fun countActiveBeaconsForCreator(
        creatorNodeNum: Long,
        nowMs: Long = System.currentTimeMillis(),
        excludeBeaconId: Long? = null,
    ): Int {
        val mask = creatorNodeNum and 0xFFFF_FFFFL
        return _beacons.value.count { b ->
            (excludeBeaconId == null || b.id != excludeBeaconId) &&
                (b.creatorNodeNum and 0xFFFF_FFFFL) == mask &&
                !b.localMapInstall &&
                nowMs - b.timestampCreated <= b.ttlMs
        }
    }

    /**
     * Добавить метку.
     * Не более [MAX_BEACONS_PER_CREATOR] активных mesh-меток на автора; в режиме «только локально»
     * ([localMapInstall]) лимит не применяется — такие метки в эфир не уходят.
     */
    fun addBeacon(
        title: String,
        latitude: Double,
        longitude: Double,
        creatorNodeNum: Long,
        creatorLongName: String,
        channelId: String,
        channelIndex: Int,
        ttlMs: Long,
        color: String = "#39E7FF",
        beaconId: Long = System.currentTimeMillis(),
        timestampMs: Long = System.currentTimeMillis(),
        fromChatLink: Boolean = false,
        localMapInstall: Boolean = false,
        /** Не учитывать в лимите (замена staging при импорте до обновления списка в памяти). */
        excludeFromLimitCountId: Long? = null,
    ): MapBeacon? {
        if (!localMapInstall &&
            countActiveBeaconsForCreator(creatorNodeNum, excludeBeaconId = excludeFromLimitCountId) >=
            MAX_BEACONS_PER_CREATOR
        ) {
            return null
        }
        val beacon = MapBeacon(
            id = beaconId,
            title = title.trim(),
            latitude = latitude,
            longitude = longitude,
            timestampCreated = timestampMs,
            creatorNodeNum = creatorNodeNum,
            creatorLongName = creatorLongName.trim(),
            channelId = channelId.trim(),
            channelIndex = channelIndex,
            ttlMs = ttlMs.coerceAtLeast(MIN_BEACON_TTL_MS),
            color = normalizeBeaconColorForStorage(color),
            fromChatLink = fromChatLink,
            localMapInstall = localMapInstall,
        )
        viewModelScope.launch {
            dao.upsert(beacon.toEntity())
            dao.deleteExpired(System.currentTimeMillis())
        }
        return beacon
    }

    /** Обновить существующий маяк (тот же [MapBeacon.id] и [MapBeacon.channelIndex]). */
    fun upsertBeacon(beacon: MapBeacon) {
        viewModelScope.launch {
            dao.upsert(beacon.toEntity())
            dao.deleteExpired(System.currentTimeMillis())
        }
    }

    /**
     * Перенос маяка в другой канал и/или смена режима «только локально на карте».
     * При неизменном [MapBeacon.channelId]/[MapBeacon.channelIndex] обновляет строку через upsert.
     */
    suspend fun moveBeaconToChannelSuspend(
        beacon: MapBeacon,
        newChannelId: String,
        newChannelIndex: Int,
        localMapInstall: Boolean = false,
    ): MapBeacon =
        withContext(Dispatchers.IO) {
            val cid = newChannelId.trim()
            val idx = newChannelIndex
            val sameSlot = beacon.channelId == cid && beacon.channelIndex == idx
            val sameLocal = beacon.localMapInstall == localMapInstall
            if (sameSlot && sameLocal) {
                return@withContext beacon
            }
            if (sameSlot) {
                val updated = beacon.copy(localMapInstall = localMapInstall)
                dao.upsert(updated.toEntity())
                dao.deleteExpired(System.currentTimeMillis())
                return@withContext updated
            }
            dao.removeByIdAndChannelIndex(beacon.id, beacon.channelIndex)
            val moved = beacon.copy(
                channelId = cid,
                channelIndex = idx,
                localMapInstall = localMapInstall,
            )
            dao.upsert(moved.toEntity())
            dao.deleteExpired(System.currentTimeMillis())
            moved
        }

    fun removeBeacon(id: Long, requesterNodeNum: Long, channelId: String? = null): Boolean {
        viewModelScope.launch {
            dao.removeByOwner(id, requesterNodeNum, channelId)
        }
        return true
    }

    fun removeBeaconAny(id: Long, channelIndex: Int? = null): Boolean {
        viewModelScope.launch {
            if (channelIndex == null) {
                _beacons.value
                    .filter { it.id == id }
                    .forEach { dao.removeByIdAndChannelIndex(id, it.channelIndex) }
            } else {
                dao.removeByIdAndChannelIndex(id, channelIndex)
            }
        }
        return true
    }

    /** Удалить несколько меток одной транзакцией (локальная БД). */
    suspend fun removeAllBeaconsSuspend(beacons: List<MapBeacon>) {
        if (beacons.isEmpty()) return
        withContext(Dispatchers.IO) {
            beacons.forEach { dao.removeByIdAndChannelIndex(it.id, it.channelIndex) }
            dao.deleteExpired(System.currentTimeMillis())
        }
    }

    fun pruneExpired(nowMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            dao.deleteExpired(nowMs)
        }
    }

    fun applyRemoteBeacon(beacon: MapBeacon) {
        viewModelScope.launch {
            dao.upsert(beacon.toEntity())
            dao.deleteExpired(System.currentTimeMillis())
        }
    }

    private fun List<MapBeacon>.filterNotExpired(nowMs: Long = System.currentTimeMillis()): List<MapBeacon> =
        filter { nowMs - it.timestampCreated <= it.ttlMs }

    companion object {
        /** Максимум активных меток одного автора (по [MapBeacon.creatorNodeNum]) на этом устройстве. */
        const val MAX_BEACONS_PER_CREATOR: Int = 5
        const val LEGACY_BEACON_TTL_MS: Long = 24L * 60L * 60L * 1000L
        const val MIN_BEACON_TTL_MS: Long = 60L * 1000L
        // How often to check for and purge expired beacons. 30 s gives snappy removal
        // without hammering the DB on every tick.
        private const val EXPIRY_CHECK_INTERVAL_MS: Long = 30_000L
    }
}

private fun MapBeaconEntity.toUi(): MapBeacon =
    MapBeacon(
        id = id,
        title = title,
        latitude = latitude,
        longitude = longitude,
        timestampCreated = timestampCreated,
        creatorNodeNum = creatorNodeNum,
        creatorLongName = creatorLongName,
        channelId = channelId,
        channelIndex = channelIndex,
        ttlMs = ttlMs,
        color = normalizeBeaconColorForStorage(color),
        fromChatLink = fromChatLink,
        localMapInstall = localMapInstall,
    )

internal fun MapBeacon.toEntity(): MapBeaconEntity =
    MapBeaconEntity(
        id = id,
        title = title,
        latitude = latitude,
        longitude = longitude,
        timestampCreated = timestampCreated,
        creatorNodeNum = creatorNodeNum,
        creatorLongName = creatorLongName,
        channelId = channelId,
        channelIndex = channelIndex,
        ttlMs = ttlMs,
        color = normalizeBeaconColorForStorage(color),
        fromChatLink = fromChatLink,
        localMapInstall = localMapInstall,
    )
