package de.bfi.trafficmaniac.game

import kotlinx.serialization.Serializable

/** Eckdaten eines Spielstands – was das Menü in der Liste zeigt, ohne den ganzen Spielstand zu laden. */
@Serializable
data class SaveInfo(
    /** Dateiname ohne Endung; „schnell“ ist der Schnellspeicher-Platz. */
    val id: String,
    /** Stadtpaket (`cities/<id>`); ältere Spielstände stammen alle aus Pforzheim. */
    val cityId: String = "pforzheim",
    val cityName: String = "Pforzheim",
    val scenarioId: String,
    val scenarioTitle: String,
    val difficulty: Difficulty,
    /** Zeitpunkt des Speicherns (ISO 8601) – zum Sortieren und Anzeigen. */
    val savedAt: String,
    /** Spielzeit beim Speichern, z. B. „Mittwoch, Tag 3, 14:20 Uhr“. */
    val gameTime: String,
    val balance: Long,
    val reputation: Int,
    val version: Int = VERSION,
) {
    companion object {
        /** Ändert sich, wenn alte Spielstände nicht mehr zum Spiel passen. */
        const val VERSION = 1
        const val QUICK_SAVE = "schnell"
    }
}
