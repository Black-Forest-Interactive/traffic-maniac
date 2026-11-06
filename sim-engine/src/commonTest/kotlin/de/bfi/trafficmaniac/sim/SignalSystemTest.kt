package de.bfi.trafficmaniac.sim

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SignalSystemTest {
    private val graph = RoadGraph(crossroads().roads)
    private val signals = SignalSystem(graph, Random(1))

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
}
