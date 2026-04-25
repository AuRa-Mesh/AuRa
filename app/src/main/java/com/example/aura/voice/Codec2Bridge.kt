package com.example.aura.voice

/** JNI к libcodec2 (CODEC2_MODE_3200). На канал добавляется ~30% избыточных байт (заголовок + FEC-хвост). */
object Codec2Bridge {

    private const val WRAP_M0: Int = 0xA6
    private const val WRAP_M1: Int = 0x57
    /** Доля «лишних» байт к длине ядра Codec2 (около +30%). */
    private const val FEC_NUM = 30
    private const val FEC_DEN = 100

    private var loaded: Boolean = false

    init {
        try {
            System.loadLibrary("codec2_jni")
            loaded = true
        } catch (_: UnsatisfiedLinkError) {
            loaded = false
        }
    }

    fun isNativeLoaded(): Boolean = loaded

    @JvmStatic
    external fun nativeEncodePcm8kMono(pcm: ShortArray): ByteArray

    @JvmStatic
    external fun nativeDecodeToPcm8kMono(encoded: ByteArray): ShortArray

    @JvmStatic
    external fun nativeSamplesPerFrame(): Int

    private fun fecLength(coreLen: Int): Int {
        if (coreLen <= 0) return 0
        return (coreLen * FEC_NUM + FEC_DEN - 1) / FEC_DEN
    }

    private val samplesPerFrameSafe: Int by lazy {
        val fallback = 160 // CODEC2_MODE_3200 @ 8 kHz.
        if (!loaded) return@lazy fallback
        runCatching { nativeSamplesPerFrame() }
            .getOrDefault(fallback)
            .coerceAtLeast(1)
    }

    private fun normalizePcmForCodec(pcm: ShortArray): ShortArray {
        if (pcm.isEmpty()) return pcm
        val spf = samplesPerFrameSafe
        if (pcm.size < spf) {
            return pcm.copyOf(spf)
        }
        val wholeFrames = pcm.size / spf
        val usable = wholeFrames * spf
        return if (usable == pcm.size) pcm else pcm.copyOf(usable)
    }

    fun encodePcm8kMono(pcm: ShortArray): ByteArray {
        if (!loaded || pcm.isEmpty()) return ByteArray(0)
        val normalized = normalizePcmForCodec(pcm)
        if (normalized.isEmpty()) return ByteArray(0)
        val core = nativeEncodePcm8kMono(normalized)
        if (core.isEmpty()) return core
        val fecLen = fecLength(core.size)
        val out = ByteArray(4 + core.size + fecLen)
        out[0] = WRAP_M0.toByte()
        out[1] = WRAP_M1.toByte()
        out[2] = ((core.size shr 8) and 0xFF).toByte()
        out[3] = (core.size and 0xFF).toByte()
        System.arraycopy(core, 0, out, 4, core.size)
        if (fecLen > 0) {
            var k = 0
            for (i in 0 until fecLen) {
                val a = core[k].toInt() and 0xFF
                val b = core[(k + 1) % core.size].toInt() and 0xFF
                out[4 + core.size + i] = (a xor b).toByte()
                k = (k + 2) % core.size
            }
        }
        return out
    }

    fun decodeToPcm8kMono(encoded: ByteArray): ShortArray {
        if (!loaded || encoded.isEmpty()) return ShortArray(0)
        val unwrapped = tryUnwrapCore(encoded)
        val forCodec = unwrapped ?: encoded
        return nativeDecodeToPcm8kMono(forCodec)
    }

    /** Если данные в новом формате — вернуть только ядро Codec2, иначе null (сырой поток). */
    private fun tryUnwrapCore(data: ByteArray): ByteArray? {
        if (data.size < 6) return null
        if ((data[0].toInt() and 0xFF) != WRAP_M0 || (data[1].toInt() and 0xFF) != WRAP_M1) return null
        val coreLen = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if (coreLen <= 0 || coreLen > data.size - 4) return null
        val fecLen = fecLength(coreLen)
        if (data.size != 4 + coreLen + fecLen) return null
        return data.copyOfRange(4, 4 + coreLen)
    }
}
