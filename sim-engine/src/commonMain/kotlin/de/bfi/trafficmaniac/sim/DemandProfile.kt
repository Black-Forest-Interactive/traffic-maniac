package de.bfi.trafficmaniac.sim

import kotlinx.serialization.Serializable

/**
 * Tagesgang der Verkehrsnachfrage: Faktor je Stunde (0..1) relativ zur Spitzenstunde,
 * getrennt nach Werktag, Samstag und Sonntag. Zwischen den Stundenmitten wird linear interpoliert.
 */
@Serializable
class DemandProfile(
    val workday: DoubleArray,
    val saturday: DoubleArray,
    val sunday: DoubleArray,
) {
    init {
        require(
            listOf(workday, saturday, sunday).all { it.size == GameTime.HOURS_PER_DAY },
        ) { "24 hourly factors required" }
    }

    fun factor(time: GameTime): Double {
        val hours =
            when (time.weekday) {
                Weekday.SATURDAY -> saturday
                Weekday.SUNDAY -> sunday
                else -> workday
            }
        // Stundenwerte gelten für die Stundenmitte
        val position = time.secondOfDay / GameTime.SECONDS_PER_HOUR - HALF_HOUR
        val lower = kotlin.math.floor(position).toInt()
        val fraction = position - lower
        val a = hours[(lower + GameTime.HOURS_PER_DAY) % GameTime.HOURS_PER_DAY]
        val b = hours[(lower + 1) % GameTime.HOURS_PER_DAY]
        return a + (b - a) * fraction
    }

    companion object {
        private const val HALF_HOUR = 0.5

        /** Gleichmäßige Nachfrage rund um die Uhr (für Tests und Lastmessungen). */
        val CONSTANT = DoubleArray(GameTime.HOURS_PER_DAY) { 1.0 }.let { DemandProfile(it, it, it) }

        /** Typischer Stadtverkehr: Spitzen um 7–8 und 16–18 Uhr, samstags Einkaufsverkehr, sonntags Ausflüge. */
        val DEFAULT =
            DemandProfile(
                workday =
                    doubleArrayOf(
                        0.03,
                        0.02,
                        0.02,
                        0.02,
                        0.05,
                        0.20,
                        0.55,
                        1.00,
                        0.90,
                        0.60,
                        0.50,
                        0.55,
                        0.60,
                        0.55,
                        0.55,
                        0.70,
                        0.90,
                        1.00,
                        0.80,
                        0.55,
                        0.35,
                        0.25,
                        0.15,
                        0.08,
                    ),
                saturday =
                    doubleArrayOf(
                        0.06,
                        0.04,
                        0.03,
                        0.02,
                        0.03,
                        0.06,
                        0.12,
                        0.25,
                        0.45,
                        0.65,
                        0.75,
                        0.80,
                        0.75,
                        0.65,
                        0.60,
                        0.60,
                        0.60,
                        0.55,
                        0.45,
                        0.35,
                        0.30,
                        0.25,
                        0.18,
                        0.12,
                    ),
                sunday =
                    doubleArrayOf(
                        0.08,
                        0.05,
                        0.03,
                        0.02,
                        0.02,
                        0.03,
                        0.05,
                        0.08,
                        0.15,
                        0.25,
                        0.35,
                        0.40,
                        0.40,
                        0.40,
                        0.45,
                        0.45,
                        0.45,
                        0.40,
                        0.35,
                        0.30,
                        0.25,
                        0.18,
                        0.12,
                        0.06,
                    ),
            )
    }
}
