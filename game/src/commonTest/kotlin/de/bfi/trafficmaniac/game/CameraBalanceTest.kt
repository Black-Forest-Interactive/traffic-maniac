package de.bfi.trafficmaniac.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraBalanceTest {
    private fun camera(vararg revenue: Pair<FineTier, Long>): CameraRecord {
        val perTier = FineTier.entries.map { tier -> revenue.firstOrNull { it.first == tier }?.second ?: 0L }
        return CameraRecord(
            cameraId = 0,
            segment = 0,
            streetName = "Teststraße",
            mobile = false,
            countPerTier = perTier.map { 1 },
            revenuePerTier = perTier,
            fastestMeasuredKmh = 0.0,
            fastestLimitKmh = 50,
        )
    }

    @Test
    fun smallFishShareCountsOnlySpeedRevenue() {
        // Rotlicht zählt nicht mit: 600 von 800 Euro Tempo-Einnahmen kommen aus der kleinsten Stufe
        val cameras =
            listOf(
                camera(FineTier.UP_TO_10 to 450, FineTier.OVER_30 to 200, FineTier.RED_LIGHT to 900),
                camera(FineTier.UP_TO_10 to 150),
            )
        assertEquals(0.75, CameraBalance.smallFishShare(cameras))
        assertTrue(CameraBalance.summary(cameras)!!.startsWith("75 %"))
    }

    @Test
    fun noRevenueNoSummary() {
        assertNull(CameraBalance.smallFishShare(emptyList()))
        assertNull(CameraBalance.summary(listOf(camera(FineTier.RED_LIGHT to 90))))
    }

    @Test
    fun theNewspaperHasAFactEveryDay() {
        val facts = (0 until 10).map { CameraBalance.practiceFact(it) }
        assertTrue(facts.all { it.isNotBlank() })
        assertTrue(facts.distinct().size > 1)
    }
}
