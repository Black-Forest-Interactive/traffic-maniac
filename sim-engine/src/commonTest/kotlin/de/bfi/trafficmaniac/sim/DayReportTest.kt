package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayReportTest {
    @Test
    fun closesTheDayAtMidnight() {
        // Start um 23:50 – nach 15 Minuten ist der erste Tag abgeschlossen
        val config =
            SimulationConfig(
                seed = 3,
                peakTripsPerHour = 1_200.0,
                demandProfile = DemandProfile.CONSTANT,
                startSecondOfDay = GameTime.SECONDS_PER_DAY - 10 * GameTime.SECONDS_PER_MINUTE,
            )
        val simulation = Simulation(crossroads(), config)

        repeat(15 * 60 * config.ticksPerSecond) { simulation.step() }

        val report = simulation.dayReports.single()
        assertEquals(0, report.day)
        assertEquals(Weekday.MONDAY, report.weekday)
        assertTrue(report.tripsStartedPerHour[23] > 0, "Fahrten in der letzten Stunde")
        assertEquals(report.tripsStarted, report.tripsStartedPerHour[23])
        assertEquals(Weekday.TUESDAY, simulation.gameTime.weekday)
    }
}
