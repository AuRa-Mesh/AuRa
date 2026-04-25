package com.example.aura.firmware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private val OTA_SERVICE: UUID = UUID.fromString("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")
private val OTA_WRITE: UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130005")
private val OTA_NOTIFY: UUID = UUID.fromString("62ec0272-3ec5-11eb-b378-0242ac130003")
private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private const val REBOOT_WAIT_MS = 5_000L
private const val CHUNK = 512

/** Как в MeshWire [calculateMacPlusOne]. */
internal fun calculateMacPlusOne(macAddress: String): String {
    val parts = macAddress.split(":")
    if (parts.size != 6) return macAddress
    val last = parts[5].toIntOrNull(16) ?: return macAddress
    val inc = ((last + 1) and 0xFF).toString(16).uppercase().padStart(2, '0')
    return parts.take(5).joinToString(":") + ":" + inc
}

/**
 * ESP32 Unified OTA по BLE (как [org.meshwire.feature.firmware.ota.BleOtaTransport]).
 */
@SuppressLint("MissingPermission")
class Esp32BleOtaClient(private val context: Context) {

    suspend fun runOtaAfterReboot(
        meshMac: String,
        firmware: ByteArray,
        sha256HexLower: String,
        onStatus: (String) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        delay(REBOOT_WAIT_MS)
        onStatus("Поиск OTA…")
        val dev = scanForOta(meshMac) ?: error("OTA устройство не найдено по BLE.")
        onStatus("Подключение…")
        val g = OtaSession(context, dev)
        g.connectAndPrepare()
        try {
            onStatus("Режим OTA…")
            g.sendHandshake(firmware.size.toLong(), sha256HexLower, onStatus)
            onStatus("Передача…")
            g.streamFirmware(firmware, onProgress)
        } finally {
            g.close()
        }
    }

    private suspend fun scanForOta(meshMac: String): BluetoothDevice? {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return null
        val want = setOf(meshMac.uppercase(), calculateMacPlusOne(meshMac))
        val def = CompletableDeferred<BluetoothDevice?>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val a = result.device.address.uppercase()
                if (a in want && !def.isCompleted) {
                    try {
                        adapter.bluetoothLeScanner.stopScan(this)
                    } catch (_: Exception) {
                    }
                    def.complete(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (!def.isCompleted) def.complete(null)
            }
        }
        try {
            adapter.bluetoothLeScanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(OTA_SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                cb,
            )
        } catch (_: SecurityException) {
            return null
        }
        val found = withTimeoutOrNull(18_000) { def.await() }
        try {
            adapter.bluetoothLeScanner.stopScan(cb)
        } catch (_: Exception) {
        }
        return found ?: runCatching { adapter.getRemoteDevice(calculateMacPlusOne(meshMac)) }.getOrNull()
    }

    private class OtaSession(
        private val ctx: Context,
        private val device: BluetoothDevice,
    ) {
        private var gatt: BluetoothGatt? = null
        private var writeChar: BluetoothGattCharacteristic? = null
        private var negotiatedMtu = 23
        private val notifyLines = Channel<String>(Channel.UNLIMITED)
        private var writeDone: CompletableDeferred<Int>? = null
        private var prepDone: CompletableDeferred<Unit>? = null

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.requestMtu(517)
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu = mtu
                g.discoverServices()
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    prepDone?.completeExceptionally(IllegalStateException("discover $status"))
                    return
                }
                val s = g.getService(OTA_SERVICE) ?: run {
                    prepDone?.completeExceptionally(IllegalStateException("no OTA service"))
                    return
                }
                val w = s.getCharacteristic(OTA_WRITE)
                val n = s.getCharacteristic(OTA_NOTIFY)
                if (w == null || n == null) {
                    prepDone?.completeExceptionally(IllegalStateException("no chars"))
                    return
                }
                writeChar = w
                g.setCharacteristicNotification(n, true)
                val d = n.getDescriptor(CCCD) ?: run {
                    prepDone?.completeExceptionally(IllegalStateException("no CCCD"))
                    return
                }
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    prepDone?.complete(Unit)
                } else {
                    prepDone?.completeExceptionally(IllegalStateException("cccd $status"))
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                characteristic.value?.decodeToString()?.let { notifyLines.trySend(it) }
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                writeDone?.complete(status)
            }
        }

        suspend fun connectAndPrepare() {
            val d = CompletableDeferred<Unit>()
            prepDone = d
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(ctx, false, callback)
            }
            d.await()
            prepDone = null
        }

        suspend fun sendHandshake(
            sizeBytes: Long,
            sha256Hex: String,
            onStatus: (String) -> Unit,
        ) {
            val data = "OTA $sizeBytes $sha256Hex\n".encodeToByteArray()
            writeBytesGatt(data, needAck = true)
            var ok = false
            while (!ok) {
                val line = withTimeout(120_000) { notifyLines.receive() }
                val t = line.trim()
                when {
                    t == "ERASING" -> onStatus("Стирание флеш…")
                    t == "OK" || t.startsWith("OK ") -> ok = true
                    t.startsWith("ERR ") -> {
                        val msg = t.removePrefix("ERR ").trim()
                        if (msg.contains("Hash Rejected", true)) error("Хеш отклонён устройством")
                        error(msg)
                    }
                }
            }
        }

        suspend fun streamFirmware(data: ByteArray, onProgress: (Float) -> Unit) {
            var off = 0
            while (off < data.size) {
                val n = minOf(CHUNK, data.size - off)
                val chunk = data.copyOfRange(off, off + n)
                writeBytesGatt(chunk, needAck = false)
                val line = withTimeout(60_000) { notifyLines.receive() }
                val t = line.trim()
                when {
                    t == "ACK" -> Unit
                    t == "OK" || t.startsWith("OK ") -> {
                        if (off + n >= data.size) {
                            onProgress(1f)
                            return
                        }
                    }
                    t.startsWith("ERR ") -> {
                        val msg = t.removePrefix("ERR ").trim()
                        if (msg.contains("Hash Mismatch", true)) error("Несовпадение хеша")
                        error(msg)
                    }
                }
                off += n
                onProgress(off.toFloat() / data.size.toFloat())
            }
            val last = withTimeout(60_000) { notifyLines.receive() }.trim()
            if (!last.startsWith("OK") && last != "OK") {
                if (last.startsWith("ERR")) error(last.removePrefix("ERR ").trim())
            }
        }

        private suspend fun writeBytesGatt(data: ByteArray, needAck: Boolean) {
            val g = gatt ?: error("gatt")
            val c = writeChar ?: error("write")
            val max = (negotiatedMtu - 3).coerceAtLeast(20)
            var i = 0
            while (i < data.size) {
                val len = minOf(max, data.size - i)
                val part = data.copyOfRange(i, i + len)
                val wt =
                    if (needAck) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                c.writeType = wt
                if (wt == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                    val done = CompletableDeferred<Int>()
                    writeDone = done
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    val st = g.writeCharacteristic(c, part, wt)
                    if (st != BluetoothGatt.GATT_SUCCESS) error("write $st")
                } else {
                    @Suppress("DEPRECATION")
                    c.value = part
                    @Suppress("DEPRECATION")
                    if (g.writeCharacteristic(c) != true) error("write false")
                }
                if (wt == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                    val st = writeDone!!.await()
                    writeDone = null
                    if (st != BluetoothGatt.GATT_SUCCESS) error("write cb $st")
                }
                i += len
            }
        }

        fun close() {
            try {
                gatt?.disconnect()
            } catch (_: Exception) {
            }
            try {
                gatt?.close()
            } catch (_: Exception) {
            }
            gatt = null
        }
    }
}
