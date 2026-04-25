package com.example.aura.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MapBeaconChannelSelection(
    val channelId: String,
    val channelIndex: Int,
    val channelTitle: String,
)

fun mapChannelIdForIndex(index: Int): String = "ch_${index.coerceAtLeast(0)}"

/** Виртуальный канал карты: показать все маяки со всех каналов в одном слое. */
const val LOCAL_BEACON_CHANNEL_ID = "ch__local_agg"
const val LOCAL_BEACON_CHANNEL_INDEX = -1
const val LOCAL_BEACON_CHANNEL_TITLE = "Локально"

fun isLocalBeaconChannelSelection(sel: MapBeaconChannelSelection): Boolean =
    sel.channelId == LOCAL_BEACON_CHANNEL_ID

object MapBeaconActiveChannelStore {
    private val _selection = MutableStateFlow(
        MapBeaconChannelSelection(
            channelId = mapChannelIdForIndex(0),
            channelIndex = 0,
            channelTitle = "ch 0",
        ),
    )
    val selection: StateFlow<MapBeaconChannelSelection> = _selection.asStateFlow()

    fun setChannel(index: Int, title: String) {
        val normalizedIndex = index.coerceAtLeast(0)
        _selection.value = MapBeaconChannelSelection(
            channelId = mapChannelIdForIndex(normalizedIndex),
            channelIndex = normalizedIndex,
            channelTitle = title.ifBlank { "ch $normalizedIndex" },
        )
    }

    fun setLocalVirtualChannel() {
        _selection.value = MapBeaconChannelSelection(
            channelId = LOCAL_BEACON_CHANNEL_ID,
            channelIndex = LOCAL_BEACON_CHANNEL_INDEX,
            channelTitle = LOCAL_BEACON_CHANNEL_TITLE,
        )
    }
}
