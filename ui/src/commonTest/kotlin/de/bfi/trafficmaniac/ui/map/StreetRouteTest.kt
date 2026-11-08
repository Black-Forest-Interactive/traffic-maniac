package de.bfi.trafficmaniac.ui.map

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.GeoPoint
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.Scenery
import kotlin.test.Test
import kotlin.test.assertEquals

class StreetRouteTest {
    /**
     * Allee mit getrennten Richtungsfahrbahnen: Nordfahrbahn (y = 10) nur nach Osten, Südfahrbahn (y = −10) nur nach
     * Westen, an beiden Enden und in der Mitte verbunden. Knoten 0–2 Nord, 3–5 Süd.
     */
    private fun dualCarriageway(): CityMap {
        val nodes = (0..2).map { RoadNode(it, it * 300f, 10f) } + (0..2).map { RoadNode(3 + it, it * 300f, -10f) }
        var id = 0

        fun road(
            from: Int,
            to: Int,
            oneway: Boolean,
        ) = RoadSegment(
            id = id++,
            from = from,
            to = to,
            roadClass = RoadClass.PRIMARY,
            geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
            lanesForward = 2,
            lanesBackward = if (oneway) 0 else 1,
            maxSpeedKmh = 50,
            name = "Allee",
        )
        val segments =
            listOf(
                road(0, 1, oneway = true), // 0: Nord, Ost
                road(1, 2, oneway = true), // 1
                road(5, 4, oneway = true), // 2: Süd, West
                road(4, 3, oneway = true), // 3
                road(0, 3, oneway = false), // 4–6: Querverbindungen
                road(1, 4, oneway = false),
                road(2, 5, oneway = false),
            )
        return CityMap(
            "Test",
            "",
            GeoPoint(0.0, 0.0),
            Bounds(0f, -20f, 600f, 20f),
            RoadNetwork(nodes, segments),
            Scenery(),
        )
    }

    private val route = StreetRoute(dualCarriageway())

    @Test
    fun aDirectedRouteStaysOnTheCarriagewayForItsDirection() {
        // Von der Nordfahrbahn (West) zur Südfahrbahn (Ost): ungerichtet direkt, gerichtet nur nach Osten oben entlang
        assertEquals(listOf(0, 1, 6, 2), route.between(0, 2, directed = true))
    }

    @Test
    fun anUndirectedRouteTakesTheShortestWay() {
        assertEquals(listOf(0, 5, 2), route.between(0, 2))
    }
}
