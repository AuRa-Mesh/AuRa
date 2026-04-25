package com.example.aura.vip

import android.content.Context
import android.util.Base64
import com.example.aura.meshwire.MeshWireAuraVipCodec
import com.example.aura.meshwire.MeshWireNodeNum
import com.example.aura.progression.HallOfFameWireStats
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Кэш VIP-статуса **других** узлов для отображения рамки и для восстановления своего
 * таймера через mesh после переустановки.
 *
 * Источник обновления — исключительно широковещание [MeshWireAuraVipCodec.PORTNUM] (портномер 503
 * без участия прошивки) и unicast-ответы [com.example.aura.meshwire.MeshWireAuraVipRecoveryCodec]
 * (портномер 504).
 *
 * Хранятся таблицы:
 *   1. [vipDeadlineMs] — «свежесть» последнего подтверждения по эфиру: `now + valid_for_sec`
 *      (по умолчанию ~65 мин). Раньше по нему одному решалась рамка — из‑за этого рамка
 *      пропадала, если пакеты не ловились дольше TTL, хотя у пира VIP ещё был.
 *   2. [peerTimerDeadlineMs] — абсолютный момент истечения *самого* VIP-таймера у пира
 *      (self-report `remaining_sec`). Используется для recovery-ответов (504) **и** для UI:
 *      [hasVip] показывает рамку, пока `peerTimerDeadlineMs > now` или пока не истёк
 *      [vipDeadlineMs] у клиентов без поля `remaining_sec`.
 *   3. [peerHofCategoryComplete] — самоотчёт пира из полей 7–10 VIP-broadcast (медали по категориям).
 *   4. [peerHofPackedStats] — упакованные счётчики всех медалей (поле 11 VIP-broadcast) для recovery 504.
 *
 * @see VipStatusBroadcaster — отправка собственных VIP-broadcast кадров.
 * @see com.example.aura.vip.VipMeshRecoveryCoordinator — приёмник унаследованных значений.
 */
object VipStatusStore {

    private const val PREFS = "vip_peer_status"
    private const val KEY_ENTRIES = "peer_vip_entries_v2"
    private const val KEY_TIMER_ENTRIES = "peer_vip_timer_entries_v1"
    /** Подсказки суммарного опыта пиров (поле 6 в VIP-broadcast) для ответов mesh-recovery 504. */
    private const val KEY_LIFETIME_AU_HINTS = "peer_aura_lifetime_au_hints_v1"
    /** Самоотчёт пира: сколько медалей в каждой из 4 категорий закрыто на макс. (поля 7–10 в VIP-broadcast). */
    private const val KEY_PEER_HOF_CATEGORIES = "peer_hof_cat_counts_v1"
    /** Упакованные счётчики медалей (Base64), для ответов mesh-recovery. */
    private const val KEY_PEER_HOF_PACKED = "peer_hof_packed_v1"
    private const val LEGACY_KEY_CONFIRMED_VIP_SET = "confirmed_vip_peer_set_v1"

    /** Запрет аномально длинного TTL (≈ 3 ч): защита от подделанных огромных значений. */
    private const val MAX_TTL_MS: Long = 3L * 60L * 60L * 1000L

    /**
     * Верхняя граница «разумного» VIP-таймера пира. 400 суток хватает с запасом для
     * годовых продлений; всё сверху — вероятнее всего мусор/подделка.
     */
    private const val MAX_PEER_TIMER_MS: Long = 400L * 24L * 60L * 60L * 1000L

    /** Сколько мы помним per-peer `remaining_sec` после того, как от пира перестали приходить пакеты. */
    private const val PEER_TIMER_RETENTION_MS: Long = 30L * 24L * 60L * 60L * 1000L // 30 суток

    /** Верхняя граница «разумного» опыта пира (защита от мусора в эфире). */
    private const val MAX_PEER_LIFETIME_EXPERIENCE: Long = 1_000_000_000L

