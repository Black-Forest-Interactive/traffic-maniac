package de.bfi.trafficmaniac.city

import kotlinx.serialization.Serializable

/**
 * Inhalt einer Stadtdatei: das simulierte Straßennetz und die Kulisse drumherum.
 *
 * Alle Koordinaten sind lokale Meter relativ zu [origin] (x nach Osten, y nach Norden).
 */
@Serializable
data class CityMap(
    val name: String,
    val attribution: String,
    val origin: GeoPoint,
    val bounds: Bounds,
    val roads: RoadNetwork,
    val scenery: Scenery,
    val zones: List<Zone> = emptyList(),
    val facilities: List<Facility> = emptyList(),
    val formatVersion: Int = FORMAT_VERSION,
) {
    companion object {
        /** Wird erhöht, sobald sich das Dateiformat inkompatibel ändert. */
        const val FORMAT_VERSION = 3
    }
}

@Serializable
data class GeoPoint(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class Bounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2
    val centerY: Float get() = (minY + maxY) / 2
}

/**
 * Linienzug als flaches Array `x0, y0, x1, y1, …` – spart bei zehntausenden Gebäuden viel Platz.
 * Flächen (Gebäude, Wasser, Grün) sind geschlossene Linienzüge.
 */
@Serializable
class Polyline(
    val coordinates: FloatArray,
) {
    init {
        require(coordinates.size % 2 == 0) { "coordinates must contain x/y pairs" }
    }

    val pointCount: Int get() = coordinates.size / 2

    fun x(index: Int): Float = coordinates[index * 2]

    fun y(index: Int): Float = coordinates[index * 2 + 1]

    override fun equals(other: Any?): Boolean = other is Polyline && coordinates.contentEquals(other.coordinates)

    override fun hashCode(): Int = coordinates.contentHashCode()

    override fun toString(): String = "Polyline(${coordinates.contentToString()})"
}

/** Nur zur Darstellung: alles, was nicht simuliert wird. */
@Serializable
data class Scenery(
    val buildings: List<Polyline> = emptyList(),
    val minorRoads: List<Polyline> = emptyList(),
    val motorways: List<Polyline> = emptyList(),
    val water: List<Polyline> = emptyList(),
    val waterways: List<Polyline> = emptyList(),
    val green: List<Polyline> = emptyList(),
)
