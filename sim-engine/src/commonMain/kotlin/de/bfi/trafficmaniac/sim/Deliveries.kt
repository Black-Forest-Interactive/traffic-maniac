package de.bfi.trafficmaniac.sim

import kotlin.random.Random

/** Lieferwagen halten einmal pro Fahrt "nur kurz" in zweiter Reihe und blockieren dabei eine Spur. */
internal class Deliveries(
    private val graph: RoadGraph,
    private val config: SimulationConfig,
    private val random: Random,
    private val statistics: DayStatistics,
    private val secondRowStop: (minutes: Double) -> Unit,
) {
    /** `true`, solange der Lieferwagen steht. */
    fun update(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ): Boolean {
        if (vehicle.deliveringUntil > 0) {
            if (time >= vehicle.deliveringUntil) vehicle.deliveringUntil = 0.0
            return vehicle.deliveringUntil > 0
        }
        val length = graph.linkLength[link]
        val midLink = vehicle.position in length * MID_FROM..length * MID_TO
        val candidate = vehicle.type == VehicleType.VAN && vehicle.role == VehicleRole.PRIVATE && !vehicle.hasDelivered
        val stops = candidate && midLink && vehicle.speed > MOVING
        if (stops && random.nextDouble() < config.vanDeliveryChance / config.ticksPerSecond) {
            vehicle.hasDelivered = true
            val minutes = random.nextDouble(config.deliveryMinMinutes, config.deliveryMaxMinutes)
            vehicle.deliveringUntil = time + minutes * SECONDS_PER_MINUTE
            statistics.secondRowStop()
            secondRowStop(minutes)
            return true
        }
        return false
    }

    private companion object {
        const val MID_FROM = 0.3
        const val MID_TO = 0.7
        const val MOVING = 0.5
        const val SECONDS_PER_MINUTE = 60.0
    }
}
