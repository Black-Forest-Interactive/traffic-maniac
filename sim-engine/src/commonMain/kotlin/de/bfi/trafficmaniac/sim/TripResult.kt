package de.bfi.trafficmaniac.sim

/** Eine beendete Fahrt – Grundlage für die Zufriedenheit "schnell am Ziel". */
data class TripResult(
    /** Wohngebiet des Fahrers oder -1 (Durchgangsverkehr). */
    val homeZone: Int,
    val actualSeconds: Double,
    val referenceSeconds: Double,
)

/** Puffer für beendete Fahrten. */
class TripLog internal constructor() {
    private val trips = ArrayList<TripResult>()

    internal fun add(trip: TripResult) {
        trips += trip
    }

    /** Liefert die gesammelten Fahrten und leert den Puffer. */
    fun drain(): List<TripResult> = trips.toList().also { trips.clear() }
}
