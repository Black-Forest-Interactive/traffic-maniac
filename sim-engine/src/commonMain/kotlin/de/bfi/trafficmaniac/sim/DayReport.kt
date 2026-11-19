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
    /** Parkplatzsuchen in Minuten (nur wer suchen musste). */
    val parkingSearchMinutes: List<Double> = emptyList(),
    val illegalParkers: Int = 0,
    /** Unfälle durch Schlaglöcher und Krater. */
    val potholeAccidents: Int = 0,
    val parkedCurb: Int = 0,
    val parkedLots: Int = 0,
    /** Bezahlte Parkgebühren in Euro. */
    val parkingFees: Double = 0.0,
    /** Verkehrsbilanz: gefahrene Fahrzeugkilometer (alle Fahrzeuge, ganze Abschnitte). */
    val vehicleKm: Double = 0.0,
    /** Verkehrsbilanz: im Verkehr verbrachte Fahrzeugstunden. */
    val vehicleHours: Double = 0.0,
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
    private var parkingSearches = ArrayList<Double>()
    private var illegal = 0
    private var potholes = 0
    private var meters = 0.0
    private var seconds = 0.0

    /** Ein Fahrzeug hat einen Link von [lengthMeters] in [travelSeconds] ganz durchfahren. */
    fun linkDriven(
        lengthMeters: Float,
        travelSeconds: Double,
    ) {
        meters += lengthMeters
        seconds += travelSeconds
    }

    fun parkingSearch(seconds: Double) {
        parkingSearches += seconds / SECONDS_PER_MINUTE
    }

    fun illegallyParked() {
        illegal++
    }

    fun potholeAccident() {
        potholes++
    }

    private var curbParked = 0
    private var lotParked = 0
    private var fees = 0.0

    /** Geparkt, am Straßenrand ([curb]) oder auf einem Platz, und dafür [fee] Euro bezahlt. */
    fun parked(
        curb: Boolean,
        fee: Double,
    ) {
        if (curb) curbParked++ else lotParked++
        if (fee > 0) fees += fee
    }

    fun incident(kind: IncidentKind) {
        incidents[kind] = (incidents[kind] ?: 0) + 1
    }

    /** Vorfälle je Art seit Mitternacht. */
    val incidentsSoFar: Map<IncidentKind, Int> get() = incidents

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
            parkingSearchMinutes = parkingSearches.toList(),
            illegalParkers = illegal,
            potholeAccidents = potholes,
            parkedCurb = curbParked,
            parkedLots = lotParked,
            parkingFees = fees,
            vehicleKm = meters / METERS_PER_KM,
            vehicleHours = seconds / SECONDS_PER_HOUR,
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
                parkingSearchMinutes = parkingSearches.toList(),
                illegalParkers = illegal,
                potholeAccidents = potholes,
                parkedCurb = curbParked,
                parkedLots = lotParked,
                parkingFees = fees,
                vehicleKm = meters / METERS_PER_KM,
                vehicleHours = seconds / SECONDS_PER_HOUR,
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
        parkingSearches = ArrayList()
        illegal = 0
        potholes = 0
        curbParked = 0
        lotParked = 0
        fees = 0.0
        meters = 0.0
        seconds = 0.0
        return report
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val SECONDS_PER_HOUR = 3_600.0
        const val METERS_PER_KM = 1_000.0
    }
}
