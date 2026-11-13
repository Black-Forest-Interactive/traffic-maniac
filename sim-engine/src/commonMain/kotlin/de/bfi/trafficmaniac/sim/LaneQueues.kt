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

    /**
     * Bleibt möglichst auf der eigenen Spur, weicht aber auf die freieste aus, wenn es eng wird. Eine Busspur nimmt
     * nur, wer darf ([Vehicle.mayUseBusLane]); Busse fahren dort bevorzugt.
     */
    fun bestEntryLane(
        link: Int,
        currentLane: Int,
        vehicle: Vehicle,
    ): Int {
        val laneCount = graph.linkLanes[link]
        val busLane = graph.busLane(link)
        val open = (0 until laneCount).filter { it != busLane || vehicle.mayUseBusLane }
        val preferred = minOf(currentLane, laneCount - 1).takeIf { it in open } ?: open.last()
        return when {
            busLane >= 0 && vehicle.role == VehicleRole.BUS -> {
                busLane
            }

            // Busspur-Sünder nehmen sie gern, wenn sie freier ist
            busLane < 0 || !vehicle.ignoresBusLane -> {
                if (freeSpaceAtStart(link, preferred) >= COMFORTABLE_ENTRY_SPACE) preferred else freest(link, open)
            }

            else -> {
                freest(link, open)
            }
        }
    }

    private fun freest(
        link: Int,
        lanes: List<Int>,
    ): Int = lanes.maxBy { freeSpaceAtStart(link, it) }

    /**
     * Der Letzte auf der Spur von [next], die [vehicle] dort nehmen wird – vor einer Busspur ist das nicht unbedingt
     * dieselbe Spurnummer wie jetzt.
     */
    fun rearmostFor(
        next: Int,
        vehicle: Vehicle,
    ): Vehicle? {
        val lane = if (graph.busLane(next) >= 0) bestEntryLane(next, vehicle.lane, vehicle) else vehicle.lane
        return rearmost(next, lane)
    }

    /** Längste Standzeit eines Fahrzeugs an der Spitze einer Spur des Links. */
    fun longestFrontWait(link: Int): Double = queues[link].maxOf { queue -> queue.firstOrNull()?.waitingTime ?: 0.0 }

    private companion object {
        const val COMFORTABLE_ENTRY_SPACE = 25.0
    }
}
