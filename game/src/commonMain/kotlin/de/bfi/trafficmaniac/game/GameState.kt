package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.GameTime

/** Meldung für den Spieler, z. B. "Zu wenig Geld" oder "Blitzer aufgestellt". */
data class GameMessage(
    val time: GameTime,
    val text: String,
)

/** Unveränderlicher Spielstand für die Oberfläche. */
data class GameState(
    val balance: Long,
    val fineIncome: Long,
    val expenses: Long,
    /** Vom Spieler geänderte Tempolimits je Straßenabschnitt. */
    val speedLimits: Map<Int, Int>,
    /** Knoten mit Ampel (ursprüngliche und gebaute). */
    val signalNodes: Set<Int>,
    val cameras: List<CameraRecord>,
    val messages: List<GameMessage>,
    val prContract: PrContract,
    val argumentsCampaign: Boolean,
    /** Anteil unzufriedener Bürger (0..1). */
    val unhappyShare: Double,
    /** Bürgerzorn 0..100. */
    val anger: Double,
    /** Abwahlindex 0..100 – bei 100 ist das Spiel verloren. */
    val recallIndex: Double,
    /** Öffentliches Image 0..100. */
    val reputation: Double,
    val zoneMoods: List<ZoneMood>,
    val lastBudgetRound: BudgetRound?,
    val isGameOver: Boolean,
    val daysPlayed: Int,
    val termDays: Int,
    /** Amtszeit regulär beendet – jetzt wird gewertet. */
    val isTermOver: Boolean,
    val score: Score,
    val latestEdition: NewspaperEdition?,
)

/** Wertung: Es zählen nur Geld und Image – ob die Stadt in Trümmern liegt, ist egal. */
data class Score(
    val balance: Long,
    val reputation: Double,
    val reputationFactor: Double,
) {
    val points: Long get() = (balance * reputationFactor).toLong()
}
