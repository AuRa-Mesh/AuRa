package com.example.aura.bluetooth

import com.example.aura.meshwire.MeshWireStreamFrameCodec
import java.util.Locale

/**
 * Строка адреса устройства: BLE MAC, `tcp:хост:порт` или `usb:путь_устройства:baud`.
 * Константы TCP совпадают с MeshWire Android ([org.meshwire.core.network.transport.StreamFrameCodec],
 * [org.meshwire.core.network.repository.NetworkConstants]).
 */
object MeshDeviceTransport {

    const val MDNS_SERVICE_TYPE = "_meshtastic._tcp"

    /** Подсказка для ручного ввода (mDNS/локальное имя). */
    const val DEFAULT_TCP_HOST = "meshtastic.local"

    const val DEFAULT_TCP_PORT = MeshWireStreamFrameCodec.DEFAULT_TCP_PORT

    /** Типичная скорость USB CDC для нод MeshWire (как в типичных инструкциях к приложению). */
    const val DEFAULT_USB_BAUD = 115200

    /**
     * Порядок перебора при автоподборе (стабильные скорости для CDC/ESP32 и др., как в практике MeshWire).
     */
    val USB_BAUD_CANDIDATES: List<Int> = listOf(
        115200,
        921600,
        460800,
        576000,
        230400,
        57600,
        38400,
        9600,
    )

    data class TcpEndpoint(val host: String, val port: Int)

    data class UsbEndpoint(val deviceName: String, val baud: Int)

    fun isTcpAddress(normalizedKey: String): Boolean =
        normalizedKey.startsWith("TCP:", ignoreCase = true)

    fun isUsbAddress(normalizedKey: String): Boolean =
        normalizedKey.startsWith("USB:", ignoreCase = true)

    /**
     * Разбор сохранённого ключа вида `TCP:HOST:PORT` (после [MeshNodeSyncMemoryStore.normalizeKey]).
     */
    fun parseTcp(normalizedKey: String): TcpEndpoint? {
        val s = normalizedKey.trim()
        if (!s.startsWith("TCP:", ignoreCase = true)) return null
        val rest = s.substring(4).trim()
        val colon = rest.lastIndexOf(':')
        if (colon <= 0) return null
        val host = rest.substring(0, colon).trim()
        val port = rest.substring(colon + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        return TcpEndpoint(host, port)
    }

    /**
     * Формат: `usb:/dev/bus/usb/001/005:115200` (регистр после normalize — `USB:...`).
     */
    fun parseUsb(normalizedKey: String): UsbEndpoint? {
        val s = normalizedKey.trim()
        if (!s.startsWith("USB:", ignoreCase = true)) return null
        val rest = s.substring(4).trim()
        val colon = rest.lastIndexOf(':')
        if (colon <= 0) return null
        val path = rest.substring(0, colon).trim()
        val baud = rest.substring(colon + 1).trim().toIntOrNull() ?: return null
        if (path.isEmpty()) return null
        return UsbEndpoint(path, baud)
    }

    fun formatTcp(host: String, port: Int): String =
        "tcp:${host.trim().lowercase(Locale.ROOT)}:$port".uppercase(Locale.ROOT)

    fun formatUsb(deviceName: String, baud: Int): String =
        "usb:${deviceName.trim()}:$baud".uppercase(Locale.ROOT)
}
