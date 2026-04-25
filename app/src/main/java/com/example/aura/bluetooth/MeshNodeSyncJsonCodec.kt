package com.example.aura.bluetooth

import android.util.Base64
import com.example.aura.meshwire.MeshStoredChannel
import com.example.aura.meshwire.MeshWireChannelsSyncResult
import com.example.aura.meshwire.MeshWireLoRaConfigLogic
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireLoRaRegions
import com.example.aura.meshwire.MeshWireExternalNotificationPushState
import com.example.aura.meshwire.MeshWireMqttPushState
import com.example.aura.meshwire.MeshWireTelemetryPushState
import com.example.aura.meshwire.MeshWireDevicePushState
import com.example.aura.meshwire.MeshWireModemPreset
import com.example.aura.meshwire.MeshWireNodeUserProfile
import com.example.aura.meshwire.MeshWireSecurityPushState
import org.json.JSONArray
import org.json.JSONObject

internal object MeshNodeSyncJsonCodec {
    private const val V = 1

    fun loraToJson(s: MeshWireLoRaPushState): String {
        val o = JSONObject()
        o.put("v", V)
        o.put("regionCode", s.region.code)
        o.put("usePreset", s.usePreset)
        o.put("modemPreset", s.modemPreset.wireOrdinal)
        o.put("bandwidthText", s.bandwidthText)
        o.put("spreadFactorText", s.spreadFactorText)
        o.put("codingRateText", s.codingRateText)
        o.put("ignoreMqtt", s.ignoreMqtt)
        o.put("configOkToMqtt", s.configOkToMqtt)
        o.put("txEnabled", s.txEnabled)
        o.put("overrideDutyCycle", s.overrideDutyCycle)
        o.put("hopLimit", s.hopLimit)
        o.put("channelNumText", s.channelNumText)
        o.put("sx126xRxBoostedGain", s.sx126xRxBoostedGain)
        o.put("overrideFrequencyMhzText", s.overrideFrequencyMhzText)
        o.put("txPowerDbmText", s.txPowerDbmText)
        o.put("paFanDisabled", s.paFanDisabled)
        return o.toString()
    }

    fun loraFromJson(json: String): MeshWireLoRaPushState? = try {
        val o = JSONObject(json)
        val region = MeshWireLoRaRegions.ALL.firstOrNull { it.code == o.getString("regionCode") }
            ?: MeshWireLoRaRegions.defaultRegion()
        MeshWireLoRaPushState(
            region = region,
            usePreset = o.optBoolean("usePreset", true),
            modemPreset = MeshWireModemPreset.fromWireOrdinal(o.optInt("modemPreset", 0)),
            bandwidthText = o.optString("bandwidthText", "0"),
            spreadFactorText = o.optString("spreadFactorText", "0"),
            codingRateText = o.optString("codingRateText", "0"),
            ignoreMqtt = o.optBoolean("ignoreMqtt", false),
            configOkToMqtt = o.optBoolean("configOkToMqtt", false),
            txEnabled = o.optBoolean("txEnabled", true),
            overrideDutyCycle = o.optBoolean("overrideDutyCycle", false),
            hopLimit = o.optInt("hopLimit", MeshWireLoRaConfigLogic.HOP_LIMIT_DEFAULT)
                .coerceIn(MeshWireLoRaConfigLogic.HOP_LIMIT_MIN, MeshWireLoRaConfigLogic.HOP_LIMIT_MAX),
            channelNumText = o.optString("channelNumText", "0"),
            sx126xRxBoostedGain = o.optBoolean("sx126xRxBoostedGain", false),
            overrideFrequencyMhzText = o.optString("overrideFrequencyMhzText", ""),
            txPowerDbmText = o.optString("txPowerDbmText", "0"),
            paFanDisabled = o.optBoolean("paFanDisabled", false),
        )
    } catch (_: Exception) {
        null
    }

