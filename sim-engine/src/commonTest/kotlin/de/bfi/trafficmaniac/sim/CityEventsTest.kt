package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CityEventsTest {
    private val config =
        SimulationConfig(
            seed = 3,
            peakTripsPerHour = 900.0,
            demandProfile = DemandProfile.CONSTANT,
            softwareFailuresPerHour = 0.0,
            oilLeaksPerHour = 0.0,
            accidentRiskPerHardBrakingSecond = 0.0,
            potholeAccidentsPerHour = 0.0,
            electricShare = 0.0,
            vanShare = 0.0,
            truckShare = 0.0,
        )

    private fun Simulation.run(seconds: Int) = repeat(seconds * config.ticksPerSecond) { step() }

    @Test
    fun aClosureBlocksTheRoadAndReopensItAfterwards() {
        val simulation = Simulation(crossroads(), config)
        val links = simulation.graph.segmentLinks[0]
        simulation.events.startClosure(EventKind.DEMO, listOf(0), "Westarm", simulation.time, simulation.time + 600)
        simulation.run(60)
        assertTrue(links.none { simulation.graph.isOpen(it) }, "Während der Demo gesperrt")
        assertEquals(1, simulation.snapshot().events.size)
        simulation.run(600)
        assertTrue(links.all { simulation.graph.isOpen(it) }, "Danach wieder frei")
        assertEquals(
            EventKind.DEMO,
            simulation.events
                .drainFinished()
                .single()
                .kind,
        )
        assertTrue(simulation.events.active.isEmpty())
    }

    @Test
    fun overlappingClosuresKeepTheRoadClosedUntilBothAreOver() {
        val simulation = Simulation(crossroads(), config)
        val link = simulation.graph.segmentLinks[0].first()
        simulation.events.startClosure(EventKind.MARKET, listOf(0), "Markt", 0.0, 120.0)
        simulation.events.startClosure(EventKind.DEMO, listOf(0), "Demo", 0.0, 600.0)
        simulation.run(180)
        assertFalse(simulation.graph.isOpen(link), "Markt vorbei, Demo noch nicht")
        simulation.run(480)
        assertTrue(simulation.graph.isOpen(link))
    }

    @Test
    fun aBikeCorsoRunsRedLightsAndNobodyOvertakesIt() {
        val simulation = Simulation(crossroads(signals = true), config)
        simulation.run(300)
        val event =
            assertNotNull(
                simulation.events.startConvoy(EventKind.BIKE_CORSO, 1, 2, 3, "Ost-West-Achse", simulation.time),
            )
        val corsoSpeed = VehicleType.BIKE_CORSO.maxSpeed
        var checked = 0
        repeat(120) {
            simulation.run(1)
            for (corso in event.vehicles.filter { it.active }) {
                // Wer dahinter fährt und nicht gerade erst mit Einfahrtstempo eingebogen ist, rollt im Korso-Tempo
                val others =
                    simulation.vehiclesForTest.filter {
                        it.link == corso.link &&
                            it.role == VehicleRole.PRIVATE &&
                            it.position < corso.position &&
                            simulation.time - it.linkEnterTime > SETTLE_SECONDS
                    }
                others.forEach { assertTrue(it.speed < corsoSpeed + 0.5, "Überholt mit ${it.speed} m/s") }
                checked += others.size
            }
        }
        assertTrue(checked > 0, "Hinter dem Korso fährt jemand")
        // 1 km im Schritttempo plus Einfahren: nach einer Viertelstunde ist der Korso durch
        simulation.run(900)
        assertTrue(simulation.events.active.isEmpty(), "Korso ist angekommen")
        assertEquals(
            EventKind.BIKE_CORSO,
            simulation.events
                .drainFinished()
                .single()
                .kind,
        )
    }

    @Test
    fun aHeavyTransportCrawlsThroughAndCarriesABlueLight() {
        val simulation = Simulation(crossroads(), config)
        val event =
            assertNotNull(
                simulation.events.startConvoy(EventKind.HEAVY_TRANSPORT, 3, 4, 1, "Nord-Süd", simulation.time),
            )
        simulation.run(30)
        val snapshot = simulation.snapshot()
        val index = snapshot.ids.indexOf(event.vehicles.single().id)
        assertTrue(index >= 0, "Schwertransport ist unterwegs")
        assertTrue(snapshot.flags[index].toInt() and FLAG_EMERGENCY != 0)
        assertTrue(snapshot.flags[index].toInt() and FLAG_EVENT != 0)
        simulation.run(600)
        assertTrue(simulation.events.active.isEmpty(), "Schwertransport ist angekommen")
    }

    @Test
    fun stoppingAConvoyRemovesItsVehiclesWithoutCountingThemAsStuck() {
        val simulation = Simulation(crossroads(), config)
        val event = assertNotNull(simulation.events.startConvoy(EventKind.BIKE_CORSO, 1, 2, 4, "Korso", 0.0))
        simulation.run(60)
        val stuckBefore = simulation.snapshot().stats.stuckRemovals
        simulation.events.stop(event.id, simulation.time)
        simulation.run(30)
        assertTrue(event.vehicles.none { it.active })
        assertTrue(simulation.vehiclesForTest.none { it.role == VehicleRole.BIKE_CORSO })
        assertEquals(stuckBefore, simulation.snapshot().stats.stuckRemovals)
    }

    private companion object {
        /** So lange darf ein eben eingebogenes Auto auf das Korso-Tempo abbremsen. */
        const val SETTLE_SECONDS = 8.0
    }
}
