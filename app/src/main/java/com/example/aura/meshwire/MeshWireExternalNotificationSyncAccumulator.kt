package com.example.aura.meshwire

import kotlin.math.min

/**
 * Разбор [FromRadio] при want_config: `moduleConfig` (поле 9) → [ModuleConfig.external_notification] (поле 3).
 */
internal class MeshWireExternalNotificationSyncAccumulator {
    var sawExternal: Boolean = false
    var configComplete: Boolean = false

    private var enabled: Boolean? = null
    private var outputMs: UInt? = null
    private var output: UInt? = null
    private var outputVibra: UInt? = null
    private var outputBuzzer: UInt? = null
    private var active: Boolean? = null
    private var alertMessage: Boolean? = null
    private var alertMessageVibra: Boolean? = null
    private var alertMessageBuzzer: Boolean? = null
    private var alertBell: Boolean? = null
    private var alertBellVibra: Boolean? = null
    private var alertBellBuzzer: Boolean? = null
    private var usePwm: Boolean? = null
    private var nagTimeout: UInt? = null
    private var useI2sAsBuzzer: Boolean? = null

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

    fun toPushState(): MeshWireExternalNotificationPushState {
        val b = MeshWireExternalNotificationPushState.initial()
        return b.copy(
            enabled = enabled ?: b.enabled,
            outputMs = outputMs ?: b.outputMs,
            output = output ?: b.output,
            outputVibra = outputVibra ?: b.outputVibra,
            outputBuzzer = outputBuzzer ?: b.outputBuzzer,
            active = active ?: b.active,
            alertMessage = alertMessage ?: b.alertMessage,
            alertMessageVibra = alertMessageVibra ?: b.alertMessageVibra,
            alertMessageBuzzer = alertMessageBuzzer ?: b.alertMessageBuzzer,
            alertBell = alertBell ?: b.alertBell,
            alertBellVibra = alertBellVibra ?: b.alertBellVibra,
            alertBellBuzzer = alertBellBuzzer ?: b.alertBellBuzzer,
            usePwm = usePwm ?: b.usePwm,
            nagTimeout = nagTimeout ?: b.nagTimeout,
            useI2sAsBuzzer = useI2sAsBuzzer ?: b.useI2sAsBuzzer,
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
                    if (fieldNum == 3) {
                        sawExternal = true
                        parseExternalNotificationBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseExternalNotificationBytes(m: ByteArray) {
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
                        4 -> active = on
                        5 -> alertMessage = on
                        6 -> alertBell = on
                        7 -> usePwm = on
                        10 -> alertMessageVibra = on
                        11 -> alertMessageBuzzer = on
                        12 -> alertBellVibra = on
                        13 -> alertBellBuzzer = on
                        15 -> useI2sAsBuzzer = on
                        2 -> outputMs = v.toUInt()
                        3 -> output = v.toUInt()
                        8 -> outputVibra = v.toUInt()
                        9 -> outputBuzzer = v.toUInt()
                        14 -> nagTimeout = v.toUInt()
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(m, i)
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
