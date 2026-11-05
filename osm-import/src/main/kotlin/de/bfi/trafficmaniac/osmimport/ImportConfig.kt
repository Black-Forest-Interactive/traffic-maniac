package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.GeoPoint
import de.bfi.trafficmaniac.city.RoadClass

/** Ausschnitt in Längen-/Breitengraden. */
data class GeoBox(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
) {
    init {
        require(minLon < maxLon && minLat < maxLat) { "Invalid bounding box: $this" }
    }

    val center: GeoPoint get() = GeoPoint(lat = (minLat + maxLat) / 2, lon = (minLon + maxLon) / 2)

    fun contains(
        lat: Double,
        lon: Double,
    ): Boolean = lat in minLat..maxLat && lon in minLon..maxLon

    companion object {
        /** Stadtgebiet Pforzheim mit etwas Rand. */
        private const val BOX_PARTS = 4

        val PFORZHEIM = GeoBox(minLon = 8.60, minLat = 48.83, maxLon = 8.80, maxLat = 48.94)

        fun parse(text: String): GeoBox {
            val parts = text.split(',').map { it.trim().toDouble() }
            require(parts.size == BOX_PARTS) { "Expected minLon,minLat,maxLon,maxLat but got '$text'" }
            return GeoBox(minLon = parts[0], minLat = parts[1], maxLon = parts[2], maxLat = parts.last())
        }
    }
}

data class ImportConfig(
    val name: String = "Pforzheim",
    val box: GeoBox = GeoBox.PFORZHEIM,
    /** Straßenklassen, die simuliert werden. Alle anderen Straßen landen höchstens in der Kulisse. */
    val roadClasses: Set<RoadClass> = DEFAULT_ROAD_CLASSES,
) {
    companion object {
        val DEFAULT_ROAD_CLASSES =
            setOf(
                RoadClass.TRUNK,
                RoadClass.TRUNK_LINK,
                RoadClass.PRIMARY,
                RoadClass.PRIMARY_LINK,
                RoadClass.SECONDARY,
                RoadClass.SECONDARY_LINK,
            )
    }
}
