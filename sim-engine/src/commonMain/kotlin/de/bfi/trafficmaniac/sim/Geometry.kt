package de.bfi.trafficmaniac.sim

import kotlin.math.sqrt

/**
 * Abstand zweier Punkte aus den Differenzen. Bewusst `sqrt(dx² + dy²)` statt `hypot`: `hypot` schützt vor
 * Über- und Unterlauf bei extremen Werten (die bei Stadtkoordinaten in Metern nie vorkommen) und ist auf der JVM
 * mehrfach langsamer – im Profil der Wegsuche waren das rund 7 % der Simulationszeit.
 */
internal fun distance(
    dx: Float,
    dy: Float,
): Float = sqrt(dx * dx + dy * dy)

/** Wie [distance] für `Double`. */
internal fun distance(
    dx: Double,
    dy: Double,
): Double = sqrt(dx * dx + dy * dy)
