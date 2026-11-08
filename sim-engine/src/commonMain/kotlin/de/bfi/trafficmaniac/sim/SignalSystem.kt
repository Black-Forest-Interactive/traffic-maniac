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
 * Feste Ampelprogramme. In OSM stehen Ampeln oft einzeln an den Haltelinien einer Kreuzung;
 * nahe beieinander liegende Ampelknoten werden deshalb zu einer Anlage zusammengefasst.
 * Jede Anlage hat zwei Phasen: Zufahrten etwa entlang einer Achse sind gemeinsam grün.
 * Gibt es nur eine Achse (z. B. Fußgängerampel), folgt auf die Autophase eine kurze Fußgängerphase.
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
    private var groupCycle = DoubleArray(0)
    private var groupFailed = BooleanArray(0)
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
        groupCycle =
            DoubleArray(plants.size) { if (twoPhase[it]) CYCLE_SECONDS else PHASE_SECONDS + PEDESTRIAN_SECONDS }
        groupOffset = DoubleArray(plants.size) { offsetFraction(plants[it].signalNodes.min()) * groupCycle[it] }
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
        val t = (time + groupOffset[group]) % groupCycle[group]
        val activePhase = (t / PHASE_SECONDS).toInt()
        val inPhase = t - activePhase * PHASE_SECONDS
        // Rot-Gelb in der letzten Sekunde vor der eigenen Grünphase (fällt in die Allrot-Zeit der Querrichtung)
        val untilGreen = (linkPhase[link] * PHASE_SECONDS - t + groupCycle[group]) % groupCycle[group]
        return when {
            activePhase != linkPhase[link] && untilGreen in 0.0..RED_YELLOW_SECONDS && untilGreen > 0 -> {
                SignalState.RED_YELLOW
            }

            activePhase != linkPhase[link] -> {
                SignalState.RED
            }

            inPhase < GREEN_SECONDS -> {
                SignalState.GREEN
            }

            inPhase < GREEN_SECONDS + YELLOW_SECONDS -> {
                SignalState.YELLOW
            }

            else -> {
                SignalState.RED
            }
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
        val referenceAxis = axis(graph.endHeading(approaches.first()))
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

        /** Fußgängerphase an Ampeln ohne Querverkehr. */
        const val PEDESTRIAN_SECONDS = 12.0
        const val CLUSTER_RADIUS = 35f
        private const val NO_GROUP = -1
        private const val GOLDEN_RATIO_FRACTION = 0.6180339887
    }
}
