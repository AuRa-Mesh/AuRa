package com.example.aura.firmware

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Загрузка .bin для ESP32 OTA по тем же URL, что и [org.meshwire.feature.firmware.FirmwareRetriever] в MeshWire Android.
 */
private const val FIRMWARE_BASE =
    "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master"
private const val OTA_PART_NAME = "app0"

object MeshWireEsp32FirmwareDownloader {

    fun resolveEsp32FirmwareBytes(
        releaseId: String,
        platformioTarget: String,
        onProgress: (Float) -> Unit,
    ): ByteArray {
        val version = releaseId.removePrefix("v")
        val target = platformioTarget.ifBlank { error("platformioTarget empty") }

        resolveFromManifest(version, target, onProgress)?.let { return it }

        val currentFilename = "firmware-$target-$version.bin"
        val directUrl = "$FIRMWARE_BASE/firmware-$version/$currentFilename"
        if (urlExists(directUrl)) {
            return MeshWireFirmwareApi.downloadToByteArray(directUrl, onProgress)
        }

        val legacyFilename = "firmware-$target-$version-update.bin"
        val legacyUrl = "$FIRMWARE_BASE/firmware-$version/$legacyFilename"
        if (urlExists(legacyUrl)) {
            return MeshWireFirmwareApi.downloadToByteArray(legacyUrl, onProgress)
        }

        val zipName = "firmware-$target-$version.zip"
        val zipUrl = "$FIRMWARE_BASE/firmware-$version/$zipName"
        if (urlExists(zipUrl)) {
            val zipBytes = MeshWireFirmwareApi.downloadToByteArray(zipUrl, onProgress)
            return extractBinFromZip(zipBytes, target, version)
        }

        error("Не найден файл прошивки для $target ($version)")
    }

    private fun resolveFromManifest(
        version: String,
        target: String,
        onProgress: (Float) -> Unit,
    ): ByteArray? {
        val manifestUrl = "$FIRMWARE_BASE/firmware-$version/firmware-$target-$version.mt.json"
        if (!urlExists(manifestUrl)) return null
        val text = readUrlText(manifestUrl) ?: return null
        val name = runCatching {
            val root = JSONObject(text)
            val files = root.getJSONArray("files")
            var found: String? = null
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                if (f.optString("partName", "") == OTA_PART_NAME) {
                    found = f.getString("name")
                    break
                }
            }
            found
        }.getOrNull() ?: return null
        val binUrl = "$FIRMWARE_BASE/firmware-$version/$name"
        if (!urlExists(binUrl)) return null
        return MeshWireFirmwareApi.downloadToByteArray(binUrl, onProgress)
    }

    private fun extractBinFromZip(zipBytes: ByteArray, target: String, version: String): ByteArray {
        val want = "firmware-$target-$version.bin"
        val z = java.util.zip.ZipInputStream(ByteArrayInputStream(zipBytes))
        while (true) {
            val e = z.nextEntry ?: break
            try {
                if (!e.isDirectory && (e.name == want || e.name.endsWith("/$want"))) {
                    return z.readBytes()
                }
            } finally {
                z.closeEntry()
            }
        }
        error("В zip нет $want")
    }

    private fun readUrlText(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun urlExists(url: String): Boolean {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "HEAD"
        }
        return try {
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }
}
