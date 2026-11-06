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
    val nightLimits = LinkedHashMap<Int, Int>()
    val priorityRoads = LinkedHashSet<Int>()
    val bikeCrossings = LinkedHashSet<Int>()
    val achievements = LinkedHashSet<String>()
    val cameraCounters = LinkedHashMap<Int, CameraCounter>()
    var signalNodes: Set<Int> = signalNodesOf()
        private set

    fun setSpeedLimit(
        segment: Int,
        kmh: Int?,
        nightOnly: Boolean,
    ) {
        val limits = if (nightOnly) nightLimits else speedLimits
        if (limits[segment] == kmh) return
        val street = streetName(segment)
        val suffix = if (nightOnly) " nur nachts" else ""
        if (kmh == null) {
            simulation.graph.setSegmentSpeedLimit(segment, null, nightOnly)
            limits.remove(segment)
            message("Tempolimit$suffix aufgehoben: $street")
        } else if (treasury.pay(rules.speedLimitSignCost, "Tempo-$kmh-Schild")) {
            simulation.graph.setSegmentSpeedLimit(segment, kmh, nightOnly)
            limits[segment] = kmh
            message("Tempo $kmh$suffix: $street")
            checkSignForest(segment)
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

    fun placeCamera(
        segment: Int,
        mobile: Boolean,
    ) {
        if (cameraCounters.values.any { it.segment == segment }) return message("Hier blitzt es schon")
        val affordable = if (mobile) true else treasury.pay(rules.cameraCost, "Blitzer")
        if (affordable) {
            val camera = simulation.speedControl.placeCamera(segment, mobile)
            cameraCounters[camera.id] = CameraCounter(camera.id, segment, streetName(segment), mobile)
            message("${if (mobile) "Lasermessung" else "Blitzer"} scharf geschaltet: ${streetName(segment)}")
            checkSignForest(segment)
        }
    }

    fun setPriorityRoad(
        segment: Int,
        priority: Boolean,
    ) {
        if ((segment in priorityRoads) == priority) return
        if (priority && !treasury.pay(rules.priorityRoadCost, "Vorfahrtsschilder")) return
        simulation.graph.setSegmentPriority(segment, priority)
        if (priority) priorityRoads += segment else priorityRoads -= segment
        message(
            if (priority) {
                "Neue Vorfahrtsstraße: ${streetName(
                    segment,
                )}"
            } else {
                "Vorfahrt aufgehoben: ${streetName(segment)}"
            },
        )
        checkSignForest(segment)
    }

    fun setBikeCrossing(
        segment: Int,
        crossing: Boolean,
    ) {
        if ((segment in bikeCrossings) == crossing) return
        if (crossing && !treasury.pay(rules.bikeCrossingCost, "Radweg-Querung")) return
        simulation.graph.setSegmentBikeCrossing(segment, crossing)
        if (crossing) bikeCrossings += segment else bikeCrossings -= segment
        message(
            if (crossing) {
                "Radweg-Querung markiert: ${streetName(
                    segment,
                )}"
            } else {
                "Radweg-Querung entfernt: ${streetName(segment)}"
            },
        )
        checkSignForest(segment)
    }

    /**
     * Achievement "Schilderwald": Tempolimit, Nachtlimit, Vorfahrtsstraße, Radweg-Querung und zwei Blitzer
     * auf einem Abschnitt und seinen direkten Nachbarn.
     */
    private fun checkSignForest(segment: Int) {
        if (SIGN_FOREST in achievements) return
        val nodes =
            simulation.graph.segmentLinks[segment]
                .flatMap {
                    listOf(simulation.graph.linkFrom[it], simulation.graph.linkTo[it])
                }.toSet()
        val area =
            (0 until simulation.graph.segmentCount).filter { other ->
                simulation.graph.segmentLinks[other].any {
                    simulation.graph.linkFrom[it] in nodes ||
                        simulation.graph.linkTo[it] in nodes
                }
            }
        val complete =
            area.any { it in speedLimits } &&
                area.any { it in nightLimits } &&
                area.any { it in priorityRoads } &&
                area.any { it in bikeCrossings } &&
                cameraCounters.values.count { it.segment in area } >= 2
        if (complete) {
            achievements += SIGN_FOREST
            message("Achievement freigeschaltet: $SIGN_FOREST!")
        }
    }

    /** Anzahl aktiver Lasermessungen (kosten jeden Tag Personal). */
    val laserCount: Int get() = cameraCounters.values.count { it.mobile }

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

    private companion object {
        const val SIGN_FOREST = "Schilderwald"
    }
}
