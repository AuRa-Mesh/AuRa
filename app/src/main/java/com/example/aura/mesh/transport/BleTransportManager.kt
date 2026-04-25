package com.example.aura.mesh.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Низкоуровневое состояние BLE-транспорта: MTU, режим «малый пакет» (агрессивный опрос),
 * учёт проблемных кадров. Соответствует идее mesh: [BluetoothGatt.requestMtu](512) при connect.
 *
 * Чтение [FromRadio] на стороне телефона **снимает** пакет из FIFO ноды — отдельный BLE-ACK,
 * как у TCP, в протоколе mesh для приложения обычно не требуется; см. [noteFromRadioConsumed].
 */
object BleTransportManager {

    private const val TAG = "BleTransportManager"

    /** Целевой MTU как в официальном клиенте типичном Android mesh-клиенте. */
    const val TARGET_MTU: Int = 512

    /** Порог: ниже — считаем, что фрагментация/короткие PDU, нужен более частый опрос FromRadio. */
    private const val LARGE_MTU_THRESHOLD: Int = 185

    private val mtuLock = Any()
    private var negotiatedMtu: Int = 23
    private var lastMtuStatus: Int = BluetoothGatt.GATT_SUCCESS

    private val corruptFrames = AtomicInteger(0)
    private val lastCorruptAtMs = AtomicLong(0L)

    /**
     * Запрос высокого приоритета (как в типичном mesh-клиенте Android BleRadioInterface):
     * снижает connection interval с ~30 ms до ~7.5 ms → рост пропускной способности в 3–4 раза.
     * Также запрашивает 2M PHY (Bluetooth 5) при поддержке.
     */
    fun requestHighPerformance(gatt: BluetoothGatt) {
        try {
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            Log.d(TAG, "requestConnectionPriority(HIGH)")
        } catch (e: SecurityException) {
            Log.w(TAG, "CONNECTION_PRIORITY_HIGH security: ${e.message}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                gatt.setPreferredPhy(
                    BluetoothDevice.PHY_LE_2M_MASK,
                    BluetoothDevice.PHY_LE_2M_MASK,
                    BluetoothDevice.PHY_OPTION_NO_PREFERRED,
                )
                Log.d(TAG, "setPreferredPhy(2M)")
            } catch (e: SecurityException) {
                Log.w(TAG, "setPreferredPhy security: ${e.message}")
            }
        }
    }

    fun recordMtuNegotiated(mtu: Int, status: Int) {
        val m = mtu.coerceIn(23, 517)
        synchronized(mtuLock) {
            negotiatedMtu = m
            lastMtuStatus = status
        }
        Log.d(
            TAG,
            "MTU negotiated=$m status=$status large=${m >= LARGE_MTU_THRESHOLD} (target=$TARGET_MTU)",
        )
    }

    fun negotiatedMtu(): Int = synchronized(mtuLock) { negotiatedMtu }

    fun lastMtuStatus(): Int = synchronized(mtuLock) { lastMtuStatus }

    fun isMtuNegotiationOk(): Boolean =
        lastMtuStatus() == BluetoothGatt.GATT_SUCCESS

    /** Достаточно большой PDU для типичного MeshPacket без лишней фрагментации на уровне ATT. */
    fun isLargeMtu(): Boolean = negotiatedMtu() >= LARGE_MTU_THRESHOLD

    /**
     * Интервал между последовательными read FromRadio в режиме чата (мс): при малом MTU — чаще.
     */
    fun interReadDelayMsForChatDrain(): Long =
        if (isLargeMtu()) 22L else 14L

    fun firstReadDelayMsForChatDrain(): Long =
        if (isLargeMtu()) 40L else 28L

    /**
     * Протокол mesh: успешное чтение характеристики FromRadio извлекает пакет из очереди ноды.
     * Это и есть подтверждение доставки в телефон с точки зрения буфера ноды (аналог «ACK» для FIFO).
     */
    fun noteFromRadioConsumed() {
        // Точка расширения: метрики / лог при необходимости.
    }

    /** Зафиксировать подозрительный кадр (protobuf не разобрался в полезную нагрузку). */
    fun recordParseFailure(bytes: ByteArray) {
        corruptFrames.incrementAndGet()
        lastCorruptAtMs.set(System.currentTimeMillis())
        Log.w(TAG, "FromRadio parse produced no payloads, len=${bytes.size}")
    }

    fun corruptFrameCount(): Int = corruptFrames.get()

    fun lastCorruptTimestampMs(): Long = lastCorruptAtMs.get()

    /**
     * Повторный немедленный опрос FromRadio не восстанавливает **тот же** байтовый пакет —
     * FIFO уже сдвинут. Используется только чтобы быстрее забрать следующий кадр, если стек BLE
     * отдал повреждённые данные и нода ещё держит очередь.
     */
    fun shouldRequestExtraDrainAfterParseMiss(): Boolean =
        corruptFrames.get() > 0 && !isLargeMtu()

    /**
     * Эвристика «эфир занят»: частые битые кадры + недавняя активность.
     * Перед голосом показываем предупреждение пользователю.
     */
    fun isChannelAirBusyForVoice(): Boolean {
        val age = System.currentTimeMillis() - lastCorruptTimestampMs()
        return corruptFrameCount() >= 4 && age in 0..15_000L
    }
}
