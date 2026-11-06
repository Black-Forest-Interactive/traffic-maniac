package de.bfi.trafficmaniac.sim

/** Fahrzeuge je Link und Spur, jeweils von vorn (Index 0) nach hinten sortiert. */
internal class LaneQueues(
    private val graph: RoadGraph,
) {
    private val queues = Array(graph.linkCount) { link -> Array(graph.linkLanes[link]) { ArrayDeque<Vehicle>() } }

    operator fun get(link: Int): Array<ArrayDeque<Vehicle>> = queues[link]

    /** Letztes Fahrzeug auf der Spur, auf die man von [currentLane] aus in [link] einfahren würde. */
    fun rearmost(
        link: Int,
        currentLane: Int,
    ): Vehicle? = queues[link][minOf(currentLane, graph.linkLanes[link] - 1)].lastOrNull()

    /** Platz zwischen Linkanfang und Heck des letzten Fahrzeugs auf der Spur. */
    fun freeSpaceAtStart(
        link: Int,
        lane: Int,
    ): Double = queues[link][lane].lastOrNull()?.let { it.position - it.type.length } ?: Double.POSITIVE_INFINITY

    /** Bleibt möglichst auf der eigenen Spur, weicht aber auf die freieste aus, wenn es eng wird. */
    fun bestEntryLane(
        link: Int,
        currentLane: Int,
    ): Int {
        val laneCount = graph.linkLanes[link]
        val preferred = minOf(currentLane, laneCount - 1)
        if (freeSpaceAtStart(link, preferred) >= COMFORTABLE_ENTRY_SPACE) return preferred
        return (0 until laneCount).maxBy { freeSpaceAtStart(link, it) }
    }

    /** Längste Standzeit eines Fahrzeugs an der Spitze einer Spur des Links. */
    fun longestFrontWait(link: Int): Double = queues[link].maxOf { queue -> queue.firstOrNull()?.waitingTime ?: 0.0 }

    private companion object {
        const val COMFORTABLE_ENTRY_SPACE = 25.0
    }
}
