package de.bfi.trafficmaniac.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapInspectionTest {
    private val pothole = InspectTarget(Inspected.Damage(7), 100f, 0f)
    private val accident = InspectTarget(Inspected.Incident(kind = 0, startTime = 12.0), 130f, 0f)

    @Test
    fun theNearestTargetWithinTheRadiusWins() {
        assertEquals(Inspected.Damage(7), inspectAt(listOf(pothole, accident), 110f, 0f, 30f))
        assertEquals(Inspected.Incident(0, 12.0), inspectAt(listOf(pothole, accident), 125f, 0f, 30f))
    }

    @Test
    fun aClickBesideEverythingClosesTheCard() {
        assertNull(inspectAt(listOf(pothole, accident), 400f, 0f, 30f))
    }
}
