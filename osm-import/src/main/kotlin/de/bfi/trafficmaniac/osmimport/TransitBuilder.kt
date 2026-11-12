package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.TransitLine
import de.bfi.trafficmaniac.city.TransitStop
import kotlin.math.hypot

/** Haltestelle vor dem Anbinden: Position (lokale Meter) und Name aus OSM. */
internal class RawStop(
    val x: Float,
    val y: Float,
    val name: String?,
)

/** Buslinie aus einer OSM-Relation: Nummer, Name, Farbe und die Haltestellen-Knoten in Fahrtreihenfolge. */
internal class RawLine(
    val ref: String,
    val name: String,
    val colour: String?,
    val stops: List<Long>,
)

/** Haltestellen-Knoten: Bussteig (`highway=bus_stop`, `public_transport=platform`) oder Halteposition. */
internal fun isBusStop(tags: Map<String, String>): Boolean =
    tags["highway"] == "bus_stop" ||
        (tags["public_transport"] in setOf("platform", "stop_position") && tags["bus"] == "yes")

/** Buslinie (`type=route`, `route=bus`) mit ihren Haltestellen – Bussteige, sonst Haltepositionen. */
internal fun rawLineOf(
    members: List<RelationMember>,
    tags: Map<String, String>,
): RawLine? {
    if (tags["type"] != "route" || tags["route"] !in setOf("bus", "trolleybus")) return null
    val ref = tags["ref"] ?: return null
    val nodes = members.filter { it.type == "node" }
    val platforms = nodes.filter { it.role.startsWith("platform") }
    val stops = platforms.ifEmpty { nodes.filter { it.role.startsWith("stop") } }
    return RawLine(ref, tags["name"] ?: "Linie $ref", tags["colour"], stops.map { it.ref })
}

/**
 * Bindet Haltestellen an den nächsten befahrenen Abschnitt an (höchstens [MAX_SNAP_METERS] entfernt) und baut die
 * Linien daraus. Linien mit weniger als [MIN_LINE_STOPS] Haltestellen im Ausschnitt und doppelte Varianten
 * (gleiche Nummer, gleiche Haltestellenfolge) fallen weg.
 */
internal fun buildTransit(
    network: RoadNetwork,
    stops: Map<Long, RawStop>,
    lines: List<RawLine>,
): Pair<List<TransitStop>, List<TransitLine>> {
    val snapper = SegmentSnapper(network.segments.filter { !it.passive })
    val result = ArrayList<TransitStop>()
    val byOsmId = HashMap<Long, Int?>()

    fun stopId(osmId: Long): Int? =
        byOsmId.getOrPut(osmId) {
            val raw = stops[osmId] ?: return@getOrPut null
            val (segment, offset) = snapper.snap(raw.x, raw.y) ?: return@getOrPut null
            result += TransitStop(result.size, raw.name ?: "Haltestelle", raw.x, raw.y, segment, offset)
            result.size - 1
        }
    val built = ArrayList<TransitLine>()
    val seen = HashSet<Pair<String, List<Int>>>()
    for (line in lines.sortedWith(compareBy({ it.ref.length }, { it.ref }, { it.name }))) {
        val ids = line.stops.mapNotNull(::stopId).withoutRepeats(result)
        if (ids.size < MIN_LINE_STOPS || !seen.add(line.ref to ids)) continue
        built += TransitLine(built.size, line.ref, line.name, ids, line.colour?.let(::parseColour))
    }
    // Nur Haltestellen behalten, die eine Linie anfährt – mit fortlaufenden Nummern
    val used = built.flatMap { it.stops }.toSortedSet().toList()
    val renumber = used.withIndex().associate { (index, id) -> id to index }
    val kept = used.map { result[it].copy(id = renumber.getValue(it)) }
    return kept to built.map { line -> line.copy(stops = line.stops.map(renumber::getValue)) }
}

/** Ohne direkt aufeinanderfolgende gleiche Haltestellen (Bussteig und Halteposition mit gleichem Namen). */
private fun List<Int>.withoutRepeats(stops: List<TransitStop>): List<Int> {
    val out = ArrayList<Int>()
    for (id in this) {
        val last = out.lastOrNull()
        if (last == null || (last != id && stops[last].name != stops[id].name)) out += id
    }
    return out
}

/** `#E30613` bzw. `red` → ARGB; unbekannte Namen ergeben `null`. */
private fun parseColour(colour: String): Int? {
    val hex = colour.removePrefix("#")
    return if (hex.length == HEX_RGB_LENGTH) hex.toLongOrNull(HEX)?.let { (OPAQUE or it).toInt() } else null
}

/** Sucht zu einem Punkt den nächsten Abschnitt und den Abstand vom Anfang entlang seiner Geometrie. */
private class SegmentSnapper(
    private val segments: List<RoadSegment>,
) {
    fun snap(
        x: Float,
        y: Float,
    ): Pair<Int, Float>? {
        var best: RoadSegment? = null
        var bestDistance = MAX_SNAP_METERS
        var bestOffset = 0f
        for (segment in segments) {
            val geometry = segment.geometry
            var along = 0f
            for (i in 0 until geometry.pointCount - 1) {
                val ax = geometry.x(i)
                val ay = geometry.y(i)
                val dx = geometry.x(i + 1) - ax
                val dy = geometry.y(i + 1) - ay
                val length = hypot(dx, dy)
                if (length > 0f) {
                    val t = (((x - ax) * dx + (y - ay) * dy) / (length * length)).coerceIn(0f, 1f)
                    val distance = hypot(ax + t * dx - x, ay + t * dy - y)
                    if (distance < bestDistance) {
                        best = segment
                        bestDistance = distance
                        bestOffset = along + t * length
                    }
                }
                along += length
            }
        }
        return best?.let { it.id to bestOffset }
    }
}

private const val MAX_SNAP_METERS = 60f
private const val MIN_LINE_STOPS = 3
private const val HEX = 16
private const val HEX_RGB_LENGTH = 6
private const val OPAQUE = 0xFF000000L
