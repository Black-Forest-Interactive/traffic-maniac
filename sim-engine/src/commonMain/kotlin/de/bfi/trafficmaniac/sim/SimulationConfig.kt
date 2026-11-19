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
    /** Linienbusse: Takt je Linienrichtung tagsüber (6–20 Uhr) und am Rand des Betriebs, in Minuten. */
    val busHeadwayMinutes: Double = 20.0,
    val busOffPeakHeadwayMinutes: Double = 30.0,
    /** Betriebszeit der Busse: erste und letzte Abfahrtsstunde. */
    val busFirstHour: Int = 5,
    val busLastHour: Int = 23,
    /** Haltezeit an einer Haltestelle in Sekunden. */
    val busDwellMinSeconds: Double = 15.0,
    val busDwellMaxSeconds: Double = 35.0,
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
    /** Anteil der Raser bei passendem Limit (Messungen: 13–19 %, siehe SpeedingProfile). */
    val speedingBaseProbability: Double = 0.15,
    /** Wahrscheinlichkeit, ein Schild zu übersehen, bei Aufmerksamkeit 0 (skaliert linear). */
    val overlookProbability: Double = 0.5,
    /** Anteil der Ortskundigen, die feste Blitzer kennen und davor bremsen. */
    val cameraAwareShare: Double = 0.7,
    /** Obergrenze für Fahrzeuge, die auf die Einfahrt ins Netz warten. */
    val maxPendingVehicles: Int = 2_000,
    /**
     * Zustand der Straßen, Ampeln und Brücken zu Spielbeginn zwischen diesem Wert und 100 %: 1 − (1 − min) · u^Schiefe
     * mit gleichverteiltem u. Je größer die Schiefe, desto mehr Straßen sind anfangs gut in Schuss.
     */
    val initialConditionMin: Double = 0.6,
    val initialConditionSkew: Double = 3.0,
    /** Alterung je Tag (Anteil des Zustands), ohne Erhaltungsbudget. */
    val agingPerDay: Double = 0.004,
    /** Verschleiß je Überfahrt einer 10-t-Achse (Achslast hoch 4: ein PKW zählt 0,00005, ein LKW 0,39). */
    val wearPerAxleLoad: Double = 0.0001,
    /** Brücken verfallen so viel schneller. */
    val bridgeWearFactor: Double = 2.0,
    val signalAgingPerDay: Double = 0.012,
    val signalWearPerVehicle: Double = 0.000_002,
    /** Unfälle durch Schlaglöcher je Fahrstunde und Gefahrenstufe (Krater zählen doppelt so viel wie Schlaglöcher). */
    val potholeAccidentsPerHour: Double = 0.002,
    /** Anteil der LKW-Fahrer, die Gewichtslimits ignorieren. */
    val weightLimitIgnoreShare: Double = 0.15,
    /** Bergung eines eingeklemmten LKW (Kran) in Minuten. */
    val truckRecoveryMinutes: Double = 45.0,
    /** Fläche einer Gewerbe-/Einkaufszone je Straßenrand-Parkplatz. */
    val curbSquareMetersPerSpace: Double = 700.0,
    /** So weit läuft man höchstens vom Parkplatz zum Ziel. */
    val parkingWalkMeters: Double = 500.0,
    /** In diesem Umkreis kreisen Parkplatzsucher um den Block. */
    val parkingSearchMeters: Double = 700.0,
    val maxParkingAttempts: Int = 3,
    val maxParkingSearchMinutes: Double = 15.0,
    val workStayHours: Double = 8.0,
    val shortStayHours: Double = 1.0,
    /** Höchste Gebühr (Euro je Stunde), die Fahrer zahlen – gleichverteilt zwischen 0 und diesem Wert. */
    val maxFeeTolerance: Double = 4.0,
    /** Startgebühr gebührenpflichtiger Parkplätze und Parkhäuser in Euro je Stunde. */
    val defaultParkingFee: Double = 1.5,
    /** Startgebühr am Parkscheinautomaten am Straßenrand in Euro je Stunde (0 = kostenlos). */
    val defaultCurbFee: Double = 0.0,
    /** Anteil der Fahrer, die bei frischem Rot noch durchfahren (mal [DriverProfile.speedingFactor]). */
    val redLightRunnerShare: Double = 0.05,
    /** Anteil der Fahrer, die auf Fahrradstraßen trotzdem überholen (mal [DriverProfile.speedingFactor]). */
    val overtakingBanIgnoreShare: Double = 0.12,
    /** Anteil der Fahrer, die trotzdem auf der Busspur fahren (mal [DriverProfile.speedingFactor]). */
    val busLaneIgnoreShare: Double = 0.06,
    /** Bei einer Polizeikontrolle: Anteil der Angehaltenen mit Mängeln oder Handy am Steuer. */
    val checkFindingRate: Double = 0.04,
    /** Wetter (Schnee, Sommerreifen, Räumdienst); `null` = trocken. */
    val weather: WeatherConfig? = null,
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
