package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Vorberechnete Pfade in Weltkoordinaten. Sie werden einmal pro Stadt gebaut;
 * beim Zeichnen ändert sich nur die Transformation der Kamera.
 */
class MapLayers(
    city: CityMap,
) {
    val green: Path = areas(city.scenery.green)
    val water: Path = areas(city.scenery.water)
    val waterways: Path = lines(city.scenery.waterways)
    val buildings: Path = areas(city.scenery.buildings)
    val minorRoads: Path = lines(city.scenery.minorRoads)
    val motorways: Path = lines(city.scenery.motorways)

    /** Nur die befahrenen Straßen – passive Nebenstraßen stecken schon in [minorRoads]. */
    val roads: Map<RoadClass, Path> =
        city.roads.segments
            .filter { !it.passive }
            .groupBy { it.roadClass }
            .mapValues { (_, segments) -> lines(segments.map { it.geometry }) }

    /** Ein Namensschild je Hauptstraße, am längsten Abschnitt (Mitte und Richtung). */
    val labels: List<StreetLabel> =
        city.roads.segments
            .filter { !it.passive && it.name != null && it.roadClass.ordinal <= RoadClass.TERTIARY.ordinal }
            .groupBy { it.name!! }
            .map { (name, segments) ->
                val longest = segments.maxBy { straightLength(it.geometry) }
                val g = longest.geometry
                val mid = g.pointCount / 2
                val (x0, y0) = g.x(mid - 1) to g.y(mid - 1)
                val (x1, y1) = g.x(mid) to g.y(mid)
                StreetLabel(name, (x0 + x1) / 2, (y0 + y1) / 2, atan2(y1 - y0, x1 - x0), longest.roadClass)
            }.sortedBy { it.roadClass.ordinal }
    val trafficSignals: List<Offset> =
        city.roads.nodes
            .filter { it.trafficSignals }
            .map { Offset(it.x, it.y) }

    private fun straightLength(line: Polyline): Float =
        hypot(line.x(line.pointCount - 1) - line.x(0), line.y(line.pointCount - 1) - line.y(0))

    private fun lines(polylines: List<Polyline>): Path = Path().apply { polylines.forEach { addPolyline(it) } }

    private fun areas(polylines: List<Polyline>): Path =
        Path().apply {
            polylines.forEach {
                addPolyline(it)
                close()
            }
        }

    private fun Path.addPolyline(polyline: Polyline) {
        moveTo(polyline.x(0), polyline.y(0))
        for (i in 1 until polyline.pointCount) lineTo(polyline.x(i), polyline.y(i))
    }
}

/** Straßenname an einer Stelle in Weltkoordinaten; [angle] im Bogenmaß gegen die x-Achse (Osten). */
class StreetLabel(
    val name: String,
    val x: Float,
    val y: Float,
    val angle: Float,
    val roadClass: RoadClass,
)
