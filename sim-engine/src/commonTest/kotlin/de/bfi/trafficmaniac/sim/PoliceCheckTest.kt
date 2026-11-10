package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertTrue

class PoliceCheckTest {
    private fun config(ignoreBan: Double) =
        SimulationConfig(
            seed = 13,
            peakTripsPerHour = 1_500.0,
            demandProfile = DemandProfile.CONSTANT,
            accidentRiskPerHardBrakingSecond = 0.0,
            overtakingBanIgnoreShare = ignoreBan,
            checkFindingRate = 0.1,
        )

    private fun Simulation.run(minutes: Int) = repeat(minutes * 60 * config.ticksPerSecond) { step() }

    @Test
    fun aPoliceCheckOnABikeStreetCatchesDriversOvertakingCyclists() {
        val simulation = Simulation(crossroads(signals = false), config(ignoreBan = 0.3))
        simulation.graph.setSegmentBikeStreet(0, true)
        simulation.speedControl.placePoliceCheck(0)

        simulation.run(minutes = 30)

        val findings = simulation.speedControl.drainViolations().mapNotNull { it.finding }
        assertTrue(CheckFinding.OVERTAKING in findings, "Überholer: $findings")
        assertTrue(CheckFinding.DEFECT in findings || CheckFinding.PHONE in findings, "Mängel oder Handy: $findings")
    }

    @Test
    fun withoutABikeStreetNobodyIsCaughtOvertaking() {
        val simulation = Simulation(crossroads(signals = false), config(ignoreBan = 0.3))
        simulation.speedControl.placePoliceCheck(0)

        simulation.run(minutes = 30)

        val findings = simulation.speedControl.drainViolations().mapNotNull { it.finding }
        assertTrue(findings.isNotEmpty())
        assertTrue(CheckFinding.OVERTAKING !in findings)
    }

    @Test
    fun lawAbidingDriversKeepToCyclingPaceOnABikeStreet() {
        val simulation = Simulation(crossroads(signals = false), config(ignoreBan = 0.0))
        simulation.graph.setSegmentBikeStreet(0, true)
        val display = simulation.speedControl.placeDisplay(0)

        simulation.run(minutes = 30)

        // Die Anzeigetafel misst gegen Tempo 50 – auf der Fahrradstraße fährt niemand mehr zu schnell
        assertTrue(display.passed > 0)
        assertTrue(display.speeding == 0, "${display.speeding} von ${display.passed} zu schnell")
    }
}
