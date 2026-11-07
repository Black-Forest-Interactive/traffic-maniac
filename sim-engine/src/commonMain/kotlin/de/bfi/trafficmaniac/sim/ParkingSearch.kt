package de.bfi.trafficmaniac.sim

import kotlin.math.max
import kotlin.random.Random

/**
 * Was bei der Ankunft am Ziel passiert – der Reihe nach: angesteuerten Parkplatz nehmen, Straßenrand der Zielzone,
 * Parkplatz in Laufweite (wenn nicht zu teuer), eine Runde um den Block, und wenn alles nichts hilft: "nur kurz"
 * falsch parken.
 */
internal class ParkingSearch(
    private val parking: Parking,
    private val config: SimulationConfig,
    private val graph: RoadGraph,
    private val router: Router,
    private val random: Random,
    private val statistics: DayStatistics,
) {
    /** Fahrzeuge auf Parkplatzsuche. */
    private val searching = HashSet<Vehicle>()

    val searchingCount: Int get() = searching.size

    /** Wer aufgegeben hat oder liegengeblieben ist, sucht nicht mehr. */
    fun forgetInactive() {
        searching.removeAll { !it.active || it.disabled }
    }

    /** Ankunft am Ende von [link]: parken, weitersuchen (neue Route ab [link]) oder falsch parken. */
    fun arrive(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ): ParkingOutcome {
        if (vehicle.parkingSearchStart.isNaN()) vehicle.parkingSearchStart = time
        val stay = stayHours(vehicle)
        return targetLot(vehicle, link, stay, time)
            ?: curb(vehicle, stay, time)
            ?: nearbyLot(vehicle, link, stay, time)
            ?: circle(vehicle, link, time)
            ?: illegal(vehicle, stay, time)
    }

    /** Der angesteuerte Parkplatz – wenn er noch frei ist. */
    private fun targetLot(
        vehicle: Vehicle,
        link: Int,
        stay: Double,
        time: Double,
    ): ParkingOutcome? {
        val target = vehicle.parkingLot.takeIf { it >= 0 }?.let { parking.lots[it] }
        vehicle.parkingLot = -1
        if (target == null || target.facility.node != graph.linkTo[link] || !target.hasSpace) return null
        return park(vehicle, target, stay, time)
    }

    /** Straßenrand in der Zielzone – wenn der Parkschein nicht zu teuer ist. */
    private fun curb(
        vehicle: Vehicle,
        stay: Double,
        time: Double,
    ): ParkingOutcome? {
        val fee = parking.curbFee * payableHours(vehicle, stay)
        val affordable = fee / stay <= vehicle.feeTolerance
        if (!affordable || !parking.takeCurb(vehicle.parkingZone, time + stay * SECONDS_PER_HOUR)) return null
        pay(vehicle, fee)
        statistics.parked(curb = true)
        return parked(vehicle, time)
    }

    /** Parkplatz in Laufweite, der nicht zu teuer ist: sofort parken oder hinfahren. */
    private fun nearbyLot(
        vehicle: Vehicle,
        link: Int,
        stay: Double,
        time: Double,
    ): ParkingOutcome? {
        val lot =
            parking.lotsNear(vehicle.parkingZone).firstOrNull {
                it.hasSpace && it.fee * payableHours(vehicle, stay) / stay <= vehicle.feeTolerance
            } ?: return null
        return when {
            lot.facility.node == graph.linkTo[link] -> {
                park(vehicle, lot, stay, time)
            }

            driveTo(vehicle, link, lot.facility.node) -> {
                vehicle.parkingLot = lot.id
                searching(vehicle)
            }

            else -> {
                null
            }
        }
    }

    /** Eine Runde um den Block – solange Geduld und Runden reichen. */
    private fun circle(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ): ParkingOutcome? {
        vehicle.parkingAttempts++
        val searchedTooLong = time - vehicle.parkingSearchStart > config.maxParkingSearchMinutes * SECONDS_PER_MINUTE
        val candidates = parking.searchNodes(vehicle.parkingZone)
        if (vehicle.parkingAttempts > config.maxParkingAttempts || searchedTooLong || candidates.isEmpty()) return null
        val next = candidates[random.nextInt(candidates.size)]
        return if (next != graph.linkTo[link] && driveTo(vehicle, link, next)) searching(vehicle) else null
    }

    /** Aufgegeben: "nur kurz" irgendwo hingestellt. */
    private fun illegal(
        vehicle: Vehicle,
        stay: Double,
        time: Double,
    ): ParkingOutcome {
        parking.violations += ParkingViolation(ParkingViolationKind.ILLEGAL, vehicle.homeZone, stay * MINUTES_PER_HOUR)
        statistics.illegallyParked()
        finishSearch(vehicle, time)
        return ParkingOutcome.ILLEGAL
    }

    private fun park(
        vehicle: Vehicle,
        lot: ParkingLot,
        stay: Double,
        time: Double,
    ): ParkingOutcome {
        lot.departures += time + stay * SECONDS_PER_HOUR
        pay(vehicle, lot.fee * payableHours(vehicle, stay))
        statistics.parked(curb = false)
        return parked(vehicle, time)
    }

    private fun pay(
        vehicle: Vehicle,
        euros: Double,
    ) {
        if (euros <= 0) return
        parking.payments += ParkingPayment(vehicle.homeZone, euros)
        statistics.parkingFee(euros)
    }

    private fun parked(
        vehicle: Vehicle,
        time: Double,
    ): ParkingOutcome {
        finishSearch(vehicle, time)
        return ParkingOutcome.PARKED
    }

    private fun searching(vehicle: Vehicle): ParkingOutcome {
        searching += vehicle
        return ParkingOutcome.SEARCHING
    }

    private fun finishSearch(
        vehicle: Vehicle,
        time: Double,
    ) {
        if (searching.remove(vehicle)) statistics.parkingSearch(time - vehicle.parkingSearchStart)
        vehicle.parkingSearchStart = Double.NaN
    }

    /** Neue Route vom Ende von [link] nach [node]; `false`, wenn es keine gibt. */
    private fun driveTo(
        vehicle: Vehicle,
        link: Int,
        node: Int,
    ): Boolean {
        val path = router.route(graph.linkTo[link], node, weightTons = vehicle.routingTons) ?: return false
        vehicle.route = intArrayOf(link) + path
        vehicle.routeIndex = 0
        vehicle.destinationNode = node
        return true
    }

    /** Mit Brötchentaste ist bei Einkäufen die erste halbe Stunde kostenlos. */
    private fun payableHours(
        vehicle: Vehicle,
        stay: Double,
    ): Double = max(0.0, stay - if (parking.breadButton && vehicle.shortStay) BREAD_BUTTON_HOURS else 0.0)

    /** Aufenthalt am Ziel: Einkauf oder Arbeitstag, jeweils ±25 %. */
    private fun stayHours(vehicle: Vehicle): Double =
        (if (vehicle.shortStay) config.shortStayHours else config.workStayHours) *
            random.nextDouble(1 - STAY_SPREAD, 1 + STAY_SPREAD)

    private companion object {
        const val SECONDS_PER_HOUR = 3_600.0
        const val SECONDS_PER_MINUTE = 60.0
        const val MINUTES_PER_HOUR = 60.0
        const val BREAD_BUTTON_HOURS = 0.5
        const val STAY_SPREAD = 0.25
    }
}
