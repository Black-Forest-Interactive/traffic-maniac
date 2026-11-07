package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import kotlin.math.hypot

/** Ein Ort auf der Karte in Weltkoordinaten (Meter) – z. B. wohin eine Meldung führt. */
data class MapPlace(
    val x: Float,
    val y: Float,
)

/** Meldungen an den Spieler, wo möglich mit Ort. */
internal typealias Messenger = (text: String, place: MapPlace?) -> Unit

internal fun Messenger.say(
    text: String,
    place: MapPlace? = null,
) = invoke(text, place)

/** Mitte eines Straßenabschnitts (entlang der Linie gemessen). */
internal fun CityMap.placeOfSegment(segment: Int): MapPlace {
    val line = roads.segments[segment].geometry
    val lengths = (1 until line.pointCount).map { hypot(line.x(it) - line.x(it - 1), line.y(it) - line.y(it - 1)) }
    var rest = lengths.sum() / 2
    for (i in lengths.indices) {
        if (rest <= lengths[i] && lengths[i] > 0f) {
            val t = rest / lengths[i]
            return MapPlace(line.x(i) + (line.x(i + 1) - line.x(i)) * t, line.y(i) + (line.y(i + 1) - line.y(i)) * t)
        }
        rest -= lengths[i]
    }
    return MapPlace(line.x(0), line.y(0))
}

internal fun CityMap.placeOfNode(node: Int): MapPlace = MapPlace(roads.nodes[node].x, roads.nodes[node].y)

/** Ort mehrerer Abschnitte: der mittlere von ihnen. */
internal fun CityMap.placeOfSegments(segments: Collection<Int>): MapPlace? =
    segments.elementAtOrNull(segments.size / 2)?.let { placeOfSegment(it) }
