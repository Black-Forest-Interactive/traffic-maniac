package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.StreetStretches
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation
import kotlin.random.Random

/**
 * Baustellen und Vollsperrungen, die der Spieler selbst einrichtet.
 *
 * - **Baustelle:** Fahrbahn verengt, Tempo 30 – und gearbeitet wird nicht. Kostet Einrichtung und Absicherung je
 *   Tag. Mit einem Blitzer daneben eine Goldgrube, aber ab dem dritten Tag kann die Presse jeden Tag merken, dass
 *   dort niemand arbeitet.
 * - **Vollsperrung:** Absperrgitter, der Verkehr muss außen herum.
 *
 * Bezahlt wird je Strecke von Kreuzung zu Kreuzung.
 */
internal class Roadworks(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val works: PublicWorks,
    private val message: (String) -> Unit,
) {
    private val stretches = StreetStretches(city.roads)
    private val random = Random(RANDOM_SEED)

    /** Baustellen je Abschnitt: Spieltag der Einrichtung und das Tempolimit, das vorher galt. */
    private val sites = LinkedHashMap<Int, Site>()

    /** Voll gesperrte Abschnitte. */
    val closed = LinkedHashSet<Int>()

    val roadworks: Set<Int> get() = sites.keys

    /** Fahrradstraßen je Abschnitt mit dem Tempolimit, das vorher galt. */
    private val bikeStreetLimits = LinkedHashMap<Int, Int?>()

    val bikeStreets: Set<Int> get() = bikeStreetLimits.keys

    private class Site(
        val day: Int,
        val previousLimit: Int?,
    )

    fun execute(command: GameCommand) {
        when (command) {
            is GameCommand.SetRoadworks -> setRoadworks(command.segments, command.active)
            is GameCommand.SetRoadClosure -> setClosure(command.segments, command.closed)
            is GameCommand.SetBikeStreet -> setBikeStreet(command.segments, command.on)
            else -> Unit
        }
    }

    private fun stretchCount(segments: Collection<Int>): Int =
        segments
            .map {
                stretches.stretchOf(it).min()
            }.distinct()
            .size

    private fun setRoadworks(
        segments: List<Int>,
        active: Boolean,
    ) {
        val changing = segments.filter { (it in sites) != active }
        if (changing.isEmpty()) return
        val street = streetOf(changing)
        if (active) {
            if (!treasury.pay(rules.roadworksSetupCost * stretchCount(changing), tr("Baustelle einrichten"))) return
            for (segment in changing) {
                sites[segment] = Site(simulation.gameTime.day, works.speedLimits[segment])
                simulation.infrastructure.closures.setPlayerWorks(segment, true)
                works.warningSignLimit(segment, ROADWORKS_LIMIT)
            }
            message(tr("Baustelle eingerichtet: {street}", "street" to street))
        } else {
            for (segment in changing) {
                val site = sites.remove(segment) ?: continue
                simulation.infrastructure.closures.setPlayerWorks(segment, false)
                restoreLimit(segment, site.previousLimit)
            }
            message(tr("Baustelle abgebaut: {street}", "street" to street))
        }
    }

    private fun restoreLimit(
        segment: Int,
        previous: Int?,
    ) {
        if (previous != null) {
            works.warningSignLimit(segment, previous)
        } else {
            simulation.graph.setSegmentSpeedLimit(segment, null)
            works.speedLimits.remove(segment)
        }
    }

    private fun setClosure(
        segments: List<Int>,
        close: Boolean,
    ) {
        val changing = segments.filter { (it in closed) != close }
        if (changing.isEmpty()) return
        val street = streetOf(changing)
        if (close && !treasury.pay(rules.closureCost * stretchCount(changing), tr("Absperrung"))) return
        for (segment in changing) {
            simulation.infrastructure.closures.setPlayerClosed(segment, close)
            if (close) closed += segment else closed -= segment
        }
        message(
            if (close) {
                tr("Vollsperrung: {street}", "street" to street)
            } else {
                tr("Sperrung aufgehoben: {street}", "street" to street)
            },
        )
    }

    /** Fahrradstraße: Tempo 30, und Kfz dürfen Radfahrer nicht überholen; bezahlt wird je Strecke. */
    private fun setBikeStreet(
        segments: List<Int>,
        on: Boolean,
    ) {
        val changing = segments.filter { (it in bikeStreetLimits) != on }
        if (changing.isEmpty()) return
        if (on && !treasury.pay(rules.bikeStreetCost * stretchCount(changing), tr("Fahrradstraße"))) return
        for (segment in changing) {
            simulation.graph.setSegmentBikeStreet(segment, on)
            if (on) {
                bikeStreetLimits[segment] = works.speedLimits[segment]
                works.warningSignLimit(segment, ROADWORKS_LIMIT)
            } else {
                restoreLimit(segment, bikeStreetLimits.remove(segment))
            }
        }
        val street = streetOf(changing)
        message(
            if (on) {
                tr("Fahrradstraße: {street} – Kfz dürfen nicht überholen", "street" to street)
            } else {
                tr("Fahrradstraße aufgehoben: {street}", "street" to street)
            },
        )
    }

    /**
     * Mitternacht: Absicherung der Baustellen bezahlen und schauen, ob die Presse eine Baustelle ohne Bauarbeiter
     * entdeckt. Liefert die Änderung der Reputation (≤ 0).
     */
    fun dayEnded(day: Int): Double {
        if (sites.isEmpty()) return 0.0
        treasury.charge(rules.roadworksDailyCost * stretchCount(sites.keys))
        val stale = sites.filterValues { day - it.day >= rules.fakeRoadworksGraceDays }.keys
        if (stale.isEmpty() || random.nextDouble() >= rules.fakeRoadworksPressChance) return 0.0
        message(
            tr(
                "Presse: „Baustelle ohne Bauarbeiter“ – {street} ({reputation} Reputation)",
                "street" to streetOf(stale),
                "reputation" to -rules.fakeRoadworksReputationLoss.toInt(),
            ),
        )
        return -rules.fakeRoadworksReputationLoss
    }

    private fun streetOf(segments: Collection<Int>): String =
        segments.firstNotNullOfOrNull { city.roads.segments[it].name } ?: tr("Straße ohne Namen")

    private companion object {
        const val ROADWORKS_LIMIT = 30
        const val RANDOM_SEED = 1312
    }
}
