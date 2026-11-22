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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Läuft in echter Zeit: Grenzen großzügig, damit ein ausgelasteter Rechner den Test nicht kippt. */
class GameRunnerTest {
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

    private val config = SimulationConfig(seed = 3, peakTripsPerHour = 1_000.0, demandProfile = DemandProfile.CONSTANT)

    @Test
    fun publishesAtMostAtDisplayRateEvenAtHighSpeed() =
        runBlocking {
            val runner = GameRunner(CityGame(road(), config))
            runner.setSpeed(GameRunner.MAX_SPEED)
            val job = launch { runner.run() }
            val frames = HashSet<TrafficFrame>()
            val watcher = launch { runner.frames.collect { if (it != null) frames += it } }
            delay(1_000)
            job.cancel()
            watcher.cancel()
            // Ohne Drosselung wären es über 100 Bilder (alle 8 ms eins)
            assertTrue(frames.size in 10..75, "${frames.size} Bilder in einer Sekunde")
        }

    @Test
    fun aPlayerActionShowsUpRightAwayNotOnlyAfterTheStatePeriod() =
        runBlocking {
            val game = CityGame(road(), config)
            val runner = GameRunner(game)
            val job = launch { runner.run() }
            runner.frames.filterNotNull().first()
            delay(300)
            game.submit(GameCommand.SetSpeedLimit(segment = 0, kmh = 30))
            withTimeout(150) { runner.frames.filterNotNull().first { 0 in it.game.speedLimits } }
            job.cancel()
        }

    @Test
    fun aPlayerActionWorksWhilePaused() =
        runBlocking {
            val game = CityGame(road(), config)
            val runner = GameRunner(game)
            runner.setSpeed(0)
            val job = launch { runner.run() }
            runner.frames.filterNotNull().first()
            delay(300)
            // In der Pause rechnet die Simulation keinen Schritt – die Aktion muss trotzdem sofort wirken
            game.submit(GameCommand.SetSpeedLimit(segment = 0, kmh = 30))
            withTimeout(150) { runner.frames.filterNotNull().first { 0 in it.game.speedLimits } }
            job.cancel()
        }

    @Test
    fun nightBoostIsCapped() =
        runBlocking {
            val night = config.copy(startSecondOfDay = 23.0 * 3_600)
            val runner = GameRunner(CityGame(road(), night))
            runner.setSpeed(GameRunner.MAX_SPEED)
            val job = launch { runner.run() }
            val frame = withTimeout(2_000) { runner.frames.filterNotNull().first { it.effectiveSpeed > 0 } }
            job.cancel()
            assertEquals(GameRunner.NIGHT_MAX, frame.effectiveSpeed)
        }

    @Test
    fun speedStepsWalkThroughTheList() {
        val runner = GameRunner(CityGame(road(), config))
        runner.setSpeed(8)
        runner.stepSpeed(1)
        assertEquals(16, runner.speed.value)
        runner.setSpeed(GameRunner.MAX_SPEED)
        runner.stepSpeed(1)
        assertEquals(GameRunner.MAX_SPEED, runner.speed.value)
        runner.setSpeed(1)
        runner.stepSpeed(-1)
        assertEquals(1, runner.speed.value)
    }

    @Test
    fun anAchievementBrakesHighSpeedAndResumeRestoresIt() =
        runBlocking {
            val game = CityGame(road(), config)
            val runner = GameRunner(game)
            runner.setSpeed(32)
            val job = launch { runner.run() }
            runner.frames.filterNotNull().first()
            game.raiseAlert()
            val braked = withTimeout(2_000) { runner.frames.filterNotNull().first { it.brakedFrom != null } }
            assertEquals(32, braked.brakedFrom)
            assertEquals(GameRunner.BRAKE_TO, runner.speed.value)
            runner.resumeSpeed()
            assertEquals(32, runner.speed.value)
            job.cancel()
        }

    @Test
    fun fastForwardStopsAtTheNextEventAndReturnsToTheOldSpeed() =
        runBlocking {
            val game = CityGame(road(), config)
            val runner = GameRunner(game)
            runner.autoBrake = false
            runner.setSpeed(2)
            runner.fastForward()
            assertEquals(GameRunner.MAX_SPEED, runner.speed.value)
            val job = launch { runner.run() }
            runner.frames.filterNotNull().first()
            game.raiseAlert()
            withTimeout(2_000) { runner.speed.first { it == 2 } }
            job.cancel()
        }
}
