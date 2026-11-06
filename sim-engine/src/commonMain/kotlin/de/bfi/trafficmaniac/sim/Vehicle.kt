package de.bfi.trafficmaniac.sim

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

enum class VehicleType(
    val length: Float,
    val width: Float,
    /** Maximale Beschleunigung in m/s². */
    val maxAcceleration: Double,
    /** Komfortable Verzögerung in m/s². */
    val comfortableDeceleration: Double,
    /** Bauartbedingte Höchstgeschwindigkeit in m/s. */
    val maxSpeed: Double,
    /** Masse in kg – bestimmt Verbrauch, Reifen- und Bremsabrieb. */
    val massKg: Double,
    /** Kraftstoffverbrauch im Leerlauf in Litern pro Sekunde. */
    val idleFuelPerSecond: Double,
    /** Stickoxide in Gramm pro Liter Kraftstoff. */
    val noxPerLiter: Double,
    /** Feinstaub aus dem Auspuff in Gramm pro Liter. */
    val exhaustPmPerLiter: Double,
    /** Zuschlag auf den Schallleistungspegel in dB. */
    val noiseBonusDb: Double,
) {
    CAR(
        4.5f,
        1.9f,
        1.4,
        2.0,
        50.0,
        massKg = 1_400.0,
        idleFuelPerSecond = 0.00025,
        noxPerLiter = 4.0,
        exhaustPmPerLiter = 0.03,
        noiseBonusDb = 0.0,
    ),
    VAN(
        6.0f,
        2.0f,
        1.1,
        1.8,
        40.0,
        massKg = 2_800.0,
        idleFuelPerSecond = 0.0004,
        noxPerLiter = 10.0,
        exhaustPmPerLiter = 0.08,
        noiseBonusDb = 3.0,
    ),
    TRUCK(
        12f,
        2.5f,
        0.7,
        1.5,
        25.0,
        massKg = 18_000.0,
        idleFuelPerSecond = 0.0008,
        noxPerLiter = 25.0,
        exhaustPmPerLiter = 0.2,
        noiseBonusDb = 9.0,
    ),
}

/**
 * Fahrerstereotyp als Parametersatz des Intelligent Driver Model (IDM).
 * Einen "Raser"-Typ gibt es bewusst nicht; zu schnell fährt, wem das Limit unpassend niedrig vorkommt
 * ([speedingFactor] verstärkt oder dämpft diese Neigung).
 */
enum class DriverProfile(
    val germanName: String,
    /** Gewünschte Zeitlücke zum Vordermann in Sekunden. */
    val timeHeadway: Double,
    /** Mindestabstand im Stand in Metern. */
    val minimumGap: Double,
    val accelerationFactor: Double,
    val decelerationFactor: Double,
    /** Anteil des Tempolimits, den der Fahrer anstrebt. */
    val speedFactor: Double,
    /** Multiplikator für die Wahrscheinlichkeit, zu schnell zu fahren. */
    val speedingFactor: Double,
    /** Aufmerksamkeit eines ausgeruhten Fahrers (0..1). */
    val baseAttention: Double,
    /** Sekunden, bis der Fahrer losfährt, wenn es weitergeht (z. B. bei Grün). */
    val reactionDelay: Double,
    /** Anteil des Tempos, mit dem sich der Fahrer Kreuzungen nähert (1 = ohne Zögern). */
    val junctionCaution: Double,
) {
    NORMALO("Normalo", 1.4, 2.0, 1.0, 1.0, 1.0, 1.0, baseAttention = 0.85, reactionDelay = 0.6, junctionCaution = 1.0),
    DRAENGLER(
        "Drängler",
        0.8,
        1.0,
        1.4,
        1.3,
        1.0,
        1.4,
        baseAttention = 0.75,
        reactionDelay = 0.3,
        junctionCaution = 1.0,
    ),
    GEMUETLICHER(
        "Gemütlicher",
        2.0,
        3.0,
        0.7,
        0.8,
        0.85,
        0.3,
        baseAttention = 0.7,
        reactionDelay = 1.2,
        junctionCaution = 0.9,
    ),
    SPAETZUENDER(
        "Spätzünder",
        1.5,
        2.0,
        0.9,
        1.0,
        1.0,
        0.8,
        baseAttention = 0.8,
        reactionDelay = 2.5,
        junctionCaution = 1.0,
    ),
    TRAEUMER(
        "Träumer",
        1.6,
        2.5,
        0.9,
        1.1,
        0.95,
        1.0,
        baseAttention = 0.55,
        reactionDelay = 1.5,
        junctionCaution = 1.0,
    ),
    UNSICHERER(
        "Unsicherer",
        2.2,
        3.0,
        0.7,
        0.9,
        0.85,
        0.2,
        baseAttention = 0.9,
        reactionDelay = 1.0,
        junctionCaution = 0.6,
    ),
}

enum class Drivetrain {
    PETROL,
    DIESEL,
    ELECTRIC,
}

/** Moderne Autos haben Softwarepannen, alte verlieren Öl. */
enum class Generation {
    MODERN,
    OLD,
}

enum class VehicleRole(
    val label: String,
) {
    PRIVATE("Privat"),
    AMBULANCE("Rettungswagen"),
    POLICE("Polizei"),
    TOW_TRUCK("Abschleppdienst"),
    STREET_SERVICE("Straßenmeisterei"),
    ;

    /** Blaulicht: darf über Rot und bekommt eine Rettungsgasse. */
    val isEmergency: Boolean get() = this == AMBULANCE || this == POLICE
}

