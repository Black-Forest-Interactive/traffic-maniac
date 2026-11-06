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
) {
    CAR(length = 4.5f, width = 1.9f, maxAcceleration = 1.4, comfortableDeceleration = 2.0, maxSpeed = 50.0),
    TRUCK(length = 12f, width = 2.5f, maxAcceleration = 0.7, comfortableDeceleration = 1.5, maxSpeed = 25.0),
}

/**
 * Fahrerstereotyp als Parametersatz des Intelligent Driver Model (IDM).
 * Bewusst ohne Rasen: Alle halten sich ans Limit, fahren aber unterschiedlich geschickt.
 */
enum class DriverProfile(
    /** Gewünschte Zeitlücke zum Vordermann in Sekunden. */
    val timeHeadway: Double,
    /** Mindestabstand im Stand in Metern. */
    val minimumGap: Double,
    val accelerationFactor: Double,
    val decelerationFactor: Double,
    /** Anteil des Tempolimits, den der Fahrer anstrebt. */
    val speedFactor: Double,
) {
    NORMALO(timeHeadway = 1.4, minimumGap = 2.0, accelerationFactor = 1.0, decelerationFactor = 1.0, speedFactor = 1.0),
    DRAENGLER(
        timeHeadway = 0.8,
        minimumGap = 1.0,
        accelerationFactor = 1.4,
        decelerationFactor = 1.3,
        speedFactor = 1.0,
    ),
    GEMUETLICHER(
        timeHeadway = 2.0,
        minimumGap = 3.0,
        accelerationFactor = 0.7,
        decelerationFactor = 0.8,
        speedFactor = 0.85,
    ),
}

/** Ein Fahrzeug im Netz. Position ist die Stoßstange vorn, gemessen ab Beginn des aktuellen Links. */
class Vehicle internal constructor(
    val id: Int,
    val type: VehicleType,
    val driver: DriverProfile,
    /** Farbe als ARGB. */
    val color: Int,
    internal var route: IntArray,
    val destinationNode: Int,
    val departureTime: Double,
) {
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
        val desiredGap =
            driver.minimumGap +
                max(0.0, speed * driver.timeHeadway + speed * approachRate / (2 * sqrt(a * comfortableDeceleration)))
        val interaction = (desiredGap / max(gap, MIN_GAP)).pow(2)
        return (a * (1 - free - interaction)).coerceAtLeast(-MAX_DECELERATION)
    }

    private companion object {
        const val ACCELERATION_EXPONENT = 4.0
        const val MIN_DESIRED_SPEED = 1.0
        const val MIN_GAP = 0.1
        const val MAX_DECELERATION = 9.0
    }
}
