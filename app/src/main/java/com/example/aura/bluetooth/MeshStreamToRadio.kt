package com.example.aura.bluetooth

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.aura.meshwire.MeshWireFromRadioMeshPacketParser
import com.example.aura.meshwire.MeshWireFromRadioMyNodeNumParser
import com.example.aura.meshwire.MeshWireStreamFrameCodec
import com.example.aura.meshwire.MeshWireWantConfigHandshake
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private const val TAG = "MeshStreamToRadio"

/**
 * Обмен ToRadio/FromRadio по TCP или USB-serial с кадрированием mesh (как в официальном приложении).
 */
object MeshStreamToRadio {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = java.util.concurrent.Executors.newCachedThreadPool()

    fun postMain(r: Runnable) {
        mainHandler.post(r)
    }

    fun fetchNodeIdTcp(
        host: String,
        port: Int,
        timeoutMs: Long = 12_000,
        onResult: (nodeIdHex: String?, error: String?) -> Unit,
    ) {
        ioExecutor.execute {
            val found = AtomicReference<String?>(null)
            var err: String? = null
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), 8_000)
                socket.soTimeout = 350
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                out.write(MeshWireStreamFrameCodec.WAKE_BYTES)
                out.flush()
                val want = MeshWireWantConfigHandshake.encodeToRadioWantConfigId(
                    MeshWireWantConfigHandshake.CONFIG_NONCE,
                )
                out.write(MeshWireStreamFrameCodec.buildFramedPacket(want))
                out.flush()

