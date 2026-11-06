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
)

internal fun facilityKindOf(tags: Map<String, String>): FacilityKind? =
    when {
        tags["amenity"] == "charging_station" -> FacilityKind.CHARGING_STATION
        tags["amenity"] == "fire_station" -> FacilityKind.FIRE_STATION
        tags["amenity"] == "police" -> FacilityKind.POLICE
        tags["emergency"] == "ambulance_station" -> FacilityKind.AMBULANCE_STATION
        else -> null
    }

/** Bindet Einrichtungen an den nächsten Knoten an; Ladepunkte aus `capacity`, sonst zwei. */
internal fun buildFacilities(
    network: RoadNetwork,
    raw: List<RawFacility>,
): List<Facility> {
    if (network.nodes.isEmpty()) return emptyList()
    return raw.mapIndexed { i, facility ->
        val capacity = facility.tags["capacity"]?.toIntOrNull()?.coerceIn(1, MAX_CAPACITY) ?: DEFAULT_CAPACITY
        Facility(
            i,
            facility.kind,
            facility.x,
            facility.y,
            nearestNode(network, facility.x, facility.y),
            capacity,
            facility.tags["name"],
        )
    }
}

private const val DEFAULT_CAPACITY = 2
private const val MAX_CAPACITY = 20
