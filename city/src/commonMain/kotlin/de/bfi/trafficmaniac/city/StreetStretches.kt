package de.bfi.trafficmaniac.city

/**
 * Strecken von Kreuzung zu Kreuzung. OSM teilt Straßen an Kurven und Brücken in viele Abschnitte; für Spieler und
 * Schilder zählt aber die Strecke zwischen zwei echten Kreuzungen.
 *
 * Eine **Kreuzung** ist ein Knoten, an dem nicht genau zwei Straßen zusammentreffen, oder einer mit Ampel. Dabei zählen
 * auch die nur gezeichneten (passiven) Nebenstraßen mit – wer auf der Karte eine Einmündung sieht, erwartet dort das
 * Ende der Strecke. Die Strecke selbst besteht nur aus befahrenen Abschnitten.
 */
class StreetStretches(
    private val network: RoadNetwork,
) {
    /** Befahrene Abschnitte je Knoten. */
    private val activeAtNode: List<List<Int>> = segmentsAtNodes { !it.passive }

    /** Alle Abschnitte je Knoten, auch passive Nebenstraßen. */
    private val allAtNode: List<List<Int>> = segmentsAtNodes { true }

    private fun segmentsAtNodes(include: (RoadSegment) -> Boolean): List<List<Int>> =
        List(network.nodes.size) { mutableListOf<Int>() }.also { lists ->
            for (segment in network.segments) {
                if (!include(segment)) continue
                lists[segment.from] += segment.id
                if (segment.to != segment.from) lists[segment.to] += segment.id
            }
        }

    /** Endet hier eine Strecke? */
    fun isJunction(node: Int): Boolean =
        allAtNode[node].size != 2 || activeAtNode[node].size != 2 || network.nodes[node].trafficSignals

    /** Der Abschnitt, mit dem [segment] an [node] zur selben Strecke gehört – `null` an einer Kreuzung. */
    fun continuation(
        segment: Int,
        node: Int,
    ): Int? = if (isJunction(node)) null else activeAtNode[node].firstOrNull { it != segment }

    /**
     * Befahrene Abschnitte derselben Straße (gleicher Name), die an [node] auf der anderen Seite einer Kreuzung
     * weitergehen – dort reicht ein Schild für beide Seiten.
     */
    fun sameStreetAcross(
        segment: Int,
        node: Int,
    ): List<Int> {
        val name = network.segments[segment].name ?: return emptyList()
        return activeAtNode[node].filter { it != segment && network.segments[it].name == name }
    }

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
