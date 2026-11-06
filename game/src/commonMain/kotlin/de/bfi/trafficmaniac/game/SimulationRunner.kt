package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Zwei aufeinanderfolgende Simulationszustände. Die Oberfläche interpoliert zwischen [previous] und [current],
 * damit Fahrzeuge auch bei 10 Ticks pro Sekunde flüssig fahren.
 */
class TrafficFrame(
    val previous: SimulationSnapshot,
    val current: SimulationSnapshot,
    private val publishedAt: TimeSource.Monotonic.ValueTimeMark,
    private val interval: Duration,
    /** Gleitender Mittelwert der Rechenzeit pro Tick in Millisekunden. */
    val stepMillis: Double,
    val speed: Int,
) {
    /** Anteil des Wegs von [previous] nach [current] für den aktuellen Bildschirm-Frame (0..1). */
    fun interpolation(): Float =
        if (speed == 0) 1f else (publishedAt.elapsedNow() / interval).toFloat().coerceIn(0f, 1f)
}

/**
 * Lässt die Simulation in Echtzeit (oder im Zeitraffer) auf einem Hintergrund-Thread laufen
 * und veröffentlicht nach jedem Tick einen [TrafficFrame].
 */
class SimulationRunner(
    private val simulation: Simulation,
) {
    private val frameFlow = MutableStateFlow<TrafficFrame?>(null)
    val frames: StateFlow<TrafficFrame?> = frameFlow.asStateFlow()

    private val speedFlow = MutableStateFlow(1)

    /** 0 = Pause, sonst Zeitraffer-Faktor. */
    val speed: StateFlow<Int> = speedFlow.asStateFlow()

    fun setSpeed(value: Int) {
        speedFlow.value = value.coerceIn(0, MAX_SPEED)
    }

    fun togglePause() {
        speedFlow.value = if (speedFlow.value == 0) lastSpeed else 0
    }

    private var lastSpeed = 1

    /** Läuft, bis die aufrufende Coroutine abgebrochen wird. */
    suspend fun run() =
        withContext(Dispatchers.Default) {
            val tickInterval = (1.0 / simulation.config.ticksPerSecond).seconds
            var previous = simulation.snapshot()
            var stepMillis = 0.0
            var nextTick = TimeSource.Monotonic.markNow()
            while (isActive) {
                val speed = speedFlow.value
                if (speed == 0) {
                    frameFlow.value =
                        frameFlow.value?.let {
                            TrafficFrame(
                                it.previous,
                                it.current,
                                TimeSource.Monotonic.markNow(),
                                tickInterval,
                                it.stepMillis,
                                0,
                            )
                        }
                    delay(PAUSE_POLL)
                    nextTick = TimeSource.Monotonic.markNow()
                    continue
                }
                lastSpeed = speed
                val interval = tickInterval / speed
                val stepStart = TimeSource.Monotonic.markNow()
                simulation.step()
                stepMillis =
                    stepMillis * (1 - STEP_TIME_BLEND) +
                    stepStart.elapsedNow().inWholeMicroseconds / MICROS_PER_MILLI * STEP_TIME_BLEND
                val current = simulation.snapshot()
                frameFlow.value =
                    TrafficFrame(previous, current, TimeSource.Monotonic.markNow(), interval, stepMillis, speed)
                previous = current

                nextTick += interval
                val wait = -nextTick.elapsedNow()
                when {
                    wait.isPositive() -> delay(wait)

                    // Zu weit zurückgefallen (z. B. Rechner war ausgelastet): nicht nachholen, sondern weitermachen
                    wait < -MAX_LAG -> nextTick = TimeSource.Monotonic.markNow()
                }
            }
        }

    companion object {
        const val MAX_SPEED = 8
        private val PAUSE_POLL = 50.milliseconds
        private val MAX_LAG = 1.seconds
        private const val STEP_TIME_BLEND = 0.05
        private const val MICROS_PER_MILLI = 1000.0
    }
}
