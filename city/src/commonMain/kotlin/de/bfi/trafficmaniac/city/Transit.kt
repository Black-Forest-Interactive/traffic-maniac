package de.bfi.trafficmaniac.city

import kotlinx.serialization.Serializable

/**
 * Bushaltestelle aus OpenStreetMap, angebunden an den nächsten befahrenen Abschnitt: Der Bus hält auf [segment]
 * [offset] Meter hinter dessen Anfangsknoten (`from`).
 */
@Serializable
data class TransitStop(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val segment: Int,
    val offset: Float,
)

/**
 * Eine Buslinie in einer Richtung (OSM-Relation `route=bus`): Liniennummer ([ref]), Name und die Haltestellen in
 * Fahrtreihenfolge. Hin- und Rückrichtung sind zwei Linien mit derselben Nummer.
 */
@Serializable
data class TransitLine(
    val id: Int,
    val ref: String,
    val name: String,
    val stops: List<Int>,
    /** Linienfarbe aus OSM (`colour`) als ARGB, sonst `null`. */
    val color: Int? = null,
)
