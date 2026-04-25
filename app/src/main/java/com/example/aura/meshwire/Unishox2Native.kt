package com.example.aura.meshwire

import androidx.annotation.Keep

/**
 * Распаковка [portnum 7](PORTNUM_TEXT_MESSAGE_COMPRESSED_APP) через Unishox2,
 * в том же пресете `USX_PSET_DFLT`, что и прошивка mesh.
 */
@Keep
object Unishox2Native {

    private val nativeOk: Boolean =
        try {
            System.loadLibrary("unishox2_jni")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }

    fun decompressMeshTextIfAvailable(compressed: ByteArray): ByteArray? {
        if (!nativeOk || compressed.isEmpty()) return null
        return decompressMeshTextNative(compressed)
    }

    /** Сжатие для portnum 7; `null` если нативная библиотека недоступна или буфер слишком мал. */
    fun compressMeshTextIfAvailable(plainUtf8: ByteArray): ByteArray? {
        if (!nativeOk || plainUtf8.isEmpty()) return null
        return compressMeshTextNative(plainUtf8)
    }

    external fun decompressMeshTextNative(payload: ByteArray): ByteArray?

    external fun compressMeshTextNative(plainUtf8: ByteArray): ByteArray?
}