    fun channelRowToJson(c: MeshStoredChannel) =
        JSONObject().apply {
            put("v", V)
            put("rowKey", c.rowKey)
            put("index", c.index)
            put("role", c.role)
            put("name", c.name)
            put("pskB64", Base64.encodeToString(c.psk, Base64.NO_WRAP))
            put("settingsId", (c.settingsId.toLong() and 0xFFFFFFFFL))
            put("uplinkEnabled", c.uplinkEnabled)
            put("downlinkEnabled", c.downlinkEnabled)
            put("positionPrecision", (c.positionPrecision.toLong() and 0xFFFFFFFFL))
        }

    fun channelRowFromJson(o: JSONObject): MeshStoredChannel? = try {
        val psk = Base64.decode(o.getString("pskB64"), Base64.DEFAULT)
        MeshStoredChannel(
            rowKey = o.getString("rowKey"),
            index = o.getInt("index"),
            role = o.getInt("role"),
            name = o.getString("name"),
            psk = psk,
            settingsId = (o.getLong("settingsId") and 0xFFFFFFFFL).toUInt(),
            uplinkEnabled = o.optBoolean("uplinkEnabled", false),
            downlinkEnabled = o.optBoolean("downlinkEnabled", false),
            positionPrecision = (o.getLong("positionPrecision") and 0xFFFFFFFFL).toUInt(),
        )
    } catch (_: Exception) {
        null
    }

    fun channelsResultToJson(r: MeshWireChannelsSyncResult): String {
        val arr = JSONArray()
        r.channels.forEach { arr.put(channelRowToJson(it)) }
        val o = JSONObject()
        o.put("v", V)
        o.put("channels", arr)
        o.put("loraFrequencyMhz", r.loraFrequencyMhz?.toDouble() ?: JSONObject.NULL)
        o.put(
            "loraChannelNum",
            r.loraChannelNum?.let { it.toLong() and 0xFFFFFFFFL } ?: JSONObject.NULL,
        )
        o.put("rawMaxChannelIndex", r.rawMaxChannelIndex)
        return o.toString()
    }

    fun channelsResultFromJson(json: String): MeshWireChannelsSyncResult? = try {
        val o = JSONObject(json)
        val arr = o.getJSONArray("channels")
        val list = ArrayList<MeshStoredChannel>(arr.length())
        for (i in 0 until arr.length()) {
            val row = channelRowFromJson(arr.getJSONObject(i)) ?: continue
            list.add(row)
        }
        val freq = if (o.isNull("loraFrequencyMhz")) null else o.getDouble("loraFrequencyMhz").toFloat()
        val slot = if (o.isNull("loraChannelNum")) null
        else (o.getLong("loraChannelNum") and 0xFFFFFFFFL).toUInt()
        MeshWireChannelsSyncResult(
            channels = list,
            loraFrequencyMhz = freq,
            loraChannelNum = slot,
            rawMaxChannelIndex = o.getInt("rawMaxChannelIndex"),
        )
    } catch (_: Exception) {
        null
    }

    fun securityToJson(s: MeshWireSecurityPushState): String {
        val arr = JSONArray()
        s.adminKeysB64.forEach { arr.put(it) }
        return JSONObject().apply {
            put("v", V)
            put("publicKeyB64", s.publicKeyB64)
            put("privateKeyB64", s.privateKeyB64)
            put("adminKeysB64", arr)
            put("serialEnabled", s.serialEnabled)
            put("debugLogApiEnabled", s.debugLogApiEnabled)
            put("isManaged", s.isManaged)
            put("adminChannelEnabled", s.adminChannelEnabled)
        }.toString()
    }

