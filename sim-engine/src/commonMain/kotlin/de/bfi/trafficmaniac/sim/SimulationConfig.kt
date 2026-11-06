package de.bfi.trafficmaniac.sim

data class SimulationConfig(
    /** Startwert des Zufallsgenerators: gleicher Seed + gleiche Eingaben = gleicher Verlauf. */
    val seed: Long = 42,
    val ticksPerSecond: Int = SimulationClock.DEFAULT_TICKS_PER_SECOND,
    /**
     * Neue Fahrten pro Stunde (bis M3 konstant über den Tag). Pforzheim verkraftet im aktuellen Modell
     * dauerhaft etwa 5.000/h (≈ 2.100 Fahrzeuge gleichzeitig); darüber läuft das Netz langsam voll.
     */
    val tripsPerHour: Double = 5_000.0,
    val truckShare: Double = 0.08,
    val draenglerShare: Double = 0.2,
    val gemuetlicherShare: Double = 0.2,
    /** Wie oft ein Fahrzeug seine Route mit aktuellen Fahrzeiten neu berechnet. */
    val rerouteIntervalSeconds: Double = 120.0,
    /**
     * Fahrzeuge, die so lange stehen, verlassen das Netz ("aufgegeben"). Verhindert, dass ein einzelner
     * Knoten das Netz dauerhaft blockiert. `Double.POSITIVE_INFINITY` schaltet das ab.
     */
    val stuckTimeoutSeconds: Double = 300.0,
    /** Obergrenze für Fahrzeuge, die auf die Einfahrt ins Netz warten. */
    val maxPendingVehicles: Int = 2_000,
)
