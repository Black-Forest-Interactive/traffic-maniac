package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Facility
import de.bfi.trafficmaniac.city.FacilityKind
import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/** Zustand eines Parkplatzes bzw. Parkhauses für Oberfläche und Spiel. */
class ParkingLotState(
    val id: Int,
    val name: String?,
    val x: Float,
    val y: Float,
    /** Stellplätze laut OSM. */
    val baseCapacity: Int,
    /** Vom Spieler festgelegte Stellplätze (0 = abgeschafft). */
    val capacity: Int,
    val occupied: Int,
    /** Gebühr in Euro pro Stunde. */
    val feePerHour: Double,
)

/** Parken in der ganzen Stadt auf einen Blick. */
data class ParkingSummary(
    val lotCapacity: Int = 0,
    val lotOccupied: Int = 0,
    val curbCapacity: Int = 0,
    val curbOccupied: Int = 0,
    /** Fahrzeuge, die gerade einen Parkplatz suchen. */
    val searching: Int = 0,
    /** Anteil der Straßenrand-Parkplätze, die der Spieler noch übrig gelassen hat (1 = alle). */
    val curbShare: Double = 1.0,
    /** Brötchentaste: die erste halbe Stunde ist kostenlos. */
    val breadButton: Boolean = true,
    /** Parkscheinautomat am Straßenrand (Euro je Stunde). */
    val curbFee: Double = 0.0,
)

enum class ParkingViolationKind(
    val label: String,
) {
    /** Nach langer Suche "nur kurz" irgendwo abgestellt. */
    ILLEGAL("Falschparker"),

    /** Lieferwagen in zweiter Reihe. */
    SECOND_ROW("zweite Reihe"),
}

/** Ein Parkverstoß – ob er ein Knöllchen gibt, entscheiden die Politessen im Spiel. */
class ParkingViolation(
    val kind: ParkingViolationKind,
    val homeZone: Int,
    val minutes: Double,
)

/** Parkgebühr, die ein Fahrer bezahlt hat (Wohngebiet für den Ärger, -1 bei Auswärtigen). */
class ParkingPayment(
    val homeZone: Int,
    val euros: Double,
)

/** Ergebnis einer Ankunft am Ziel. */
internal enum class ParkingOutcome {
    PARKED,
    SEARCHING,
    ILLEGAL,
}

internal class ParkingLot(
    val id: Int,
    val facility: Facility,
) {
    var capacity = facility.capacity
    var fee = 0.0

    /** Abfahrtszeiten der geparkten Autos. */
    val departures = ArrayList<Double>()
    val hasSpace: Boolean get() = departures.size < capacity
}

/**
 * Parken am Ziel: Wer zur Arbeit oder zum Einkaufen fährt, braucht einen Stellplatz – erst am Straßenrand der Zielzone
 * (kostenlos, aber knapp), dann auf einem der Parkplätze aus OSM in Laufweite (gegen Gebühr). Ist alles voll oder zu
 * teuer, dreht der Fahrer Runden um den Block (**Parksuchverkehr**) und stellt sich nach ein paar Runden "nur kurz"
 * irgendwo hin (**Falschparker**). Nach Hause und an den Stadtrand muss niemand einen Parkplatz suchen.
 */