    fun securityFromJson(json: String): MeshWireSecurityPushState? = try {
        val o = JSONObject(json)
        val admins = ArrayList<String>()
        val arr = o.getJSONArray("adminKeysB64")
        for (i in 0 until arr.length()) admins.add(arr.getString(i))
        MeshWireSecurityPushState(
            publicKeyB64 = o.getString("publicKeyB64"),
            privateKeyB64 = o.getString("privateKeyB64"),
            adminKeysB64 = admins,
            serialEnabled = o.optBoolean("serialEnabled", true),
            debugLogApiEnabled = o.optBoolean("debugLogApiEnabled", false),
            isManaged = o.optBoolean("isManaged", false),
            adminChannelEnabled = o.optBoolean("adminChannelEnabled", false),
        )
    } catch (_: Exception) {
        null
    }

    fun userToJson(p: MeshWireNodeUserProfile): String =
        JSONObject().apply {
            put("v", V)
            put("longName", p.longName)
            put("shortName", p.shortName)
            put("hardwareModel", p.hardwareModel)
            p.firmwareVersion?.let { put("firmwareVersion", it) }
            p.pioEnv?.let { put("pioEnv", it) }
        }.toString()

    fun userFromJson(json: String): MeshWireNodeUserProfile? = try {
        val o = JSONObject(json)
        MeshWireNodeUserProfile(
            longName = o.getString("longName"),
            shortName = o.getString("shortName"),
            hardwareModel = o.getString("hardwareModel"),
            firmwareVersion = o.optString("firmwareVersion", "").takeIf { it.isNotBlank() },
            pioEnv = o.optString("pioEnv", "").takeIf { it.isNotBlank() },
        )
    } catch (_: Exception) {
        null
    }

    fun mqttToJson(s: MeshWireMqttPushState): String =
        JSONObject().apply {
            put("v", V)
            put("enabled", s.enabled)
            put("address", s.address)
            put("username", s.username)
            put("password", s.password)
            put("encryptionEnabled", s.encryptionEnabled)
            put("jsonEnabled", s.jsonEnabled)
            put("tlsEnabled", s.tlsEnabled)
            put("root", s.root)
            put("proxyToClientEnabled", s.proxyToClientEnabled)
            put("mapReportingEnabled", s.mapReportingEnabled)
            put("mapPublishIntervalSecs", (s.mapPublishIntervalSecs.toLong() and 0xFFFFFFFFL))
            put("mapPositionPrecision", (s.mapPositionPrecision.toLong() and 0xFFFFFFFFL))
            put("mapShouldReportLocation", s.mapShouldReportLocation)
            put("configOkToMqtt", s.configOkToMqtt)
        }.toString()

    fun deviceToJson(s: MeshWireDevicePushState): String =
        JSONObject().apply {
            put("v", V)
            put("roleWire", s.roleWire)
            put("rebroadcastModeWire", s.rebroadcastModeWire)
            put("nodeInfoBroadcastSecs", (s.nodeInfoBroadcastSecs.toLong() and 0xFFFFFFFFL))
            put("doubleTapAsButtonPress", s.doubleTapAsButtonPress)
            put("disableTripleClick", s.disableTripleClick)
            put("tzdef", s.tzdef)
            put("ledHeartbeatDisabled", s.ledHeartbeatDisabled)
            put("buttonGpio", (s.buttonGpio.toLong() and 0xFFFFFFFFL))
            put("buzzerGpio", (s.buzzerGpio.toLong() and 0xFFFFFFFFL))
            put("buzzerModeWire", s.buzzerModeWire)
        }.toString()

    fun deviceFromJson(json: String): MeshWireDevicePushState? = try {
        val o = JSONObject(json)
        MeshWireDevicePushState(
            roleWire = o.optInt("roleWire", 0),
            rebroadcastModeWire = o.optInt("rebroadcastModeWire", 0),
            nodeInfoBroadcastSecs = (o.optLong("nodeInfoBroadcastSecs", 0L) and 0xFFFFFFFFL).toUInt(),
            doubleTapAsButtonPress = o.optBoolean("doubleTapAsButtonPress", false),
            disableTripleClick = o.optBoolean("disableTripleClick", false),
            tzdef = o.optString("tzdef", ""),
            ledHeartbeatDisabled = o.optBoolean("ledHeartbeatDisabled", false),
            buttonGpio = (o.optLong("buttonGpio", 0L) and 0xFFFFFFFFL).toUInt(),
            buzzerGpio = (o.optLong("buzzerGpio", 0L) and 0xFFFFFFFFL).toUInt(),
            buzzerModeWire = o.optInt("buzzerModeWire", 0),
        )
    } catch (_: Exception) {
        null
    }

