package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals

/** Gebührenpflicht 8–20 Uhr: Nur der Teil eines Aufenthalts in dieser Zeit wird bezahlt. */
class ParkingFeeTest {
    @Test
    fun withinPaidHoursEverythingCounts() {
        assertEquals(2.0, paidHoursBetween(10.0, 12.0, OPEN, CLOSE), DELTA)
    }

    @Test
    fun nightIsFree() {
        assertEquals(0.0, paidHoursBetween(21.0, 31.0, OPEN, CLOSE), DELTA)
    }

    @Test
    fun overnightStayPaysOnlyTheDaytimeParts() {
        // 18 Uhr bis 10 Uhr am nächsten Tag: 18–20 und 8–10
        assertEquals(4.0, paidHoursBetween(18.0, 34.0, OPEN, CLOSE), DELTA)
    }

    @Test
    fun laterDaysWork() {
        // Tag 3, 7 bis 9 Uhr: nur 8–9
        assertEquals(1.0, paidHoursBetween(3 * 24 + 7.0, 3 * 24 + 9.0, OPEN, CLOSE), DELTA)
    }

    private companion object {
        const val OPEN = 8
        const val CLOSE = 20
        const val DELTA = 1e-9
    }
}
