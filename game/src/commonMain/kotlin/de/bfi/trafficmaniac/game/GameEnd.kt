package de.bfi.trafficmaniac.game

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Wartet auf das Ende der Partie. Liefert den Eintrag für die Bestenliste (Datum [today], z. B. `2026-11-10`), wenn
 * die Amtszeit regulär endet – wer abgewählt wird oder im Tutorial spielt, kommt nicht hinein.
 */
suspend fun GameRunner.highscore(
    scenario: Scenario,
    difficulty: Difficulty,
    today: () -> String,
): HighscoreEntry? {
    val end =
        frames
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
        date = today(),
        honoraryCitizen = end.reputation >= HONORARY_CITIZEN,
    )
}

/** Ab diesem Ansehen wird man Ehrenbürger. */
private const val HONORARY_CITIZEN = 70.0
