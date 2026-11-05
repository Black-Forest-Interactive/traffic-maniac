package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.RoadClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CityExtractorTest {
    private val box = GeoBox(minLon = 8.0, minLat = 48.0, maxLon = 8.1, maxLat = 48.1)

    /**
     * Zwei Hauptstraßen kreuzen sich an Knoten 3:
     *
     * ```
     *          5
     *          |
     *  1 - 2 - 3 - 4
     *          |
     *          6 (Ampel)   7 liegt außerhalb des Ausschnitts
     * ```
     */
    private fun extract(): CityMap {
        val extractor = CityExtractor(ImportConfig(name = "Test", box = box))
        extractor.node(1, 48.05, 8.01, emptyMap())
        extractor.node(2, 48.05, 8.02, emptyMap())
        extractor.node(3, 48.05, 8.03, emptyMap())
        extractor.node(4, 48.05, 8.04, emptyMap())
        extractor.node(5, 48.06, 8.03, emptyMap())
        extractor.node(6, 48.04, 8.03, mapOf("highway" to "traffic_signals"))
        extractor.node(7, 47.90, 8.03, emptyMap())
        extractor.node(10, 48.07, 8.07, emptyMap())
        extractor.node(11, 48.07, 8.08, emptyMap())
        extractor.node(12, 48.08, 8.08, emptyMap())

        extractor.way(100, longArrayOf(1, 2, 3, 4), mapOf("highway" to "primary"))
        extractor.way(101, longArrayOf(5, 3, 6, 7), mapOf("highway" to "secondary", "oneway" to "-1"))
        extractor.way(102, longArrayOf(10, 11, 12, 10), mapOf("building" to "yes"))
        extractor.way(103, longArrayOf(10, 12), mapOf("highway" to "residential"))
        return extractor.build()
    }

    @Test
    fun splitsRoadsAtJunctionsAndSignals() {
        val roads = extract().roads

        // Knoten 2 ist weder Kreuzung noch Ampel und verschwindet im Abschnitt 1–3
        assertEquals(5, roads.nodes.size)
        assertEquals(4, roads.segments.size)
        val signal = roads.nodes.single { it.trafficSignals }
        assertTrue(signal.y < roads.nodes.first().y)
    }

    @Test
    fun reversesOnewayAgainstWayDirection() {
        val map = extract()
        val secondary = map.roads.segments.filter { it.roadClass == RoadClass.SECONDARY }

        assertEquals(2, secondary.size)
        assertTrue(secondary.all { it.isOneway })
        // oneway=-1: Fahrtrichtung von Süd (Ampel) nach Nord
        val first = secondary.first()
        val from = map.roads.nodes[first.from]
        val to = map.roads.nodes[first.to]
        assertTrue(from.y < to.y)
    }

    @Test
    fun collectsSceneryWithinBox() {
        val scenery = extract().scenery

        assertEquals(1, scenery.buildings.size)
        assertEquals(1, scenery.minorRoads.size)
    }
}
