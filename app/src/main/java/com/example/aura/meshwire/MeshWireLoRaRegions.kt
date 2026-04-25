package com.example.aura.meshwire

import java.util.Locale

/**
 * Диапазоны частот и лимиты как в приложении mesh:
 * — enum `RegionCode` в [meshwire config.proto](https://github.com/meshtastic/protobufs/blob/master/meshtastic/config.proto)
 * — таблица [docs/blocks/_lora-regions.mdx](https://github.com/meshtastic/meshwire/blob/master/docs/blocks/_lora-regions.mdx)
 *
 * Android APK использует те же коды региона в protobuf.
 */
data class MeshWireLoRaRegion(
    /** Код из protobuf, напр. RU, EU_868 */
    val code: String,
    val description: String,
    /** Диапазон МГц без суффикса, напр. "868.7 - 869.2" */
    val frequencyRangeMhz: String,
    val dutyCyclePercent: String,
    /** Пусто если в документации не указано */
    val powerLimitDbm: String,
) {
    val menuTitle: String get() = "$description ($code)"

    fun detailText(): String {
        val pwr = powerLimitDbm.ifBlank { "—" }
        return "Диапазон: $frequencyRangeMhz MHz · duty cycle $dutyCyclePercent% · макс. мощность $pwr dBm"
    }

    /** Середина диапазона как подсказка для «Переопределить частоту». */
    fun suggestMidFrequencyMhz(): String? {
        val normalized = frequencyRangeMhz.replace("–", "-").replace("—", "-")
        val parts = normalized.split("-").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val a = parts[0].toDoubleOrNull() ?: return null
        val b = parts[1].toDoubleOrNull() ?: return null
        return String.format(Locale.US, "%.3f", (a + b) / 2.0)
    }
}

object MeshWireLoRaRegions {
    /** Порядок совпадает с `RegionCode` в config.proto (без UNSET). */
    val ALL: List<MeshWireLoRaRegion> = listOf(
        MeshWireLoRaRegion("US", "United States", "902.0 - 928.0", "100", "30"),
        MeshWireLoRaRegion("EU_433", "European Union 433MHz", "433.0 - 434.0", "10", "12"),
        MeshWireLoRaRegion("EU_868", "European Union 868MHz", "869.4 - 869.65", "10", "27"),
        MeshWireLoRaRegion("CN", "China", "470.0 - 510.0", "100", "19"),
        MeshWireLoRaRegion("JP", "Japan", "920.8 - 927.8", "100", "16"),
        MeshWireLoRaRegion("ANZ", "Australia & New Zealand", "915.0 - 928.0", "100", "30"),
        MeshWireLoRaRegion("KR", "Korea", "920.0 - 923.0", "100", ""),
        MeshWireLoRaRegion("TW", "Taiwan", "920.0 - 925.0", "100", "27"),
        MeshWireLoRaRegion("RU", "Russia", "868.7 - 869.2", "100", "20"),
        MeshWireLoRaRegion("IN", "India", "865.0 - 867.0", "100", "30"),
        MeshWireLoRaRegion("NZ_865", "New Zealand 865MHz", "864.0 - 868.0", "100", "36"),
        MeshWireLoRaRegion("TH", "Thailand", "920.0 - 925.0", "100", "16"),
        MeshWireLoRaRegion("LORA_24", "2.4 GHz band worldwide", "2400.0 - 2483.5", "100", "10"),
        MeshWireLoRaRegion("UA_433", "Ukraine 433MHz", "433.0 - 434.7", "10", "10"),
        MeshWireLoRaRegion("UA_868", "Ukraine 868MHz", "868.0 - 868.6", "1", "14"),
        MeshWireLoRaRegion("MY_433", "Malaysia 433MHz", "433.0 - 435.0", "100", "20"),
        MeshWireLoRaRegion("MY_919", "Malaysia 919MHz", "919.0 - 924.0", "100", "27"),
        MeshWireLoRaRegion("SG_923", "Singapore 923MHz", "917.0 - 925.0", "100", "20"),
        MeshWireLoRaRegion("PH_433", "Philippines 433MHz", "433.0 - 434.7", "100", "10"),
        MeshWireLoRaRegion("PH_868", "Philippines 868MHz", "868.0 - 869.4", "100", "14"),
        MeshWireLoRaRegion("PH_915", "Philippines 915MHz", "915.0 - 918.0", "100", "24"),
        MeshWireLoRaRegion("ANZ_433", "Australia & New Zealand 433MHz", "433.05 - 434.79", "100", "14"),
        MeshWireLoRaRegion("KZ_433", "Kazakhstan 433 MHz", "433.075 - 434.775", "100", "10"),
        MeshWireLoRaRegion("KZ_863", "Kazakhstan 863 MHz", "863.0 - 868.0", "100", "30"),
        MeshWireLoRaRegion("NP_865", "Nepal 865MHz", "865.0 - 868.0", "100", ""),
        MeshWireLoRaRegion("BR_902", "Brazil 902MHz", "902.0 - 907.5", "100", "30"),
    )

    fun defaultRegion(): MeshWireLoRaRegion =
        ALL.firstOrNull { it.code == "RU" } ?: ALL.first()

    /** `RegionCode` из protobuf: 1 = US, 0 или вне диапазона — регион по умолчанию. */
    fun fromProtoCode(code: Int): MeshWireLoRaRegion {
        if (code <= 0 || code > ALL.size) return defaultRegion()
        return ALL[code - 1]
    }
}

/** Значение `RegionCode` в protobuf (1 = US, 9 = RU, …). Порядок [MeshWireLoRaRegions.ALL] совпадает с enum без UNSET. */
fun MeshWireLoRaRegion.toProtoRegionCode(): Int {
    val i = MeshWireLoRaRegions.ALL.indexOfFirst { it.code == code }
    check(i >= 0) { "Неизвестный регион: $code" }
    return i + 1
}
