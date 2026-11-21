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

/** So viel langsamer als gewählt darf das erreichte Tempo sein, ehe die Anzeige es meldet. */
private const val LAG_TOLERANCE = 0.15

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
    /**
     * Tatsächlich erreichter Zeitraffer (gemessen, geglättet) – kleiner als [effectiveSpeed], wenn das Gerät nicht
     * nachkommt.
     */
    val actualSpeed: Double = effectiveSpeed.toDouble(),
    /** Die Auto-Bremse hat von diesem Tempo auf [GameRunner.BRAKE_TO] gebremst; `null`, wenn nicht. */
    val brakedFrom: Int? = null,
    /** „Vorspulen bis zum nächsten Ereignis“ läuft. */
    val fastForward: Boolean = false,
) {
    /** Das Gerät schafft das gewählte Tempo nicht (mehr als 15 % langsamer). */
    val lagging: Boolean get() = effectiveSpeed > 0 && actualSpeed < effectiveSpeed * (1 - LAG_TOLERANCE)

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
 * - Nachts (22–5 Uhr) läuft die Zeit automatisch [NIGHT_BOOST]-mal schneller, höchstens [NIGHT_MAX]-fach.
 * - Um Mitternacht hält die Simulation an, bis der Spieler den Tagesabschluss bestätigt.
 * - Auto-Bremse: Passiert etwas, das der Spieler sehen sollte ([CityGame.alerts]), fällt ein Tempo ab [BRAKE_FROM] auf
 *   [BRAKE_TO]; [resumeSpeed] stellt das alte wieder her. [fastForward] spult bis zum nächsten solchen Ereignis vor.
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

    /** Auto-Bremse an (Einstellung des Spielers). Vorspulen hält immer am nächsten Ereignis. */
    var autoBrake = true

    @kotlin.concurrent.Volatile
    private var brakedFrom: Int? = null

    @kotlin.concurrent.Volatile
    private var fastForwardFrom: Int? = null

    /** Echte Sekunden je gewähltem Tempo (für die Statistik, Paket F). */
    private val secondsPerSpeed = HashMap<Int, Double>()

    /** Echte Sekunden je gewähltem Tempo, gerundet ("0" = Pause). */
    fun speedSeconds(): Map<String, Int> = secondsPerSpeed.entries.associate { (speed, s) -> "$speed" to s.toInt() }

    /** Hält die Simulation zwischen zwei Schritten an, solange jemand das Spiel ansieht (z. B. zum Speichern). */
    private val lock = Mutex()
    private var acknowledgedReports = simulation.dayReports.size

    fun setSpeed(value: Int) {
        speedFlow.value = value.coerceIn(0, MAX_SPEED)
        if (value > 0) lastSpeed = value
        brakedFrom = null
        fastForwardFrom = null
    }

    fun togglePause() {
        setSpeed(if (speedFlow.value == 0) lastSpeed else 0)
    }

    /** Eine Stufe schneller oder (mit [step] < 0) langsamer; aus der Pause heraus mit der kleinsten Stufe. */
    fun stepSpeed(step: Int) {
        val index = SPEEDS.indexOf(speedFlow.value)
        val next = if (index < 0) 0 else (index + step).coerceIn(0, SPEEDS.lastIndex)
        setSpeed(SPEEDS[next])
    }

    /** Nach der Auto-Bremse: mit dem Tempo von vorher weiter. */
    fun resumeSpeed() {
        brakedFrom?.let(::setSpeed)
    }

    /** Mit höchstem Tempo bis zum nächsten Ereignis, danach wieder das Tempo von jetzt. */
    fun fastForward() {
        val current = speedFlow.value.takeIf { it > 0 } ?: lastSpeed
        setSpeed(MAX_SPEED)
        fastForwardFrom = current
    }

    /** Ein Ereignis ist passiert: Vorspulen beenden bzw. automatisch bremsen. */
    private fun alert() {
        val chosen = speedFlow.value
        val forwardFrom = fastForwardFrom
        when {
            forwardFrom != null -> {
                setSpeed(forwardFrom)
            }

            autoBrake && chosen >= BRAKE_FROM -> {
                speedFlow.value = BRAKE_TO
                brakedFrom = chosen
            }
        }
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
            val loop = RunLoop(simulation.snapshot(), game.state(), game.revision, warmupTicks, game.alerts)
            while (isActive) {
                val now = TimeSource.Monotonic.markNow()
                val elapsed = now - loop.lastLoop
                loop.lastLoop = now
                val effective = effectiveSpeed()
                val chosen = speedFlow.value
                val seconds = elapsed.inWholeMicroseconds / MICROS_PER_SECOND
                secondsPerSpeed[chosen] = (secondsPerSpeed[chosen] ?: 0.0) + seconds
                loop.tickDebt += seconds * simulation.config.ticksPerSecond * effective
                // Zu weit zurückgefallen (z. B. Rechner ausgelastet): nicht endlos nachholen
                loop.tickDebt = loop.tickDebt.coerceAtMost(MAX_STEPS_PER_LOOP.toDouble())
                val stepStart = TimeSource.Monotonic.markNow()
                val steps = simulate(loop, stepStart)
                // Ereignisse erst nach dem Vorlauf beachten
                if (loop.warmupLeft == 0 && game.alerts != loop.seenAlerts) alert()
                loop.seenAlerts = game.alerts
                measure(loop, steps, seconds, stepStart)
                publish(loop, effective)
                delay(LOOP_PERIOD)
            }
        }

    /** Was [run] von Durchlauf zu Durchlauf mitführt. */
    private class RunLoop(
        var previous: SimulationSnapshot,
        var state: GameState,
        var stateRevision: Int,
        var warmupLeft: Int,
        var seenAlerts: Int,
    ) {
        var stepMillis = 0.0
        var tickDebt = 0.0
        var lastLoop = TimeSource.Monotonic.markNow()
        var lastPublish = lastLoop
        var pendingSteps = 0
        var stateBuilt = lastLoop
        var actualSpeed = 0.0
    }

    /** Rechnet die fälligen Schritte (im Vorlauf so viele wie in ein Zeitscheibchen passen); liefert ihre Zahl. */
    private suspend fun simulate(
        loop: RunLoop,
        stepStart: TimeSource.Monotonic.ValueTimeMark,
    ): Int {
        var steps = 0
        lock.withLock {
            if (loop.warmupLeft > 0) {
                // Vorlauf: ein Zeitscheibchen lang so viele Schritte wie möglich, dann ein Bild zeigen
                while (loop.warmupLeft > 0 && pendingReport == null && stepStart.elapsedNow() < WARMUP_SLICE) {
                    game.step()
                    loop.warmupLeft--
                    steps++
                }
                loop.tickDebt = 0.0
            }
            while (loop.tickDebt >= 1 && pendingReport == null) {
                game.step()
                loop.tickDebt--
                steps++
            }
        }
        if (pendingReport != null) loop.tickDebt = 0.0
        return steps
    }

    /** Erreichtes Tempo (Spielsekunden je echter Sekunde) und Rechenzeit je Schritt, beides geglättet. */
    private fun measure(
        loop: RunLoop,
        steps: Int,
        seconds: Double,
        stepStart: TimeSource.Monotonic.ValueTimeMark,
    ) {
        if (seconds > 0 && loop.warmupLeft == 0) {
            val measured = steps.toDouble() / simulation.config.ticksPerSecond / seconds
            loop.actualSpeed = loop.actualSpeed * (1 - SPEED_BLEND) + measured * SPEED_BLEND
        }
        if (steps > 0) {
            val perStep = stepStart.elapsedNow().inWholeMicroseconds / MICROS_PER_MILLI / steps
            loop.stepMillis = loop.stepMillis * (1 - STEP_TIME_BLEND) + perStep * STEP_TIME_BLEND
        }
        loop.pendingSteps += steps
    }

    /** Veröffentlicht ein neues Bild, wenn es fällig ist oder der Spieler etwas getan hat. */
    private fun publish(
        loop: RunLoop,
        effective: Int,
    ) {
        val changed = game.revision != loop.stateRevision
        val due = loop.lastPublish.elapsedNow() >= PUBLISH_PERIOD && (loop.pendingSteps > 0 || effective == 0)
        val reportWaiting = pendingReport != null && frameFlow.value?.pendingReport == null
        if (!due && !changed && !reportWaiting) return
        if (changed || reportWaiting || loop.stateBuilt.elapsedNow() >= STATE_PERIOD) {
            loop.state = game.state()
            loop.stateBuilt = TimeSource.Monotonic.markNow()
            loop.stateRevision = game.revision
        }
        val moved = loop.pendingSteps > 0
        val current = if (moved) simulation.snapshot() else frameFlow.value?.current ?: loop.previous
        frameFlow.value = frame(loop, effective, if (moved) loop.previous else current, current)
        loop.previous = current
        loop.pendingSteps = 0
        loop.lastPublish = TimeSource.Monotonic.markNow()
    }

    /** Ein Bild für die Oberfläche; zwischen [previous] und [current] wird interpoliert. */
    private fun frame(
        loop: RunLoop,
        effective: Int,
        previous: SimulationSnapshot,
        current: SimulationSnapshot,
    ): TrafficFrame {
        // Zwischen zwei Veröffentlichungen vergeht im Mittel diese Zeit – so lange wird interpoliert
        val interval =
            if (loop.warmupLeft > 0) {
                PUBLISH_PERIOD
            } else {
                maxOf(PUBLISH_PERIOD, (1.0 / simulation.config.ticksPerSecond).seconds / maxOf(effective, 1))
            }
        return TrafficFrame(
            previous = previous,
            current = current,
            publishedAt = TimeSource.Monotonic.markNow(),
            interval = interval,
            stepMillis = loop.stepMillis,
            speed = speedFlow.value,
            effectiveSpeed = effective,
            pendingReport = pendingReport,
            game = loop.state,
            warmup = if (loop.warmupLeft > 0) 1f - loop.warmupLeft.toFloat() / warmupTicks else null,
            actualSpeed = if (effective == 0) 0.0 else loop.actualSpeed,
            brakedFrom = brakedFrom,
            fastForward = fastForwardFrom != null,
        )
    }

    private fun effectiveSpeed(): Int {
        val chosen = speedFlow.value
        return when {
            chosen == 0 || pendingReport != null || game.isFinished -> 0
            simulation.gameTime.isNight -> minOf(chosen * NIGHT_BOOST, NIGHT_MAX)
            else -> chosen
        }
    }

    companion object {
        /** Die wählbaren Stufen (Desktop); das Handy bietet nur bis [TOUCH_MAX_SPEED]. */
        val SPEEDS = listOf(1, 2, 4, 8, 16, 32, 64)

        /** Die Knöpfe der Kopfleiste (Tasten 1–5); 16× und 32× nur mit +/−. */
        val TOP_SPEEDS = listOf(1, 2, 4, 8, 64)
        const val MAX_SPEED = 64
        const val TOUCH_MAX_SPEED = 16
        const val NIGHT_BOOST = 8
        const val NIGHT_MAX = 256

        /** Auto-Bremse: ab diesem Tempo … */
        const val BRAKE_FROM = 16

        /** … auf dieses. */
        const val BRAKE_TO = 4
        private const val SPEED_BLEND = 0.02
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
