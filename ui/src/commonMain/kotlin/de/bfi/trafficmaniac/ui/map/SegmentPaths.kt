package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.graphics.Path
import de.bfi.trafficmaniac.city.CityMap

/** Pfad je Straßenabschnitt (in Weltkoordinaten), erst bei Bedarf gebaut. */
internal class SegmentPaths(
    private val city: CityMap,
) {
    private val paths = arrayOfNulls<Path>(city.roads.segments.size)

    operator fun get(segment: Int): Path =
        paths[segment] ?: Path().also { path ->
            val line = city.roads.segments[segment].geometry
            path.moveTo(line.x(0), line.y(0))
            for (i in 1 until line.pointCount) path.lineTo(line.x(i), line.y(i))
            paths[segment] = path
        }

    /** Befahrene (nicht passive) Abschnitte. */
    val mainSegments: List<Int> =
        city.roads.segments
            .filter { !it.passive }
            .map { it.id }
}
