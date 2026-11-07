package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.RoadClass

/**
 * Feste Positionen der Ampeln je geregelter Zufahrt: [x], [y] an der Haltelinie in der Mitte der Zufahrt (Punkt in der
 * Gesamtansicht); dazu Fahrtrichtung, Straßenmitte und Straßenklasse, damit die Karte den Signalgeber in Fahrtrichtung
 * rechts neben die gezeichnete Fahrbahn stellen kann.
 */
class SignalMarkers(
    val links: IntArray,
    val x: FloatArray,
    val y: FloatArray,
    val dirX: FloatArray = FloatArray(links.size),
    val dirY: FloatArray = FloatArray(links.size),
    val centerX: FloatArray = x,
    val centerY: FloatArray = y,
    val roadClasses: Array<RoadClass> = Array(links.size) { RoadClass.TERTIARY },
) {
    companion object {
        private const val SETBACK_METERS = 2f

        internal fun create(
            graph: RoadGraph,
            signals: SignalSystem,
        ): SignalMarkers {
            val links = signals.controlledLinks
            val point = FloatArray(LOCATE_SIZE)
            val count = links.size
            val markers =
                SignalMarkers(
                    links,
                    FloatArray(count),
                    FloatArray(count),
                    FloatArray(count),
                    FloatArray(count),
                    FloatArray(count),
                    FloatArray(count),
                    Array(count) { graph.linkRoadClass[links[it]] },
                )
            links.forEachIndexed { i, link ->
                graph.locate(link, graph.linkLength[link] - SETBACK_METERS, 0, point)
                val dx = point[LOCATE_DIR_X]
                val dy = point[LOCATE_DIR_Y]
                // Rechts in Fahrtrichtung ist (dy, −dx); Spur 0 liegt um laneOffset(0) rechts der Straßenmitte
                val firstLane = graph.laneOffset(link, 0)
                val centerOffset = graph.laneOffset(link, (graph.linkLanes[link] - 1) / 2) - firstLane
                markers.x[i] = point[LOCATE_X] + dy * centerOffset
                markers.y[i] = point[LOCATE_Y] - dx * centerOffset
                markers.dirX[i] = dx
                markers.dirY[i] = dy
                markers.centerX[i] = point[LOCATE_X] - dy * firstLane
                markers.centerY[i] = point[LOCATE_Y] + dx * firstLane
            }
            return markers
        }
    }
}
