package com.example.aura.meshwire

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Разбор бинарного [DeviceProfile] (clientonly.proto), как при импорте в типичном mesh-клиенте Android
 * ([ImportProfileUseCase] + [InstallProfileUseCase]).
 */
object MeshWireDeviceProfileWireParser {

    fun parse(bytes: ByteArray): Result<MeshWireImportedConfig> = runCatching {
        var i = 0
        var longName: String? = null
        var shortName: String? = null
        var channelUrl: String? = null
        var localConfig: ByteArray? = null
        var localModule: ByteArray? = null
        var fixedPosition: ByteArray? = null
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> i += skipVarint(bytes, i)
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
                        1 -> longName = String(sub, StandardCharsets.UTF_8)
                        2 -> shortName = String(sub, StandardCharsets.UTF_8)
                        3 -> channelUrl = String(sub, StandardCharsets.UTF_8)
                        4 -> localConfig = sub
                        5 -> localModule = sub
                        6 -> fixedPosition = sub
                    }
                }
                else -> break
            }
        }
        val fromLc = localConfig?.let { parseLocalConfig(it) } ?: LocalConfigParts()
        val fromLm = localModule?.let { parseLocalModule(it) } ?: LocalModuleParts()
        val fixed = fixedPosition?.let { parseMeshPositionLatLonAlt(it) }
        val channelsFromUrl = channelUrl?.let { MeshWireChannelShareUrl.parseChannelsFromUrl(it) }
        MeshWireImportedConfig(
            longName = longName,
            shortName = shortName,
            channelsFromUrl = channelsFromUrl,
            device = fromLc.device,
            lora = fromLc.lora,
            position = fromLc.position,
            rootFixedPosition = null,
            mqtt = fromLm.mqtt,
            externalNotification = fromLm.externalNotification,
            telemetry = fromLm.telemetry,
            security = fromLc.security,
            fixedPositionFromProfile = fixed,
        )
    }

    private data class LocalConfigParts(
        val device: MeshWireDevicePushState? = null,
        val position: MeshWirePositionPushState? = null,
        val lora: MeshWireLoRaPushState? = null,
        val security: MeshWireSecurityPushState? = null,
    )

    private data class LocalModuleParts(
        val mqtt: MeshWireMqttPushState? = null,
        val externalNotification: MeshWireExternalNotificationPushState? = null,
        val telemetry: MeshWireTelemetryPushState? = null,
    )

    private fun parseLocalConfig(bytes: ByteArray): LocalConfigParts {
        var device: MeshWireDevicePushState? = null
        var position: MeshWirePositionPushState? = null
        var lora: MeshWireLoRaPushState? = null
        var security: MeshWireSecurityPushState? = null
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> i += skipVarint(bytes, i)
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
                        1 -> device = parseDeviceConfigBytes(sub)
                        2 -> position = parsePositionConfigBytes(sub)
                        6 -> lora = parseLoRaConfigBytes(sub)
                        9 -> security = parseSecurityConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
        return LocalConfigParts(device, position, lora, security)
    }

    private fun parseLocalModule(bytes: ByteArray): LocalModuleParts {
        var mqtt: MeshWireMqttPushState? = null
        var ext: MeshWireExternalNotificationPushState? = null
        var telem: MeshWireTelemetryPushState? = null
        var i = 0
        while (i < bytes.size) {
            val tag = bytes[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> i += skipVarint(bytes, i)
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
                        1 -> mqtt = parseMqttConfigBytes(sub)
                        3 -> ext = parseExternalNotificationBytes(sub)
                        6 -> telem = parseTelemetryConfigBytes(sub)
                    }
                }
                else -> break
            }
        }
        return LocalModuleParts(mqtt, ext, telem)
    }

    /** [mesh.proto] Position — для [DeviceProfile.fixed_position]. */
    private fun parseMeshPositionLatLonAlt(pos: ByteArray): Triple<Double, Double, Int?>? {
        var latI: Int? = null
        var lonI: Int? = null
        var alt: Int? = null
        var i = 0
        while (i < pos.size) {
            val tag = pos[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(pos, i)
                    i += n
                    if (fieldNum == 3) alt = decodeInt32Varint(v)
                }
                5 -> {
                    if (i + 4 <= pos.size) {
                        val bits = ByteBuffer.wrap(pos, i, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        when (fieldNum) {
                            1 -> latI = bits
                            2 -> lonI = bits
                        }
                    }
                    i += 4
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(pos, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        val la = latI ?: return null
        val lo = lonI ?: return null
        return Triple(la / 1e7, lo / 1e7, alt)
    }

    private fun parseDeviceConfigBytes(device: ByteArray): MeshWireDevicePushState {
        val base = MeshWireDevicePushState.initial()
        var roleWire: Int? = null
        var rebroadcastWire: Int? = null
        var nodeInfoSecs: UInt? = null
        var buttonGpio: UInt? = null
        var buzzerGpio: UInt? = null
        var doubleTap: Boolean? = null
        var disableTripleClick: Boolean? = null
        var tzdef: String? = null
        var ledHeartbeatDisabled: Boolean? = null
        var buzzerModeWire: Int? = null
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
                    if (fieldNum == 11) tzdef = String(sub, StandardCharsets.UTF_8)
                }
                else -> break
            }
        }
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

    private fun parsePositionConfigBytes(pos: ByteArray): MeshWirePositionPushState {
        val base = MeshWirePositionPushState.initial()
        var positionBroadcastSecs: UInt? = null
        var positionBroadcastSmartEnabled: Boolean? = null
        var fixedPosition: Boolean? = null
        var gpsUpdateInterval: UInt? = null
        var positionFlags: UInt? = null
        var rxGpio: UInt? = null
        var txGpio: UInt? = null
        var broadcastSmartMinimumDistance: UInt? = null
        var broadcastSmartMinimumIntervalSecs: UInt? = null
        var gpsEnGpio: UInt? = null
        var gpsModeWire: Int? = null
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
        return base.copy(
            positionBroadcastSecs = positionBroadcastSecs ?: base.positionBroadcastSecs,
            positionBroadcastSmartEnabled = positionBroadcastSmartEnabled ?: base.positionBroadcastSmartEnabled,
            fixedPosition = fixedPosition ?: base.fixedPosition,
            gpsUpdateIntervalSecs = gpsUpdateInterval ?: base.gpsUpdateIntervalSecs,
            positionFlags = positionFlags ?: base.positionFlags,
            rxGpio = rxGpio ?: base.rxGpio,
            txGpio = txGpio ?: base.txGpio,
            broadcastSmartMinimumDistance = broadcastSmartMinimumDistance ?: base.broadcastSmartMinimumDistance,
            broadcastSmartMinimumIntervalSecs = broadcastSmartMinimumIntervalSecs
                ?: base.broadcastSmartMinimumIntervalSecs,
            gpsEnGpio = gpsEnGpio ?: base.gpsEnGpio,
            gpsModeWire = gpsModeWire ?: base.gpsModeWire,
        )
    }

    private fun parseLoRaConfigBytes(lora: ByteArray): MeshWireLoRaPushState {
        val base = MeshWireLoRaPushState.initial()
        var usePreset: Boolean? = null
        var modemPresetOrdinal: Int? = null
        var regionCode: Int? = null
        var hopLimit: Int? = null
        var txEnabled: Boolean? = null
        var txPower: Int? = null
        var channelNum: UInt? = null
        var overrideDutyCycle: Boolean? = null
        var sx126xRxBoostedGain: Boolean? = null
        var overrideFreqMhz: Float? = null
        var ignoreMqtt: Boolean? = null
        var configOkToMqtt: Boolean? = null
        var bandwidth: UInt? = null
        var spreadFactor: UInt? = null
        var codingRate: UInt? = null
        var paFanDisabled: Boolean? = null
        var i = 0
        while (i < lora.size) {
            val tag = lora[i++].toInt() and 0xFF
            val fieldNum = tag ushr 3
            val wire = tag and 0x07
            when (wire) {
                0 -> {
                    val (v, n) = readVarint(lora, i)
                    i += n
                    when (fieldNum) {
                        1 -> usePreset = v != 0L
                        2 -> modemPresetOrdinal = v.toInt()
                        3 -> bandwidth = v.toUInt()
                        4 -> spreadFactor = v.toUInt()
                        5 -> codingRate = v.toUInt()
                        7 -> regionCode = v.toInt()
                        8 -> hopLimit = v.toInt().coerceIn(0, 7)
                        9 -> txEnabled = v != 0L
                        10 -> txPower = decodeInt32Varint(v)
                        11 -> channelNum = v.toUInt()
                        12 -> overrideDutyCycle = v != 0L
                        13 -> sx126xRxBoostedGain = v != 0L
                        15 -> paFanDisabled = v != 0L
                        104 -> ignoreMqtt = v != 0L
                        105 -> configOkToMqtt = v != 0L
                    }
                }
                5 -> {
                    if (fieldNum == 14 && i + 4 <= lora.size) {
                        val bits = (lora[i].toInt() and 0xFF) or
                            ((lora[i + 1].toInt() and 0xFF) shl 8) or
                            ((lora[i + 2].toInt() and 0xFF) shl 16) or
                            ((lora[i + 3].toInt() and 0xFF) shl 24)
                        overrideFreqMhz = java.lang.Float.intBitsToFloat(bits)
                    }
                    i += 4
                }
                1 -> i += 8
                2 -> {
                    val (len, lb) = readVarint(lora, i)
                    i += lb
                    i += len.toInt().coerceAtLeast(0)
                }
                else -> break
            }
        }
        val region = regionCode?.let { MeshWireLoRaRegions.fromProtoCode(it) } ?: base.region
        val modem = modemPresetOrdinal?.let { MeshWireModemPreset.fromWireOrdinal(it) } ?: base.modemPreset
        val hop = hopLimit?.let { MeshWireLoRaConfigLogic.clampHopLimit(it) } ?: base.hopLimit
        val chText = channelNum?.let { it.toString() } ?: base.channelNumText
        val freqTxt = when {
            overrideFreqMhz != null && overrideFreqMhz!! > 0f ->
                String.format(java.util.Locale.US, "%.3f", overrideFreqMhz!!)
            else -> base.overrideFrequencyMhzText
        }
        val pwrTxt = txPower?.let { it.toString() } ?: base.txPowerDbmText
        return base.copy(
            region = region,
            usePreset = usePreset ?: base.usePreset,
            modemPreset = modem,
            bandwidthText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(bandwidth?.toString() ?: base.bandwidthText),
            spreadFactorText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(
                spreadFactor?.toString() ?: base.spreadFactorText,
            ),
            codingRateText = MeshWireLoRaConfigLogic.sanitizeUInt32Input(
                codingRate?.toString() ?: base.codingRateText,
            ),
            ignoreMqtt = ignoreMqtt ?: base.ignoreMqtt,
            configOkToMqtt = configOkToMqtt ?: base.configOkToMqtt,
            txEnabled = txEnabled ?: base.txEnabled,
            overrideDutyCycle = overrideDutyCycle ?: base.overrideDutyCycle,
            hopLimit = hop,
            channelNumText = MeshWireLoRaConfigLogic.sanitizeChannelNumInput(chText),
            sx126xRxBoostedGain = sx126xRxBoostedGain ?: base.sx126xRxBoostedGain,
            overrideFrequencyMhzText = freqTxt,
            txPowerDbmText = MeshWireLoRaConfigLogic.sanitizeIntSigned(pwrTxt),
            paFanDisabled = paFanDisabled ?: base.paFanDisabled,
        )
    }

    private fun parseSecurityConfigBytes(sec: ByteArray): MeshWireSecurityPushState {
        val base = MeshWireSecurityPushState.initial()
        val adminKeys = ArrayList<String>()
        var publicKey: ByteArray? = null
        var privateKey: ByteArray? = null
        var isManaged: Boolean? = null
        var serialEnabled: Boolean? = null
        var debugLogApiEnabled: Boolean? = null
        var adminChannelEnabled: Boolean? = null
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
                        3 -> adminKeys.add(Base64.encodeToString(payload, Base64.NO_WRAP))
                    }
                }
                else -> break
            }
        }
        fun b64(b: ByteArray?): String =
            if (b == null || b.isEmpty()) "" else Base64.encodeToString(b, Base64.NO_WRAP)
        return base.copy(
            publicKeyB64 = b64(publicKey),
            privateKeyB64 = b64(privateKey),
            adminKeysB64 = adminKeys.take(3),
            isManaged = isManaged ?: base.isManaged,
            serialEnabled = serialEnabled ?: base.serialEnabled,
            debugLogApiEnabled = debugLogApiEnabled ?: base.debugLogApiEnabled,
            adminChannelEnabled = adminChannelEnabled ?: base.adminChannelEnabled,
        )
    }

    private fun parseMqttConfigBytes(m: ByteArray): MeshWireMqttPushState {
        val b = MeshWireMqttPushState.initial()
        var enabled: Boolean? = null
        var address: String? = null
        var username: String? = null
        var password: String? = null
        var encryptionEnabled: Boolean? = null
        var jsonEnabled: Boolean? = null
        var tlsEnabled: Boolean? = null
        var root: String? = null
        var proxyToClientEnabled: Boolean? = null
        var mapReportingEnabled: Boolean? = null
        var mapPublishIntervalSecs: UInt? = null
        var mapPositionPrecision: UInt? = null
        var mapShouldReportLocation: Boolean? = null
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
                        11 -> {
                            val t = parseMapReportSettingsBytes(sub)
                            mapPublishIntervalSecs = t.first
                            mapPositionPrecision = t.second
                            mapShouldReportLocation = t.third
                        }
                    }
                }
                else -> break
            }
        }
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

    private fun parseMapReportSettingsBytes(b: ByteArray): Triple<UInt?, UInt?, Boolean?> {
        var mapPublishIntervalSecs: UInt? = null
        var mapPositionPrecision: UInt? = null
        var mapShouldReportLocation: Boolean? = null
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
        return Triple(mapPublishIntervalSecs, mapPositionPrecision, mapShouldReportLocation)
    }

    private fun parseExternalNotificationBytes(m: ByteArray): MeshWireExternalNotificationPushState {
        val b = MeshWireExternalNotificationPushState.initial()
        var enabled: Boolean? = null
        var active: Boolean? = null
        var alertMessage: Boolean? = null
        var alertBell: Boolean? = null
        var usePwm: Boolean? = null
        var alertMessageVibra: Boolean? = null
        var alertMessageBuzzer: Boolean? = null
        var alertBellVibra: Boolean? = null
        var alertBellBuzzer: Boolean? = null
        var useI2sAsBuzzer: Boolean? = null
        var outputMs: UInt? = null
        var output: UInt? = null
        var outputVibra: UInt? = null
        var outputBuzzer: UInt? = null
        var nagTimeout: UInt? = null
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
        return b.copy(
            enabled = enabled ?: b.enabled,
            active = active ?: b.active,
            alertMessage = alertMessage ?: b.alertMessage,
            alertBell = alertBell ?: b.alertBell,
            usePwm = usePwm ?: b.usePwm,
            alertMessageVibra = alertMessageVibra ?: b.alertMessageVibra,
            alertMessageBuzzer = alertMessageBuzzer ?: b.alertMessageBuzzer,
            alertBellVibra = alertBellVibra ?: b.alertBellVibra,
            alertBellBuzzer = alertBellBuzzer ?: b.alertBellBuzzer,
            useI2sAsBuzzer = useI2sAsBuzzer ?: b.useI2sAsBuzzer,
            outputMs = outputMs ?: b.outputMs,
            output = output ?: b.output,
            outputVibra = outputVibra ?: b.outputVibra,
            outputBuzzer = outputBuzzer ?: b.outputBuzzer,
            nagTimeout = nagTimeout ?: b.nagTimeout,
        )
    }

    private fun parseTelemetryConfigBytes(t: ByteArray): MeshWireTelemetryPushState {
        val b = MeshWireTelemetryPushState.initial()
        var deviceUpdateIntervalSecs: UInt? = null
        var environmentUpdateIntervalSecs: UInt? = null
        var environmentMeasurementEnabled: Boolean? = null
        var environmentScreenEnabled: Boolean? = null
        var environmentDisplayFahrenheit: Boolean? = null
        var airQualityEnabled: Boolean? = null
        var airQualityIntervalSecs: UInt? = null
        var powerMeasurementEnabled: Boolean? = null
        var powerUpdateIntervalSecs: UInt? = null
        var powerScreenEnabled: Boolean? = null
        var healthMeasurementEnabled: Boolean? = null
        var healthUpdateIntervalSecs: UInt? = null
        var healthScreenEnabled: Boolean? = null
        var deviceTelemetryEnabled: Boolean? = null
        var airQualityScreenEnabled: Boolean? = null
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
        return b.copy(
            deviceUpdateIntervalSecs = deviceUpdateIntervalSecs ?: b.deviceUpdateIntervalSecs,
            environmentUpdateIntervalSecs = environmentUpdateIntervalSecs ?: b.environmentUpdateIntervalSecs,
            environmentMeasurementEnabled = environmentMeasurementEnabled ?: b.environmentMeasurementEnabled,
            environmentScreenEnabled = environmentScreenEnabled ?: b.environmentScreenEnabled,
            environmentDisplayFahrenheit = environmentDisplayFahrenheit ?: b.environmentDisplayFahrenheit,
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

    private fun skipVarint(bytes: ByteArray, start: Int): Int = readVarint(bytes, start).second

    /** Protobuf `int32` как varint (как в [MeshWireLoRaSyncAccumulator]). */
    private fun decodeInt32Varint(v: Long): Int {
        if (v <= Int.MAX_VALUE.toLong() && v >= Int.MIN_VALUE.toLong()) return v.toInt()
        return 0
    }
}
