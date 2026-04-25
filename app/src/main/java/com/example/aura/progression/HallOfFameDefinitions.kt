package com.example.aura.progression

/**
 * Категории и медали «Аллеи славы» (5 уровней на медаль).
 * Пороги — накопленное значение счётчика для достижения уровня 1..5.
 */
object HallOfFameKeys {
    // Связист
    const val SIG_MESH_MESSAGES = "sig_mesh_messages"
    const val SIG_HOP_ACK_SUM = "sig_hop_ack_sum"
    const val SIG_REACTIONS_ON_MINE = "sig_reactions_on_mine"
    const val SIG_POLLS_3NODES = "sig_polls_3nodes"
    const val SIG_UPTIME_APP_MS = "sig_uptime_app_ms"

    // Инженер
    const val ENG_NODE_CARDS = "eng_node_cards"
    const val ENG_NODE_INFO_PREFETCH = "eng_node_info_prefetch"
    const val ENG_TOTAL_XP_MIRROR = "eng_total_xp_mirror"
    const val ENG_NAME_CHANGES = "eng_name_changes"
    const val ENG_FAV_DISTINCT_PEERS = "eng_fav_distinct_peers"

    // Картограф
    const val MAP_BEACONS_PLACED = "map_beacons_placed"
    const val MAP_BEACON_LINK_CLICKS = "map_beacon_link_clicks"
    const val MAP_BEACON_IMPORTS = "map_beacon_imports"
    const val MAP_POSITION_PACKETS = "map_position_packets"
    const val MAP_BEACON_SYNC_ACTS = "map_beacon_sync_acts"

    // VIP
    const val VIP_OWNERSHIP_MS = "vip_ownership_ms"
    const val VIP_CODES_REDEEMED = "vip_codes_redeemed"
    const val VIP_DEMO_UNLOCK_USES = "vip_demo_unlock_uses"
    const val VIP_MATRIX_MS = "vip_matrix_ms"
    const val VIP_BROADCASTS_SENT = "vip_broadcasts_sent"
}

enum class HallOfFameCategoryId { SIGNAL, ENGINEER, CARTOGRAPHER, VIP }

