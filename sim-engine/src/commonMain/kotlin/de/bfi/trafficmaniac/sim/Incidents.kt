package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Facility
import de.bfi.trafficmaniac.city.FacilityKind
import kotlin.math.hypot
import kotlin.random.Random

enum class IncidentKind(
    val label: String,
) {
    SOFTWARE_FAILURE("Softwarepanne"),
    EMPTY_BATTERY("Akku leer"),
    OIL_SPILL("Ölspur"),
    ACCIDENT("Unfall"),
    TRUCK_STUCK("LKW eingeklemmt"),
}

/** Ein Vorfall auf der Straße und die Einsatzkräfte, die dafür ausrücken. */
class Incident internal constructor(
    val id: Int,
    val kind: IncidentKind,
    val link: Int,
    val position: Double,
    val x: Float,
    val y: Float,
    val startTime: Double,
) {
    internal val vehicles = ArrayList<Vehicle>()
    internal val responders = ArrayList<Vehicle>()
    internal val arrived = HashSet<VehicleRole>()
    internal var workUntil = Double.NaN

    /** Anfahrtszeit des Rettungswagens in Sekunden (nur bei Unfällen, sobald er da ist). */
    var ambulanceResponseSeconds: Double? = null
        internal set
}

/**
 * Pannen, Ölspuren und Unfälle – und wer sie wieder beseitigt.
 *
 * - Moderne Autos haben Softwarepannen (im Stau häufiger), alte verlieren Öl,
 *   Elektroautos bleiben mit leerem Akku liegen.
 * - Unfälle passieren bei Vollbremsungen, umso eher, je unaufmerksamer und gereizter der Fahrer ist.
 * - Abschleppdienst und Straßenmeisterei rücken von der Feuerwache aus, Rettungswagen und Polizei mit Blaulicht
 *   von ihren Wachen. Sie stehen selbst im Stau – blockierte Spuren bleiben blockiert, bis sie da sind.
 */
