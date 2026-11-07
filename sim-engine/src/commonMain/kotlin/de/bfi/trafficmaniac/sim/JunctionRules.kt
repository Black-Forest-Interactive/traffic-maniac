package de.bfi.trafficmaniac.sim

import kotlin.math.max

/** Wann ein Fahrzeug am Ende seines Links anhalten muss: Ampel oder Vorfahrt. */
internal class JunctionRules(
    private val graph: RoadGraph,
    private val signals: SignalSystem,
    private val lanes: LaneQueues,
) {
    fun mustStopAtEnd(
        vehicle: Vehicle,
        link: Int,
        remaining: Double,
        time: Double,
    ): Boolean =
        when {
            vehicle.holdAtEnd -> true

            // Blaulicht, Rad-Korso und Schwertransport fahren über Rot und nehmen sich die Vorfahrt
            vehicle.role.ignoresSignals -> false

            else -> signalOrYield(vehicle, link, remaining, time)
        }

    private fun signalOrYield(
        vehicle: Vehicle,
        link: Int,
        remaining: Double,
        time: Double,
    ): Boolean =
        when (signals.state(link, time)) {
            SignalState.RED -> true

            // Bei Gelb hält, wer noch bequem zum Stehen kommt
            SignalState.YELLOW -> remaining > vehicle.speed * vehicle.speed / (2 * vehicle.comfortableDeceleration)

            SignalState.GREEN -> false

            SignalState.NONE -> remaining < YIELD_CHECK_DISTANCE && mustYield(link)

            // Ampel aus: Jeder wartet auf jeden – bis einer die Geduld verliert
            SignalState.FLASHING -> remaining < YIELD_CHECK_DISTANCE && mustYield(link, anyone = true)
        }

    /** Ungeregelte Kreuzung: Die Straße mit niedrigerem Rang wartet auf herannahende Fahrzeuge der höherrangigen. */
    private fun mustYield(
        link: Int,
        anyone: Boolean = false,
    ): Boolean {
        val rank = graph.priorityRank(link)
        return graph.inLinks[graph.linkTo[link]].any { other ->
            other != link &&
                (anyone || graph.priorityRank(other) < rank) &&
                lanes[other].any { queue -> queue.firstOrNull()?.let { isApproaching(it, other) } == true }
        }
    }

    private fun isApproaching(
        vehicle: Vehicle,
        link: Int,
    ): Boolean {
        // Wer selbst schon lange steht (z. B. weil es hinter der Kreuzung nicht weitergeht), blockiert nicht
        if (vehicle.waitingTime > PRIORITY_WAIT_LIMIT) return false
        val distance = graph.linkLength[link] - vehicle.position
        return distance < PRIORITY_NEAR_DISTANCE || distance / max(vehicle.speed, MIN_SPEED) < PRIORITY_TIME_GAP
    }

    private companion object {
        const val YIELD_CHECK_DISTANCE = 40.0
        const val PRIORITY_NEAR_DISTANCE = 8.0
        const val PRIORITY_TIME_GAP = 4.0
        const val PRIORITY_WAIT_LIMIT = 10.0
        const val MIN_SPEED = 0.1
    }
}