class Parking internal constructor(
    private val config: SimulationConfig,
    facilities: List<Facility>,
    zones: List<Zone>,
    private val graph: RoadGraph,
    private val router: Router,
    private val random: Random,
    private val statistics: DayStatistics,
) {
    internal val lots =
        facilities.filter { it.kind == FacilityKind.PARKING }.mapIndexed { i, facility ->
            ParkingLot(i, facility).also { it.fee = if (facility.paid) config.defaultParkingFee else 0.0 }
        }
    private val zonesById = zones.associateBy { it.id }
    private val curbBase =
        zones.associate { zone ->
            val parks = zone.kind != ZoneKind.RESIDENTIAL && zone.kind != ZoneKind.GATE
            zone.id to if (parks) (zone.area / config.curbSquareMetersPerSpace).toInt().coerceAtLeast(MIN_CURB) else 0
        }
    private val curbDepartures = HashMap<Int, ArrayList<Double>>()

    /** Parkplätze in Laufweite je Zone (nächste zuerst). */
    private val lotsNear = HashMap<Int, List<ParkingLot>>()

    /** Knoten im Umkreis einer Zone, zu denen Parkplatzsucher kreisen. */
    private val searchNodes = HashMap<Int, IntArray>()
    private val activeNodes =
        (0 until graph.nodeCount).filter { node ->
            graph.outLinks[node].any { !graph.linkPassive[it] }
        }
    internal val violations = ArrayList<ParkingViolation>()
    internal val payments = ArrayList<ParkingPayment>()
    private val search = ParkingSearch(this, config, graph, router, random, statistics)

    /** Anteil der Straßenrand-Parkplätze (Radspuren und Blumenkübel nehmen welche weg, Anwohnerparken auch). */
    var curbShare = 1.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    /** Brötchentaste: die erste halbe Stunde parkt man kostenlos. */
    var breadButton = true

    /** Parkscheinautomat am Straßenrand in Gewerbe- und Einkaufsgebieten (Euro je Stunde). */
    var curbFee = config.defaultCurbFee
        set(value) {
            field = value.coerceIn(0.0, MAX_FEE)
        }

    val lotCount: Int get() = lots.size

    fun lotName(lot: Int): String? = lots[lot].facility.name

    fun setLotCapacity(
        lot: Int,
        capacity: Int,
    ) {
        lots[lot].capacity = capacity.coerceIn(0, MAX_LOT_CAPACITY)
    }

    fun setLotFee(
        lot: Int,
        euros: Double,
    ) {
        lots[lot].fee = euros.coerceIn(0.0, MAX_FEE)
    }

    fun states(): List<ParkingLotState> =
        lots.map {
            ParkingLotState(
                it.id,
                it.facility.name,
                it.facility.x,
                it.facility.y,
                it.facility.capacity,
                it.capacity,
                it.departures.size,
                it.fee,
            )
        }

    fun summary(): ParkingSummary =
        ParkingSummary(
            lotCapacity = lots.sumOf { it.capacity },
            lotOccupied = lots.sumOf { it.departures.size },
            curbCapacity = curbBase.keys.sumOf { curbCapacity(it) },
            curbOccupied = curbDepartures.values.sumOf { it.size },
            searching = search.searchingCount,
            curbShare = curbShare,
            breadButton = breadButton,
            curbFee = curbFee,
        )

    fun drainViolations(): List<ParkingViolation> = violations.toList().also { violations.clear() }

    fun drainPayments(): List<ParkingPayment> = payments.toList().also { payments.clear() }

    /** Fahrtbeginn: Muss am Ziel geparkt werden, und wie viel darf es kosten? */
    internal fun planTrip(
        vehicle: Vehicle,
        trip: Trip,
    ) {
        if (vehicle.role != VehicleRole.PRIVATE || trip.parkingZone < 0) return
        vehicle.parkingZone = trip.parkingZone
        vehicle.shortStay = trip.shortStay
        vehicle.feeTolerance = random.nextDouble(0.0, config.maxFeeTolerance)
    }

    /** Lieferwagen in zweiter Reihe – auch das ist Falschparken. */
    internal fun secondRowStop(minutes: Double) {
        violations += ParkingViolation(ParkingViolationKind.SECOND_ROW, -1, minutes)
    }

    /** Ankunft am Ende von [link]: parken, weitersuchen oder falsch parken (siehe [ParkingSearch]). */
    internal fun arrive(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ): ParkingOutcome = search.arrive(vehicle, link, time)

    /** Abgelaufene Parkzeiten freigeben; wer aufgegeben hat oder liegengeblieben ist, sucht nicht mehr. */
    internal fun update(time: Double) {
        search.forgetInactive()
        for (lot in lots) lot.departures.removeAll { it <= time }
        for (list in curbDepartures.values) list.removeAll { it <= time }
    }

    /** Belegt einen Platz am Straßenrand der Zone bis [until]; `false`, wenn alles voll ist. */
    internal fun takeCurb(
        zone: Int,
        until: Double,
    ): Boolean {
        val taken = curbDepartures.getOrPut(zone) { ArrayList() }
        if (taken.size >= curbCapacity(zone)) return false
        taken += until
        return true
    }

    private fun curbCapacity(zone: Int): Int = ((curbBase[zone] ?: 0) * curbShare).toInt()

    internal fun lotsNear(zone: Int): List<ParkingLot> =
        lotsNear.getOrPut(zone) {
            val z = zonesById[zone] ?: return@getOrPut emptyList()
            lots
                .map { it to hypot(it.facility.x - z.x, it.facility.y - z.y) }
                .filter { it.second <= config.parkingWalkMeters }
                .sortedBy { it.second }
                .map { it.first }
        }

    internal fun searchNodes(zone: Int): IntArray =
        searchNodes.getOrPut(zone) {
            val z = zonesById[zone] ?: return@getOrPut IntArray(0)
            activeNodes
                .filter { hypot(graph.nodeX[it] - z.x, graph.nodeY[it] - z.y) <= config.parkingSearchMeters }
                .toIntArray()
        }

    private companion object {
        const val MIN_CURB = 5
        const val MAX_LOT_CAPACITY = 5_000
        const val MAX_FEE = 50.0
    }
}
