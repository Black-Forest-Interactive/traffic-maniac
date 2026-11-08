package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.ParkingViolation
import de.bfi.trafficmaniac.sim.ParkingViolationKind
import de.bfi.trafficmaniac.sim.Simulation
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.random.Random

/** Parkraum und Politessen für die Oberfläche. */
data class ParkingOfficeState(
    val wardens: Int = 0,
    val hiring: Int = 0,
    val tickets: Int = 0,
    val ticketIncome: Long = 0,
    val feeIncome: Long = 0,
    val illegalParkers: Int = 0,
    /** Straßenrand-Parkplätze, die in Radspuren und Blumenkübel umgewandelt wurden (in Schritten). */
    val curbConversions: Int = 0,
    val residentParking: Boolean = false,
    val breadButton: Boolean = true,
)

/** Zähler eines Tages (für die Zeitung). */
internal class ParkingNews {
    var tickets = 0
    var ticketIncome = 0L
    var illegal = 0
    var secondRow = 0
}

/**
 * Parkraumbewirtschaftung und Politessen. Gebühren fließen direkt in die Kasse; Falschparker und Lieferwagen in
 * zweiter Reihe bekommen ein Knöllchen – wenn eine Politesse vorbeikommt. Mehr Politessen erwischen mehr, aber mit
 * abnehmendem Nutzen und höchstens [GameRules.ticketsPerWardenHour] je Politesse und Stunde. Die Schärfe des
 * Ordnungsamts legt fest, ab welcher Minute es ein Knöllchen gibt. Politessen sind nur tagsüber unterwegs.
 */
internal class ParkingOffice(
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val citizens: Citizens,
    private val message: (String) -> Unit,
) {
    private val random = Random(RANDOM_SEED)
    private val hires = ArrayDeque<Double>()
    var wardens = rules.initialWardens
        private set
    private var tickets = 0
    private var illegal = 0
    private var curbConversions = 0
    var residentParking = false
        private set
    var today = ParkingNews()
        private set

    /** Gebühren, Stellplätze und Brötchentaste. */
    val pricing = ParkingPricing(simulation, message)

    fun state() =
        ParkingOfficeState(
            wardens = wardens,
            hiring = hires.size,
            tickets = tickets,
            ticketIncome = treasury.ticketIncome,
            feeIncome = treasury.parkingIncome,
            illegalParkers = illegal,
            curbConversions = curbConversions,
            residentParking = residentParking,
            breadButton = simulation.parking.breadButton,
        )

    fun startNewDay(): ParkingNews = today.also { today = ParkingNews() }

    fun hire(
        count: Int,
        time: Double,
    ) {
        repeat(count) { hires.addLast(time + rules.wardenHireDelayHours * SECONDS_PER_HOUR) }
        message(
            tr(
                "{count} Politessen eingestellt – im Dienst in {wardenHireDelayHours} Stunden",
                "count" to count,
                "wardenHireDelayHours" to rules.wardenHireDelayHours,
            ),
        )
    }

    fun fire(count: Int) {
        wardens = (wardens - count).coerceAtLeast(0)
        message(tr("Politessen: {wardens}", "wardens" to wardens))
    }

    fun weeklySalaries(): Long = wardens * rules.wardenWeeklySalary

    /** Einnahmen aus Anwohnerparkausweisen pro Woche. */
    fun weeklyPermits(): Long = if (residentParking) rules.residentPermitWeekly else 0L

    /** Straßenrand-Parkplätze in Radspuren und Blumenkübel umwandeln (ein Schritt). */
    fun convertCurb() {
        if (curbShare() <= 0.0) return message(tr("Es gibt keine Parkplätze am Straßenrand mehr."))
        if (!treasury.pay(rules.curbConversionCost, tr("Radspuren und Blumenkübel"))) return
        curbConversions++
        applyCurb()
        message(
            tr(
                "Parkplätze weg, Blumenkübel da: noch {curbShare} % am Straßenrand",
                "curbShare" to (curbShare() * PERCENT).toInt(),
            ),
        )
    }

    fun setResidentParking(enabled: Boolean) {
        residentParking = enabled
        applyCurb()
        message(
            if (enabled) {
                tr(
                    "Anwohnerparken ausgeweitet – Besucher bleiben draußen",
                )
            } else {
                tr("Anwohnerparken aufgehoben")
            },
        )
    }

    /** Jede Stunde: Gebühren einnehmen, Knöllchen verteilen, neue Politessen antreten lassen. */
    fun hourPassed(
        time: Double,
        clock: GameTime,
        strictness: Strictness,
    ) {
        while (hires.isNotEmpty() && hires.first() <= time) {
            hires.removeFirst()
            wardens++
        }
        collectFees()
        issueTickets(onDuty = clock.hour in rules.wardenStartHour until rules.wardenEndHour, strictness)
    }

    private fun collectFees() {
        var fees = 0.0
        for (payment in simulation.parking.drainPayments()) {
            fees += payment.euros
            citizens.fined(payment.homeZone, (payment.euros * rules.parkingFeePainFactor).toInt())
        }
        treasury.earnParking(fees.roundToLong())
    }

    private fun issueTickets(
        onDuty: Boolean,
        strictness: Strictness,
    ) {
        var capacity = wardens * rules.ticketsPerWardenHour
        for (violation in simulation.parking.drainViolations()) {
            if (violation.kind == ParkingViolationKind.ILLEGAL) {
                illegal++
                today.illegal++
            } else {
                today.secondRow++
            }
            if (!onDuty || capacity <= 0 || !caught(violation, strictness)) continue
            capacity--
            val fine = fineFor(violation, strictness)
            if (fine > 0) {
                treasury.earnTicket(fine)
                citizens.fined(violation.homeZone, (fine * strictness.painFactor).toInt())
                tickets++
                today.tickets++
                today.ticketIncome += fine
            }
        }
    }

    /** Erst nach der Kulanzzeit gibt es ein Knöllchen – "gnadenlos" ab der ersten Minute. */
    private fun caught(
        violation: ParkingViolation,
        strictness: Strictness,
    ): Boolean {
        val chargeable = violation.minutes - strictness.parkingGraceMinutes
        return chargeable > 0 && random.nextDouble() < 1 - exp(-wardens * chargeable / rules.wardenCoverageMinutes)
    }

    /** Bezahltes Knöllchen in Euro; 0 nach erfolgreichem Einspruch oder wenn niemand zahlt. */
    private fun fineFor(
        violation: ParkingViolation,
        strictness: Strictness,
    ): Int {
        val appealed = random.nextDouble() < strictness.appealSuccess
        val paid = random.nextDouble() < rules.ticketPayShare
        if (appealed || !paid) return 0
        return if (violation.kind == ParkingViolationKind.ILLEGAL) rules.illegalParkingTicket else rules.secondRowTicket
    }

    private fun curbShare(): Double = (1.0 - curbConversions * rules.curbConversionShare).coerceAtLeast(0.0)

    private fun applyCurb() {
        simulation.parking.curbShare = curbShare() * if (residentParking) 1 - rules.residentParkingCurbShare else 1.0
    }

    private companion object {
        const val RANDOM_SEED = 1312
        const val SECONDS_PER_HOUR = 3_600.0
        const val PERCENT = 100
    }
}

