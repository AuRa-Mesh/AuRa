package com.example.aura.meshwire

import java.nio.charset.StandardCharsets

/**
 * Разбор кадров [FromRadio] при want_config: [MyNodeInfo], [NodeInfo]+[User], [DeviceMetadata].
 */
internal class MeshWireFromRadioProfileAccumulator {
    var myNodeNum: Long? = null
    /** Если известен ID из сессии — сопоставляем NodeInfo до прихода my_info. */
    var expectedNodeNum: Long? = null
    var longName: String? = null
    var shortName: String? = null
    var hwFromUser: Int? = null
    var hwFromMetadata: Int? = null
    var firmwareVersionFromMetadata: String? = null
    var pioEnv: String? = null
    var configComplete: Boolean = false

    fun consumeFromRadio(bytes: ByteArray) {
        parseFromRadioTop(bytes, this)
    }

    fun shouldFinish(): Boolean = configComplete

    fun hasMinimumUserFields(): Boolean =
        longName != null && shortName != null

    fun toProfileOrNull(): MeshWireNodeUserProfile? {
        if (!hasMinimumUserFields()) return null
        return toProfileMaximal()
    }

    /** Имена могут быть пустыми после config_complete — всё равно показать HW. */
    fun toProfileMaximal(): MeshWireNodeUserProfile {
        val hwCode = hwFromMetadata ?: hwFromUser ?: 0
        val hw = MeshWireHardwareModel.wireCodeToName(hwCode)
        return MeshWireNodeUserProfile(
            longName = longName ?: "",
            shortName = shortName ?: "",
            hardwareModel = hw,
            firmwareVersion = firmwareVersionFromMetadata?.takeIf { it.isNotBlank() },
            pioEnv = pioEnv?.takeIf { it.isNotBlank() },
        )
    }
}

private fun parseFromRadioTop(bytes: ByteArray, acc: MeshWireFromRadioProfileAccumulator) {
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
                    acc.configComplete = true
                }
            }
            5 -> i += 4
            1 -> i += 8
            2 -> {
                val (len, lb) = readVarint(bytes, i)
                i += lb
                val ln = len.toInt()
                if (ln < 0 || i + ln > bytes.size) break
                val sub = bytes.copyOfRange(i, i + ln)
                i += ln
                when (fieldNum) {
                    3 -> parseMyInfo(sub, acc)
                    4 -> parseNodeInfo(sub, acc)
                    2, 1 -> tryParseAdminOwnerResponse(sub, acc)
                    13 -> parseDeviceMetadata(sub, acc)
                }
            }
            else -> break
        }
    }
}

private fun tryParseAdminOwnerResponse(meshPacketBytes: ByteArray, acc: MeshWireFromRadioProfileAccumulator) {
    val admin = MeshWireAdminResponseParser.extractAdminPayloadFromMeshPacket(meshPacketBytes) ?: return
    val userBytes = MeshWireAdminResponseParser.extractOwnerResponse(admin) ?: return
    parseUser(userBytes, acc)
    acc.configComplete = true
}

private fun parseMyInfo(bytes: ByteArray, acc: MeshWireFromRadioProfileAccumulator) {
    var i = 0
    while (i < bytes.size) {
        val tag = bytes[i++].toInt() and 0xFF
        val fieldNum = tag ushr 3
        val wire = tag and 0x07
        if (fieldNum == 1 && wire == 0) {
            val (v, n) = readVarint(bytes, i)
            acc.myNodeNum = v
            i += n
            continue
        }
        when (wire) {
            0 -> {
                while (i < bytes.size && (bytes[i++].toInt() and 0x80) != 0) { }
            }
            5 -> i += 4
            1 -> i += 8
            2 -> {
                val (len, lb) = readVarint(bytes, i)
                i += lb
                val ln = len.toInt()
                if (ln < 0 || i + ln > bytes.size) break
                val raw = bytes.copyOfRange(i, i + ln)
                i += ln
                if (fieldNum == 13) {
                    acc.pioEnv = runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
                }
            }
            else -> break
        }
    }
}

private fun parseNodeInfo(bytes: ByteArray, acc: MeshWireFromRadioProfileAccumulator) {
    var i = 0
    var num: Long? = null
    var userSub: ByteArray? = null
    while (i < bytes.size) {
        val tag = bytes[i++].toInt() and 0xFF
        val fieldNum = tag ushr 3
        val wire = tag and 0x07
        when (wire) {
            0 -> {
                val (v, n) = readVarint(bytes, i)
                i += n
                if (fieldNum == 1) num = v
            }
            5 -> i += 4
            1 -> i += 8
            2 -> {
                val (len, lb) = readVarint(bytes, i)
                i += lb
                val ln = len.toInt()
                if (ln < 0 || i + ln > bytes.size) break
                val sub = bytes.copyOfRange(i, i + ln)
                i += ln
                if (fieldNum == 2) userSub = sub
            }
            else -> break
        }
    }
    val mine = acc.myNodeNum ?: acc.expectedNodeNum
    if (mine != null && num == mine && userSub != null) {
        parseUser(userSub, acc)
    }
}

private fun parseUser(bytes: ByteArray, acc: MeshWireFromRadioProfileAccumulator) {
    var i = 0
    while (i < bytes.size) {
        val tag = bytes[i++].toInt() and 0xFF
        val fieldNum = tag ushr 3
        val wire = tag and 0x07
        when (wire) {
            0 -> {
                val (v, n) = readVarint(bytes, i)
                i += n
                if (fieldNum == 5) acc.hwFromUser = v.toInt()
            }
            5 -> i += 4
            1 -> i += 8
            2 -> {
                val (len, lb) = readVarint(bytes, i)
                i += lb
                val ln = len.toInt()
                if (ln < 0 || i + ln > bytes.size) break
                val raw = bytes.copyOfRange(i, i + ln)
                i += ln
                val s = runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
                when (fieldNum) {
                    2 -> acc.longName = s
                    3 -> acc.shortName = s
                }
            }
            else -> break
        }
    }
}

private fun parseDeviceMetadata(bytes: ByteArray, acc: MeshWireFromRadioProfileAccumulator) {
    var i = 0
    while (i < bytes.size) {
        val tag = bytes[i++].toInt() and 0xFF
        val fieldNum = tag ushr 3
        val wire = tag and 0x07
        when (wire) {
            0 -> {
                val (v, n) = readVarint(bytes, i)
                i += n
                if (fieldNum == 9) acc.hwFromMetadata = v.toInt()
            }
            5 -> i += 4
            1 -> i += 8
            2 -> {
                val (len, lb) = readVarint(bytes, i)
                i += lb
                val ln = len.toInt()
                if (ln < 0 || i + ln > bytes.size) break
                val raw = bytes.copyOfRange(i, i + ln)
                i += ln
                if (fieldNum == 1) {
                    acc.firmwareVersionFromMetadata =
                        runCatching { String(raw, StandardCharsets.UTF_8) }.getOrElse { "" }
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