    fun mqttFromJson(json: String): MeshWireMqttPushState? = try {
        val o = JSONObject(json)
        MeshWireMqttPushState(
            enabled = o.optBoolean("enabled", false),
            address = o.optString("address", "mqtt.meshtastic.org"),
            username = o.optString("username", "meshdev"),
            password = o.optString("password", ""),
            encryptionEnabled = o.optBoolean("encryptionEnabled", true),
            jsonEnabled = o.optBoolean("jsonEnabled", false),
            tlsEnabled = o.optBoolean("tlsEnabled", false),
            root = o.optString("root", "msh"),
            proxyToClientEnabled = o.optBoolean("proxyToClientEnabled", false),
            mapReportingEnabled = o.optBoolean("mapReportingEnabled", false),
            mapPublishIntervalSecs = (o.optLong("mapPublishIntervalSecs", 0L) and 0xFFFFFFFFL).toUInt(),
            mapPositionPrecision = (o.optLong("mapPositionPrecision", 32L) and 0xFFFFFFFFL).toUInt(),
            mapShouldReportLocation = o.optBoolean("mapShouldReportLocation", false),
            configOkToMqtt = o.optBoolean("configOkToMqtt", false),
        )
    } catch (_: Exception) {
        null
    }

    fun externalNotificationToJson(s: MeshWireExternalNotificationPushState): String =
        JSONObject().apply {
            put("v", V)
            put("enabled", s.enabled)
            put("outputMs", (s.outputMs.toLong() and 0xFFFFFFFFL))
            put("output", (s.output.toLong() and 0xFFFFFFFFL))
            put("outputVibra", (s.outputVibra.toLong() and 0xFFFFFFFFL))
            put("outputBuzzer", (s.outputBuzzer.toLong() and 0xFFFFFFFFL))
            put("active", s.active)
            put("alertMessage", s.alertMessage)
            put("alertMessageVibra", s.alertMessageVibra)
            put("alertMessageBuzzer", s.alertMessageBuzzer)
            put("alertBell", s.alertBell)
            put("alertBellVibra", s.alertBellVibra)
            put("alertBellBuzzer", s.alertBellBuzzer)
            put("usePwm", s.usePwm)
            put("nagTimeout", (s.nagTimeout.toLong() and 0xFFFFFFFFL))
            put("useI2sAsBuzzer", s.useI2sAsBuzzer)
        }.toString()

    fun externalNotificationFromJson(json: String): MeshWireExternalNotificationPushState? = try {
        val o = JSONObject(json)
        MeshWireExternalNotificationPushState(
            enabled = o.optBoolean("enabled", false),
            outputMs = (o.optLong("outputMs", 1000L) and 0xFFFFFFFFL).toUInt(),
            output = (o.optLong("output", 0L) and 0xFFFFFFFFL).toUInt(),
            outputVibra = (o.optLong("outputVibra", 0L) and 0xFFFFFFFFL).toUInt(),
            outputBuzzer = (o.optLong("outputBuzzer", 0L) and 0xFFFFFFFFL).toUInt(),
            active = o.optBoolean("active", true),
            alertMessage = o.optBoolean("alertMessage", false),
            alertMessageVibra = o.optBoolean("alertMessageVibra", false),
            alertMessageBuzzer = o.optBoolean("alertMessageBuzzer", false),
            alertBell = o.optBoolean("alertBell", false),
            alertBellVibra = o.optBoolean("alertBellVibra", false),
            alertBellBuzzer = o.optBoolean("alertBellBuzzer", false),
            usePwm = o.optBoolean("usePwm", false),
            nagTimeout = (o.optLong("nagTimeout", 0L) and 0xFFFFFFFFL).toUInt(),
            useI2sAsBuzzer = o.optBoolean("useI2sAsBuzzer", false),
        )
    } catch (_: Exception) {
        null
    }

