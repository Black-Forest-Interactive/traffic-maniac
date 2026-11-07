package de.bfi.trafficmaniac.city

/**
 * Strecken von Kreuzung zu Kreuzung. OSM teilt Straßen an Kurven, Brücken und Einmündungen von Nebenstraßen in viele
 * Abschnitte; für Spieler und Schilder zählt aber die Strecke zwischen zwei echten Kreuzungen.
 *
 * Eine **Kreuzung** ist ein Knoten, an dem nicht genau zwei befahrene Abschnitte zusammentreffen, oder einer mit Ampel.
 * Passive Nebenstraßen zählen nicht – an ihren Einmündungen läuft die Strecke weiter.
 */
class StreetStretches(
    private val network: RoadNetwork,
) {
    private val segmentsAtNode: List<List<Int>> =
        List(network.nodes.size) { mutableListOf<Int>() }.also { lists ->
            for (segment in network.segments) {
                if (segment.passive) continue
                lists[segment.from] += segment.id
                if (segment.to != segment.from) lists[segment.to] += segment.id
            }
        }

    /** Endet hier eine Strecke? */
    fun isJunction(node: Int): Boolean = segmentsAtNode[node].size != 2 || network.nodes[node].trafficSignals

    /** Der Abschnitt, mit dem [segment] an [node] zur selben Strecke gehört – `null` an einer Kreuzung. */
    fun continuation(
        segment: Int,
        node: Int,
    ): Int? = if (isJunction(node)) null else segmentsAtNode[node].firstOrNull { it != segment }

    /** Alle Abschnitte der Strecke, zu der [segment] gehört (passive Abschnitte bleiben für sich). */
    fun stretchOf(segment: Int): List<Int> {
        if (network.segments[segment].passive) return listOf(segment)
        val stretch = LinkedHashSet<Int>()
        val queue = ArrayDeque(listOf(segment))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!stretch.add(current)) continue
            val s = network.segments[current]
            for (node in listOf(s.from, s.to)) continuation(current, node)?.let { queue += it }
        }
        return stretch.toList()
    }
}
