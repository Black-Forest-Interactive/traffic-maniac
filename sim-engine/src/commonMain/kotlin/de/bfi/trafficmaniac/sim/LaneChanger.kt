package de.bfi.trafficmaniac.sim

/**
 * Einfacher Spurwechsel: Ein Fahrzeug wechselt, wenn die Nachbarspur deutlich mehr Beschleunigung erlaubt
 * und der neue Hintermann nicht stark bremsen muss. Jedes Fahrzeug prüft das einmal pro Sekunde.
 */
internal class LaneChanger(
    private val graph: RoadGraph,
    private val lanes: LaneQueues,
) {
    fun changeLanes(tick: Long) {
        for (link in 0 until graph.linkCount) {
            if (graph.linkLanes[link] < 2) continue
            val desired = graph.linkSpeedLimit[link].toDouble()
            for (queue in lanes[link]) {
                val candidates = queue.filter { (it.id + tick) % INTERVAL_TICKS == 0L && wantsToChange(it) }
                for (vehicle in candidates) tryChangeLane(vehicle, link, desired)
            }
        }
    }

    private fun wantsToChange(vehicle: Vehicle) = vehicle.acceleration < THRESHOLD - vehicle.maxAcceleration

    private fun tryChangeLane(
        vehicle: Vehicle,
        link: Int,
        desired: Double,
    ) {
        val target =
            intArrayOf(vehicle.lane - 1, vehicle.lane + 1)
                .filter { it in 0 until graph.linkLanes[link] }
                .firstOrNull { isBetterAndSafe(vehicle, lanes[link][it], desired) }
                ?: return
        val queue = lanes[link][target]
        lanes[link][vehicle.lane].remove(vehicle)
        queue.add(insertIndex(queue, vehicle), vehicle)
        vehicle.lane = target
    }

    private fun isBetterAndSafe(
        vehicle: Vehicle,
        queue: ArrayDeque<Vehicle>,
        desired: Double,
    ): Boolean {
        val index = insertIndex(queue, vehicle)
        val leader = queue.getOrNull(index - 1)
        val follower = queue.getOrNull(index)
        val gapAhead = leader?.let { it.position - it.type.length - vehicle.position } ?: Double.POSITIVE_INFINITY
        val gapBehind =
            follower?.let { vehicle.position - vehicle.type.length - it.position } ?: Double.POSITIVE_INFINITY
        val enoughSpace = gapAhead >= vehicle.driver.minimumGap && gapBehind >= (follower?.driver?.minimumGap ?: 0.0)
        // Der neue Hintermann darf nicht stark bremsen müssen
        val safe =
            follower == null || follower.idm(desired, gapBehind, follower.speed - vehicle.speed) > -SAFE_DECELERATION
        val gain =
            leader?.let { vehicle.idm(desired, gapAhead, vehicle.speed - it.speed) } ?: vehicle.maxAcceleration
        return enoughSpace && safe && gain >= vehicle.acceleration + THRESHOLD
    }

    private fun insertIndex(
        queue: ArrayDeque<Vehicle>,
        vehicle: Vehicle,
    ): Int = queue.indexOfFirst { it.position < vehicle.position }.let { if (it < 0) queue.size else it }

    private companion object {
        const val INTERVAL_TICKS = 10L
        const val THRESHOLD = 0.4
        const val SAFE_DECELERATION = 3.0
    }
}
