package com.example.aura.meshwire

/**
 * Узел из базы радио / потока [FromRadio] (NodeInfo, User, Position, Telemetry, любые MeshPacket с [from]).
 *
 * [lastSeenEpochSec] — последний контакт (Unix сек), см. [MeshNodeDbRepository] / аккумулятор.
 */
data class MeshWireNodeSummary(
    /** 32-bit node number. */
    val nodeNum: Long,
    /** `!xxxxxxxx` — совпадает с [displayLongName] если имя не задано. */
    val nodeIdHex: String,
    /** user.longName или плейсхолдер !hex. */
    val longName: String,
    val shortName: String,
    val hardwareModel: String,
    val roleLabel: String,
    val userId: String?,
    /** Текст «последний раз» для деталей и совместимости. */
    val lastHeardLabel: String,
    val lastSeenEpochSec: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Position.altitude (м MSL), mesh.proto. */
    val altitudeMeters: Int? = null,
    /** device_metrics.battery_level (0–100 для отображения; >100 на эфире = внешнее питание). */
    val batteryPercent: Int? = null,
    /** DeviceMetrics.voltage (В). */
    val batteryVoltage: Float? = null,
    /** true если battery_level с эфира > 100 (USB/питание по mesh). */
    val isCharging: Boolean? = null,
    /** DeviceMetrics.channel_utilization / air_util_tx. */
    val channelUtilization: Float? = null,
    val airUtilTx: Float? = null,
    /** NodeInfo.channel — локальный индекс канала (если радио отдало). */
    val channel: UInt? = null,
    val hopsAway: UInt? = null,
    /** Последний известный MeshPacket.hop_limit (если есть). */
    val meshHopLimit: UInt? = null,
    /** Последний известный MeshPacket.hop_start (если есть). */
    val meshHopStart: UInt? = null,
    val viaMqtt: Boolean = false,
    val isFavorite: Boolean = false,
    val isIgnored: Boolean = false,
    /** Последний rx_snr с эфира (дБ). */
    val lastSnrDb: Float? = null,
    /** Аптайм приложения (сек), присланный другим узлом по Aura-порту; null — данных нет. */
    val peerReportedUptimeSec: Long? = null,
    /** Unix сек — когда мы получили [peerReportedUptimeSec]. */
    val peerUptimeReceivedEpochSec: Long? = null,
    /** X25519 public key (32 bytes) в Base64 — для PKI и сопоставления с [com.example.aura.mesh.incoming.resolveInboundDmPeerUInt]. */
    val publicKeyB64: String? = null,
) {
    /** Считаем онлайном, если слышали за последние [windowSec] секунд (по умолчанию 15 мин). */
    fun isOnline(nowEpochSec: Long, windowSec: Long = 15 * 60): Boolean {
        val t = lastSeenEpochSec ?: return false
        if (t <= 0L) return false
        return (nowEpochSec - t) <= windowSec
    }

    fun displayLongName(): String {
        val n = longName.trim()
        return if (n.isEmpty() || n.equals(nodeIdHex, ignoreCase = true)) nodeIdHex else n
    }

    /** Оценка числа ретрансляций по hop_start / hop_limit, если оба заданы. */
    fun relayHopsCount(): UInt? {
        val hs = meshHopStart ?: return null
        val hl = meshHopLimit ?: return null
        if (hs < hl) return null
        return hs - hl
    }

    /** Неизвестный пользователь (только номер / short «?»). */
    fun isUnknownProfile(): Boolean {
        val n = longName.trim()
        return shortName == "?" || shortName.isBlank() ||
            n.isEmpty() || n.equals(nodeIdHex, ignoreCase = true)
    }

    /** Роутер / ретранслятор — «инфраструктура» для фильтра (mesh DeviceRole). */
    fun isInfrastructureRole(): Boolean =
        roleLabel == "ROUTER" || roleLabel == "ROUTER_LATE" || roleLabel == "REPEATER"
}
