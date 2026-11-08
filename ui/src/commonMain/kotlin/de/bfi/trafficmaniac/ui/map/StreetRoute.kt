package de.bfi.trafficmaniac.ui.map

import de.bfi.trafficmaniac.city.CityMap
import kotlin.math.hypot

/**
 * Weg entlang der Straßen zwischen zwei Abschnitten – für „Tempolimit von hier bis da“. Bevorzugt bleibt der Weg auf
 * der Straße, auf der er beginnt: Abschnitte mit anderem Namen zählen [OTHER_STREET_PENALTY]-fach.
 * Fahrtrichtungen spielen keine Rolle (ein Schild gilt für beide Richtungen).
 */
class StreetRoute(
    private val city: CityMap,
) {
    private val segments = city.roads.segments

    /** Abschnitte je Knoten (nur befahrene; mit [allow] auch zugeschaltete Nebenstraßen). */
    private fun neighbors(
        node: Int,
        allow: (Int) -> Boolean,
    ): List<Int> = atNode[node].filter { !segments[it].passive || allow(it) }

    private val atNode: List<List<Int>> =
        List(city.roads.nodes.size) { mutableListOf<Int>() }.also { lists ->
            for (s in segments) {
                lists[s.from] += s.id
                if (s.to != s.from) lists[s.to] += s.id
            }
        }

    /**
     * Abschnitte von [from] bis [to] einschließlich, oder `null`, wenn es keinen Weg gibt.
     * [allow] lässt passive Nebenstraßen zu (z. B. zugeschaltete Umleitungen). Mit [directed] nur in erlaubter
     * Fahrtrichtung – wichtig bei getrennten Richtungsfahrbahnen (zwei Einbahnstraßen), etwa für grüne Wellen.
     */
    fun between(
        from: Int,
        to: Int,
        allow: (Int) -> Boolean = { false },
        directed: Boolean = false,
    ): List<Int>? {
        if (from == to) return listOf(from)
        val target = segments[to]
        // Eine Einbahnstraße am Ende wird an ihrem Anfang betreten
        val targets = if (directed && target.isOneway) setOf(target.from) else setOf(target.from, target.to)
        val search = Search(from, targets, allow, directed)
        val end = search.run() ?: return null
        return (listOf(from) + search.pathTo(end) + to).distinct()
    }

    /** Dijkstra von beiden Enden von [from] zum ersten erreichten Knoten aus [targets]. */
    private inner class Search(
        private val from: Int,
        private val targets: Set<Int>,
        private val allow: (Int) -> Boolean,
        private val directed: Boolean,
    ) {
        private val name = segments[from].name
        private val distance = HashMap<Int, Double>()
        private val via = HashMap<Int, Int>()
        private val queue = ArrayList<Pair<Int, Double>>()

        fun run(): Int? {
            val start = segments[from]
            // Eine Einbahnstraße am Anfang wird an ihrem Ende verlassen
            val starts = if (directed && start.isOneway) listOf(start.to) else listOf(start.from, start.to)
            for (node in starts) {
                distance[node] = 0.0
                queue += node to 0.0
            }
            var reached: Int? = null
            while (queue.isNotEmpty() && reached == null) {
                val (node, dist) = queue.removeAt(queue.indices.minBy { queue[it].second })
                when {
                    dist > (distance[node] ?: Double.MAX_VALUE) -> Unit
                    dist > MAX_ROUTE_METERS -> queue.clear()
                    node in targets -> reached = node
                    else -> relax(node, dist)
                }
            }
            return reached
        }

        private fun relax(
            node: Int,
            dist: Double,
        ) {
            for (segment in neighbors(node, allow)) {
                val s = segments[segment]
                // Gerichtet: eine Einbahnstraße nur von ihrem Anfang aus
                val againstOneway = directed && s.isOneway && s.from != node
                if (segment == from || againstOneway) continue
                val next = if (s.from == node) s.to else s.from
                val penalty = if (s.name != null && s.name == name) 1.0 else OTHER_STREET_PENALTY
                val candidate = dist + length(segment) * penalty
                if (candidate < (distance[next] ?: Double.MAX_VALUE)) {
                    distance[next] = candidate
                    via[next] = segment
                    queue += next to candidate
                }
            }
        }

        /** Abschnitte vom Start bis [end] in Fahrtreihenfolge. */
        fun pathTo(end: Int): List<Int> {
            val path = ArrayList<Int>()
            var node = end
            while (true) {
                val segment = via[node] ?: break
                path += segment
                val s = segments[segment]
                node = if (s.from == node) s.to else s.from
            }
            return path.asReversed()
        }
    }

    private fun length(segment: Int): Double {
        val g = segments[segment].geometry
        return (1 until g.pointCount).sumOf { hypot(g.x(it) - g.x(it - 1), g.y(it) - g.y(it - 1)).toDouble() }
    }

    private companion object {
        const val OTHER_STREET_PENALTY = 3.0

        /** Weiter wird nicht gesucht (gewichtete Meter) – so bleibt ein Fehlklick billig. */
        const val MAX_ROUTE_METERS = 6_000.0
    }
}
