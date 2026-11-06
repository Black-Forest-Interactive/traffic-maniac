package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Facility
import de.bfi.trafficmaniac.city.FacilityKind
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Zustand einer Ladesäule für Oberfläche und Spiel. */
class ChargingStationState(
    val id: Int,
    val x: Float,
    val y: Float,
    val capacity: Int,
    val charging: Int,
    val waiting: Int,
)

internal class ChargingStation(
    val id: Int,
    val facility: Facility,
) {
    /** Ladende Fahrzeuge mit dem Zeitpunkt, an dem sie fertig sind. */
    val charging = ArrayList<Pair<Vehicle, Double>>()

    /** Wartende Fahrzeuge – zuerst auf dem Parkplatz, ist der voll, am Ende der Zufahrt auf der Straße. */
    val waiting = ArrayDeque<Pair<Vehicle, Double>>()

    val hasFreePlug: Boolean get() = charging.size < facility.capacity

    /** Parkplätze zum Warten: drei je Ladepunkt. */
    val hasFreeLot: Boolean get() = waiting.count { !it.first.holdAtEnd } < facility.capacity * LOT_PER_PLUG

    /** Auslastung inklusive Warteschlange (für die Wahl der Ladesäule). */
    val load: Double get() = (charging.size + waiting.size).toDouble() / facility.capacity

    private companion object {
        const val LOT_PER_PLUG = 3
    }
}

/**
 * Elektroautos und Ladesäulen: Der Akku leert sich beim Fahren, im Stand (Heizung, Klima) und lädt beim Bremsen
 * etwas nach. Wer zu Fahrtbeginn wenig Ladung hat, fährt zuerst zur nächsten Ladesäule. Sind alle Ladepunkte belegt,
 * wartet man – auf der Straße. Wer unterwegs leer wird, bleibt liegen.
 */
