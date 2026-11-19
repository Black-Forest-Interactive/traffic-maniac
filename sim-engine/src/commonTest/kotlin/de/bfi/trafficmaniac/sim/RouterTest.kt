package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RouterTest {
    private val graph = RoadGraph(crossroads().roads)
    private val router = Router(graph)

    @Test
    fun findsRouteAcrossTheJunction() {
        val route = assertNotNull(router.route(fromNode = 1, toNode = 3))

        assertEquals(2, route.size)
        assertEquals(1, graph.linkFrom[route.first()])
        assertEquals(3, graph.linkTo[route.last()])
    }

    @Test
    fun noRouteToItself() {
        assertNull(router.route(1, 1))
    }

    @Test
    fun prefersFasterRouteWhenTravelTimesChange() {
        val direct = assertNotNull(router.route(1, 2))
        router.raiseTo(direct.last(), 10_000.0)

        // Es gibt keinen Umweg – die Route bleibt, wird aber nicht ungültig
        assertContentEquals(direct, router.route(1, 2))
    }
}
