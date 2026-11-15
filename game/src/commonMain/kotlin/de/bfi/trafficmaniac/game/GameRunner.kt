package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.DayReport
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Vom Spieler gewähltes Tempo (0 = Pause). */
    val speed: Int,
    /** Tatsächlicher Zeitraffer inklusive Nacht-Beschleunigung. */
    val effectiveSpeed: Int,
    /** Bilanz des gerade beendeten Tags, solange der Spieler sie noch nicht bestätigt hat. */
    val pendingReport: DayReport?,
    val game: GameState,
    /** Fortschritt des Vorlaufs (0..1), solange er läuft; sonst `null`. */
    val warmup: Float? = null,
) {
    /** Anteil des Wegs von [previous] nach [current] für den aktuellen Bildschirm-Frame (0..1). */
    fun interpolation(): Float =
        if (effectiveSpeed ==
            0
        ) {
            1f
        } else {
            (publishedAt.elapsedNow() / interval).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * Lässt das Spiel in Echtzeit (oder im Zeitraffer) auf einem Hintergrund-Thread laufen
 * und veröffentlicht laufend [TrafficFrame]s.
 *
 * - Nachts (22–5 Uhr) läuft die Zeit automatisch [NIGHT_BOOST]-mal schneller.
 * - Um Mitternacht hält die Simulation an, bis der Spieler den Tagesabschluss bestätigt.
 */
class GameRunner(
    private val game: CityGame,
    /**
     * Vorlauf in Ticks: so viele Schritte rechnet der Runner zuerst so schnell er kann, bevor die normale Zeit beginnt.
     * Das Spiel ist dabei schon zu sehen (statt eines Ladebildschirms, der auf dem Handy über eine Minute dauert).
     */
    private val warmupTicks: Int = 0,
) {
    private val simulation = game.simulation
    private val frameFlow = MutableStateFlow<TrafficFrame?>(null)
    val frames: StateFlow<TrafficFrame?> = frameFlow.asStateFlow()

    private val speedFlow = MutableStateFlow(1)

    /** 0 = Pause, sonst gewählter Zeitraffer-Faktor. */
    val speed: StateFlow<Int> = speedFlow.asStateFlow()

    private var lastSpeed = 1

    /** Hält die Simulation zwischen zwei Schritten an, solange jemand das Spiel ansieht (z. B. zum Speichern). */
    private val lock = Mutex()
    private var acknowledgedReports = simulation.dayReports.size

    fun setSpeed(value: Int) {
        speedFlow.value = value.coerceIn(0, MAX_SPEED)
        if (value > 0) lastSpeed = value
    }

    fun togglePause() {
        speedFlow.value = if (speedFlow.value == 0) lastSpeed else 0
    }

    /** Führt [block] aus, während die Simulation steht – zwischen zwei Schritten, nie mittendrin. */
    suspend fun <T> paused(block: (CityGame) -> T): T = lock.withLock { block(game) }

    /** Bestätigt den Tagesabschluss; danach läuft der nächste Tag. */
    fun acknowledgeReport() {
        acknowledgedReports = simulation.dayReports.size
    }

    private val pendingReport: DayReport?
        get() = simulation.dayReports.getOrNull(acknowledgedReports)

    /**
     * Läuft, bis die aufrufende Coroutine abgebrochen wird.
     *
     * Gerechnet wird so viel, wie das Tempo verlangt; veröffentlicht wird höchstens im Bildtakt ([PUBLISH_PERIOD]),
     * denn ein Simulations-Schnappschuss kostet etwa so viel wie ein Simulationsschritt. Den Spielstand für die
     * Oberfläche baut der Runner nur alle [STATE_PERIOD] neu – oder sofort, wenn eine Spieleraktion ausgeführt wurde.
     */
    suspend fun run() =
        withContext(Dispatchers.Default) {
            val ticksPerSecond = simulation.config.ticksPerSecond
            var previous = simulation.snapshot()
            var stepMillis = 0.0
            var tickDebt = 0.0
            var lastLoop = TimeSource.Monotonic.markNow()
            var lastPublish = lastLoop
            var pendingSteps = 0
            var state = game.state()
            var stateBuilt = lastLoop
            var stateRevision = game.revision
            var warmupLeft = warmupTicks
            while (isActive) {
                val now = TimeSource.Monotonic.markNow()
                val elapsed = now - lastLoop
                lastLoop = now
                val effective = effectiveSpeed()
                tickDebt += elapsed.inWholeMicroseconds / MICROS_PER_SECOND * ticksPerSecond * effective
                // Zu weit zurückgefallen (z. B. Rechner ausgelastet): nicht endlos nachholen
                tickDebt = tickDebt.coerceAtMost(MAX_STEPS_PER_LOOP.toDouble())

                var steps = 0
                val stepStart = TimeSource.Monotonic.markNow()
                lock.withLock {
                    if (warmupLeft > 0) {
                        // Vorlauf: ein Zeitscheibchen lang so viele Schritte wie möglich, dann ein Bild zeigen
                        while (warmupLeft > 0 && pendingReport == null && stepStart.elapsedNow() < WARMUP_SLICE) {
                            game.step()
                            warmupLeft--
                            steps++
                        }
                        tickDebt = 0.0
                    }
                    while (tickDebt >= 1 && pendingReport == null) {
                        game.step()
                        tickDebt--
                        steps++
                    }
                }
                if (pendingReport != null) tickDebt = 0.0
                if (steps > 0) {
                    val perStep = stepStart.elapsedNow().inWholeMicroseconds / MICROS_PER_MILLI / steps
                    stepMillis = stepMillis * (1 - STEP_TIME_BLEND) + perStep * STEP_TIME_BLEND
                }
                pendingSteps += steps

                val changed = game.revision != stateRevision
                val due = lastPublish.elapsedNow() >= PUBLISH_PERIOD
                val reportWaiting = pendingReport != null && frameFlow.value?.pendingReport == null
                if ((pendingSteps > 0 && due) || (effective == 0 && due) || changed || reportWaiting) {
                    if (changed || reportWaiting || stateBuilt.elapsedNow() >= STATE_PERIOD) {
                        state = game.state()
                        stateBuilt = TimeSource.Monotonic.markNow()
                        stateRevision = game.revision
                    }
                    val moved = pendingSteps > 0
                    val current = if (moved) simulation.snapshot() else frameFlow.value?.current ?: previous
                    // Zwischen zwei Veröffentlichungen vergeht im Mittel diese Zeit – so lange wird interpoliert
                    val interval =
                        if (warmupLeft > 0) {
                            PUBLISH_PERIOD
                        } else {
                            maxOf(PUBLISH_PERIOD, (1.0 / ticksPerSecond).seconds / maxOf(effective, 1))
                        }
                    frameFlow.value =
                        TrafficFrame(
                            previous = if (moved) previous else current,
                            current = current,
                            publishedAt = TimeSource.Monotonic.markNow(),
                            interval = interval,
                            stepMillis = stepMillis,
                            speed = speedFlow.value,
                            effectiveSpeed = effective,
                            pendingReport = pendingReport,
                            game = state,
                            warmup = if (warmupLeft > 0) 1f - warmupLeft.toFloat() / warmupTicks else null,
                        )
                    previous = current
                    pendingSteps = 0
                    lastPublish = TimeSource.Monotonic.markNow()
                }
                delay(LOOP_PERIOD)
            }
        }

    private fun effectiveSpeed(): Int {
        val chosen = speedFlow.value
        return when {
            chosen == 0 || pendingReport != null || game.isFinished -> 0
            simulation.gameTime.isNight -> chosen * NIGHT_BOOST
            else -> chosen
        }
    }

    companion object {
        const val MAX_SPEED = 8
        const val NIGHT_BOOST = 8
        private val LOOP_PERIOD = 8.milliseconds

        /** Höchstens so oft ein neues Bild (≈ 60 Bilder je Sekunde). */
        private val PUBLISH_PERIOD = 16.milliseconds

        /** Rechenzeit je Schleifendurchlauf im Vorlauf; danach wird ein Bild gezeigt. */
        private val WARMUP_SLICE = 40.milliseconds

        /** So alt darf der Spielstand in einem Bild höchstens sein, wenn der Spieler nichts tut. */
        private val STATE_PERIOD = 250.milliseconds
        private const val MAX_STEPS_PER_LOOP = 400
        private const val STEP_TIME_BLEND = 0.05
        private const val MICROS_PER_MILLI = 1_000.0
        private const val MICROS_PER_SECOND = 1_000_000.0
    }
}
