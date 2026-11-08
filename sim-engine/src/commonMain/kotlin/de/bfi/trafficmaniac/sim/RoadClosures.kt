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

    /**
     * Baustellen und Vollsperrungen, die der Spieler selbst einrichtet – getrennt von denen des Bauamts, damit das
     * Ende einer Reparatur sie nicht mit aufhebt.
     */
    internal val playerWorks = BooleanArray(graph.segmentCount)
    internal val playerClosed = BooleanArray(graph.segmentCount)

    /** Sperrungen durch Ereignisse (Demo, Fest, Marathon, Markt) – gezählt, weil sie sich überlappen können. */
    internal val eventClosures = IntArray(graph.segmentCount)

    fun isCollapsed(segment: Int): Boolean = collapsed[segment]

    fun isDetour(segment: Int): Boolean = detour[segment]

    /** Wegen einer Baustelle voll gesperrt (nicht wegen eines Ereignisses). */
    fun isWorksClosed(segment: Int): Boolean = worksClosed[segment]

    /** Für den Verkehr gesperrt (Vollsperrung, Brücke gesperrt oder eingestürzt). */
    internal fun isClosed(segment: Int): Boolean =
        worksClosed[segment] || bridgeClosed[segment] || collapsed[segment] || eventClosures[segment] > 0 ||
            playerClosed[segment]

    /** Baustelle des Spielers: Fahrbahn verengt (langsamer), aber befahrbar. */
    fun setPlayerWorks(
        segment: Int,
        active: Boolean,
    ) {
        playerWorks[segment] = active
        changed(segment)
    }

    /** Vollsperrung durch den Spieler. */
    fun setPlayerClosed(
        segment: Int,
        closed: Boolean,
    ) {
        playerClosed[segment] = closed
        changed(segment)
    }

    /** Ereignis sperrt [segment] ([closed]) oder gibt es wieder frei. */
    internal fun setEventClosed(
        segment: Int,
        closed: Boolean,
    ) {
        eventClosures[segment] = (eventClosures[segment] + if (closed) 1 else -1).coerceAtLeast(0)
        changed(segment)
    }

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
