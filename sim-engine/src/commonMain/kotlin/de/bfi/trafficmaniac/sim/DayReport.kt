package de.bfi.trafficmaniac.sim

/** Bilanz eines Spieltags (Grundlage für den Tagesabschluss). */
class DayReport(
    val day: Int,
    val weekday: Weekday,
    /** Ins Netz eingefahrene Fahrzeuge je Stunde – die Nachfragekurve des Tages. */
    val tripsStartedPerHour: IntArray,
    val tripsCompleted: Int,
    val meanTripMinutes: Double,
    val peakVehicles: Int,
    val peakHour: Int,
    val stuckRemovals: Int,
) {
    val tripsStarted: Int get() = tripsStartedPerHour.sum()
}

/** Sammelt die Kennzahlen des laufenden Tags. */
internal class DayStatistics {
    private var startedPerHour = IntArray(GameTime.HOURS_PER_DAY)
    private var completed = 0
    private var tripSeconds = 0.0
    private var peakVehicles = 0
    private var peakHour = 0
    private var stuck = 0

    fun tripStarted(time: GameTime) {
        startedPerHour[time.hour]++
    }

    fun tripCompleted(seconds: Double) {
        completed++
        tripSeconds += seconds
    }

    fun vehicleStuck() {
        stuck++
    }

    fun sampleVehicles(
        count: Int,
        time: GameTime,
    ) {
        if (count > peakVehicles) {
            peakVehicles = count
            peakHour = time.hour
        }
    }

    /** Schließt den Tag ab und setzt die Zähler für den nächsten zurück. */
    fun finish(
        day: Int,
        weekday: Weekday,
    ): DayReport {
        val report =
            DayReport(
                day = day,
                weekday = weekday,
                tripsStartedPerHour = startedPerHour,
                tripsCompleted = completed,
                meanTripMinutes = if (completed > 0) tripSeconds / completed / GameTime.SECONDS_PER_MINUTE else 0.0,
                peakVehicles = peakVehicles,
                peakHour = peakHour,
                stuckRemovals = stuck,
            )
        startedPerHour = IntArray(GameTime.HOURS_PER_DAY)
        completed = 0
        tripSeconds = 0.0
        peakVehicles = 0
        peakHour = 0
        stuck = 0
        return report
    }
}