internal class Charging(
    private val config: SimulationConfig,
    facilities: List<Facility>,
    private val router: Router,
    private val graph: RoadGraph,
    private val lanes: LaneQueues,
    private val incidents: () -> Incidents,
    private val spawn: (Vehicle) -> Unit,
    private val complete: (Vehicle) -> Unit,
    private val statistics: DayStatistics,
) {
    val stations =
        facilities.filter { it.kind == FacilityKind.CHARGING_STATION }.mapIndexed {
            i,
            f,
            ->
            ChargingStation(i, f)
        }

    /** Elektroauto mit wenig Ladung: Route erst zur nächsten Ladesäule. */
    fun planTrip(vehicle: Vehicle) {
        if (vehicle.drivetrain != Drivetrain.ELECTRIC || stations.isEmpty()) return
        if (vehicle.batteryKwh > config.batteryKwh * config.chargeBelowShare) return
        val origin = graph.linkFrom[vehicle.route.first()]
        // Das Navi wählt unter den drei nächsten Ladesäulen die am wenigsten belegte
        val station =
            stations
                .sortedBy { hypot(it.facility.x - graph.nodeX[origin], it.facility.y - graph.nodeY[origin]) }
                .take(NEAREST_CANDIDATES)
                .minBy { it.load }
        val route = if (station.facility.node == origin) null else router.route(origin, station.facility.node)
        if (route == null) return
        vehicle.chargingStation = station.id
        vehicle.finalDestination = vehicle.destinationNode
        vehicle.destinationNode = station.facility.node
        vehicle.route = route
        vehicle.routeIndex = 0
    }

    /** Verbrauch eines Zeitschritts; leerer Akku → Panne. */
    fun consume(
        vehicle: Vehicle,
        dt: Double,
        time: Double,
    ) {
        if (vehicle.drivetrain != Drivetrain.ELECTRIC || vehicle.disabled) return
        val v = vehicle.speed
        val power = vehicle.type.massKg * (vehicle.acceleration * v + GRAVITY * ROLLING * v) + DRAG * v.pow(3)
        val drive = if (power > 0) power / MOTOR_EFFICIENCY else power * RECUPERATION
        vehicle.batteryKwh -= (drive + AUXILIARY_WATTS) * dt / JOULES_PER_KWH
        if (vehicle.batteryKwh <= 0) {
            vehicle.batteryKwh = 0.0
            incidents().breakdown(vehicle, IncidentKind.EMPTY_BATTERY, time)
        }
    }

    /**
     * Ankunft an der Ladesäule. `true`: Das Auto verlässt die Straße zum Laden.
     * `false`: Alle Ladepunkte belegt – es wartet am Ende der Zufahrt.
     */
    fun arrive(
        vehicle: Vehicle,
        time: Double,
    ): Boolean {
        val station = stations[vehicle.chargingStation]
        if (station.hasFreePlug && station.waiting.isEmpty()) {
            startCharging(station, vehicle, time)
            return true
        }
        // Erst auf den Parkplatz der Ladesäule, ist der voll, wartet man auf der Straße
        val offRoad = station.hasFreeLot
        if (offRoad) vehicle.active = false else vehicle.holdAtEnd = true
        station.waiting.addLast(vehicle to time)
        return offRoad
    }

    /** Fertig geladene Autos fahren weiter; frei gewordene Ladepunkte gehen an die Wartenden. */
    fun update(time: Double) {
        for (station in stations) {
            val done = station.charging.filter { it.second <= time }
            for ((vehicle, _) in done) {
                station.charging.removeAll { it.first === vehicle }
                continueTrip(station, vehicle)
            }
            while (station.hasFreePlug && station.waiting.isNotEmpty()) {
                val (vehicle, since) = station.waiting.removeFirst()
                if (vehicle.disabled) continue
                if (vehicle.holdAtEnd) lanes[vehicle.link][vehicle.lane].remove(vehicle)
                vehicle.holdAtEnd = false
                statistics.chargingWait(time - since)
                startCharging(station, vehicle, time)
            }
        }
    }

    fun states(): List<ChargingStationState> =
        stations.map {
            ChargingStationState(
                it.id,
                it.facility.x,
                it.facility.y,
                it.facility.capacity,
                it.charging.size,
                it.waiting.size,
            )
        }

    private fun startCharging(
        station: ChargingStation,
        vehicle: Vehicle,
        time: Double,
    ) {
        vehicle.active = false
        vehicle.charging = true
        val missing = max(0.0, config.batteryKwh * CHARGE_TARGET - vehicle.batteryKwh) / config.batteryKwh
        val duration = config.chargeMinutes * SECONDS_PER_MINUTE * min(1.0, missing / CHARGE_TARGET + MIN_SESSION)
        vehicle.chargingSeconds += duration
        station.charging += vehicle to time + duration
    }

    private fun continueTrip(
        station: ChargingStation,
        vehicle: Vehicle,
    ) {
        vehicle.charging = false
        vehicle.batteryKwh = config.batteryKwh * CHARGE_TARGET
        vehicle.chargingStation = -1
        vehicle.destinationNode = vehicle.finalDestination
        val route = router.route(station.facility.node, vehicle.destinationNode)
        if (route == null) {
            complete(vehicle)
            return
        }
        vehicle.route = route
        vehicle.routeIndex = 0
        spawn(vehicle)
    }

    private companion object {
        const val GRAVITY = 9.81
        const val ROLLING = 0.012
        const val DRAG = 0.4
        const val MOTOR_EFFICIENCY = 0.85
        const val RECUPERATION = 0.6
        const val AUXILIARY_WATTS = 1_500.0
        const val JOULES_PER_KWH = 3_600_000.0
        const val CHARGE_TARGET = 0.8
        const val MIN_SESSION = 0.2
        const val SECONDS_PER_MINUTE = 60.0
        const val NEAREST_CANDIDATES = 3
    }
}
