package de.bfi.trafficmaniac.city

import kotlinx.serialization.Serializable

/**
 * Quelle und Ziel von Fahrten: ein Wohn-, Gewerbe- oder Einkaufsgebiet oder ein Ein-/Ausfallpunkt am Stadtrand.
 * [node] ist der nächstgelegene Knoten des Straßennetzes, über den das Gebiet angebunden ist.
 */
@Serializable
data class Zone(
    val id: Int,
    val kind: ZoneKind,
    val x: Float,
    val y: Float,
    /** Fläche in Quadratmetern; bei [ZoneKind.GATE] 0. */
    val area: Float,
    val node: Int,
)

enum class ZoneKind {
    RESIDENTIAL,
    COMMERCIAL,
    RETAIL,
    INDUSTRIAL,

    /** Straße verlässt den Kartenausschnitt oder führt auf die Autobahn. */
    GATE,
}
