package de.bfi.trafficmaniac.sim

/** Fester Blitzer in der Mitte eines Straßenabschnitts; misst beide Fahrtrichtungen. */
class SpeedCamera(
    val id: Int,
    val segment: Int,
    val x: Float,
    val y: Float,
)

/** Eine Messung über dem Limit (nach Abzug der Messtoleranz). */
data class SpeedViolation(
    val cameraId: Int,
    val vehicleId: Int,
    /** Wohngebiet des Fahrers oder -1 (Durchgangsverkehr). */
    val homeZone: Int,
    val measuredKmh: Double,
    val limitKmh: Int,
    val excessKmh: Double,
)

/** Messpunkt eines Blitzers auf einem Link. */
internal class CameraSite(
    val camera: SpeedCamera,
    val position: Double,
)
