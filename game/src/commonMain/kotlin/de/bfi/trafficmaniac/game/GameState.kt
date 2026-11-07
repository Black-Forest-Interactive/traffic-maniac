package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.GameTime

/** Meldung für den Spieler, z. B. "Zu wenig Geld" oder "Blitzer aufgestellt". */
data class GameMessage(
    val time: GameTime,
    val text: String,
    /** Wohin ein Klick auf die Meldung führt; `null`, wenn sie keinen Ort hat. */
    val place: MapPlace? = null,
)

/** Unveränderlicher Spielstand für die Oberfläche. */
data class GameState(
    val balance: Long,
    val fineIncome: Long,
    val expenses: Long,
    /** Vom Spieler geänderte Tempolimits je Straßenabschnitt. */
    val speedLimits: Map<Int, Int>,
    /** Tempolimits, die nur nachts gelten. */
    val nightLimits: Map<Int, Int>,
    val priorityRoads: Set<Int>,
    val bikeCrossings: Set<Int>,
    val achievements: List<String>,
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
    val fineOffice: FineOfficeState,
    /** Fahrer mit Fahrverbot oder ohne Führerschein. */
    val bannedDrivers: Int,
    val licenseRevocations: Int,
    /** Pünktlichkeit heute (laufend) und gestern (abgeschlossen). */
    val commuteToday: CommuteStats,
    val commuteYesterday: CommuteStats,
    /** "Volkswirtschaftlicher Schaden" des Vortags durch Verspätungen in Euro. */
    val commuteDamage: Long,
    /** Schadensmeldungen, Reparaturen, Erhaltungsbudget. */
    val maintenance: MaintenanceState = MaintenanceState(),
    /** Parkgebühren, Politessen, Straßenrand. */
    val parkingOffice: ParkingOfficeState = ParkingOfficeState(),
    /** Abschnitte der ausgeschilderten Umleitung. */
    val detourSegments: Set<Int> = emptySet(),
    /** Spielkalender und laufende Ereignisse. */
    val events: EventsState = EventsState(),
    /** Tonlage der Zeitung, PR-Wirkung, nächster Spatenstich. */
    val press: PressState = PressState(),
    /** Alle Achievements mit Freischalt-Tag ([achievements] enthält nur die Titel der freigeschalteten). */
    val achievementList: List<AchievementStatus> = emptyList(),
    /** Gekaufte Blitzeranhänger und davon gerade im Depot (frei zum Aufstellen). */
    val trailers: Int = 0,
    val idleTrailers: Int = 0,
    /** Erster Spieltag (Tage seit dem Montag der ersten Woche) – Szenarien beginnen nicht immer montags. */
    val firstDay: Int = 0,
) {
    /** Tag der Amtszeit, ab 1 gezählt. */
    fun termDay(day: Int): Int = day - firstDay + 1
}

/**
 * Wertung: Es zählen nur selbst verdientes Geld und Image – ob die Stadt in Trümmern liegt, ist egal.
 * Verdient ist, was über Startbudget und überwiesenen Haushalt hinaus in der Kasse liegt.
 */
data class Score(
    val earnings: Long,
    val reputation: Double,
    val reputationFactor: Double,
) {
    val points: Long get() = (earnings * reputationFactor).toLong()
}
