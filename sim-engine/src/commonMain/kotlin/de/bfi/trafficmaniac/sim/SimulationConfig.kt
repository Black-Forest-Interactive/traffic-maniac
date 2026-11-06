package de.bfi.trafficmaniac.sim

import kotlinx.serialization.Serializable

@Serializable
data class SimulationConfig(
    /** Startwert des Zufallsgenerators: gleicher Seed + gleiche Eingaben = gleicher Verlauf. */
    val seed: Long = 42,
    val ticksPerSecond: Int = SimulationClock.DEFAULT_TICKS_PER_SECOND,
    /**
     * Neue Fahrten pro Stunde in der Spitzenstunde; über den Tag skaliert mit [demandProfile].
     * Pforzheim verkraftet mit Pannen, Unfällen und Ladesäulen etwa 4.500/h in der Spitze;
     * kurze Spitzen darüber bauen sich in ruhigeren Stunden wieder ab.
     */
    val peakTripsPerHour: Double = 4_500.0,
    val demandProfile: DemandProfile = DemandProfile.DEFAULT,
    /** Uhrzeit beim Start am ersten Montag (Sekunden nach Mitternacht). */
    val startSecondOfDay: Double = DEFAULT_START_HOUR * GameTime.SECONDS_PER_HOUR,
    val truckShare: Double = 0.06,
    val vanShare: Double = 0.08,
    /** Anteil Elektroautos unter den PKW. */
    val electricShare: Double = 0.15,
    /** Anteil Diesel unter den Verbrennern (LKW fahren immer Diesel). */
    val dieselShare: Double = 0.35,
    /** Anteil alter Fahrzeuge (Elektroautos sind immer modern). */
    val oldShare: Double = 0.35,
    /** Softwarepannen moderner Autos je Fahrstunde; im Stau dreimal so oft ("Systemüberlastung"). */
    val softwareFailuresPerHour: Double = 0.0015,
    /** Ölverlust alter Autos je Fahrstunde. */
    val oilLeaksPerHour: Double = 0.002,
    /** Unfallrisiko je Sekunde Vollbremsung, skaliert mit Unaufmerksamkeit und Gereiztheit. */
    val accidentRiskPerHardBrakingSecond: Double = 0.0001,
    /** Wahrscheinlichkeit je Sekunde (in der Mitte eines Abschnitts), dass ein Lieferwagen in zweiter Reihe hält. */
    val vanDeliveryChance: Double = 0.003,
    val deliveryMinMinutes: Double = 2.0,
    val deliveryMaxMinutes: Double = 6.0,
    /** Arbeitszeit am Einsatzort in Minuten. */
    val towMinutes: Double = 10.0,
    val oilCleanupMinutes: Double = 15.0,
    val accidentMinutes: Double = 20.0,
    /** Akkugröße und Ladestand, ab dem ein Elektroauto erst zur Ladesäule fährt. */
    val batteryKwh: Double = 60.0,
    val chargeBelowShare: Double = 0.2,
    val chargeMinutes: Double = 25.0,
    /** Anteile der Fahrertypen (werden normiert). */
    val driverMix: Map<DriverProfile, Double> = DEFAULT_DRIVER_MIX,
    /** Wie oft ein Fahrzeug seine Route mit aktuellen Fahrzeiten neu berechnet. */
    val rerouteIntervalSeconds: Double = 120.0,
    /**
     * Fahrzeuge, die so lange stehen, verlassen das Netz ("aufgegeben"). Verhindert, dass ein einzelner
     * Knoten das Netz dauerhaft blockiert. `Double.POSITIVE_INFINITY` schaltet das ab.
     */
    val stuckTimeoutSeconds: Double = 300.0,
    /** Anteil der Fahrer, die auch bei passendem Limit zu schnell fahren. */
    val speedingBaseProbability: Double = 0.08,
    /** Wahrscheinlichkeit, ein Schild zu übersehen, bei Aufmerksamkeit 0 (skaliert linear). */
    val overlookProbability: Double = 0.5,
    /** Anteil der Ortskundigen, die feste Blitzer kennen und davor bremsen. */
    val cameraAwareShare: Double = 0.7,
    /** Obergrenze für Fahrzeuge, die auf die Einfahrt ins Netz warten. */
    val maxPendingVehicles: Int = 2_000,
) {
    companion object {
        const val DEFAULT_START_HOUR = 5
        val DEFAULT_DRIVER_MIX =
            mapOf(
                DriverProfile.NORMALO to 0.35,
                DriverProfile.DRAENGLER to 0.15,
                DriverProfile.GEMUETLICHER to 0.15,
                DriverProfile.SPAETZUENDER to 0.1,
                DriverProfile.TRAEUMER to 0.1,
                DriverProfile.UNSICHERER to 0.15,
            )
    }
}
