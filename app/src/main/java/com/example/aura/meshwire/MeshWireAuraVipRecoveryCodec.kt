package com.example.aura.meshwire

/**
 * Лёгкий protobuf для **восстановления** VIP-таймера через mesh-сеть.
 *
 * Идея: при переустановке приложения локальные данные теряются, но соседи по mesh уже
 * видели наши регулярные VIP-broadcast'ы ([MeshWireAuraVipCodec], portnum 503) и помнят
 * последнее известное значение `remaining_sec`. При свежем старте мы широковещательно
 * просим их прислать эту информацию обратно и восстанавливаем таймер.
 *
 * Схема payload:
 * ```
 * message AuraVipRecovery {
 *   fixed32 subject_node_num  = 1;  // про какой node_num идёт разговор
 *                                   //   в request: = node_num отправителя (= нас)
 *                                   //   в response: = node_num того, про кого отвечаем
 *   bool    is_response       = 2;  // false → запрос; true → ответ
 *   uint32  remaining_sec     = 3;  // только в response: сколько у subject осталось таймера
 *   bool    unlocked_forever  = 4;  // только в response: терминальное «ограничения сняты»
 *   uint64  app_uptime_sec    = 5;  // опционально в response: последний известный аптайм Aura (сек),
 *                                   // из пакетов portnum 502 ([MeshWireAuraPeerUptimeCodec])
 * }
 * ```
 *
 * Запросы широковещательные (primary-канал, без want_ack). Ответы — unicast обратно
 * отправителю запроса (без want_ack). Обе стороны используют [PORTNUM] = 504.
 */
object MeshWireAuraVipRecoveryCodec {

    /** Зарезервированный порт приложения для recovery-трафика (отдельный от 503, чтобы не смешивать). */
    const val PORTNUM: Int = 504

    data class Payload(
        val subjectNodeNum: UInt,
        val isResponse: Boolean,
        /** В response: seconds of VIP left for [subjectNodeNum] on the responder's записи. */
        val remainingSec: UInt = 0u,
        /** В response: `true` → у [subjectNodeNum] ограничения бессрочно сняты. */
        val unlockedForever: Boolean = false,
        /** В response: последний известный суммарный аптайм приложения subject (сек), portnum 502. */
        val appUptimeSec: Long = 0L,
    )

    fun parsePayload(dataPayload: ByteArray): Payload? {
        if (dataPayload.isEmpty()) return null
        var subject: UInt? = null
        var isResponse: Boolean? = null
        var remaining: UInt? = null
        var unlocked: Boolean? = null
        var uptimeField: Long? = null
        var i = 0
        while (i < dataPayload.size) {
            val tag = dataPayload[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(dataPayload, i)
                    i += n
                    when (fieldNum) {
                        2 -> isResponse = v != 0L
                        3 -> remaining = (v and 0xFFFF_FFFFL).toUInt()
                        4 -> unlocked = v != 0L
                        5 -> uptimeField = v.coerceIn(0L, Long.MAX_VALUE)
                    }
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
                    i += ln
                }
                1 -> i = minOf(i + 8, dataPayload.size)
                else -> {
                    val ni = skipUnknownField(dataPayload, i, wire) ?: return null
                    i = ni
                }
            }
        }
        val s = subject ?: return null
        val r = isResponse ?: return null
        return Payload(
            subjectNodeNum = s,
            isResponse = r,
            remainingSec = remaining ?: 0u,
            unlockedForever = unlocked ?: false,
            appUptimeSec = uptimeField ?: 0L,
        )
    }

    private fun skipUnknownField(bytes: ByteArray, i: Int, wire: Int): Int? =
        when (wire) {
            0 -> {
                val (_, n) = readVarint(bytes, i)
                i + n
            }
            1 -> if (i + 8 <= bytes.size) i + 8 else null
            2 -> {
                val (len, lb) = readVarint(bytes, i)
                val i2 = i + lb
                val ln = len.toInt()
                if (ln < 0 || i2 + ln > bytes.size) null else i2 + ln
            }
            5 -> if (i + 4 <= bytes.size) i + 4 else null
            else -> null
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
