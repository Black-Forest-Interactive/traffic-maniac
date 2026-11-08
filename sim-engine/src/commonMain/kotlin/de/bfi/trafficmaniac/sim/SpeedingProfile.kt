package de.bfi.trafficmaniac.sim

import kotlin.random.Random

/**
 * Wie weit Raser über dem Limit liegen – kalibriert an echten Messungen:
 *
 * - **Anteil zu schneller Fahrzeuge** ohne Blitzer: Bad Bramstedt (Tempo 30, 2023) 13 %, Allensbach (Tempo 40,
 *   2026) 19 %; auf Straßen, die nach mehr aussehen, deutlich mehr – Allensbach Höhrenbergstraße und Hegne
 *   (Tempo 30, 2022) 25–30 % allein bis 5 km/h drüber. Daher Grundwahrscheinlichkeit ~15 %, mit der
 *   „Straßenwirkung“ (gefühltes Tempo minus Limit) bis gut 30 %.
 * - **Verteilung der Überschreitung** unter den Rasern ([CLASSES]): Nach Abzug der 3 km/h Toleranz entfallen
 *   damit rund 76 % der Verstöße auf bis 10 km/h, 16 % auf 11–15, 6 % auf 16–20 und 2 % auf mehr –
 *   Bamberg (~2018) 74/20/5/1, Hambrücken (2. Quartal 2026) 77/18/5. Verkehrsdisplays sehen mehr knappe
 *   Überschreitungen (Bad Bramstedt: die Hälfte nur 1–4 km/h) – die meisten Raser werden also nie geblitzt.
 * - **An festen Blitzern** bremsen Ortskundige ab (siehe [SpeedControl]); übrig bleiben die Ortsfremden.
 *   Das ergibt wenige Prozent Überschreitungen wie in der UDV-Evaluation (innerorts ~3 % über, ~0,2 % > 10 km/h).
 */
internal object SpeedingProfile {
    /** Klassen der Überschreitung: Obergrenze in km/h und Anteil unter den Rasern. */
    val CLASSES: List<Pair<Double, Double>> =
        listOf(
            5.0 to 0.45,
            10.0 to 0.275,
            15.0 to 0.17,
            20.0 to 0.083,
            25.0 to 0.016,
            40.0 to 0.006,
        )

    /** Würfelt eine Überschreitung in km/h (mindestens knapp über 0). */
    fun sample(random: Random): Double {
        var roll = random.nextDouble() * CLASSES.sumOf { it.second }
        var lower = MIN_EXCESS_KMH
        for ((upper, share) in CLASSES) {
            if (roll < share) return random.nextDouble(lower, upper)
            roll -= share
            lower = upper
        }
        return CLASSES.last().first
    }

    private const val MIN_EXCESS_KMH = 0.5
}
