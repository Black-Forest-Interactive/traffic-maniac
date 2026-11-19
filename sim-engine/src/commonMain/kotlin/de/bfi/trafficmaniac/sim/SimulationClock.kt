package de.bfi.trafficmaniac.sim

/**
 * Taktgeber der Simulation mit festem Zeitschritt.
 *
 * Die Simulation rechnet ausschließlich in ganzen Ticks, damit gleiche Eingaben
 * immer zum gleichen Ergebnis führen (Determinismus).
 */
class SimulationClock(
    val ticksPerSecond: Int = DEFAULT_TICKS_PER_SECOND,
) {
    init {
        require(ticksPerSecond > 0) { "ticksPerSecond must be positive, was $ticksPerSecond" }
    }

    var tick: Long = 0
        private set

    /** Dauer eines Ticks in simulierten Sekunden. */
    val tickDurationSeconds: Double get() = 1.0 / ticksPerSecond

    /** Bisher simulierte Zeit in Sekunden. */
    val elapsedSeconds: Double get() = tick.toDouble() / ticksPerSecond

    fun advance(ticks: Long = 1) {
        require(ticks >= 0) { "ticks must not be negative, was $ticks" }
        tick += ticks
    }

    companion object {
        const val DEFAULT_TICKS_PER_SECOND = 10
    }
}
