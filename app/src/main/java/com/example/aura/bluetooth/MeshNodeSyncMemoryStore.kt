package com.example.aura.bluetooth

import android.content.Context
import com.example.aura.meshwire.MeshStoredChannel
import com.example.aura.meshwire.MeshWireChannelsSyncResult
import com.example.aura.meshwire.MeshWireLoRaPushState
import com.example.aura.meshwire.MeshWireExternalNotificationPushState
import com.example.aura.meshwire.MeshWireMqttPushState
import com.example.aura.meshwire.MeshWireTelemetryPushState
import com.example.aura.meshwire.MeshWireNodeUserProfile
import com.example.aura.meshwire.MeshWireDevicePushState
import com.example.aura.meshwire.MeshWireImportedConfig
import com.example.aura.meshwire.MeshWireSecurityPushState
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Синхронизированные с нодой данные по BLE MAC: RAM + [SharedPreferences], чтобы после перезапуска
 * приложения при той же ноде не терять кэш до следующей синхронизации.
 */
object MeshNodeSyncMemoryStore {
    private const val PREFS = "aura_mesh_node_sync"
    /**
     * Последнее осмысленное user.longName с ноды (для QR / синка профиля на сайт, если нет привязанного MAC
     * или список узлов ещё не тянулся).
     */
    private const val KEY_SITE_EXPORT_LONG_NAME = "site_export_long_name"

    private val lock = ReentrantLock()
    private val loraByDevice = mutableMapOf<String, MeshWireLoRaPushState>()
    private val channelsByDevice = mutableMapOf<String, MeshWireChannelsSyncResult>()
    private val securityByDevice = mutableMapOf<String, MeshWireSecurityPushState>()
    private val userByDevice = mutableMapOf<String, MeshWireNodeUserProfile>()
    private val mqttByDevice = mutableMapOf<String, MeshWireMqttPushState>()
    private val externalNotificationByDevice = mutableMapOf<String, MeshWireExternalNotificationPushState>()
    private val telemetryByDevice = mutableMapOf<String, MeshWireTelemetryPushState>()
    private val deviceByDevice = mutableMapOf<String, MeshWireDevicePushState>()

    @Volatile
    private var appContext: Context? = null

    fun init(applicationContext: Context) {
        appContext = applicationContext.applicationContext
    }

