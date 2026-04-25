package com.example.aura.meshwire

import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Разбор [FromRadio] при want_config: `moduleConfig` (поле 9) → [ModuleConfig.mqtt] (поле 1) и `config_complete_id` (7).
 */
internal class MeshWireMqttSyncAccumulator {
    var sawMqtt: Boolean = false
    var configComplete: Boolean = false

    private var enabled: Boolean? = null
    private var address: String? = null
    private var username: String? = null
    private var password: String? = null
    private var encryptionEnabled: Boolean? = null
    private var jsonEnabled: Boolean? = null
    private var tlsEnabled: Boolean? = null
    private var root: String? = null
    private var proxyToClientEnabled: Boolean? = null
    private var mapReportingEnabled: Boolean? = null
    private var mapPublishIntervalSecs: UInt? = null
    private var mapPositionPrecision: UInt? = null
    private var mapShouldReportLocation: Boolean? = null

    fun consumeFromRadio(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(bytes, i)
                    i += n
                    if (fieldNum == 7 && v == MeshWireWantConfigHandshake.CONFIG_NONCE.toLong()) {
                        configComplete = true
                    }
                }
                5 -> i = min(i + 4, bytes.size)
                1 -> i = min(i + 8, bytes.size)
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        9 -> parseModuleConfigEnvelope(sub)
                        2, 1 -> tryParseAdminModuleConfigResponse(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun tryParseAdminModuleConfigResponse(meshPacketBytes: ByteArray) {
        val admin = MeshWireAdminResponseParser.extractAdminPayloadFromMeshPacket(meshPacketBytes) ?: return
        val mc = MeshWireAdminResponseParser.extractModuleConfigResponse(admin) ?: return
        parseModuleConfigEnvelope(mc)
        configComplete = true
    }

    fun shouldFinish(): Boolean = configComplete

    fun toPushState(): MeshWireMqttPushState {
        val b = MeshWireMqttPushState.initial()
        return b.copy(
            enabled = enabled ?: b.enabled,
            address = address?.takeIf { it.isNotEmpty() } ?: b.address,
            username = username ?: b.username,
            password = password ?: b.password,
            encryptionEnabled = encryptionEnabled ?: b.encryptionEnabled,
            jsonEnabled = jsonEnabled ?: b.jsonEnabled,
            tlsEnabled = tlsEnabled ?: b.tlsEnabled,
            root = root?.takeIf { it.isNotEmpty() } ?: b.root,
            proxyToClientEnabled = proxyToClientEnabled ?: b.proxyToClientEnabled,
            mapReportingEnabled = mapReportingEnabled ?: b.mapReportingEnabled,
            mapPublishIntervalSecs = mapPublishIntervalSecs ?: b.mapPublishIntervalSecs,
            mapPositionPrecision = mapPositionPrecision ?: b.mapPositionPrecision,
            mapShouldReportLocation = mapShouldReportLocation ?: b.mapShouldReportLocation,
        )
    }

    private fun parseModuleConfigEnvelope(moduleBytes: ByteArray) {
        var i = 0
        while (i < moduleBytes.size) {
            val tag = moduleBytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(moduleBytes, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(moduleBytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > moduleBytes.size) break
                    val sub = moduleBytes.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 1) {
                        sawMqtt = true
                        parseMqttConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseMqttConfigBytes(m: ByteArray) {
        var i = 0
        while (i < m.size) {
            val tag = m[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(m, i)
                    i += n
                    val on = v != 0L
                    when (fieldNum) {
                        1 -> enabled = on
                        5 -> encryptionEnabled = on
                        6 -> jsonEnabled = on
                        7 -> tlsEnabled = on
                        9 -> proxyToClientEnabled = on
                        10 -> mapReportingEnabled = on
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(m, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > m.size) break
                    val sub = m.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        2 -> address = String(sub, StandardCharsets.UTF_8)
                        3 -> username = String(sub, StandardCharsets.UTF_8)
                        4 -> password = String(sub, StandardCharsets.UTF_8)
                        8 -> root = String(sub, StandardCharsets.UTF_8)
                        11 -> parseMapReportSettingsBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseMapReportSettingsBytes(b: ByteArray) {
        var i = 0
        while (i < b.size) {
            val tag = b[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(b, i)
                    i += n
                    when (fieldNum) {
                        1 -> mapPublishIntervalSecs = v.toUInt()
                        2 -> mapPositionPrecision = v.toUInt()
                        3 -> mapShouldReportLocation = v != 0L
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(b, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
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
