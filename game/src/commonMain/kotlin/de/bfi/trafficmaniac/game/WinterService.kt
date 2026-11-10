package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation

/**
 * Winterdienst: wie viele Räumfahrzeuge unterwegs sind (jedes kostet je Tag), und Meldungen, wenn es zu schneien
 * beginnt oder aufhört. Nur in Szenarien mit Wetter.
 */
internal class WinterService(
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val message: (String) -> Unit,
) {
    private val weather = simulation.weather
    private var wasSnowing = false

    val enabled: Boolean get() = weather.isEnabled
    val ploughs: Int get() = weather.ploughCount

    fun execute(command: GameCommand) {
        if (command !is GameCommand.SetPloughs || !enabled) return
        val count = command.count.coerceIn(0, rules.maxPloughs)
        if (count == weather.ploughCount) return
        weather.ploughCount = count
        message(tr("Winterdienst: {count} Räumfahrzeuge", "count" to count))
    }

    /** Jeden Schritt: Beginn und Ende des Schneefalls melden. */
    fun update() {
        if (!enabled || weather.snowing == wasSnowing) return
        wasSnowing = weather.snowing
        message(
            if (wasSnowing) {
                tr("Es schneit! Winterdienst mit {count} Räumfahrzeugen unterwegs", "count" to weather.ploughCount)
            } else {
                tr("Es hat aufgehört zu schneien")
            },
        )
    }

    /** Mitternacht: Räumfahrzeuge bezahlen. */
    fun dayEnded() {
        if (enabled && weather.ploughCount > 0) treasury.charge(weather.ploughCount * rules.ploughDailyCost)
    }
}
