package de.bfi.trafficmaniac.sim

enum class Weekday(
    val germanName: String,
) {
    MONDAY("Montag"),
    TUESDAY("Dienstag"),
    WEDNESDAY("Mittwoch"),
    THURSDAY("Donnerstag"),
    FRIDAY("Freitag"),
    SATURDAY("Samstag"),
    SUNDAY("Sonntag"),
    ;

    val isWorkday: Boolean get() = this != SATURDAY && this != SUNDAY
}

/**
 * Spielzeit: [day] zählt ab 0 (Montag der ersten Woche), [secondOfDay] läuft von 0 bis 86.400.
 */
data class GameTime(
    val day: Int,
    val secondOfDay: Double,
) {
    val weekday: Weekday get() = Weekday.entries[day % Weekday.entries.size]
    val hour: Int get() = (secondOfDay / SECONDS_PER_HOUR).toInt()
    val minute: Int get() = (secondOfDay / SECONDS_PER_MINUTE).toInt() % MINUTES_PER_HOUR

    /** Nachts (22–5 Uhr) ist kaum Verkehr – die Zeit darf dann schneller laufen. */
    val isNight: Boolean get() = hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR

    /** z. B. "07:05". */
    val clockText: String get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val SECONDS_PER_HOUR = 3_600.0
        const val SECONDS_PER_DAY = 86_400.0
        const val HOURS_PER_DAY = 24
        private const val MINUTES_PER_HOUR = 60
        private const val NIGHT_START_HOUR = 22
        private const val NIGHT_END_HOUR = 5

        /** Spielzeit nach [elapsedSeconds], wenn die Simulation um [startSecondOfDay] am ersten Montag beginnt. */
        fun at(
            elapsedSeconds: Double,
            startSecondOfDay: Double,
        ): GameTime {
            val total = elapsedSeconds + startSecondOfDay
            val day = (total / SECONDS_PER_DAY).toInt()
            return GameTime(day, total - day * SECONDS_PER_DAY)
        }
    }
}
