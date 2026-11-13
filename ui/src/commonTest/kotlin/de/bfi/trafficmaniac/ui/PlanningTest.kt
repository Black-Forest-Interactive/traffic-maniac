package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.GeoPoint
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.Scenery
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.StreetRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanningTest {
    /**
     * Hauptstraße von x = 0 bis 1000 (Knoten 0–5, alle 200 m eine Kreuzung mit einer kurzen Seitenstraße nach Norden),
     * zwischen Knoten 1 und 4 in einem Bogen nach Norden (700 m). Darunter der „Schleichweg“ von Knoten 1 nach 4 –
     * mit knapp 680 m kürzer. Ein Tempolimit „von hier bis da“ soll trotzdem auf der Hauptstraße bleiben.
     */
    private fun town(): CityMap {
        val bend = listOf(0f, 0f, 150f, 150f, 0f, 0f)
        val main = (0..5).map { RoadNode(it, it * 200f, bend[it]) }
        val side = (1..4).map { RoadNode(5 + it, it * 200f, bend[it] + 100f) }
        val shortcut = listOf(RoadNode(10, 400f, -150f))
        val nodes = main + side + shortcut
        var id = 0

        fun road(
            from: Int,
            to: Int,
            name: String,
        ) = RoadSegment(
            id = id++,
            from = from,
            to = to,
            roadClass = RoadClass.SECONDARY,
            geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
            lanesForward = 1,
            lanesBackward = 1,
            maxSpeedKmh = 50,
            name = name,
        )
        val segments =
            (0 until 5).map { road(it, it + 1, "Hauptstraße") } +
                (1..4).map { road(it, 5 + it, "Seitenweg $it") } +
                listOf(road(1, 10, "Schleichweg"), road(10, 4, "Schleichweg"))
        return CityMap(
            name = "Test",
            attribution = "",
            origin = GeoPoint(0.0, 0.0),
            bounds = Bounds(0f, -200f, 1000f, 200f),
            roads = RoadNetwork(nodes, segments),
            scenery = Scenery(),
        )
    }

    private val city = town()
    private val picker = MapPicker(city)
    private val rules = GameRules()
    private val context = PlanContext(city, picker, StreetRoute(city), rules, null)
    private val limit30 = ToolSelection(tool = Tool.SPEED_LIMIT, speedLimit = 30)

    /** Klick mitten auf Abschnitt [segment] der Hauptstraße. */
    private fun Plan.clickMain(
        segment: Int,
        selection: ToolSelection = limit30,
    ) = picker.midpoint(segment).let { (x, y) -> click(selection, context, x, y, radius = 10f) }

    @Test
    fun speedLimitFromStartToEndCoversEveryStretchInBetween() {
        val plan = Plan()
        plan.clickMain(0)
        assertEquals(0, plan.actions.size, "Erst der Anfang")
        assertEquals(0, plan.routeStart)

        plan.clickMain(4)

        val action = plan.actions.single()
        val segments = action.commands.map { (it as GameCommand.SetSpeedLimit).segment }.sorted()
        assertEquals(listOf(0, 1, 2, 3, 4), segments, "Auf der Hauptstraße geblieben, nicht über den Schleichweg")
        assertEquals(5 * rules.speedLimitSignCost, plan.total)
        assertEquals(null, plan.routeStart)
    }

    @Test
    fun sameStretchTwiceIsJustThatStretchAndPlanningItAgainTakesItOut() {
        val plan = Plan()
        plan.clickMain(2)
        plan.clickMain(2)
        assertEquals(listOf(GameCommand.SetSpeedLimit(2, 30, false)), plan.actions.single().commands)

        plan.clickMain(2)
        plan.clickMain(2)
        assertTrue(plan.actions.isEmpty(), "Dieselbe Planung noch einmal nimmt sie heraus")
    }

    @Test
    fun anotherValueForTheSameStretchReplacesThePlan() {
        val plan = Plan()
        plan.clickMain(1)
        plan.clickMain(1)
        val fifty = limit30.copy(speedLimit = 50)
        plan.clickMain(1, fifty)
        plan.clickMain(1, fifty)
        assertEquals(listOf(GameCommand.SetSpeedLimit(1, 50, false)), plan.actions.single().commands)
    }

    @Test
    fun removeToolStrikesAPlannedCameraAndBuildingSendsEverythingInOrder() {
        val plan = Plan()
        plan.clickMain(3, ToolSelection.of(ToolKind.CAMERA_FIXED))
        plan.clickMain(1, ToolSelection.of(ToolKind.PRIORITY))
        plan.clickMain(3, ToolSelection.of(ToolKind.REMOVE))
        assertEquals(1, plan.actions.size, "Der geplante Blitzer ist gestrichen")

        plan.clickMain(2, ToolSelection.of(ToolKind.CAMERA_FIXED))
        val commands = plan.take()

        assertEquals(listOf(GameCommand.SetPriorityRoad(1, true), GameCommand.PlaceCamera(2)), commands)
        assertTrue(!plan.isActive, "Nach dem Bauen ist die Planung leer")
    }

    @Test
    fun greenWaveIsPlannedFromStartToEndLikeASpeedLimit() {
        val plan = Plan()
        val wave = ToolSelection.of(ToolKind.WAVE_50)
        plan.clickMain(0, wave)
        plan.clickMain(4, wave)

        assertEquals(listOf(GameCommand.SetGreenWave(listOf(0, 1, 2, 3, 4), 50)), plan.actions.single().commands)
    }

    @Test
    fun roadworksArePlannedPerStretchAndCostPerStretch() {
        val plan = Plan()
        val works = ToolSelection.of(ToolKind.ROADWORKS)
        plan.clickMain(1, works)
        plan.clickMain(2, works)

        assertEquals(listOf(GameCommand.SetRoadworks(listOf(1, 2), true)), plan.actions.single().commands)
        assertEquals(2 * rules.roadworksSetupCost, plan.total)

        val closure = works.with(ToolKind.CLOSURE)
        plan.clickMain(4, closure)
        plan.clickMain(4, closure)
        assertEquals(
            GameCommand.SetRoadClosure(listOf(4), true),
            plan.actions
                .last()
                .commands
                .single(),
        )
    }

    @Test
    fun aGroupRemembersItsKindAndTheKeyCyclesThroughIt() {
        val camera = ToolSelection.of(ToolKind.POLICE_CHECK).copy(tool = Tool.LOOK)
        assertEquals(ToolKind.LOOK, camera.kind)
        assertEquals(ToolKind.POLICE_CHECK, camera.copy(tool = Tool.CAMERA).kind, "Die Gruppe merkt sich die Art")
        // Nach der Polizeikontrolle kommt der Busspur-Blitzer, danach wieder der feste Blitzer
        val busLane = camera.copy(tool = Tool.CAMERA).next()
        assertEquals(ToolKind.BUS_LANE_CAMERA, busLane.kind)
        assertEquals(ToolKind.CAMERA_FIXED, busLane.next().kind)
        assertEquals(Tool.entries.size, 7, "Sieben Kacheln im Dock")
    }

    @Test
    fun undoTakesBackTheStartFirstThenTheLastAction() {
        val plan = Plan()
        plan.clickMain(0, ToolSelection.of(ToolKind.CAMERA_FIXED))
        plan.clickMain(2)
        plan.undo()
        assertEquals(null, plan.routeStart)
        assertEquals(1, plan.actions.size)
        plan.undo()
        assertTrue(plan.actions.isEmpty())
    }
}
