package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherTest {
    /** Schneeschauer ab Spielbeginn (5 Uhr) für zwei Stunden, 6 cm pro Stunde. */
    private fun config(
        ploughs: Int,
        summerTyres: Double = 0.0,
    ) = SimulationConfig(
        seed = 9,
        peakTripsPerHour = 600.0,
        demandProfile = DemandProfile.CONSTANT,
        accidentRiskPerHardBrakingSecond = 0.0,
        softwareFailuresPerHour = 0.0,
        oilLeaksPerHour = 0.0,
        weather =
            WeatherConfig(
                spells = listOf(SnowSpell(day = 0, startHour = 5.0, hours = 2.0, cmPerHour = 6.0)),
                summerTyreShare = summerTyres,
                ploughs = ploughs,
                slideAccidentsPerHour = 0.0,
                summerTyreStuckPerHour = 2.0,
            ),
    )

    private fun Simulation.run(minutes: Int) = repeat(minutes * 60 * config.ticksPerSecond) { step() }

    @Test
    fun snowPilesUpAndSlowsTrafficDown() {
        val simulation = Simulation(crossroads(facilities = testFacilities), config(ploughs = 0))
        simulation.run(minutes = 30)

        assertTrue(simulation.snapshot().weather.snowing)
        val depth = simulation.weather.meanDepth()
        assertEquals(3.0, depth, 0.3, "Eine halbe Stunde bei 6 cm/h")
        assertTrue(simulation.graph.speedFactor(0) < 1f, "Auf Schnee langsamer")
    }

    @Test
    fun ploughsKeepTheRoadsClearer() {
        val without =
            Simulation(
                crossroads(facilities = testFacilities),
                config(ploughs = 0),
            ).apply { run(minutes = 60) }
        val with = Simulation(crossroads(facilities = testFacilities), config(ploughs = 2)).apply { run(minutes = 60) }

        assertTrue(with.snapshot().stats.activeVehicles > 0)
        assertTrue(
            with.weather.meanDepth() < without.weather.meanDepth() * 0.8,
            "mit Räumdienst ${with.weather.meanDepth()} cm, ohne ${without.weather.meanDepth()} cm",
        )
    }

    @Test
    fun carsOnSummerTyresGetStuckInDeepSnow() {
        val simulation = Simulation(crossroads(facilities = testFacilities), config(ploughs = 0, summerTyres = 1.0))
        simulation.run(minutes = 60)

        val stuck = simulation.incidentsToday[IncidentKind.STUCK_IN_SNOW] ?: 0
        assertTrue(stuck > 0, "Steckengeblieben: $stuck")
    }
}
