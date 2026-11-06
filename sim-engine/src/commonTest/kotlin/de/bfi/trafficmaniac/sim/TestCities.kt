package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.GeoPoint
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.Scenery
import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind

/**
 * Kreuzung mit Ampel in der Mitte (Knoten 0) und vier 500 m langen Armen:
 * Westen/Osten Hauptstraße (primary), Norden/Süden Nebenstraße (secondary).
 * Wohngebiete im Westen und Osten, Gewerbe im Norden und Süden.
 */
fun crossroads(signals: Boolean = true): CityMap {
    val nodes =
        listOf(
            RoadNode(0, 0f, 0f, trafficSignals = signals),
            RoadNode(1, -500f, 0f),
            RoadNode(2, 500f, 0f),
            RoadNode(3, 0f, 500f),
            RoadNode(4, 0f, -500f),
        )

    fun arm(
        id: Int,
        outer: Int,
        roadClass: RoadClass,
    ) = RoadSegment(
        id = id,
        from = outer,
        to = 0,
        roadClass = roadClass,
        geometry = Polyline(floatArrayOf(nodes[outer].x, nodes[outer].y, 0f, 0f)),
        lanesForward = 1,
        lanesBackward = 1,
        maxSpeedKmh = 50,
    )
    return CityMap(
        name = "Testkreuzung",
        attribution = "",
        origin = GeoPoint(0.0, 0.0),
        bounds = Bounds(-500f, -500f, 500f, 500f),
        roads =
            RoadNetwork(
                nodes = nodes,
                segments =
                    listOf(
                        arm(0, 1, RoadClass.PRIMARY),
                        arm(1, 2, RoadClass.PRIMARY),
                        arm(2, 3, RoadClass.SECONDARY),
                        arm(3, 4, RoadClass.SECONDARY),
                    ),
            ),
        scenery = Scenery(),
        zones =
            listOf(
                Zone(0, ZoneKind.RESIDENTIAL, -500f, 0f, area = 10_000f, node = 1),
                Zone(1, ZoneKind.RESIDENTIAL, 500f, 0f, area = 10_000f, node = 2),
                Zone(2, ZoneKind.COMMERCIAL, 0f, 500f, area = 10_000f, node = 3),
                Zone(3, ZoneKind.COMMERCIAL, 0f, -500f, area = 10_000f, node = 4),
            ),
    )
}
