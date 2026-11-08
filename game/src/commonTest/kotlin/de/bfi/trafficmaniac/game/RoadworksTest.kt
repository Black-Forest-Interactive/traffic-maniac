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

class RoadworksTest {
    /** Zwei Kilometer gerade Straße aus zwei Abschnitten (eine Strecke), Wohngebiet und Gewerbe an den Enden. */
    private fun straightRoad(): CityMap {
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
            name = "Teststraße",
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

    private val config = SimulationConfig(seed = 7, peakTripsPerHour = 200.0, demandProfile = DemandProfile.CONSTANT)

    @Test
    fun roadworksSetTempoThirtyAndGiveTheOldLimitBack() {
        val game = CityGame(straightRoad(), config)
        game.submit(GameCommand.SetSpeedLimit(0, 40))
        game.submit(GameCommand.SetRoadworks(listOf(0, 1), active = true))
        game.step()
        val state = game.state()
        assertEquals(mapOf(0 to 30, 1 to 30), state.speedLimits)
        assertEquals(setOf(0, 1), state.roadworks)
        assertEquals(game.rules.speedLimitSignCost + game.rules.roadworksSetupCost, state.expenses, "Eine Strecke")

        game.submit(GameCommand.SetRoadworks(listOf(0, 1), active = false))
        game.step()
        assertEquals(mapOf(0 to 40, 1 to 40), game.state().speedLimits, "Das Tempolimit von vorher gilt wieder")
        assertTrue(game.state().roadworks.isEmpty())
    }

    @Test
    fun aClosureBlocksTheRoadUntilItIsLifted() {
        val game = CityGame(straightRoad(), config)
        game.submit(GameCommand.SetRoadClosure(listOf(1), closed = true))
        game.step()
        assertEquals(
            setOf(1),
            game.simulation.infrastructure
                .snapshot()
                .playerClosedSegments,
        )
        assertEquals(setOf(1), game.state().roadClosures)
        assertEquals(game.rules.closureCost, game.state().expenses)

        game.submit(GameCommand.SetRoadClosure(listOf(1), closed = false))
        game.step()
        assertTrue(
            game.simulation.infrastructure
                .snapshot()
                .playerClosedSegments
                .isEmpty(),
        )
    }

    @Test
    fun aRoadworksWithoutWorkersCostsEveryDayAndThePressFindsOut() {
        val rules = GameRules(fakeRoadworksGraceDays = 0, fakeRoadworksPressChance = 1.0)
        val game = CityGame(straightRoad(), config, rules)
        game.submit(GameCommand.SetRoadworks(listOf(0), active = true))
        game.step()
        val reputation = game.state().reputation
        val expenses = game.state().expenses

        // Start um 5 Uhr – bis nach Mitternacht
        repeat(20 * 3600 * config.ticksPerSecond) { game.step() }

        val state = game.state()
        assertEquals(
            expenses + rules.roadworksDailyCost,
            state.expenses,
            "Absicherung für einen Tag",
        )
        assertTrue(state.reputation < reputation, "Reputation ${state.reputation} statt $reputation")
        assertTrue(state.messages.any { it.text.startsWith("Presse: „Baustelle ohne Bauarbeiter“") })
    }
}
