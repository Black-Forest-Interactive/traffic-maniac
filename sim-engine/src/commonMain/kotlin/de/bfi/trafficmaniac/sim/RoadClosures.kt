package de.bfi.trafficmaniac.sim

/**
 * Was der Spieler (oder der Verfall) an einem Abschnitt verfügt: Baustelle, Vollsperrung, gesperrte oder
 * eingestürzte Brücke, Gewichtslimit und Umleitung. [changed] meldet einen geänderten Abschnitt an die
 * [Infrastructure], die daraus Tempo-Faktor und Sperrung im Graphen ableitet.
 */
class RoadClosures internal constructor(
    private val graph: RoadGraph,
    private val changed: (segment: Int) -> Unit,
) {
    internal val works = BooleanArray(graph.segmentCount)
    internal val worksClosed = BooleanArray(graph.segmentCount)
    internal val bridgeClosed = BooleanArray(graph.segmentCount)
    internal val collapsed = BooleanArray(graph.segmentCount)
    internal val playerWeightLimit = BooleanArray(graph.segmentCount)
    internal val detour = BooleanArray(graph.segmentCount)

    fun isCollapsed(segment: Int): Boolean = collapsed[segment]

    fun isDetour(segment: Int): Boolean = detour[segment]

    /** Für den Verkehr gesperrt (Vollsperrung, Brücke gesperrt oder eingestürzt). */
    internal fun isClosed(segment: Int): Boolean = worksClosed[segment] || bridgeClosed[segment] || collapsed[segment]

    /** Baustelle an [segment]: mit [closed] als Vollsperrung, sonst halbes Tempo. */
    fun setWorks(
        segment: Int,
        active: Boolean,
        closed: Boolean = false,
    ) {
        works[segment] = active
        worksClosed[segment] = active && closed
        changed(segment)
    }

    fun setBridgeClosed(
        segment: Int,
        closed: Boolean,
    ) {
        bridgeClosed[segment] = closed
        changed(segment)
    }

    /** Gewichtslimit an einer maroden Brücke (oder Straße); `null` hebt es auf. */
    fun setWeightLimit(
        segment: Int,
        tons: Float?,
    ) {
        graph.setSegmentWeightLimit(segment, tons)
        playerWeightLimit[segment] = tons != null
        changed(segment)
    }

    /** Abschnitt in die Umleitung aufnehmen (passive Nebenstraßen werden dabei befahrbar). */
    fun setDetour(
        segment: Int,
        enabled: Boolean,
    ) {
        graph.setSegmentDetour(segment, enabled)
        detour[segment] = enabled
        changed(segment)
    }

    internal fun collapse(segment: Int) {
        collapsed[segment] = true
        changed(segment)
    }
}
