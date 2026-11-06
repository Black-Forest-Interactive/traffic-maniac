package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.sim.Simulation

/** Das Tiefbauamt: stellt Schilder, Ampeln und Blitzer auf – gegen Bezahlung. */
internal class PublicWorks(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val message: (String) -> Unit,
) {
    val speedLimits = LinkedHashMap<Int, Int>()
    val cameraCounters = LinkedHashMap<Int, CameraCounter>()
    var signalNodes: Set<Int> = signalNodesOf()
        private set

    fun setSpeedLimit(
        segment: Int,
        kmh: Int?,
    ) {
        if (speedLimits[segment] == kmh) return
        val street = streetName(segment)
        if (kmh == null) {
            simulation.graph.setSegmentSpeedLimit(segment, null)
            speedLimits.remove(segment)
            message("Tempolimit aufgehoben: $street")
        } else if (treasury.pay(rules.speedLimitSignCost, "Tempo-$kmh-Schild")) {
            simulation.graph.setSegmentSpeedLimit(segment, kmh)
            speedLimits[segment] = kmh
            message("Tempo $kmh: $street")
        }
    }

    fun buildSignal(node: Int) {
        if (simulation.graph.nodeHasSignals[node]) return message("Hier steht schon eine Ampel")
        if (treasury.pay(rules.signalCost, "Ampel")) {
            simulation.signals.setSignal(node, true)
            signalNodes = signalNodesOf()
            message("Neue Ampel in Betrieb")
        }
    }

    fun removeSignal(node: Int) {
        if (!simulation.graph.nodeHasSignals[node]) return
        if (treasury.pay(rules.signalRemovalCost, "Abbau der Ampel")) {
            simulation.signals.setSignal(node, false)
            signalNodes = signalNodesOf()
            message("Ampel abgebaut")
        }
    }

    fun placeCamera(segment: Int) {
        if (cameraCounters.values.any { it.segment == segment }) return message("Hier blitzt es schon")
        if (treasury.pay(rules.cameraCost, "Blitzer")) {
            val camera = simulation.speedControl.placeCamera(segment)
            cameraCounters[camera.id] = CameraCounter(camera.id, segment, streetName(segment))
            message("Blitzer scharf geschaltet: ${streetName(segment)}")
        }
    }

    fun removeCamera(cameraId: Int) {
        val counter = cameraCounters.remove(cameraId) ?: return
        simulation.speedControl.removeCamera(cameraId)
        message("Blitzer abgebaut: ${counter.streetName}")
    }

    private fun signalNodesOf(): Set<Int> =
        (0 until simulation.graph.nodeCount)
            .filter {
                simulation.graph.nodeHasSignals[it]
            }.toSet()

    private fun streetName(segment: Int): String = city.roads.segments[segment].name ?: "Straße ohne Namen"
}
