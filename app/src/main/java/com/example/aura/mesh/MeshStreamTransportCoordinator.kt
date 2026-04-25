package com.example.aura.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.aura.R
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.NodeGattConnection
import com.example.aura.meshwire.MeshWireStreamFrameCodec
import com.example.aura.mesh.repository.MeshIncomingChatRepository
import com.example.aura.notifications.MeshNotificationDispatcher
import com.example.aura.security.NodeAuthStore
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val TAG = "MeshStreamTransport"

private const val TCP_READ_TIMEOUT_MS = 35_000
private const val TCP_KEEPALIVE_IDLE_MS = 50_000L
private const val TCP_RECONNECT_DELAY_MS = 3_000L
private const val USB_READ_TIMEOUT_MS = 30_000
private const val USB_RECONNECT_DELAY_MS = 2_000L

/**
 * Долгоживущие TCP и USB-serial читатели FromRadio с кадрированием [MeshWireStreamFrameCodec].
 * Приоритет относительно BLE: при старте TCP/USB вызывается [NodeGattConnection.disconnect].
 */
object MeshStreamTransportCoordinator {

    private var tcpThread: Thread? = null
    private val tcpStop = AtomicBoolean(false)

    private var usbThread: Thread? = null
    private val usbStop = AtomicBoolean(false)

    private var usbAttachReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var wakePingPartial: PowerManager.WakeLock? = null

    fun start(context: Context) {
        val app = context.applicationContext
        val auth = NodeAuthStore.load(app) ?: return
        val raw = auth.deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val norm = MeshNodeSyncMemoryStore.normalizeKey(raw)
        stop(app)
        when {
            MeshDeviceTransport.isUsbAddress(norm) -> {
                val ep = MeshDeviceTransport.parseUsb(norm) ?: return
                NodeGattConnection.disconnect()
                registerUsbAttach(app, norm, ep)
                startUsbLoop(app, ep)
            }
            MeshDeviceTransport.isTcpAddress(norm) -> {
                NodeGattConnection.disconnect()
                acquireWifiLock(app)
                registerNetworkLostInterrupt(app)
                startTcpLoop(app, MeshDeviceTransport.parseTcp(norm) ?: return)
            }
            else -> { /* BLE — поток не используется */ }
        }
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        tcpStop.set(true)
        tcpThread?.interrupt()
        tcpThread = null
        usbStop.set(true)
        usbThread?.interrupt()
        usbThread = null
        unregisterUsbAttach(app)
        unregisterNetworkLostCallback(app)
        releaseWifiLock()
        releaseWakePingLock()
        MeshStreamTransportState.reset()
    }

    private fun acquireWifiLock(ctx: Context) {
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            if (wm == null) return
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(mode, "Aura:MeshTcp").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock", e)
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (_: RuntimeException) {
        }
        wifiLock = null
    }

    private fun acquireWakePingLock(ctx: Context) {
        if (wakePingPartial != null) return
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakePingPartial = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aura:TcpKeepAlive").apply {
                setReferenceCounted(false)
                acquire(120_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock keepalive", e)
        }
    }

    private fun releaseWakePingLock() {
        try {
            wakePingPartial?.let { if (it.isHeld) it.release() }
        } catch (_: RuntimeException) {
        }
        wakePingPartial = null
    }

