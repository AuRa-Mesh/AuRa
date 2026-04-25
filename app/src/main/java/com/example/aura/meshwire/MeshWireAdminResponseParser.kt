package com.example.aura.meshwire

/**
 * Извлекает ответы [AdminMessage] из кадров [FromRadio], содержащих [MeshPacket].
 *
 * Цепочка развёртки: `FromRadio.packet` (поле 2, для старых прошивок — 1) → `MeshPacket.decoded (4)` →
 * `Data { portnum (1)=ADMIN(6), payload (2)=AdminMessage }` → нужное поле oneof.
 */
object MeshWireAdminResponseParser {

    /**
     * Из байтов **MeshPacket** (поле 11 FromRadio) извлекает тело [AdminMessage].
     * @return payload AdminMessage или `null`, если пакет не admin.
     */
    fun extractAdminPayloadFromMeshPacket(meshPacketBytes: ByteArray): ByteArray? {
        val decodedBytes = extractLengthDelimited(meshPacketBytes, targetField = 4) ?: return null
        var portnum = -1
        var payload: ByteArray? = null
        var i = 0
        while (i < decodedBytes.size) {
            val tag = decodedBytes[i++].toInt() and 0xFF
            val fn = tag ushr 3
            val wt = tag and 0x07
            when (wt) {
                0 -> {
                    val (v, n) = readVarint(decodedBytes, i)
                    i += n
                    if (fn == 1) portnum = v.toInt()
                }
                2 -> {
                    val (len, lb) = readVarint(decodedBytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > decodedBytes.size) break
                    if (fn == 2) payload = decodedBytes.copyOfRange(i, i + ln)
                    i += ln
                }
                5 -> i += 4
                1 -> i += 8
                else -> break
            }
        }
        if (portnum != 6) return null
        return payload
    }

    /** AdminMessage.get_config_response (поле 6) → Config bytes. */
    fun extractConfigResponse(adminBytes: ByteArray): ByteArray? =
        extractLengthDelimited(adminBytes, targetField = 6)

    /** AdminMessage.get_module_config_response (поле 8) → ModuleConfig bytes. */
    fun extractModuleConfigResponse(adminBytes: ByteArray): ByteArray? =
        extractLengthDelimited(adminBytes, targetField = 8)

    /** AdminMessage.get_channel_response (поле 2) → Channel bytes. */
    fun extractChannelResponse(adminBytes: ByteArray): ByteArray? =
        extractLengthDelimited(adminBytes, targetField = 2)

    /** AdminMessage.get_owner_response (поле 4) → User bytes. */
    fun extractOwnerResponse(adminBytes: ByteArray): ByteArray? =
        extractLengthDelimited(adminBytes, targetField = 4)

    private fun extractLengthDelimited(bytes: ByteArray, targetField: Int): ByteArray? {
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fn = tag ushr 3
            val wt = tag and 0x07
            when (wt) {
                0 -> { val (_, n) = readVarint(bytes, i); i += n }
                1 -> i += 8
                5 -> i += 4
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) return null
                    if (fn == targetField) return bytes.copyOfRange(i, i + ln)
                    i += ln
                }
                else -> return null
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
}
