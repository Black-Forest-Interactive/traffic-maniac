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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeedSignPlacementTest {
    /** Ein gerader Abschnitt von [length] Metern entlang der x-Achse. */
    private fun picker(length: Float): MapPicker {
        val nodes = listOf(RoadNode(0, 0f, 0f), RoadNode(1, length, 0f))
        val segment =
            RoadSegment(
                id = 0,
                from = 0,
                to = 1,
                roadClass = RoadClass.PRIMARY,
                geometry = Polyline(floatArrayOf(0f, 0f, length, 0f)),
                lanesForward = 1,
                lanesBackward = 1,
                maxSpeedKmh = 50,
            )
        return MapPicker(
            CityMap(
                name = "Gerade",
                attribution = "",
                origin = GeoPoint(0.0, 0.0),
                bounds = Bounds(0f, -10f, length, 10f),
                roads = RoadNetwork(nodes, listOf(segment)),
                scenery = Scenery(),
                zones = emptyList(),
            ),
        )
    }

    @Test
    fun signsMarkStartAndEndAndLeaveTheMiddleToTheCamera() {
        val scale = 2f
        val radius = signRadius(scale)
        val signs = speedSigns(mapOf(0 to 30), emptyMap(), picker(1_000f), scale)
        val xs = signs.map { it.x }.sorted()
        assertTrue(xs.first() < 200f, "Schild am Anfang der Strecke: ${xs.first()}")
        assertTrue(xs.last() > 800f, "Schild am Ende der Strecke: ${xs.last()}")
        assertTrue(signs.size >= 3, "Lange Strecke: Wiederholungsschilder (${signs.size})")
        assertTrue(signs.none { abs(it.x - 500f) < radius * 2 }, "Die Mitte bleibt für den Blitzer frei")
        assertTrue(signs.all { it.label == "30" })
    }

    @Test
    fun aShortStretchGetsASingleSignOffTheMiddle() {
        val scale = 1f
        val signs = speedSigns(mapOf(0 to 20), emptyMap(), picker(60f), scale)
        assertEquals(1, signs.size)
        assertTrue(abs(signs.single().x - 30f) > signRadius(scale), "Nicht auf dem Blitzer in der Mitte")
    }

    @Test
    fun nightSignStandsBesideTheDaySign() {
        val scale = 2f
        val radius = signRadius(scale)
        val signs = speedSigns(mapOf(0 to 30), mapOf(0 to 20), picker(1_000f), scale)
        val day = signs.filter { !it.night }
        val night = signs.filter { it.night }
        assertEquals(day.size, night.size)
        for ((d, n) in day.sortedBy { it.x }.zip(night.sortedBy { it.x })) {
            assertTrue(hypot(d.x - n.x, d.y - n.y) >= radius * 2, "Schilder überlappen nicht")
            assertTrue(abs(d.x - n.x) < 1f, "Quer zur Straße versetzt, nicht längs")
        }
    }
}
