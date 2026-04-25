package com.example.aura.meshwire

/**
 * Снимок [Config.SecurityConfig] для UI и записи через AdminMessage.set_config.
 *
 * @see meshtastic protobufs config.proto](https://github.com/meshtastic/protobufs)
 */
data class MeshWireSecurityPushState(
    /** Base64, 32 байта после декодирования. */
    val publicKeyB64: String,
    val privateKeyB64: String,
    /** До 3 публичных ключей администратора (Base64 × 32 байт). */
    val adminKeysB64: List<String>,
    val serialEnabled: Boolean,
    val debugLogApiEnabled: Boolean,
    val isManaged: Boolean,
    val adminChannelEnabled: Boolean,
) {
    companion object {
        fun initial(): MeshWireSecurityPushState =
            MeshWireSecurityPushState(
                publicKeyB64 = "",
                privateKeyB64 = "",
                adminKeysB64 = emptyList(),
                serialEnabled = true,
                debugLogApiEnabled = false,
                isManaged = false,
                adminChannelEnabled = false,
            )
    }
}