    private fun prefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Не плейсхолдер вида 8-hex (как !A1B2C3D4) — иначе на сайт лучше не слать вместо «человеческого» имени.
     */
    private fun isPlausibleSiteExportLongName(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty() || t == "?") return false
        var h = t.removePrefix("!")
        h = h.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
        if (h.length == 8) return false
        return true
    }

    /**
     * Снимок longName из prefs / любых u|* на диске (после путей с известным MAC).
     */
    fun readSiteProfileLongNameFromGlobalDiskCache(): String? {
        val p = prefs() ?: return null
        p.getString(KEY_SITE_EXPORT_LONG_NAME, null)
            ?.trim()
            ?.takeIf { isPlausibleSiteExportLongName(it) }
            ?.let { return it }
        for (key in p.all.keys.sorted()) {
            if (!key.startsWith("u|")) continue
            val json = p.getString(key, null) ?: continue
            val u = MeshNodeSyncJsonCodec.userFromJson(json) ?: continue
            val n = u.longName.trim()
            if (isPlausibleSiteExportLongName(n)) return n
        }
        return null
    }

    private fun keyLora(k: String) = "l|$k"
    private fun keyChannels(k: String) = "c|$k"
    private fun keySecurity(k: String) = "s|$k"
    private fun keyUser(k: String) = "u|$k"
    private fun keyMqtt(k: String) = "m|$k"
    private fun keyExternalNotification(k: String) = "x|$k"
    private fun keyTelemetry(k: String) = "t|$k"
    private fun keyDevice(k: String) = "d|$k"

    fun normalizeKey(address: String): String =
        address.trim().uppercase(Locale.ROOT)

    /**
     * Сравнение BLE MAC для одной и той же ноды независимо от двоеточий/дефисов в строке
     * (адрес сессии в UI vs [android.bluetooth.BluetoothDevice.getAddress] / [MeshDevice.address]).
     */
    fun bleHardwareIdentityKey(raw: String): String {
        val nk = normalizeKey(raw)
        if (MeshDeviceTransport.isTcpAddress(nk) || MeshDeviceTransport.isUsbAddress(nk)) return nk
        val hex = raw.trim().uppercase(Locale.ROOT).filter { it in '0'..'9' || it in 'A'..'F' }
        return when {
            hex.length >= 12 -> hex.takeLast(12)
            hex.isNotEmpty() -> hex
            else -> nk
        }
    }

    /** Подтянуть с диска только отсутствующие в RAM секции (холодный старт). */
    fun warmFromDisk(rawAddress: String) {
        val p = prefs() ?: return
        val k = normalizeKey(rawAddress)
        lock.withLock {
            if (!loraByDevice.containsKey(k)) {
                p.getString(keyLora(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.loraFromJson(json)?.let { loraByDevice[k] = it }
                }
            }
            if (!channelsByDevice.containsKey(k)) {
                p.getString(keyChannels(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.channelsResultFromJson(json)?.let { channelsByDevice[k] = it }
                }
            }
            if (!securityByDevice.containsKey(k)) {
                p.getString(keySecurity(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.securityFromJson(json)?.let { securityByDevice[k] = it }
                }
            }
            if (!userByDevice.containsKey(k)) {
                p.getString(keyUser(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.userFromJson(json)?.let { userByDevice[k] = it }
                }
            }
            if (!mqttByDevice.containsKey(k)) {
                p.getString(keyMqtt(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.mqttFromJson(json)?.let { mqttByDevice[k] = it }
                }
            }
            if (!externalNotificationByDevice.containsKey(k)) {
                p.getString(keyExternalNotification(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.externalNotificationFromJson(json)?.let {
                        externalNotificationByDevice[k] = it
                    }
                }
            }
            if (!telemetryByDevice.containsKey(k)) {
                p.getString(keyTelemetry(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.telemetryFromJson(json)?.let { telemetryByDevice[k] = it }
                }
            }
            if (!deviceByDevice.containsKey(k)) {
                p.getString(keyDevice(k), null)?.let { json ->
                    MeshNodeSyncJsonCodec.deviceFromJson(json)?.let { deviceByDevice[k] = it }
                }
            }
        }
    }

    /** Снять кэш для MAC (отключение ноды или смена устройства). */
    fun removeDevice(rawAddress: String) {
        val k = normalizeKey(rawAddress)
        lock.withLock {
            loraByDevice.remove(k)
            channelsByDevice.remove(k)
            securityByDevice.remove(k)
            userByDevice.remove(k)
            mqttByDevice.remove(k)
            externalNotificationByDevice.remove(k)
            telemetryByDevice.remove(k)
            deviceByDevice.remove(k)
        }
        prefs()?.edit()
            ?.remove(keyLora(k))
            ?.remove(keyChannels(k))
            ?.remove(keySecurity(k))
            ?.remove(keyUser(k))
            ?.remove(keyMqtt(k))
            ?.remove(keyExternalNotification(k))
            ?.remove(keyTelemetry(k))
            ?.remove(keyDevice(k))
            ?.remove(KEY_SITE_EXPORT_LONG_NAME)
            ?.apply()
    }

    fun getLora(rawAddress: String): MeshWireLoRaPushState? =
        lock.withLock { loraByDevice[normalizeKey(rawAddress)] }

    fun putLora(rawAddress: String, state: MeshWireLoRaPushState) {
        val k = normalizeKey(rawAddress)
        lock.withLock { loraByDevice[k] = state }
        prefs()?.edit()?.putString(keyLora(k), MeshNodeSyncJsonCodec.loraToJson(state))?.apply()
    }

    fun getChannels(rawAddress: String): MeshWireChannelsSyncResult? =
        lock.withLock { channelsByDevice[normalizeKey(rawAddress)] }

    /**
     * Сохраняет снимок каналов с ноды, подмешивая PSK из предыдущего кэша, если в want_config ключ не пришёл
     * (типично после перезагрузки ноды) или пришёл только шорткат `0x01` при том же [MeshStoredChannel.settingsId].
     * Возвращает фактически записанный результат.
     */
    fun putChannels(rawAddress: String, result: MeshWireChannelsSyncResult): MeshWireChannelsSyncResult {
        val k = normalizeKey(rawAddress)
        val merged = lock.withLock {
            val existing = channelsByDevice[k]
            val mergedChannels = mergeChannelListsPreservingPskWhenDeviceOmits(existing?.channels, result.channels)
            val out = result.copy(channels = mergedChannels)
            channelsByDevice[k] = out
            out
        }
        prefs()?.edit()?.putString(keyChannels(k), MeshNodeSyncJsonCodec.channelsResultToJson(merged))?.apply()
        return merged
    }

    /**
     * Слияние по индексу канала — для потока FromRadio, где каналы приходят по одному.
     * Полные замены по-прежнему через [putChannels].
     */
    fun putChannelsMerged(rawAddress: String, incoming: MeshWireChannelsSyncResult) {
        val k = normalizeKey(rawAddress)
        val merged = lock.withLock {
            val existing = channelsByDevice[k]
            val m = if (existing == null) incoming else mergeChannelSyncResults(existing, incoming)
            channelsByDevice[k] = m
            m
        }
        prefs()?.edit()?.putString(keyChannels(k), MeshNodeSyncJsonCodec.channelsResultToJson(merged))?.apply()
    }

    private fun mergeChannelSyncResults(
        old: MeshWireChannelsSyncResult,
        new: MeshWireChannelsSyncResult,
    ): MeshWireChannelsSyncResult {
        val byIdx = old.channels.associateBy { it.index }.toMutableMap()
        for (c in new.channels) {
            val prev = byIdx[c.index]
            byIdx[c.index] = mergePskPreservingWhenDeviceOmits(c, prev)
        }
        val list = byIdx.values.sortedBy { it.index }
        val rawMax = maxOf(
            old.rawMaxChannelIndex,
            new.rawMaxChannelIndex,
            list.maxOfOrNull { it.index } ?: -1,
        )
        return MeshWireChannelsSyncResult(
            channels = list,
            loraFrequencyMhz = new.loraFrequencyMhz ?: old.loraFrequencyMhz,
            loraChannelNum = new.loraChannelNum ?: old.loraChannelNum,
            rawMaxChannelIndex = rawMax,
        )
    }

    private fun isStrongAesPsk(psk: ByteArray): Boolean = psk.size == 16 || psk.size == 32

    private fun hasPreservablePsk(psk: ByteArray): Boolean =
        psk.isNotEmpty() && !psk.contentEquals(byteArrayOf(0))

    /**
     * want_config часто не содержит bytes psk; реже вместо 16/32 байт AES приходит только шорткат из channel.proto.
     */
    private fun mergePskPreservingWhenDeviceOmits(
        incoming: MeshStoredChannel,
        previous: MeshStoredChannel?,
    ): MeshStoredChannel {
        if (previous == null || previous.index != incoming.index) return incoming
        val prevPsk = previous.psk
        if (!hasPreservablePsk(prevPsk)) return incoming
        val incomingPsk = incoming.psk
        val incomingIsFullySpecified =
            incomingPsk.isNotEmpty() && !incomingPsk.contentEquals(MeshStoredChannel.DEFAULT_PSK)
        if (incomingIsFullySpecified) return incoming
        if (incomingPsk.isEmpty()) {
            return incoming.copyForEdit(psk = prevPsk.copyOf())
        }
        if (incomingPsk.contentEquals(MeshStoredChannel.DEFAULT_PSK) &&
            isStrongAesPsk(prevPsk) &&
            incoming.settingsId == previous.settingsId &&
            previous.settingsId != 0U
        ) {
            return incoming.copyForEdit(psk = prevPsk.copyOf())
        }
        return incoming
    }

    private fun mergeChannelListsPreservingPskWhenDeviceOmits(
        existingChannels: List<MeshStoredChannel>?,
        incomingChannels: List<MeshStoredChannel>,
    ): List<MeshStoredChannel> {
        if (existingChannels.isNullOrEmpty()) return incomingChannels
        val prevByIdx = existingChannels.associateBy { it.index }
        return incomingChannels.map { ch ->
            mergePskPreservingWhenDeviceOmits(ch, prevByIdx[ch.index])
        }
    }

    fun getSecurity(rawAddress: String): MeshWireSecurityPushState? =
        lock.withLock { securityByDevice[normalizeKey(rawAddress)] }

    fun putSecurity(rawAddress: String, state: MeshWireSecurityPushState) {
        val k = normalizeKey(rawAddress)
        lock.withLock { securityByDevice[k] = state }
        prefs()?.edit()?.putString(keySecurity(k), MeshNodeSyncJsonCodec.securityToJson(state))?.apply()
    }

    fun getUser(rawAddress: String): MeshWireNodeUserProfile? =
        lock.withLock { userByDevice[normalizeKey(rawAddress)] }

    fun putUser(rawAddress: String, profile: MeshWireNodeUserProfile) {
        val k = normalizeKey(rawAddress)
        lock.withLock { userByDevice[k] = profile }
        val e = prefs()?.edit()?.putString(keyUser(k), MeshNodeSyncJsonCodec.userToJson(profile)) ?: return
        val ln = profile.longName.trim()
        if (isPlausibleSiteExportLongName(ln)) e.putString(KEY_SITE_EXPORT_LONG_NAME, ln)
        e.apply()
    }

    fun getMqtt(rawAddress: String): MeshWireMqttPushState? =
        lock.withLock { mqttByDevice[normalizeKey(rawAddress)] }

    fun putMqtt(rawAddress: String, state: MeshWireMqttPushState) {
        val k = normalizeKey(rawAddress)
        lock.withLock { mqttByDevice[k] = state }
        prefs()?.edit()?.putString(keyMqtt(k), MeshNodeSyncJsonCodec.mqttToJson(state))?.apply()
    }

    fun getExternalNotification(rawAddress: String): MeshWireExternalNotificationPushState? =
        lock.withLock { externalNotificationByDevice[normalizeKey(rawAddress)] }

    fun putExternalNotification(rawAddress: String, state: MeshWireExternalNotificationPushState) {
        val k = normalizeKey(rawAddress)
        lock.withLock { externalNotificationByDevice[k] = state }
        prefs()?.edit()
            ?.putString(keyExternalNotification(k), MeshNodeSyncJsonCodec.externalNotificationToJson(state))
            ?.apply()
    }

    fun getTelemetry(rawAddress: String): MeshWireTelemetryPushState? =
        lock.withLock { telemetryByDevice[normalizeKey(rawAddress)] }

    fun putTelemetry(rawAddress: String, state: MeshWireTelemetryPushState) {
        val k = normalizeKey(rawAddress)
        lock.withLock { telemetryByDevice[k] = state }
        prefs()?.edit()?.putString(keyTelemetry(k), MeshNodeSyncJsonCodec.telemetryToJson(state))?.apply()
    }

    fun getDevice(rawAddress: String): MeshWireDevicePushState? =
        lock.withLock { deviceByDevice[normalizeKey(rawAddress)] }

    /** RAM + SharedPreferences (JSON), как [putLora] / [putChannels]. */
    fun putDevice(rawAddress: String, state: MeshWireDevicePushState) {
        val k = normalizeKey(rawAddress)
        lock.withLock { deviceByDevice[k] = state }
        prefs()?.edit()?.putString(keyDevice(k), MeshNodeSyncJsonCodec.deviceToJson(state))?.apply()
    }

    /** Обновить кэш после успешного импорта JSON (как в Meshtastic после restore). */
    fun putAfterImport(rawAddress: String, cfg: MeshWireImportedConfig) {
        cfg.channelsFromUrl?.let { putChannels(rawAddress, it) }
        cfg.lora?.let { putLora(rawAddress, it) }
        cfg.security?.let { putSecurity(rawAddress, it) }
        cfg.mqtt?.let { putMqtt(rawAddress, it) }
        cfg.externalNotification?.let { putExternalNotification(rawAddress, it) }
        cfg.telemetry?.let { putTelemetry(rawAddress, it) }
        if (cfg.longName != null || cfg.shortName != null) {
            val u = getUser(rawAddress)
            putUser(
                rawAddress,
                MeshWireNodeUserProfile(
                    longName = cfg.longName ?: u?.longName ?: "",
                    shortName = cfg.shortName ?: u?.shortName ?: "",
                    hardwareModel = u?.hardwareModel ?: "UNSET",
                    firmwareVersion = u?.firmwareVersion,
                    pioEnv = u?.pioEnv,
                ),
            )
        }
    }
}
