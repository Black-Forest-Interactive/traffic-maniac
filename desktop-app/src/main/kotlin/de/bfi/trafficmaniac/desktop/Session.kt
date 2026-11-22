package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.Difficulty
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.SaveInfo
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.game.TelemetryContext
import de.bfi.trafficmaniac.game.TelemetryPerformance
import de.bfi.trafficmaniac.game.highscore
import de.bfi.trafficmaniac.game.telemetry
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.HudInput
import de.bfi.trafficmaniac.ui.map.MapDrawTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Eine laufende Partie: Szenario, Schwierigkeitsgrad, Spiel und der Takt, der es antreibt. */
internal class Session(
    val pack: CityPack,
    val scenario: Scenario,
    val difficulty: Difficulty,
    val game: CityGame,
    val runner: GameRunner,
) {
    /** Tasten an die Spieloberfläche: Werkzeuge (Q, W, E …) und Entwickler-Anzeige (F3). */
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
            appVersion = System.getProperty("trafficmaniac.version") ?: "dev",
            platform = "desktop-" + System.getProperty("os.name").substringBefore(' ').lowercase(),
            deviceClass = deviceClass(),
            language = I18n.language.code,
            city = pack.id,
            scenario = scenario.id,
            difficulty = difficulty.name.lowercase(),
            realMinutes = ((System.nanoTime() - started) / NANOS_PER_MINUTE).toInt(),
            speeds = runner.speedSeconds(),
            performance =
                frame?.let {
                    TelemetryPerformance(it.stepMillis, MapDrawTime.millis, MapDrawTime.framesPerSecond)
                },
        )
    }

    /**
     * Wartet auf das Ende der Partie. Liefert den Eintrag für die Bestenliste, wenn die Amtszeit regulär endet –
     * wer abgewählt wird, kommt nicht hinein.
     */
    suspend fun result(): HighscoreEntry? = runner.highscore(scenario, difficulty) { LocalDate.now().toString() }

    /** Speichert die Partie unter [id] – zwischen zwei Simulationsschritten, das Spiel läuft danach weiter. */
    suspend fun save(
        saves: SaveGames,
        id: String,
    ): SaveInfo =
        runner.paused { game ->
            val state = game.state()
            val time = game.simulation.gameTime
            val info =
                SaveInfo(
                    id = id,
                    cityId = pack.id,
                    cityName = pack.name,
                    scenarioId = scenario.id,
                    scenarioTitle = scenario.title,
                    difficulty = difficulty,
                    savedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                    gameTime =
                        tr(
                            "{germanName}, Tag {day}/{termDays}, " +
                                "{clockText} Uhr",
                            "germanName" to time.weekday.germanName,
                            "day" to state.termDay(time.day),
                            "termDays" to state.termDays,
                            "clockText" to time.clockText,
                        ),
                    balance = state.balance,
                    reputation = state.reputation.roundToInt(),
                )
            saves.save(game, info)
            game.announce(if (id == SaveInfo.QUICK_SAVE) tr("Schnell gespeichert") else tr("Spiel gespeichert"))
            info
        }

    companion object {
        private const val SECONDS_PER_MINUTE = 60
        private const val NANOS_PER_MINUTE = 60_000_000_000L
        private const val BYTES_PER_GB = 1L shl 30

        /** Grobe Geräteklasse: Kerne und größter Java-Speicher – genug, um langsame Geräte zu erkennen. */
        private fun deviceClass(): String {
            val runtime = Runtime.getRuntime()
            return "${runtime.availableProcessors()} cores, ${runtime.maxMemory() / BYTES_PER_GB} GB heap"
        }

        /** Setzt einen Spielstand fort; das Spiel steht zunächst, bis der Spieler weiterlaufen lässt. */
        fun resume(
            saves: SaveGames,
            info: SaveInfo,
            pack: CityPack,
            scenario: Scenario,
        ): Session {
            val game = saves.load(info)
            val runner = GameRunner(game)
            runner.setSpeed(0)
            game.announce(tr("Spielstand geladen – Leertaste setzt fort"))
            return Session(pack, scenario, info.difficulty, game, runner)
        }

        /** Bereitet [scenario] vor (Regeln, Schwierigkeitsgrad, Vorlauf); läuft im Hintergrund, kann etwas dauern. */
        fun start(
            pack: CityPack,
            scenario: Scenario,
            settings: GameSettings,
            options: Options,
        ): Session {
            val base = pack.simulationConfig(Balancing.simulationConfig())
            val withOptions = options.peakTrips?.let { base.copy(peakTripsPerHour = it) } ?: base
            val (config, rules) =
                scenario.apply(
                    withOptions,
                    pack.gameRules(Balancing.gameRules()),
                    settings.difficulty,
                )
            val game = CityGame(pack.map, config, rules)
            val warmupTicks =
                (scenario.warmupMinutes + options.warmupMinutes) * SECONDS_PER_MINUTE * config.ticksPerSecond
            // Der Vorlauf läuft im Runner, während das Spiel schon zu sehen ist
            val runner = GameRunner(game, warmupTicks)
            runner.setSpeed(settings.startSpeed)
            runner.autoBrake = settings.autoBrake
            return Session(pack, scenario, settings.difficulty, game, runner)
        }
    }
}
