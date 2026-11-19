package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import kotlin.random.Random

/** Eine geplante Fahrt von einem Netzknoten zum anderen. */
data class Trip(
    val originNode: Int,
    val destinationNode: Int,
    /** Wohngebiet, aus dem der Fahrer stammt (Start oder Ziel), oder -1 bei Durchgangsverkehr. */
    val homeZone: Int = -1,
    /** Fahrer aus dem Wohngebiet (für Punkte und Führerschein) oder -1 bei Durchgangsverkehr. */
    val person: Int = -1,
    /** Zielzone, in der geparkt werden muss (Gewerbe, Einkauf, Industrie), oder -1 (nach Hause, Stadtrand). */
    val parkingZone: Int = -1,
    /** Einkaufsfahrt: kurzer Aufenthalt am Ziel. */
    val shortStay: Boolean = false,
)

/** Gruppen von Zonen, zwischen denen Fahrten stattfinden. */
internal enum class ZoneGroup {
    HOME,
    WORK,
    SHOP,
    ANY,
}

/** Anteil [share] der Fahrten führt von [from] nach [to]. */
internal class TripPattern(
    val from: ZoneGroup,
    val to: ZoneGroup,
    val share: Double,
)

/**
 * Würfelt Fahrten zwischen den Zonen – abhängig von Wochentag und Uhrzeit: morgens von zu Hause zur Arbeit,
 * abends zurück, mittags und samstags zum Einkaufen. Ein fester Anteil ist Durchgangs-, Pendler- und
 * Autobahnverkehr über die Stadtränder. Nur Zonen im größten zusammenhängenden Teil des Netzes werden genutzt.
 */