    /** `nodeNum & 0xFFFFFFFF` → абсолютный `System.currentTimeMillis()`-дедлайн VIP-записи. */
    private val vipDeadlineMs = ConcurrentHashMap<Long, Long>()

    /**
     * `nodeNum & 0xFFFFFFFF` → абсолютный момент истечения VIP-таймера, самоотчёт пира.
     * Заполняется когда пир присылает поле `remaining_sec > 0`.
     */
    private val peerTimerDeadlineMs = ConcurrentHashMap<Long, Long>()

    /** `nodeNum` → первоначальный момент последней записи (для гигиены: удаляем старое). */
    private val peerTimerUpdatedAtMs = ConcurrentHashMap<Long, Long>()

    /** `nodeNum` → максимальный известный суммарный опыт пира (самоотчёт из VIP-broadcast). */
    private val peerLifetimeExperience = ConcurrentHashMap<Long, Long>()

    /** `nodeNum` → время последнего обновления [peerLifetimeExperience]. */
    private val peerLifetimeExperienceUpdatedAtMs = ConcurrentHashMap<Long, Long>()

    /** `nodeNum` → [связист, инженер, картограф, VIP] — число медалей в категории, закрытых на макс. */
    private val peerHofCategoryComplete = ConcurrentHashMap<Long, IntArray>()

    /** `nodeNum` → время последнего обновления [peerHofCategoryComplete]. */
    private val peerHofCategoryUpdatedAtMs = ConcurrentHashMap<Long, Long>()

    /** `nodeNum` → [HallOfFameWireStats.PACKED_BYTE_LEN] байт (поле 11). */
    private val peerHofPackedStats = ConcurrentHashMap<Long, ByteArray>()

    private val peerHofPackedUpdatedAtMs = ConcurrentHashMap<Long, Long>()

    @Volatile
    private var loaded: Boolean = false