data class HallOfFameMedalDef(
    val id: String,
    val title: String,
    val description: String,
    val statKey: String,
    /** Пороги для уровней 1…5 (накопительное значение). */
    val thresholds: LongArray,
) {
    fun tierForValue(value: Long): Int {
        var tier = 0
        thresholds.forEachIndexed { i, t ->
            if (value >= t) tier = i + 1
        }
        return tier.coerceIn(0, thresholds.size)
    }

    fun progressInCurrentTier(value: Long): Float {
        val t = tierForValue(value)
        if (t >= thresholds.size) return 1f
        val prev = if (t <= 0) 0L else thresholds[t - 1]
        val next = thresholds[t]
        val span = (next - prev).coerceAtLeast(1L)
        return ((value - prev).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    /** Все 5 ступеней медали пройдены (порог последнего уровня достигнут). */
    fun isFullyComplete(value: Long): Boolean = tierForValue(value) >= thresholds.size

    /**
     * Для текущей ступени медали: **накопленное значение** и **целевой порог** следующей ступени
     * (счётчики накопительные). При полном завершении оба — последний порог.
     */
    fun currentProgressNumeratorDenominator(value: Long): Pair<Long, Long> {
        val v = value.coerceAtLeast(0L)
        val t = tierForValue(v)
        if (t >= thresholds.size) {
            val last = thresholds.last()
            return last to last
        }
        return v to thresholds[t]
    }
}

data class HallOfFameCategoryDef(
    val id: HallOfFameCategoryId,
    val title: String,
    val medals: List<HallOfFameMedalDef>,
) {
    /** Сколько медалей в категории полностью закрыто (все 5 уровней), порядок не важен. */
    fun countFullyCompletedMedals(statLookup: (String) -> Long): Int =
        medals.count { m -> m.isFullyComplete(statLookup(m.statKey)) }
}

object HallOfFameCatalog {
    private val h = longArrayOf(5L, 25L, 100L, 500L, 1000L)
    private val hop = longArrayOf(10L, 50L, 200L, 1000L, 5000L)
    private val react = longArrayOf(10L, 50L, 250L, 1000L, 5000L)
    private val poll = longArrayOf(3L, 10L, 30L, 100L, 300L)
    private val uptime = longArrayOf(
        24L * 3600_000L,
        7L * 24L * 3600_000L,
        30L * 24L * 3600_000L,
        90L * 24L * 3600_000L,
        365L * 24L * 3600_000L,
    )

    private val nodes = longArrayOf(5L, 20L, 100L, 500L, 2000L)
    private val cfg = longArrayOf(5L, 25L, 100L, 500L, 1000L)
    private val xpVet = longArrayOf(1000L, 3000L, 10_000L, 50_000L, 100_000L)
    private val names = longArrayOf(3L, 10L, 15L, 50L, 100L)
    private val fav = longArrayOf(1L, 3L, 10L, 30L, 100L)

    private val beacon = longArrayOf(5L, 25L, 100L, 500L, 1000L)
    private val nav = longArrayOf(10L, 50L, 200L, 800L, 2000L)
    private val imp = longArrayOf(3L, 15L, 50L, 150L, 500L)
    private val pos = longArrayOf(10L, 100L, 500L, 2000L, 10_000L)
    private val sync = longArrayOf(2L, 5L, 20L, 100L, 500L)

    private val gold = longArrayOf(
        30L * 24L * 3600_000L,
        90L * 24L * 3600_000L,
        180L * 24L * 3600_000L,
        365L * 24L * 3600_000L,
        3L * 365L * 24L * 3600_000L,
    )
    private val codes = longArrayOf(1L, 3L, 10L, 25L, 50L)
    private val demo = longArrayOf(10L, 100L, 500L, 2000L, 10_000L)
    private val matrix = longArrayOf(
        3600_000L,
        24L * 3600_000L,
        7L * 24L * 3600_000L,
        30L * 24L * 3600_000L,
        100L * 24L * 3600_000L,
    )
    private val air = longArrayOf(5L, 20L, 100L, 500L, 2000L)

    val CATEGORIES: List<HallOfFameCategoryDef> = listOf(
        HallOfFameCategoryDef(
            id = HallOfFameCategoryId.SIGNAL,
            title = "Связист",
            medals = listOf(
                HallOfFameMedalDef(
                    "sig_voice",
                    "Голос эфира",
                    "Отправить сообщения через Aura-mesh.",
                    HallOfFameKeys.SIG_MESH_MESSAGES,
                    h,
                ),
                HallOfFameMedalDef(
                    "sig_relay",
                    "Ретранслятор",
                    "Сумма hop при доставке ваших сообщений.",
                    HallOfFameKeys.SIG_HOP_ACK_SUM,
                    hop,
                ),
                HallOfFameMedalDef(
                    "sig_opinion",
                    "Лидер мнений",
                    "Реакции на ваши сообщения в каналах.",
                    HallOfFameKeys.SIG_REACTIONS_ON_MINE,
                    react,
                ),
                HallOfFameMedalDef(
                    "sig_poll",
                    "Опросник",
                    "Опросы с участием ≥3 узлов.",
                    HallOfFameKeys.SIG_POLLS_3NODES,
                    poll,
                ),
                HallOfFameMedalDef(
                    "sig_online",
                    "Всегда на связи",
                    "Непрерывный аптайм приложения.",
                    HallOfFameKeys.SIG_UPTIME_APP_MS,
                    uptime,
                ),
            ),
        ),
        HallOfFameCategoryDef(
            id = HallOfFameCategoryId.ENGINEER,
            title = "Инженер",
            medals = listOf(
                HallOfFameMedalDef(
                    "eng_watch",
                    "Смотритель узлов",
                    "Уникальные ноды в кэше карточек.",
                    HallOfFameKeys.ENG_NODE_CARDS,
                    nodes,
                ),
                HallOfFameMedalDef(
                    "eng_cfg",
                    "Мастер конфигураций",
                    "Запросы Node Info и префетч настроек.",
                    HallOfFameKeys.ENG_NODE_INFO_PREFETCH,
                    cfg,
                ),
                HallOfFameMedalDef(
                    "eng_vet",
                    "Ветеран Mesh",
                    "Накопленный опыт (ОП).",
                    HallOfFameKeys.ENG_TOTAL_XP_MIRROR,
                    xpVet,
                ),
                HallOfFameMedalDef(
                    "eng_collect",
                    "Коллекционер",
                    "Смены LongName / ShortName.",
                    HallOfFameKeys.ENG_NAME_CHANGES,
                    names,
                ),
                HallOfFameMedalDef(
                    "eng_sensei",
                    "Сенсей",
                    "Избранные пиры в группах.",
                    HallOfFameKeys.ENG_FAV_DISTINCT_PEERS,
                    fav,
                ),
            ),
        ),
        HallOfFameCategoryDef(
            id = HallOfFameCategoryId.CARTOGRAPHER,
            title = "Картограф",
            medals = listOf(
                HallOfFameMedalDef(
                    "map_pioneer",
                    "Первопроходец",
                    "Установка собственных маяков.",
                    HallOfFameKeys.MAP_BEACONS_PLACED,
                    beacon,
                ),
                HallOfFameMedalDef(
                    "map_nav",
                    "Навигатор",
                    "Переходы по чужим маякам из чата.",
                    HallOfFameKeys.MAP_BEACON_LINK_CLICKS,
                    nav,
                ),
                HallOfFameMedalDef(
                    "map_scout",
                    "Следопыт",
                    "Импорт маяков по ссылкам.",
                    HallOfFameKeys.MAP_BEACON_IMPORTS,
                    imp,
                ),
                HallOfFameMedalDef(
                    "map_radius",
                    "Радиус обзора",
                    "Передача своей позиции в mesh.",
                    HallOfFameKeys.MAP_POSITION_PACKETS,
                    pos,
                ),
                HallOfFameMedalDef(
                    "map_sync",
                    "Синхронизатор",
                    "Успешная синхронизация маяков.",
                    HallOfFameKeys.MAP_BEACON_SYNC_ACTS,
                    sync,
                ),
            ),
        ),
        HallOfFameCategoryDef(
            id = HallOfFameCategoryId.VIP,
            title = "VIP",
            medals = listOf(
                HallOfFameMedalDef(
                    "vip_gold",
                    "Золотая рамка",
                    "Время владения VIP.",
                    HallOfFameKeys.VIP_OWNERSHIP_MS,
                    gold,
                ),
                HallOfFameMedalDef(
                    "vip_patron",
                    "Меценат",
                    "Введённые коды продления.",
                    HallOfFameKeys.VIP_CODES_REDEEMED,
                    codes,
                ),
                HallOfFameMedalDef(
                    "vip_free",
                    "Без границ",
                    "Использования вложений и голоса (не демо).",
                    HallOfFameKeys.VIP_DEMO_UNLOCK_USES,
                    demo,
                ),
                HallOfFameMedalDef(
                    "vip_matrix",
                    "Матричный житель",
                    "Время с активной темой Matrix-rain.",
                    HallOfFameKeys.VIP_MATRIX_MS,
                    matrix,
                ),
                HallOfFameMedalDef(
                    "vip_air",
                    "Эфирный маркер",
                    "Успешные VIP-трансляции в эфир.",
                    HallOfFameKeys.VIP_BROADCASTS_SENT,
                    air,
                ),
            ),
        ),
    )
}

/** Подписи «уже / нужно» для строк медалей и похожих мест. */
object HallOfFameProgressLabels {

    private val MS_STATS = setOf(
        HallOfFameKeys.SIG_UPTIME_APP_MS,
        HallOfFameKeys.VIP_OWNERSHIP_MS,
        HallOfFameKeys.VIP_MATRIX_MS,
    )

    fun medalRatioCaption(medal: HallOfFameMedalDef, value: Long): String {
        val (n, d) = medal.currentProgressNumeratorDenominator(value)
        if (d <= 0L) return "0 / 0"
        return if (medal.statKey in MS_STATS) {
            "${formatMs(n)} / ${formatMs(d)}"
        } else {
            "$n / $d"
        }
    }

    private fun formatMs(ms: Long): String {
        val v = ms.coerceAtLeast(0L)
        val days = v / 86_400_000L
        val hours = (v % 86_400_000L) / 3_600_000L
        if (days > 0L) return "${days}д ${hours}ч"
        val minutes = (v % 3_600_000L) / 60_000L
        if (hours > 0L) return "${hours}ч ${minutes}м"
        val sec = (v % 60_000L) / 1000L
        if (minutes > 0L) return "${minutes}м ${sec}с"
        return "${sec}с"
    }
}
