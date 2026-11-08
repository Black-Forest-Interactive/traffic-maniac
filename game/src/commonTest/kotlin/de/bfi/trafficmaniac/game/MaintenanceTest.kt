package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.GeoPoint
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.Scenery
import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.sim.DamageKind
import de.bfi.trafficmaniac.sim.DemandProfile
import de.bfi.trafficmaniac.sim.Infrastructure
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.sim.isClosed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Zwei Kilometer Straße zwischen Wohngebiet und Gewerbe; Abschnitt 0 ist der, der kaputtgeht. */
private fun road(): CityMap {
    val nodes = listOf(RoadNode(0, -1000f, 0f), RoadNode(1, 0f, 0f), RoadNode(2, 1000f, 0f))

    fun segment(
        id: Int,
        from: Int,
        to: Int,
    ) = RoadSegment(
        id = id,
        from = from,
        to = to,
        roadClass = RoadClass.SECONDARY,
        geometry = Polyline(floatArrayOf(nodes[from].x, 0f, nodes[to].x, 0f)),
        lanesForward = 1,
        lanesBackward = 1,
        maxSpeedKmh = 50,
        name = "Kraterstraße",
    )
    return CityMap(
        name = "Test",
        attribution = "",
        origin = GeoPoint(0.0, 0.0),
        bounds = Bounds(-1000f, -100f, 1000f, 100f),
        roads = RoadNetwork(nodes, listOf(segment(0, 0, 1), segment(1, 1, 2))),
        scenery = Scenery(),
        zones =
            listOf(
                Zone(0, ZoneKind.RESIDENTIAL, -1000f, 0f, area = 10_000f, node = 0),
                Zone(1, ZoneKind.COMMERCIAL, 1000f, 0f, area = 10_000f, node = 2),
            ),
    )
}

class MaintenanceTest {
    private val config =
        SimulationConfig(
            seed = 21,
            ticksPerSecond = 4,
            peakTripsPerHour = 600.0,
            demandProfile = DemandProfile.CONSTANT,
            truckShare = 0.3,
            softwareFailuresPerHour = 0.0,
            oilLeaksPerHour = 0.0,
            accidentRiskPerHardBrakingSecond = 0.0,
            potholeAccidentsPerHour = 0.0,
            electricShare = 0.0,
            initialConditionMin = 0.99,
            wearPerAxleLoad = 0.0005,
            // Genug Parkplätze, sonst wird man im Testort vor lauter Parksuchverkehr abgewählt
            curbSquareMetersPerSpace = 5.0,
        )

    private fun CityGame.run(minutes: Int) = repeat(minutes * 60 * simulation.config.ticksPerSecond) { step() }

    /** Bringt Abschnitt 0 knapp über die Schwelle und fährt, bis die Meldung da ist. */
    private fun CityGame.damage(threshold: Double = Infrastructure.POTHOLES): DamageReport {
        simulation.infrastructure.repairSegment(0, threshold + 0.002)
        repeat(6) {
            run(minutes = 60)
            state()
                .maintenance.reports
                .firstOrNull { it.segment == 0 }
                ?.let { return it }
        }
        error("Keine Schadensmeldung")
    }

