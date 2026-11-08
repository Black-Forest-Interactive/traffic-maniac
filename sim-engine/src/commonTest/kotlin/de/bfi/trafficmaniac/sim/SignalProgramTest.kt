package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalProgramTest {
    /**
     * Hauptstraße (primary) von West nach Ost mit drei Ampelkreuzungen bei x = 0, 500, 1000; an jeder eine
     * Querstraße (residential) nach Norden und Süden.
     */
    private fun avenue(): RoadGraph {
        val junctions = listOf(0f, 500f, 1000f)
        val nodes = ArrayList<RoadNode>()
        junctions.forEachIndexed { i, x -> nodes += RoadNode(i, x, 0f, trafficSignals = true) }
        nodes += RoadNode(3, -500f, 0f)
        nodes += RoadNode(4, 1500f, 0f)
        junctions.forEachIndexed { i, x ->
            nodes += RoadNode(5 + 2 * i, x, 300f)
            nodes += RoadNode(6 + 2 * i, x, -300f)
        }
        var id = 0

        fun road(
            from: Int,
            to: Int,
            roadClass: RoadClass,
        ) = RoadSegment(
            id = id++,
            from = from,
            to = to,
            roadClass = roadClass,
            geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
            lanesForward = 1,
            lanesBackward = 1,
            maxSpeedKmh = 50,
        )
        val main =
            listOf(
                road(3, 0, RoadClass.PRIMARY),
                road(0, 1, RoadClass.PRIMARY),
                road(1, 2, RoadClass.PRIMARY),
                road(2, 4, RoadClass.PRIMARY),
            )
        val cross =
            junctions.indices.flatMap {
                listOf(road(5 + 2 * it, it, RoadClass.RESIDENTIAL), road(6 + 2 * it, it, RoadClass.RESIDENTIAL))
            }
        return RoadGraph(RoadNetwork(nodes, main + cross))
    }

    private val graph = avenue()
    private val signals = SignalSystem(graph)

    /** Link, der auf der Hauptstraße von Westen an Kreuzung [junction] heranführt. */
    private fun eastbound(junction: Int) =
        graph.inLinks[junction].single {
            graph.linkFrom[it] ==
                listOf(3, 0, 1)[junction]
        }

    private fun northbound(junction: Int) = graph.inLinks[junction].single { graph.linkFrom[it] == 6 + 2 * junction }

    private fun greenSeconds(link: Int) =
        (0 until SignalSystem.CYCLE_SECONDS.toInt() * 10).count {
            signals.state(
                link,
                it / 10.0,
            ) == SignalState.GREEN
        } /
            10.0

    /** Erste Sekunde im Umlauf, zu der [link] von Rot auf Grün springt. */
    private fun greenStart(link: Int): Double {
        val steps = SignalSystem.CYCLE_SECONDS.toInt() * 10
        return (0 until steps).first { t ->
            signals.state(link, t / 10.0) == SignalState.GREEN &&
                signals.state(link, (t - 1) / 10.0) != SignalState.GREEN
        } / 10.0
    }

    @Test
    fun mainRoadGetsTheLongerGreenWhenItIsPreferred() {
        assertEquals(greenSeconds(eastbound(1)), greenSeconds(northbound(1)), "Ab Werk gleich verteilt")

        signals.setPrograms(mapOf(1 to SignalProgram(mainShare = 0.7)))

        assertEquals(35.0, greenSeconds(eastbound(1)), 0.2)
        assertEquals(15.0, greenSeconds(northbound(1)), 0.2)
        // Querrichtung und Hauptrichtung sind trotzdem nie gleichzeitig grün
        for (t in 0 until 600) {
            if (signals.state(eastbound(1), t / 10.0) == SignalState.GREEN) {
                assertEquals(SignalState.RED, signals.state(northbound(1), t / 10.0))
            }
        }
    }

    @Test
    fun greenWaveLetsAFiftyKmhCarRideThroughOnGreen() {
        val speed = 50 / 3.6
        val route = graph.linksAlong(listOf(0, 1, 2))
        assertEquals(listOf(eastbound(0), eastbound(1), eastbound(2)), route)

        assertEquals(3, signals.greenWave(graph, route, speed))

        // Wer zu Grünbeginn an der ersten Haltelinie ist, trifft die nächsten 500 m später wieder auf Grün
        for (junction in 1..2) {
            val expected = (greenStart(eastbound(0)) + junction * 500 / speed) % SignalSystem.CYCLE_SECONDS
            val actual = greenStart(eastbound(junction))
            val gap = abs(actual - expected).let { minOf(it, SignalSystem.CYCLE_SECONDS - it) }
            assertTrue(gap < 0.2, "Kreuzung $junction: Grün um $actual, erwartet $expected")
        }
    }

    @Test
    fun programsSurviveRebuildingTheSignals() {
        signals.setPrograms(mapOf(2 to SignalProgram(mainShare = 0.3)))
        signals.setSignal(1, false)
        assertEquals(0.3, signals.program(2)?.mainShare)
        assertEquals(15.0, greenSeconds(eastbound(2)), 0.2)
    }
}
