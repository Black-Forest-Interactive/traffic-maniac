package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerClosureTest {
    private val simulation = Simulation(crossroads(), SimulationConfig(seed = 1))
    private val closures = simulation.infrastructure.closures
    private val links = simulation.graph.segmentLinks[0]

    @Test
    fun aPlayerClosureStaysWhenTheBuildingOfficeFinishesItsRepair() {
        closures.setPlayerClosed(0, true)
        closures.setWorks(0, true, closed = true)
        closures.setWorks(0, false)

        assertTrue(links.none { simulation.graph.isOpen(it) }, "Die Sperre des Spielers gilt weiter")
        assertEquals(setOf(0), simulation.infrastructure.snapshot().playerClosedSegments)

        closures.setPlayerClosed(0, false)
        assertTrue(links.all { simulation.graph.isOpen(it) })
    }

    @Test
    fun playerRoadworksSlowTrafficButKeepTheRoadOpen() {
        closures.setPlayerWorks(0, true)

        assertTrue(links.all { simulation.graph.isOpen(it) })
        assertTrue(links.all { simulation.graph.linkRoadFactor[it] < 1f })
        assertEquals(setOf(0), simulation.infrastructure.snapshot().playerWorksSegments)
        assertFalse(0 in simulation.infrastructure.snapshot().worksSegments, "Keine Bagger: zählt nicht als Reparatur")
    }
}
