package de.bfi.trafficmaniac.sim

import kotlin.math.PI
import kotlin.math.abs

enum class SignalState {
    NONE,
    GREEN,
    YELLOW,
    RED,

    /** Deutsche Rot-Gelb-Phase: gleich wird es grün – noch stehen bleiben. */
    RED_YELLOW,

    /** Ausgefallen oder abgeschaltet: blinkt gelb, es gilt die Vorfahrt – im Zweifel rechts vor links. */
    FLASHING,
}

/**
 * Programm einer Ampelanlage, vom Spieler einstellbar: [mainShare] = Anteil der Grünzeit für die Hauptrichtung
 * (Zufahrten der wichtigsten Straße), [offsetSeconds] = Versatz im Umlauf (für grüne Wellen; `null` = zufällig, aber
 * fest je Anlage).
 */
data class SignalProgram(
    val mainShare: Double = EQUAL_SHARE,
    val offsetSeconds: Double? = null,
) {
    companion object {
        const val EQUAL_SHARE = 0.5
    }
}

/**
 * Ampelprogramme. In OSM stehen Ampeln oft einzeln an den Haltelinien einer Kreuzung;
 * nahe beieinander liegende Ampelknoten werden deshalb zu einer Anlage zusammengefasst.
 * Jede Anlage hat zwei Phasen: Zufahrten etwa entlang einer Achse sind gemeinsam grün; Phase 0 ist die Hauptrichtung
 * (die Achse der wichtigsten Straße). Gibt es nur eine Achse (z. B. Fußgängerampel), folgt auf die Autophase eine
 * kurze Fußgängerphase. Alle Anlagen haben denselben Umlauf ([CYCLE_SECONDS]) – sonst liefe eine grüne Welle
 * auseinander. Wie die Grünzeit aufgeteilt wird und wie der Umlauf versetzt ist, stellt der Spieler je Anlage ein
 * ([setPrograms]); die Einstellung hängt am kleinsten Ampelknoten der Anlage und übersteht Umbauten.
 *
 * Der Versatz der Programme hängt nur von den Knoten ab – baut der Spieler eine Ampel, ändern sich
 * die übrigen Anlagen nicht.
 */
