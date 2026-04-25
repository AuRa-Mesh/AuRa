package com.example.aura.meshwire

/**
 * Разбор [FromRadio]: `config` (5) → [Config.security] (поле 8) и `config_complete` (7).
 */
internal class MeshWireSecuritySyncAccumulator {
    var sawSecurity: Boolean = false
    var configComplete: Boolean = false

    private var publicKey: ByteArray? = null
    private var privateKey: ByteArray? = null
    private val adminKeys: MutableList<ByteArray> = mutableListOf()
    private var isManaged: Boolean? = null
    private var serialEnabled: Boolean? = null
    private var debugLogApiEnabled: Boolean? = null
    private var adminChannelEnabled: Boolean? = null

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

    fun toPushState(): MeshWireSecurityPushState {
        val b = MeshWireSecurityPushState.initial()
        fun b64(bytes: ByteArray?): String =
            if (bytes == null || bytes.isEmpty()) ""
            else android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val admins = adminKeys.map { b64(it) }.filter { it.isNotEmpty() }.take(3)
        return b.copy(
            publicKeyB64 = b64(publicKey),
            privateKeyB64 = b64(privateKey),
            adminKeysB64 = admins,
            isManaged = isManaged ?: b.isManaged,
            serialEnabled = serialEnabled ?: b.serialEnabled,
            debugLogApiEnabled = debugLogApiEnabled ?: b.debugLogApiEnabled,
            adminChannelEnabled = adminChannelEnabled ?: b.adminChannelEnabled,
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
                    if (fieldNum == 8) {
                        sawSecurity = true
                        parseSecurityConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
    }

    private fun parseSecurityConfigBytes(sec: ByteArray) {
        var i = 0
        while (i < sec.size) {
            val tag = sec[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(sec, i)
                    i += n
                    val on = v != 0L
                    when (fieldNum) {
                        4 -> isManaged = on
                        5 -> serialEnabled = on
                        6 -> debugLogApiEnabled = on
                        8 -> adminChannelEnabled = on
                    }
                }
                5 -> i += 4
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(sec, i)
                    i += lb
                    val ln = len.toInt()
                    if (ln < 0 || i + ln > sec.size) break
                    val payload = sec.copyOfRange(i, i + ln)
                    i += ln
                    when (fieldNum) {
                        1 -> publicKey = payload
                        2 -> privateKey = payload
                        3 -> adminKeys.add(payload)
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
