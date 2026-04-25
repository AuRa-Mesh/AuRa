package com.example.aura.mesh.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

/**
 * Единая точка состояния синхронизации mesh ↔ приложение для UI и метрик.
 * Низкоуровневый BLE-конвейер по-прежнему в [com.example.aura.bluetooth.MeshGattClient] / [MeshBleScanner].
 */
object MeshSyncRepository {

    private val _uiState = MutableStateFlow(MeshSyncUiState())
    val uiState: StateFlow<MeshSyncUiState> = _uiState.asStateFlow()

    private val reconnectGen = AtomicInteger(0)

    fun resetReconnectAttempt() {
        reconnectGen.set(0)
        _uiState.update { it.copy(reconnectAttempt = 0) }
    }

    fun bumpReconnectAttempt() {
        val n = reconnectGen.incrementAndGet()
        _uiState.update { it.copy(reconnectAttempt = n) }
    }

    fun setPhase(phase: MeshSyncConnectionPhase, progressPercent: Int? = null, error: String? = null) {
        _uiState.update {
            it.copy(
                phase = phase,
                progressPercent = progressPercent,
                lastError = error ?: it.lastError,
            )
        }
    }

    fun setProgress(percent: Int) {
        _uiState.update { it.copy(progressPercent = percent.coerceIn(0, 100)) }
    }

    fun markDisconnected() {
        _uiState.value = MeshSyncUiState(
            phase = MeshSyncConnectionPhase.DISCONNECTED,
            progressPercent = null,
            lastError = _uiState.value.lastError,
            reconnectAttempt = _uiState.value.reconnectAttempt,
        )
    }

    fun markError(message: String) {
        _uiState.update {
            it.copy(phase = MeshSyncConnectionPhase.ERROR, lastError = message, progressPercent = null)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(lastError = null) }
    }
}
