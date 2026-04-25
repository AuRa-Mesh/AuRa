package com.example.aura.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.aura.meshwire.MeshWireLoRaToRadioEncoder
import com.example.aura.meshwire.MeshWirePositionPayloadEncoder

private const val TAG = "MeshPhoneLoc"
private const val PREFS_LAST_CENTER = "aura_last_user_map_center"
private const val KEY_LAT = "lat"
private const val KEY_LON = "lon"

/**
 * Отправка [Position] на ноду через TORADIO (как типичный mesh-клиент Android при включённой отдаче GPS в сеть).
 */
object MeshPhoneLocationToMeshSender {

    fun clearPersistedLastUserLatLng(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_LAST_CENTER, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAT)
            .remove(KEY_LON)
            .apply()
    }

    /** Последняя известная точка (для экрана фиксированной позиции, как в типичном mesh-клиенте). */
    fun lastKnownLocationOrNull(context: Context): Location? {
        if (MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext)) return null
        if (!hasLocationPermission(context)) return null
        val app = context.applicationContext
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        if (loc != null) persistLastUserLatLng(app, loc.latitude, loc.longitude)
        return loc
    }

    /**
     * Последние сохранённые координаты пользователя (после любого успешного определения места).
     * Используется как запасной центр карты, если сейчас нет ни GPS, ни нод.
     */
    fun persistedLastUserLatLngOrNull(context: Context): Pair<Double, Double>? {
        if (MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext)) return null
        val p = context.applicationContext.getSharedPreferences(PREFS_LAST_CENTER, Context.MODE_PRIVATE)
        if (!p.contains(KEY_LAT) || !p.contains(KEY_LON)) return null
        val lat = p.getFloat(KEY_LAT, 0f).toDouble()
        val lon = p.getFloat(KEY_LON, 0f).toDouble()
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    private fun persistLastUserLatLng(app: Context, lat: Double, lon: Double) {
        if (!lat.isFinite() || !lon.isFinite()) return
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        app.getSharedPreferences(PREFS_LAST_CENTER, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .apply()
    }

    /**
     * Достаточно точного или приблизительного местоположения (как в диалоге Android / mesh).
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Берёт последнее известное положение и шлёт в primary channel (index 0).
     */
    fun sendLastKnownLocationIfPossible(
        context: Context,
        deviceAddress: String,
        destinationNodeNum: UInt,
        onComplete: (ok: Boolean, detail: String?) -> Unit,
    ) {
        if (MeshLocationPreferences.isHideCoordinatesTransmission(context.applicationContext)) {
            mainHandlerPost { onComplete(false, "Передача координат отключена в профиле") }
            return
        }
        if (!hasLocationPermission(context)) {
            mainHandlerPost { onComplete(false, "Нет разрешения на геолокацию") }
            return
        }
        val app = context.applicationContext
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            mainHandlerPost { onComplete(false, "LocationManager недоступен") }
            return
        }
        val loc: Location? = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            Log.w(TAG, "getLastKnownLocation", e)
            null
        }
        if (loc == null) {
            mainHandlerPost { onComplete(false, "Нет последней точки GPS — подождите фикса или откройте карту") }
            return
        }
        persistLastUserLatLng(app, loc.latitude, loc.longitude)
        val alt = if (loc.hasAltitude()) loc.altitude.toInt() else null
        val timeSec = (loc.time / 1000L).toUInt().takeIf { it != 0U }
            ?: (System.currentTimeMillis() / 1000L).toUInt()
        val payload = MeshWirePositionPayloadEncoder.encodePosition(
            latitudeDeg = loc.latitude,
            longitudeDeg = loc.longitude,
            altitudeMeters = alt,
            timeEpochSec = timeSec,
        )
        val toRadio = MeshWireLoRaToRadioEncoder.encodePhonePositionToRadio(
            positionPayload = payload,
            channelIndex = 0U,
        )
        MeshGattToRadioWriter(app).writeToradioQueue(
            deviceAddress = deviceAddress,
            payloads = listOf(toRadio),
            delayBetweenWritesMs = 220L,
        ) { ok, err ->
            onComplete(ok, err)
        }
    }

    private fun mainHandlerPost(block: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(block)
    }
}
