package de.bfi.trafficmaniac.city

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalProjectionTest {
    private val projection = LocalProjection(GeoPoint(lat = 48.89, lon = 8.69))

    @Test
    fun originIsZero() {
        assertEquals(0f, projection.x(8.69))
        assertEquals(0f, projection.y(48.89))
    }

    @Test
    fun oneHundredthDegreeHasExpectedLength() {
        // 0,01° Breite ≈ 1112 m, 0,01° Länge bei 48,89° N ≈ 731 m
        assertTrue(abs(projection.y(48.90) - 1112f) < 2f)
        assertTrue(abs(projection.x(8.70) - 731f) < 2f)
    }
}
