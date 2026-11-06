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
    val cameraCost: Long = 90_000,
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
    /** Ab diesem Anteil wirkt PR nur noch halb. */
    val prLimitShare: Double = 0.7,
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
    val priorityRoadCost: Long = 3_000,
    val bikeCrossingCost: Long = 8_000,
) {
    companion object {
        val DEFAULT_SPEED_LIMITS = listOf(7, 10, 20, 30, 40, 50, 70)
    }
}
