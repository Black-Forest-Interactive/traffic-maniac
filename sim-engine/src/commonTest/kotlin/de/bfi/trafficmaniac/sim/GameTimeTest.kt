package de.bfi.trafficmaniac.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameTimeTest {
    private val fiveAm = 5 * GameTime.SECONDS_PER_HOUR

    @Test
    fun startsOnMondayMorning() {
        val time = GameTime.at(0.0, fiveAm)

        assertEquals(Weekday.MONDAY, time.weekday)
        assertEquals("05:00", time.clockText)
        assertFalse(time.isNight)
    }

    @Test
    fun rollsOverToNextDayAndWeek() {
        val tuesday = GameTime.at(19 * GameTime.SECONDS_PER_HOUR + 90, fiveAm)
        assertEquals(1, tuesday.day)
        assertEquals(Weekday.TUESDAY, tuesday.weekday)
        assertEquals("00:01", tuesday.clockText)
        assertTrue(tuesday.isNight)

        val nextMonday = GameTime.at(7 * GameTime.SECONDS_PER_DAY, fiveAm)
        assertEquals(Weekday.MONDAY, nextMonday.weekday)
        assertEquals(7, nextMonday.day)
    }

    @Test
    fun demandPeaksInRushHour() {
        val profile = DemandProfile.DEFAULT

        fun at(
            day: Int,
            hour: Double,
        ) = profile.factor(GameTime(day, hour * GameTime.SECONDS_PER_HOUR))

        assertEquals(1.0, at(day = 0, hour = 7.5), 1e-9)
        assertTrue(at(day = 0, hour = 3.0) < 0.05)
        assertTrue(at(day = 0, hour = 7.0) in at(day = 0, hour = 6.5)..at(day = 0, hour = 7.5))
        // Sonntag ist ruhiger als Montag
        assertTrue(at(day = 6, hour = 7.5) < at(day = 0, hour = 7.5))
    }
}
