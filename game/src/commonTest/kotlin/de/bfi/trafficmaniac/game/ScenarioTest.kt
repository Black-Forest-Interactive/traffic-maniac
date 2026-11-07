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
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.sim.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenarioTest {
    private fun town(): CityMap {
        val nodes = (0..3).map { RoadNode(it, -1500f + it * 1000f, 0f) }
        val segments =
            (0..2).map {
                RoadSegment(
                    id = it,
                    from = it,
                    to = it + 1,
                    roadClass = RoadClass.PRIMARY,
                    geometry = Polyline(floatArrayOf(nodes[it].x, 0f, nodes[it + 1].x, 0f)),
                    lanesForward = 1,
                    lanesBackward = 1,
                    maxSpeedKmh = 50,
                    name = "Hauptstraße",
                )
            }
        return CityMap(
            name = "Test",
            attribution = "",
            origin = GeoPoint(0.0, 0.0),
            bounds = Bounds(-1500f, -100f, 1500f, 100f),
            roads = RoadNetwork(nodes, segments),
            scenery = Scenery(),
            zones =
                listOf(
                    Zone(0, ZoneKind.RESIDENTIAL, -1500f, 0f, area = 10_000f, node = 0),
                    Zone(1, ZoneKind.RETAIL, 0f, 0f, area = 5_000f, node = 1),
                ),
        )
    }

    private val base = SimulationConfig(peakTripsPerHour = 0.0, demandProfile = DemandProfile.CONSTANT)

    private fun scenario(id: String) = Scenario.DEFAULTS.single { it.id == id }

    @Test
    fun aSaturdayScenarioReallyStartsOnSaturday() {
        val (config, rules) = scenario("adventssamstag").apply(base, GameRules(), Difficulty.NORMAL)
        val game = CityGame(town(), config, rules)
        val start = game.simulation.gameTime
        assertEquals(Weekday.SATURDAY, start.weekday)
        assertEquals(9, start.hour)
        assertEquals(7, rules.termDays)
        assertEquals(1, game.state().termDay(start.day), "Der erste Spieltag ist Tag 1 der Amtszeit")
    }

    @Test
    fun theCalendarFollowsTheScenarioStartDay() {
        val (config, rules) = scenario("adventssamstag").apply(base, GameRules(), Difficulty.NORMAL)
        val game = CityGame(town(), config, rules)
        val firstDay = game.simulation.gameTime.day
        val calendar = game.state().events.calendar
        assertTrue(calendar.all { it.day in firstDay until firstDay + rules.termDays }, "Nur Tage der Amtszeit")
        val markets = calendar.filter { it.kind == EventKind.MARKET }.map { it.weekday }
        assertEquals(listOf(Weekday.SATURDAY, Weekday.WEDNESDAY), markets)
        assertEquals(Weekday.SUNDAY, calendar.single { it.kind == EventKind.MARATHON }.weekday)
        assertEquals(Weekday.SATURDAY, calendar.single { it.kind == EventKind.CITY_FESTIVAL }.weekday)
    }

    @Test
    fun aWorkingWeekHasNoMarathonAndNoFestival() {
        val (config, rules) = scenario("berufsverkehr").apply(base, GameRules(), Difficulty.NORMAL)
        val calendar = CityGame(town(), config, rules).state().events.calendar
        assertTrue(calendar.none { it.kind == EventKind.MARATHON || it.kind == EventKind.CITY_FESTIVAL })
    }

    @Test
    fun difficultyChangesMoneyAndPatience() {
        val scenario = scenario("erste-amtszeit")
        val easy = scenario.apply(base, GameRules(), Difficulty.EASY).second
        val hard = scenario.apply(base, GameRules(), Difficulty.HARD).second
        assertTrue(easy.startBudget > hard.startBudget)
        assertTrue(easy.angerThreshold > hard.angerThreshold, "Leicht: Die Bürger halten mehr aus")
        assertTrue(easy.recallDivisor > hard.recallDivisor, "Leicht: Der Abwahlindex steigt langsamer")
    }

    @Test
    fun scenariosOverrideOnlyWhatTheySet() {
        val (config, rules) = scenario("brueckensanierung").apply(base, GameRules(), Difficulty.NORMAL)
        assertEquals(0.25, config.initialConditionMin)
        assertEquals(400_000, rules.startBudget)
        assertEquals(base.truckShare, config.truckShare)
        assertEquals(GameRules().weeklyAllowance, rules.weeklyAllowance)
    }

    @Test
    fun highscoresKeepTheBestTenPerScenarioAndDifficulty() {
        var scores = Highscores()
        repeat(12) { scores = scores.add(entry("erste-amtszeit", Difficulty.NORMAL, points = it * 100L)) }
        scores = scores.add(entry("erste-amtszeit", Difficulty.HARD, points = 5))
        val normal = scores.of("erste-amtszeit", Difficulty.NORMAL)
        assertEquals(Highscores.MAX_PER_TABLE, normal.size)
        assertEquals(1_100, normal.first().points)
        assertEquals(1, scores.of("erste-amtszeit", Difficulty.HARD).size, "Andere Tabelle bleibt unberührt")
        val low = entry("erste-amtszeit", Difficulty.NORMAL, points = 50)
        assertNull(scores.add(low).rankOf(low), "Zu wenig für die Bestenliste")
        assertEquals(1_100, scores.best("erste-amtszeit")?.points)
    }

    private fun entry(
        scenario: String,
        difficulty: Difficulty,
        points: Long,
    ) = HighscoreEntry(scenario, difficulty, points, points, 50, "2026-11-07")
}
