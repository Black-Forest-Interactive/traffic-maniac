package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertTrue

class RedLightTest {
    private fun config(runners: Double) =
        SimulationConfig(
            seed = 21,
            peakTripsPerHour = 2_000.0,
            demandProfile = DemandProfile.CONSTANT,
            accidentRiskPerHardBrakingSecond = 0.0,
            redLightRunnerShare = runners,
        )

    private fun Simulation.run(minutes: Int) = repeat(minutes * 60 * config.ticksPerSecond) { step() }

    /** Zufahrt von Westen auf die Ampelkreuzung (Knoten 0). */
    private fun Simulation.westApproach() = graph.inLinks[0].single { graph.linkFrom[it] == 1 }

    @Test
    fun aRedLightCameraCatchesDriversWhoGoThroughFreshRed() {
        val simulation = Simulation(crossroads(signals = true), config(runners = 0.5))
        simulation.speedControl.placeRedLightCamera(simulation.westApproach(), withSpeed = false)

        simulation.run(minutes = 30)

        val photos = simulation.speedControl.drainViolations()
        assertTrue(photos.isNotEmpty(), "Niemand über Rot gefahren")
        assertTrue(photos.all { it.redSeconds != null && it.redSeconds!! < 3.0 }, "Nur bei frischem Rot: $photos")
    }

    @Test
    fun withoutRedLightRunnersTheCameraStaysQuiet() {
        val simulation = Simulation(crossroads(signals = true), config(runners = 0.0))
        simulation.speedControl.placeRedLightCamera(simulation.westApproach(), withSpeed = false)

        simulation.run(minutes = 30)

        val photos = simulation.speedControl.drainViolations()
        // Wer bei Gelb nicht mehr bremsen kann, rutscht höchstens eine Sekunde ins Rot
        assertTrue(photos.all { it.redSeconds!! < 1.0 }, "Rotlicht ohne Sünder: $photos")
    }

    @Test
    fun aDisplayCountsSpeedersButIssuesNoFines() {
        val simulation = Simulation(crossroads(signals = false), config(runners = 0.0))
        simulation.graph.setSegmentSpeedLimit(0, 30)
        val display = simulation.speedControl.placeDisplay(0)

        simulation.run(minutes = 30)

        assertTrue(display.passed > 0)
        assertTrue(display.speeding in 1..display.passed, "${display.speeding} von ${display.passed} zu schnell")
        assertTrue(simulation.speedControl.drainViolations().isEmpty(), "Anzeigetafeln blitzen nicht")
    }
}
