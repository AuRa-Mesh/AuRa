package com.example.aura.progression

import android.content.Context
import com.example.aura.AuraApplication
import com.example.aura.app.AppUptimeTracker
import com.example.aura.bluetooth.MeshNodeListDiskCache
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.data.local.FavGroupDao
import com.example.aura.data.local.HallOfFameCounterDao
import com.example.aura.data.local.MapBeaconDao
import com.example.aura.data.local.UserStatsDao
import com.example.aura.data.local.UserStatsEntity
import com.example.aura.preferences.NodeScopedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ОП в [UserStatsDao], медали — в [HallOfFameCounterDao]. Локально по [NodeScopedStorage.nodeKey].
 */
object HallOfFameRepository {

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var userStatsDao: UserStatsDao? = null
    private var counterDao: HallOfFameCounterDao? = null
    private var mapBeaconDao: MapBeaconDao? = null
    private var favGroupDao: FavGroupDao? = null
    private var appRef: AuraApplication? = null

    private val _totalXp = MutableStateFlow(0L)
    val totalXpFlow: StateFlow<Long> = _totalXp.asStateFlow()

    val levelFlow: StateFlow<Int> = totalXpFlow
        .map { xp -> (xp / XP_PER_LEVEL).toInt() }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val levelProgressToNextFlow: StateFlow<Float> = totalXpFlow
        .map { xp -> ((xp % XP_PER_LEVEL).toFloat() / XP_PER_LEVEL.toFloat()).coerceIn(0f, 1f) }
        .stateIn(scope, SharingStarted.Eagerly, 0f)

    const val XP_PER_LEVEL: Long = 100L
    /** ОП за каждую новую ступень медали (при переходе на следующий уровень достижения). */
    const val OP_REWARD_PER_MEDAL_TIER: Long = 20L
    private const val XP_MESSAGE: Long = 1L
    private const val XP_BEACON: Long = 2L
    private const val XP_UPTIME_HOUR: Long = 5L

    fun scheduleDerivedCountersRefresh(ctx: Context) {
        val c = ctx.applicationContext
        scope.launch {
            refreshDerivedCounters(c)
        }
    }

    fun addVipOwnershipMsBlocking(ctx: Context, ms: Long) {
        runBlocking { addVipOwnershipMs(ctx.applicationContext, ms) }
    }

    fun addMatrixActiveMsBlocking(ctx: Context, ms: Long) {
        runBlocking { addMatrixActiveMs(ctx.applicationContext, ms) }
    }

    fun onVipCodeRedeemedBlocking(ctx: Context) {
        runBlocking { onVipCodeRedeemed(ctx.applicationContext) }
    }

    fun onHopAckBlocking(ctx: Context, hops: Int) {
        runBlocking { onHopAck(ctx.applicationContext, hops) }
    }

    fun install(application: AuraApplication) {
        appRef = application
        userStatsDao = application.chatDatabase.userStatsDao()
        counterDao = application.chatDatabase.hallOfFameCounterDao()
        mapBeaconDao = application.chatDatabase.mapBeaconDao()
        favGroupDao = application.chatDatabase.favGroupDao()
        scope.launch {
            mutex.withLock {
                migrateLegacySecureXpIfNeeded(application)
                refreshTotalXpCache(application)
            }
        }
    }

    private fun owner(ctx: Context): String = NodeScopedStorage.nodeKey(ctx)

    private suspend fun migrateLegacySecureXpIfNeeded(ctx: Context) {
        if (AuraGodNodeProfile.matches(ctx.applicationContext)) return
        val uDao = userStatsDao ?: return
        val own = owner(ctx)
        val cur = uDao.getTotalXp(own) ?: 0L
        if (cur > 0L) return
        ExperienceSecureStore.migrateLegacyIfNeeded(ctx)
        val legacy = ExperienceSecureStore.readTotalExperience(ctx).coerceAtLeast(0L)
        val now = System.currentTimeMillis()
        uDao.insertIgnore(UserStatsEntity(own, totalXp = 0L, updatedAtMs = now))
        if (legacy > 0L) {
            uDao.ensureRowAndAddXp(own, legacy, now)
        }
    }

