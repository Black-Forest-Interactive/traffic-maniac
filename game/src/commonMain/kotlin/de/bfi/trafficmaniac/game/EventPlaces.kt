package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation
import kotlin.math.hypot
import kotlin.random.Random

/** Wo Ereignisse stattfinden: Markt, Stadtfest, Marathon, Demo und Korso-Route – aus dem echten Straßennetz. */
internal class EventPlaces(
    private val city: CityMap,
    private val simulation: Simulation,
    private val random: Random,
) {
    /** Benannte Hauptstraßen (bis Tertiärstraße) – hier wird demonstriert, geradelt und gelaufen. */
    val mainRoads =
        city.roads.segments.filter {
            !it.passive && it.name != null && it.roadClass.ordinal <= RoadClass.TERTIARY.ordinal
        }

    /** Straße mit Geschäften nahe der Stadtmitte: kleine Straße am zentralsten Einkaufsgebiet. */
    fun marketStreet(): Pair<String, Set<Int>>? {
        val shops =
            city.zones
                .filter { it.kind == ZoneKind.RETAIL || it.kind == ZoneKind.COMMERCIAL }
                .minByOrNull { hypot(it.x - city.bounds.centerX, it.y - city.bounds.centerY) } ?: return null
        return streetNear(
            shops.x,
            shops.y,
            MARKET_SEGMENTS,
            city.roads.segments.filter { !it.passive && it.name != null },
        )
    }

    fun centralStreets(): Pair<String, Set<Int>> {
        val central =
            mainRoads.sortedBy { distance(it, city.bounds.centerX, city.bounds.centerY) }.take(FESTIVAL_SEGMENTS)
        return tr("Innenstadt ({name})", "name" to central.first().name) to central.mapTo(LinkedHashSet()) { it.id }
    }

    /** Die längste Hauptstraße – über die läuft der Marathon. */
    fun longestMainStreet(): Pair<String, Set<Int>> {
        val (name, segments) =
            mainRoads.groupBy { it.name!! }.maxBy { (_, list) -> list.sumOf { lengthOf(it.id).toDouble() } }
        return name to segments.take(MARATHON_MAX_SEGMENTS).mapTo(LinkedHashSet()) { it.id }
    }

    /** [count] Abschnitte der Straße, die (x, y) am nächsten liegt. */
    fun streetNear(
        x: Float,
        y: Float,
        count: Int,
        candidates: List<RoadSegment> = mainRoads,
    ): Pair<String, Set<Int>>? {
        val nearest = candidates.minByOrNull { distance(it, x, y) } ?: return null
        val street =
            candidates
                .filter { it.name == nearest.name }
                .sortedBy { distance(it, x, y) }
                .take(count)
        return nearest.name!! to street.mapTo(LinkedHashSet()) { it.id }
    }

    fun farApartMainNodes(minMeters: Float): Pair<Int, Int>? {
        repeat(NODE_ATTEMPTS) {
            val a = mainRoads.random(random)
            val b = mainRoads.random(random)
            val from = city.roads.nodes[a.from]
            val to = city.roads.nodes[b.to]
            if (hypot(from.x - to.x, from.y - to.y) >= minMeters) return a.from to b.to
        }
        return null
    }

    fun streetAtNode(node: Int): String =
        mainRoads.firstOrNull { it.from == node || it.to == node }?.name
            ?: city.roads.segments
                .firstOrNull { (it.from == node || it.to == node) && it.name != null }
                ?.name
            ?: tr("Stadtrand")

    private fun lengthOf(segment: Int): Float =
        simulation.graph.linkLength[simulation.graph.segmentLinks[segment].first()]

    private fun distance(
        segment: RoadSegment,
        x: Float,
        y: Float,
    ): Float {
        val g = segment.geometry
        val mid = g.pointCount / 2
        return hypot(g.x(mid) - x, g.y(mid) - y)
    }

    private companion object {
        const val MARKET_SEGMENTS = 3
        const val FESTIVAL_SEGMENTS = 6
        const val MARATHON_MAX_SEGMENTS = 14
        const val NODE_ATTEMPTS = 30
    }
}
