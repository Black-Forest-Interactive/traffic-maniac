package de.bfi.trafficmaniac.game

import kotlinx.serialization.Serializable

/** Ein Eintrag in der Bestenliste: eine regulär beendete Amtszeit. */
@Serializable
data class HighscoreEntry(
    val scenarioId: String,
    val difficulty: Difficulty,
    val points: Long,
    val earnings: Long,
    val reputation: Int,
    /** Datum als Text, z. B. "2026-11-07". */
    val date: String,
    val honoraryCitizen: Boolean = false,
)

/** Lokale Bestenliste: je Szenario und Schwierigkeitsgrad die besten zehn. */
@Serializable
data class Highscores(
    val entries: List<HighscoreEntry> = emptyList(),
) {
    fun of(
        scenarioId: String,
        difficulty: Difficulty,
    ): List<HighscoreEntry> =
        entries.filter { it.scenarioId == scenarioId && it.difficulty == difficulty }.sortedByDescending { it.points }

    fun best(scenarioId: String): HighscoreEntry? =
        entries.filter { it.scenarioId == scenarioId }.maxByOrNull { it.points }

    /** Fügt [entry] hinzu; je Szenario und Schwierigkeitsgrad bleiben nur die besten [MAX_PER_TABLE]. */
    fun add(entry: HighscoreEntry): Highscores {
        val table =
            (
                of(
                    entry.scenarioId,
                    entry.difficulty,
                ) + entry
            ).sortedByDescending { it.points }.take(MAX_PER_TABLE)
        val others = entries.filterNot { it.scenarioId == entry.scenarioId && it.difficulty == entry.difficulty }
        return Highscores(others + table)
    }

    /** Platz von [entry] in seiner Tabelle (ab 1), `null`, wenn er es nicht hineingeschafft hat. */
    fun rankOf(entry: HighscoreEntry): Int? =
        of(entry.scenarioId, entry.difficulty).indexOf(entry).takeIf { it >= 0 }?.plus(1)

    companion object {
        const val MAX_PER_TABLE = 10
    }
}

/** Einstellungen des Spielers. */
@Serializable
data class GameSettings(
    val sound: Boolean = true,
    val music: Boolean = true,
    /** Fertige Grafiken aus `assets/sprites/` verwenden (sonst immer gezeichnet). */
    val sprites: Boolean = true,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Zeitraffer beim Spielstart (1, 2, 4 oder 8). */
    val startSpeed: Int = 1,
    /** Sprache als Kürzel („de“, „en“); `null` = Sprache des Systems. */
    val language: String? = null,
)