    private suspend fun refreshTotalXpCache(ctx: Context) {
        if (AuraGodNodeProfile.matches(ctx.applicationContext)) {
            _totalXp.value = AuraGodNodeProfile.GOD_TOTAL_XP
            return
        }
        val uDao = userStatsDao ?: return
        _totalXp.value = uDao.getTotalXp(owner(ctx)) ?: 0L
    }

    fun totalXpBlocking(ctx: Context): Long {
        if (AuraGodNodeProfile.matches(ctx.applicationContext)) {
            _totalXp.value = AuraGodNodeProfile.GOD_TOTAL_XP
            return AuraGodNodeProfile.GOD_TOTAL_XP
        }
        if (userStatsDao == null) return 0L
        return runBlocking {
            mutex.withLock {
                migrateLegacySecureXpIfNeeded(ctx.applicationContext)
                val v = userStatsDao!!.getTotalXp(owner(ctx.applicationContext)) ?: 0L
                _totalXp.value = v
                v
            }
        }
    }

    private suspend fun addXpRaw(ctx: Context, delta: Long) {
        if (AuraGodNodeProfile.matches(ctx.applicationContext)) {
            _totalXp.value = AuraGodNodeProfile.GOD_TOTAL_XP
            return
        }
        if (delta == 0L) return
        val uDao = userStatsDao ?: return
        migrateLegacySecureXpIfNeeded(ctx.applicationContext)
        val own = owner(ctx)
        val now = System.currentTimeMillis()
        uDao.ensureRowAndAddXp(own, delta, now)
        syncXpMirrorCounter(own)
        _totalXp.value = uDao.getTotalXp(own) ?: 0L
    }

    suspend fun addXp(ctx: Context, delta: Long, @Suppress("UNUSED_PARAMETER") reason: String = "") {
        mutex.withLock {
            addXpRaw(ctx, delta)
        }
        AuraExperience.notifyStatsChanged()
    }

    fun addXpBlocking(ctx: Context, delta: Long, reason: String = "") {
        runBlocking {
            mutex.withLock {
                addXpRaw(ctx, delta)
            }
            AuraExperience.notifyStatsChanged()
        }
    }

    private suspend fun syncXpMirrorCounter(owner: String) {
        val ctr = counterDao ?: return
        val xp = userStatsDao?.getTotalXp(owner) ?: 0L
        ctr.setValue(owner, HallOfFameKeys.ENG_TOTAL_XP_MIRROR, xp)
    }

