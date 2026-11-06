package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import kotlin.math.abs

/** Flächennutzung aus OSM (`landuse=*`), aus der Verkehrszonen entstehen. */
internal fun zoneKindOf(tags: Map<String, String>): ZoneKind? =
    when (tags["landuse"]) {
        "residential" -> ZoneKind.RESIDENTIAL
        "commercial" -> ZoneKind.COMMERCIAL
        "retail" -> ZoneKind.RETAIL
        "industrial" -> ZoneKind.INDUSTRIAL
        else -> null
    }

internal class ZoneArea(
    val kind: ZoneKind,
    val polygon: FloatArray,
)

/**
 * Baut die Verkehrszonen: Flächen werden an den nächstgelegenen Netzknoten angebunden,
 * Sackgassen am Kartenrand und an Autobahnauffahrten werden zu Ein-/Ausfallpunkten ([ZoneKind.GATE]).
 */
internal class ZoneBuilder(
    private val network: RoadNetwork,
    private val bounds: Bounds,
) {
    fun build(
        areas: List<ZoneArea>,
        motorwayNodeIds: Set<Int>,
    ): List<Zone> {
        val zones = ArrayList<Zone>()
        for (area in areas) {
            val size = polygonArea(area.polygon)
            if (size < MIN_ZONE_AREA) continue
            val (cx, cy) = centroid(area.polygon)
            zones += Zone(zones.size, area.kind, cx, cy, size, nearestNode(cx, cy))
        }
        for (node in gateNodes(motorwayNodeIds)) {
            val roadNode = network.nodes[node]
            zones += Zone(zones.size, ZoneKind.GATE, roadNode.x, roadNode.y, area = 0f, node = node)
        }
        return zones
    }

    /** Knoten mit nur einem Abschnitt, die am Kartenrand liegen oder auf die Autobahn führen. */
    private fun gateNodes(motorwayNodeIds: Set<Int>): List<Int> {
        val degree = IntArray(network.nodes.size)
        for (segment in network.segments) {
            degree[segment.from]++
            degree[segment.to]++
        }
        return network.nodes
            .filter { degree[it.id] == 1 && (it.id in motorwayNodeIds || isNearEdge(it.x, it.y)) }
            .map { it.id }
    }

    private fun isNearEdge(
        x: Float,
        y: Float,
    ): Boolean =
        x - bounds.minX < EDGE_DISTANCE ||
            bounds.maxX - x < EDGE_DISTANCE ||
            y - bounds.minY < EDGE_DISTANCE ||
            bounds.maxY - y < EDGE_DISTANCE

    private fun nearestNode(
        x: Float,
        y: Float,
    ): Int =
        network.nodes
            .minBy { node ->
                val dx = node.x - x
                val dy = node.y - y
                dx * dx + dy * dy
            }.id

    companion object {
        /** Kleinere Flächen (einzelne Grundstücke) erzeugen keinen nennenswerten Verkehr. */
        const val MIN_ZONE_AREA = 2_000f
        const val EDGE_DISTANCE = 300f
    }
}

private const val DEGENERATE_AREA = 1e-3f
private const val CENTROID_FACTOR = 6f

/** Fläche eines geschlossenen Polygons (Gaußsche Trapezformel). */
internal fun polygonArea(polygon: FloatArray): Float = abs(signedArea(polygon))

private fun signedArea(polygon: FloatArray): Float {
    var sum = 0f
    val count = polygon.size / 2
    for (i in 0 until count) {
        val j = (i + 1) % count
        sum += polygon[i * 2] * polygon[j * 2 + 1] - polygon[j * 2] * polygon[i * 2 + 1]
    }
    return sum / 2
}

/** Schwerpunkt eines Polygons; bei entarteten Polygonen der Mittelwert der Punkte. */
internal fun centroid(polygon: FloatArray): Pair<Float, Float> {
    val area = signedArea(polygon)
    val count = polygon.size / 2
    if (abs(area) < DEGENERATE_AREA) {
        return (0 until count).map { polygon[it * 2] }.average().toFloat() to
            (0 until count).map { polygon[it * 2 + 1] }.average().toFloat()
    }
    var cx = 0f
    var cy = 0f
    for (i in 0 until count) {
        val j = (i + 1) % count
        val cross = polygon[i * 2] * polygon[j * 2 + 1] - polygon[j * 2] * polygon[i * 2 + 1]
        cx += (polygon[i * 2] + polygon[j * 2]) * cross
        cy += (polygon[i * 2 + 1] + polygon[j * 2 + 1]) * cross
    }
    // Schwerpunktformel: Summe / (6 · Fläche)
    val divisor = CENTROID_FACTOR * area
    return cx / divisor to cy / divisor
}
