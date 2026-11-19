package de.bfi.trafficmaniac.sim

import kotlin.math.hypot

/**
 * Eine Ampelanlage: alle Ampelknoten einer Kreuzung ([signalNodes]), die Kreuzungsknoten selbst ([core]) und die
 * Haltelinien-Ampeln vor der Kreuzung mit den Zufahrten, die sie regeln ([stopLines]).
 */
internal class SignalPlant(
    val signalNodes: List<Int>,
    val core: Set<Int>,
    val stopLines: Map<Int, List<Int>>,
)

/**
 * Bildet die Ampelanlagen aus den Ampelknoten.
 *
 * In OSM steht die Ampel oft nicht am Kreuzungsknoten, sondern als eigener Knoten an der Haltelinie jeder Zufahrt,
 * 20–50 m vor der Kreuzung. So ein Knoten liegt mitten im Straßenzug (genau zwei Nachbarn) – er wird der nächsten
 * echten Kreuzung entlang der Straße zugeordnet und regelt nur die Fahrtrichtung **auf die Kreuzung zu**; wer die
 * Kreuzung verlässt, fährt an ihm ohne Halt vorbei. Alle Ampeln einer Kreuzung bilden eine Anlage mit einem
 * gemeinsamen Programm. Ampeln an Kreuzungsknoten, die näher als [SignalSystem.CLUSTER_RADIUS] beieinander liegen,
 * gehören ebenfalls zusammen (große Kreuzungen sind in OSM oft mehrere Knoten). Ein Ampelknoten ohne Kreuzung in
 * der Nähe bleibt für sich (Fußgängerampel) und regelt beide Richtungen.
 */
internal class SignalPlants(
    private val graph: RoadGraph,
) {
    /** Befahrene Nachbarn je Knoten mit der Länge der Verbindung. */
    private fun neighbors(node: Int): List<Pair<Int, Float>> =
        (
            graph.outLinks[node].filter { !graph.linkPassive[it] }.map { graph.linkTo[it] to graph.linkLength[it] } +
                graph.inLinks[node].filter { !graph.linkPassive[it] }.map { graph.linkFrom[it] to graph.linkLength[it] }
        ).filter { it.first != node }.distinctBy { it.first }

    private fun degree(node: Int): Int = neighbors(node).size

    fun build(): List<SignalPlant> {
        val signalNodes = (0 until graph.nodeCount).filter { graph.nodeHasSignals[it] }
        val anchor = HashMap<Int, Int>()
        val stopLines = HashMap<Int, List<Int>>()
        for (node in signalNodes) {
            val junction = if (degree(node) == 2) nearestJunction(node) else null
            if (junction == null) {
                anchor[node] = node
            } else {
                anchor[node] = junction.node
                // Geregelt wird nur, wer von der abgewandten Seite auf die Kreuzung zufährt
                stopLines[node] =
                    graph.inLinks[node].filter { !graph.linkPassive[it] && graph.linkFrom[it] != junction.via }
            }
        }
        return groupAnchors(anchor.values.distinct()).map { anchors ->
            val members = signalNodes.filter { anchor[it] in anchors }
            SignalPlant(
                signalNodes = members,
                core = coreNodes(anchors),
                stopLines = members.filter { it in stopLines }.associateWith { stopLines.getValue(it) },
            )
        }
    }

    /** Kreuzung, die von [node] aus entlang der Straße erreicht wird, und der erste Nachbar in ihre Richtung. */
    private class Junction(
        val node: Int,
        val via: Int,
        val distance: Float,
    )

    /** Nächste Kreuzung (mindestens drei befahrene Nachbarn) höchstens [MAX_STOP_LINE_DISTANCE] entlang der Straße. */
    private fun nearestJunction(node: Int): Junction? =
        neighbors(node)
            .mapNotNull { (first, length) -> walk(node, first, length) }
            .minByOrNull { it.distance }

    private fun walk(
        start: Int,
        first: Int,
        firstLength: Float,
    ): Junction? {
        var previous = start
        var current = first
        var distance = firstLength
        var steps = 0
        var next = neighbors(current)
        // Durch Knicke (genau zwei Nachbarn) weiter, bis eine Kreuzung oder ein Ende kommt
        while (next.size == 2 && distance <= MAX_STOP_LINE_DISTANCE && steps < MAX_STEPS) {
            val onward = next.first { it.first != previous }
            previous = current
            current = onward.first
            distance += onward.second
            steps++
            next = neighbors(current)
        }
        val found = next.size >= MIN_JUNCTION_DEGREE && distance <= MAX_STOP_LINE_DISTANCE
        return if (found) Junction(current, first, distance) else null
    }

    /** Fasst Kreuzungen zusammen, die näher als [SignalSystem.CLUSTER_RADIUS] beieinander liegen (Union-Find). */
    private fun groupAnchors(anchors: List<Int>): List<List<Int>> {
        val parent = IntArray(anchors.size) { it }

        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        for (a in anchors.indices) {
            for (b in a + 1 until anchors.size) {
                if (distance(anchors[a], anchors[b]) < SignalSystem.CLUSTER_RADIUS) parent[root(a)] = root(b)
            }
        }
        return anchors.indices
            .groupBy { root(it) }
            .values
            .map { members -> members.map { anchors[it] } }
    }

    /**
     * Alle Knoten der Kreuzung: die Anker und jeder Knoten im Kreis um ihren Mittelpunkt, der alle Anker umschließt.
     * Ohne diese Erweiterung würde die Verbindung von einem ampellosen Kreuzungsknoten zum nächsten Ampelknoten als
     * Zufahrt gelten – mit einer roten Ampel mitten in der Kreuzung, auf der sich der Verkehr staut.
     */
    private fun coreNodes(anchors: List<Int>): Set<Int> {
        if (anchors.size < 2) return anchors.toSet()
        val cx = anchors.sumOf { graph.nodeX[it].toDouble() } / anchors.size
        val cy = anchors.sumOf { graph.nodeY[it].toDouble() } / anchors.size
        val radius = anchors.maxOf { hypot(graph.nodeX[it] - cx, graph.nodeY[it] - cy) } + JUNCTION_MARGIN
        return anchors.toSet() +
            (0 until graph.nodeCount).filter { hypot(graph.nodeX[it] - cx, graph.nodeY[it] - cy) <= radius }
    }

    private fun distance(
        a: Int,
        b: Int,
    ): Float = hypot(graph.nodeX[a] - graph.nodeX[b], graph.nodeY[a] - graph.nodeY[b])

    private companion object {
        /** So weit vor einer Kreuzung (entlang der Straße) gilt ein Ampelknoten als ihre Haltelinie. */
        const val MAX_STOP_LINE_DISTANCE = 60f

        /** So weit über die äußersten Kreuzungsknoten hinaus gehören Knoten noch zur Kreuzung (Meter). */
        const val JUNCTION_MARGIN = 5.0
        const val MAX_STEPS = 20

        /** Ab so vielen befahrenen Nachbarn ist ein Knoten eine Kreuzung. */
        const val MIN_JUNCTION_DEGREE = 3
    }
}
