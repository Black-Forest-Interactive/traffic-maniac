package de.bfi.trafficmaniac.sim

/** Feste Positionen der Ampeln (an der Haltelinie jeder geregelten Zufahrt, in der Mitte der Zufahrt). */
class SignalMarkers(
    val links: IntArray,
    val x: FloatArray,
    val y: FloatArray,
) {
    companion object {
        private const val SETBACK_METERS = 2f

        internal fun create(
            graph: RoadGraph,
            signals: SignalSystem,
        ): SignalMarkers {
            val links = signals.controlledLinks
            val point = FloatArray(LOCATE_SIZE)
            val x = FloatArray(links.size)
            val y = FloatArray(links.size)
            links.forEachIndexed { i, link ->
                graph.locate(link, graph.linkLength[link] - SETBACK_METERS, 0, point)
                val centerOffset = graph.laneOffset(link, (graph.linkLanes[link] - 1) / 2) - graph.laneOffset(link, 0)
                x[i] = point[LOCATE_X] + point[LOCATE_DIR_Y] * centerOffset
                y[i] = point[LOCATE_Y] - point[LOCATE_DIR_X] * centerOffset
            }
            return SignalMarkers(links, x, y)
        }
    }
}
