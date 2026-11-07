package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.Facility
import de.bfi.trafficmaniac.city.FacilityKind
import de.bfi.trafficmaniac.city.RoadNetwork

/** Rohdaten einer Einrichtung (Knoten oder Fläche) vor dem Anbinden ans Netz. */
internal class RawFacility(
    val kind: FacilityKind,
    val x: Float,
    val y: Float,
    val tags: Map<String, String>,
    /** Fläche in Quadratmetern (0 bei Knoten) – Schätzung der Stellplätze ohne `capacity`. */
    val area: Float = 0f,
)

internal fun facilityKindOf(tags: Map<String, String>): FacilityKind? =
    when {
        tags["amenity"] == "charging_station" -> FacilityKind.CHARGING_STATION
        tags["amenity"] == "fire_station" -> FacilityKind.FIRE_STATION
        tags["amenity"] == "police" -> FacilityKind.POLICE
        tags["emergency"] == "ambulance_station" -> FacilityKind.AMBULANCE_STATION
        isPublicParking(tags) -> FacilityKind.PARKING
        else -> null
    }

/** Öffentliche Parkplätze und Parkhäuser – keine Privat- und Kundenparkplätze, kein Parken am Straßenrand. */
private fun isPublicParking(tags: Map<String, String>): Boolean =
    tags["amenity"] == "parking" &&
        tags["access"] !in setOf("private", "no", "customers", "permissive") &&
        tags["parking"] != "street_side"

/** Parkhäuser kosten immer, Parkplätze nur mit `fee=yes`. */
private fun isPaidParking(tags: Map<String, String>): Boolean =
    tags["parking"] in setOf("multi-storey", "underground") || tags["fee"] == "yes"

/**
 * Bindet Einrichtungen an den nächsten Knoten des befahrenen Netzes an. Ladepunkte aus `capacity`, sonst zwei;
 * Stellplätze aus `capacity` oder der Fläche. Kleine Parkplätze (unter [MIN_PARKING_SPACES]) fallen weg.
 */
internal fun buildFacilities(
    network: RoadNetwork,
    raw: List<RawFacility>,
): List<Facility> {
    val nodes = activeNodes(network)
    if (nodes.isEmpty()) return emptyList()
    val result = ArrayList<Facility>()
    for (facility in raw) {
        val parking = facility.kind == FacilityKind.PARKING
        val tagged = facility.tags["capacity"]?.toIntOrNull()
        val capacity =
            if (parking) {
                (tagged ?: (facility.area / SQUARE_METERS_PER_SPACE).toInt()).coerceAtMost(MAX_PARKING_SPACES)
            } else {
                tagged?.coerceIn(1, MAX_CAPACITY) ?: DEFAULT_CAPACITY
            }
        if (parking && capacity < MIN_PARKING_SPACES) continue
        result +=
            Facility(
                result.size,
                facility.kind,
                facility.x,
                facility.y,
                nearestNode(nodes, facility.x, facility.y),
                capacity,
                facility.tags["name"],
                paid = parking && isPaidParking(facility.tags),
            )
    }
    return result
}

private const val DEFAULT_CAPACITY = 2
private const val MAX_CAPACITY = 20
private const val SQUARE_METERS_PER_SPACE = 25f
private const val MIN_PARKING_SPACES = 20
private const val MAX_PARKING_SPACES = 2_000
