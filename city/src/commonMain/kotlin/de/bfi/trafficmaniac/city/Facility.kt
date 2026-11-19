package de.bfi.trafficmaniac.city

import kotlinx.serialization.Serializable

/** Einrichtungen mit Bedeutung für den Verkehr, angebunden an den nächsten Knoten des Straßennetzes. */
@Serializable
data class Facility(
    val id: Int,
    val kind: FacilityKind,
    val x: Float,
    val y: Float,
    val node: Int,
    /** Ladepunkte einer Ladesäule, Fahrzeuge einer Wache bzw. Stellplätze eines Parkplatzes. */
    val capacity: Int = 1,
    val name: String? = null,
    /** Parkplatz kostet schon in OSM Gebühr (`fee=yes`) bzw. ist ein Parkhaus. */
    val paid: Boolean = false,
)

enum class FacilityKind {
    CHARGING_STATION,
    FIRE_STATION,
    POLICE,
    AMBULANCE_STATION,

    /** Parkhaus oder Parkplatz (`amenity=parking`). */
    PARKING,
}
