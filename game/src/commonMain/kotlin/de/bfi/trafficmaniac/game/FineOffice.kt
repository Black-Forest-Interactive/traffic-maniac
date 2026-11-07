package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.GameTime
import kotlin.math.exp
import kotlin.random.Random

/** Wie streng das Ordnungsamt ist. */
enum class Strictness(
    val label: String,
    /** Messtoleranz in km/h. */
    val toleranceKmh: Double,
    /** Anteil erfolgreicher Einsprüche. */
    val appealSuccess: Double,
    /** So sehr ärgern sich die Bürger über einen Bescheid. */
    val painFactor: Double,
    /** Knöllchen erst nach so vielen Minuten Falschparken. */
    val parkingGraceMinutes: Double,
) {
    LENIENT("kulant", toleranceKmh = 5.0, appealSuccess = 0.15, painFactor = 0.8, parkingGraceMinutes = 15.0),
    NORMAL("normal", toleranceKmh = 3.0, appealSuccess = 0.05, painFactor = 1.0, parkingGraceMinutes = 5.0),
    RUTHLESS("gnadenlos", toleranceKmh = 1.0, appealSuccess = 0.0, painFactor = 1.3, parkingGraceMinutes = 0.0),
}

/** Was passiert, wenn auf dem Foto niemand zu erkennen ist. */
enum class UnidentifiedPolicy(
    val label: String,
) {
    DROP("Verfahren einstellen"),
    QUESTIONNAIRE("Zeugenfragebogen"),
    LOGBOOK("Fahrtenbuch androhen"),
}

/** Zustand der Bußgeldstelle für die Oberfläche. */
data class FineOfficeState(
    val staff: Int,
    val hiring: Int,
    val backlog: Int,
    val oldestCaseHours: Double,
    val identificationRate: Double,
    val processed: Int,
    val paid: Int,
    val expired: Int,
    val dropped: Int,
    val strictness: Strictness,
    val policy: UnidentifiedPolicy,
    val deadlineDays: Int,
)

/** Ein Blitzerfoto auf dem Stapel. */
internal class FineCase(
    val createdAt: Double,
    val tier: FineTier,
    val homeZone: Int,
    val person: Int,
)

/**
 * Die Bußgeldstelle: Blitzerfotos landen auf einem Stapel und werden zu Bürozeiten (werktags 8–16 Uhr)
 * abgearbeitet. Erst mit dem Bescheid fließt Geld und ärgern sich die Bürger ("Bescheid-Welle").
 * Fälle, die die Bearbeitungsfrist überschreiten, verjähren.
 */
internal class FineOffice(
    private val rules: GameRules,
    private val treasury: Treasury,
    private val citizens: Citizens,
    private val drivers: DriverRegistry,
    private val message: (String) -> Unit,
) {
    private val random = Random(RANDOM_SEED)
    private val backlog = ArrayDeque<FineCase>()
    private val hires = ArrayDeque<Double>()
    var staff = rules.initialStaff
        private set
    var strictness = Strictness.NORMAL
    var policy = UnidentifiedPolicy.DROP
    var deadlineDays = rules.defaultDeadlineDays
        set(value) {
            field = value.coerceIn(1, rules.legalMaxDeadlineDays)
        }

    /** Zähler seit Spielbeginn und für den laufenden Tag (für die Zeitung). */
    val totals = Counters()
    var today = Counters()
        private set

    class Counters {
        var processed = 0
        var paid = 0
        var expired = 0
        var dropped = 0
    }

    /** Aufklärungsquote: mehr Personal recherchiert gründlicher – mit abnehmendem Nutzen. */
    val identificationRate: Double
        get() =
            rules.identificationBase +
                (rules.identificationMax - rules.identificationBase) *
                (1 - exp(-staff / rules.identificationStaffScale))

    fun register(
        tier: FineTier,
        homeZone: Int,
        person: Int,
        time: Double,
    ) {
        backlog.addLast(FineCase(time, tier, homeZone, person))
    }

    fun hire(
        count: Int,
        time: Double,
    ) {
        repeat(count) { hires.addLast(time + rules.hireDelayHours * SECONDS_PER_HOUR) }
        message("$count neue Sachbearbeiter eingestellt – Arbeitsbeginn in ${rules.hireDelayHours} Stunden")
    }

    fun fire(count: Int) {
        staff = (staff - count).coerceAtLeast(0)
        message("Personal der Bußgeldstelle: $staff")
    }

    /** Eine Spielstunde Büroarbeit. */
    fun hourPassed(
        time: Double,
        clock: GameTime,
    ) {
        while (hires.isNotEmpty() && hires.first() <= time) {
            hires.removeFirst()
            staff++
        }
        val officeHours = clock.weekday.isWorkday && clock.hour in rules.officeStartHour until rules.officeEndHour
        if (!officeHours) return
        repeat(staff * rules.casesPerStaffHour) {
            val case = backlog.removeFirstOrNull() ?: return
            process(case, time)
        }
    }

    fun weeklySalaries(): Long = staff * rules.staffWeeklySalary

    fun startNewDay(): Counters = today.also { today = Counters() }

    fun state(time: Double) =
        FineOfficeState(
            staff = staff,
            hiring = hires.size,
            backlog = backlog.size,
            oldestCaseHours = backlog.firstOrNull()?.let { (time - it.createdAt) / SECONDS_PER_HOUR } ?: 0.0,
            identificationRate = identificationRate,
            processed = totals.processed,
            paid = totals.paid,
            expired = totals.expired,
            dropped = totals.dropped,
            strictness = strictness,
            policy = policy,
            deadlineDays = deadlineDays,
        )

    private fun process(
        case: FineCase,
        time: Double,
    ) {
        count { processed++ }
        if (time - case.createdAt > deadlineDays * SECONDS_PER_DAY) return count { expired++ }
        val identified = random.nextDouble() < identificationRate
        val payShare =
            when {
                identified -> 1.0
                policy == UnidentifiedPolicy.QUESTIONNAIRE -> rules.questionnairePayShare
                policy == UnidentifiedPolicy.LOGBOOK -> rules.logbookPayShare
                else -> 0.0
            }
        val appealed = random.nextDouble() < strictness.appealSuccess
        if (appealed || random.nextDouble() >= payShare) return count { dropped++ }

        count { paid++ }
        treasury.earnFine(case.tier.fine)
        val logbookAnger = if (!identified && policy == UnidentifiedPolicy.LOGBOOK) rules.logbookPainFactor else 1.0
        citizens.fined(case.homeZone, (case.tier.fine * strictness.painFactor * logbookAnger).toInt())
        drivers.penalize(case.person, case.homeZone, case.tier, time)?.let(message)
    }

    private inline fun count(update: Counters.() -> Unit) {
        totals.update()
        today.update()
    }

    private companion object {
        const val RANDOM_SEED = 4711
        const val SECONDS_PER_HOUR = 3_600.0
        const val SECONDS_PER_DAY = 86_400.0
    }
}