    @Test
    fun warningSignIsCheapAndSlowsTrafficButDamageKeepsGrowing() {
        val game = CityGame(road(), config)
        val report = game.damage()
        assertEquals(DamageKind.POTHOLES, report.kind)
        val expensesBefore = game.state().expenses

        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.WarningSign(20)))
        game.run(minutes = 1)
        val signed = game.state()
        val conditionAfterSign = game.simulation.infrastructure.condition(0)
        game.run(minutes = 60)

        assertEquals(game.rules.warningSignCost, signed.expenses - expensesBefore)
        assertEquals(20, signed.speedLimits[0])
        assertEquals(
            DamageStatus.SIGNED,
            signed.maintenance.reports
                .single { it.segment == 0 }
                .status,
        )
        assertTrue(game.simulation.infrastructure.condition(0) < conditionAfterSign, "Der Schaden wächst weiter")
    }

    @Test
    fun doingNothingLeadsToAccidentsAndLawsuits() {
        val game = CityGame(road(), config.copy(potholeAccidentsPerHour = 3.0))
        val report = game.damage(Infrastructure.CRATER)
        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.Ignore))

        game.run(minutes = 4 * 60)

        val state = game.state()
        assertTrue(state.maintenance.lawsuits > 0, "Keine Klagen")
        assertTrue(state.maintenance.lawsuitCosts >= game.rules.lawsuitMin)
        assertEquals(
            DamageStatus.IGNORED,
            state.maintenance.reports
                .single { it.segment == 0 }
                .status,
        )
    }

    @Test
    fun warningSignProtectsAgainstLawsuits() {
        val game = CityGame(road(), config.copy(potholeAccidentsPerHour = 3.0))
        val report = game.damage(Infrastructure.CRATER)
        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.WarningSign(30)))
        game.run(minutes = 1)
        // Klagen aus der Zeit vor dem Schild zählen nicht
        val before = game.state().maintenance.lawsuits

        game.run(minutes = 4 * 60)

        assertEquals(before, game.state().maintenance.lawsuits)
    }

    @Test
    fun missedDeadlineCountsAsDoingNothing() {
        val game = CityGame(road(), config, GameRules(damageDeadlineHours = 1))
        game.damage()
        game.run(minutes = 2 * 60)
        assertEquals(
            DamageStatus.IGNORED,
            game
                .state()
                .maintenance.reports
                .single { it.segment == 0 }
                .status,
        )
    }

    @Test
    fun planningApprovalNeverFinishes() {
        val game = CityGame(road(), config, GameRules(termDays = 30))
        val report = game.damage()
        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.Repair(RepairMethod.PLANNING)))

        game.run(minutes = 3 * 24 * 60)

        val planning =
            game
                .state()
                .maintenance.reports
                .single { it.segment == 0 }
        assertEquals(DamageStatus.PLANNING, planning.status)
        assertTrue(planning.progress!!.contains("Fertigstellung voraussichtlich 20"), planning.progress)
        assertTrue(game.simulation.infrastructure.condition(0) < Infrastructure.POTHOLES, "Straße bleibt marode")
    }

    @Test
    fun tenderRepairClosesTheRoadAndThenFixesIt() {
        val rules = GameRules(tenderDays = 0.1, constructionDays = 0.1)
        val game = CityGame(road(), config, rules)
        val report = game.damage()
        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.Repair(RepairMethod.TENDER)))

        var closed = false
        repeat(12) {
            game.run(minutes = 30)
            // Nur die Baustelle zählt – Markt oder Stadtfest können die einzige Straße der Testkarte auch sperren
            closed = closed ||
                game.simulation.infrastructure.closures
                    .isWorksClosed(0)
        }

        assertTrue(closed, "Während der Baustelle ist gesperrt")
        assertTrue(
            game
                .state()
                .maintenance.reports
                .isEmpty(),
        )
        assertTrue(game.simulation.infrastructure.condition(0) > 0.95)
        assertTrue(
            !game.simulation.infrastructure.closures
                .isWorksClosed(0),
        )
    }

    @Test
    fun turboConsortiumRepairsOvernightWithAMaintenanceContract() {
        val game = CityGame(road(), config)
        val report = game.damage()
        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.Repair(RepairMethod.TURBO)))

        val closedAt = ArrayList<Int>()
        repeat(30) {
            if (game
                    .state()
                    .maintenance.reports
                    .isEmpty()
            ) {
                return@repeat
            }
            game.run(minutes = 60)
            if (game.simulation.infrastructure.closures
                    .isWorksClosed(0)
            ) {
                closedAt += game.simulation.gameTime.hour
            }
        }

        val state = game.state()
        // Gebaut wird nur nachts (22–5 Uhr)
        assertTrue(closedAt.isNotEmpty() && closedAt.all { it >= 22 || it < 5 }, "Sperrung um $closedAt Uhr")
        assertTrue(state.maintenance.reports.isEmpty(), "Repariert: ${state.maintenance.reports}")
        assertEquals(1, state.maintenance.turboContracts)
        // Pfusch: nur 75 % und das hält nicht lange
        assertTrue(game.simulation.infrastructure.condition(0) <= game.rules.turboCondition)
    }

    @Test
    fun fewParkingSpacesBringTicketIncome() {
        val game = CityGame(road(), config.copy(curbSquareMetersPerSpace = 1e9, peakTripsPerHour = 1_200.0))
        game.submit(GameCommand.HireWardens(20))
        game.submit(GameCommand.SetStrictness(Strictness.RUTHLESS))

        game.run(minutes = 10 * 60)

        val parking = game.state().parkingOffice
        assertTrue(parking.illegalParkers > 0, "Keine Falschparker")
        assertTrue(parking.ticketIncome > 0, "Keine Knöllchen")
    }
}
