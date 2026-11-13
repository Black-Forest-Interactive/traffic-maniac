package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.StreetStretches
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.setNodeBusPriority
import de.bfi.trafficmaniac.sim.setSegmentBusLane
import kotlin.math.roundToInt

/**
 * Was der Spieler für die Busse tun kann – und was ihm die Busse einbringen:
 *
 * - **Busspur:** Die rechte Spur mehrspuriger Straßen gehört den Bussen. Die Busse kommen am Stau vorbei, die Autos
 *   haben eine Spur weniger. Ein paar fahren trotzdem drauf.
 * - **Busspur-Blitzer:** fotografiert genau die (55 € je Foto).
 * - **Busvorrang:** An der Ampel bekommt der Bus sein eigenes Signal und muss nicht warten.
 *
 * Abends zählt die Pünktlichkeit: Pünktliche Busse lobt die Presse, und am nächsten Tag lassen mehr Leute das Auto
 * stehen. Unpünktliche bringen Schelte.
 */
internal class BusService(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val works: PublicWorks,
    private val message: (String) -> Unit,
) {
    private val stretches = StreetStretches(city.roads)

    val busLanes = LinkedHashSet<Int>()
    val priorityNodes = LinkedHashSet<Int>()

    /** Anteil der Autofahrten, die heute mit dem Bus stattfinden. */
    var modeShift = 0.0
        private set

    fun execute(command: GameCommand) {
        when (command) {
            is GameCommand.SetBusLane -> setBusLane(command.segments, command.on)
            is GameCommand.PlaceBusLaneCamera -> placeCamera(command.segment)
            is GameCommand.SetBusPriority -> setPriority(command.node, command.on)
            else -> Unit
        }
    }

    private fun setBusLane(
        segments: List<Int>,
        on: Boolean,
    ) {
        // Nur wo eine Richtung zwei Spuren hat – sonst bliebe für die Autos nichts
        val wide =
            segments.filter { segment ->
                simulation.graph.segmentLinks[segment].any {
                    simulation.graph.linkLanes[it] >=
                        2
                }
            }
        val changing = wide.filter { (it in busLanes) != on }
        if (on && wide.isEmpty()) return message(tr("Für eine Busspur braucht es mindestens zwei Spuren"))
        if (changing.isEmpty()) return
        val stretchCount = changing.map { stretches.stretchOf(it).min() }.distinct().size
        if (on && !treasury.pay(rules.busLaneCost * stretchCount, tr("Busspur"))) return
        for (segment in changing) {
            simulation.graph.setSegmentBusLane(segment, on)
            if (on) busLanes += segment else busLanes -= segment
        }
        val street = streetOf(changing)
        message(
            if (on) {
                tr("Busspur: {street} – die rechte Spur gehört den Bussen", "street" to street)
            } else {
                tr("Busspur aufgehoben: {street}", "street" to street)
            },
        )
    }

    private fun placeCamera(segment: Int) {
        if (segment !in busLanes) return message(tr("Hier ist keine Busspur"))
        if (works.cameraCounters.values.any { it.segment == segment }) return message(tr("Hier blitzt es schon"))
        if (!treasury.pay(rules.busLaneCameraCost, tr("Busspur-Blitzer"))) return
        val camera = simulation.speedControl.placeBusLaneCamera(segment)
        val street = streetOf(listOf(segment))
        works.cameraCounters[camera.id] = CameraCounter(camera.id, segment, street, camera = camera)
        message(tr("Busspur-Blitzer scharf geschaltet: {street}", "street" to street))
    }

    private fun setPriority(
        node: Int,
        on: Boolean,
    ) {
        if (on == (node in priorityNodes)) return
        val signalled = simulation.graph.inLinks[node].any { simulation.signals.isControlled(it) }
        if (on && !signalled) return message(tr("Hier ist keine Ampel"))
        if (on && !treasury.pay(rules.busPriorityCost, tr("Busvorrang"))) return
        simulation.graph.setNodeBusPriority(node, on)
        if (on) priorityNodes += node else priorityNodes -= node
        val street = streetAt(node)
        message(
            if (on) {
                tr("Busvorrang an der Ampel: {street}", "street" to street)
            } else {
                tr("Busvorrang aufgehoben: {street}", "street" to street)
            },
        )
    }

    /**
     * Mitternacht: Wie pünktlich waren die Busse heute? Danach richtet sich die Presse (Rückgabe: Änderung der
     * Reputation) und wie viele morgen das Auto stehen lassen.
     */
    fun dayEnded(): Double {
        val lines = simulation.snapshot().transit.lines
        val served = lines.sumOf { it.stopsServed }
        if (served == 0) return 0.0
        val share = lines.sumOf { it.punctualShare * it.stopsServed } / served
        // Ab 60 % Pünktlichkeit steigen die ersten um, bei 100 % die meisten Willigen
        modeShift =
            ((share - rules.busComplaintShare) / (1 - rules.busComplaintShare)).coerceIn(0.0, 1.0) *
            rules.busModeShiftMax
        simulation.carTripReduction = modeShift
        val percent = (share * PERCENT).roundToInt()
        return when {
            share >= rules.busPraiseShare -> {
                message(tr("Presse: „Busse pünktlich wie nie“ – {percent} % (Reputation +)", "percent" to percent))
                rules.busPressReputation
            }

            share < rules.busComplaintShare -> {
                message(
                    tr(
                        "Presse: „Bus fährt, wann er will“ – nur {percent} % pünktlich (Reputation −)",
                        "percent" to percent,
                    ),
                )
                -rules.busPressReputation
            }

            else -> {
                0.0
            }
        }
    }

    private fun streetOf(segments: Collection<Int>): String =
        segments.firstNotNullOfOrNull { city.roads.segments[it].name } ?: tr("Straße ohne Namen")

    private fun streetAt(node: Int): String =
        city.roads.segments
            .firstOrNull { (it.from == node || it.to == node) && !it.passive && it.name != null }
            ?.name ?: tr("Kreuzung ohne Namen")

    private companion object {
        const val PERCENT = 100
    }
}
