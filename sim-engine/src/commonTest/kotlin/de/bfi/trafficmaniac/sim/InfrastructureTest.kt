package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.FacilityKind
import de.bfi.trafficmaniac.city.GeoPoint
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.Scenery
import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hauptstraße West → Brücke → Ost; um die Brücke führt eine enge Gasse (passive Nebenstraße, 2 m breit).
 * Wohngebiet im Westen, Gewerbe im Osten.
 */
private fun detourCity(): CityMap {
    val nodes =
        listOf(
            RoadNode(0, -1000f, 0f),
            RoadNode(1, 0f, 0f),
            RoadNode(2, 500f, 0f),
            RoadNode(3, 1500f, 0f),
            RoadNode(4, 100f, 150f),
            RoadNode(5, 400f, 150f),
        )

    fun segment(
        id: Int,
        from: Int,
        to: Int,
        bridge: Boolean = false,
        passive: Boolean = false,
    ) = RoadSegment(
        id = id,
        from = from,
        to = to,
        roadClass = if (passive) RoadClass.LIVING_STREET else RoadClass.PRIMARY,
        geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
        lanesForward = 1,
        lanesBackward = 1,
        maxSpeedKmh = if (passive) 20 else 50,
        name = if (passive) "Gasse" else "Hauptstraße",
        bridge = bridge,
        passive = passive,
        maxWidthMeters = if (passive) 2.0f else null,
    )
    return CityMap(
        name = "Umleitungsstadt",
        attribution = "",
        origin = GeoPoint(0.0, 0.0),
        bounds = Bounds(-1000f, -200f, 1500f, 300f),
        roads =
            RoadNetwork(
                nodes,
                listOf(
                    segment(0, 0, 1),
                    segment(1, 1, 2, bridge = true),
                    segment(2, 2, 3),
                    segment(3, 1, 4, passive = true),
                    segment(4, 4, 5, passive = true),
                    segment(5, 5, 2, passive = true),
                ),
            ),
        scenery = Scenery(),
        zones =
            listOf(
                Zone(0, ZoneKind.RESIDENTIAL, -1000f, 0f, area = 10_000f, node = 0),
                Zone(1, ZoneKind.COMMERCIAL, 1500f, 0f, area = 10_000f, node = 3),
            ),
        facilities =
            testFacilities
                .filter { it.kind != FacilityKind.CHARGING_STATION }
                .map { it.copy(node = 0, x = -1000f, y = 0f) },
    )
}

class InfrastructureTest {
    private val base =
        SimulationConfig(
            seed = 5,
            peakTripsPerHour = 600.0,
            demandProfile = DemandProfile.CONSTANT,
            softwareFailuresPerHour = 0.0,
            oilLeaksPerHour = 0.0,
            accidentRiskPerHardBrakingSecond = 0.0,
            potholeAccidentsPerHour = 0.0,
            electricShare = 0.0,
            initialConditionMin = 0.99,
        )

    private fun Simulation.run(minutes: Int) = repeat(minutes * 60 * config.ticksPerSecond) { step() }

    private fun Simulation.events(minutes: Int): List<DamageEvent> {
        val events = ArrayList<DamageEvent>()
        repeat(minutes) {
            run(1)
            events += infrastructure.drainEvents()
        }
        return events
    }

    @Test
    fun aTruckWearsTheRoadLikeThousandsOfCars() {
        val ratio = VehicleType.TRUCK.equivalentAxleLoads / VehicleType.CAR.equivalentAxleLoads
        assertTrue(ratio > 1_000, "LKW / PKW: $ratio")
    }

    @Test
    fun trucksWearRoadsDisproportionately() {
        fun wear(truckShare: Double): Double {
            val simulation = Simulation(crossroads(), base.copy(truckShare = truckShare, vanShare = 0.0))
            val before = simulation.infrastructure.condition(0)
            simulation.run(minutes = 60)
            return before - simulation.infrastructure.condition(0)
        }
        val cars = wear(0.0)
        val trucks = wear(0.5)
        assertTrue(cars > 0, "Auch ohne LKW altert die Straße: $cars")
        assertTrue(trucks > cars * 10, "PKW $cars, halb LKW $trucks")
    }

    @Test
    fun maintenanceBudgetSlowsDecay() {
        fun wear(factor: Double): Double {
            val simulation = Simulation(crossroads(), base.copy(truckShare = 0.3))
            simulation.infrastructure.maintenanceFactor = factor
            val before = simulation.infrastructure.condition(0)
            simulation.run(minutes = 30)
            return before - simulation.infrastructure.condition(0)
        }
        assertTrue(wear(0.2) < wear(1.0) * 0.3)
    }

