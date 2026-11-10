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

class SurveillanceTest {
    /** Kreuzung mit Ampel (Knoten 0), Wohngebiete im Westen und Osten, Gewerbe im Norden und Süden. */
    private fun crossroads(): CityMap {
        val nodes =
            listOf(
                RoadNode(0, 0f, 0f, trafficSignals = true),
                RoadNode(1, -500f, 0f),
                RoadNode(2, 500f, 0f),
                RoadNode(3, 0f, 500f),
                RoadNode(4, 0f, -500f),
            )

        fun arm(
            id: Int,
            outer: Int,
            roadClass: RoadClass,
        ) = RoadSegment(
            id,
            outer,
            0,
            roadClass,
            Polyline(floatArrayOf(nodes[outer].x, nodes[outer].y, 0f, 0f)),
            1,
            1,
            50,
            "Arm $id",
        )
        return CityMap(
            "Test",
            "",
            GeoPoint(0.0, 0.0),
            Bounds(-500f, -500f, 500f, 500f),
            RoadNetwork(
                nodes,
                listOf(
                    arm(0, 1, RoadClass.PRIMARY),
                    arm(1, 2, RoadClass.PRIMARY),
                    arm(2, 3, RoadClass.SECONDARY),
                    arm(3, 4, RoadClass.SECONDARY),
                ),
            ),
            Scenery(),
            listOf(
                Zone(0, ZoneKind.RESIDENTIAL, -500f, 0f, area = 10_000f, node = 1),
                Zone(1, ZoneKind.RESIDENTIAL, 500f, 0f, area = 10_000f, node = 2),
                Zone(2, ZoneKind.COMMERCIAL, 0f, 500f, area = 10_000f, node = 3),
                Zone(3, ZoneKind.COMMERCIAL, 0f, -500f, area = 10_000f, node = 4),
            ),
        )
    }

    private val config =
        SimulationConfig(
            seed = 5,
            peakTripsPerHour = 2_000.0,
            demandProfile = DemandProfile.CONSTANT,
            redLightRunnerShare = 0.4,
        )

    @Test
    fun aRedLightCameraEarnsRedLightFinesOnly() {
        val game = CityGame(crossroads(), config)
        val west =
            game.simulation.graph.inLinks[0]
                .single { game.simulation.graph.linkFrom[it] == 1 }
        game.submit(GameCommand.PlaceRedLightCamera(west, withSpeed = false))
        repeat(40 * 60 * config.ticksPerSecond) { game.step() }

        val camera = game.state().cameras.single()
        assertTrue(camera.redLight)
        assertEquals(game.rules.redLightCameraCost, game.state().expenses)
        val red = FineTier.entries.filter { it.isRedLight }.sumOf { camera.countPerTier[it.ordinal] }
        val speed = FineTier.SPEED.sumOf { camera.countPerTier[it.ordinal] }
        assertTrue(red > 0, "Rotlichtfotos: ${camera.countPerTier}")
        assertEquals(0, speed, "Ohne Tempomessung keine Tempofotos")
    }

    @Test
    fun aDisplayCountsAndCostsLittle() {
        val game = CityGame(crossroads(), config)
        game.submit(GameCommand.SetSpeedLimit(0, 30))
        game.submit(GameCommand.PlaceDisplay(0))
        repeat(30 * 60 * config.ticksPerSecond) { game.step() }

        val display = game.state().cameras.single()
        assertTrue(display.display)
        assertTrue(display.passed > 0 && display.speedingShare > 0.0, "${display.speeding}/${display.passed}")
        assertEquals(0, display.totalCount, "Keine Fotos")
        assertEquals(game.rules.speedLimitSignCost + game.rules.displayCost, game.state().expenses)
    }
}
