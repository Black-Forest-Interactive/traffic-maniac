package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadNetwork
import de.bfi.trafficmaniac.city.RoadSegment
import kotlin.math.atan2
import kotlin.math.hypot

/** Breite einer Fahrspur in Metern. */
const val LANE_WIDTH_METERS = 3.5f

/** Indizes im Ergebnis von [RoadGraph.locate]. */
const val LOCATE_X = 0
const val LOCATE_Y = 1
const val LOCATE_DIR_X = 2
const val LOCATE_DIR_Y = 3
const val LOCATE_SIZE = 4

/** Umrechnung km/h → m/s. */
internal const val KMH_PER_MS = 3.6

/**
 * Gerichteter Graph für die Simulation: Aus jedem Straßenabschnitt werden ein oder zwei
 * Fahrtrichtungen ("Links"), jede mit eigener Spuranzahl und Geometrie in Fahrtrichtung.
 */
class RoadGraph(
    network: RoadNetwork,
) {
    val nodeCount: Int = network.nodes.size
    val nodeX: FloatArray = FloatArray(nodeCount) { network.nodes[it].x }
    val nodeY: FloatArray = FloatArray(nodeCount) { network.nodes[it].y }

    /** Ampel am Knoten. Veränderlich: Der Spieler kann Ampeln bauen und abreißen. */
    val nodeHasSignals: BooleanArray = BooleanArray(nodeCount) { network.nodes[it].trafficSignals }
    val segmentCount: Int = network.segments.size

    val linkCount: Int
    val linkFrom: IntArray
    val linkTo: IntArray
    val linkLanes: IntArray
    val linkLength: FloatArray

    /** Aktuelles Tempolimit in m/s. Veränderlich über [setSegmentSpeedLimit]. */
    val linkSpeedLimit: FloatArray

    /**
     * Tempo in km/h, das sich auf der Straße "richtig anfühlt" – das ursprüngliche Limit aus OSM.
     * Je weiter das aktuelle Limit darunter liegt, desto mehr Fahrer sind zu schnell.
     */
    val linkPerceivedSpeedKmh: FloatArray
    val linkSegment: IntArray

    /** Links (Fahrtrichtungen) je Straßenabschnitt. */
    val segmentLinks: Array<IntArray>
    val linkRoadClass: Array<RoadClass>
    val linkOneway: BooleanArray

    /** Gegenrichtung desselben Abschnitts oder -1. */
    val linkReverse: IntArray
    val outLinks: Array<IntArray>
    val inLinks: Array<IntArray>

    /** Nebenstraße aus dem Import: wird erst befahren, wenn eine Umleitung sie zuschaltet. */
    val linkPassive: BooleanArray

    val linkBridge: BooleanArray

    /** Wahrscheinlichkeit, dass ein LKW in der (Neben-)Straße stecken bleibt: enge Kurven, Breite, Höhe, Gewicht. */
    internal val linkTruckBlockChance: FloatArray

    /** Höchstgewicht in Tonnen (aus OSM oder vom Spieler an maroden Brücken), sonst unbegrenzt. */
    val linkMaxWeightTons: FloatArray

    /** Für Navi und Einfahrt freigegeben: aktive Straße oder zugeschaltete Nebenstraße. */
    internal val linkEnabled: BooleanArray

    /**
     * Links, die in jedem Tick durchlaufen werden: befahrene Straßen und alle Nebenstraßen, die je zugeschaltet
     * wurden (dort können noch Fahrzeuge unterwegs sein). Die übrigen Tausend Nebenstraßen kosten so nichts.
     */
    var simulatedLinks: IntArray
        internal set

    private val geometry: Array<FloatArray>
    private val cumulative: Array<FloatArray>

    init {
        val specs = ArrayList<LinkSpec>()
        for (segment in network.segments) {
            val coordinates = segment.geometry.coordinates
            val forward = specs.size
            val reverse = if (segment.isOneway) -1 else forward + 1
            specs +=
                LinkSpec(
                    segment.from,
                    segment.to,
                    segment.lanesForward,
                    coordinates,
                    segment.isOneway,
                    reverse,
                    segment,
                )
            if (!segment.isOneway) {
                specs +=
                    LinkSpec(
                        segment.to,
                        segment.from,
                        segment.lanesBackward,
                        reversePoints(coordinates),
                        false,
                        forward,
                        segment,
                    )
            }
        }
        linkCount = specs.size
        linkFrom = IntArray(linkCount) { specs[it].from }
        linkTo = IntArray(linkCount) { specs[it].to }
        linkLanes = IntArray(linkCount) { specs[it].lanes.coerceAtLeast(1) }
        linkOneway = BooleanArray(linkCount) { specs[it].oneway }
        linkReverse = IntArray(linkCount) { specs[it].reverse }
        linkSpeedLimit = FloatArray(linkCount) { (specs[it].segment.maxSpeedKmh / KMH_PER_MS).toFloat() }
        linkPerceivedSpeedKmh = FloatArray(linkCount) { specs[it].segment.maxSpeedKmh.toFloat() }
        linkSegment = IntArray(linkCount) { specs[it].segment.id }
        segmentLinks =
            Array(segmentCount) { segment -> (0 until linkCount).filter { linkSegment[it] == segment }.toIntArray() }
        linkRoadClass = Array(linkCount) { specs[it].segment.roadClass }
        geometry = Array(linkCount) { specs[it].coordinates }
        cumulative = Array(linkCount) { cumulativeLengths(geometry[it]) }
        linkLength = FloatArray(linkCount) { cumulative[it].last().coerceAtLeast(MIN_LINK_LENGTH) }
        outLinks = groupLinksByNode(linkFrom)
        inLinks = groupLinksByNode(linkTo)
        linkPassive = BooleanArray(linkCount) { specs[it].segment.passive }
        linkBridge = BooleanArray(linkCount) { specs[it].segment.bridge }
        linkTruckBlockChance = FloatArray(linkCount) { truckBlockChance(specs[it].segment) }
        linkMaxWeightTons = FloatArray(linkCount) { specs[it].segment.maxWeightTons ?: Float.POSITIVE_INFINITY }
        linkEnabled = BooleanArray(linkCount) { !linkPassive[it] }
        simulatedLinks = (0 until linkCount).filter { !linkPassive[it] }.toIntArray()
    }

    /** Gesperrt (Vollsperrung, Brücke gesperrt oder eingestürzt). */
    internal val linkClosed = BooleanArray(linkCount)

    /** Teil einer ausgeschilderten Umleitung. */
    internal val linkDetour = BooleanArray(linkCount)

    /** Tempo-Faktor durch den Straßenzustand und Baustellen (1 = normal). */
    internal val linkRoadFactor = FloatArray(linkCount) { 1f }

    /** Tempo-Faktor durch Schnee (1 = trocken), gesetzt vom [Weather]. */
    internal val linkWeatherFactor = FloatArray(linkCount) { 1f }

    /** Gibt es irgendwo ein Gewichtslimit? Nur dann lohnt sich für LKW eine eigene Route. */
    var hasWeightLimits: Boolean = linkMaxWeightTons.any { it.isFinite() }
        internal set

    /** Wird gerade irgendwo eine Umleitung ausgeschildert? */
    var hasDetours: Boolean = false
        internal set

    private fun groupLinksByNode(nodeOfLink: IntArray): Array<IntArray> {
        val lists = Array(nodeCount) { ArrayList<Int>() }
        for (link in 0 until linkCount) lists[nodeOfLink[link]] += link
        return Array(nodeCount) { lists[it].toIntArray() }
    }

    /** Limits tagsüber und in der Nachtruhe (km/h; NaN = kein eigenes Nachtlimit). */
    private val linkDayLimitKmh = linkPerceivedSpeedKmh.copyOf()
    private val linkNightLimitKmh = FloatArray(linkCount) { Float.NaN }
    private var quietHours = false

    /**
     * Setzt das Tempolimit beider Fahrtrichtungen; `null` stellt das ursprüngliche Limit wieder her.
     * Mit [nightOnly] gilt es nur in der Nachtruhe (22–6 Uhr), z. B. "30 nur nachts".
     */
    fun setSegmentSpeedLimit(
        segment: Int,
        kmh: Int?,
        nightOnly: Boolean = false,
    ) {
        for (link in segmentLinks[segment]) {
            if (nightOnly) {
                linkNightLimitKmh[link] = kmh?.toFloat() ?: Float.NaN
            } else {
                linkDayLimitKmh[link] = kmh?.toFloat() ?: linkPerceivedSpeedKmh[link]
            }
            applyLimit(link)
        }
    }

    /** Schaltet zwischen Tag- und Nachtlimits um. */
    fun setQuietHours(quiet: Boolean) {
        if (quiet == quietHours) return
        quietHours = quiet
        for (link in 0 until linkCount) applyLimit(link)
    }

    fun hasNightLimit(link: Int): Boolean = !linkNightLimitKmh[link].isNaN()

    /** Tempo-Faktor durch Vorfälle (Ölspur, Gaffer); 1 = normal. */
    internal val linkSlowdown = FloatArray(linkCount) { 1f }

    /** Höchsttempo in m/s, solange ein Rad-Korso auf dem Link fährt – überholt wird er nicht. */
    internal val linkEventSpeedCap = FloatArray(linkCount) { Float.MAX_VALUE }

    /** Vom Spieler zur Vorfahrtsstraße erklärt: Rang 0 schlägt an ungeregelten Kreuzungen alles. */
    private val linkPriority = BooleanArray(linkCount)

    /** Radweg quert die Straße (noch ohne Radfahrer – ein weiterer Reiz für die Fahrer). */
    private val linkBikeCrossing = BooleanArray(linkCount)

    /** Fahrradstraße: Kfz dürfen Radfahrer nicht überholen (fahren im Radtempo, kein Spurwechsel). */
    internal val linkBikeStreet = BooleanArray(linkCount)

    fun setSegmentBikeStreet(
        segment: Int,
        on: Boolean,
    ) {
        for (link in segmentLinks[segment]) linkBikeStreet[link] = on
    }

    fun setSegmentPriority(
        segment: Int,
        priority: Boolean,
    ) {
        for (link in segmentLinks[segment]) linkPriority[link] = priority
    }

    fun setSegmentBikeCrossing(
        segment: Int,
        crossing: Boolean,
    ) {
        for (link in segmentLinks[segment]) linkBikeCrossing[link] = crossing
    }

    fun hasPriority(link: Int): Boolean = linkPriority[link]

    fun hasBikeCrossing(link: Int): Boolean = linkBikeCrossing[link]

    /** Rang an ungeregelten Kreuzungen: kleiner = Vorfahrt. */
    fun priorityRank(link: Int): Int = if (linkPriority[link]) -1 else linkRoadClass[link].ordinal

    private fun applyLimit(link: Int) {
        val night = linkNightLimitKmh[link]
        val kmh = if (quietHours && !night.isNaN()) night else linkDayLimitKmh[link]
        linkSpeedLimit[link] = (kmh / KMH_PER_MS).toFloat()
    }

    fun speedLimitKmh(link: Int): Float = (linkSpeedLimit[link] * KMH_PER_MS).toFloat()

    /** Seitlicher Abstand der Spurmitte von der Mittellinie; positive Werte liegen rechts in Fahrtrichtung. */
    fun laneOffset(
        link: Int,
        lane: Int,
    ): Float =
        if (linkOneway[link]) {
            (lane - (linkLanes[link] - 1) / 2f) * LANE_WIDTH_METERS
        } else {
            (lane + HALF) * LANE_WIDTH_METERS
        }

    /**
     * Schreibt Position und Fahrtrichtung eines Punkts [distance] Meter nach Beginn des Links auf Spur [lane] in [out]:
     * `x, y, dirX, dirY`.
     */
    fun locate(
        link: Int,
        distance: Float,
        lane: Int,
        out: FloatArray,
    ) {
        val points = geometry[link]
        val lengths = cumulative[link]
        val d = distance.coerceIn(0f, lengths.last())
        val i = segmentIndex(lengths, d)
        val x0 = points[i * 2]
        val y0 = points[i * 2 + 1]
        val dx = points[(i + 1) * 2] - x0
        val dy = points[(i + 1) * 2 + 1] - y0
        val length = lengths[i + 1] - lengths[i]
        val t = if (length > 0f) (d - lengths[i]) / length else 0f
        val dirX = if (length > 0f) dx / length else 1f
        val dirY = if (length > 0f) dy / length else 0f
        val offset = laneOffset(link, lane)
        out[LOCATE_X] = x0 + dx * t + dirY * offset
        out[LOCATE_Y] = y0 + dy * t - dirX * offset
        out[LOCATE_DIR_X] = dirX
        out[LOCATE_DIR_Y] = dirY
    }

    /** Fahrtrichtung am Ende des Links als Winkel im Bogenmaß. */
    fun endHeading(link: Int): Double {
        val points = geometry[link]
        val last = points.size / 2 - 1
        val dy = points[last * 2 + 1] - points[(last - 1) * 2 + 1]
        val dx = points[last * 2] - points[(last - 1) * 2]
        return atan2(dy.toDouble(), dx.toDouble())
    }

    private class LinkSpec(
        val from: Int,
        val to: Int,
        val lanes: Int,
        val coordinates: FloatArray,
        val oneway: Boolean,
        val reverse: Int,
        val segment: RoadSegment,
    )

    private companion object {
        const val MIN_LINK_LENGTH = 1f
        const val HALF = 0.5f

        /** LKW (2,5 m breit, 4 m hoch, 18 t) passen nicht: sicher stecken. Sonst je nach Straßenklasse. */
        fun truckBlockChance(segment: RoadSegment): Float {
            val tooNarrow = (segment.maxWidthMeters ?: Float.MAX_VALUE) < TRUCK_MIN_WIDTH
            val tooLow = (segment.maxHeightMeters ?: Float.MAX_VALUE) < TRUCK_MIN_HEIGHT
            val tooHeavy = (segment.maxWeightTons ?: Float.MAX_VALUE) < TRUCK_TONS
            if (tooNarrow || tooLow || tooHeavy) return 1f
            return when (segment.roadClass) {
                RoadClass.LIVING_STREET -> LIVING_STREET_BLOCK
                RoadClass.RESIDENTIAL -> RESIDENTIAL_BLOCK
                RoadClass.UNCLASSIFIED -> UNCLASSIFIED_BLOCK
                RoadClass.TERTIARY, RoadClass.TERTIARY_LINK -> TERTIARY_BLOCK
                else -> 0f
            }
        }

        const val TRUCK_MIN_WIDTH = 2.8f
        const val TRUCK_MIN_HEIGHT = 4.0f
        const val TRUCK_TONS = 18f
        const val LIVING_STREET_BLOCK = 0.6f
        const val RESIDENTIAL_BLOCK = 0.15f
        const val UNCLASSIFIED_BLOCK = 0.05f
        const val TERTIARY_BLOCK = 0.02f

        fun reversePoints(coordinates: FloatArray): FloatArray {
            val count = coordinates.size / 2
            return FloatArray(coordinates.size) { i ->
                val point = count - 1 - i / 2
                coordinates[point * 2 + i % 2]
            }
        }

        fun cumulativeLengths(points: FloatArray): FloatArray {
            val count = points.size / 2
            val result = FloatArray(count)
            for (i in 1 until count) {
                result[i] =
                    result[i - 1] + hypot(points[i * 2] - points[i * 2 - 2], points[i * 2 + 1] - points[i * 2 - 1])
            }
            return result
        }

        /** Index des Geometrie-Abschnitts, in dem [distance] liegt (binäre Suche). */
        fun segmentIndex(
            lengths: FloatArray,
            distance: Float,
        ): Int {
            var low = 0
            var high = lengths.size - 2
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (lengths[mid] <= distance) low = mid else high = mid - 1
            }
            return low
        }
    }
}