    fun telemetryToJson(s: MeshWireTelemetryPushState): String =
        JSONObject().apply {
            put("v", V)
            put("deviceUpdateIntervalSecs", (s.deviceUpdateIntervalSecs.toLong() and 0xFFFFFFFFL))
            put("environmentUpdateIntervalSecs", (s.environmentUpdateIntervalSecs.toLong() and 0xFFFFFFFFL))
            put("environmentMeasurementEnabled", s.environmentMeasurementEnabled)
            put("environmentScreenEnabled", s.environmentScreenEnabled)
            put("environmentDisplayFahrenheit", s.environmentDisplayFahrenheit)
            put("airQualityEnabled", s.airQualityEnabled)
            put("airQualityIntervalSecs", (s.airQualityIntervalSecs.toLong() and 0xFFFFFFFFL))
            put("powerMeasurementEnabled", s.powerMeasurementEnabled)
            put("powerUpdateIntervalSecs", (s.powerUpdateIntervalSecs.toLong() and 0xFFFFFFFFL))
            put("powerScreenEnabled", s.powerScreenEnabled)
            put("healthMeasurementEnabled", s.healthMeasurementEnabled)
            put("healthUpdateIntervalSecs", (s.healthUpdateIntervalSecs.toLong() and 0xFFFFFFFFL))
            put("healthScreenEnabled", s.healthScreenEnabled)
            put("deviceTelemetryEnabled", s.deviceTelemetryEnabled)
            put("airQualityScreenEnabled", s.airQualityScreenEnabled)
        }.toString()

    fun telemetryFromJson(json: String): MeshWireTelemetryPushState? = try {
        val o = JSONObject(json)
        MeshWireTelemetryPushState(
            deviceUpdateIntervalSecs = (o.optLong("deviceUpdateIntervalSecs", 300L) and 0xFFFFFFFFL).toUInt(),
            environmentUpdateIntervalSecs = (o.optLong("environmentUpdateIntervalSecs", 300L) and 0xFFFFFFFFL)
                .toUInt(),
            environmentMeasurementEnabled = o.optBoolean("environmentMeasurementEnabled", false),
            environmentScreenEnabled = o.optBoolean("environmentScreenEnabled", false),
            environmentDisplayFahrenheit = o.optBoolean("environmentDisplayFahrenheit", false),
            airQualityEnabled = o.optBoolean("airQualityEnabled", false),
            airQualityIntervalSecs = (o.optLong("airQualityIntervalSecs", 300L) and 0xFFFFFFFFL).toUInt(),
            powerMeasurementEnabled = o.optBoolean("powerMeasurementEnabled", false),
            powerUpdateIntervalSecs = (o.optLong("powerUpdateIntervalSecs", 300L) and 0xFFFFFFFFL).toUInt(),
            powerScreenEnabled = o.optBoolean("powerScreenEnabled", false),
            healthMeasurementEnabled = o.optBoolean("healthMeasurementEnabled", false),
            healthUpdateIntervalSecs = (o.optLong("healthUpdateIntervalSecs", 300L) and 0xFFFFFFFFL).toUInt(),
            healthScreenEnabled = o.optBoolean("healthScreenEnabled", false),
            deviceTelemetryEnabled = o.optBoolean("deviceTelemetryEnabled", false),
            airQualityScreenEnabled = o.optBoolean("airQualityScreenEnabled", false),
        )
    } catch (_: Exception) {
        null
    }
}
