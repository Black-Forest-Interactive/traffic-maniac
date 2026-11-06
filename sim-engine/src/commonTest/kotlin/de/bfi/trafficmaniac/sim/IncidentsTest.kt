package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun Simulation.dayStatisticsForTest(): DayReport = statisticsForTest.peek(gameTime.day, gameTime.weekday)

private fun Simulation.openIncidents(): Int = incidentsForTest.active.size

private fun Simulation.firstPrivateVehicleAfter(minutes: Int): Vehicle {
    repeat(minutes * 60 * config.ticksPerSecond) { step() }
    return vehiclesForTest.first { it.role == VehicleRole.PRIVATE && !it.disabled }
}

private fun Simulation.forceAccident(vehicle: Vehicle) = incidentsForTest.accident(vehicle, vehicle.link, time)

class IncidentsTest {
    private val base =
        SimulationConfig(
            seed = 3,
            peakTripsPerHour = 600.0,
            demandProfile = DemandProfile.CONSTANT,
            softwareFailuresPerHour = 0.0,
            oilLeaksPerHour = 0.0,
            accidentRiskPerHardBrakingSecond = 0.0,
            electricShare = 0.0,
        )

    private fun Simulation.run(minutes: Int) = repeat(minutes * 60 * config.ticksPerSecond) { step() }

    @Test
    fun brokenCarsAreTowedAway() {
        val simulation =
            Simulation(
                crossroads(facilities = testFacilities),
                base.copy(softwareFailuresPerHour = 2.0, oldShare = 0.0),
            )

        simulation.run(minutes = 60)

        val report = simulation.snapshot()
        val breakdowns = simulation.dayStatisticsForTest().incidents[IncidentKind.SOFTWARE_FAILURE] ?: 0
        assertTrue(breakdowns > 0, "Pannen: $breakdowns")
        // Abgeschleppt: Es stehen nicht mehr Autos herum als offene Einsätze
        val disabledNow = report.flags.count { it.toInt() and FLAG_DISABLED != 0 }
        assertTrue(
            disabledNow <= simulation.openIncidents(),
            "liegengeblieben $disabledNow, offen ${simulation.openIncidents()}",
        )
        assertTrue(simulation.openIncidents() < breakdowns, "Einsätze werden abgeschlossen")
    }

    @Test
    fun ambulancesReportResponseTimes() {
        val simulation = Simulation(crossroads(facilities = testFacilities), base)
        val victim = simulation.firstPrivateVehicleAfter(minutes = 5)

        simulation.forceAccident(victim)
        simulation.run(minutes = 20)

        val minutes = simulation.dayStatisticsForTest().ambulanceMinutes
        assertEquals(1, minutes.size)
        assertTrue(minutes.single() in 0.1..20.0, "Anfahrt ${minutes.single()} min")
    }

    @Test
    fun electricCarsQueueAtChargingStations() {
        val config = base.copy(electricShare = 1.0, chargeBelowShare = 1.0, peakTripsPerHour = 1_200.0)
        val simulation = Simulation(crossroads(facilities = testFacilities), config)

        simulation.run(minutes = 60)

        val station = simulation.snapshot().chargingStations.single()
        assertEquals(1, station.capacity)
        assertTrue(station.charging + station.waiting > 0)
        assertTrue(simulation.dayStatisticsForTest().chargingWaitMinutes.isNotEmpty(), "Es musste jemand warten")
    }
}
