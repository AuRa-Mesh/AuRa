package com.example.aura.meshwire

/**
 * Трансляция по mesh «какие коды продления VIP уже использованы» — чтобы факт одноразовости
 * кода переживал переустановку приложения **без** доступа к медиа-файлам.
 *
 * Каждый Aura-клиент:
 *  - при успешном вводе кода шлёт широковещательный [Kind.ANNOUNCE] с хэшем;
 *  - запоминает пары `(subject_node_num → set<hash>)` у себя в
 *    [com.example.aura.vip.VipUsedCodesMeshStore] и при повторном запросе ([Kind.REQUEST])
 *    от того же subject'а присылает обратно [Kind.RESPONSE] со всеми помнимыми хэшами.
 *
 * Безопасность: по эфиру ходит **8-байтовый усечённый SHA-256** нормализованного кода, а не сам
 * код. Это не даёт подсмотрев пакет повторно использовать чужой код (он и так привязан к
 * `nodeId` получателя через HMAC в [com.example.aura.security.VipExtensionCodeCodec]), но делает
 * пассивное прослушивание совсем бесполезным.
 *
 * Схема payload:
 * ```
 * message AuraVipUsedCodes {
 *   fixed32        subject_node_num = 1;  // чей список обсуждаем
 *   uint32         kind             = 2;  // 0 ANNOUNCE | 1 REQUEST | 2 RESPONSE
 *   repeated bytes code_hash        = 3;  // каждый hash — ровно 8 байт
 * }
 * ```
 *
 * Размер: 16 хэшей × ≈10 байт + overhead ≲ 200 байт — укладывается в один LoRa-пакет.
 */
object MeshWireAuraVipUsedCodesCodec {

    /** Зарезервированный порт приложения (вне core Portnums прошивки). */
    const val PORTNUM: Int = 505

    /** Длина транслируемого хэша в байтах. Усечённый SHA-256. */
    const val HASH_LEN: Int = 8

    /**
     * Одного пакета хватает на столько хэшей (ограничение выбрано с запасом от LoRa-MTU).
     * Если у узла помнится больше — пришлёт «хвост», который чаще всего уже есть у получателя.
     */
    const val MAX_HASHES_PER_PACKET: Int = 16

    enum class Kind(val wire: Int) {
        ANNOUNCE(0),
        REQUEST(1),
        RESPONSE(2),
        ;
        companion object {
            fun parse(v: Int): Kind? = entries.firstOrNull { it.wire == v }
        }
    }

    data class Payload(
        val subjectNodeNum: UInt,
        val kind: Kind,
        val hashes: List<ByteArray>,
    )

    fun parsePayload(dataPayload: ByteArray): Payload? {
        if (dataPayload.isEmpty()) return null
        var subject: UInt? = null
        var kind: Kind? = null
        val hashes = ArrayList<ByteArray>()
        var i = 0
        while (i < dataPayload.size) {
            val tag = dataPayload[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(dataPayload, i)
                    i += n
                    if (fieldNum == 2) kind = Kind.parse((v and 0xFFFF_FFFFL).toInt())
                }
                5 -> {
                    if (i + 4 > dataPayload.size) return null
                    val v = (
                        (dataPayload[i].toInt() and 0xFF) or
                            ((dataPayload[i + 1].toInt() and 0xFF) shl 8) or
                            ((dataPayload[i + 2].toInt() and 0xFF) shl 16) or
                            ((dataPayload[i + 3].toInt() and 0xFF) shl 24)
                        ).toUInt()
                    i += 4
                    if (fieldNum == 1) subject = v
                }
                2 -> {
                    val (len, lb) = readVarint(dataPayload, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > dataPayload.size) return null
                    if (fieldNum == 3 && ln == HASH_LEN) {
                        hashes.add(dataPayload.copyOfRange(i, i + ln))
                    }
                    i += ln
                }
                1 -> i = minOf(i + 8, dataPayload.size)
                else -> return null
            }
        }
        val s = subject ?: return null
        val k = kind ?: return null
        return Payload(subjectNodeNum = s, kind = k, hashes = hashes)
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
