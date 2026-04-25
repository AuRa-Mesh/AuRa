package com.example.aura.meshwire

/**
 * Кадрирование потока Protobuf для TCP/USB-serial, как в прошивке MeshWire ([StreamAPI]) и
 * [org.meshwire.core.network.transport.StreamFrameCodec] (Meshtastic-Android).
 */
class MeshWireStreamFrameCodec(
    private val onPacketReceived: (ByteArray) -> Unit,
) {
    companion object {
        const val START1: Byte = 0x94.toByte()
        const val START2: Byte = 0xc3.toByte()
        const val MAX_TO_FROM_RADIO_SIZE = 512
        const val HEADER_SIZE = 4

        /** Порт TCP-сервиса ноды (тот же, что в MeshWire Android / NetworkConstants). */
        const val DEFAULT_TCP_PORT = 4403

        /** Пробуждение перед обменом (как в официальном клиенте). */
        val WAKE_BYTES = byteArrayOf(START1, START1, START1, START1)

        fun buildFramedPacket(payload: ByteArray): ByteArray {
            require(payload.size <= MAX_TO_FROM_RADIO_SIZE) {
                "ToRadio/FromRadio packet too large: ${payload.size}"
            }
            val out = ByteArray(HEADER_SIZE + payload.size)
            out[0] = START1
            out[1] = START2
            out[2] = (payload.size shr 8).toByte()
            out[3] = (payload.size and 0xff).toByte()
            payload.copyInto(out, HEADER_SIZE)
            return out
        }
    }

    private var ptr = 0
    private var msb = 0
    private var lsb = 0
    private var packetLen = 0
    private val rxPacket = ByteArray(MAX_TO_FROM_RADIO_SIZE)

    fun reset() {
        ptr = 0
        msb = 0
        lsb = 0
        packetLen = 0
    }

    fun processInputBytes(buf: ByteArray, len: Int) {
        for (i in 0 until len) processInputByte(buf[i])
    }

    fun processInputByte(c: Byte) {
        var nextPtr = ptr + 1

        fun lostSync() {
            nextPtr = 0
        }

        fun deliverPacket() {
            onPacketReceived(rxPacket.copyOf(packetLen))
            nextPtr = 0
        }

        when (ptr) {
            0 ->
                if (c != START1) {
                    nextPtr = 0
                }
            1 -> if (c != START2) lostSync()
            2 -> msb = c.toInt() and 0xff
            3 -> {
                lsb = c.toInt() and 0xff
                packetLen = (msb shl 8) or lsb
                if (packetLen > MAX_TO_FROM_RADIO_SIZE) {
                    lostSync()
                } else if (packetLen == 0) {
                    deliverPacket()
                }
            }
            else -> {
                rxPacket[ptr - HEADER_SIZE] = c
                if (ptr - HEADER_SIZE + 1 == packetLen) {
                    deliverPacket()
                }
            }
        }
        ptr = nextPtr
    }
}
