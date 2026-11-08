package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.DamageEvent
import de.bfi.trafficmaniac.sim.DamageKind
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.Simulation
import kotlin.math.roundToInt
import kotlin.random.Random

/** Wie viel Geld jede Woche in den Erhalt von Straßen, Ampeln und Brücken fließt. */
enum class MaintenanceBudget(
    private val labelText: String,
    /** Anteil des normalen Verfalls. */
    val decayFactor: Double,
) {
    NONE(translatable("kein Erhaltungsbudget"), 1.0),
    BASIC(translatable("das Nötigste"), 0.5),
    FULL(translatable("vorbildlich"), 0.2),
    ;

    val label: String get() = tr(labelText)
}

/** Die drei Wege zur Reparatur. */
enum class RepairMethod(
    private val labelText: String,
) {
    TENDER(translatable("Normale Ausschreibung")),
    TURBO(translatable("Turbo-Baukonsortium")),
    PLANNING(translatable("Planfeststellungsverfahren")),
    ;

    val label: String get() = tr(labelText)
}

/** Reaktion des Spielers auf eine Schadensmeldung. */
sealed interface DamageResponse {
    data class Repair(
        val method: RepairMethod,
    ) : DamageResponse

    /** "Vorsicht Straßenschäden" mit frei wählbarem Tempolimit; an Brücken zusätzlich ein Gewichtslimit. */
    data class WarningSign(
        val kmh: Int,
    ) : DamageResponse

    /** Nur Brücken: sperren. */
    data object Close : DamageResponse

    data object Ignore : DamageResponse
}

enum class DamageStatus(
    private val labelText: String,
) {
    OPEN(translatable("gemeldet")),
    SIGNED(translatable("Warnschild")),
    CLOSED(translatable("gesperrt")),
    IGNORED(translatable("ignoriert")),
    TENDER(translatable("in Ausschreibung")),
    CONSTRUCTION(translatable("Baustelle")),
    PLANNING(translatable("Planfeststellung")),
    COLLAPSED(translatable("eingestürzt")),
    ;

    val label: String get() = tr(labelText)

    /** Rechtlich abgesichert: keine Klagen nach Unfällen. */
    val isCovered: Boolean get() = this == SIGNED || this == CLOSED || this == PLANNING
}

/** Eine Schadensmeldung für die Oberfläche. Straßenschäden sind je Straße gebündelt. */
data class DamageReport(
    val id: Int,
    val kind: DamageKind,
    /** Erster betroffener Abschnitt (-1 bei Ampeln). */
    val segment: Int,
    val node: Int,
    /** Straßenname (bzw. "Ampel an der …", "Brücke …"). */
    val place: String,
    val reportedAt: GameTime,
    /** Stunden bis zum Ablauf der Frist (negativ: abgelaufen); nur für offene Meldungen sinnvoll. */
    val hoursLeft: Double,
    val status: DamageStatus,
    /** Zustand des schlechtesten Abschnitts in Prozent. */
    val conditionPercent: Int,
    val signKmh: Int? = null,
    val method: RepairMethod? = null,
    /** z. B. "fertig in 5 Std." oder "Fertigstellung voraussichtlich 2066". */
    val progress: String? = null,
    /** Alle betroffenen Abschnitte der Straße. */
    val segments: Set<Int> = if (segment >= 0) setOf(segment) else emptySet(),
    /** Kosten einer normalen Ausschreibung (Turbo: × [GameRules.turboCostFactor]). */
    val repairCost: Long = 0,
) {
    /** Einsturzgefährdeten Brücken hilft kein Schild mehr. */
    val canSign: Boolean get() = kind != DamageKind.BRIDGE_UNSAFE && kind != DamageKind.BRIDGE_COLLAPSE

    /** Nur Brücken lassen sich sperren. */
    val canClose: Boolean get() = kind.isBridge && kind != DamageKind.BRIDGE_COLLAPSE
    val isActionable: Boolean get() = status != DamageStatus.COLLAPSED
}

/** Bauamt für die Oberfläche. */
data class MaintenanceState(
    val budget: MaintenanceBudget = MaintenanceBudget.NONE,
    val reports: List<DamageReport> = emptyList(),
    /** Mittlerer Zustand der Straßen in Prozent. */
    val meanConditionPercent: Int = 100,
    val lawsuitCosts: Long = 0,
    val lawsuits: Int = 0,
    /** Laufende Wartungsverträge mit dem Turbo-Konsortium. */
    val turboContracts: Int = 0,
    val repaired: Int = 0,
    /** Zuletzt reparierte Straße, die noch nicht feierlich wiedereröffnet wurde. */
    val lastRepaired: String? = null,
)

