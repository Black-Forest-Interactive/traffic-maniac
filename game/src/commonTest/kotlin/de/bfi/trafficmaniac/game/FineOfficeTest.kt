package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.sim.GameTime
import kotlin.test.*

class FineOfficeTest {
    private val rules = GameRules()
    private val citizens = Citizens(listOf(Zone(0, ZoneKind.RESIDENTIAL, 0f, 0f, area = 1000f, node = 0)), rules)
    private val messages = ArrayList<String>()
    private val treasury = Treasury(0, messages::add)
    private val drivers = DriverRegistry(rules, citizens)
    private val office = FineOffice(rules, treasury, citizens, drivers, messages::add)

    /** Montag 9 Uhr (Bürozeit) nach [days] Tagen. */
    private fun mondayMorning(days: Int = 0) = GameTime(days, 9 * GameTime.SECONDS_PER_HOUR)

    private fun seconds(time: GameTime) = time.day * GameTime.SECONDS_PER_DAY + time.secondOfDay

    @Test
    fun casesAreProcessedDuringOfficeHoursAndPaid() {
        repeat(10) { office.register(FineTier.UP_TO_10, homeZone = 0, person = it, time = 0.0) }
        val nine = mondayMorning()

        office.hourPassed(seconds(nine), nine)

        assertEquals(10, office.totals.processed)
        assertTrue(treasury.fineIncome > 0)
        assertTrue(office.totals.paid + office.totals.dropped == 10)
    }

    @Test
    fun oldCasesExpire() {
        office.deadlineDays = 1
        repeat(5) { office.register(FineTier.UP_TO_20, homeZone = 0, person = it, time = 0.0) }
        val later = GameTime(2, 9 * GameTime.SECONDS_PER_HOUR)

        office.hourPassed(seconds(later), later)

        assertEquals(5, office.totals.expired)
        assertEquals(0L, treasury.fineIncome)
    }

    @Test
    fun deadlineIsLimitedByLaw() {
        office.deadlineDays = 99
        assertEquals(rules.legalMaxDeadlineDays, office.deadlineDays)
    }

    @Test
    fun enoughPointsCostTheLicense() {
        val person = 42
        // 21–30 km/h zu schnell gibt einen Punkt
        repeat(rules.revocationPoints - 1) { assertEquals(null, drivers.penalize(person, 0, FineTier.UP_TO_30, 0.0)) }
        assertTrue(drivers.isAllowedToDrive(person, 0.0))

        assertNotNull(drivers.penalize(person, 0, FineTier.UP_TO_30, 0.0))

        assertFalse(drivers.isAllowedToDrive(person, 0.0))
        assertTrue(drivers.isAllowedToDrive(person, (rules.revocationDays + 1) * GameTime.SECONDS_PER_DAY))
        assertTrue(drivers.isAllowedToDrive(-1, 0.0), "Durchgangsverkehr hat keine Kartei")
    }
}
