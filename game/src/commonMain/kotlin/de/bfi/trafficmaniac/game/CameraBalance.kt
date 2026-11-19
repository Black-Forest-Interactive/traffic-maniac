package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import kotlin.math.roundToInt

/**
 * Lerneffekt B1: Blitzer innerorts erwischen vor allem kleine Überschreitungen – und genau damit verdient die Stadt.
 * Alle Zahlen kommen aus den Blitzern des Spiels; die Kästen „Aus der Praxis“ zitieren echte Messungen (dieselben
 * Quellen, an denen `SpeedingProfile` kalibriert ist).
 */
object CameraBalance {
    /** Einnahmen der Tempoblitzer je Stufe (ohne Rotlicht und Polizeikontrollen). */
    fun speedRevenue(cameras: List<CameraRecord>): List<Long> =
        FineTier.SPEED.map { tier -> cameras.sumOf { it.revenuePerTier[tier.ordinal] } }

    /** Anteil der Tempo-Einnahmen aus Verstößen bis 10 km/h (0..1); `null`, solange noch nichts eingenommen wurde. */
    fun smallFishShare(cameras: List<CameraRecord>): Double? {
        val revenue = speedRevenue(cameras)
        val total = revenue.sum()
        return if (total > 0) revenue[FineTier.UP_TO_10.ordinal].toDouble() / total else null
    }

    /** „82 % der Blitzer-Einnahmen stammen aus Verstößen bis 10 km/h.“ – oder `null` ohne Einnahmen. */
    fun summary(cameras: List<CameraRecord>): String? =
        smallFishShare(cameras)?.let {
            tr(
                "{percent} % der Blitzer-Einnahmen stammen aus Verstößen bis 10 km/h.",
                "percent" to (it * PERCENT).roundToInt(),
            )
        }

    /** Echter Messwert für den Kasten „Aus der Praxis“ in der Zeitung – jeden Tag ein anderer. */
    fun practiceFact(day: Int): String = tr(FACTS[day.mod(FACTS.size)])

    private val FACTS =
        listOf(
            translatable(
                "Bamberg: Rund drei Viertel aller geblitzten Verstöße lagen höchstens 10 km/h über dem Limit, " +
                    "nur jeder Hundertste mehr als 20 km/h.",
            ),
            translatable(
                "Bad Bramstedt (Tempo 30, 2023): 13 % der Autos waren zu schnell – die Hälfte davon nur 1 bis 4 km/h.",
            ),
            translatable(
                "Unfallforschung der Versicherer: An festen Blitzern innerorts fahren nur rund 3 % zu schnell, " +
                    "0,2 % mehr als 10 km/h. Wer den Kasten kennt, bremst.",
            ),
            translatable(
                "Hambrücken (2. Quartal 2026): 77 % der Verstöße bis 10 km/h, 18 % bis 15 km/h, 5 % darüber.",
            ),
            translatable(
                "Allensbach (Tempo 40, 2026): 19 % zu schnell. Auf Straßen, die nach mehr aussehen, " +
                    "fahren 25 bis 30 % allein bis 5 km/h drüber.",
            ),
        )

    private const val PERCENT = 100
}
