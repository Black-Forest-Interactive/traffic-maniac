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

class SignalTimingTest {
    /** Allee mit zwei Ampelkreuzungen (Knoten 1 und 2, 500 m auseinander) und je einer Querstraße. */
    private fun avenue(): CityMap {
        val nodes =
            listOf(
                RoadNode(0, -1000f, 0f),
                RoadNode(1, 0f, 0f, trafficSignals = true),
                RoadNode(2, 500f, 0f, trafficSignals = true),
                RoadNode(3, 1500f, 0f),
                RoadNode(4, 0f, 300f),
                RoadNode(5, 500f, 300f),
            )

        fun road(
            id: Int,
            from: Int,
            to: Int,
            roadClass: RoadClass,
        ) = RoadSegment(
            id = id,
            from = from,
            to = to,
            roadClass = roadClass,
            geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
            lanesForward = 1,
            lanesBackward = 1,
            maxSpeedKmh = 50,
            name = if (roadClass == RoadClass.PRIMARY) "Allee" else "Quergasse",
        )
        return CityMap(
            name = "Test",
            attribution = "",
            origin = GeoPoint(0.0, 0.0),
            bounds = Bounds(-1000f, -100f, 1500f, 300f),
            roads =
                RoadNetwork(
                    nodes,
                    listOf(
                        road(0, 0, 1, RoadClass.PRIMARY),
                        road(1, 1, 2, RoadClass.PRIMARY),
                        road(2, 2, 3, RoadClass.PRIMARY),
                        road(3, 4, 1, RoadClass.RESIDENTIAL),
                        road(4, 5, 2, RoadClass.RESIDENTIAL),
                    ),
                ),
            scenery = Scenery(),
            zones =
                listOf(
                    Zone(0, ZoneKind.RESIDENTIAL, -1000f, 0f, area = 10_000f, node = 0),
                    Zone(1, ZoneKind.COMMERCIAL, 1500f, 0f, area = 10_000f, node = 3),
                ),
        )
    }

    private val config = SimulationConfig(seed = 4, peakTripsPerHour = 600.0, demandProfile = DemandProfile.CONSTANT)

    @Test
    fun greenWaveCostsOneProgrammingPerSignalAndShowsOnTheMap() {
        val game = CityGame(avenue(), config)
        game.submit(GameCommand.SetGreenWave(listOf(0, 1, 2), kmh = 50))
        game.step()

        val state = game.state()
        assertEquals(2 * game.rules.signalProgramCost, state.expenses)
        assertEquals(listOf(listOf(0, 1, 2)), state.greenWaves)
        assertTrue(state.messages.any { it.text.startsWith("Grüne Welle 50 km/h: Allee") }, "${state.messages}")
    }

    @Test
    fun splittingTheGreenIsCheapAndOnlyChargedWhenSomethingChanges() {
        val game = CityGame(avenue(), config)
        game.submit(GameCommand.SetSignalProgram(1, 0.7))
        game.submit(GameCommand.SetSignalProgram(1, 0.7))
        game.submit(GameCommand.SetSignalProgram(0, 0.7))
        game.step()

        assertEquals(game.rules.signalProgramCost, game.state().expenses)
        assertEquals(
            0.7,
            game.simulation.signals
                .program(1)
                ?.mainShare,
        )
        assertTrue(game.state().messages.any { it.text == "Hier ist keine Ampel" })
    }
}
