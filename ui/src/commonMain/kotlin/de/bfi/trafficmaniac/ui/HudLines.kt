package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.EnvironmentSnapshot
import de.bfi.trafficmaniac.sim.Pollutant
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import kotlin.math.roundToInt

/** Zeilen für die aufgeklappte Verkehrslage: Umwelt, Pendler, offene Vorfälle. */
internal fun environmentLine(environment: EnvironmentSnapshot): String {
    val totals = environment.totals
    val particulates = PARTICULATES.sumOf { totals[it] ?: 0.0 } / GRAMS_PER_KG
    val co2 = (totals[Pollutant.CO2] ?: 0.0) / GRAMS_PER_TONNE
    return tr(
        "Sprit {totalFuelLiters} l · CO₂ {co2} t · Feinstaub {particulates} kg",
        "totalFuelLiters" to formatDecimal(environment.totalFuelLiters, 0),
        "co2" to formatDecimal(co2),
        "particulates" to formatDecimal(particulates, 2),
    )
}

internal fun commuteLine(frame: TrafficFrame): String {
    val yesterday = frame.game.commuteYesterday
    if (yesterday.trips == 0) return tr("Pendler: noch keine Tagesbilanz")
    return tr(
        "Zu spät gestern: {lateShare} % · Schaden {commuteDamage}",
        "lateShare" to (yesterday.lateShare * PERCENT).roundToInt(),
        "commuteDamage" to formatEuro(frame.game.commuteDamage),
    )
}

internal fun incidentLine(snapshot: SimulationSnapshot): String {
    val open =
        snapshot.incidents
            .groupingBy { it.kind.label }
            .eachCount()
            .entries
            .joinToString { "${it.value}× ${it.key}" }
    val waiting = snapshot.chargingStations.sumOf { it.waiting }
    return tr(
        "Vorfälle: {open} · an Ladesäulen wartend: {waiting}",
        "open" to open.ifEmpty { tr("keine") },
        "waiting" to waiting,
    )
}

private val PARTICULATES = listOf(Pollutant.PM_EXHAUST, Pollutant.PM_TIRES, Pollutant.PM_BRAKES)
private const val GRAMS_PER_KG = 1_000.0
private const val GRAMS_PER_TONNE = 1_000_000.0
private const val PERCENT = 100
