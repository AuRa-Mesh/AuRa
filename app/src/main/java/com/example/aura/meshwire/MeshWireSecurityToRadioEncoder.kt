package com.example.aura.meshwire

import android.util.Base64

/**
 * AdminMessage.set_config с вариантом [Config.security] (поле 8 в [Config]).
 * Транзакция как у LoRa: begin_edit → set_config → commit_edit.
 */
object MeshWireSecurityToRadioEncoder {

    private fun decodeKey32(b64: String): ByteArray? {
        val t = b64.trim()
        if (t.isEmpty()) return null
        return try {
            Base64.decode(t, Base64.DEFAULT).takeIf { it.size == 32 }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Base64 (32 байта после декодирования) или null. */
    fun parseBase64Key32OrNull(b64: String): ByteArray? = decodeKey32(b64)

    /** Сериализация только SecurityConfig (внутри Config). */
    fun encodeSecurityConfig(
        state: MeshWireSecurityPushState,
        omitPrivateKey: Boolean = false,
    ): ByteArray =
        MeshWireProtobufWriter().apply {
            decodeKey32(state.publicKeyB64)?.let { writeLengthDelimitedField(1, it) }
            if (!omitPrivateKey) {
                decodeKey32(state.privateKeyB64)?.let { writeLengthDelimitedField(2, it) }
            }
            for (k in state.adminKeysB64) {
                val b = decodeKey32(k) ?: continue
                writeLengthDelimitedField(3, b)
            }
            writeBoolField(4, state.isManaged)
            writeBoolField(5, state.serialEnabled)
            writeBoolField(6, state.debugLogApiEnabled)
            writeBoolField(8, state.adminChannelEnabled)
        }.toByteArray()

    fun encodeAdminSetConfigSecurity(
        securityInner: ByteArray,
    ): ByteArray {
        val configBytes = MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(8, securityInner)
        }.toByteArray()
        return MeshWireProtobufWriter().apply {
            writeLengthDelimitedField(34, configBytes)
        }.toByteArray()
    }

    fun encodeSecuritySetConfigTransaction(
        state: MeshWireSecurityPushState,
        device: MeshWireLoRaToRadioEncoder.LoRaDeviceParams,
        omitPrivateKey: Boolean = false,
    ): List<ByteArray> {
        val begin = MeshWireProtobufWriter().apply { writeBoolField(64, true) }.toByteArray()
        val commit = MeshWireProtobufWriter().apply { writeBoolField(65, true) }.toByteArray()
        val inner = encodeSecurityConfig(state, omitPrivateKey = omitPrivateKey)
        return listOf(
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(begin, device),
            MeshWireLoRaToRadioEncoder.encodeAdminAppMeshToRadio(
                encodeAdminSetConfigSecurity(inner),
                device,
            ),
            MeshWireLoRaToRadioEncoder.encodeAdminOneofToRadio(commit, device),
        )
    }
}
