package com.example.aura.bluetooth

import android.content.Context

/**
 * Как «Provide location to mesh» в типичном mesh-клиенте Android: сохраняет флаг отправки координат телефона в эфир.
 */
object MeshLocationPreferences {
    private const val PREFS = "aura_mesh_location_prefs"
    private const val KEY_PROVIDE = "provide_location_to_mesh"
    private const val KEY_HIDE_COORDINATES = "hide_coordinates_transmission"

    fun isProvideLocationToMesh(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROVIDE, false)

    fun setProvideLocationToMesh(context: Context, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROVIDE, value)
            .apply()
    }

    /** Вкл.: не передаём координаты ни в mesh, ни в чат, ни в админ‑конфиг позиции и т.д. */
    fun isHideCoordinatesTransmission(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_COORDINATES, false)

    fun setHideCoordinatesTransmission(context: Context, value: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_COORDINATES, value)
            .apply()
        if (value) {
            setProvideLocationToMesh(app, false)
            MeshPhoneLocationToMeshSender.clearPersistedLastUserLatLng(app)
        }
    }
}
