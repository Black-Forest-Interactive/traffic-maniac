package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import kotlin.random.Random

/** Eine geplante Fahrt von einem Netzknoten zum anderen. */
data class Trip(
    val originNode: Int,
    val destinationNode: Int,
)

/**
 * Würfelt Fahrten zwischen den Zonen. Startgewicht: vor allem Wohngebiete, Zielgewicht: vor allem Arbeit
 * und Einkauf. Ein fester Anteil sind Durchgangs-, Pendler- und Autobahnverkehr über die Stadtränder.
 * Nur Zonen im größten zusammenhängenden Teil des Netzes werden genutzt.
 */
class TripGenerator(
    zones: List<Zone>,
    graph: RoadGraph,
    private val random: Random,
) {
    private val usable = reachableNodes(graph).let { reachable -> zones.filter { it.node in reachable } }
    private val gates = usable.filter { it.kind == ZoneKind.GATE }
    private val areas = usable.filter { it.kind != ZoneKind.GATE }
    private val origins =
        WeightedChoice(areas) {
            if (it.kind ==
                ZoneKind.RESIDENTIAL
            ) {
                it.area
            } else {
                it.area * SECONDARY_WEIGHT
            }
        }
    private val destinations =
        WeightedChoice(areas) {
            if (it.kind ==
                ZoneKind.RESIDENTIAL
            ) {
                it.area * SECONDARY_WEIGHT
            } else {
                it.area
            }
        }

    val isEmpty: Boolean get() = areas.isEmpty()

    fun next(): Trip {
        val throughOrigin = gates.isNotEmpty() && random.nextDouble() < GATE_SHARE
        val throughDestination = gates.isNotEmpty() && random.nextDouble() < GATE_SHARE
        val origin = if (throughOrigin) gates.random(random) else origins.pick(random)
        val destination = if (throughDestination) gates.random(random) else destinations.pick(random)
        return Trip(origin.node, destination.node)
    }

    private class WeightedChoice(
        private val zones: List<Zone>,
        weight: (Zone) -> Float,
    ) {
        private val cumulative = DoubleArray(zones.size)

        init {
            var sum = 0.0
            zones.forEachIndexed { i, zone ->
                sum += weight(zone)
                cumulative[i] = sum
            }
        }

        fun pick(random: Random): Zone {
            val target = random.nextDouble() * cumulative.last()
            val index = cumulative.indexOfFirst { it >= target }
            return zones[if (index >= 0) index else zones.lastIndex]
        }
    }

    private companion object {
        /** Anteil der Fahrten, die am Stadtrand beginnen bzw. enden. */
        const val GATE_SHARE = 0.25

        /** Gewichtung der "falschen" Richtung, z. B. Fahrten von Gewerbe- zu Gewerbegebiet. */
        const val SECONDARY_WEIGHT = 0.3f

        /** Knoten der größten stark zusammenhängenden Komponente (Kosaraju). */
        fun reachableNodes(graph: RoadGraph): Set<Int> {
            val order = ArrayList<Int>()
            val visited = BooleanArray(graph.nodeCount)
            for (start in 0 until graph.nodeCount) {
                if (!visited[start]) {
                    postOrder(
                        start,
                        visited,
                        order,
                    ) { node -> graph.outLinks[node].map { graph.linkTo[it] } }
                }
            }
            val component = IntArray(graph.nodeCount) { -1 }
            var count = 0
            for (start in order.asReversed()) {
                if (component[start] >= 0) continue
                val stack = ArrayDeque(listOf(start))
                component[start] = count
                while (stack.isNotEmpty()) {
                    val node = stack.removeLast()
                    for (link in graph.inLinks[node]) {
                        val previous = graph.linkFrom[link]
                        if (component[previous] < 0) {
                            component[previous] = count
                            stack.addLast(previous)
                        }
                    }
                }
                count++
            }
            val largest =
                component
                    .toList()
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
            return (0 until graph.nodeCount).filter { component[it] == largest }.toSet()
        }

        /** Iterative Tiefensuche, die Knoten in Post-Order an [order] anhängt. */
        fun postOrder(
            start: Int,
            visited: BooleanArray,
            order: MutableList<Int>,
            neighbors: (Int) -> List<Int>,
        ) {
            val stack = ArrayDeque<Pair<Int, Iterator<Int>>>()
            visited[start] = true
            stack.addLast(start to neighbors(start).iterator())
            while (stack.isNotEmpty()) {
                val (node, iterator) = stack.last()
                val next = iterator.asSequence().firstOrNull { !visited[it] }
                if (next == null) {
                    stack.removeLast()
                    order += node
                } else {
                    visited[next] = true
                    stack.addLast(next to neighbors(next).iterator())
                }
            }
        }
    }
}
