package com.example.aura.mesh.sync

/**
 * Фазы жизненного цикла связи с нодой (аналог статусов в официальном mesh).
 */
enum class MeshSyncConnectionPhase {
    /** Нет BLE или сессия не активна. */
    DISCONNECTED,

    /** GATT поднимается / первый handshake. */
    CONNECTING,

    /** want_config, MyNodeInfo, конфиги модулей (до завершения дампа конфигурации). */
    SYNCING_CONFIG,

    /** Поток NodeInfo / NodeDB после NODE_INFO_NONCE. */
    SYNCING_NODES,

    /** Живой поток FromRadio (чат, позиции, телеметрия). */
    ONLINE,

    /** Ошибка синхронизации или обрыв. */
    ERROR,
}
