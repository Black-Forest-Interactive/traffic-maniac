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

class CameraHabitsTest {
    private fun road(): CityMap {
        val nodes = listOf(RoadNode(0, -1000f, 0f), RoadNode(1, 1000f, 0f))
        val segment =
            RoadSegment(
                id = 0,
                from = 0,
                to = 1,
                roadClass = RoadClass.SECONDARY,
                geometry = Polyline(floatArrayOf(-1000f, 0f, 1000f, 0f)),
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
            roads = RoadNetwork(nodes, listOf(segment)),
            scenery = Scenery(),
            zones =
                listOf(
                    Zone(0, ZoneKind.RESIDENTIAL, -1000f, 0f, area = 10_000f, node = 0),
                    Zone(1, ZoneKind.COMMERCIAL, 1000f, 0f, area = 10_000f, node = 1),
                ),
        )
    }

    private val config = SimulationConfig(seed = 9, peakTripsPerHour = 600.0, demandProfile = DemandProfile.CONSTANT)

    @Test
    fun fixedCamerasBecomeKnownAndWarnerAppsSpread() {
        val game = CityGame(road(), config.copy(startSecondOfDay = 86_400.0 - 60))
        game.submit(GameCommand.PlaceCamera(segment = 0))
        repeat(2 * 60 * config.ticksPerSecond) { game.step() }
        val state = game.state()
        assertEquals((game.rules.habits.familiarityPerDay * 100).toInt(), state.cameras.single().familiarityPercent)
        assertEquals(game.rules.habits.warnerShareStart + game.rules.habits.warnerGrowthPerDay, state.warnerShare, 1e-9)
    }

    @Test
    fun theWarnerCampaignCutsTheShareEarnsFinesAndWaitsAWeek() {
        val game = CityGame(road(), config)
        val before = game.state().warnerShare
        game.submit(GameCommand.WarnerCampaign)
        game.step()
        val state = game.state()
        assertEquals(before * (1 - game.rules.habits.campaignReduction), state.warnerShare, 1e-9)
        assertTrue(state.fineIncome > 0, "Treffer bringen Bußgeld")
        assertTrue("Handy-Razzia" in state.achievements)
        assertEquals(game.rules.habits.campaignPauseDays, state.campaignInDays)

        val spent = state.expenses
        game.submit(GameCommand.WarnerCampaign)
        game.step()
        assertEquals(spent, game.state().expenses, "Erst nächste Woche wieder")
    }

    @Test
    fun aMarathonRunsForADayAndThenLowersTheShare() {
        // Ohne Abwahl – die Teststadt hat keine Parkplätze, nach einem Tag wäre man sonst raus
        val game = CityGame(road(), config.copy(startSecondOfDay = 6 * 3_600.0), GameRules(recallDivisor = 1e9))
        val before = game.state().warnerShare
        game.submit(GameCommand.AnnounceMarathon)
        game.step()
        assertTrue(game.state().marathonActive)
        assertTrue(game.simulation.speedControl.marathon)
        repeat(((game.rules.habits.marathonHours * 3_600).toInt() + 60) * config.ticksPerSecond) { game.step() }
        assertFalse(game.state().marathonActive)
        assertTrue(game.state().warnerShare < before + game.rules.habits.warnerGrowthPerDay)
    }

    @Test
    fun aDummyIsCheapAndMeasuresNothing() {
        val game = CityGame(road(), config)
        game.submit(GameCommand.PlaceCamera(segment = 0, dummy = true))
        repeat(20 * 60 * config.ticksPerSecond) { game.step() }
        val state = game.state()
        assertEquals(game.rules.habits.dummyCost, state.expenses)
        assertTrue(state.cameras.single().dummy)
        assertEquals(0, state.cameras.single().totalCount)
    }
}