    @Test
    fun potholesAreReportedAndSlowTrafficDown() {
        val simulation = Simulation(crossroads(), base.copy(truckShare = 0.5, wearPerAxleLoad = 0.001))
        simulation.infrastructure.repairSegment(0, Infrastructure.POTHOLES + 0.01)
        val link = simulation.graph.segmentLinks[0].first()
        assertEquals(1f, simulation.graph.speedFactor(link))

        val events = simulation.events(minutes = 30)

        assertTrue(
            events.any { it.kind == DamageKind.POTHOLES && it.segment == 0 },
            "Meldungen: ${events.map { it.kind }}",
        )
        assertTrue(simulation.graph.speedFactor(link) < 1f)
    }

    @Test
    fun brokenSignalsFlashYellow() {
        val simulation = Simulation(crossroads(signals = true), base.copy(signalWearPerVehicle = 0.01))
        val approach = simulation.signals.controlledLinks.first()

        val events = simulation.events(minutes = 20)

        assertTrue(events.any { it.kind == DamageKind.SIGNAL_FAILURE && it.node == 0 })
        assertEquals(SignalState.FLASHING, simulation.signals.state(approach, simulation.time))
        simulation.infrastructure.trafficLights.repair(0)
        assertTrue(simulation.signals.state(approach, simulation.time) != SignalState.FLASHING)
    }

    @Test
    fun neglectedBridgesCollapse() {
        val simulation = Simulation(detourCity(), base.copy(truckShare = 0.5, wearPerAxleLoad = 0.002))
        simulation.infrastructure.repairSegment(1, Infrastructure.BRIDGE_UNSAFE + 0.005)

        val events = simulation.events(minutes = 60)

        assertTrue(events.any { it.kind == DamageKind.BRIDGE_UNSAFE }, "Meldungen: ${events.map { it.kind }}")
        assertTrue(events.any { it.kind == DamageKind.BRIDGE_COLLAPSE }, "Meldungen: ${events.map { it.kind }}")
        assertTrue(simulation.graph.segmentLinks[1].all { simulation.graph.isClosed(it) })
        assertTrue(simulation.infrastructure.closures.isCollapsed(1))
    }

    @Test
    fun weightLimitsSendTrucksAround() {
        val simulation =
            Simulation(detourCity(), base.copy(truckShare = 1.0, vanShare = 0.0, weightLimitIgnoreShare = 0.0))
        simulation.infrastructure.closures.setDetour(3, true)
        simulation.infrastructure.closures.setDetour(4, true)
        simulation.infrastructure.closures.setDetour(5, true)
        simulation.infrastructure.closures.setWeightLimit(1, 7.5f)
        simulation.run(minutes = 10)

        val bridge = simulation.graph.segmentLinks[1].toSet()
        val onBridge = simulation.vehiclesForTest.count { it.link in bridge && it.role == VehicleRole.PRIVATE }
        assertEquals(0, onBridge, "LKW auf der Brücke trotz 7,5-t-Limit")
    }

    @Test
    fun aDetourThroughANarrowSideStreetGetsATruckStuck() {
        val simulation = Simulation(detourCity(), base.copy(truckShare = 0.3))
        // Vollsperrung der Brücke, Umleitung durch die Gasse
        simulation.infrastructure.closures.setWorks(1, active = true, closed = true)
        for (segment in 3..5) simulation.infrastructure.closures.setDetour(segment, true)

        simulation.run(minutes = 60)

        val stuck = simulation.statisticsForTest.peek(0, Weekday.MONDAY).incidents[IncidentKind.TRUCK_STUCK] ?: 0
        assertTrue(stuck > 0, "Kein LKW eingeklemmt")
        assertTrue(simulation.snapshot().stats.completedTrips > 0, "Die Umleitung wird befahren")
    }

    @Test
    fun sideStreetsStayEmptyWithoutDetour() {
        val simulation = Simulation(detourCity(), base)
        simulation.run(minutes = 20)
        val side = (3..5).flatMap { simulation.graph.segmentLinks[it].toList() }.toSet()
        assertTrue(simulation.vehiclesForTest.none { it.link in side })
    }

    @Test
    fun fewParkingSpacesCauseSearchTrafficAndIllegalParking() {
        val simulation = Simulation(crossroads(), base.copy(curbSquareMetersPerSpace = 1e9, peakTripsPerHour = 1_200.0))

        simulation.run(minutes = 90)

        val report = simulation.statisticsForTest.peek(0, Weekday.MONDAY)
        assertTrue(report.illegalParkers > 0, "Falschparker: ${report.illegalParkers}")
        assertTrue(report.parkingSearchMinutes.isNotEmpty(), "Niemand musste suchen")
        val violations = simulation.parking.drainViolations()
        assertTrue(violations.any { it.kind == ParkingViolationKind.ILLEGAL })
    }
}
