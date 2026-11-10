package de.bfi.trafficmaniac.game

import kotlinx.serialization.Serializable

/** Spielwerte, die das Balancing später über Konfigurationsdateien anpasst. */
@Serializable
data class GameRules(
    /** Amtszeit in Spieltagen. Wer bis zum Ende durchhält, wird gewertet. */
    val termDays: Int = 14,
    /** Punkte = Kasse × Faktor; der Faktor wächst linear mit der Reputation von min bis max. */
    val minReputationFactor: Double = 0.5,
    val maxReputationFactor: Double = 2.0,
    val startBudget: Long = 250_000,
    val speedLimitSignCost: Long = 2_000,
    val signalCost: Long = 60_000,
    val signalRemovalCost: Long = 5_000,
    /** Ampelprogramm ändern (Aufteilung der Grünzeit oder grüne Welle), je Anlage. */
    val signalProgramCost: Long = 1_000,
    /** Eigene Baustelle (Tempo 30, verengt): Einrichtung und Absicherung je Tag, je Strecke. */
    val roadworksSetupCost: Long = 1_000,
    val roadworksDailyCost: Long = 200,
    /** Vollsperrung durch den Spieler, je Strecke. */
    val closureCost: Long = 500,
    /** Räumfahrzeug im Winterdienst, je Tag. */
    val ploughDailyCost: Long = 1_500,
    val maxPloughs: Int = 12,
    /** Ab so vielen Tagen fällt auf, dass an einer Baustelle niemand arbeitet … */
    val fakeRoadworksGraceDays: Int = 2,
    /** … mit dieser Wahrscheinlichkeit je Tag … */
    val fakeRoadworksPressChance: Double = 0.3,
    /** … und kostet dann so viel Reputation. */
    val fakeRoadworksReputationLoss: Double = 3.0,
    val cameraCost: Long = 90_000,
    /** Rotlichtblitzer an einer Ampelzufahrt, mit Tempomessung teurer. */
    val redLightCameraCost: Long = 70_000,
    val redLightSpeedCameraCost: Long = 120_000,
    /** Geschwindigkeits-Anzeigetafel (blitzt nicht). */
    val displayCost: Long = 2_500,
    /** Polizeikontrolle: Einsatz für [policeCheckHours] Stunden, höchstens [maxPoliceChecks] gleichzeitig. */
    val policeCheckCost: Long = 1_200,
    val policeCheckHours: Double = 3.0,
    val maxPoliceChecks: Int = 2,
    /** Fahrradstraße einrichten (Markierung, Schilder), je Strecke. */
    val bikeStreetCost: Long = 5_000,
    /** Wählbare Tempolimits; 7 km/h steht für Schrittgeschwindigkeit. */
    val speedLimitOptions: List<Int> = DEFAULT_SPEED_LIMITS,
    /** Fester Haushalt, der jede Woche (Sonntag um Mitternacht) überwiesen wird. */
    val weeklyAllowance: Long = 60_000,
    /** Kampagne "Totschlagargumente" pro Woche. */
    val argumentsWeeklyCost: Long = 8_000,
    /** Bis zu diesem Verhältnis Fahrzeit / gewohnte Fahrzeit ist alles in Ordnung. */
    val acceptableDelayRatio: Double = 1.5,
    /** Ab diesem Verhältnis ist "schnell am Ziel" völlig unerfüllt. */
    val unbearableDelayRatio: Double = 3.0,
    /** Bußgeld je Fahrt (gleitender Mittelwert in Euro), ab dem "Mobilität" völlig unerfüllt ist. */
    val unbearableFinePerTrip: Double = 4.0,
    val finePainHalfLifeHours: Double = 24.0,
    /** Bis zu diesem Lärmpegel (dB) ist es ruhig ... */
    val quietGoodDb: Double = 50.0,
    /** ... ab diesem unerträglich. */
    val quietBadDb: Double = 70.0,
    /** Abgase und Feinstaub (g je 50-m-Zelle in der Nachbarschaft), bis zu denen die Luft gut ist ... */
    val airGoodGrams: Double = 2.0,
    /** ... und ab denen sie unerträglich ist. */
    val airBadGrams: Double = 15.0,
    /** Pendler planen die gewohnte Fahrzeit mal diesen Faktor ein; wer länger braucht, kommt zu spät. */
    val plannedTimeFactor: Double = 1.5,
    /** "Volkswirtschaftlicher Schaden" je verlorener Minute. */
    val damagePerLostMinute: Double = 0.6,
    /** Die Totschlagargumente lassen Bußgelder nur noch so weh tun. */
    val argumentsPainFactor: Double = 0.6,
    /** Unter diesem Anteil Unzufriedener gibt es keinen Zorn. */
    val angerThreshold: Double = 0.35,
    /** Ab diesem Anteil ist der Zorn maximal. */
    val angerFullAt: Double = 0.75,
    /** Ab diesem Anteil Unzufriedener verpufft PR allmählich ... */
    val prLimitShare: Double = 0.7,
    /** ... und ab diesem hilft auch die schönste Story nichts mehr (kein Dämpfen, kein Image, kritische Presse). */
    val prNoEffectShare: Double = 0.85,
    /** Der Abwahlindex steigt, solange der Zorn darüber liegt ... */
    val recallAngerThreshold: Double = 30.0,
    /** ... und zwar um (Zorn − Schwelle) / [recallDivisor] pro Spielstunde. */
    val recallDivisor: Double = 8.0,
    val startReputation: Double = 50.0,
    val reputationLossPerAnger: Double = 0.01,
    val argumentsReputationPerHour: Double = 0.1,
    /** Ohne PR-Vertrag berichtet die Presse über Abzocke, sobald pro Stunde so viel Bußgeld eingeht. */
    val ripOffPressFines: Long = 1_500,
    val ripOffReputationLoss: Double = 0.25,
    val initialStaff: Int = 4,
    val staffWeeklySalary: Long = 2_500,
    val hireDelayHours: Int = 48,
    val casesPerStaffHour: Int = 15,
    val officeStartHour: Int = 8,
    val officeEndHour: Int = 16,
    val identificationBase: Double = 0.55,
    val identificationMax: Double = 0.95,
    val identificationStaffScale: Double = 8.0,
    val questionnairePayShare: Double = 0.4,
    val logbookPayShare: Double = 0.85,
    val logbookPainFactor: Double = 2.0,
    val defaultDeadlineDays: Int = 3,
    val legalMaxDeadlineDays: Int = 10,
    val revocationPoints: Int = 8,
    val revocationDays: Int = 5,
    /** So sehr ärgert ein Fahrverbot das Wohngebiet (in "Euro Bußgeld"). */
    val licenseLossPain: Double = 400.0,
    val laserDailyCost: Long = 1_500,
    /** Texte der Stadt (aus dem Stadtpaket). */
    val city: CityTexts = CityTexts(),
    /** Blitzeranhänger: einmal kaufen, danach gegen eine kleine Gebühr beliebig umsetzen. */
    val trailerCost: Long = 45_000,
    val trailerMoveCost: Long = 500,
    val priorityRoadCost: Long = 3_000,
    val bikeCrossingCost: Long = 8_000,
    /** Frist, innerhalb der auf eine Schadensmeldung reagiert werden muss. */
    val damageDeadlineHours: Int = 48,
    val warningSignCost: Long = 500,
    /** Gewichtslimit, das ein Warnschild an einer maroden Brücke mitbringt. */
    val bridgeWeightLimitTons: Float = 7.5f,
    val bridgeClosureCost: Long = 2_000,
    val repairCostPotholes: Long = 25_000,
    val repairCostCrater: Long = 45_000,
    val repairCostSignal: Long = 15_000,
    val repairCostBridge: Long = 180_000,
    /** Jeder weitere beschädigte Abschnitt derselben Straße verteuert die Reparatur um diesen Anteil. */
    val repairCostPerExtraSegment: Double = 0.25,
    /** Normale Ausschreibung: so lange dauert die Vergabe, dann so lange die Baustelle (Vollsperrung). */
    val tenderDays: Double = 2.0,
    val constructionDays: Double = 2.0,
    /** Turbo-Baukonsortium: Aufpreis, Zustand nach der "Reparatur", schnellerer Verfall und Wartungsvertrag. */
    val turboCostFactor: Double = 3.0,
    val turboCondition: Double = 0.75,
    val turboWearFactor: Double = 2.5,
    val turboWeeklyMaintenance: Long = 2_500,
    val planningCost: Long = 4_000,
    val planningStartYear: Int = 2066,
    /** Schadensersatz nach einem Schlagloch-Unfall ohne Warnschild. */
    val lawsuitMin: Long = 4_000,
    val lawsuitMax: Long = 18_000,
    /** Wahrscheinlichkeit je Tag und ignoriertem Krater für eine Reifenschaden-Klage. */
    val craterClaimsPerDay: Double = 0.4,
    val tireClaimCost: Long = 1_500,
    /** Image-Verlust je ignoriertem Krater, über den die Zeitung ohne PR-Vertrag schreibt. */
    val ignoredDamageReputationLoss: Double = 1.0,
    val maintenanceBudgetBasic: Long = 20_000,
    val maintenanceBudgetFull: Long = 45_000,
    val initialWardens: Int = 2,
    val wardenWeeklySalary: Long = 2_200,
    val wardenHireDelayHours: Int = 24,
    val wardenStartHour: Int = 7,
    val wardenEndHour: Int = 20,
    /** Erwischt-Wahrscheinlichkeit: 1 − exp(−Politessen × Minuten / diesem Wert). */
    val wardenCoverageMinutes: Double = 900.0,
    /** Mehr Knöllchen schafft eine Politesse pro Stunde nicht. */
    val ticketsPerWardenHour: Int = 6,
    val illegalParkingTicket: Int = 35,
    val secondRowTicket: Int = 55,
    val ticketPayShare: Double = 0.9,
    /** Parkgebühren ärgern weniger als Bußgelder. */
    val parkingFeePainFactor: Double = 0.5,
    val parkingFeeOptions: List<Double> = DEFAULT_PARKING_FEES,
    /** Ein Schritt "Parkplätze in Radspuren/Blumenkübel" kostet so viel und nimmt diesen Anteil weg. */
    val curbConversionCost: Long = 15_000,
    val curbConversionShare: Double = 0.2,
    /** Anwohnerparken: Anteil der Straßenrand-Parkplätze, die für Besucher wegfallen, und Einnahmen pro Woche. */
    val residentParkingCurbShare: Double = 0.4,
    val residentPermitWeekly: Long = 3_000,
    /** Rad-Korso (10–18 Uhr): Chance je Stunde am Wochenende ... */
    val corsoChanceWeekend: Double = 0.1,
    /** ... plus je Dauerstau-Tag in Folge ... */
    val corsoChancePerJamDay: Double = 0.04,
    /** ... plus bis zu diesem Wert, wenn [corsoBannedReference] Bürger ihren Führerschein los sind. */
    val corsoChanceBanned: Double = 0.1,
    val corsoBannedReference: Int = 100,
    /**
     * Ein Tag gilt als Dauerstau-Tag, wenn mindestens dieser Anteil der Pendler zu spät kam – und zugleich
     * [jamExtraLateShare] mehr als am ersten Spieltag (so viele kommen in der Stadt ohnehin zu spät).
     */
    val jamLateShare: Double = 0.2,
    val jamExtraLateShare: Double = 0.1,
    val maxCorsosPerDay: Int = 2,
    /** Spontandemos (9–20 Uhr) erst ab diesem Zorn; die Chance je Stunde wächst bis [demoChancePerHour]. */
    val demoMinAnger: Double = 20.0,
    val demoChancePerHour: Double = 0.25,
    val maxDemosPerDay: Int = 4,
    val demoMinHours: Double = 1.0,
    val demoMaxHours: Double = 3.0,
    /** Schwertransport: Chance je Nacht (Start 23 Uhr). */
    val heavyTransportChancePerNight: Double = 0.4,
    /** Die Zeitung nähert ihren Ton jeden Tag um diesen Anteil der Tonlage an, die der PR-Vertrag vorgibt. */
    val pressToneInertia: Double = 0.6,
    /** Image je Tag bei ganz freundlicher (+) bzw. ganz kritischer (−) Zeitung. */
    val pressReputationPerDay: Double = 1.5,
    /** PR-Aufschlag für die Lücke zwischen Story und Realität: bis zu diesem Anteil der Vertragskosten. */
    val prGapCostFactor: Double = 1.5,
    /** Spatenstich oder Eröffnung: Kosten und Image (jede weitere in derselben Woche bringt weniger). */
    val ceremonyCost: Long = 6_000,
    val ceremonyReputation: Double = 4.0,
) {
    companion object {
        val DEFAULT_SPEED_LIMITS = listOf(7, 10, 20, 30, 40, 50, 70)
        val DEFAULT_PARKING_FEES = listOf(0.0, 1.0, 1.5, 2.0, 3.0, 5.0)
    }
}
