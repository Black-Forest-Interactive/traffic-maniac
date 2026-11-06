package de.bfi.trafficmaniac.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

enum class SignalState {
    NONE,
    GREEN,
    YELLOW,
    RED,
}

/**
 * Feste Ampelprogramme. In OSM stehen Ampeln oft einzeln an den Haltelinien einer Kreuzung;
 * nahe beieinander liegende Ampelknoten werden deshalb zu einer Anlage zusammengefasst.
 * Jede Anlage hat zwei Phasen: Zufahrten etwa entlang einer Achse sind gemeinsam grün.
 * Gibt es nur eine Achse (z. B. Fußgängerampel), folgt auf die Autophase eine kurze Fußgängerphase.
 */
class SignalSystem(
    private val graph: RoadGraph,
    random: Random,
) {
    private val linkGroup = IntArray(graph.linkCount) { NO_GROUP }
    private val linkPhase = IntArray(graph.linkCount)
    private val groupOffset: DoubleArray
    private val groupCycle: DoubleArray

    /** Alle Links, deren Ende von einer Ampel geregelt wird. */
    val controlledLinks: IntArray

    init {
        val groups = clusterSignalNodes()
        val controlled = ArrayList<Int>()
        val twoPhase = BooleanArray(groups.size)
        groups.forEachIndexed { index, nodes ->
            val approaches = assignPhases(index, nodes)
            controlled += approaches
            twoPhase[index] = approaches.any { linkPhase[it] == 1 }
        }
        controlledLinks = controlled.toIntArray()
        groupCycle =
            DoubleArray(groups.size) { if (twoPhase[it]) CYCLE_SECONDS else PHASE_SECONDS + PEDESTRIAN_SECONDS }
        groupOffset = DoubleArray(groups.size) { random.nextDouble() * groupCycle[it] }
    }

    fun isControlled(link: Int): Boolean = linkGroup[link] != NO_GROUP

    fun state(
        link: Int,
        time: Double,
    ): SignalState {
        val group = linkGroup[link]
        if (group == NO_GROUP) return SignalState.NONE
        val t = (time + groupOffset[group]) % groupCycle[group]
        val activePhase = (t / PHASE_SECONDS).toInt()
        val inPhase = t - activePhase * PHASE_SECONDS
        return when {
            activePhase != linkPhase[link] -> SignalState.RED
            inPhase < GREEN_SECONDS -> SignalState.GREEN
            inPhase < GREEN_SECONDS + YELLOW_SECONDS -> SignalState.YELLOW
            else -> SignalState.RED
        }
    }

    /** Fasst Ampelknoten zusammen, die näher als [CLUSTER_RADIUS] beieinander liegen (Union-Find). */
    private fun clusterSignalNodes(): List<List<Int>> {
        val signalNodes = (0 until graph.nodeCount).filter { graph.nodeHasSignals[it] }
        val parent = IntArray(signalNodes.size) { it }

        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            return r
        }
        for (a in signalNodes.indices) {
            for (b in a + 1 until signalNodes.size) {
                val distance =
                    hypot(
                        graph.nodeX[signalNodes[a]] - graph.nodeX[signalNodes[b]],
                        graph.nodeY[signalNodes[a]] - graph.nodeY[signalNodes[b]],
                    )
                if (distance < CLUSTER_RADIUS) parent[root(a)] = root(b)
            }
        }
        return signalNodes.indices
            .groupBy { root(it) }
            .values
            .map { members -> members.map { signalNodes[it] } }
    }

    /** Ordnet die Zufahrten einer Anlage den Phasen zu. Verbindungen innerhalb der Anlage bleiben ungeregelt. */
    private fun assignPhases(
        group: Int,
        nodes: List<Int>,
    ): List<Int> {
        val nodeSet = nodes.toSet()
        val approaches = nodes.flatMap { node -> graph.inLinks[node].filter { graph.linkFrom[it] !in nodeSet } }
        if (approaches.isEmpty()) return emptyList()
        val referenceAxis = axis(graph.endHeading(approaches.first()))
        for (link in approaches) {
            val difference = abs(axis(graph.endHeading(link)) - referenceAxis)
            val sameAxis = difference < PI / 4 || difference > 3 * PI / 4
            linkGroup[link] = group
            linkPhase[link] = if (sameAxis) 0 else 1
        }
        return approaches
    }

    /** Richtung ohne Vorzeichen: Gegenverkehr liegt auf derselben Achse. */
    private fun axis(heading: Double): Double = ((heading % PI) + PI) % PI

    companion object {
        const val GREEN_SECONDS = 25.0
        const val YELLOW_SECONDS = 3.0
        const val ALL_RED_SECONDS = 2.0
        const val PHASE_SECONDS = GREEN_SECONDS + YELLOW_SECONDS + ALL_RED_SECONDS
        const val CYCLE_SECONDS = 2 * PHASE_SECONDS

        /** Fußgängerphase an Ampeln ohne Querverkehr. */
        const val PEDESTRIAN_SECONDS = 12.0
        const val CLUSTER_RADIUS = 35f
        private const val NO_GROUP = -1
    }
}
