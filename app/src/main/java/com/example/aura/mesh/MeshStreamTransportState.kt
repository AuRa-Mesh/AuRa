package com.example.aura.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MeshStreamTransportPhase {
    IDLE,
    CONNECTING,
    READY,
    DISCONNECTED,
}

data class MeshStreamTransportUi(
    val phase: MeshStreamTransportPhase,
    val detail: String,
)

/**
 * Состояние TCP/USB-потока для текста foreground-уведомления [MessageResilientService].
 */
object MeshStreamTransportState {
    private val _ui = MutableStateFlow(
        MeshStreamTransportUi(MeshStreamTransportPhase.IDLE, ""),
    )
    val ui: StateFlow<MeshStreamTransportUi> = _ui.asStateFlow()

    internal fun set(phase: MeshStreamTransportPhase, detail: String) {
        _ui.value = MeshStreamTransportUi(phase, detail)
    }

    internal fun reset() {
        _ui.value = MeshStreamTransportUi(MeshStreamTransportPhase.IDLE, "")
    }
}
