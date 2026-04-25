package com.example.aura.meshwire

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

object MeshWireGeo {
    private fun rad(deg: Double): Double = deg * PI / 180.0

    /** Расстояние по поверхности сферы (м), WGS84-аппроксимация через гаверсинус. */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = rad(lat1)
        val p2 = rad(lat2)
        val dp = rad(lat2 - lat1)
        val dl = rad(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) +
            cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun formatDistanceMeters(m: Double): String =
        when {
            m >= 1000.0 -> String.format("%.1f km", m / 1000.0)
            m >= 1.0 -> "%.0f m".format(m)
            else -> "<1 m"
        }

    /** Начальный азимут от (lat1,lon1) к (lat2,lon2), градусы по часовой стрелке от севера [0,360). */
    fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val φ1 = rad(lat1)
        val φ2 = rad(lat2)
        val Δλ = rad(lon2 - lon1)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        val θ = atan2(y, x)
        var deg = Math.toDegrees(θ)
        while (deg < 0) deg += 360.0
        while (deg >= 360.0) deg -= 360.0
        return deg
    }

    /** Короткая роза ветров для подписи стрелки. */
    fun bearingRose16(degrees: Double): String {
        val d = ((degrees % 360.0) + 360.0) % 360.0
        val names = arrayOf("С", "ССВ", "СВ", "ВСВ", "В", "ВЮВ", "ЮВ", "ЮЮВ", "Ю", "ЮЮЗ", "ЮЗ", "ЗЮЗ", "З", "ЗСЗ", "СЗ", "ССЗ")
        val idx = ((d + 11.25) / 22.5).toInt() and 15
        return names[idx]
    }
}
