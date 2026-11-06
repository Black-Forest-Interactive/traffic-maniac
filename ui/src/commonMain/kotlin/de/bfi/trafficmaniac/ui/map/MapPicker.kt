package de.bfi.trafficmaniac.ui.map

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.Polyline
import kotlin.math.hypot

/** Findet Straßenabschnitte und Knoten in der Nähe eines Klicks (Weltkoordinaten in Metern). */
class MapPicker(
    private val city: CityMap,
) {
    /** Nächster Straßenabschnitt innerhalb von [maxDistance] Metern oder `null`. */
    fun nearestSegment(
        x: Float,
        y: Float,
        maxDistance: Float,
    ): Int? =
        city.roads.segments
            .map { it.id to distanceToPolyline(it.geometry, x, y) }
            .filter { it.second <= maxDistance }
            .minByOrNull { it.second }
            ?.first

    /** Nächster Knoten des Straßennetzes innerhalb von [maxDistance] Metern oder `null`. */
    fun nearestNode(
        x: Float,
        y: Float,
        maxDistance: Float,
    ): Int? =
        city.roads.nodes
            .map { it.id to hypot(it.x - x, it.y - y) }
            .filter { it.second <= maxDistance }
            .minByOrNull { it.second }
            ?.first

    /** Mittelpunkt (entlang der Strecke) eines Abschnitts, z. B. für Schilder. */
    fun midpoint(segment: Int): Pair<Float, Float> {
        val line = city.roads.segments[segment].geometry
        val half = length(line) / 2
        var walked = 0f
        for (i in 1 until line.pointCount) {
            val step = hypot(line.x(i) - line.x(i - 1), line.y(i) - line.y(i - 1))
            if (walked + step >= half && step > 0f) {
                val t = (half - walked) / step
                return line.x(i - 1) + (line.x(i) - line.x(i - 1)) * t to
                    line.y(i - 1) + (line.y(i) - line.y(i - 1)) * t
            }
            walked += step
        }
        return line.x(0) to line.y(0)
    }

    private fun length(line: Polyline): Float =
        (1 until line.pointCount)
            .sumOf { i ->
                hypot(
                    line.x(i) - line.x(i - 1),
                    line.y(i) - line.y(i - 1),
                ).toDouble()
            }.toFloat()

    private fun distanceToPolyline(
        line: Polyline,
        x: Float,
        y: Float,
    ): Float =
        (1 until line.pointCount).minOf { i ->
            distanceToSegment(x, y, line.x(i - 1), line.y(i - 1), line.x(i), line.y(i))
        }

    private fun distanceToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
