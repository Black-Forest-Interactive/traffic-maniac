package de.bfi.trafficmaniac.android

import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.game.highscore
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.ui.HudInput
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

    suspend fun result(): HighscoreEntry? =
        runner.highscore(scenario, settings.difficulty) { LocalDate.now().toString() }

    companion object {
        private const val SECONDS_PER_MINUTE = 60

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
            repeat(scenario.warmupMinutes * SECONDS_PER_MINUTE * config.ticksPerSecond) { game.step() }
            val runner = GameRunner(game)
            runner.setSpeed(settings.startSpeed)
            return Session(city, scenario, settings, game, runner)
        }
    }
}
