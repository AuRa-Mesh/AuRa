package com.example.aura.meshwire

import kotlin.math.min

/**
 * Разбор [FromRadio] при want_config: `config` (поле 5) → [Config.device] (поле 1) — см. [Config] oneof.
 */
internal class MeshWireDeviceSyncAccumulator {
    var sawDevice: Boolean = false
    var configComplete: Boolean = false

    private var roleWire: Int? = null
    private var rebroadcastWire: Int? = null
    private var nodeInfoSecs: UInt? = null
    private var buttonGpio: UInt? = null
    private var buzzerGpio: UInt? = null
    private var doubleTap: Boolean? = null
    private var disableTripleClick: Boolean? = null
    private var tzdef: String? = null
    private var ledHeartbeatDisabled: Boolean? = null
    private var buzzerModeWire: Int? = null

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
        configComplete = true
    }

    fun shouldFinish(): Boolean = configComplete

    fun toPushState(): MeshWireDevicePushState {
        val base = MeshWireDevicePushState.initial()
        return base.copy(
            roleWire = roleWire ?: base.roleWire,
            rebroadcastModeWire = rebroadcastWire ?: base.rebroadcastModeWire,
            nodeInfoBroadcastSecs = nodeInfoSecs ?: base.nodeInfoBroadcastSecs,
            buttonGpio = buttonGpio ?: base.buttonGpio,
            buzzerGpio = buzzerGpio ?: base.buzzerGpio,
            doubleTapAsButtonPress = doubleTap ?: base.doubleTapAsButtonPress,
            disableTripleClick = disableTripleClick ?: base.disableTripleClick,
            tzdef = tzdef ?: base.tzdef,
            ledHeartbeatDisabled = ledHeartbeatDisabled ?: base.ledHeartbeatDisabled,
            buzzerModeWire = buzzerModeWire ?: base.buzzerModeWire,
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
                    if (fieldNum == 1) {
                        sawDevice = true
                        parseDeviceConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseDeviceConfigBytes(device: ByteArray) {
        var i = 0
        while (i < device.size) {
            val tag = device[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(device, i)
                    i += n
                    when (fieldNum) {
                        1 -> roleWire = v.toInt().coerceIn(0, 31)
                        4 -> buttonGpio = v.toUInt()
                        5 -> buzzerGpio = v.toUInt()
                        6 -> rebroadcastWire = v.toInt().coerceIn(0, 31)
                        7 -> nodeInfoSecs = v.toUInt()
                        8 -> doubleTap = v != 0L
                        10 -> disableTripleClick = v != 0L
                        12 -> ledHeartbeatDisabled = v != 0L
                        13 -> buzzerModeWire = v.toInt().coerceIn(0, 31)
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(device, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > device.size) break
                    val sub = device.copyOfRange(i, i + ln)
                    i += ln
                    if (fieldNum == 11) {
                        tzdef = String(sub, Charsets.UTF_8)
                    }
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
