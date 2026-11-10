package de.bfi.trafficmaniac.sim

/**
 * Blitzer in der Mitte eines Straßenabschnitts; misst beide Fahrtrichtungen.
 * Feste Blitzer kennen die Ortskundigen, mobile Lasermessungen ([mobile]) nicht.
 *
 * Ein Rotlichtblitzer ([redLight]) steht an der Haltelinie einer Ampelzufahrt ([link]) und misst nur diese
 * Richtung, auf Wunsch zusätzlich das Tempo ([measuresSpeed]). Eine Anzeigetafel ([display]) misst nur und zählt,
 * wie viele zu schnell sind – Bußgelder gibt es dort nicht.
 */
class SpeedCamera(
    val id: Int,
    val segment: Int,
    val x: Float,
    val y: Float,
    val mobile: Boolean = false,
    /** Blitzeranhänger: mobil (Ortskundige kennen ihn nicht), lässt sich umsetzen. */
    val trailer: Boolean = false,
    val redLight: Boolean = false,
    val measuresSpeed: Boolean = true,
    val display: Boolean = false,
    /** Zufahrt eines Rotlichtblitzers, sonst -1. */
    val link: Int = -1,
) {
    /** Anzeigetafel: Fahrzeuge vorbei und davon zu schnell. */
    var passed: Int = 0
        internal set
    var speeding: Int = 0
        internal set
}

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
    /** Über Rot gefahren: so viele Sekunden nach Beginn der Rotphase (sonst `null`, Tempoverstoß). */
    val redSeconds: Double? = null,
)

/** Messpunkt eines Blitzers auf einem Link. */
internal class CameraSite(
    val camera: SpeedCamera,
    val position: Double,
)
