package de.bfi.trafficmaniac.city

import kotlin.test.Test
import kotlin.test.assertEquals

class StreetStretchesTest {
    /**
     * Hauptstraße 0–1–2–3–4 (Abschnitte 0..3), bei Knoten 2 mündet eine passive Nebenstraße (Abschnitt 4 zu Knoten 5),
     * Knoten 3 hat eine Ampel. Knoten 1 ist nur ein Knick.
     */
    private val network =
        RoadNetwork(
            nodes =
                listOf(
                    RoadNode(0, 0f, 0f),
                    RoadNode(1, 100f, 0f),
                    RoadNode(2, 200f, 0f),
                    RoadNode(3, 300f, 0f, trafficSignals = true),
                    RoadNode(4, 400f, 0f),
                    RoadNode(5, 200f, 100f),
                ),
            segments =
                listOf(
                    segment(0, 0, 1, "Hauptstraße"),
                    segment(1, 1, 2, "Hauptstraße"),
                    segment(2, 2, 3, "Hauptstraße"),
                    segment(3, 3, 4, "Hauptstraße"),
                    segment(4, 2, 5, "Seitengasse", passive = true),
                ),
        )

    private fun segment(
        id: Int,
        from: Int,
        to: Int,
        name: String,
        passive: Boolean = false,
    ): RoadSegment {
        val a = network0Point(from)
        val b = network0Point(to)
        return RoadSegment(
            id = id,
            from = from,
            to = to,
            roadClass = if (passive) RoadClass.RESIDENTIAL else RoadClass.PRIMARY,
            geometry = Polyline(floatArrayOf(a.first, a.second, b.first, b.second)),
            lanesForward = 1,
            lanesBackward = 1,
            maxSpeedKmh = 50,
            name = name,
            passive = passive,
        )
    }

    private fun network0Point(node: Int): Pair<Float, Float> = if (node == 5) 200f to 100f else node * 100f to 0f

    private val stretches = StreetStretches(network)

    @Test
    fun aSideStreetEndsTheStretchEvenIfItIsOnlyDrawn() {
        assertEquals(listOf(0, 1), stretches.stretchOf(0), "Knick bei 1 läuft durch, Einmündung bei 2 beendet")
        assertEquals(listOf(2), stretches.stretchOf(2), "zwischen Einmündung und Ampel")
        assertEquals(listOf(3), stretches.stretchOf(3), "hinter der Ampel")
    }

    @Test
    fun theSameStreetContinuesAcrossAJunction() {
        assertEquals(listOf(2), stretches.sameStreetAcross(1, 2))
        assertEquals(emptyList(), stretches.sameStreetAcross(0, 0))
    }
}
