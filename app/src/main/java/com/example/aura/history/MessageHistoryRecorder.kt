package com.example.aura.history

/**
 * Связь с [MessageHistoryRepository] без протаскивания через все конструкторы;
 * устанавливается из [com.example.aura.AuraApplication].
 */
object MessageHistoryRecorder {
    var repository: MessageHistoryRepository? = null
}
