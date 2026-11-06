package de.bfi.trafficmaniac.sim

import kotlinx.serialization.Serializable

@Serializable
data class SimulationConfig(
    /** Startwert des Zufallsgenerators: gleicher Seed + gleiche Eingaben = gleicher Verlauf. */
    val seed: Long = 42,
    val ticksPerSecond: Int = SimulationClock.DEFAULT_TICKS_PER_SECOND,
    /**
     * Neue Fahrten pro Stunde in der Spitzenstunde; über den Tag skaliert mit [demandProfile].
     * Pforzheim verkraftet im aktuellen Modell dauerhaft etwa 5.000/h (≈ 2.100 Fahrzeuge gleichzeitig);
     * kurze Spitzen darüber bauen sich in ruhigeren Stunden wieder ab.
     */
    val peakTripsPerHour: Double = 5_000.0,
    val demandProfile: DemandProfile = DemandProfile.DEFAULT,
    /** Uhrzeit beim Start am ersten Montag (Sekunden nach Mitternacht). */
    val startSecondOfDay: Double = DEFAULT_START_HOUR * GameTime.SECONDS_PER_HOUR,
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
    /** Anteil der Fahrer, die auch bei passendem Limit zu schnell fahren. */
    val speedingBaseProbability: Double = 0.08,
    /** Anteil der Ortskundigen, die feste Blitzer kennen und davor bremsen. */
    val cameraAwareShare: Double = 0.7,
    /** Obergrenze für Fahrzeuge, die auf die Einfahrt ins Netz warten. */
    val maxPendingVehicles: Int = 2_000,
) {
    companion object {
        const val DEFAULT_START_HOUR = 5
    }
}