    suspend fun onMeshTextSent(ctx: Context) {
        mutex.withLock {
            AuraProgressCounters.incrementOutgoingMessages(ctx)
            incrementStatLocked(ctx, HallOfFameKeys.SIG_MESH_MESSAGES, 1L)
            addXpRaw(ctx, XP_MESSAGE)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onBeaconPlaced(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.MAP_BEACONS_PLACED, 1L)
            addXpRaw(ctx, XP_BEACON)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onUptimeHours(ctx: Context, hours: Long) {
        if (hours <= 0L) return
        mutex.withLock {
            addXpRaw(ctx, XP_UPTIME_HOUR * hours)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onVipCodeRedeemed(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.VIP_CODES_REDEEMED, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onNameChangeSaved(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.ENG_NAME_CHANGES, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onNodeInfoOrPrefetch(ctx: Context, delta: Int = 1) {
        if (delta <= 0) return
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.ENG_NODE_INFO_PREFETCH, delta.toLong())
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onBeaconLinkNavigation(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.MAP_BEACON_LINK_CLICKS, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onBeaconImportedFromLink(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.MAP_BEACON_IMPORTS, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onPositionPacketSent(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.MAP_POSITION_PACKETS, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onBeaconSyncSuccess(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.MAP_BEACON_SYNC_ACTS, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onVipBroadcastSuccess(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.VIP_BROADCASTS_SENT, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    fun onVipBroadcastSuccessBlocking(ctx: Context) {
        runBlocking { onVipBroadcastSuccess(ctx.applicationContext) }
    }

    /**
     * Восстановление суммарного ОП после переустановки: самоотчёт `lifetime_au` из ответа
     * mesh recovery (504). Берём максимум с локальным [UserStatsDao], без уменьшения прогресса.
     */
    fun applyMeshRecoveredLifetimeXpBlocking(ctx: Context, lifetimeXp: Long) {
        if (lifetimeXp <= 0L) return
        if (AuraGodNodeProfile.matches(ctx.applicationContext)) return
        val capped = lifetimeXp.coerceIn(1L, 1_000_000_000L)
        runBlocking {
            mutex.withLock {
                migrateLegacySecureXpIfNeeded(ctx.applicationContext)
                val uDao = userStatsDao ?: return@runBlocking
                val own = owner(ctx.applicationContext)
                val now = System.currentTimeMillis()
                uDao.ensureRowAndBumpTotalXpAtLeast(own, capped, now)
                syncXpMirrorCounter(own)
                _totalXp.value = uDao.getTotalXp(own) ?: 0L
            }
            AuraExperience.notifyStatsChanged()
        }
    }

    /**
     * Восстановление счётчиков медалей из mesh (504 / поле 7): по каждому ключу [max] с локальным
     * состоянием без начисления ОП за ступени (как при читерской выкладке медали).
     */
    fun applyMeshRecoveredMedalCountersBlocking(ctx: Context, packed: ByteArray) {
        val values = HallOfFameWireStats.unpackToLongArray(packed) ?: return
        if (AuraGodNodeProfile.matches(ctx.applicationContext)) return
        val appCtx = ctx.applicationContext
        runBlocking {
            mutex.withLock {
                migrateLegacySecureXpIfNeeded(appCtx)
                val uDao = userStatsDao ?: return@runBlocking
                val ctr = counterDao ?: return@runBlocking
                val own = owner(appCtx)
                val now = System.currentTimeMillis()
                val keys = HallOfFameWireStats.ORDERED_STAT_KEYS
                for (i in values.indices) {
                    val remote = values[i].coerceAtLeast(0L)
                    if (remote <= 0L) continue
                    val key = keys[i]
                    val current = compositeStatUnderLock(appCtx, key)
                    val merged = maxOf(current, remote)
                    if (merged <= current) continue
                    mergeMeshRecoveredSingleStatLocked(appCtx, own, key, merged, uDao, ctr, now)
                }
                syncXpMirrorCounter(own)
                _totalXp.value = uDao.getTotalXp(own) ?: 0L
            }
            AuraExperience.notifyStatsChanged()
        }
    }

    private suspend fun mergeMeshRecoveredSingleStatLocked(
        ctx: Context,
        owner: String,
        key: String,
        merged: Long,
        uDao: UserStatsDao,
        ctr: HallOfFameCounterDao,
        nowMs: Long,
    ) {
        when (key) {
            HallOfFameKeys.ENG_TOTAL_XP_MIRROR -> {
                uDao.ensureRowAndBumpTotalXpAtLeast(owner, merged, nowMs)
            }
            else -> {
                ctr.setValue(owner, key, merged)
            }
        }
    }

    suspend fun onDemoUnlockUse(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.VIP_DEMO_UNLOCK_USES, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun addVipOwnershipMs(ctx: Context, ms: Long) {
        if (ms <= 0L) return
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.VIP_OWNERSHIP_MS, ms)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun addMatrixActiveMs(ctx: Context, ms: Long) {
        if (ms <= 0L) return
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.VIP_MATRIX_MS, ms)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onReactionOnOwnMessage(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.SIG_REACTIONS_ON_MINE, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onPollQualified(ctx: Context) {
        mutex.withLock {
            incrementStatLocked(ctx, HallOfFameKeys.SIG_POLLS_3NODES, 1L)
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun onHopAck(ctx: Context, hops: Int) {
        if (hops <= 0) return
        mutex.withLock {
            AuraProgressCounters.addHopObservation(ctx, hops)
            incrementStatLocked(ctx, HallOfFameKeys.SIG_HOP_ACK_SUM, hops.toLong())
        }
        AuraExperience.notifyStatsChanged()
    }

    suspend fun refreshDerivedCounters(ctx: Context) {
        mutex.withLock {
            if (AuraGodNodeProfile.matches(ctx.applicationContext)) {
                _totalXp.value = AuraGodNodeProfile.GOD_TOTAL_XP
                return@withLock
            }
            val own = owner(ctx)
            val ctr = counterDao ?: return@withLock
            val app = ctx.applicationContext
            val wallMs = AppUptimeTracker.uptimeMs()
            val prevUptime = ctr.getValue(own, HallOfFameKeys.SIG_UPTIME_APP_MS) ?: 0L
            ctr.setValue(own, HallOfFameKeys.SIG_UPTIME_APP_MS, maxOf(wallMs, prevUptime))
            val mac = NodeGattConnection.targetDevice?.address
            val nodes = if (!mac.isNullOrBlank()) {
                (app as? AuraApplication)?.let { MeshNodeListDiskCache.load(it, mac)?.size?.toLong() } ?: 0L
            } else {
                0L
            }
            val curNodes = ctr.getValue(own, HallOfFameKeys.ENG_NODE_CARDS) ?: 0L
            if (nodes > curNodes) ctr.setValue(own, HallOfFameKeys.ENG_NODE_CARDS, nodes)
            val my = NodeGattConnection.myNodeNum.value?.toLong()?.and(0xFFFF_FFFFL) ?: 0L
            if (my != 0L && mapBeaconDao != null) {
                val bc = mapBeaconDao!!.countNonLocalByCreator(my).toLong()
                val curB = ctr.getValue(own, HallOfFameKeys.MAP_BEACONS_PLACED) ?: 0L
                if (bc > curB) ctr.setValue(own, HallOfFameKeys.MAP_BEACONS_PLACED, bc)
            }
            val fav = favGroupDao?.countDistinctPeers()?.toLong() ?: 0L
            val curF = ctr.getValue(own, HallOfFameKeys.ENG_FAV_DISTINCT_PEERS) ?: 0L
            if (fav > curF) ctr.setValue(own, HallOfFameKeys.ENG_FAV_DISTINCT_PEERS, fav)
            syncXpMirrorCounter(own)
        }
    }

    /**
     * Число медалей в каждой категории Аллеи славы, полностью закрытых на всех ступенях (0–5),
     * для самоотчёта в VIP-broadcast без отдельного запроса по mesh.
     */
    suspend fun categoryFullMedalCountsForBroadcast(ctx: Context): IntArray {
        val lookup = LinkedHashMap<String, Long>()
        for (cat in HallOfFameCatalog.CATEGORIES) {
            for (medal in cat.medals) {
                if (!lookup.containsKey(medal.statKey)) {
                    lookup[medal.statKey] = getStat(ctx, medal.statKey)
                }
            }
        }
        val lookupFn: (String) -> Long = { k -> lookup[k] ?: 0L }
        return IntArray(HallOfFameCatalog.CATEGORIES.size) { i ->
            HallOfFameCatalog.CATEGORIES[i].countFullyCompletedMedals(lookupFn)
        }
    }

    suspend fun getStat(ctx: Context, key: String): Long {
        return mutex.withLock {
            if (AuraGodNodeProfile.matches(ctx.applicationContext)) {
                return@withLock godModeStat(ctx, key)
            }
            compositeStatUnderLock(ctx, key)
        }
    }

    /** Значения всех 20 счётчиков медалей для VIP-broadcast (поле 11). */
    suspend fun medalStatValuesForBroadcast(ctx: Context): LongArray {
        return mutex.withLock {
            val keys = HallOfFameWireStats.ORDERED_STAT_KEYS
            if (AuraGodNodeProfile.matches(ctx.applicationContext)) {
                LongArray(keys.size) { i -> godModeStat(ctx, keys[i]) }
            } else {
                val out = LongArray(keys.size)
                for (i in keys.indices) {
                    out[i] = compositeStatUnderLock(ctx, keys[i])
                }
                out
            }
        }
    }

    private suspend fun compositeStatUnderLock(ctx: Context, key: String): Long {
        val own = owner(ctx)
        val ctr = counterDao ?: return 0L
        return when (key) {
            HallOfFameKeys.SIG_MESH_MESSAGES -> {
                val room = ctr.getValue(own, key) ?: 0L
                maxOf(room, AuraProgressCounters.outgoingMessages(ctx))
            }
            HallOfFameKeys.SIG_HOP_ACK_SUM -> {
                val room = ctr.getValue(own, key) ?: 0L
                maxOf(room, AuraProgressCounters.hopSum(ctx))
            }
            HallOfFameKeys.SIG_UPTIME_APP_MS -> {
                val room = ctr.getValue(own, key) ?: 0L
                maxOf(AppUptimeTracker.uptimeMs(), room)
            }
            HallOfFameKeys.ENG_TOTAL_XP_MIRROR -> userStatsDao?.getTotalXp(own) ?: 0L
            else -> ctr.getValue(own, key) ?: 0L
        }
    }

    /** Вызывать уже под [mutex], без вложенного mutex. */
    private suspend fun incrementStatLocked(ctx: Context, key: String, delta: Long) {
        if (AuraGodNodeProfile.matches(ctx.applicationContext)) return
        if (delta == 0L) return
        val own = owner(ctx)
        val ctr = counterDao ?: return
        val before = when (key) {
            HallOfFameKeys.SIG_MESH_MESSAGES -> maxOf(ctr.getValue(own, key) ?: 0L, AuraProgressCounters.outgoingMessages(ctx))
            HallOfFameKeys.SIG_HOP_ACK_SUM -> maxOf(ctr.getValue(own, key) ?: 0L, AuraProgressCounters.hopSum(ctx))
            else -> ctr.getValue(own, key) ?: 0L
        }
        val after = before + delta
        ctr.setValue(own, key, after)
        val medal = HallOfFameCatalog.CATEGORIES.asSequence().flatMap { it.medals }.firstOrNull { it.statKey == key }
        if (medal != null) {
            val t0 = medal.tierForValue(before)
            val t1 = medal.tierForValue(after)
            if (t1 > t0) {
                addXpRaw(ctx, OP_REWARD_PER_MEDAL_TIER * (t1 - t0))
            }
        }
    }

    /**
     * Режим разработчика: выставить счётчик медали на последний порог (все 5 ступеней).
     * ОП за ступени **не** начисляется, чтобы не раздувать прогресс.
     */
    suspend fun devCheatMaxMedal(ctx: Context, medal: HallOfFameMedalDef) {
        mutex.withLock {
            if (AuraGodNodeProfile.matches(ctx.applicationContext)) return@withLock
            val ctr = counterDao ?: return@withLock
            val uDao = userStatsDao ?: return@withLock
            val own = owner(ctx)
            migrateLegacySecureXpIfNeeded(ctx.applicationContext)
            val target = medal.thresholds.last()
            val now = System.currentTimeMillis()
            when (medal.statKey) {
                HallOfFameKeys.ENG_TOTAL_XP_MIRROR -> {
                    uDao.ensureRowAndBumpTotalXpAtLeast(own, target, now)
                }
                else -> {
                    ctr.setValue(own, medal.statKey, target)
                }
            }
            syncXpMirrorCounter(own)
            _totalXp.value = uDao.getTotalXp(own) ?: 0L
        }
        AuraExperience.notifyStatsChanged()
    }

    fun devCheatMaxMedalBlocking(ctx: Context, medal: HallOfFameMedalDef) {
        runBlocking { devCheatMaxMedal(ctx.applicationContext, medal) }
    }

    private fun godModeStat(ctx: Context, key: String): Long {
        val medalMax = HallOfFameCatalog.CATEGORIES
            .asSequence()
            .flatMap { it.medals }
            .firstOrNull { it.statKey == key }
            ?.thresholds
            ?.lastOrNull()
            ?: 0L
        return when (key) {
            HallOfFameKeys.SIG_MESH_MESSAGES ->
                maxOf(medalMax, AuraProgressCounters.outgoingMessages(ctx))
            HallOfFameKeys.SIG_HOP_ACK_SUM ->
                maxOf(medalMax, AuraProgressCounters.hopSum(ctx))
            HallOfFameKeys.SIG_UPTIME_APP_MS ->
                maxOf(medalMax, AppUptimeTracker.uptimeMs())
            HallOfFameKeys.ENG_TOTAL_XP_MIRROR ->
                maxOf(medalMax, AuraGodNodeProfile.GOD_TOTAL_XP)
            else -> medalMax
        }
    }
}
