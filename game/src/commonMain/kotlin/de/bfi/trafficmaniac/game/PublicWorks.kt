package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.StreetStretches
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation

/** Das Tiefbauamt: stellt Schilder, Ampeln und Blitzer auf – gegen Bezahlung. */
internal class PublicWorks(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val message: (String) -> Unit,
) {
    private val stretches = StreetStretches(city.roads)
    val speedLimits = LinkedHashMap<Int, Int>()
    val nightLimits = LinkedHashMap<Int, Int>()
    val priorityRoads = LinkedHashSet<Int>()
    val bikeCrossings = LinkedHashSet<Int>()
    val achievements = LinkedHashSet<String>()
    val cameraCounters = LinkedHashMap<Int, CameraCounter>()
    var signalNodes: Set<Int> = signalNodesOf()
        private set

    /**
     * Tempolimit für die ganze Strecke von Kreuzung zu Kreuzung, zu der [segment] gehört – ein Schild am Anfang,
     * eins am Ende, bezahlt wird einmal. `null` hebt es für die Strecke auf.
     */
    fun setSpeedLimit(
        segment: Int,
        kmh: Int?,
        nightOnly: Boolean,
    ) {
        val limits = if (nightOnly) nightLimits else speedLimits
        val stretch = stretches.stretchOf(segment)
        if (stretch.all { limits[it] == kmh }) return
        val street = streetName(segment)
        val suffix = if (nightOnly) tr(" nur nachts") else ""
        if (kmh == null) {
            for (s in stretch) {
                simulation.graph.setSegmentSpeedLimit(s, null, nightOnly)
                limits.remove(s)
            }
            message(tr("Tempolimit{suffix} aufgehoben: {street}", "suffix" to suffix, "street" to street))
        } else if (treasury.pay(rules.speedLimitSignCost, tr("Tempo-{kmh}-Schild", "kmh" to kmh))) {
            for (s in stretch) {
                simulation.graph.setSegmentSpeedLimit(s, kmh, nightOnly)
                limits[s] = kmh
            }
            message(tr("Tempo {kmh}{suffix}: {street}", "kmh" to kmh, "suffix" to suffix, "street" to street))
            checkSignForest(segment)
        }
    }

    /** Tempolimit eines Warnschilds (im Preis des Schilds enthalten). */
    fun warningSignLimit(
        segment: Int,
        kmh: Int,
    ) {
        simulation.graph.setSegmentSpeedLimit(segment, kmh)
        speedLimits[segment] = kmh
        checkSignForest(segment)
    }

    /** Umleitungsabschnitte; passive Nebenstraßen werden dabei zugeschaltet. */
    val detourSegments = LinkedHashSet<Int>()

    fun setDetour(
        segment: Int,
        enabled: Boolean,
    ) {
        if ((segment in detourSegments) == enabled) return
        simulation.infrastructure.closures.setDetour(segment, enabled)
        if (enabled) detourSegments += segment else detourSegments -= segment
        val street = streetName(segment)
        val side = if (city.roads.segments[segment].passive) tr(" (Nebenstraße zugeschaltet)") else ""
        message(
            if (enabled) {
                tr("Umleitung über {street}{side}", "street" to street, "side" to side)
            } else {
                tr(
                    "Umleitung aufgehoben: {street}",
                    "street" to street,
                )
            },
        )
    }

    fun buildSignal(node: Int) {
        if (simulation.graph.nodeHasSignals[node]) return message(tr("Hier steht schon eine Ampel"))
        if (treasury.pay(rules.signalCost, tr("Ampel"))) {
            simulation.signals.setSignal(node, true)
            signalNodes = signalNodesOf()
            message(tr("Neue Ampel in Betrieb"))
        }
    }

    fun removeSignal(node: Int) {
        if (!simulation.graph.nodeHasSignals[node]) return
        if (treasury.pay(rules.signalRemovalCost, tr("Abbau der Ampel"))) {
            simulation.signals.setSignal(node, false)
            signalNodes = signalNodesOf()
            message(tr("Ampel abgebaut"))
        }
    }

    fun placeCamera(
        segment: Int,
        mobile: Boolean,
    ) {
        if (cameraCounters.values.any { it.segment == segment }) return message(tr("Hier blitzt es schon"))
        val affordable = if (mobile) true else treasury.pay(rules.cameraCost, tr("Blitzer"))
        if (affordable) {
            val camera = simulation.speedControl.placeCamera(segment, mobile)
            cameraCounters[camera.id] = CameraCounter(camera.id, segment, streetName(segment), mobile)
            message(
                tr(
                    "{blitzer} scharf geschaltet: {segment}",
                    "blitzer" to if (mobile) tr("Lasermessung") else tr("Blitzer"),
                    "segment" to streetName(segment),
                ),
            )
            checkSignForest(segment)
        }
    }

    /** Blitzeranhänger, die gerade im Depot stehen bzw. insgesamt gekauft wurden. */
    var idleTrailers = 0
        private set
    var trailers = 0
        private set

    /**
     * Blitzeranhänger an [segment] aufstellen: Steht einer im Depot, kostet nur das Umsetzen, sonst wird ein neuer
     * gekauft. Ortskundige kennen ihn nicht – er erwischt mehr als ein fester Blitzer.
     */
    fun placeTrailer(segment: Int) {
        if (cameraCounters.values.any { it.segment == segment }) return message(tr("Hier blitzt es schon"))
        val fromDepot = idleTrailers > 0
        val paid =
            if (fromDepot) {
                treasury.pay(rules.trailerMoveCost, tr("Blitzeranhänger umsetzen"))
            } else {
                treasury.pay(rules.trailerCost, tr("Blitzeranhänger"))
            }
        if (!paid) return
        if (fromDepot) idleTrailers-- else trailers++
        val camera = simulation.speedControl.placeCamera(segment, mobile = true, trailer = true)
        cameraCounters[camera.id] =
            CameraCounter(camera.id, segment, streetName(segment), mobile = true, trailer = true)
        message(tr("Blitzeranhänger aufgestellt: {segment}", "segment" to streetName(segment)))
        checkSignForest(segment)
    }

    fun setPriorityRoad(
        segment: Int,
        priority: Boolean,
    ) {
        if ((segment in priorityRoads) == priority) return
        if (priority && !treasury.pay(rules.priorityRoadCost, tr("Vorfahrtsschilder"))) return
        simulation.graph.setSegmentPriority(segment, priority)
        if (priority) priorityRoads += segment else priorityRoads -= segment
        message(
            if (priority) {
                tr(
                    "Neue Vorfahrtsstraße: {segment}",
                    "segment" to
                        streetName(
                            segment,
                        ),
                )
            } else {
                tr("Vorfahrt aufgehoben: {segment}", "segment" to streetName(segment))
            },
        )
        checkSignForest(segment)
    }

    fun setBikeCrossing(
        segment: Int,
        crossing: Boolean,
    ) {
        if ((segment in bikeCrossings) == crossing) return
        if (crossing && !treasury.pay(rules.bikeCrossingCost, tr("Radweg-Querung"))) return
        simulation.graph.setSegmentBikeCrossing(segment, crossing)
        if (crossing) bikeCrossings += segment else bikeCrossings -= segment
        message(
            if (crossing) {
                tr(
                    "Radweg-Querung markiert: {segment}",
                    "segment" to
                        streetName(
                            segment,
                        ),
                )
            } else {
                tr("Radweg-Querung entfernt: {segment}", "segment" to streetName(segment))
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
            // Gemeldet wird über die Achievements des Spiels
            achievements += SIGN_FOREST
        }
    }

    /** Anzahl aktiver Lasermessungen (kosten jeden Tag Personal). */
    val laserCount: Int get() = cameraCounters.values.count { it.mobile && !it.trailer }

    fun removeCamera(cameraId: Int) {
        val counter = cameraCounters.remove(cameraId) ?: return
        simulation.speedControl.removeCamera(cameraId)
        if (counter.trailer) {
            // Der Anhänger gehört weiter der Stadt und wartet im Depot auf den nächsten Einsatz
            idleTrailers++
            message(
                tr(
                    "Blitzeranhänger aufgeladen: {streetName} – mit dem Anhänger-Werkzeug auf eine Straße klicken, " +
                        "um ihn dort aufzustellen",
                    "streetName" to counter.streetName,
                ),
            )
        } else {
            message(tr("Blitzer abgebaut: {streetName}", "streetName" to counter.streetName))
        }
    }

    private fun signalNodesOf(): Set<Int> =
        (0 until simulation.graph.nodeCount)
            .filter {
                simulation.graph.nodeHasSignals[it]
            }.toSet()

    private fun streetName(segment: Int): String = city.roads.segments[segment].name ?: tr("Straße ohne Namen")

    companion object {
        const val SIGN_FOREST = "Schilderwald"
    }
}
