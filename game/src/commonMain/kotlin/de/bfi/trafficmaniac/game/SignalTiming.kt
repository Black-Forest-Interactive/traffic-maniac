package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.greenWave
import de.bfi.trafficmaniac.sim.linksAlong
import kotlin.math.roundToInt

/**
 * Ampelprogramme des Spielers: Grünzeit zwischen Haupt- und Querrichtung aufteilen und grüne Wellen entlang einer
 * Straße. Billig (eine Programmierung je Anlage) – damit es auch ohne volle Kasse etwas zu entscheiden gibt.
 */
internal class SignalTiming(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val message: (String) -> Unit,
) {
    /** Abschnitte der grünen Wellen (für die Karte); eine neue Welle verdrängt alte auf denselben Abschnitten. */
    val greenWaves = ArrayList<List<Int>>()

    fun execute(command: GameCommand) {
        when (command) {
            is GameCommand.SetSignalProgram -> setSplit(command.node, command.mainShare)
            is GameCommand.SetGreenWave -> setGreenWave(command.segments, command.kmh)
            else -> Unit
        }
    }

    /** Grünzeit-Anteil der Hauptrichtung an der Anlage von [node]. */
    private fun setSplit(
        node: Int,
        mainShare: Double,
    ) {
        val signals = simulation.signals
        val current = signals.program(node) ?: return message(tr("Hier ist keine Ampel"))
        if (current.mainShare == mainShare) return
        if (!treasury.pay(rules.signalProgramCost, tr("Ampelprogramm"))) return
        signals.setPrograms(mapOf(node to current.copy(mainShare = mainShare)))
        message(
            tr(
                "Ampelprogramm {percent} % Hauptrichtung: {street}",
                "percent" to (mainShare * PERCENT).roundToInt(),
                "street" to streetAt(node),
            ),
        )
    }

    /** Grüne Welle für [kmh] entlang [segments] (vom ersten zum letzten); bezahlt wird je Anlage auf dem Weg. */
    private fun setGreenWave(
        segments: List<Int>,
        kmh: Int,
    ) {
        val graph = simulation.graph
        val links = graph.linksAlong(segments)
        val plants = links.mapNotNull { simulation.signals.plantOf(it) }.distinct()
        if (plants.size < 2) return message(tr("Für eine grüne Welle braucht es mindestens zwei Ampeln auf dem Weg"))
        if (!treasury.pay(rules.signalProgramCost * plants.size, tr("Grüne Welle"))) return
        simulation.signals.greenWave(graph, links, kmh / KMH_PER_MS)
        greenWaves.removeAll { wave -> wave.any { it in segments } }
        greenWaves += segments
        val street = segments.firstNotNullOfOrNull { city.roads.segments[it].name } ?: tr("Straße ohne Namen")
        message(
            tr(
                "Grüne Welle {kmh} km/h: {street} ({count} Ampeln)",
                "kmh" to kmh,
                "street" to street,
                "count" to plants.size,
            ),
        )
    }

    private fun streetAt(node: Int): String =
        city.roads.segments
            .filter { !it.passive && (it.from == node || it.to == node) }
            .firstNotNullOfOrNull { it.name } ?: tr("Kreuzung")

    private companion object {
        const val PERCENT = 100
        const val KMH_PER_MS = 3.6
    }
}
