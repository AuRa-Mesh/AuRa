package com.example.aura.meshwire

import kotlin.math.min

/**
 * Разбор [FromRadio]: `moduleConfig` (9) → [ModuleConfig.telemetry] (поле 6) и `config_complete_id` (7).
 */
internal class MeshWireTelemetrySyncAccumulator {
    var sawTelemetry: Boolean = false
    var configComplete: Boolean = false

    private var deviceUpdateIntervalSecs: UInt? = null
    private var environmentUpdateIntervalSecs: UInt? = null
    private var environmentMeasurementEnabled: Boolean? = null
    private var environmentScreenEnabled: Boolean? = null
    private var environmentDisplayFahrenheit: Boolean? = null
    private var airQualityEnabled: Boolean? = null
    private var airQualityIntervalSecs: UInt? = null
    private var powerMeasurementEnabled: Boolean? = null
    private var powerUpdateIntervalSecs: UInt? = null
    private var powerScreenEnabled: Boolean? = null
    private var healthMeasurementEnabled: Boolean? = null
    private var healthUpdateIntervalSecs: UInt? = null
    private var healthScreenEnabled: Boolean? = null
    private var deviceTelemetryEnabled: Boolean? = null
    private var airQualityScreenEnabled: Boolean? = null

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

    fun toPushState(): MeshWireTelemetryPushState {
        val b = MeshWireTelemetryPushState.initial()
        return b.copy(
            deviceUpdateIntervalSecs = deviceUpdateIntervalSecs ?: b.deviceUpdateIntervalSecs,
            environmentUpdateIntervalSecs = environmentUpdateIntervalSecs ?: b.environmentUpdateIntervalSecs,
            environmentMeasurementEnabled = environmentMeasurementEnabled
                ?: b.environmentMeasurementEnabled,
            environmentScreenEnabled = environmentScreenEnabled ?: b.environmentScreenEnabled,
            environmentDisplayFahrenheit = environmentDisplayFahrenheit
                ?: b.environmentDisplayFahrenheit,
            airQualityEnabled = airQualityEnabled ?: b.airQualityEnabled,
            airQualityIntervalSecs = airQualityIntervalSecs ?: b.airQualityIntervalSecs,
            powerMeasurementEnabled = powerMeasurementEnabled ?: b.powerMeasurementEnabled,
            powerUpdateIntervalSecs = powerUpdateIntervalSecs ?: b.powerUpdateIntervalSecs,
            powerScreenEnabled = powerScreenEnabled ?: b.powerScreenEnabled,
            healthMeasurementEnabled = healthMeasurementEnabled ?: b.healthMeasurementEnabled,
            healthUpdateIntervalSecs = healthUpdateIntervalSecs ?: b.healthUpdateIntervalSecs,
            healthScreenEnabled = healthScreenEnabled ?: b.healthScreenEnabled,
            deviceTelemetryEnabled = deviceTelemetryEnabled ?: b.deviceTelemetryEnabled,
            airQualityScreenEnabled = airQualityScreenEnabled ?: b.airQualityScreenEnabled,
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
                    if (fieldNum == 6) {
                        sawTelemetry = true
                        parseTelemetryConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseTelemetryConfigBytes(t: ByteArray) {
        var i = 0
        while (i < t.size) {
            val tag = t[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(t, i)
                    i += n
                    val u = v.toUInt()
                    when (fieldNum) {
                        1 -> deviceUpdateIntervalSecs = u
                        2 -> environmentUpdateIntervalSecs = u
                        3 -> environmentMeasurementEnabled = v != 0L
                        4 -> environmentScreenEnabled = v != 0L
                        5 -> environmentDisplayFahrenheit = v != 0L
                        6 -> airQualityEnabled = v != 0L
                        7 -> airQualityIntervalSecs = u
                        8 -> powerMeasurementEnabled = v != 0L
                        9 -> powerUpdateIntervalSecs = u
                        10 -> powerScreenEnabled = v != 0L
                        11 -> healthMeasurementEnabled = v != 0L
                        12 -> healthUpdateIntervalSecs = u
                        13 -> healthScreenEnabled = v != 0L
                        14 -> deviceTelemetryEnabled = v != 0L
                        15 -> airQualityScreenEnabled = v != 0L
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(t, i)
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
