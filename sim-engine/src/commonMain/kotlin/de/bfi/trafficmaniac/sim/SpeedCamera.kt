package de.bfi.trafficmaniac.sim

/**
 * Blitzer in der Mitte eines Straßenabschnitts; misst beide Fahrtrichtungen.
 * Feste Blitzer kennen die Ortskundigen, mobile Lasermessungen ([mobile]) nicht.
 */
class SpeedCamera(
    val id: Int,
    val segment: Int,
    val x: Float,
    val y: Float,
    val mobile: Boolean = false,
)

/** Eine Messung über dem Limit (nach Abzug der Messtoleranz). */
data class SpeedViolation(
    val cameraId: Int,
    val vehicleId: Int,
    /** Wohngebiet des Fahrers oder -1 (Durchgangsverkehr). */
    val homeZone: Int,
    /** Fahrer (Personennummer) oder -1. */
    val person: Int,
    val measuredKmh: Double,
    val limitKmh: Int,
    val excessKmh: Double,
)

/** Messpunkt eines Blitzers auf einem Link. */
internal class CameraSite(
    val camera: SpeedCamera,
    val position: Double,
)
