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
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.sim.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventsAndPressTest {
    /** Hauptstraße West–Ost über drei Kilometer; Wohngebiet im Westen, Laden in der Mitte, Gewerbe im Osten. */
    private fun mainStreet(): CityMap {
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
                    Zone(2, ZoneKind.COMMERCIAL, 1500f, 0f, area = 10_000f, node = 3),
                    Zone(3, ZoneKind.GATE, -1500f, 0f, area = 1f, node = 0),
                    Zone(4, ZoneKind.GATE, 1500f, 0f, area = 1f, node = 3),
                ),
        )
    }

    private val quiet =
        SimulationConfig(
            seed = 1,
            peakTripsPerHour = 0.0,
            demandProfile = DemandProfile.CONSTANT,
            startSecondOfDay = 10 * GameTime.SECONDS_PER_HOUR,
        )

    private fun planner(rules: GameRules = GameRules()): Pair<EventPlanner, Simulation> {
        val simulation = Simulation(mainStreet(), quiet)
        return EventPlanner(mainStreet(), simulation, rules) {} to simulation
    }

    private fun moods(rules: GameRules = GameRules()) = Citizens(mainStreet().zones, rules).moods(false)

    // --- Ereignisse -----------------------------------------------------------------------------------------

    @Test
    fun plannedEventsAreVisibleInTheCalendarAhead() {
        val (planner, _) = planner()
        val calendar = planner.state().calendar
        val markets = calendar.filter { it.kind == EventKind.MARKET }
        assertEquals(setOf(Weekday.WEDNESDAY, Weekday.SATURDAY), markets.map { it.weekday }.toSet())
        assertEquals(4, markets.size, "Zwei Wochen, zwei Markttage je Woche")
        assertEquals(Weekday.SUNDAY, calendar.single { it.kind == EventKind.MARATHON }.weekday)
        assertEquals(12, calendar.single { it.kind == EventKind.CITY_FESTIVAL }.day)
        assertTrue(calendar.all { it.segments.isNotEmpty() && it.place.isNotBlank() })
    }

    @Test
    fun corsosComeOnWeekendsAfterPersistentJamsAndWithManyBannedDrivers() {
        val (planner, _) = planner()
        val weekday = planner.corsoChance(Weekday.TUESDAY, bannedDrivers = 0)
        assertEquals(0.0, weekday, "Normaler Werktag ohne Stau: kein Korso")
        assertTrue(planner.corsoChance(Weekday.SATURDAY, 0) > weekday)
        assertTrue(planner.corsoChance(Weekday.TUESDAY, 200) > weekday)
        val jam = CommuteStats(trips = 100, late = 40)
        repeat(3) { planner.dayEnded(jam) }
        assertEquals(3, planner.state().jamStreak)
        assertTrue(planner.corsoChance(Weekday.TUESDAY, 0) > 0.1, "Nach drei Dauerstau-Tagen")
        planner.dayEnded(CommuteStats(trips = 100, late = 1))
        assertEquals(0, planner.state().jamStreak, "Ein flüssiger Tag beendet die Serie")
    }

    @Test
    fun aCorsoBlocksTheMainStreet() {
        val (planner, simulation) = planner(GameRules(corsoChanceBanned = 1.0, corsoBannedReference = 1))
        planner.hourPassed(anger = 0.0, bannedDrivers = 5, moods = moods())
        val corso = simulation.events.active.single { it.kind == EventKind.BIKE_CORSO }
        assertTrue(corso.segments.size >= 2, "Der Korso fährt über die Hauptstraße")
        assertEquals(1, planner.state().totals[EventKind.BIKE_CORSO])
        assertEquals("Hauptstraße", planner.today.corsos.single())
    }

    @Test
    fun demosGetMoreFrequentWithAnger() {
        val (planner, _) = planner()
        assertEquals(0.0, planner.demoChance(10.0))
        assertTrue(planner.demoChance(50.0) > 0)
        assertTrue(planner.demoChance(90.0) > planner.demoChance(50.0) * 2, "Überproportional mehr Demos")
    }

    @Test
    fun anAngryCityGetsADemoInFrontOfItsUnhappiestDistrict() {
        val (planner, simulation) = planner(GameRules(demoChancePerHour = 1.0))
        planner.hourPassed(anger = 100.0, bannedDrivers = 0, moods = moods())
        val demo = simulation.events.active.single { it.kind == EventKind.DEMO }
        assertEquals(3, demo.segments.size, "Bei Zorn 100 sperrt die Demo alles, was die Straße hergibt")
        assertTrue(demo.endTime > simulation.time + GameTime.SECONDS_PER_HOUR)
        // Höchstens eine Demo gleichzeitig
        planner.hourPassed(anger = 100.0, bannedDrivers = 0, moods = moods())
        assertEquals(1, simulation.events.active.count { it.kind == EventKind.DEMO })
    }

    // --- Presse & Reputation --------------------------------------------------------------------------------

    private fun press(rules: GameRules = GameRules()) = Press(rules, Treasury(rules.startBudget) {}) {}

    @Test
    fun withPrThePressIsFriendlyAndWithoutItTurnsQuickly() {
        val press = press()
        repeat(3) { press.dayEnded(PrContract.PREMIUM, unhappyShare = 0.3) }
        assertTrue(press.tone > 0.6, "Mit PR: Jubelpresse (${press.tone})")
        assertEquals("Jubelpresse", Press.toneLabel(press.tone))
        val reputation = press.dayEnded(PrContract.NONE, unhappyShare = 0.3)
        assertTrue(press.tone < 0, "Einen Tag nach der Kündigung kritisch (${press.tone})")
        assertTrue(reputation < 0, "Kritische Presse kostet Image")
    }

    @Test
    fun evenBoughtPressTurnsWhenEveryoneSuffers() {
        val press = press()
        repeat(5) { press.dayEnded(PrContract.PREMIUM, unhappyShare = 0.95) }
        assertTrue(press.tone < -0.5, "Bei flächendeckendem Leid hilft keine Story (${press.tone})")
    }

    @Test
    fun prDampensTheRecallIndexButNotWhenEveryoneSuffers() {
        fun recall(
            share: Double,
            pr: PrContract,
        ) = Politics(GameRules()).run {
            repeat(12) { hourPassed(share, pr, argumentsCampaign = false, finesLastHour = 0) }
            recallIndex
        }
        assertTrue(recall(0.6, PrContract.PREMIUM) < recall(0.6, PrContract.NONE) * 0.8)
        assertEquals(recall(0.95, PrContract.NONE), recall(0.95, PrContract.PREMIUM))
    }

    @Test
    fun prGetsExpensiveWhenStoryAndRealityDrift() {
        val honest = press().apply { repeat(24) { hourPassed(0.2) } }
        assertEquals(PrContract.STANDARD.weeklyCost, honest.weeklyPrCost(PrContract.STANDARD))
        val spin = press().apply { repeat(24) { hourPassed(0.7) } }
        assertTrue(spin.weeklyPrCost(PrContract.STANDARD) > PrContract.STANDARD.weeklyCost * 2)
        assertEquals(PrContract.STANDARD.weeklyCost, spin.weeklyPrCost(PrContract.STANDARD), "Neue Woche, neues Glück")
    }

    @Test
    fun groundbreakingBuysReputationOncePerDayWithDiminishingReturns() {
        val rules = GameRules()
        val press = press(rules)
        val first = press.ceremony(MaintenanceState()) {}
        assertEquals(rules.ceremonyReputation, first)
        assertEquals(0.0, press.ceremony(MaintenanceState()) {}, "Nur ein Spatenstich am Tag")
        press.dayEnded(PrContract.STANDARD, 0.2)
        assertTrue(press.startNewDay().single().startsWith("Spatenstich"), "Schlagzeile für die Zeitung")
        val second = press.ceremony(MaintenanceState()) {}
        assertTrue(second > 0 && second < first, "Der zweite Spatenstich der Woche bringt weniger")
    }

    @Test
    fun aRepairedStreetIsReopenedWithRibbonCutting() {
        val press = press()
        var reopened = false
        press.ceremony(MaintenanceState(lastRepaired = "Goethestraße")) { reopened = true }
        assertTrue(reopened)
        assertTrue("Wiedereröffnung: Goethestraße" in press.startNewDay().single())
    }

    @Test
    fun theCeremonyCommandRaisesReputation() {
        val game = CityGame(mainStreet(), quiet)
        val before = game.state().reputation
        game.submit(GameCommand.HoldCeremony)
        game.step()
        assertTrue(game.state().reputation > before)
        assertEquals(null, game.state().press.nextCeremony, "Heute schon gefeiert")
    }

    // --- Achievements ---------------------------------------------------------------------------------------

    @Test
    fun atLeastTenAchievementsAreReachable() {
        val achievements = Achievements {}
        achievements.check(AchievementFacts(), day = 0)
        assertTrue(achievements.titles.isEmpty(), "Nichts geschenkt")
        val extreme =
            AchievementFacts(
                activeVehicles = 1_000,
                meanSpeedKmh = 1.0,
                bridgesOpen = 0,
                signForest = true,
                cameras = 12,
                collapses = 1,
                plannings = 3,
                corsos = 1,
                revocations = 30,
                lawsuits = 12,
                reputation = 95.0,
                lateShare = 0.6,
                lettersToday = 2_000,
                demosToday = 3,
                recordRatio = 2.5,
                trucksStuck = 1,
            )
        achievements.check(extreme, day = 3)
        assertTrue(achievements.titles.size >= 10)
        assertEquals(Achievement.entries.size, achievements.state().count { it.unlocked && it.unlockedDay == 3 })
    }
}
