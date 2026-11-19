package de.bfi.trafficmaniac.sim

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class SpeedingProfileTest {
    private val samples = List(200_000) { SpeedingProfile.sample(Random(it.toLong() * 7919)) }

    private fun share(predicate: (Double) -> Boolean) = samples.count(predicate).toDouble() / samples.size

    private fun assertNear(
        expected: Double,
        actual: Double,
        tolerance: Double,
        what: String,
    ) = assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$what: erwartet ~$expected, ist $actual")

    @Test
    fun mostSpeedersAreOnlySlightlyTooFast() {
        assertNear(0.45, share { it <= 5 }, 0.01, "bis 5 km/h")
        assertNear(0.725, share { it <= 10 }, 0.01, "bis 10 km/h")
        assertTrue(samples.all { it > 0 && it <= 40 })
    }

    @Test
    fun finesFollowBambergAndHambruecken() {
        // Geahndet wird erst über der Toleranz; die Klassen zählen nach Abzug der Toleranz
        val tolerance = SpeedControl.DEFAULT_TOLERANCE_KMH
        val fined = samples.filter { it > tolerance }.map { it - tolerance }

        fun fineShare(range: ClosedFloatingPointRange<Double>) = fined.count { it in range }.toDouble() / fined.size
        assertNear(0.76, fineShare(0.0..10.0), 0.03, "bis 10 km/h (Bamberg 74 %, Hambrücken 77 %)")
        assertNear(0.17, fineShare(10.0..15.0), 0.03, "11–15 km/h (Bamberg 20 %, Hambrücken 18 %)")
        assertNear(0.055, fineShare(15.0..20.0), 0.02, "16–20 km/h (Bamberg 5 %)")
        assertNear(0.015, fineShare(20.0..100.0), 0.01, "über 20 km/h (Bamberg 1 %)")
    }
}
