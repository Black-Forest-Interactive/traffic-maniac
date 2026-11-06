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

class CityGameTest {
    /** Zwei Kilometer gerade Straße (Tempo 50) mit Wohngebiet an einem und Gewerbe am anderen Ende. */
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

    private val config = SimulationConfig(seed = 11, peakTripsPerHour = 1_500.0, demandProfile = DemandProfile.CONSTANT)

    private fun CityGame.run(minutes: Int) = repeat(minutes * 60 * config.ticksPerSecond) { step() }

    @Test
    fun fineTiers() {
        assertEquals(FineTier.UP_TO_10, FineTier.of(0.5))
        assertEquals(FineTier.UP_TO_10, FineTier.of(10.0))
        assertEquals(FineTier.UP_TO_20, FineTier.of(10.1))
        assertEquals(FineTier.OVER_30, FineTier.of(55.0))
    }

    @Test
    fun cameraOnAbsurdLimitEarnsMoney() {
        val game = CityGame(straightRoad(), config)
        game.submit(GameCommand.SetSpeedLimit(segment = 0, kmh = 20))
        game.submit(GameCommand.PlaceCamera(segment = 0))

        game.run(minutes = 20)

        val state = game.state()
        val camera = state.cameras.single()
        assertEquals(game.rules.speedLimitSignCost + game.rules.cameraCost, state.expenses)
        assertTrue(state.fineIncome > 0, "Bußgelder: ${state.fineIncome}")
        assertEquals(state.fineIncome, camera.totalRevenue)
        assertEquals(game.rules.startBudget - state.expenses + state.fineIncome, state.balance)
        assertEquals(mapOf(0 to 20), state.speedLimits)
    }

    @Test
    fun cameraWithoutLowLimitEarnsLittle() {
        val strict =
            CityGame(straightRoad(), config).apply {
                submit(GameCommand.SetSpeedLimit(segment = 0, kmh = 20))
                submit(GameCommand.PlaceCamera(segment = 0))
                run(minutes = 20)
            }
        val normal =
            CityGame(straightRoad(), config).apply {
                submit(GameCommand.PlaceCamera(segment = 0))
                run(minutes = 20)
            }

        assertTrue(strict.state().fineIncome > 3 * normal.state().fineIncome)
    }

    @Test
    fun refusesWhenMoneyRunsOut() {
        val game = CityGame(straightRoad(), config, GameRules(startBudget = 100_000))
        game.submit(GameCommand.PlaceCamera(segment = 0))
        game.submit(GameCommand.PlaceCamera(segment = 1))

        game.step()

        val state = game.state()
        assertEquals(1, state.cameras.size)
        assertTrue(
            state.messages
                .last()
                .text
                .startsWith("Zu wenig Geld"),
        )
    }

    @Test
    fun buildsAndRemovesSignals() {
        val game = CityGame(straightRoad(), config)
        game.submit(GameCommand.BuildSignal(node = 1))
        game.step()
        assertEquals(setOf(1), game.state().signalNodes)
        assertEquals(2, game.simulation.signals.controlledLinks.size)

        game.submit(GameCommand.RemoveSignal(node = 1))
        game.step()
        assertEquals(emptySet(), game.state().signalNodes)
    }

    @Test
    fun termEndsWithScoreAndNewspaper() {
        val lateEvening = config.copy(startSecondOfDay = 86_400.0 - 600)
        val game = CityGame(straightRoad(), lateEvening, GameRules(termDays = 1))
        game.submit(GameCommand.SetSpeedLimit(segment = 0, kmh = 20))
        game.submit(GameCommand.PlaceCamera(segment = 0))

        game.run(minutes = 15)

        val state = game.state()
        assertTrue(state.isTermOver)
        assertTrue(game.isFinished)
        assertEquals(1, state.daysPlayed)
        assertTrue(state.latestEdition?.headlines?.isNotEmpty() == true)
        assertEquals((state.balance * state.score.reputationFactor).toLong(), state.score.points)
        // Nach dem Ende passiert nichts mehr
        val tick = game.simulation.clock.tick
        game.step()
        assertEquals(tick, game.simulation.clock.tick)
    }

    @Test
    fun formatsEuro() {
        assertEquals("1.234.567 €", formatEuro(1_234_567))
        assertEquals("-500 €", formatEuro(-500))
    }
}
