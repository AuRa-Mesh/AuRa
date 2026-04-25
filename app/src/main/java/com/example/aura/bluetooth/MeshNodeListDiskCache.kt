package com.example.aura.bluetooth

import android.content.Context
import com.example.aura.meshwire.MeshWireNodeSummary
import org.json.JSONArray
import org.json.JSONObject

/**
 * Кэш списка узлов (NodeDB-снимок) на диске по MAC привязанной ноды.
 */
object MeshNodeListDiskCache {
    private const val PREFS = "aura_mesh_node_list"

    private fun key(mac: String) = "nodes|${MeshNodeSyncMemoryStore.normalizeKey(mac)}"

    fun load(context: Context, rawMac: String): List<MeshWireNodeSummary>? {
        val json = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(rawMac), null) ?: return null
        return decodeList(json)
    }

    fun save(context: Context, rawMac: String, nodes: List<MeshWireNodeSummary>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(rawMac), encodeList(nodes))
            .apply()
    }

    fun clear(context: Context, rawMac: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(rawMac))
            .apply()
    }

    /** Узел помечен игнорируемым на нашей стороне (как в NodeDB радио). */
    fun isPeerIgnoredInCache(context: Context, rawMac: String, peerNodeNumLong: Long): Boolean {
        val nodes = load(context, rawMac) ?: return false
        val peer = peerNodeNumLong and 0xFFFF_FFFFL
        return nodes.any { (it.nodeNum and 0xFFFF_FFFFL) == peer && it.isIgnored }
    }

    private fun encodeList(nodes: List<MeshWireNodeSummary>): String {
        val arr = JSONArray()
        nodes.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("nodeNum", n.nodeNum)
                    put("nodeIdHex", n.nodeIdHex)
                    put("longName", n.longName)
                    put("shortName", n.shortName)
                    put("hardwareModel", n.hardwareModel)
                    put("roleLabel", n.roleLabel)
                    put("userId", n.userId ?: JSONObject.NULL)
                    put("lastHeardLabel", n.lastHeardLabel)
                    put("lastSeenEpochSec", n.lastSeenEpochSec ?: JSONObject.NULL)
                    put("latitude", n.latitude ?: JSONObject.NULL)
                    put("longitude", n.longitude ?: JSONObject.NULL)
                    put("altitudeMeters", n.altitudeMeters ?: JSONObject.NULL)
                    put("batteryPercent", n.batteryPercent ?: JSONObject.NULL)
                    put("batteryVoltage", n.batteryVoltage?.toDouble() ?: JSONObject.NULL)
                    put("isCharging", n.isCharging ?: JSONObject.NULL)
                    put("channelUtilization", n.channelUtilization?.toDouble() ?: JSONObject.NULL)
                    put("airUtilTx", n.airUtilTx?.toDouble() ?: JSONObject.NULL)
                    put("channel", n.channel?.toLong() ?: JSONObject.NULL)
                    put("hopsAway", n.hopsAway?.toLong() ?: JSONObject.NULL)
                    put("meshHopLimit", n.meshHopLimit?.toLong() ?: JSONObject.NULL)
                    put("meshHopStart", n.meshHopStart?.toLong() ?: JSONObject.NULL)
                    put("viaMqtt", n.viaMqtt)
                    put("isFavorite", n.isFavorite)
                    put("isIgnored", n.isIgnored)
                    put("lastSnrDb", n.lastSnrDb?.toDouble() ?: JSONObject.NULL)
                    put("peerReportedUptimeSec", n.peerReportedUptimeSec ?: JSONObject.NULL)
                    put("peerUptimeReceivedEpochSec", n.peerUptimeReceivedEpochSec ?: JSONObject.NULL)
                    put("publicKeyB64", n.publicKeyB64 ?: JSONObject.NULL)
                },
            )
        }
        return arr.toString()
    }

    private fun decodeList(json: String): List<MeshWireNodeSummary>? = try {
        val arr = JSONArray(json)
        val out = ArrayList<MeshWireNodeSummary>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val num = o.getLong("nodeNum")
            val idHex = if (o.isNull("nodeIdHex")) "!%08x".format(num) else o.getString("nodeIdHex")
            val snr = when {
                !o.isNull("lastSnrDb") -> o.getDouble("lastSnrDb").toFloat()
                !o.isNull("snrDb") -> o.getDouble("snrDb").toFloat()
                else -> null
            }
            out.add(
                MeshWireNodeSummary(
                    nodeNum = num,
                    nodeIdHex = idHex,
                    longName = o.getString("longName"),
                    shortName = o.getString("shortName"),
                    hardwareModel = o.getString("hardwareModel"),
                    roleLabel = o.getString("roleLabel"),
                    userId = if (o.isNull("userId")) null else o.getString("userId"),
                    lastHeardLabel = o.getString("lastHeardLabel"),
                    lastSeenEpochSec = if (o.isNull("lastSeenEpochSec")) null else o.getLong("lastSeenEpochSec"),
                    latitude = if (o.isNull("latitude")) null else o.getDouble("latitude"),
                    longitude = if (o.isNull("longitude")) null else o.getDouble("longitude"),
                    altitudeMeters = if (o.isNull("altitudeMeters")) null else o.getInt("altitudeMeters"),
                    batteryPercent = if (o.isNull("batteryPercent")) null else o.getInt("batteryPercent"),
                    batteryVoltage = if (o.isNull("batteryVoltage")) null else o.getDouble("batteryVoltage").toFloat(),
                    isCharging = if (o.isNull("isCharging")) null else o.getBoolean("isCharging"),
                    channelUtilization = if (o.isNull("channelUtilization")) null else o.getDouble("channelUtilization").toFloat(),
                    airUtilTx = if (o.isNull("airUtilTx")) null else o.getDouble("airUtilTx").toFloat(),
                    channel = if (o.isNull("channel")) null else o.getLong("channel").toUInt(),
                    hopsAway = if (o.isNull("hopsAway")) null else o.getLong("hopsAway").toUInt(),
                    meshHopLimit = if (o.isNull("meshHopLimit")) null else o.getLong("meshHopLimit").toUInt(),
                    meshHopStart = if (o.isNull("meshHopStart")) null else o.getLong("meshHopStart").toUInt(),
                    viaMqtt = !o.isNull("viaMqtt") && o.getBoolean("viaMqtt"),
                    isFavorite = !o.isNull("isFavorite") && o.getBoolean("isFavorite"),
                    isIgnored = !o.isNull("isIgnored") && o.getBoolean("isIgnored"),
                    lastSnrDb = snr,
                    peerReportedUptimeSec = if (o.isNull("peerReportedUptimeSec")) null else o.getLong("peerReportedUptimeSec"),
                    peerUptimeReceivedEpochSec = if (o.isNull("peerUptimeReceivedEpochSec")) null else o.getLong("peerUptimeReceivedEpochSec"),
                    publicKeyB64 = if (o.isNull("publicKeyB64")) null else o.getString("publicKeyB64"),
                ),
            )
        }
        out
    } catch (_: Exception) {
        null
    }
}
