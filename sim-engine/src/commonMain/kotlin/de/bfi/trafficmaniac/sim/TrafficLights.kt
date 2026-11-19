package de.bfi.trafficmaniac.sim

import kotlin.random.Random

/**
 * Zustand der Ampeln: Sie altern, nutzen sich mit jedem Fahrzeug ein wenig ab und fallen unter 30 % aus
 * (blinken gelb). Eine Baustelle an der Ampel schaltet sie ebenfalls ab.
 */
class TrafficLights internal constructor(
    private val graph: RoadGraph,
    private val signals: SignalSystem,
    private val config: SimulationConfig,
    random: Random,
    private val report: (DamageEvent) -> Unit,
    private val changed: () -> Unit,
) {
    private val condition =
        DoubleArray(graph.nodeCount) {
            if (graph.nodeHasSignals[it]) initialCondition(random, config) else 1.0
        }
    private val quality = DoubleArray(graph.nodeCount) { 1.0 }

    fun condition(node: Int): Double = condition[node]

    /** Ausgefallen oder abgeschaltet (blinkt gelb). */
    fun isFailed(node: Int): Boolean = signals.isFailed(node)

    /** Zustand zurücksetzen; [wearFactor] > 1 heißt Pfusch. */
    fun repair(
        node: Int,
        newCondition: Double = 1.0,
        wearFactor: Double = 1.0,
    ) {
        condition[node] = newCondition
        quality[node] = wearFactor
        signals.setFailed(node, false)
        changed()
    }

    /** Ampel abschalten (Baustelle) – sie blinkt dann gelb wie bei einem Ausfall. */
    fun setOff(
        node: Int,
        off: Boolean,
    ) {
        signals.setFailed(node, off || condition[node] < Infrastructure.SIGNAL_FAILURE)
        changed()
    }

    /** Alterung aller Ampeln um [amount] (Anteil des Zustands). */
    internal fun age(
        amount: Double,
        time: Double,
    ) {
        for (node in 0 until graph.nodeCount) {
            if (graph.nodeHasSignals[node]) wear(node, amount * quality[node], time)
        }
    }

    /** Ein Fahrzeug fährt durch die Ampel an [node]. */
    internal fun vehiclePassed(
        node: Int,
        time: Double,
    ) {
        if (graph.nodeHasSignals[node]) wear(node, config.signalWearPerVehicle * quality[node], time)
    }

    var maintenanceFactor = 1.0

    private fun wear(
        node: Int,
        amount: Double,
        time: Double,
    ) {
        val before = condition[node]
        val after = (before - amount * maintenanceFactor).coerceAtLeast(0.0)
        condition[node] = after
        if (before >= Infrastructure.SIGNAL_FAILURE && after < Infrastructure.SIGNAL_FAILURE) {
            signals.setFailed(node, true)
            report(DamageEvent(DamageKind.SIGNAL_FAILURE, -1, node, time))
            changed()
        }
    }
}
