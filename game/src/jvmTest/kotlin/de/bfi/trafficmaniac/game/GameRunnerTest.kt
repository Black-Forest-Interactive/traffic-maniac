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
}