                val codec = MeshWireStreamFrameCodec { bytes ->
                    val n = MeshWireFromRadioMyNodeNumParser.parseMyNodeNum(bytes) ?: return@MeshWireStreamFrameCodec
                    if (n != 0L) {
                        found.compareAndSet(null, "%08x".format(n).uppercase())
                    }
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (found.get() == null && System.currentTimeMillis() < deadline) {
                    try {
                        val n = inp.read(buf)
                        if (n > 0) codec.processInputBytes(buf, n)
                        else if (n < 0) break
                    } catch (_: SocketTimeoutException) {
                    }
                }
                if (found.get() == null) err = "Не удалось получить Node ID по TCP (таймаут или неверный хост)"
            } catch (e: Exception) {
                Log.w(TAG, "fetchNodeIdTcp", e)
                err = e.message ?: "Ошибка TCP"
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
            val nid = found.get()
            val e = err
            postMain { onResult(nid, if (nid != null) null else e) }
        }
    }

    /**
     * @param baud фиксированная скорость или `null` — перебор [MeshDeviceTransport.USB_BAUD_CANDIDATES] по очереди.
     * @param onResult при успехе [baudUsed] — скорость, с которой получен Node ID.
     */
    fun fetchNodeIdUsb(
        context: Context,
        device: UsbDevice,
        baud: Int?,
        timeoutMsPerBaud: Long = 4_000,
        onResult: (nodeIdHex: String?, error: String?, baudUsed: Int?) -> Unit,
    ) {
        ioExecutor.execute {
            val sequence = baud?.let { listOf(it) } ?: MeshDeviceTransport.USB_BAUD_CANDIDATES
            var lastFailure: String? = null
            for ((idx, b) in sequence.withIndex()) {
                if (idx > 0) {
                    try {
                        Thread.sleep(150)
                    } catch (_: InterruptedException) {
                    }
                }
                val id = try {
                    tryUsbFetchNodeIdOnce(context.applicationContext, device, b, timeoutMsPerBaud)
                } catch (e: Exception) {
                    Log.d(TAG, "fetchNodeIdUsb baud=$b", e)
                    null
                }
                if (id != null) {
                    postMain { onResult(id, null, b) }
                    return@execute
                }
                lastFailure = "Нет ответа на $b bps"
            }
            val summary = if (baud != null) {
                "Не удалось получить Node ID по USB (${baud} bps)"
            } else {
                "Не удалось по USB; перепробованы: ${sequence.joinToString()}"
            }
            postMain { onResult(null, lastFailure ?: summary, null) }
        }
    }

    /** Одна попытка: открыть порт, выставить baud, want_config, читать Until timeout. */
    private fun tryUsbFetchNodeIdOnce(
        appContext: Context,
        device: UsbDevice,
        baud: Int,
        timeoutMs: Long,
    ): String? {
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: throw IOException("Нет UsbManager")
        val conn = usbManager.openDevice(device) ?: throw IOException("openDevice вернул null")
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: throw IOException("Нет CDC/serial драйвера для устройства")
        val port = driver.ports.firstOrNull() ?: throw IOException("Нет serial порта")
        val found = AtomicReference<String?>(null)
        try {
            port.open(conn)
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.write(MeshWireStreamFrameCodec.WAKE_BYTES, 1_500)
            val want = MeshWireWantConfigHandshake.encodeToRadioWantConfigId(
                MeshWireWantConfigHandshake.CONFIG_NONCE,
            )
            port.write(MeshWireStreamFrameCodec.buildFramedPacket(want), 3_000)
            val codec = MeshWireStreamFrameCodec { bytes ->
                val n = MeshWireFromRadioMyNodeNumParser.parseMyNodeNum(bytes)
                    ?: return@MeshWireStreamFrameCodec
                if (n != 0L) {
                    found.compareAndSet(null, "%08x".format(n).uppercase())
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(1024)
            while (found.get() == null && System.currentTimeMillis() < deadline) {
                val n = try {
                    port.read(buf, 400)
                } catch (_: IOException) {
                    0
                }
                if (n > 0) codec.processInputBytes(buf, n)
            }
            return found.get()
        } finally {
            try {
                port.close()
            } catch (_: Exception) {
            }
        }
    }

    fun writeToradioTcp(
        endpoint: MeshDeviceTransport.TcpEndpoint,
        payload: ByteArray,
        drainFromRadio: Boolean,
        onComplete: (ok: Boolean, error: String?, meshSummary: String?) -> Unit,
    ) {
        ioExecutor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 8_000)
                socket.soTimeout = 280
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()
                out.write(MeshWireStreamFrameCodec.WAKE_BYTES)
                out.flush()
                out.write(MeshWireStreamFrameCodec.buildFramedPacket(payload))
                out.flush()
                if (!drainFromRadio) {
                    postMain { onComplete(true, null, null) }
                    return@execute
                }
                val summaries = mutableListOf<String>()
                val codec = MeshWireStreamFrameCodec { bytes ->
                    MeshWireFromRadioMeshPacketParser.summarizeFromRadio(bytes)?.let { summaries.add(it) }
                }
                val deadline = System.currentTimeMillis() + 5_200L
                val buf = ByteArray(2048)
                var reads = 0
                while (reads < 72 && System.currentTimeMillis() < deadline) {
                    try {
                        val n = inp.read(buf)
                        if (n > 0) {
                            reads++
                            codec.processInputBytes(buf, n)
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
                val detail = summaries.distinct().take(12).joinToString("\n").ifBlank { null }
                postMain { onComplete(true, null, detail) }
            } catch (e: Exception) {
                Log.w(TAG, "writeToradioTcp", e)
                postMain { onComplete(false, e.message ?: "TCP ошибка", null) }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun writeToradioUsb(
        context: Context,
        endpoint: MeshDeviceTransport.UsbEndpoint,
        payload: ByteArray,
        drainFromRadio: Boolean,
        onComplete: (ok: Boolean, error: String?, meshSummary: String?) -> Unit,
    ) {
        ioExecutor.execute {
            val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager
            var port: UsbSerialPort? = null
            try {
                if (usbManager == null) throw IOException("Нет UsbManager")
                val device = usbManager.deviceList.values.firstOrNull { it.deviceName == endpoint.deviceName }
                    ?: throw IOException("USB устройство не найдено (переподключите кабель)")
                val conn = usbManager.openDevice(device) ?: throw IOException("openDevice null")
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                    ?: throw IOException("Нет serial драйвера")
                port = driver.ports.firstOrNull() ?: throw IOException("Нет порта")
                port.open(conn)
                port.setParameters(endpoint.baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                port.write(MeshWireStreamFrameCodec.WAKE_BYTES, 1_500)
                port.write(MeshWireStreamFrameCodec.buildFramedPacket(payload), 4_000)
                if (!drainFromRadio) {
                    postMain { onComplete(true, null, null) }
                    return@execute
                }
                val summaries = mutableListOf<String>()
                val codec = MeshWireStreamFrameCodec { bytes ->
                    MeshWireFromRadioMeshPacketParser.summarizeFromRadio(bytes)?.let { summaries.add(it) }
                }
                val deadline = System.currentTimeMillis() + 5_200L
                val buf = ByteArray(1024)
                var reads = 0
                while (reads < 72 && System.currentTimeMillis() < deadline) {
                    val n = try {
                        port.read(buf, min(320, (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(80)))
                    } catch (_: IOException) {
                        0
                    }
                    if (n > 0) {
                        reads++
                        codec.processInputBytes(buf, n)
                    }
                }
                val detail = summaries.distinct().take(12).joinToString("\n").ifBlank { null }
                postMain { onComplete(true, null, detail) }
            } catch (e: Exception) {
                Log.w(TAG, "writeToradioUsb", e)
                postMain { onComplete(false, e.message ?: "USB ошибка", null) }
            } finally {
                try {
                    port?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun writeToradioQueueTcp(
        endpoint: MeshDeviceTransport.TcpEndpoint,
        payloads: List<ByteArray>,
        delayBetweenWritesMs: Long,
        onComplete: (ok: Boolean, error: String?) -> Unit,
    ) {
        if (payloads.isEmpty()) {
            postMain { onComplete(true, null) }
            return
        }
        ioExecutor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 8_000)
                socket.soTimeout = 120
                val out = socket.getOutputStream()
                out.write(MeshWireStreamFrameCodec.WAKE_BYTES)
                out.flush()
                for ((i, p) in payloads.withIndex()) {
                    out.write(MeshWireStreamFrameCodec.buildFramedPacket(p))
                    out.flush()
                    if (i < payloads.lastIndex) Thread.sleep(delayBetweenWritesMs.coerceAtMost(2000))
                }
                postMain { onComplete(true, null) }
            } catch (e: Exception) {
                Log.w(TAG, "writeToradioQueueTcp", e)
                postMain { onComplete(false, e.message ?: "TCP очередь") }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun writeToradioQueueUsb(
        context: Context,
        endpoint: MeshDeviceTransport.UsbEndpoint,
        payloads: List<ByteArray>,
        delayBetweenWritesMs: Long,
        onComplete: (ok: Boolean, error: String?) -> Unit,
    ) {
        if (payloads.isEmpty()) {
            postMain { onComplete(true, null) }
            return
        }
        ioExecutor.execute {
            val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager
            var port: UsbSerialPort? = null
            try {
                if (usbManager == null) throw IOException("Нет UsbManager")
                val device = usbManager.deviceList.values.firstOrNull { it.deviceName == endpoint.deviceName }
                    ?: throw IOException("USB устройство не найдено")
                val conn = usbManager.openDevice(device) ?: throw IOException("openDevice null")
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                    ?: throw IOException("Нет драйвера")
                port = driver.ports.firstOrNull() ?: throw IOException("Нет порта")
                port.open(conn)
                port.setParameters(endpoint.baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                port.write(MeshWireStreamFrameCodec.WAKE_BYTES, 1_500)
                for ((i, p) in payloads.withIndex()) {
                    port.write(MeshWireStreamFrameCodec.buildFramedPacket(p), 5_000)
                    if (i < payloads.lastIndex) {
                        Thread.sleep(delayBetweenWritesMs.coerceAtMost(2000))
                    }
                }
                postMain { onComplete(true, null) }
            } catch (e: Exception) {
                Log.w(TAG, "writeToradioQueueUsb", e)
                postMain { onComplete(false, e.message ?: "USB очередь") }
            } finally {
                try {
                    port?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun drainFromRadioOnlyTcp(
        endpoint: MeshDeviceTransport.TcpEndpoint,
        onFrame: (ByteArray) -> Unit,
        onComplete: (ok: Boolean, error: String?) -> Unit,
    ) {
        ioExecutor.execute {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), 8_000)
                socket.soTimeout = 200
                val inp = socket.getInputStream()
                val out = socket.getOutputStream()
                out.write(MeshWireStreamFrameCodec.WAKE_BYTES)
                out.flush()
                val codec = MeshWireStreamFrameCodec { bytes ->
                    postMain { onFrame(bytes) }
                }
                val deadline = System.currentTimeMillis() + 4_500L
                val buf = ByteArray(2048)
                var reads = 0
                while (reads < 96 && System.currentTimeMillis() < deadline) {
                    try {
                        val n = inp.read(buf)
                        if (n > 0) {
                            reads++
                            codec.processInputBytes(buf, n)
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
                postMain { onComplete(true, null) }
            } catch (e: Exception) {
                Log.w(TAG, "drainFromRadioOnlyTcp", e)
                postMain { onComplete(false, e.message) }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun drainFromRadioOnlyUsb(
        context: Context,
        endpoint: MeshDeviceTransport.UsbEndpoint,
        onFrame: (ByteArray) -> Unit,
        onComplete: (ok: Boolean, error: String?) -> Unit,
    ) {
        ioExecutor.execute {
            val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as? UsbManager
            var port: UsbSerialPort? = null
            try {
                if (usbManager == null) throw IOException("Нет UsbManager")
                val device = usbManager.deviceList.values.firstOrNull { it.deviceName == endpoint.deviceName }
                    ?: throw IOException("USB не найдено")
                val conn = usbManager.openDevice(device) ?: throw IOException("openDevice null")
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                    ?: throw IOException("Нет драйвера")
                port = driver.ports.firstOrNull() ?: throw IOException("Нет порта")
                port.open(conn)
                port.setParameters(endpoint.baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                port.write(MeshWireStreamFrameCodec.WAKE_BYTES, 1_500)

                val codec = MeshWireStreamFrameCodec { bytes ->
                    postMain { onFrame(bytes) }
                }
                val deadline = System.currentTimeMillis() + 4_500L
                val buf = ByteArray(1024)
                var reads = 0
                while (reads < 96 && System.currentTimeMillis() < deadline) {
                    val n = try {
                        port.read(buf, 220)
                    } catch (_: IOException) {
                        0
                    }
                    if (n > 0) {
                        reads++
                        codec.processInputBytes(buf, n)
                    }
                }
                postMain { onComplete(true, null) }
            } catch (e: Exception) {
                Log.w(TAG, "drainFromRadioOnlyUsb", e)
                postMain { onComplete(false, e.message) }
            } finally {
                try {
                    port?.close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
