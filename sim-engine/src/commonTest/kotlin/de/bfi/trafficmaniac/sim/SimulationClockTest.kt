package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SimulationClockTest {
    @Test
    fun startsAtZero() {
        val clock = SimulationClock()

        assertEquals(0, clock.tick)
        assertEquals(0.0, clock.elapsedSeconds)
    }

    @Test
    fun advancingConvertsTicksToSeconds() {
        val clock = SimulationClock(ticksPerSecond = 10)

        clock.advance(25)

        assertEquals(25, clock.tick)
        assertEquals(2.5, clock.elapsedSeconds)
        assertEquals(0.1, clock.tickDurationSeconds)
    }

    @Test
    fun rejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { SimulationClock(ticksPerSecond = 0) }
        assertFailsWith<IllegalArgumentException> { SimulationClock().advance(-1) }
    }
}