/** Was das Bauamt heute erlebt hat (für die Zeitung). */
internal class MaintenanceNews {
    val ignoredCraters = ArrayList<String>()
    val closedBridges = ArrayList<String>()
    val collapses = ArrayList<Pair<String, Int>>()
    val plannings = ArrayList<Pair<String, Int>>()
    val turboRepairs = ArrayList<String>()
    var lawsuitCosts = 0L
    var lawsuits = 0
}

/**
 * Das Bauamt: Schäden werden gemeldet und müssen innerhalb einer Frist beantwortet werden – reparieren,
 * Warnschild aufstellen oder nichts tun. Wer nichts tut, zahlt nach Unfällen Schadensersatz und liest
 * darüber in der Zeitung. Die Reparaturwege stecken in [Construction].
 *
 * Straßenschäden werden je Straße gebündelt: Bekommt die Goethestraße an einem weiteren Abschnitt Schlaglöcher,
 * wächst die bestehende Meldung, statt eine neue zu öffnen. Brücken und Ampeln werden einzeln gemeldet.
 */
internal class Maintenance(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val works: PublicWorks,
    private val messenger: Messenger,
) {
    private val random = Random(RANDOM_SEED)
    private val places = DamagePlaces(city)
    private val cases = ArrayList<DamageCase>()
    private var nextId = 0
    var budget = MaintenanceBudget.NONE
        set(value) {
            field = value
            simulation.infrastructure.maintenanceFactor = value.decayFactor
        }
    private var lawsuitCosts = 0L
    private var lawsuits = 0
    var today = MaintenanceNews()
        private set
    internal val construction = Construction(simulation, rules, treasury, { today }, messenger, places::locate)

    fun state(time: Double): MaintenanceState =
        MaintenanceState(
            budget = budget,
            reports = cases.map { report(it, time) },
            meanConditionPercent = (simulation.infrastructure.meanCondition() * PERCENT).roundToInt(),
            lawsuitCosts = lawsuitCosts,
            lawsuits = lawsuits,
            turboContracts = construction.turboContracts,
            repaired = construction.repaired,
            lastRepaired = construction.lastRepaired,
        )

    fun weeklyCost(): Long =
        when (budget) {
            MaintenanceBudget.NONE -> 0L
            MaintenanceBudget.BASIC -> rules.maintenanceBudgetBasic
            MaintenanceBudget.FULL -> rules.maintenanceBudgetFull
        } + construction.turboContracts * rules.turboWeeklyMaintenance

    fun startNewDay(): MaintenanceNews = today.also { today = MaintenanceNews() }

    /** Neue Schäden aus der Simulation entgegennehmen. */
    fun collect(time: Double) {
        for (event in simulation.infrastructure.drainEvents()) handle(event, time)
        for (segment in simulation.infrastructure.drainPotholeAccidents()) potholeAccident(segment)
    }

    private fun handle(
        event: DamageEvent,
        time: Double,
    ) {
        val key = places.keyOf(event.segment, event.node)
        val existing = cases.firstOrNull { it.key == key && it.status != DamageStatus.COLLAPSED }
        if (event.kind == DamageKind.BRIDGE_COLLAPSE) return collapse(existing, event, time)
        if (existing == null) {
            val case =
                DamageCase(
                    nextId++,
                    event.kind,
                    key,
                    places.placeOf(event.segment, event.node),
                    event.node,
                    simulation.gameTime,
                    time + rules.damageDeadlineHours * SECONDS_PER_HOUR,
                )
            if (event.segment >= 0) case.segments += event.segment
            cases += case
            messenger.say(
                tr(
                    "Schadensmeldung: {kind} – {place} (Frist {damageDeadlineHours} Std.)",
                    "kind" to event.kind.label,
                    "place" to case.place,
                    "damageDeadlineHours" to rules.damageDeadlineHours,
                ),
                places.locate(case),
            )
            return
        }
        grow(existing, event, time)
    }

    /** Die Straße bekommt einen weiteren Schaden: Meldung wächst, Schild und Baustelle gelten mit. */
    private fun grow(
        case: DamageCase,
        event: DamageEvent,
        time: Double,
    ) {
        val newSegment = event.segment >= 0 && case.segments.add(event.segment)
        if (newSegment) {
            case.signKmh?.let { works.warningSignLimit(event.segment, it) }
            if (case.status == DamageStatus.CONSTRUCTION) {
                simulation.infrastructure.closures.setWorks(event.segment, true, closed = true)
            }
        }
        val worse = event.kind.severity() > case.kind.severity()
        if (worse) case.kind = event.kind
        val signNoLongerEnough = event.kind == DamageKind.BRIDGE_UNSAFE && case.status == DamageStatus.SIGNED
        if (signNoLongerEnough || (worse && case.status == DamageStatus.IGNORED)) {
            case.status = DamageStatus.OPEN
            case.deadline = time + rules.damageDeadlineHours * SECONDS_PER_HOUR
        }
        val hint = if (signNoLongerEnough) tr(" – ein Schild reicht nicht mehr: sperren oder reparieren!") else ""
        if (worse) {
            messenger.say(
                tr(
                    "Es wird schlimmer: {kind} – {place}{hint}",
                    "kind" to event.kind.label,
                    "place" to case.place,
                    "hint" to hint,
                ),
                places.locate(case),
            )
        }
    }

    private fun collapse(
        existing: DamageCase?,
        event: DamageEvent,
        time: Double,
    ) {
        val place = places.placeOf(event.segment, -1)
        val case =
            existing
                ?: DamageCase(
                    nextId++,
                    event.kind,
                    places.keyOf(event.segment, -1),
                    place,
                    -1,
                    simulation.gameTime,
                    time,
                ).also {
                    it.segments += event.segment
                    cases += it
                }
        case.kind = DamageKind.BRIDGE_COLLAPSE
        case.status = DamageStatus.COLLAPSED
        today.collapses += place to event.victims
        if (event.victims > 0) sue(rules.lawsuitMax * event.victims, tr("Einsturz der Brücke"))
        messenger.say(
            tr("EINSTURZ: {place} ist eingestürzt", "place" to place) +
                if (event.victims > 0) tr(" – {victims} Fahrzeuge in der Tiefe", "victims" to event.victims) else "",
            city.placeOfSegment(event.segment),
        )
    }

    /** Unfall durch Schlagloch oder Krater: ohne Schild oder Verfahren klagen die Geschädigten. */
    private fun potholeAccident(segment: Int) {
        val case = cases.firstOrNull { segment in it.segments }
        if (case != null && case.status.isCovered) return
        sue(
            random.nextLong(rules.lawsuitMin, rules.lawsuitMax + 1),
            tr("Unfall durch Schlagloch, {segment}", "segment" to places.streetName(segment)),
        )
    }

    private fun sue(
        cost: Long,
        what: String,
    ) {
        treasury.charge(cost)
        lawsuitCosts += cost
        lawsuits++
        today.lawsuitCosts += cost
        today.lawsuits++
        messenger.say(tr("Schadensersatz: −{cost} ({what})", "cost" to formatEuro(cost), "what" to what))
    }

    fun respond(
        reportId: Int,
        response: DamageResponse,
        time: Double,
    ) {
        val case = cases.firstOrNull { it.id == reportId } ?: return
        if (case.status == DamageStatus.COLLAPSED) return messenger.say(tr("Da ist nichts mehr zu retten."))
        val busy = case.status == DamageStatus.TENDER || case.status == DamageStatus.CONSTRUCTION
        if (busy) return messenger.say(tr("Hier wird schon gebaut."))
        when (response) {
            is DamageResponse.Repair -> {
                construction.order(case, response.method, time)
            }

            is DamageResponse.WarningSign -> {
                sign(case, response.kmh)
            }

            DamageResponse.Close -> {
                close(case)
            }

            DamageResponse.Ignore -> {
                case.status = DamageStatus.IGNORED
                messenger.say(
                    tr("Ignoriert: {kind} – {place}", "kind" to case.kind.label, "place" to case.place),
                    places.locate(case),
                )
            }
        }
    }

    private fun sign(
        case: DamageCase,
        kmh: Int,
    ) {
        val report = report(case, 0.0)
        if (!report.canSign) return messenger.say(tr("Ein Schild reicht hier nicht mehr – sperren oder reparieren!"))
        if (!treasury.pay(rules.warningSignCost, tr("Warnschild"))) return
        case.status = DamageStatus.SIGNED
        if (case.segments.isNotEmpty()) {
            case.signKmh = kmh
            for (segment in case.segments) {
                works.warningSignLimit(segment, kmh)
                if (case.kind.isBridge) {
                    simulation.infrastructure.closures.setWeightLimit(
                        segment,
                        rules.bridgeWeightLimitTons,
                    )
                }
            }
        }
        val weight = if (case.kind.isBridge) ", ${rules.bridgeWeightLimitTons} t" else ""
        val text =
            if (case.node >=
                0
            ) {
                tr("„Ampel ausgefallen“")
            } else {
                tr(
                    "„Vorsicht Straßenschäden“ (Tempo {kmh}{weight})",
                    "kmh" to kmh,
                    "weight" to weight,
                )
            }
        messenger.say(tr("Warnschild {text}: {place}", "text" to text, "place" to case.place), places.locate(case))
    }

    private fun close(case: DamageCase) {
        if (!case.kind.isBridge) return
        if (!treasury.pay(rules.bridgeClosureCost, tr("Absperrung"))) return
        case.status = DamageStatus.CLOSED
        for (segment in case.segments) simulation.infrastructure.closures.setBridgeClosed(segment, true)
        today.closedBridges += case.place
        messenger.say(tr("Brücke gesperrt: {place}", "place" to case.place), places.locate(case))
    }

    /** Jede Stunde: Fristen und Bauphasen. */
    fun hourPassed(time: Double) {
        for (case in cases.toList()) {
            if (case.status == DamageStatus.OPEN && time > case.deadline) {
                case.status = DamageStatus.IGNORED
                messenger.say(
                    tr(
                        "Frist verstrichen: {kind} – {place} bleibt, wie es ist",
                        "kind" to case.kind.label,
                        "place" to case.place,
                    ),
                    places.locate(case),
                )
            }
            if (construction.advance(case, time)) cases.remove(case)
        }
    }

    /** Mitternacht: Reifenschäden an ignorierten Kratern, Gutachten im Planfeststellungsverfahren. */
    fun dayPassed() {
        for (case in cases) {
            if (case.status == DamageStatus.IGNORED && case.kind == DamageKind.CRATER) {
                today.ignoredCraters += case.place
                if (random.nextDouble() <
                    rules.craterClaimsPerDay
                ) {
                    sue(rules.tireClaimCost, "Reifenschaden, ${case.place}")
                }
            }
            if (case.status == DamageStatus.PLANNING && random.nextDouble() < PLANNING_EVENT_CHANCE) {
                val (event, years) = PLANNING_EVENTS[random.nextInt(PLANNING_EVENTS.size)]
                case.planningYear += years
                messenger.say(
                    tr(
                        "Planfeststellung {place}: {event} – Fertigstellung voraussichtlich {planningYear}",
                        "place" to case.place,
                        "event" to event,
                        "planningYear" to case.planningYear,
                    ),
                    places.locate(case),
                )
            }
        }
    }

    private fun report(
        case: DamageCase,
        time: Double,
    ): DamageReport {
        val infrastructure = simulation.infrastructure
        val condition =
            if (case.node >= 0) {
                infrastructure.trafficLights.condition(case.node)
            } else {
                case.segments.minOfOrNull { infrastructure.condition(it) } ?: 0.0
            }
        return DamageReport(
            id = case.id,
            kind = case.kind,
            segment = case.segments.firstOrNull() ?: -1,
            node = case.node,
            place = case.place,
            reportedAt = case.reportedAt,
            hoursLeft = (case.deadline - time) / SECONDS_PER_HOUR,
            status = case.status,
            conditionPercent = (condition * PERCENT).roundToInt(),
            signKmh = case.signKmh,
            method = case.method,
            progress = construction.progress(case, time),
            segments = case.segments.toSet(),
            repairCost = case.repairCost(rules),
        )
    }

    private companion object {
        const val RANDOM_SEED = 815
        const val PERCENT = 100
        const val SECONDS_PER_HOUR = 3_600.0
        const val PLANNING_EVENT_CHANCE = 0.3
        val PLANNING_EVENTS =
            listOf(
                translatable("Gutachten zu Fledermäusen angefordert") to 3,
                translatable("Einspruch eines Anwohners") to 2,
                translatable("Neuplanung nach Kostenexplosion") to 5,
                translatable("Zuständigkeit ungeklärt") to 1,
                translatable("Akten im Keller wiedergefunden") to 1,
            )
    }
}
