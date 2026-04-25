package com.example.aura.firmware

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * Публичные API mesh (как в типичном mesh-клиенте Android [ApiServiceImpl]): каталог железа и список релизов прошивки.
 */
object MeshWireFirmwareApi {

    private const val HARDWARE_URL = "https://api.meshtastic.org/resource/deviceHardware"
    private const val FIRMWARE_LIST_URL = "https://api.meshtastic.org/github/firmware/list"

    private val hardwareCache = AtomicReference<List<MeshWireDeviceHardwareInfo>?>(null)

    fun fetchDeviceHardware(): List<MeshWireDeviceHardwareInfo> {
        hardwareCache.get()?.let { return it }
        val conn = (URL(HARDWARE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("deviceHardware HTTP $code")
            val arr = JSONArray(text)
            val out = ArrayList<MeshWireDeviceHardwareInfo>(arr.length())
            for (i in 0 until arr.length()) {
                out.add(parseHardware(arr.getJSONObject(i)))
            }
            hardwareCache.set(out)
            return out
        } finally {
            conn.disconnect()
        }
    }

    fun fetchFirmwareReleases(): MeshWireFirmwareReleaseIndex {
        val conn = (URL(FIRMWARE_LIST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("firmware list HTTP $code")
            return parseReleaseIndex(JSONObject(text))
        } finally {
            conn.disconnect()
        }
    }

    fun downloadToByteArray(url: String, onProgress: (Float) -> Unit): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 60_000
            readTimeout = 120_000
            requestMethod = "GET"
        }
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            error("download HTTP $code")
        }
        val len = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
        val input = BufferedInputStream(conn.inputStream)
        val buf = java.io.ByteArrayOutputStream(256 * 1024)
        val chunk = ByteArray(32 * 1024)
        var readTotal = 0L
        while (true) {
            val n = input.read(chunk)
            if (n <= 0) break
            buf.write(chunk, 0, n)
            readTotal += n
            if (len > 0) {
                onProgress((readTotal.toFloat() / len.toFloat()).coerceIn(0f, 1f))
            }
        }
        input.close()
        conn.disconnect()
        if (len <= 0) onProgress(1f)
        return buf.toByteArray()
    }

    private fun parseHardware(o: JSONObject): MeshWireDeviceHardwareInfo =
        MeshWireDeviceHardwareInfo(
            hwModel = o.optInt("hwModel", 0),
            hwModelSlug = o.optString("hwModelSlug", ""),
            platformioTarget = o.optString("platformioTarget", ""),
            architecture = o.optString("architecture", ""),
            displayName = o.optString("displayName", ""),
        )

    private fun parseReleaseIndex(root: JSONObject): MeshWireFirmwareReleaseIndex {
        val rel = root.optJSONObject("releases") ?: JSONObject()
        val stable = rel.optJSONArray("stable")?.toReleaseList() ?: emptyList()
        val alpha = rel.optJSONArray("alpha")?.toReleaseList() ?: emptyList()
        return MeshWireFirmwareReleaseIndex(stable = stable, alpha = alpha)
    }

    private fun JSONArray.toReleaseList(): List<MeshWireFirmwareRelease> {
        val out = ArrayList<MeshWireFirmwareRelease>(length())
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            out.add(
                MeshWireFirmwareRelease(
                    id = o.optString("id", ""),
                    title = o.optString("title", ""),
                    pageUrl = o.optString("page_url", ""),
                    zipUrl = o.optString("zip_url", ""),
                    releaseNotes = o.optString("release_notes", ""),
                ),
            )
        }
        return out
    }
}

data class MeshWireDeviceHardwareInfo(
    val hwModel: Int,
    val hwModelSlug: String,
    val platformioTarget: String,
    val architecture: String,
    val displayName: String,
)

data class MeshWireFirmwareRelease(
    val id: String,
    val title: String,
    val pageUrl: String,
    val zipUrl: String,
    val releaseNotes: String,
)

data class MeshWireFirmwareReleaseIndex(
    val stable: List<MeshWireFirmwareRelease>,
    val alpha: List<MeshWireFirmwareRelease>,
)