/** Parkgebühren (Parkplätze und Straßenrand), Stellplätze und Brötchentaste. */
internal class ParkingPricing(
    private val simulation: Simulation,
    private val message: (String) -> Unit,
) {
    /** Gebühr für einen Parkplatz oder (`null`) alle. */
    fun setFee(
        lot: Int?,
        euros: Double,
    ) {
        val lots = if (lot == null) 0 until simulation.parking.lotCount else lot..lot
        lots.forEach { simulation.parking.setLotFee(it, euros) }
        val where = if (lot == null) tr("alle Parkplätze") else lotName(lot)
        message(tr("Parkgebühr {euros} €/Std.: {where}", "euros" to formatDecimal(euros, 2), "where" to where))
    }

    /** Gebühr am Parkscheinautomaten (Straßenrand in Gewerbe- und Einkaufsgebieten). */
    fun setCurbFee(euros: Double) {
        simulation.parking.curbFee = euros
        message(tr("Parkscheinautomat am Straßenrand: {euros} €/Std.", "euros" to formatDecimal(euros, 2)))
    }

    fun setCapacity(
        lot: Int,
        capacity: Int,
    ) {
        simulation.parking.setLotCapacity(lot, capacity)
        message(
            if (capacity <=
                0
            ) {
                tr("Parkplatz abgeschafft: {lot}", "lot" to lotName(lot))
            } else {
                tr("{lot}: {capacity} Stellplätze", "lot" to lotName(lot), "capacity" to capacity)
            },
        )
    }

    fun setBreadButton(enabled: Boolean) {
        simulation.parking.breadButton = enabled
        message(if (enabled) tr("Brötchentaste wieder da: 30 Minuten gratis") else tr("Brötchentaste abgeschafft"))
    }

    private fun lotName(lot: Int): String = simulation.parking.lotName(lot) ?: tr("Parkplatz {lot}", "lot" to lot + 1)
}
