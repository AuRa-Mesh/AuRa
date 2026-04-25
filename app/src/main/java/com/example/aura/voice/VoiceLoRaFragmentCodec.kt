package com.example.aura.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Фрагментация сжатого голоса для LoRa: заголовок + куски полезной нагрузки.
 *
 * Формат пакета ([TOTAL_PACKET_MAX] байт на эфир, вместе с заголовком):
 * - [0..1]: magic 0xA5, 0x56
 * - [2..5]: recordId (big-endian UInt)
 * - [6..7]: fragmentIndex (big-endian UShort)
 * - [8..9]: totalFragments (big-endian UShort)
 * - [10..11]: reserved (0)
 * - [12..]: payload (до [MAX_PAYLOAD] байт)
 */
object VoiceLoRaFragmentCodec {

    const val TOTAL_PACKET_MAX: Int = 200
    private const val HEADER_SIZE: Int = 12
    const val MAX_PAYLOAD: Int = TOTAL_PACKET_MAX - HEADER_SIZE
    private const val MAGIC0: Byte = 0xA5.toByte()
    private const val MAGIC1: Byte = 0x56.toByte()

    fun buildPackets(recordId: UInt, payload: ByteArray): List<ByteArray> {
        if (payload.isEmpty()) return emptyList()
        val total = (payload.size + MAX_PAYLOAD - 1) / MAX_PAYLOAD
        if (total <= 0) return emptyList()
        val out = ArrayList<ByteArray>(total)
        var off = 0
        var idx = 0
        while (off < payload.size) {
            val len = minOf(MAX_PAYLOAD, payload.size - off)
            val buf = ByteBuffer.allocate(HEADER_SIZE + len)
            buf.put(MAGIC0)
            buf.put(MAGIC1)
            buf.putInt(recordId.toInt())
            buf.putShort(idx.toShort())
            buf.putShort(total.toShort())
            buf.putShort(0)
            buf.put(payload, off, len)
            out.add(buf.array())
            off += len
            idx++
        }
        return out
    }

    fun tryParsePacket(bytes: ByteArray): VoiceFragment? {
        if (bytes.size <= HEADER_SIZE) return null
        if (bytes[0] != MAGIC0 || bytes[1] != MAGIC1) return null
        val bb = ByteBuffer.wrap(bytes)
        bb.position(2)
        val rid = bb.int.toUInt()
        val part = bb.short.toInt() and 0xFFFF
        val total = bb.short.toInt() and 0xFFFF
        bb.short // reserved
        if (total <= 0 || part !in 0 until total) return null
        val pl = ByteArray(bytes.size - HEADER_SIZE)
        System.arraycopy(bytes, HEADER_SIZE, pl, 0, pl.size)
        return VoiceFragment(rid, part, total, pl)
    }

    data class VoiceFragment(
        val recordId: UInt,
        val index: Int,
        val total: Int,
        val payload: ByteArray,
    )

    class Reassembler {
        private data class Session(val total: Int, val parts: Array<ByteArray?>)
        private val sessions = HashMap<String, Session>()

        fun ingest(fromNode: UInt?, fragment: VoiceFragment): ByteArray? {
            val fromKey = (fromNode?.toLong() ?: 0L).toString()
            val key = "$fromKey:${fragment.recordId}"
            val slot = sessions.getOrPut(key) {
                Session(fragment.total, arrayOfNulls(fragment.total))
            }
            if (slot.total != fragment.total) {
                sessions.remove(key)
                return null
            }
            if (fragment.index >= slot.parts.size) return null
            if (slot.parts[fragment.index] != null) return null
            slot.parts[fragment.index] = fragment.payload
            if (slot.parts.any { it == null }) return null
            val bos = ByteArrayOutputStream()
            for (p in slot.parts) {
                if (p != null) bos.write(p)
            }
            sessions.remove(key)
            return bos.toByteArray()
        }
    }
}
