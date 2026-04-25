package com.example.aura.meshwire

import kotlin.math.min

/**
 * Разбор [FromRadio] при want_config: `config` (поле 5) → [Config.position] (поле 2).
 */
internal class MeshWirePositionSyncAccumulator {
    var sawPosition: Boolean = false
    var configComplete: Boolean = false

    private var positionBroadcastSecs: UInt? = null
    private var positionBroadcastSmartEnabled: Boolean? = null
    private var fixedPosition: Boolean? = null
    private var gpsUpdateInterval: UInt? = null
    private var positionFlags: UInt? = null
    private var rxGpio: UInt? = null
    private var txGpio: UInt? = null
    private var gpsEnGpio: UInt? = null
    private var broadcastSmartMinimumDistance: UInt? = null
    private var broadcastSmartMinimumIntervalSecs: UInt? = null
    private var gpsModeWire: Int? = null

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
                5 -> i = minOf(i + 4, bytes.size)
                1 -> i = minOf(i + 8, bytes.size)
                2 -> {
                    val (len, lb) = readVarint(bytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > bytes.size) break
                    val sub = bytes.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        5 -> parseConfigEnvelope(sub)
                        2, 1 -> tryParseAdminConfigResponse(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun tryParseAdminConfigResponse(meshPacketBytes: ByteArray) {
        val admin = MeshWireAdminResponseParser.extractAdminPayloadFromMeshPacket(meshPacketBytes) ?: return
        val config = MeshWireAdminResponseParser.extractConfigResponse(admin) ?: return
        parseConfigEnvelope(config)
        // Прошивка может не сериализовать пустой PositionConfig (все значения по умолчанию) — поле 2 отсутствует.
        if (!sawPosition) sawPosition = true
        configComplete = true
    }

    fun shouldFinish(): Boolean = configComplete

    fun toPushState(): MeshWirePositionPushState {
        val base = MeshWirePositionPushState.initial()
        return base.copy(
            positionBroadcastSecs = positionBroadcastSecs ?: base.positionBroadcastSecs,
            positionBroadcastSmartEnabled = positionBroadcastSmartEnabled
                ?: base.positionBroadcastSmartEnabled,
            fixedPosition = fixedPosition ?: base.fixedPosition,
            gpsUpdateIntervalSecs = gpsUpdateInterval ?: base.gpsUpdateIntervalSecs,
            positionFlags = positionFlags ?: base.positionFlags,
            rxGpio = rxGpio ?: base.rxGpio,
            txGpio = txGpio ?: base.txGpio,
            gpsEnGpio = gpsEnGpio ?: base.gpsEnGpio,
            broadcastSmartMinimumDistance = broadcastSmartMinimumDistance
                ?: base.broadcastSmartMinimumDistance,
            broadcastSmartMinimumIntervalSecs = broadcastSmartMinimumIntervalSecs
                ?: base.broadcastSmartMinimumIntervalSecs,
            gpsModeWire = gpsModeWire ?: base.gpsModeWire,
        )
    }

    private fun parseConfigEnvelope(configBytes: ByteArray) {
        var i = 0
        while (i < configBytes.size) {
            val tag = configBytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (_, n) = readVarint(configBytes, i)
                    i += n
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(configBytes, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > configBytes.size) break
                    val sub = configBytes.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 2) {
                        sawPosition = true
                        parsePositionConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parsePositionConfigBytes(pos: ByteArray) {
        var i = 0
        while (i < pos.size) {
            val tag = pos[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(pos, i)
                    i += n
                    val u = v.toUInt()
                    when (fieldNum) {
                        1 -> positionBroadcastSecs = u
                        2 -> positionBroadcastSmartEnabled = v != 0L
                        3 -> fixedPosition = v != 0L
                        5 -> gpsUpdateInterval = u
                        7 -> positionFlags = u
                        8 -> rxGpio = u
                        9 -> txGpio = u
                        10 -> broadcastSmartMinimumDistance = u
                        11 -> broadcastSmartMinimumIntervalSecs = u
                        12 -> gpsEnGpio = u
                        13 -> gpsModeWire = v.toInt().coerceIn(0, 7)
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(pos, i)
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
