package de.bfi.trafficmaniac.android

import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.game.TelemetryContext
import de.bfi.trafficmaniac.game.TelemetryPerformance
import de.bfi.trafficmaniac.game.highscore
import de.bfi.trafficmaniac.game.telemetry
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.ui.HudInput
import de.bfi.trafficmaniac.ui.map.MapDrawTime
import java.time.LocalDate

/** Eine laufende Partie auf dem Gerät. */
class Session(
    val city: AssetCity,
    val scenario: Scenario,
    private val settings: GameSettings,
    val game: CityGame,
    val runner: GameRunner,
) {
    val input = HudInput()

    private val started = System.nanoTime()
    private var reported = false

    /**
     * Legt das Endpaket dieser Partie ab – einmal je Partie, am Ende der Amtszeit, nach der Abwahl oder wenn eine neue
     * Partie sie ersetzt. Nur mit Zustimmung (prüft der Aufrufer).
     */
    fun report(statistics: Statistics) {
        if (reported) return
        reported = true
        statistics.store(game.telemetry(context(statistics), final = true))
    }

    /**
     * Zwischenstand (Ende „running“) zu Beginn und nach jedem Spieltag – zwischen zwei Simulationsschritten erzeugt,
     * damit das Paket in sich stimmig ist. Ersetzt im Postausgang den vorigen Stand dieser Partie.
     */
    suspend fun progress(statistics: Statistics) {
        if (reported) return
        val telemetry = runner.paused { it.telemetry(context(statistics), final = false) }
        statistics.store(telemetry)
    }

    private fun context(statistics: Statistics): TelemetryContext {
        val frame = runner.frames.value
        return TelemetryContext(
            installation = statistics.installation,
            appVersion = BuildConfig.VERSION_NAME,
            platform = "android",
            deviceClass = deviceClass(),
            language = I18n.language.code,
            city = city.id,
            scenario = scenario.id,
            difficulty = settings.difficulty.name.lowercase(),
            realMinutes = ((System.nanoTime() - started) / NANOS_PER_MINUTE).toInt(),
            speeds = runner.speedSeconds(),
            performance =
                frame?.let {
                    TelemetryPerformance(it.stepMillis, MapDrawTime.millis, MapDrawTime.framesPerSecond)
                },
        )
    }

    suspend fun result(): HighscoreEntry? =
        runner.highscore(scenario, settings.difficulty) { LocalDate.now().toString() }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val NANOS_PER_MINUTE = 60_000_000_000L
        private const val BYTES_PER_MB = 1L shl 20

        /** Grobe Geräteklasse wie am Desktop: Kerne und größter Java-Speicher (auf dem Handy in MB). */
        private fun deviceClass(): String {
            val runtime = Runtime.getRuntime()
            return "${runtime.availableProcessors()} cores, ${runtime.maxMemory() / BYTES_PER_MB} MB heap"
        }

        /** Bereitet [scenario] vor (Regeln, Schwierigkeit, Vorlauf) – im Hintergrund aufrufen, dauert etwas. */
        fun start(
            content: GameContent,
            city: AssetCity,
            scenario: Scenario,
            settings: GameSettings,
        ): Session {
            val (config, rules) =
                scenario.apply(
                    city.info.simulationConfig(content.simulationConfig()),
                    city.info.gameRules(content.gameRules(), I18n.language),
                    settings.difficulty,
                )
            val game = CityGame(city.map, config, rules)
            // Der Vorlauf läuft im Runner, während das Spiel schon zu sehen ist (statt über eine Minute Ladebild)
            val runner = GameRunner(game, scenario.warmupMinutes * SECONDS_PER_MINUTE * config.ticksPerSecond)
            runner.setSpeed(settings.startSpeed)
            runner.autoBrake = settings.autoBrake
            return Session(city, scenario, settings, game, runner)
        }
    }
}
