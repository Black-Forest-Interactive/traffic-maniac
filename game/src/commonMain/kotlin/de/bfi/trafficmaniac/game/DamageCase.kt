package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.sim.DamageKind
import de.bfi.trafficmaniac.sim.GameTime

/** Eine Schadensmeldung im Bauamt (veränderlich; nach außen gibt es nur [DamageReport]). */
internal class DamageCase(
    val id: Int,
    var kind: DamageKind,
    /** Straße, Brücke oder Ampel – woran die Meldung hängt. */
    val key: String,
    val place: String,
    val node: Int,
    val reportedAt: GameTime,
    var deadline: Double,
    var status: DamageStatus = DamageStatus.OPEN,
) {
    val segments = LinkedHashSet<Int>()
    var signKmh: Int? = null
    var method: RepairMethod? = null
    var phaseUntil = Double.NaN
    var planningYear = 0

    /** Grundpreis je Schaden, jeder weitere Abschnitt der Straße kostet einen Anteil dazu. */
    fun repairCost(rules: GameRules): Long {
        val base =
            when (kind) {
                DamageKind.POTHOLES -> rules.repairCostPotholes
                DamageKind.CRATER -> rules.repairCostCrater
                DamageKind.SIGNAL_FAILURE -> rules.repairCostSignal
                else -> rules.repairCostBridge
            }
        val extra = (segments.size - 1).coerceAtLeast(0)
        return (base * (1 + extra * rules.repairCostPerExtraSegment)).toLong()
    }
}

/** Wie schlimm ein Schaden ist – eine Meldung wird nur schlimmer, nie harmloser. */
internal fun DamageKind.severity(): Int =
    when (this) {
        DamageKind.CRATER, DamageKind.BRIDGE_UNSAFE -> SEVERE
        DamageKind.BRIDGE_COLLAPSE -> FATAL
        else -> 1
    }

private const val SEVERE = 2
private const val FATAL = 3

/** Namen und Schlüssel der Orte, an denen Schäden gemeldet werden. */
internal class DamagePlaces(
    private val city: CityMap,
) {
    /** Straßenschäden je Straßenname, Brücken je Abschnitt, Ampeln je Knoten. */
    fun keyOf(
        segment: Int,
        node: Int,
    ): String =
        when {
            node >= 0 -> {
                "ampel:$node"
            }

            city.roads.segments[segment].bridge -> {
                "bruecke:$segment"
            }

            else -> {
                city.roads.segments[segment]
                    .name
                    ?.let { "strasse:$it" } ?: "abschnitt:$segment"
            }
        }

    fun placeOf(
        segment: Int,
        node: Int,
    ): String =
        when {
            node >= 0 -> "Ampel an der ${streetAtNode(node)}"
            city.roads.segments[segment].bridge -> "Brücke ${streetName(segment)}"
            else -> streetName(segment)
        }

    fun streetName(segment: Int): String = city.roads.segments[segment].name ?: "Straße ohne Namen"

    private fun streetAtNode(node: Int): String =
        city.roads.segments
            .firstOrNull { (it.from == node || it.to == node) && !it.passive && it.name != null }
            ?.name
            ?: "Kreuzung ohne Namen"
}
