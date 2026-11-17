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
import de.bfi.trafficmaniac.sim.DemandProfile
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Eine Wohnstraße mit Tempo 40: Möglich ist nur „Tempo 30 gewünscht“ (Raser-Beschwerden erst ab Tempo 50, keine
 * Unfälle, keine Schlaglöcher) – so ist jeder Auftrag vorhersehbar.
 */
class AssignmentsTest {
    private fun street(): CityMap {
        val nodes = listOf(RoadNode(0, -500f, 0f), RoadNode(1, 500f, 0f))
        val segment =
            RoadSegment(
                id = 0,
                from = 0,
                to = 1,
                roadClass = RoadClass.SECONDARY,
                geometry = Polyline(floatArrayOf(-500f, 0f, 500f, 0f)),
                lanesForward = 1,
                lanesBackward = 1,
                maxSpeedKmh = 40,
                name = "Wohnstraße",
            )
        return CityMap(
            name = "Test",
            attribution = "",
            origin = GeoPoint(0.0, 0.0),
            bounds = Bounds(-500f, -100f, 500f, 100f),
            roads = RoadNetwork(nodes, listOf(segment)),
            scenery = Scenery(),
            zones =
                listOf(
                    Zone(0, ZoneKind.RESIDENTIAL, 0f, 50f, area = 10_000f, node = 0),
                    Zone(1, ZoneKind.COMMERCIAL, 500f, 0f, area = 10_000f, node = 1),
                ),
        )
    }

    // Start 5:50 – die Spielstunde zählt ab Start: um 6:50 kommt der erste Auftrag
    private val config =
        SimulationConfig(
            seed = 5,
            peakTripsPerHour = 200.0,
            demandProfile = DemandProfile.CONSTANT,
            startSecondOfDay =
                5 * 3_600.0 + 50 * 60,
        )
    private val rules = GameRules(assignments = AssignmentRules(perHour = 1.0, firstDayFactor = 1.0, maxOpen = 1))

    private fun CityGame.run(minutes: Int) = repeat(minutes * 60 * config.ticksPerSecond) { step() }

    @Test
    fun aTempo30RequestPaysOnceTheLimitIsSet() {
        val game = CityGame(street(), config, rules)
        game.run(minutes = 61)
        val assignment = game.state().assignments.single()
        assertEquals(AssignmentKind.TEMPO_30, assignment.kind)
        assertEquals(
            HighlightKind.MEDIUM,
            game
                .state()
                .highlights
                .last()
                .kind,
        )

        game.submit(GameCommand.SetSpeedLimit(segment = 0, kmh = 30))
        game.run(minutes = 60)

        val state = game.state()
        assertEquals(rules.assignments.rewardTempo30, state.grantIncome)
        assertTrue(state.assignments.none { it.id == assignment.id })
        assertTrue(state.highlights.any { it.kind == HighlightKind.BIG && it.title.startsWith("Auftrag erfüllt") })
    }

    @Test
    fun aMissedRequestCostsReputation() {
        val game = CityGame(street(), config, rules)
        game.run(minutes = 61)
        val before = game.state().reputation
        game.run(minutes = ((rules.assignments.hoursTempo30 + 1) * 60).toInt())
        val state = game.state()
        assertEquals(0, state.grantIncome)
        assertTrue(state.highlights.any { it.kind == HighlightKind.WARNING && it.title.startsWith("Auftrag verpasst") })
        assertTrue(state.reputation < before, "${state.reputation} statt unter $before")
    }

    @Test
    fun aDeclinedKindPausesForADay() {
        val game = CityGame(street(), config, rules)
        game.run(minutes = 61)
        game.submit(
            GameCommand.DeclineAssignment(
                game
                    .state()
                    .assignments
                    .single()
                    .id,
            ),
        )
        game.run(minutes = 5 * 60)
        assertTrue(game.state().assignments.isEmpty(), "Die einzige mögliche Art pausiert")
    }
}
