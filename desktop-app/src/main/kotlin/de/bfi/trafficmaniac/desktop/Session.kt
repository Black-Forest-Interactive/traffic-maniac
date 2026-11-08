package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.Difficulty
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.SaveInfo
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.i18n.tr
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    /**
     * Wartet auf das Ende der Partie. Liefert den Eintrag für die Bestenliste, wenn die Amtszeit regulär endet –
     * wer abgewählt wird, kommt nicht hinein.
     */
    suspend fun result(): HighscoreEntry? {
        val end =
            runner.frames
                .filterNotNull()
                .first { it.game.isTermOver || it.game.isGameOver }
                .game
        if (end.isGameOver || scenario.tutorial) return null
        return HighscoreEntry(
            scenarioId = scenario.id,
            difficulty = difficulty,
            points = end.score.points,
            earnings = end.score.earnings,
            reputation = end.reputation.roundToInt(),
            date = LocalDate.now().toString(),
            honoraryCitizen = end.reputation >= HONORARY_CITIZEN,
        )
    }

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
        private const val HONORARY_CITIZEN = 70.0
        private const val SECONDS_PER_MINUTE = 60

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
            repeat(warmupTicks) { game.step() }
            val runner = GameRunner(game)
            runner.setSpeed(settings.startSpeed)
            return Session(pack, scenario, settings.difficulty, game, runner)
        }
    }
}
