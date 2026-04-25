package com.example.aura.mesh.sync

/**
 * Состояние для UI: фаза, прогресс 0–100 (опционально), текст ошибки.
 */
data class MeshSyncUiState(
    val phase: MeshSyncConnectionPhase = MeshSyncConnectionPhase.DISCONNECTED,
    /** 0–100 при SYNCING_*; null если неизвестно. */
    val progressPercent: Int? = null,
    val lastError: String? = null,
    /** Счётчик попыток переподключения (для экспоненциальной задержки). */
    val reconnectAttempt: Int = 0,
)
