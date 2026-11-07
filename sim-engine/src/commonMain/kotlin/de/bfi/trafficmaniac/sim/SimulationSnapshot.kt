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

internal fun flagsOf(vehicle: Vehicle): Byte {
    var flags = 0
    if (vehicle.disabled) flags = flags or FLAG_DISABLED
    if (vehicle.role.isEmergency) flags = flags or FLAG_EMERGENCY
    if (vehicle.drivetrain == Drivetrain.ELECTRIC) flags = flags or FLAG_ELECTRIC
    if (vehicle.role != VehicleRole.PRIVATE) flags = flags or FLAG_SERVICE
    return flags.toByte()
}
