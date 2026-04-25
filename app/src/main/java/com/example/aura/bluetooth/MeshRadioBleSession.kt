package com.example.aura.bluetooth

/**
 * Устарело — логика координации BLE перенесена в [NodeGattConnection].
 * Оставлен для обратной совместимости с кодом, который ещё может ссылаться.
 */
@Deprecated("Используй NodeGattConnection напрямую")
object MeshRadioBleSession {
    @Deprecated("Не нужен — NodeGattConnection сам управляет соединением")
    fun bindWriter(w: Any?) = Unit

    @Deprecated("Не нужен — NodeGattConnection сам управляет соединением")
    fun disconnectBoundWriterForFullSync() = Unit

    val isChatSessionActive: Boolean get() = NodeGattConnection.isReady
}
