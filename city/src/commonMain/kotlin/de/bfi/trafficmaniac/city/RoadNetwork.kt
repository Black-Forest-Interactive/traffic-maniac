package de.bfi.trafficmaniac.city

import kotlinx.serialization.Serializable

/**
 * Das simulierte Straßennetz: Knoten sind Kreuzungen und Straßenenden,
 * Abschnitte verbinden zwei Knoten ohne Kreuzung dazwischen.
 */
@Serializable
data class RoadNetwork(
    val nodes: List<RoadNode>,
    val segments: List<RoadSegment>,
)

@Serializable
data class RoadNode(
    val id: Int,
    val x: Float,
    val y: Float,
    val trafficSignals: Boolean = false,
)

/**
 * Straßenabschnitt von [from] nach [to]. [geometry] beginnt am Start- und endet am Zielknoten.
 * Einbahnstraßen haben [lanesBackward] = 0.
 */
@Serializable
data class RoadSegment(
    val id: Int,
    val from: Int,
    val to: Int,
    val roadClass: RoadClass,
    val geometry: Polyline,
    val lanesForward: Int,
    val lanesBackward: Int,
    val maxSpeedKmh: Int,
    val name: String? = null,
    /** Brücke (`bridge=*`): verfällt, bekommt Gewichtslimits, wird gesperrt oder stürzt ein. */
    val bridge: Boolean = false,
    /**
     * Nebenstraße, die normalerweise nicht befahren wird (nur Kulisse). Eine Umleitung kann sie zuschalten –
     * dann zählen [maxWeightTons], [maxWidthMeters] und [maxHeightMeters] für LKW.
     */
    val passive: Boolean = false,
    val maxWeightTons: Float? = null,
    val maxWidthMeters: Float? = null,
    val maxHeightMeters: Float? = null,
) {
    val isOneway: Boolean get() = lanesBackward == 0
}

/** Straßenklassen nach OpenStreetMap (`highway=*`). */
enum class RoadClass(
    val osmValue: String,
) {
    MOTORWAY("motorway"),
    MOTORWAY_LINK("motorway_link"),
    TRUNK("trunk"),
    TRUNK_LINK("trunk_link"),
    PRIMARY("primary"),
    PRIMARY_LINK("primary_link"),
    SECONDARY("secondary"),
    SECONDARY_LINK("secondary_link"),
    TERTIARY("tertiary"),
    TERTIARY_LINK("tertiary_link"),
    UNCLASSIFIED("unclassified"),
    RESIDENTIAL("residential"),
    LIVING_STREET("living_street"),
    ;

    companion object {
        private val byOsmValue = entries.associateBy { it.osmValue }

        fun fromOsm(value: String): RoadClass? = byOsmValue[value]
    }
}
