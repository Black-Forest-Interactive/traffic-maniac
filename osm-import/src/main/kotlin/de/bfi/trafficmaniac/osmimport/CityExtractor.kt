package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.LocalProjection
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadNode
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.Scenery

/**
 * Sammelt OSM-Elemente im Ausschnitt und baut daraus eine [CityMap]:
 * Hauptstraßen werden an Kreuzungen und Ampeln in Abschnitte zerlegt, alles andere wird Kulisse.
 */
class CityExtractor(
    private val config: ImportConfig,
) : OsmHandler {
    private val projection = LocalProjection(config.box.center)
    private val nodes = NodeStore()
    private val signalNodes = HashSet<Long>()
    private val roads = ArrayList<RawRoad>()
    private val scenery = SceneryKind.entries.associateWith { ArrayList<Polyline>() }

    override fun node(
        id: Long,
        lat: Double,
        lon: Double,
        tags: Map<String, String>,
    ) {
        if (!config.box.contains(lat, lon)) return
        nodes.add(id, lat, lon)
        if (tags["highway"] == "traffic_signals") signalNodes += id
    }

    override fun way(
        id: Long,
        refs: LongArray,
        tags: Map<String, String>,
    ) {
        val roadClass = tags["highway"]?.let { RoadClass.fromOsm(it) }
        if (roadClass != null && roadClass in config.roadClasses) {
            val attributes = RoadTags.parse(tags, roadClass)
            roads += RawRoad(if (attributes.reversed) refs.reversedArray() else refs, roadClass, attributes)
        } else {
            addScenery(refs, tags)
        }
    }

    private fun addScenery(
        refs: LongArray,
        tags: Map<String, String>,
    ) {
        val kind = SceneryKind.classify(tags) ?: return
        val closed = refs.size >= MIN_AREA_POINTS && refs.first() == refs.last()
        if (kind.isArea && !closed) return
        project(refs)?.let { scenery.getValue(kind) += Polyline(simplify(it, SCENERY_TOLERANCE_METERS)) }
    }

    fun build(): CityMap {
        val box = config.box
        return CityMap(
            name = config.name,
            attribution = ATTRIBUTION,
            origin = projection.origin,
            bounds =
                Bounds(
                    minX = projection.x(box.minLon),
                    minY = projection.y(box.minLat),
                    maxX = projection.x(box.maxLon),
                    maxY = projection.y(box.maxLat),
                ),
            roads = buildRoadNetwork(),
            scenery =
                Scenery(
                    buildings = scenery.getValue(SceneryKind.BUILDING),
                    minorRoads = scenery.getValue(SceneryKind.MINOR_ROAD),
                    motorways = scenery.getValue(SceneryKind.MOTORWAY),
                    water = scenery.getValue(SceneryKind.WATER),
                    waterways = scenery.getValue(SceneryKind.WATERWAY),
                    green = scenery.getValue(SceneryKind.GREEN),
                ),
        )
    }

    private fun buildRoadNetwork(): RoadNetwork {
        // Wege am Rand des Ausschnitts in die Stücke zerlegen, deren Knoten bekannt sind
        val pieces = roads.flatMap { road -> splitAtMissingNodes(road.refs).map { road to it } }

        val splitNodes = findSplitNodes(pieces.map { it.second })
        val builder = NetworkBuilder()
        for ((road, refs) in pieces) {
            var start = 0
            for (i in 1 until refs.size) {
                if (refs[i] in splitNodes || i == refs.lastIndex) {
                    builder.addSegment(road, refs.copyOfRange(start, i + 1))
                    start = i
                }
            }
        }
        return builder.build()
    }

    /** Kreuzungen: Knoten, die mehrfach vorkommen, Wegenden und Ampeln. */
    private fun findSplitNodes(pieces: List<LongArray>): Set<Long> {
        val usage = HashMap<Long, Int>()
        val splitNodes = HashSet<Long>(signalNodes)
        for (refs in pieces) {
            refs.forEach { usage.merge(it, 1, Int::plus) }
            splitNodes += refs.first()
            splitNodes += refs.last()
        }
        usage.forEach { (id, count) -> if (count > 1) splitNodes += id }
        return splitNodes
    }

    private inner class NetworkBuilder {
        private val roadNodes = LinkedHashMap<Long, RoadNode>()
        private val segments = ArrayList<RoadSegment>()

        fun addSegment(
            road: RawRoad,
            refs: LongArray,
        ) {
            val from = nodeFor(refs.first())
            val to = nodeFor(refs.last())
            // Geschlossene Schleifen ohne Zwischenpunkte sind wertlos
            if (from == to && refs.size <= MIN_LOOP_POINTS) return
            val geometry = project(refs) ?: return
            segments +=
                RoadSegment(
                    id = segments.size,
                    from = from.id,
                    to = to.id,
                    roadClass = road.roadClass,
                    geometry = Polyline(simplify(geometry, ROAD_TOLERANCE_METERS)),
                    lanesForward = road.attributes.lanesForward,
                    lanesBackward = road.attributes.lanesBackward,
                    maxSpeedKmh = road.attributes.maxSpeedKmh,
                    name = road.attributes.name,
                )
        }

        fun build(): RoadNetwork = RoadNetwork(nodes = roadNodes.values.toList(), segments = segments)

        private fun nodeFor(osmId: Long): RoadNode =
            roadNodes.getOrPut(osmId) {
                val index = nodes.indexOf(osmId)
                RoadNode(
                    id = roadNodes.size,
                    x = projection.x(nodes.lon(index)),
                    y = projection.y(nodes.lat(index)),
                    trafficSignals = osmId in signalNodes,
                )
            }
    }

    private fun splitAtMissingNodes(refs: LongArray): List<LongArray> {
        val result = ArrayList<LongArray>()
        var start = 0
        for (i in 0..refs.size) {
            if (i == refs.size || nodes.indexOf(refs[i]) < 0) {
                if (i - start >= 2) result += refs.copyOfRange(start, i)
                start = i + 1
            }
        }
        return result
    }

    /** Projiziert die bekannten Knoten; `null`, wenn weniger als zwei übrig bleiben. */
    private fun project(refs: LongArray): FloatArray? {
        val coordinates = FloatArray(refs.size * 2)
        var count = 0
        for (ref in refs) {
            val index = nodes.indexOf(ref)
            if (index < 0) continue
            coordinates[count * 2] = projection.x(nodes.lon(index))
            coordinates[count * 2 + 1] = projection.y(nodes.lat(index))
            count++
        }
        return if (count >= 2) coordinates.copyOf(count * 2) else null
    }

    private class RawRoad(
        val refs: LongArray,
        val roadClass: RoadClass,
        val attributes: RoadAttributes,
    )

    companion object {
        const val ATTRIBUTION = "© OpenStreetMap-Mitwirkende (ODbL)"
        private const val ROAD_TOLERANCE_METERS = 0.5f
        private const val SCENERY_TOLERANCE_METERS = 1.0f
        private const val MIN_AREA_POINTS = 4
        private const val MIN_LOOP_POINTS = 3
    }
}
