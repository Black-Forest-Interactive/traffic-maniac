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
    val incidents: Map<IncidentKind, Int> = emptyMap(),
    /** Anfahrtszeiten der Rettungswagen in Minuten. */
    val ambulanceMinutes: List<Double> = emptyList(),
    /** Wartezeiten an Ladesäulen in Minuten. */
    val chargingWaitMinutes: List<Double> = emptyList(),
    /** Lieferwagen, die in zweiter Reihe gehalten haben. */
    val secondRowStops: Int = 0,
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
    private var incidents = HashMap<IncidentKind, Int>()
    private var ambulance = ArrayList<Double>()
    private var chargingWaits = ArrayList<Double>()

    fun incident(kind: IncidentKind) {
        incidents[kind] = (incidents[kind] ?: 0) + 1
    }

    fun ambulanceArrived(seconds: Double) {
        ambulance += seconds / SECONDS_PER_MINUTE
    }

    private var secondRow = 0

    fun secondRowStop() {
        secondRow++
    }

    fun chargingWait(seconds: Double) {
        chargingWaits += seconds / SECONDS_PER_MINUTE
    }

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

    /** Zwischenstand des laufenden Tags (für Tests). */
    fun peek(
        day: Int,
        weekday: Weekday,
    ): DayReport =
        DayReport(
            day = day,
            weekday = weekday,
            tripsStartedPerHour = startedPerHour.copyOf(),
            tripsCompleted = completed,
            meanTripMinutes = 0.0,
            peakVehicles = peakVehicles,
            peakHour = peakHour,
            stuckRemovals = stuck,
            incidents = incidents.toMap(),
            ambulanceMinutes = ambulance.toList(),
            chargingWaitMinutes = chargingWaits.toList(),
            secondRowStops = secondRow,
        )

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
                incidents = incidents.toMap(),
                ambulanceMinutes = ambulance.toList(),
                chargingWaitMinutes = chargingWaits.toList(),
                secondRowStops = secondRow,
            )
        startedPerHour = IntArray(GameTime.HOURS_PER_DAY)
        completed = 0
        tripSeconds = 0.0
        peakVehicles = 0
        peakHour = 0
        stuck = 0
        incidents = HashMap()
        ambulance = ArrayList()
        chargingWaits = ArrayList()
        secondRow = 0
        return report
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
