package com.example.aura.voice

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Запись моно PCM 16-bit little-endian в WAV (для внутреннего хранилища истории). */
object Pcm16MonoWavWriter {

    fun write(file: File, pcm16: ShortArray, sampleRateHz: Int) {
        file.parentFile?.mkdirs()
        val dataBytes = pcm16.size * 2
        val riffSize = 36 + dataBytes
        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray())
            out.write(int32Le(riffSize))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(int32Le(16))
            out.write(int16Le(1))
            out.write(int16Le(1))
            out.write(int32Le(sampleRateHz))
            out.write(int32Le(sampleRateHz * 2))
            out.write(int16Le(2))
            out.write(int16Le(16))
            out.write("data".toByteArray())
            out.write(int32Le(dataBytes))
            val buf = ByteBuffer.allocate(pcm16.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm16) buf.putShort(s)
            out.write(buf.array())
        }
    }

    private fun int32Le(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun int16Le(v: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((v and 0xFFFF).toShort()).array()
}
