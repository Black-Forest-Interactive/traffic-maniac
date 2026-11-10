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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinterServiceTest {
    private fun straightRoad(): CityMap {
        val nodes = listOf(RoadNode(0, -1000f, 0f), RoadNode(1, 1000f, 0f))
        val road =
            RoadSegment(
                0,
                0,
                1,
                RoadClass.PRIMARY,
                Polyline(floatArrayOf(-1000f, 0f, 1000f, 0f)),
                1,
                1,
                50,
                "Teststraße",
            )
        return CityMap(
            "Test",
            "",
            GeoPoint(0.0, 0.0),
            Bounds(-1000f, -100f, 1000f, 100f),
            RoadNetwork(nodes, listOf(road)),
            Scenery(),
            listOf(
                Zone(0, ZoneKind.RESIDENTIAL, -1000f, 0f, area = 10_000f, node = 0),
                Zone(1, ZoneKind.COMMERCIAL, 1000f, 0f, area = 10_000f, node = 1),
            ),
        )
    }

    private val config = SimulationConfig(seed = 3, peakTripsPerHour = 200.0, demandProfile = DemandProfile.CONSTANT)

    @Test
    fun theSnowScenarioBringsWeatherAndAWinterService() {
        val scenario = Scenario.DEFAULTS.single { it.id == "erster-schnee" }
        val (simConfig, rules) = scenario.apply(config, GameRules(), Difficulty.NORMAL)
        val game = CityGame(straightRoad(), simConfig, rules)

        // Start 5:30, Schnee ab 6 Uhr
        repeat(45 * 60 * simConfig.ticksPerSecond) { game.step() }

        val state = game.state()
        assertTrue(state.winterService)
        assertEquals(3, state.ploughs)
        assertTrue(state.messages.any { it.text.startsWith("Es schneit!") }, "${state.messages}")

        game.submit(GameCommand.SetPloughs(5))
        game.step()
        assertEquals(5, game.state().ploughs)
    }

    @Test
    fun withoutWeatherThereIsNoWinterService() {
        val game = CityGame(straightRoad(), config)
        game.submit(GameCommand.SetPloughs(5))
        game.step()
        assertFalse(game.state().winterService)
        assertEquals(0, game.state().ploughs)
    }
}