    private val _revision = MutableStateFlow(0)
    /** Инкрементируется на каждое изменение — используется для ре-композиции UI. */
    val revision: StateFlow<Int> get() = _revision

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()
            prefs.getStringSet(KEY_ENTRIES, emptySet())?.forEach { s ->
                parseEntry(s)?.let { (node, deadline) ->
                    if (deadline > nowMs) vipDeadlineMs[node] = deadline
                }
            }
            prefs.getStringSet(KEY_TIMER_ENTRIES, emptySet())?.forEach { s ->
                parseTimerEntry(s)?.let { (node, deadline, updatedAt) ->
                    // Записи «совсем старые без подтверждений» не тянем в память.
                    if (updatedAt + PEER_TIMER_RETENTION_MS >= nowMs && deadline > nowMs) {
                        peerTimerDeadlineMs[node] = deadline
                        peerTimerUpdatedAtMs[node] = updatedAt
                    }
                }
            }
            prefs.getStringSet(KEY_LIFETIME_AU_HINTS, emptySet())?.forEach { s ->
                parseLifetimeExperienceHint(s)?.let { (node, xp, updatedAt) ->
                    if (updatedAt + PEER_TIMER_RETENTION_MS >= nowMs && xp > 0L) {
                        peerLifetimeExperience[node] = xp
                        peerLifetimeExperienceUpdatedAtMs[node] = updatedAt
                    }
                }
            }
            prefs.getStringSet(KEY_PEER_HOF_CATEGORIES, emptySet())?.forEach { s ->
                parseHofCategoryEntry(s)?.let { (node, counts, updatedAt) ->
                    if (updatedAt + PEER_TIMER_RETENTION_MS >= nowMs) {
                        peerHofCategoryComplete[node] = counts
                        peerHofCategoryUpdatedAtMs[node] = updatedAt
                    }
                }
            }
            prefs.getStringSet(KEY_PEER_HOF_PACKED, emptySet())?.forEach { s ->
                parseHofPackedEntry(s)?.let { (node, bytes, updatedAt) ->
                    if (updatedAt + PEER_TIMER_RETENTION_MS >= nowMs && bytes.size == HallOfFameWireStats.PACKED_BYTE_LEN) {
                        peerHofPackedStats[node] = bytes
                        peerHofPackedUpdatedAtMs[node] = updatedAt
                    }
                }
            }
            if (prefs.contains(LEGACY_KEY_CONFIRMED_VIP_SET)) {
                prefs.edit().remove(LEGACY_KEY_CONFIRMED_VIP_SET).apply()
            }
            loaded = true
            _revision.value = _revision.value + 1
        }
    }

    /**
     * Применить принятое по эфиру VIP-широковещание.
     *
     * @param validForSec TTL (UI-рамка), из payload; 0 → [MeshWireAuraVipCodec.DEFAULT_VALID_FOR_SEC].
     * @param remainingSec самоотчёт пира о своём таймере (0 = информации нет).
     * @param lifetimeExperience самоотчёт пира о суммарном опыте (0 = не передано).
     * @param hofCategoryFullMedals самоотчёт по категориям Аллеи славы (4×0–5); `null` — поля 7–10 отсутствуют.
     * @param hofPackedMedalStats поле 11 — упакованные счётчики 20 медалей; `null` — не передано.
     */
    fun applyBroadcast(
        context: Context,
        nodeNum: UInt?,
        active: Boolean,
        validForSec: UInt,
        remainingSec: UInt = 0u,
        lifetimeExperience: Long = 0L,
        hofCategoryFullMedals: IntArray? = null,
        hofPackedMedalStats: ByteArray? = null,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (nodeNum == null || nodeNum == 0u) return
        ensureLoaded(context)
        val key = nodeNum.toLong() and 0xFFFF_FFFFL
        var hintDirty = false
        if (lifetimeExperience > 0L) {
            val capped = lifetimeExperience.coerceIn(1L, MAX_PEER_LIFETIME_EXPERIENCE)
            val prevXp = peerLifetimeExperience[key] ?: 0L
            val mergedXp = maxOf(prevXp, capped)
            peerLifetimeExperience[key] = mergedXp
            val prevTs = peerLifetimeExperienceUpdatedAtMs[key] ?: 0L
            peerLifetimeExperienceUpdatedAtMs[key] = nowMs
            hintDirty = mergedXp != prevXp || kotlin.math.abs(nowMs - prevTs) > 60_000L
        }
        if (!active) {
            // Пир явно сообщил VIP=off — сбрасываем всё, иначе [peerTimerDeadlineMs] мог
            // бы тянуть устаревшую «рамку» после реального истечения у них.
            var c = vipDeadlineMs.remove(key) != null
            if (peerTimerDeadlineMs.remove(key) != null) c = true
            if (peerTimerUpdatedAtMs.remove(key) != null) c = true
            if (peerHofCategoryComplete.remove(key) != null) c = true
            if (peerHofCategoryUpdatedAtMs.remove(key) != null) c = true
            if (peerHofPackedStats.remove(key) != null) c = true
            if (peerHofPackedUpdatedAtMs.remove(key) != null) c = true
            if (c || hintDirty) {
                persist(context)
                _revision.value = _revision.value + 1
            }
            return
        }
        var changed = hintDirty
        val airUpdated = run {
            val ttlSec = validForSec.takeIf { it > 0u } ?: MeshWireAuraVipCodec.DEFAULT_VALID_FOR_SEC
            val ttlMs = (ttlSec.toLong() * 1000L).coerceAtMost(MAX_TTL_MS)
            val deadline = nowMs + ttlMs
            val prev = vipDeadlineMs[key]
            if (prev == null || kotlin.math.abs(prev - deadline) > 5_000L) {
                vipDeadlineMs[key] = deadline
                true
            } else {
                false
            }
        }
        changed = changed || airUpdated
        // ── Отдельно обновляем «знание о чужом таймере» ────────────────────────────
        if (remainingSec > 0u) {
            // Пир прислал актуальный self-report → сдвигаем дедлайн относительно нашего "now".
            val ms = (remainingSec.toLong() * 1000L).coerceAtMost(MAX_PEER_TIMER_MS)
            val newDeadline = nowMs + ms
            val prev = peerTimerDeadlineMs[key]
            if (prev == null || kotlin.math.abs(prev - newDeadline) > 5_000L) {
                peerTimerDeadlineMs[key] = newDeadline
                peerTimerUpdatedAtMs[key] = nowMs
                changed = true
            } else {
                peerTimerUpdatedAtMs[key] = nowMs
            }
        }
        if (hofCategoryFullMedals != null && hofCategoryFullMedals.size >= 4) {
            val arr = intArrayOf(
                hofCategoryFullMedals[0].coerceIn(0, 5),
                hofCategoryFullMedals[1].coerceIn(0, 5),
                hofCategoryFullMedals[2].coerceIn(0, 5),
                hofCategoryFullMedals[3].coerceIn(0, 5),
            )
            val prev = peerHofCategoryComplete[key]
            if (prev == null || !prev.contentEquals(arr)) {
                peerHofCategoryComplete[key] = arr
                peerHofCategoryUpdatedAtMs[key] = nowMs
                changed = true
            } else {
                peerHofCategoryUpdatedAtMs[key] = nowMs
            }
        }
        if (hofPackedMedalStats != null && hofPackedMedalStats.size == HallOfFameWireStats.PACKED_BYTE_LEN) {
            peerHofPackedStats[key] = hofPackedMedalStats.copyOf()
            peerHofPackedUpdatedAtMs[key] = nowMs
            changed = true
        }
        if (changed) {
            persist(context)
            _revision.value = _revision.value + 1
        }
    }

    /**
     * Должна ли отображаться рамка у этого узла.
     *
     * Приоритет: самоотчёт `remaining_sec` (таймер ещё не истёк) → «свежий» broadcast с active=true
     * (для старых клиентов без поля `remaining_sec`).
     */
    fun hasVip(nodeNum: Long?): Boolean {
        if (nodeNum == null) return false
        val key = nodeNum and 0xFFFF_FFFFL
        val now = System.currentTimeMillis()
        peerTimerDeadlineMs[key]?.let { timerEnd ->
            if (timerEnd > now) return true
            if (peerTimerDeadlineMs.remove(key, timerEnd)) {
                peerTimerUpdatedAtMs.remove(key)
                _revision.value = _revision.value + 1
            }
        }
        val airDeadline = vipDeadlineMs[key] ?: return false
        if (now >= airDeadline) {
            if (vipDeadlineMs.remove(key, airDeadline)) {
                _revision.value = _revision.value + 1
            }
            return false
        }
        return true
    }

    fun hasVipU(nodeNum: UInt?): Boolean {
        if (nodeNum == null) return false
        return hasVip(nodeNum.toLong() and 0xFFFF_FFFFL)
    }

    /** Искать по nodeIdHex (`!abcd…`). */
    fun hasVipByNodeIdHex(nodeIdHex: String?): Boolean {
        if (nodeIdHex.isNullOrBlank()) return false
        val n = MeshWireNodeNum.parseToUInt(nodeIdHex) ?: return false
        return hasVipU(n)
    }

    /**
     * Информация для ответа на recovery-запрос (portnum 504).
     *
     * @return null — нам нечего рассказать про этот узел;
     *         иначе — актуальный `remaining_sec`.
     */
    data class PeerTimerSnapshot(val remainingSec: UInt)

    fun peerTimerSnapshot(nodeNum: UInt?, nowMs: Long = System.currentTimeMillis()): PeerTimerSnapshot? {
        if (nodeNum == null || nodeNum == 0u) return null
        val key = nodeNum.toLong() and 0xFFFF_FFFFL
        val deadline = peerTimerDeadlineMs[key] ?: return null
        val remainingMs = deadline - nowMs
        if (remainingMs <= 0L) {
            if (peerTimerDeadlineMs.remove(key, deadline)) {
                peerTimerUpdatedAtMs.remove(key)
            }
            return null
        }
        val remainingSec = (remainingMs / 1000L).coerceIn(1L, UInt.MAX_VALUE.toLong())
        return PeerTimerSnapshot(remainingSec = remainingSec.toUInt())
    }

    /**
     * Последний известный суммарный опыт узла [nodeNum] (для unicast-ответа mesh-recovery 504).
     * Возвращает 0, если записи нет или она устарела по [PEER_TIMER_RETENTION_MS].
     */
    fun peerLifetimeExperienceHint(nodeNum: UInt?, nowMs: Long = System.currentTimeMillis()): Long {
        if (nodeNum == null || nodeNum == 0u) return 0L
        val key = nodeNum.toLong() and 0xFFFF_FFFFL
        val updatedAt = peerLifetimeExperienceUpdatedAtMs[key] ?: return 0L
        if (updatedAt + PEER_TIMER_RETENTION_MS < nowMs) return 0L
        return (peerLifetimeExperience[key] ?: 0L).coerceIn(0L, MAX_PEER_LIFETIME_EXPERIENCE)
    }

    /**
     * Последний самоотчёт пира о числе полностью закрытых медалей по категориям (из VIP-broadcast поля 7–10).
     * `null` — нет данных или срок хранения истёк.
     */
    fun peerHallOfFameCategoryCounts(nodeNum: UInt?, nowMs: Long = System.currentTimeMillis()): IntArray? {
        if (nodeNum == null || nodeNum == 0u) return null
        val key = nodeNum.toLong() and 0xFFFF_FFFFL
        val updatedAt = peerHofCategoryUpdatedAtMs[key] ?: return null
        if (updatedAt + PEER_TIMER_RETENTION_MS < nowMs) return null
        return peerHofCategoryComplete[key]?.copyOf()
    }

    /**
     * Упакованные счётчики медалей пира (поле 11 / 504), для unicast-ответа на mesh-recovery.
     */
    fun peerHofFullStatsPackedHint(nodeNum: UInt?, nowMs: Long = System.currentTimeMillis()): ByteArray? {
        if (nodeNum == null || nodeNum == 0u) return null
        val key = nodeNum.toLong() and 0xFFFF_FFFFL
        val updatedAt = peerHofPackedUpdatedAtMs[key] ?: return null
        if (updatedAt + PEER_TIMER_RETENTION_MS < nowMs) return null
        val b = peerHofPackedStats[key] ?: return null
        return if (b.size == HallOfFameWireStats.PACKED_BYTE_LEN) b.copyOf() else null
    }

    private fun persist(context: Context) {
        val now = System.currentTimeMillis()
        val uiSnapshot = vipDeadlineMs.entries
            .asSequence()
            .filter { it.value > now }
            .map { "${it.key}:${it.value}" }
            .toSet()
        val timerSnapshot = peerTimerDeadlineMs.entries
            .asSequence()
            .filter { it.value > now }
            .map { "${it.key}:${it.value}:${peerTimerUpdatedAtMs[it.key] ?: now}" }
            .toSet()
        val lauSnapshot = peerLifetimeExperience.entries
            .asSequence()
            .mapNotNull { (k, xp) ->
                val ts = peerLifetimeExperienceUpdatedAtMs[k] ?: return@mapNotNull null
                if (ts + PEER_TIMER_RETENTION_MS < now) return@mapNotNull null
                if (xp <= 0L) return@mapNotNull null
                "$k:$xp:$ts"
            }
            .toSet()
        val hofSnapshot = peerHofCategoryComplete.entries
            .asSequence()
            .mapNotNull { (k, arr) ->
                if (arr.size < 4) return@mapNotNull null
                val ts = peerHofCategoryUpdatedAtMs[k] ?: return@mapNotNull null
                if (ts + PEER_TIMER_RETENTION_MS < now) return@mapNotNull null
                "${k}:${arr[0]}:${arr[1]}:${arr[2]}:${arr[3]}:$ts"
            }
            .toSet()
        val hofPackedSnapshot = peerHofPackedStats.entries
            .asSequence()
            .mapNotNull { (k, bytes) ->
                val ts = peerHofPackedUpdatedAtMs[k] ?: return@mapNotNull null
                if (ts + PEER_TIMER_RETENTION_MS < now) return@mapNotNull null
                if (bytes.size != HallOfFameWireStats.PACKED_BYTE_LEN) return@mapNotNull null
                "${k}:${Base64.encodeToString(bytes, Base64.NO_WRAP)}:$ts"
            }
            .toSet()
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ENTRIES, uiSnapshot)
            .putStringSet(KEY_TIMER_ENTRIES, timerSnapshot)
            .putStringSet("peer_vip_forever_set_v1", emptySet())
            .putStringSet(KEY_LIFETIME_AU_HINTS, lauSnapshot)
            .putStringSet(KEY_PEER_HOF_CATEGORIES, hofSnapshot)
            .putStringSet(KEY_PEER_HOF_PACKED, hofPackedSnapshot)
            .apply()
    }

    /** Формат `node:deadline` → (key, deadlineMs). */
    private fun parseEntry(raw: String): Pair<Long, Long>? {
        val sep = raw.indexOf(':')
        if (sep <= 0) return null
        val key = raw.substring(0, sep).toLongOrNull()?.and(0xFFFF_FFFFL) ?: return null
        val deadline = raw.substring(sep + 1).toLongOrNull() ?: return null
        return key to deadline
    }

    /** Формат `node:deadline:updatedAt` → (key, deadline, updatedAt). */
    private fun parseTimerEntry(raw: String): Triple<Long, Long, Long>? {
        val parts = raw.split(':')
        if (parts.size < 3) return null
        val key = parts[0].toLongOrNull()?.and(0xFFFF_FFFFL) ?: return null
        val deadline = parts[1].toLongOrNull() ?: return null
        val updated = parts[2].toLongOrNull() ?: return null
        return Triple(key, deadline, updated)
    }

    /** Формат `node:experience:updatedAt`. */
    private fun parseLifetimeExperienceHint(raw: String): Triple<Long, Long, Long>? {
        val parts = raw.split(':')
        if (parts.size < 3) return null
        val key = parts[0].toLongOrNull()?.and(0xFFFF_FFFFL) ?: return null
        val xp = parts[1].toLongOrNull()?.coerceIn(0L, MAX_PEER_LIFETIME_EXPERIENCE) ?: return null
        val updated = parts[2].toLongOrNull() ?: return null
        return Triple(key, xp, updated)
    }

    /** Формат `node:base64:updatedAt` → (key, bytes, updatedAt). */
    private fun parseHofPackedEntry(raw: String): Triple<Long, ByteArray, Long>? {
        val first = raw.indexOf(':')
        val last = raw.lastIndexOf(':')
        if (first <= 0 || last <= first) return null
        val key = raw.substring(0, first).toLongOrNull()?.and(0xFFFF_FFFFL) ?: return null
        val updated = raw.substring(last + 1).toLongOrNull() ?: return null
        val b64 = raw.substring(first + 1, last)
        val bytes = try {
            Base64.decode(b64, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return Triple(key, bytes, updated)
    }

    /** Формат `node:c0:c1:c2:c3:updatedAt` → (key, int[4], updatedAt). */
    private fun parseHofCategoryEntry(raw: String): Triple<Long, IntArray, Long>? {
        val parts = raw.split(':')
        if (parts.size < 6) return null
        val key = parts[0].toLongOrNull()?.and(0xFFFF_FFFFL) ?: return null
        val c0 = parts[1].toIntOrNull()?.coerceIn(0, 5) ?: return null
        val c1 = parts[2].toIntOrNull()?.coerceIn(0, 5) ?: return null
        val c2 = parts[3].toIntOrNull()?.coerceIn(0, 5) ?: return null
        val c3 = parts[4].toIntOrNull()?.coerceIn(0, 5) ?: return null
        val updated = parts[5].toLongOrNull() ?: return null
        return Triple(key, intArrayOf(c0, c1, c2, c3), updated)
    }
}
