package de.bfi.trafficmaniac.sim

/**
 * Verkehr je Straßenabschnitt seit Spielbeginn: Durchfahrten und die dabei gebrauchte Fahrzeit. Grundlage für
 * Vorher/Nachher-Vergleiche (z. B. „Was hat Tempo 30 auf der Goethestraße bewirkt – und wohin ist der Verkehr
 * ausgewichen?“).
 */
class SegmentTraffic(
    segmentCount: Int,
) {
    private val passages = IntArray(segmentCount)
    private val seconds = DoubleArray(segmentCount)

    internal fun passed(
        segment: Int,
        travelSeconds: Double,
    ) {
        passages[segment]++
        seconds[segment] += travelSeconds
    }

    fun passages(segment: Int): Int = passages[segment]

    /** Mittlere Fahrzeit über den Abschnitt in Sekunden (0 ohne Durchfahrt). */
    fun meanSeconds(segment: Int): Double = if (passages[segment] == 0) 0.0 else seconds[segment] / passages[segment]

    /** Kopie der Durchfahrten aller Abschnitte. */
    fun passagesSnapshot(): IntArray = passages.copyOf()
}
