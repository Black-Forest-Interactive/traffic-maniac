package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass

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
    val roads: Map<RoadClass, Path> =
        city.roads.segments
            .groupBy { it.roadClass }
            .mapValues { (_, segments) -> lines(segments.map { it.geometry }) }
    val trafficSignals: List<Offset> =
        city.roads.nodes
            .filter { it.trafficSignals }
            .map { Offset(it.x, it.y) }

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