internal class Incidents(
    private val graph: RoadGraph,
    private val config: SimulationConfig,
    private val random: Random,
    private val router: Router,
    private val factory: VehicleFactory,
    private val lanes: LaneQueues,
    facilities: List<Facility>,
    private val spawn: (Vehicle) -> Unit,
    private val statistics: DayStatistics,
    private val infrastructure: Infrastructure,
) {
    private val depots = facilities.groupBy { it.kind }
    private val incidents = ArrayList<Incident>()
    private var nextId = 0

    val active: List<Incident> get() = incidents

    fun isOpen(id: Int): Boolean = incidents.any { it.id == id }

    /** Nach jeder Bewegung: Panne, Ölverlust oder Unfall auswürfeln. */
    fun afterMove(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ) {
        if (vehicle.role != VehicleRole.PRIVATE || vehicle.disabled || vehicle.holdAtEnd) return
        if (rollTechnicalProblems(vehicle, link, time)) return
        val hazard = infrastructure.hazard(link)
        // Unfälle passieren bei Vollbremsungen aus voller Fahrt, nicht beim Rangieren im Stau
        if (vehicle.acceleration < HARD_BRAKING && vehicle.speed > CRASH_SPEED) {
            val inattention = (1 - vehicle.attention) * (1 + vehicle.irritation)
            val risk = config.accidentRiskPerHardBrakingSecond * config.ticksPerSecondInverse() * inattention * hazard
            if (random.nextDouble() < risk) return accident(vehicle, link, time)
        }
        // Schlaglöcher und Krater: Achsbruch, Ausweichmanöver, Auffahrunfall
        if (hazard > 1 && vehicle.speed > CRASH_SPEED) {
            val risk = config.potholeAccidentsPerHour * (hazard - 1) * config.ticksPerSecondInverse() / SECONDS_PER_HOUR
            if (random.nextDouble() < risk) {
                accident(vehicle, link, time)
                infrastructure.potholeAccident(link)
                statistics.potholeAccident()
            }
        }
    }

    /** Softwarepanne (moderne Autos, im Stau häufiger) oder Ölverlust (alte Autos). `true` bei Panne. */
    private fun rollTechnicalProblems(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ): Boolean {
        val hours = config.ticksPerSecondInverse() / SECONDS_PER_HOUR
        val moving = vehicle.speed > MOVING
        val overloaded = vehicle.waitingTime > OVERLOAD_WAIT
        val failureRate = config.softwareFailuresPerHour * (if (overloaded) OVERLOAD_FACTOR else 1.0)
        val failing = (moving || overloaded) && random.nextDouble() < failureRate * hours
        if (vehicle.generation == Generation.MODERN && failing) {
            breakdown(vehicle, IncidentKind.SOFTWARE_FAILURE, time)
            return true
        }
        // Schlaglöcher reißen alten Autos die Ölwanne auf
        val oilRate = config.oilLeaksPerHour * infrastructure.hazard(link)
        if (vehicle.generation == Generation.OLD && moving && random.nextDouble() < oilRate * hours) {
            oilSpill(vehicle, link, time)
        }
        return false
    }

    /** Fahrzeug bleibt liegen (oder steckt fest) und wartet auf den Abschleppdienst bzw. Kran. */
    fun breakdown(
        vehicle: Vehicle,
        kind: IncidentKind,
        time: Double,
    ) {
        vehicle.disabled = true
        val incident = open(kind, vehicle.link, vehicle.position, time)
        incident.vehicles += vehicle
        dispatch(incident, VehicleRole.TOW_TRUCK)
        statistics.incident(kind)
    }

    private fun oilSpill(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ) {
        if (incidents.any { it.kind == IncidentKind.OIL_SPILL && it.link == link }) return
        val incident = open(IncidentKind.OIL_SPILL, link, vehicle.position, time)
        graph.linkSlowdown[link] = OIL_SLOWDOWN
        dispatch(incident, VehicleRole.STREET_SERVICE)
        statistics.incident(IncidentKind.OIL_SPILL)
    }

    /** Unfall mit [vehicle] (und meist seinem Vordermann). */
    internal fun accident(
        vehicle: Vehicle,
        link: Int,
        time: Double,
    ) {
        val incident = open(IncidentKind.ACCIDENT, link, vehicle.position, time)
        vehicle.disabled = true
        incident.vehicles += vehicle
        // Der Vordermann ist meist beteiligt
        crashPartner(vehicle, link)?.let {
            it.disabled = true
            incident.vehicles += it
        }
        // Gaffer auf der Gegenfahrbahn
        graph.linkReverse[link].takeIf { it >= 0 }?.let { graph.linkSlowdown[it] = GAPER_SLOWDOWN }
        listOf(VehicleRole.AMBULANCE, VehicleRole.POLICE, VehicleRole.TOW_TRUCK).forEach { dispatch(incident, it) }
        statistics.incident(IncidentKind.ACCIDENT)
    }

    private fun crashPartner(
        vehicle: Vehicle,
        link: Int,
    ): Vehicle? {
        val queue = lanes[link][vehicle.lane]
        val leader = queue.getOrNull(queue.indexOf(vehicle) - 1) ?: return null
        val close = leader.position - leader.type.length - vehicle.position < CRASH_GAP
        return leader.takeIf { close && it.role == VehicleRole.PRIVATE }
    }

    private fun open(
        kind: IncidentKind,
        link: Int,
        position: Double,
        time: Double,
    ): Incident {
        val point = FloatArray(LOCATE_SIZE)
        graph.locate(link, position.toFloat(), 0, point)
        return Incident(nextId++, kind, link, position, point[LOCATE_X], point[LOCATE_Y], time).also { incidents += it }
    }

    /** Schickt ein Einsatzfahrzeug von der nächsten passenden Wache los. */
    private fun dispatch(
        incident: Incident,
        role: VehicleRole,
    ) {
        val target = graph.linkFrom[incident.link]
        val path =
            depotFor(role, incident)?.let {
                if (it.node ==
                    target
                ) {
                    IntArray(0)
                } else {
                    router.route(it.node, target)
                }
            }
        if (path == null) {
            // Keine Wache oder kein Weg: Es kommt niemand, der Einsatz läuft nur über die Zeit ab
            incident.arrived += role
            return
        }
        val responder = factory.createService(role, path + incident.link, incident.startTime, incident.id)
        incident.responders += responder
        spawn(responder)
    }

    private fun depotFor(
        role: VehicleRole,
        incident: Incident,
    ): Facility? {
        val kind =
            when (role) {
                VehicleRole.AMBULANCE -> FacilityKind.AMBULANCE_STATION
                VehicleRole.POLICE -> FacilityKind.POLICE
                else -> FacilityKind.FIRE_STATION
            }
        val candidates = depots[kind] ?: depots[FacilityKind.FIRE_STATION] ?: return null
        return candidates.minByOrNull { hypot(it.x - incident.x, it.y - incident.y) }
    }

    /** Jeden Tick: Ankunft der Einsatzkräfte prüfen und erledigte Einsätze abschließen. */
    fun update(time: Double) {
        val finished = ArrayList<Incident>()
        for (incident in incidents) {
            for (responder in incident.responders) {
                if (responder.role in incident.arrived || !hasArrived(responder, incident)) continue
                incident.arrived += responder.role
                responder.working = true
                if (responder.role == VehicleRole.AMBULANCE) {
                    val seconds = time - incident.startTime
                    incident.ambulanceResponseSeconds = seconds
                    statistics.ambulanceArrived(seconds)
                }
            }
            val everyoneThere = incident.responders.all { it.role in incident.arrived }
            if (everyoneThere && incident.workUntil.isNaN()) incident.workUntil = time + workSeconds(incident.kind)
            if (time >= incident.workUntil || time - incident.startTime > GIVE_UP_SECONDS) finished += incident
        }
        finished.forEach { resolve(it) }
    }

    /** Sicherheitsnetz: Einsatzfahrzeuge ohne laufenden Einsatz verlassen die Straße. */
    fun sweep(vehicles: List<Vehicle>) {
        val open = incidents.mapTo(HashSet()) { it.id }
        for (vehicle in vehicles) {
            if (vehicle.role == VehicleRole.PRIVATE || vehicle.role.isEvent || vehicle.incidentId in open) continue
            lanes[vehicle.link][vehicle.lane].remove(vehicle)
            vehicle.active = false
            vehicle.removedByIncident = true
        }
    }

    private fun hasArrived(
        responder: Vehicle,
        incident: Incident,
    ): Boolean =
        responder.active &&
            responder.link == incident.link &&
            (responder.position >= incident.position - ARRIVAL_DISTANCE || responder.holdAtEnd)

    private fun workSeconds(kind: IncidentKind): Double =
        SECONDS_PER_MINUTE *
            when (kind) {
                IncidentKind.OIL_SPILL -> config.oilCleanupMinutes
                IncidentKind.ACCIDENT -> config.accidentMinutes
                IncidentKind.TRUCK_STUCK -> config.truckRecoveryMinutes
                else -> config.towMinutes
            }

    private fun resolve(incident: Incident) {
        for (vehicle in incident.vehicles + incident.responders) {
            if (vehicle.active &&
                vehicle.routeIndex < vehicle.route.size
            ) {
                lanes[vehicle.link][vehicle.lane].remove(vehicle)
            }
            vehicle.active = false
            vehicle.removedByIncident = true
        }
        graph.linkSlowdown[incident.link] = 1f
        graph.linkReverse[incident.link].takeIf { it >= 0 }?.let { graph.linkSlowdown[it] = 1f }
        incidents.remove(incident)
    }

    private companion object {
        const val SECONDS_PER_HOUR = 3_600.0
        const val SECONDS_PER_MINUTE = 60.0
        const val MOVING = 0.5
        const val OVERLOAD_WAIT = 60.0
        const val OVERLOAD_FACTOR = 3.0
        const val HARD_BRAKING = -6.0
        const val CRASH_GAP = 3.0
        const val CRASH_SPEED = 8.0

        const val OIL_SLOWDOWN = 0.7f
        const val GAPER_SLOWDOWN = 0.7f
        const val ARRIVAL_DISTANCE = 20.0

        /** Sicherheitsnetz: Nach drei Stunden ist jeder Einsatz erledigt, auch wenn niemand durchkam. */
        const val GIVE_UP_SECONDS = 3 * 3_600.0
    }
}

internal fun SimulationConfig.ticksPerSecondInverse(): Double = 1.0 / ticksPerSecond
