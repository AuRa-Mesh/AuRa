package com.example.aura.meshwire

import kotlin.math.min

/**
 * Значения [ToRadio.want_config_id], совпадающие с официальным клиентом MeshWire Android
 * ([org.meshwire.core.repository.HandshakeConstants]): иначе прошивка не отдаёт полный NodeDB.
 */
object MeshWireWantConfigHandshake {
    /** Этап 1: конфиг, модули, каналы, my_info. */
    const val CONFIG_NONCE = 69420

    /** Этап 2: полный поток NodeInfo для базы узлов. */
    const val NODE_INFO_NONCE = 69421

    /** ToRadio: поле 3 want_config_id (varint). */
    fun encodeToRadioWantConfigId(nonce: Int): ByteArray {
        require(nonce >= 0)
        val tag = 0x18.toByte()
        return byteArrayOf(tag) + encodeVarintUnsigned(nonce.toLong())
    }

    /**
     * Из кадра [FromRadio]: поле 7 `config_complete_id` (varint), как в
     * [meshtastic/web decodePacket](https://github.com/meshtastic/web/blob/main/packages/core/src/utils/transform/decodePacket.ts)
     * при `configCompleteId`.
     */
    fun parseConfigCompleteNonceOrNull(fromRadio: ByteArray): Int? {
        var i = 0
        while (i < fromRadio.size) {
            val tag = fromRadio[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(fromRadio, i)
                    i += n
                    if (fieldNum == 7) return v.toInt()
                }
                5 -> i = min(i + 4, fromRadio.size)
                1 -> i = min(i + 8, fromRadio.size)
                2 -> {
                    val (len, lb) = readVarint(fromRadio, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > fromRadio.size) return null
                    i += ln
                }
                else -> break
            }
        }
        return null
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            shift += 7
            if ((b and 0x80) == 0) break
        }
        return result to (i - start)
    }

    private fun encodeVarintUnsigned(v: Long): ByteArray {
        var x = v
        val out = ArrayList<Byte>(5)
        while (true) {
            val b = (x and 0x7F).toInt()
            x = x ushr 7
            if (x == 0L) {
                out.add(b.toByte())
                break
            }
            out.add((b or 0x80).toByte())
        }
        return out.toByteArray()
    }
}