class SignalSystem(
    private val graph: RoadGraph,
) {
    private val linkGroup = IntArray(graph.linkCount)

    /** Verbindung innerhalb einer Anlage (z. B. Haltelinie → Kreuzungsknoten): keine Ampel, aber auch kein Warten. */
    private val linkInternal = BooleanArray(graph.linkCount)
    private val linkPhase = IntArray(graph.linkCount)
    private var groupOffset = DoubleArray(0)

    /** Grünzeit der Phase 0 und 1 je Anlage (Phase 1 = 0 bei Anlagen mit nur einer Achse). */
    private var groupGreen = Array(2) { DoubleArray(0) }
    private var groupFailed = BooleanArray(0)

    /** Kennung (kleinster Ampelknoten) je Anlage. */
    private var groupKey = IntArray(0)

    /** Kennung der Anlage je Knoten (Ampelknoten, Kreuzungsknoten, Haltelinien). */
    private var nodeGroupKey = HashMap<Int, Int>()

    /** Vom Spieler gesetzte Programme je Anlagen-Kennung. */
    private val programs = HashMap<Int, SignalProgram>()
    private val failedNode = BooleanArray(graph.nodeCount)

    /** Alle Links, deren Ende von einer Ampel geregelt wird. */
    var controlledLinks: IntArray = IntArray(0)
        private set

    /** Positionen der Ampeln für die Darstellung. */
    var markers: SignalMarkers = SignalMarkers(IntArray(0), FloatArray(0), FloatArray(0))
        private set

    init {
        rebuild()
    }

    /** Berechnet alle Anlagen neu, z. B. nachdem [RoadGraph.nodeHasSignals] geändert wurde. */
    fun rebuild() {
        linkGroup.fill(NO_GROUP)
        linkPhase.fill(0)
        linkInternal.fill(false)
        val plants = SignalPlants(graph).build()
        val controlled = ArrayList<Int>()
        val twoPhase = BooleanArray(plants.size)
        plants.forEachIndexed { index, plant ->
            val approaches = assignPhases(index, plant)
            controlled += approaches
            twoPhase[index] = approaches.any { linkPhase[it] == 1 }
        }
        controlledLinks = controlled.toIntArray()
        groupKey = IntArray(plants.size) { plants[it].signalNodes.min() }
        nodeGroupKey = HashMap()
        plants.forEachIndexed { index, plant ->
            for (node in plant.signalNodes + plant.core + plant.stopLines.keys) nodeGroupKey[node] = groupKey[index]
        }
        val program = Array(plants.size) { programs[groupKey[it]] ?: SignalProgram() }
        groupGreen =
            arrayOf(
                DoubleArray(plants.size) { if (twoPhase[it]) splitGreen(program[it].mainShare) else SINGLE_AXIS_GREEN },
                DoubleArray(
                    plants.size,
                ) { if (twoPhase[it]) USABLE_GREEN - splitGreen(program[it].mainShare) else 0.0 },
            )
        groupOffset =
            DoubleArray(plants.size) { program[it].offsetSeconds ?: (offsetFraction(groupKey[it]) * CYCLE_SECONDS) }
        groupFailed = BooleanArray(plants.size) { group -> plants[group].signalNodes.any { failedNode[it] } }
        markers = SignalMarkers.create(graph, this)
    }

    /** Baut ([enabled]) oder entfernt eine Ampel an [node] und berechnet die Anlagen neu. */
    fun setSignal(
        node: Int,
        enabled: Boolean,
    ) {
        graph.nodeHasSignals[node] = enabled
        rebuild()
    }

    fun isControlled(link: Int): Boolean = linkGroup[link] != NO_GROUP

    /** Programm der Anlage, zu der [node] gehört, oder `null`, wenn dort keine Ampel ist. */
    fun program(node: Int): SignalProgram? = nodeGroupKey[node]?.let { programs[it] ?: SignalProgram() }

    /** Kennung der Anlage, die [link] regelt, oder `null`. */
    fun plantOf(link: Int): Int? = linkGroup[link].takeIf { it != NO_GROUP }?.let { groupKey[it] }

    /**
     * Setzt Programme für die Anlagen der angegebenen Knoten (irgendein Knoten der Anlage genügt) und berechnet
     * einmal neu. Knoten ohne Ampel werden übergangen; liefert die Zahl der geänderten Anlagen.
     */
    fun setPrograms(byNode: Map<Int, SignalProgram>): Int {
        val byKey = byNode.mapNotNull { (node, program) -> nodeGroupKey[node]?.let { it to program } }.toMap()
        programs += byKey
        if (byKey.isNotEmpty()) rebuild()
        return byKey.size
    }

    /** Sekunden seit Beginn der Rotphase für [link] (nach Gelb), NaN bei Grün, Gelb oder ohne Ampel. */
    fun redSeconds(
        link: Int,
        time: Double,
    ): Double {
        val group = linkGroup[link]
        if (group == NO_GROUP || groupFailed[group]) return Double.NaN
        val t = ((time + groupOffset[group]) % CYCLE_SECONDS + CYCLE_SECONDS) % CYCLE_SECONDS
        val inPhase = (t - greenStartInCycle(link) + CYCLE_SECONDS) % CYCLE_SECONDS
        val redSince = inPhase - groupGreen[linkPhase[link]][group] - YELLOW_SECONDS
        return if (redSince >= 0) redSince else Double.NaN
    }

    /** Sekunde im Umlauf (ohne Versatz), zu der die Grünphase von [link] beginnt. */
    fun greenStartInCycle(link: Int): Double {
        val group = linkGroup[link]
        return if (group == NO_GROUP || linkPhase[link] == 0) 0.0 else groupGreen[0][group] + CLEARANCE_SECONDS
    }

    /** Verbindung mitten in einer Ampelanlage – wer hier fährt, hatte an der Haltelinie Grün. */
    fun isInternal(link: Int): Boolean = linkInternal[link]

    /** Ampel an [node] ausgefallen bzw. abgeschaltet – die ganze Anlage blinkt dann gelb. */
    fun setFailed(
        node: Int,
        failed: Boolean,
    ) {
        if (failedNode[node] == failed) return
        failedNode[node] = failed
        rebuild()
    }

    fun isFailed(node: Int): Boolean = failedNode[node] && graph.nodeHasSignals[node]

    fun state(
        link: Int,
        time: Double,
    ): SignalState {
        val group = linkGroup[link]
        if (group == NO_GROUP) return SignalState.NONE
        if (groupFailed[group]) return SignalState.FLASHING
        val t = ((time + groupOffset[group]) % CYCLE_SECONDS + CYCLE_SECONDS) % CYCLE_SECONDS
        val green = groupGreen[linkPhase[link]][group]
        val inPhase = (t - greenStartInCycle(link) + CYCLE_SECONDS) % CYCLE_SECONDS
        // Rot-Gelb in der letzten Sekunde vor der eigenen Grünphase (fällt in die Allrot-Zeit der Querrichtung)
        val untilGreen = CYCLE_SECONDS - inPhase
        return when {
            inPhase < green -> SignalState.GREEN
            inPhase < green + YELLOW_SECONDS -> SignalState.YELLOW
            untilGreen <= RED_YELLOW_SECONDS -> SignalState.RED_YELLOW
            else -> SignalState.RED
        }
    }

    /**
     * Ordnet die Zufahrten einer Anlage den Phasen zu: Haltelinien-Ampeln regeln nur ihre Zufahrt, an den
     * Kreuzungsknoten zählen alle Links von außerhalb. Verbindungen innerhalb der Anlage bleiben ungeregelt.
     */
    private fun assignPhases(
        group: Int,
        plant: SignalPlant,
    ): List<Int> {
        val inside = plant.core + plant.stopLines.keys
        val fromOutside =
            plant.core.flatMap { node -> graph.inLinks[node].filter { graph.linkFrom[it] !in inside } }
        val approaches = (plant.stopLines.values.flatten() + fromOutside).distinct()
        for (node in inside) {
            for (link in graph.inLinks[node]) if (graph.linkFrom[link] in inside) linkInternal[link] = true
        }
        if (approaches.isEmpty()) return emptyList()
        // Phase 0 ist die Hauptrichtung: die Achse der wichtigsten Straße unter den Zufahrten
        val referenceAxis = axis(graph.endHeading(approaches.minBy { graph.priorityRank(it) }))
        for (link in approaches) {
            val difference = abs(axis(graph.endHeading(link)) - referenceAxis)
            val sameAxis = difference < PI / 4 || difference > 3 * PI / 4
            linkGroup[link] = group
            linkPhase[link] = if (sameAxis) 0 else 1
        }
        return approaches
    }

    /** Pseudozufälliger, aber fester Anteil 0..1 je Knoten (Fibonacci-Hashing). */
    private fun offsetFraction(node: Int): Double = (node * GOLDEN_RATIO_FRACTION) % 1.0

    /** Richtung ohne Vorzeichen: Gegenverkehr liegt auf derselben Achse. */
    private fun axis(heading: Double): Double = ((heading % PI) + PI) % PI

    companion object {
        const val GREEN_SECONDS = 25.0
        const val YELLOW_SECONDS = 3.0
        const val ALL_RED_SECONDS = 2.0

        /** Rot-Gelb vor Grün (kürzer als die Allrot-Zeit, damit die Querrichtung schon Rot hat). */
        const val RED_YELLOW_SECONDS = 1.0
        const val PHASE_SECONDS = GREEN_SECONDS + YELLOW_SECONDS + ALL_RED_SECONDS
        const val CYCLE_SECONDS = 2 * PHASE_SECONDS

        /** Gelb und Allrot nach jeder Grünphase. */
        const val CLEARANCE_SECONDS = YELLOW_SECONDS + ALL_RED_SECONDS

        /** Grünzeit, die sich die beiden Phasen teilen. */
        const val USABLE_GREEN = CYCLE_SECONDS - 2 * CLEARANCE_SECONDS

        /** Fußgängerphase an Ampeln ohne Querverkehr. */
        const val PEDESTRIAN_SECONDS = 12.0

        /** Autogrün an Ampeln ohne Querverkehr: der Rest des Umlaufs nach Fußgängerphase und Räumzeit. */
        private const val SINGLE_AXIS_GREEN = CYCLE_SECONDS - PEDESTRIAN_SECONDS - CLEARANCE_SECONDS

        /** Keine Richtung bekommt weniger Grün als das. */
        const val MIN_GREEN_SECONDS = 10.0

        /** Grünzeit der Hauptrichtung für [share] (begrenzt, damit die Querrichtung nie verhungert). */
        fun splitGreen(share: Double): Double =
            (USABLE_GREEN * share).coerceIn(MIN_GREEN_SECONDS, USABLE_GREEN - MIN_GREEN_SECONDS)

        const val CLUSTER_RADIUS = 35f
        private const val NO_GROUP = -1
        private const val GOLDEN_RATIO_FRACTION = 0.6180339887
    }
}
