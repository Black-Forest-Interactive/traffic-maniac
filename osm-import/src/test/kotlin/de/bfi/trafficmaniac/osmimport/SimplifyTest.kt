package de.bfi.trafficmaniac.osmimport

import kotlin.test.Test
import kotlin.test.assertContentEquals

class SimplifyTest {
    @Test
    fun removesPointsOnAStraightLine() {
        val line = floatArrayOf(0f, 0f, 5f, 0.1f, 10f, 0f)

        assertContentEquals(floatArrayOf(0f, 0f, 10f, 0f), simplify(line, tolerance = 0.5f))
    }

    @Test
    fun keepsSignificantCorners() {
        val corner = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f)

        assertContentEquals(corner, simplify(corner, tolerance = 0.5f))
    }
}
