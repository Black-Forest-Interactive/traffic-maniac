package de.bfi.trafficmaniac.city

import kotlin.math.PI
import kotlin.math.cos

/**
 * Rechnet Längen-/Breitengrade in lokale Meter um [origin] um.
 *
 * Für eine Stadt von wenigen Kilometern reicht die einfache Zylinderprojektion:
 * Der Fehler bleibt im Bereich weniger Zentimeter pro Kilometer.
 */
class LocalProjection(
    val origin: GeoPoint,
) {
    private val metersPerDegreeLat = EARTH_RADIUS_METERS * PI / 180.0
    private val metersPerDegreeLon = metersPerDegreeLat * cos(origin.lat * PI / 180.0)

    fun x(lon: Double): Float = ((lon - origin.lon) * metersPerDegreeLon).toFloat()

    fun y(lat: Double): Float = ((lat - origin.lat) * metersPerDegreeLat).toFloat()

    companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