/** Ein Fahrzeug im Netz. Position ist die Stoßstange vorn, gemessen ab Beginn des aktuellen Links. */
class Vehicle internal constructor(
    val id: Int,
    val type: VehicleType,
    val driver: DriverProfile,
    /** Farbe als ARGB. */
    val color: Int,
    internal var route: IntArray,
    internal var destinationNode: Int,
    val departureTime: Double,
    /** Ortskundige kennen die festen Blitzer und bremsen davor ab. */
    val knowsCameras: Boolean,
    /** Wohngebiet des Fahrers oder -1 (Durchgangsverkehr). */
    val homeZone: Int,
    /** Fahrer (Personennummer) oder -1 bei Durchgangsverkehr. */
    val person: Int,
    /** Fahrzeit der Route ohne Verkehr und mit den ursprünglichen Tempolimits – der Maßstab für "schnell am Ziel". */
    val referenceSeconds: Double,
    val drivetrain: Drivetrain = Drivetrain.PETROL,
    val generation: Generation = Generation.MODERN,
    val role: VehicleRole = VehicleRole.PRIVATE,
) {
    /** Akkuladung in kWh (nur Elektroautos). */
    internal var batteryKwh = 0.0

    /** Panne oder Unfall: steht und wartet auf den Abschleppdienst. */
    internal var disabled = false

    /** Einsatzfahrzeug arbeitet am Einsatzort. */
    internal var working = false

    /** Lieferwagen hält in zweiter Reihe bis zu diesem Zeitpunkt (0 = fährt). */
    internal var deliveringUntil = 0.0

    /** Pro Fahrt hält ein Lieferwagen höchstens einmal. */
    internal var hasDelivered = false

    /** Wartet am Ende des Links, z. B. weil an der Ladesäule kein Platz frei ist. */
    internal var holdAtEnd = false

    /** Ladesäule, die angesteuert wird (Index), oder -1. */
    internal var chargingStation = -1

    /** Eigentliches Ziel nach dem Laden. */
    internal var finalDestination = destinationNode

    /** Steht gerade an der Ladesäule (nicht auf der Straße). */
    internal var charging = false

    /** Reine Ladezeit dieser Fahrt – zählt nicht als Verspätung (das Warten davor schon). */
    internal var chargingSeconds = 0.0

    /** Vom Abschleppdienst entfernt bzw. Einsatz beendet (zählt weder als angekommen noch als aufgegeben). */
    internal var removedByIncident = false

    /** Einsatz, zu dem ein Einsatzfahrzeug unterwegs ist, oder -1. */
    internal var incidentId = -1

    /** Steht absichtlich (Panne, Einsatz, Warten) und zählt deshalb nicht als "aufgegeben". */
    internal val isParked: Boolean get() = disabled || working || holdAtEnd || deliveringUntil > 0

    /** Wie viel km/h der Fahrer auf dem aktuellen Link über dem Limit fährt (0 = hält sich daran). */
    internal var speedingKmh = 0.0

    /** Aufmerksamkeit 0..1: sinkt durch Schilderwald, Frust und Stress, erholt sich auf ruhigen Strecken. */
    internal var attention = driver.baseAttention

    /** Frust 0..1: steigt hinter langsamen Vordermännern. */
    internal var frustration = 0.0

    /** Stress 0..1: steigt, wenn die Fahrt deutlich länger dauert als gewohnt. */
    internal var stress = 0.0

    /** Wie lange der Fahrer schon steht, obwohl es weitergehen könnte (Reaktionszeit). */
    internal var startHesitation = 0.0

    /** Seit der letzten Ablage im Umweltraster ausgestoßen (Gramm bzw. Schallenergie). */
    internal val pendingEmissions = DoubleArray(Pollutant.entries.size)

    /** Gereiztheit aus Frust und Stress (0..1). */
    internal val irritation: Double get() = max(frustration, stress)

    internal var routeIndex = 0
    internal var lane = 0
    internal var position = 0.0
    internal var speed = 0.0
    internal var acceleration = 0.0
    internal var waitingTime = 0.0
    internal var linkEnterTime = departureTime
    internal var lastRerouteTime = departureTime
    internal var active = true

    val link: Int get() = route[routeIndex]

    /** Nächster Link der Route oder -1 am Ziel. */
    internal val nextLink: Int get() = if (routeIndex + 1 < route.size) route[routeIndex + 1] else -1

    internal val maxAcceleration get() = type.maxAcceleration * driver.accelerationFactor
    internal val comfortableDeceleration get() = type.comfortableDeceleration * driver.decelerationFactor

    /**
     * IDM-Beschleunigung für Wunschgeschwindigkeit [desiredSpeed], Lücke [gap] zum Hindernis
     * und Geschwindigkeitsdifferenz [approachRate] (positiv, wenn man aufholt).
     */
    internal fun idm(
        desiredSpeed: Double,
        gap: Double,
        approachRate: Double,
    ): Double {
        val a = maxAcceleration
        val free = (speed / max(desiredSpeed, MIN_DESIRED_SPEED)).pow(ACCELERATION_EXPONENT)
        if (gap == Double.POSITIVE_INFINITY) return a * (1 - free)
        // Gereizte Fahrer fahren dichter auf
        val headway = driver.timeHeadway * (1 - IRRITATION_HEADWAY_CUT * irritation)
        val desiredGap =
            driver.minimumGap +
                max(0.0, speed * headway + speed * approachRate / (2 * sqrt(a * comfortableDeceleration)))
        val interaction = (desiredGap / max(gap, MIN_GAP)).pow(2)
        return (a * (1 - free - interaction)).coerceAtLeast(-MAX_DECELERATION)
    }

    private companion object {
        const val ACCELERATION_EXPONENT = 4.0
        const val MIN_DESIRED_SPEED = 1.0
        const val MIN_GAP = 0.1
        const val MAX_DECELERATION = 9.0
        const val IRRITATION_HEADWAY_CUT = 0.35
    }
}
