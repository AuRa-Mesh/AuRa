package com.example.aura.meshwire

/**
 * Лёгкий protobuf-payload VIP-статуса Aura, который рассылается широковещательно на primary-канал
 * (portnum вне официальных mesh PortNum enum — только для клиентов Aura).
 *
 * Схема payload:
 * ```
 * message AuraVip {
 *   fixed32 node_num         = 1;  // логический nodeNum отправителя
 *   bool    active           = 2;  // true = VIP активен; false = VIP снят
 *   uint32  valid_for_sec    = 3;  // сколько это состояние считать валидным (≈ heartbeat × 2)
 *   uint32  remaining_sec    = 4;  // (Aura v2) сколько реально осталось таймера у отправителя;
 *                                  //           используется для восстановления таймера после
 *                                  //           переустановки — см. [MeshWireAuraVipRecoveryCodec]
 *   bool    unlocked_forever = 5;  // (Aura v2) терминальное состояние «ограничений нет» (пароль)
 *   uint64  lifetime_au       = 6;  // (Aura) самоотчёт: суммарный опыт отправителя (для mesh-recovery)
 *   uint32  hof_sig_full      = 7;  // (Aura v3) сколько медалей категории «Связист» закрыто на макс. (0–5)
 *   uint32  hof_eng_full      = 8;  // «Инженер»
 *   uint32  hof_map_full      = 9;  // «Картограф»
 *   uint32  hof_vip_full      = 10; // «VIP»
 *   bytes   hof_stats_packed  = 11; // (legacy) 20×uint64 — старые клиенты; игнорируется
 * }
 * ```
 *
 * Используется вместе с [MeshWireLoRaToRadioEncoder.encodeAuraVipBroadcast]. Приём — в
 * [com.example.aura.mesh.repository.MeshIncomingChatRepository], кэш — в
 * [com.example.aura.vip.VipStatusStore] с собственной TTL-логикой.
 *
 * Поля 4 и 5 опциональны: старые клиенты их игнорируют, новые — сохраняют
 * [Payload.remainingSec] / [Payload.unlockedForever] для восстановления своего VIP-таймера
 * после полной переустановки приложения.
 *
 * Поля 7–10 опциональны: самоотчёт «сколько медалей в категории закрыто на максимум» для чужого
 * профиля без отдельного запроса (см. [com.example.aura.vip.VipStatusStore]).
 */
object MeshWireAuraVipCodec {

    /** Старые клиенты: 20×uint64; игнорируем содержимое. */
    const val LEGACY_HOF_PACKED_BYTE_LEN: Int = 160

    /** Зарезервированный порт приложения (вне core Portnums прошивки). */
    const val PORTNUM: Int = 503

    data class Payload(
        val nodeNum: UInt,
        val active: Boolean,
        /** Через сколько секунд считать запись устаревшей, если подтверждения больше не поступают. */
        val validForSec: UInt,
        /** Самоотчёт отправителя: сколько секунд VIP-таймера у него ещё осталось. 0 = не передал / истёк. */
        val remainingSec: UInt = 0u,
        /** `true` → отправитель бессрочно снял ограничения паролем разблокировки. */
        val unlockedForever: Boolean = false,
        /** Самоотчёт: суммарный опыт за всё время у отправителя (0 = не передано / старый клиент). */
        val lifetimeExperience: Long = 0L,
        /**
         * Самоотчёт: число медалей в категории, полностью закрытых на всех ступенях (0–5 по каждой).
         * `null` — поля 7–10 в пакете отсутствуют (старый клиент).
         */
        val hofCategoryFullMedals: IntArray? = null,
        val hofPackedMedalStats: ByteArray? = null,
    )

    fun parsePayload(dataPayload: ByteArray): Payload? {
        if (dataPayload.isEmpty()) return null
        var node: UInt? = null
        var active: Boolean? = null
        var validFor: UInt? = null
        var remaining: UInt? = null
        var unlocked: Boolean? = null
        var lifeAuField: Long? = null
        var h0: Int? = null
        var h1: Int? = null
        var h2: Int? = null
        var h3: Int? = null
        var hofPacked: ByteArray? = null
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
                        2 -> active = v != 0L
                        3 -> validFor = (v and 0xFFFF_FFFFL).toUInt()
                        4 -> remaining = (v and 0xFFFF_FFFFL).toUInt()
                        5 -> unlocked = v != 0L
                        6 -> lifeAuField = v.coerceAtLeast(0L)
                        7 -> h0 = (v and 0xFFFF_FFFFL).toInt().coerceIn(0, 5)
                        8 -> h1 = (v and 0xFFFF_FFFFL).toInt().coerceIn(0, 5)
                        9 -> h2 = (v and 0xFFFF_FFFFL).toInt().coerceIn(0, 5)
                        10 -> h3 = (v and 0xFFFF_FFFFL).toInt().coerceIn(0, 5)
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
                    if (fieldNum == 1) node = v
                }
                2 -> {
                    val (len, lb) = readVarint(dataPayload, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > dataPayload.size) return null
                    if (fieldNum == 11 && ln == LEGACY_HOF_PACKED_BYTE_LEN) {
                        hofPacked = dataPayload.copyOfRange(i, i + ln)
                    }
                    i += ln
                }
                1 -> i = minOf(i + 8, dataPayload.size)
                else -> return null
            }
        }
        val n = node ?: return null
        val a = active ?: return null
        val hof = if (h0 == null && h1 == null && h2 == null && h3 == null) {
            null
        } else {
            intArrayOf(h0 ?: 0, h1 ?: 0, h2 ?: 0, h3 ?: 0)
        }
        return Payload(
            nodeNum = n,
            active = a,
            validForSec = validFor ?: DEFAULT_VALID_FOR_SEC,
            remainingSec = remaining ?: 0u,
            unlockedForever = unlocked ?: false,
            lifetimeExperience = lifeAuField ?: 0L,
            hofCategoryFullMedals = hof,
            hofPackedMedalStats = hofPacked,
        )
    }

    /** TTL по умолчанию, если в пакете нет поля `valid_for_sec` (или оно 0). */
    const val DEFAULT_VALID_FOR_SEC: UInt = 3_900u // ~65 min (heartbeat 30 мин × 2 + запас)

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