    /** При потере сети прерываем блокирующий read — цикл TCP сам переподключится. */
    private fun registerNetworkLostInterrupt(ctx: Context) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        unregisterNetworkLostCallback(ctx)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                Log.d(TAG, "network lost — interrupt TCP read")
                MeshStreamTransportState.set(
                    MeshStreamTransportPhase.DISCONNECTED,
                    ctx.getString(R.string.mesh_stream_tcp_network_lost),
                )
                tcpThread?.interrupt()
            }
        }
        networkCallback = cb
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(cb)
            } else {
                @Suppress("DEPRECATION")
                cm.registerNetworkCallback(
                    android.net.NetworkRequest.Builder().build(),
                    cb,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "registerDefaultNetworkCallback", e)
        }
    }

    private fun unregisterNetworkLostCallback(ctx: Context) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
    }

    private fun registerUsbAttach(ctx: Context, normalizedKey: String, ep: MeshDeviceTransport.UsbEndpoint) {
        unregisterUsbAttach(ctx)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
                val dev: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (dev?.deviceName == ep.deviceName) {
                    Log.d(TAG, "USB attached ${ep.deviceName} — перезапуск чтения")
                    usbStop.set(true)
                    usbThread?.interrupt()
                    usbThread = null
                    usbStop.set(false)
                    startUsbLoop(context.applicationContext, ep)
                }
            }
        }
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(receiver, filter)
            }
            usbAttachReceiver = receiver
        } catch (e: Exception) {
            Log.w(TAG, "registerReceiver USB", e)
        }
    }

    private fun unregisterUsbAttach(ctx: Context) {
        usbAttachReceiver?.let {
            try {
                ctx.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        usbAttachReceiver = null
    }

    private fun startTcpLoop(ctx: Context, ep: MeshDeviceTransport.TcpEndpoint) {
        tcpStop.set(false)
        val deviceMacNorm = MeshNodeSyncMemoryStore.normalizeKey(
            MeshDeviceTransport.formatTcp(ep.host, ep.port),
        )
        tcpThread = thread(name = "AuraMeshTcp", priority = Thread.NORM_PRIORITY - 1) {
            while (!tcpStop.get()) {
                MeshStreamTransportState.set(
                    MeshStreamTransportPhase.CONNECTING,
                    ctx.getString(R.string.mesh_stream_tcp_connecting, ep.host, ep.port),
                )
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.connect(InetSocketAddress(ep.host, ep.port), 20_000)
                    val inp = socket.getInputStream()
                    val out = socket.getOutputStream()
                    out.write(MeshWireStreamFrameCodec.WAKE_BYTES)
                    out.flush()
                    MeshStreamTransportState.set(
                        MeshStreamTransportPhase.READY,
                        ctx.getString(R.string.mesh_stream_tcp_ready, ep.host),
                    )
                    val codec = MeshWireStreamFrameCodec { bytes ->
                        MeshIncomingChatRepository.dispatchStreamFrame(
                            deviceMacNorm,
                            bytes,
                            MeshNotificationDispatcher.TransportLabel.WIFI,
                        )
                    }
                    val buf = ByteArray(4096)
                    var lastIo = System.currentTimeMillis()
                    while (!tcpStop.get()) {
                        socket.soTimeout = TCP_READ_TIMEOUT_MS
                        try {
                            val n = inp.read(buf)
                            when {
                                n > 0 -> {
                                    codec.processInputBytes(buf, n)
                                    lastIo = System.currentTimeMillis()
                                }
                                n < 0 -> break
                            }
                        } catch (_: SocketTimeoutException) {
                            val now = System.currentTimeMillis()
                            if (now - lastIo >= TCP_KEEPALIVE_IDLE_MS) {
                                acquireWakePingLock(ctx)
                                try {
                                    out.write(MeshWireStreamFrameCodec.WAKE_BYTES)
                                    out.flush()
                                    lastIo = now
                                } catch (e: Exception) {
                                    Log.d(TAG, "TCP keepalive write failed", e)
                                    break
                                } finally {
                                    releaseWakePingLock()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!tcpStop.get()) {
                        Log.w(TAG, "TCP loop error", e)
                        MeshStreamTransportState.set(
                            MeshStreamTransportPhase.DISCONNECTED,
                            ctx.getString(R.string.mesh_stream_tcp_reconnecting),
                        )
                        try {
                            Thread.sleep(TCP_RECONNECT_DELAY_MS)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                } finally {
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun startUsbLoop(ctx: Context, ep: MeshDeviceTransport.UsbEndpoint) {
        usbStop.set(false)
        val deviceMacNorm = MeshNodeSyncMemoryStore.normalizeKey(
            MeshDeviceTransport.formatUsb(ep.deviceName, ep.baud),
        )
        usbThread = thread(name = "AuraMeshUsb", priority = Thread.NORM_PRIORITY - 1) {
            while (!usbStop.get()) {
                val usbManager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
                if (usbManager == null) {
                    Thread.sleep(USB_RECONNECT_DELAY_MS)
                    continue
                }
                val device = usbManager.deviceList.values.firstOrNull { it.deviceName == ep.deviceName }
                if (device == null) {
                    MeshStreamTransportState.set(
                        MeshStreamTransportPhase.DISCONNECTED,
                        ctx.getString(R.string.mesh_stream_usb_waiting),
                    )
                    try {
                        Thread.sleep(USB_RECONNECT_DELAY_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                MeshStreamTransportState.set(
                    MeshStreamTransportPhase.CONNECTING,
                    ctx.getString(R.string.mesh_stream_usb_connecting),
                )
                var port: UsbSerialPort? = null
                try {
                    val conn = usbManager.openDevice(device) ?: throw IOException("openDevice null")
                    val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                        ?: throw IOException("no serial driver")
                    port = driver.ports.firstOrNull() ?: throw IOException("no port")
                    port.open(conn)
                    port.setParameters(ep.baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    port.write(MeshWireStreamFrameCodec.WAKE_BYTES, 2_000)
                    MeshStreamTransportState.set(
                        MeshStreamTransportPhase.READY,
                        ctx.getString(R.string.mesh_stream_usb_ready),
                    )
                    val codec = MeshWireStreamFrameCodec { bytes ->
                        MeshIncomingChatRepository.dispatchStreamFrame(
                            deviceMacNorm,
                            bytes,
                            MeshNotificationDispatcher.TransportLabel.USB,
                        )
                    }
                    val buf = ByteArray(4096)
                    while (!usbStop.get()) {
                        val n = try {
                            port.read(buf, USB_READ_TIMEOUT_MS)
                        } catch (e: IOException) {
                            Log.d(TAG, "USB read", e)
                            break
                        }
                        if (n > 0) codec.processInputBytes(buf, n)
                    }
                } catch (e: Exception) {
                    if (!usbStop.get()) {
                        Log.w(TAG, "USB loop", e)
                        MeshStreamTransportState.set(
                            MeshStreamTransportPhase.DISCONNECTED,
                            ctx.getString(R.string.mesh_stream_usb_reconnecting),
                        )
                        try {
                            Thread.sleep(USB_RECONNECT_DELAY_MS)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                } finally {
                    try {
                        port?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