class TripGenerator(
    zones: List<Zone>,
    graph: RoadGraph,
    private val random: Random,
) {
    private val usable = reachableNodes(graph).let { reachable -> zones.filter { it.node in reachable } }
    private val gates = usable.filter { it.kind == ZoneKind.GATE }
    private val areas = usable.filter { it.kind != ZoneKind.GATE }
    private val groups =
        mapOf(
            ZoneGroup.HOME to WeightedChoice(areas) { if (it.kind == ZoneKind.RESIDENTIAL) it.area else 0f },
            ZoneGroup.WORK to WeightedChoice(areas) { if (it.kind in WORK_KINDS) it.area else it.area * MINOR_WEIGHT },
            ZoneGroup.SHOP to WeightedChoice(areas) { if (it.kind in SHOP_KINDS) it.area else it.area * MINOR_WEIGHT },
            ZoneGroup.ANY to WeightedChoice(areas) { it.area },
        )

    val isEmpty: Boolean get() = areas.isEmpty()

    fun next(time: GameTime = GameTime(0, 0.0)): Trip {
        val pattern = pick(patternsFor(time))
        val throughOrigin = gates.isNotEmpty() && random.nextDouble() < GATE_SHARE
        val throughDestination = gates.isNotEmpty() && random.nextDouble() < GATE_SHARE
        val origin = if (throughOrigin) gates.random(random) else groups.getValue(pattern.from).pick(random)
        val destination = if (throughDestination) gates.random(random) else groups.getValue(pattern.to).pick(random)
        val home =
            when {
                origin.kind == ZoneKind.RESIDENTIAL -> origin.id
                destination.kind == ZoneKind.RESIDENTIAL -> destination.id
                else -> -1
            }
        val person = if (home >= 0) home * PERSONS_PER_ZONE_BLOCK + random.nextInt(driversIn(home)) else -1
        val parks = destination.kind != ZoneKind.RESIDENTIAL && destination.kind != ZoneKind.GATE
        return Trip(
            origin.node,
            destination.node,
            home,
            person,
            parkingZone = if (parks) destination.id else -1,
            shortStay = pattern.to == ZoneGroup.SHOP || destination.kind == ZoneKind.RETAIL,
        )
    }

    /** Anzahl Autofahrer eines Wohngebiets (wächst mit der Fläche). */
    private fun driversIn(zone: Int): Int {
        val area = areaOf[zone] ?: 0f
        return (area / SQUARE_METERS_PER_DRIVER).toInt().coerceIn(MIN_DRIVERS, PERSONS_PER_ZONE_BLOCK)
    }

    private val areaOf = zones.associate { it.id to it.area }

    private fun pick(patterns: List<TripPattern>): TripPattern {
        var roll = random.nextDouble() * patterns.sumOf { it.share }
        return patterns.firstOrNull {
            roll -= it.share
            roll <= 0
        } ?: patterns.last()
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
            // Leere Gruppe (z. B. Stadt ohne Gewerbe): auf alle Flächen ausweichen
            if (cumulative.isEmpty() || cumulative.last() <= 0.0) return zones[random.nextInt(zones.size)]
            val target = random.nextDouble() * cumulative.last()
            val index = cumulative.indexOfFirst { it >= target }
            return zones[if (index >= 0) index else zones.lastIndex]
        }
    }

    private companion object {
        /** Personennummern: Wohngebiet × 10.000 + laufende Nummer. */
        const val PERSONS_PER_ZONE_BLOCK = 10_000
        const val SQUARE_METERS_PER_DRIVER = 150f
        const val MIN_DRIVERS = 20

        /** Anteil der Fahrten, die am Stadtrand beginnen bzw. enden. */
        const val GATE_SHARE = 0.25

        /** Gewicht von Flächen, die nicht zum eigentlichen Zweck passen (z. B. Wohngebiet als Einkaufsziel). */
        const val MINOR_WEIGHT = 0.1f
        val WORK_KINDS = setOf(ZoneKind.COMMERCIAL, ZoneKind.INDUSTRIAL, ZoneKind.RETAIL)
        val SHOP_KINDS = setOf(ZoneKind.RETAIL, ZoneKind.COMMERCIAL)

        private const val MORNING_START = 5
        private const val MORNING_END = 10
        private const val EVENING_START = 15
        private const val EVENING_END = 20
        private const val SHOPPING_START = 8
        private const val SHOPPING_END = 19

        private val mixed = listOf(TripPattern(ZoneGroup.ANY, ZoneGroup.ANY, 1.0))
        private val workdayMorning =
            listOf(
                TripPattern(ZoneGroup.HOME, ZoneGroup.WORK, 0.7),
                TripPattern(ZoneGroup.HOME, ZoneGroup.SHOP, 0.1),
                TripPattern(ZoneGroup.ANY, ZoneGroup.ANY, 0.2),
            )
        private val workdayMidday =
            listOf(
                TripPattern(ZoneGroup.ANY, ZoneGroup.SHOP, 0.4),
                TripPattern(ZoneGroup.ANY, ZoneGroup.ANY, 0.6),
            )
        private val workdayEvening =
            listOf(
                TripPattern(ZoneGroup.WORK, ZoneGroup.HOME, 0.6),
                TripPattern(ZoneGroup.SHOP, ZoneGroup.HOME, 0.2),
                TripPattern(ZoneGroup.ANY, ZoneGroup.ANY, 0.2),
            )
        private val saturdayShopping =
            listOf(
                TripPattern(ZoneGroup.HOME, ZoneGroup.SHOP, 0.5),
                TripPattern(ZoneGroup.SHOP, ZoneGroup.HOME, 0.3),
                TripPattern(ZoneGroup.ANY, ZoneGroup.ANY, 0.2),
            )

        private fun patternsFor(time: GameTime): List<TripPattern> {
            val hour = time.hour
            return when {
                time.weekday == Weekday.SATURDAY && hour in SHOPPING_START until SHOPPING_END -> saturdayShopping
                !time.weekday.isWorkday -> mixed
                hour in MORNING_START until MORNING_END -> workdayMorning
                hour in EVENING_START until EVENING_END -> workdayEvening
                hour in MORNING_END until EVENING_START -> workdayMidday
                else -> mixed
            }
        }

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
                    ) { node -> graph.outLinks[node].filter { !graph.linkPassive[it] }.map { graph.linkTo[it] } }
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
                        if (graph.linkPassive[link]) continue
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
