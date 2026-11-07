package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.Simulation
import kotlin.math.roundToInt

/**
 * Die drei Reparaturwege: Ausschreibung (Vergabe, dann Baustelle mit Vollsperrung), Turbo-Baukonsortium
 * (nur nachts, Pfusch und Wartungsvertrag) und Planfeststellung (wird nie fertig).
 */
internal class Construction(
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val news: () -> MaintenanceNews,
    private val messenger: Messenger,
    private val locate: (DamageCase) -> MapPlace?,
) {
    var turboContracts = 0
        private set
    var repaired = 0
        private set

    /** Zuletzt fertig gewordene Reparatur – ein Anlass für eine feierliche Wiedereröffnung. */
    var lastRepaired: String? = null

    /** Reparatur beauftragen; `false`, wenn das Geld nicht reicht. */
    fun order(
        case: DamageCase,
        method: RepairMethod,
        time: Double,
    ) {
        val base = case.repairCost(rules)
        val cost =
            when (method) {
                RepairMethod.TENDER -> base
                RepairMethod.TURBO -> (base * rules.turboCostFactor).toLong()
                RepairMethod.PLANNING -> rules.planningCost
            }
        if (!treasury.pay(cost, method.label)) return
        case.method = method
        when (method) {
            RepairMethod.TENDER -> {
                case.status = DamageStatus.TENDER
                case.phaseUntil = time + rules.tenderDays * SECONDS_PER_DAY
                messenger.say("Ausschreibung läuft: ${case.place}", locate(case))
            }

            RepairMethod.TURBO -> {
                // Gebaut wird nachts: sofort, wenn es schon Nacht ist, sonst ab 22 Uhr
                val hour = simulation.gameTime.hour
                val night = hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
                case.status = DamageStatus.TENDER
                case.phaseUntil = if (night) time else time + secondsUntil(NIGHT_START_HOUR)
                if (night) start(case, time)
                messenger.say(
                    "Turbo-Baukonsortium „Nachtschicht & Söhne“ beauftragt: ${case.place} – gebaut wird nachts",
                    locate(case),
                )
            }

            RepairMethod.PLANNING -> {
                case.status = DamageStatus.PLANNING
                case.planningYear = rules.planningStartYear
                news().plannings += case.place to case.planningYear
                messenger.say(
                    "Planfeststellung eröffnet: ${case.place} – Fertigstellung voraussichtlich ${case.planningYear}",
                    locate(case),
                )
            }
        }
    }

    /** Bauphasen fortschreiben; `true`, wenn die Reparatur fertig ist. */
    fun advance(
        case: DamageCase,
        time: Double,
    ): Boolean {
        if (case.status == DamageStatus.TENDER && time >= case.phaseUntil) start(case, time)
        if (case.status != DamageStatus.CONSTRUCTION || time < case.phaseUntil) return false
        finish(case)
        return true
    }

    fun progress(
        case: DamageCase,
        time: Double,
    ): String? =
        when (case.status) {
            DamageStatus.TENDER -> "Baubeginn in ${hours(case.phaseUntil - time)}"
            DamageStatus.CONSTRUCTION -> "fertig in ${hours(case.phaseUntil - time)}"
            DamageStatus.PLANNING -> "Fertigstellung voraussichtlich ${case.planningYear}"
            else -> null
        }

    private fun start(
        case: DamageCase,
        time: Double,
    ) {
        case.status = DamageStatus.CONSTRUCTION
        val turbo = case.method == RepairMethod.TURBO
        case.phaseUntil =
            if (turbo) time + secondsUntil(NIGHT_END_HOUR) else time + rules.constructionDays * SECONDS_PER_DAY
        // Vollsperrung: Jetzt ist eine Umleitung gefragt
        for (segment in case.segments) simulation.infrastructure.closures.setWorks(segment, true, closed = true)
        if (case.node >= 0) simulation.infrastructure.trafficLights.setOff(case.node, true)
        messenger.say("Baustelle eingerichtet (Vollsperrung): ${case.place}", locate(case))
    }

    private fun finish(case: DamageCase) {
        val turbo = case.method == RepairMethod.TURBO
        val condition = if (turbo) rules.turboCondition else 1.0
        val wear = if (turbo) rules.turboWearFactor else 1.0
        val infrastructure = simulation.infrastructure
        for (segment in case.segments) {
            infrastructure.closures.setWorks(segment, false)
            infrastructure.repairSegment(segment, condition, wear)
            infrastructure.closures.setBridgeClosed(segment, false)
            if (case.kind.isBridge) infrastructure.closures.setWeightLimit(segment, null)
        }
        if (case.node >= 0) {
            infrastructure.trafficLights.repair(case.node, condition, wear)
            infrastructure.trafficLights.setOff(case.node, false)
        }
        if (turbo) {
            turboContracts++
            news().turboRepairs += case.place
            val fee = formatEuro(rules.turboWeeklyMaintenance)
            messenger.say("Über Nacht repariert: ${case.place} – Wartungsvertrag läuft ($fee/Woche)", locate(case))
        } else {
            messenger.say("Repariert: ${case.place}", locate(case))
        }
        repaired++
        lastRepaired = case.place
    }

    /** Sekunden bis zur nächsten vollen [hour] Uhr (0, wenn es gerade so weit ist). */
    private fun secondsUntil(hour: Int): Double {
        val now = simulation.gameTime.secondOfDay
        val target = hour * SECONDS_PER_HOUR
        return ((target - now) % SECONDS_PER_DAY + SECONDS_PER_DAY) % SECONDS_PER_DAY
    }

    private fun hours(seconds: Double): String {
        val h = (seconds / SECONDS_PER_HOUR).coerceAtLeast(0.0)
        return if (h < HOURS_PER_DAY) "${h.roundToInt()} Std." else "${"%.1f".format(h / HOURS_PER_DAY)} Tagen"
    }

    private companion object {
        const val SECONDS_PER_HOUR = 3_600.0
        const val SECONDS_PER_DAY = 86_400.0
        const val HOURS_PER_DAY = 24
        const val NIGHT_START_HOUR = 22
        const val NIGHT_END_HOUR = 5
    }
}
