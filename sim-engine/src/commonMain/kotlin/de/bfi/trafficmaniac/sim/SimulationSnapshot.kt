package de.bfi.trafficmaniac.sim

/** Kennzahlen eines Simulationszeitpunkts. */
data class SimulationStats(
    val activeVehicles: Int,
    val pendingVehicles: Int,
    val completedTrips: Int,
    val stuckRemovals: Int,
    val meanSpeedKmh: Double,
    val meanTripMinutes: Double,
)

/**
 * Unveränderlicher Zustand für die Darstellung. Fahrzeuge sind nach [ids] aufsteigend sortiert,
 * damit die Oberfläche zwei Snapshots schnell einander zuordnen und dazwischen interpolieren kann.
 */
class SimulationSnapshot(
    val tick: Long,
    val time: Double,
    val gameTime: GameTime,
    val ids: IntArray,
    val x: FloatArray,
    val y: FloatArray,
    val dirX: FloatArray,
    val dirY: FloatArray,
    val lengths: FloatArray,
    val widths: FloatArray,
    val colors: IntArray,
    /** Positionen der Ampeln; ändert sich, wenn Ampeln gebaut oder abgerissen werden. */
    val signalMarkers: SignalMarkers,
    /** Zustand je Ampel in der Reihenfolge von [signalMarkers]. */
    val signalStates: Array<SignalState>,
    val cameras: List<SpeedCamera>,
    val incidents: List<IncidentMarker>,
    val chargingStations: List<ChargingStationState>,
    /** Je Fahrzeug: [FLAG_DISABLED], [FLAG_EMERGENCY], [FLAG_ELECTRIC], [FLAG_SERVICE]. */
    val flags: ByteArray,
    /** Umweltraster (wird alle 5 Sekunden Spielzeit erneuert). */
    val environment: EnvironmentSnapshot,
    val stats: SimulationStats,
    /** Straßenzustand, Ampelausfälle, Baustellen, Sperrungen und Umleitungen. */
    val infrastructure: InfrastructureSnapshot = InfrastructureSnapshot.EMPTY,
    val parkingLots: List<ParkingLotState> = emptyList(),
    val parking: ParkingSummary = ParkingSummary(),
    /** Laufende Ereignisse (Demos, Feste, Korsos, Schwertransporte). */
    val events: List<EventMarker> = emptyList(),
    /** Je Fahrzeug: [VehicleType.ordinal] – für die Sprites. */
    val types: ByteArray = ByteArray(ids.size),
    /** Je Fahrzeug: [VehicleRole.ordinal]. */
    val roles: ByteArray = ByteArray(ids.size),
    /** Je Fahrzeug: Pannenart als [IncidentKind.ordinal] + 1, 0 = keine Panne. */
    val troubles: ByteArray = ByteArray(ids.size),
    /**
     * Verkehrsfluss je Straßenabschnitt: freie Fahrzeit / aktuelle Fahrzeit des langsamsten Links (1 = frei, nahe 0 =
     * Stillstand) – für die Staufarben in der Gesamtansicht.
     */
    val segmentFlow: FloatArray = FloatArray(0),
) {
    val vehicleCount: Int get() = ids.size
}

/** Ort und Art eines Vorfalls für die Karte. */
class IncidentMarker(
    val kind: IncidentKind,
    val x: Float,
    val y: Float,
)

const val FLAG_DISABLED = 1
const val FLAG_EMERGENCY = 2
const val FLAG_ELECTRIC = 4
const val FLAG_SERVICE = 8

/** Rad-Korso oder Schwertransport (Ereignis). */
const val FLAG_EVENT = 16

/** Steht schon lange im Stau (Dampf aus der Motorhaube). */
const val FLAG_JAMMED = 32

/** Bremst kräftig (Sprite wippt nach vorn). */
const val FLAG_BRAKING = 64

private const val JAMMED_SECONDS = 90.0
private const val BRAKING = -2.5

internal fun flagsOf(vehicle: Vehicle): Byte {
    var flags = 0
    if (vehicle.disabled) flags = flags or FLAG_DISABLED
    // Der Schwertransport fährt mit Polizeibegleitung – also mit Blaulicht
    if (vehicle.role.isEmergency || vehicle.role == VehicleRole.HEAVY_TRANSPORT) flags = flags or FLAG_EMERGENCY
    if (vehicle.role.isEvent) flags = flags or FLAG_EVENT
    if (vehicle.waitingTime > JAMMED_SECONDS && !vehicle.disabled) flags = flags or FLAG_JAMMED
    if (vehicle.acceleration < BRAKING) flags = flags or FLAG_BRAKING
    if (vehicle.drivetrain == Drivetrain.ELECTRIC) flags = flags or FLAG_ELECTRIC
    if (vehicle.role != VehicleRole.PRIVATE) flags = flags or FLAG_SERVICE
    return flags.toByte()
}
