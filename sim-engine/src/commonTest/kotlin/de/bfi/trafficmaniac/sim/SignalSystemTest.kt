package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SignalSystemTest {
    private val graph = RoadGraph(crossroads().roads)
    private val signals = SignalSystem(graph)

    private fun approachFrom(node: Int) = graph.inLinks[0].single { graph.linkFrom[it] == node }

    @Test
    fun controlsAllFourApproaches() {
        assertEquals(4, signals.controlledLinks.size)
    }

    @Test
    fun crossingDirectionsAreNeverGreenTogether() {
        val west = approachFrom(1)
        val east = approachFrom(2)
        val north = approachFrom(3)
        var time = 0.0
        while (time < SignalSystem.CYCLE_SECONDS * 2) {
            val westState = signals.state(west, time)
            assertEquals(westState, signals.state(east, time), "Gegenrichtungen haben dieselbe Phase")
            if (westState != SignalState.RED) assertEquals(SignalState.RED, signals.state(north, time))
            time += 0.5
        }
    }

    @Test
    fun everyApproachGetsGreen() {
        val states = (0 until SignalSystem.CYCLE_SECONDS.toInt()).map { signals.state(approachFrom(3), it.toDouble()) }

        assertTrue(SignalState.GREEN in states)
        assertNotEquals(SignalState.NONE, states.first())
    }

    /**
     * Kreuzung mit Ampeln nur an zwei von drei Kreuzungsknoten (wie oft in OSM):
     * Westen A (Ampel), Mitte B (keine Ampel), Norden C (Ampel).
     * Wer von Osten über B nach A fährt, ist schon in der Kreuzung – dort darf keine Ampel stehen.
     */
    private fun splitJunction(): RoadGraph {
        val nodes =
            listOf(
                RoadNode(0, -300f, 0f),
                RoadNode(1, -10f, 0f, trafficSignals = true),
                RoadNode(2, 10f, 0f),
                RoadNode(3, 300f, 0f),
                RoadNode(4, 10f, 15f, trafficSignals = true),
                RoadNode(5, 10f, 300f),
                RoadNode(6, 10f, -300f),
            )
        val pairs = listOf(0 to 1, 1 to 2, 2 to 3, 5 to 4, 4 to 2, 2 to 6)
        val segments =
            pairs.mapIndexed { id, (from, to) ->
                RoadSegment(
                    id = id,
                    from = from,
                    to = to,
                    roadClass = RoadClass.PRIMARY,
                    geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
                    lanesForward = 1,
                    lanesBackward = 1,
                    maxSpeedKmh = 50,
                )
            }
        return RoadGraph(RoadNetwork(nodes, segments))
    }

    @Test
    fun noRedLightInsideAJunctionWithoutSignalAtTheCrossingNode() {
        val graph = splitJunction()
        val signals = SignalSystem(graph)

        fun link(
            from: Int,
            to: Int,
        ) = (0 until graph.linkCount).single { graph.linkFrom[it] == from && graph.linkTo[it] == to }

        // Innerhalb der Kreuzung: frei
        for ((from, to) in listOf(2 to 1, 1 to 2, 2 to 4, 4 to 2)) {
            assertFalse(signals.isControlled(link(from, to)), "Keine Ampel auf $from → $to innerhalb der Kreuzung")
        }
        // Alle vier Zufahrten von außen: geregelt, Gegenrichtungen gemeinsam, Querrichtung im Wechsel
        val west = link(0, 1)
        val east = link(3, 2)
        val north = link(5, 4)
        val south = link(6, 2)
        assertEquals(setOf(west, east, north, south), signals.controlledLinks.toSet())
        var time = 0.0
        while (time < SignalSystem.CYCLE_SECONDS) {
            assertEquals(signals.state(west, time), signals.state(east, time))
            assertEquals(signals.state(north, time), signals.state(south, time))
            if (signals.state(west, time) != SignalState.RED) assertEquals(SignalState.RED, signals.state(north, time))
            time += 1.0
        }
    }

    @Test
    fun redYellowComesOneSecondBeforeGreenWhileCrossTrafficIsRed() {
        val west = approachFrom(1)
        val north = approachFrom(3)
        var time = 0.0
        var redYellowSeconds = 0.0
        var previous = signals.state(west, 0.0)
        while (time < SignalSystem.CYCLE_SECONDS) {
            val state = signals.state(west, time)
            if (state == SignalState.RED_YELLOW) {
                redYellowSeconds += STEP
                assertEquals(SignalState.RED, signals.state(north, time), "Querrichtung hat Rot")
            }
            if (state == SignalState.GREEN && previous != SignalState.GREEN) {
                assertEquals(SignalState.RED_YELLOW, previous, "Vor Grün kommt Rot-Gelb")
            }
            previous = state
            time += STEP
        }
        assertEquals(SignalSystem.RED_YELLOW_SECONDS, redYellowSeconds, 0.2)
    }

    private companion object {
        const val STEP = 0.1
    }

    /**
     * OSM-Muster großer Kreuzungen: Der Kreuzungsknoten hat keine Ampel, dafür steht an jeder Zufahrt 30 m davor ein
     * Ampelknoten (Haltelinie). Das ist eine Anlage mit vier Ampeln – nicht zwei Anlagen mit je zwei Knoten, die auch
     * den abfließenden Verkehr anhalten.
     */
    @Test
    fun stopLineSignalsBeforeAJunctionFormOnePlantForIncomingTrafficOnly() {
        // 0 Mitte; 1–4 Haltelinien (Westen, Osten, Norden, Süden); 5–8 Enden der Arme
        val stopLines = listOf(-30f to 0f, 30f to 0f, 0f to 30f, 0f to -30f)
        val nodes =
            listOf(RoadNode(0, 0f, 0f)) +
                stopLines.mapIndexed { i, (x, y) -> RoadNode(1 + i, x, y, trafficSignals = true) } +
                stopLines.mapIndexed { i, (x, y) -> RoadNode(5 + i, x * 15, y * 15) }

        fun road(
            id: Int,
            from: Int,
            to: Int,
        ) = RoadSegment(
            id = id,
            from = from,
            to = to,
            roadClass = RoadClass.SECONDARY,
            geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
            lanesForward = 1,
            lanesBackward = 1,
            maxSpeedKmh = 50,
        )
        val segments = (0 until 4).flatMap { i -> listOf(road(2 * i, 5 + i, 1 + i), road(2 * i + 1, 1 + i, 0)) }
        val graph = RoadGraph(RoadNetwork(nodes, segments))
        val signals = SignalSystem(graph)

        assertEquals(4, signals.controlledLinks.size, "Eine Ampel je Zufahrt")
        for (link in signals.controlledLinks) {
            assertTrue(graph.linkFrom[link] in 5..8, "Nur die Fahrt auf die Kreuzung zu: ${graph.linkFrom[link]}")
        }
        // Eine Anlage: Querrichtungen sind nie gemeinsam grün
        val west = signals.controlledLinks.single { graph.linkTo[it] == 1 }
        val north = signals.controlledLinks.single { graph.linkTo[it] == 3 }
        var time = 0.0
        while (time < SignalSystem.CYCLE_SECONDS * 2) {
            if (signals.state(west, time) ==
                SignalState.GREEN
            ) {
                assertEquals(SignalState.RED, signals.state(north, time))
            }
            time += 0.5
        }
        // Zwischen Haltelinie und Kreuzungsknoten wird nicht noch einmal gewartet
        val inside = graph.inLinks[0]
        assertTrue(inside.all { signals.isInternal(it) && !signals.isControlled(it) })
    }
}
