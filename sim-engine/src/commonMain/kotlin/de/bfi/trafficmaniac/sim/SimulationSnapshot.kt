package de.bfi.trafficmaniac.sim

/** Kennzahlen eines Simulationszeitpunkts. */
data class SimulationStats(
    val activeVehicles: Int,
    val pendingVehicles: Int,
    val completedTrips: Int,
    val stuckRemovals: Int,
    val meanSpeedKmh: Double,
    val meanTripMinutes: Double,
)

/**
 * Unveränderlicher Zustand für die Darstellung. Fahrzeuge sind nach [ids] aufsteigend sortiert,
 * damit die Oberfläche zwei Snapshots schnell einander zuordnen und dazwischen interpolieren kann.
 */
class SimulationSnapshot(
    val tick: Long,
    val time: Double,
    val ids: IntArray,
    val x: FloatArray,
    val y: FloatArray,
    val dirX: FloatArray,
    val dirY: FloatArray,
    val lengths: FloatArray,
    val widths: FloatArray,
    val colors: IntArray,
    /** Zustand je Ampel in der Reihenfolge von [Simulation.signalMarkers]. */
    val signalStates: Array<SignalState>,
    val stats: SimulationStats,
) {
    val vehicleCount: Int get() = ids.size
}
