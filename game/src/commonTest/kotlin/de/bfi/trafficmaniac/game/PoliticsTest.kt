package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.sim.TripResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoliticsTest {
    private val rules = GameRules()

    private fun Politics.hours(
        count: Int,
        share: Double,
        pr: PrContract = PrContract.NONE,
    ) = repeat(count) { hourPassed(share, pr, argumentsCampaign = false, finesLastHour = 0) }

    @Test
    fun individualsDoNotMatter() {
        val politics = Politics(rules)

        politics.hours(count = 200, share = 0.3)

        assertEquals(0.0, politics.anger)
        assertEquals(0.0, politics.recallIndex)
    }

    @Test
    fun widespreadMiseryEndsTheTermOfOffice() {
        val politics = Politics(rules)

        politics.hours(count = 24, share = 0.8)

        assertTrue(politics.isRecalled, "Abwahlindex ${politics.recallIndex}")
    }

    @Test
    fun prBuysTime() {
        val withoutPr = Politics(rules).apply { hours(count = 24, share = 0.6) }
        val withPr = Politics(rules).apply { hours(count = 24, share = 0.6, pr = PrContract.PREMIUM) }

        assertTrue(withPr.recallIndex < withoutPr.recallIndex)
        assertTrue(withPr.reputation > withoutPr.reputation)
        assertFalse(withPr.isRecalled)
    }

    @Test
    fun criticalPressWithoutPrCostsReputation() {
        val politics = Politics(rules)

        val messages = politics.hourPassed(0.0, PrContract.NONE, argumentsCampaign = false, finesLastHour = 10_000)

        assertTrue(politics.reputation < rules.startReputation)
        assertTrue(messages.any { "Abzocke" in it })
    }

    @Test
    fun citizensSufferFromDelaysAndFines() {
        val zones =
            listOf(
                Zone(0, ZoneKind.RESIDENTIAL, 0f, 0f, area = 100f, node = 0),
                Zone(1, ZoneKind.RESIDENTIAL, 0f, 0f, area = 300f, node = 0),
            )
        val citizens = Citizens(zones, rules)
        repeat(100) {
            citizens.tripFinished(TripResult(homeZone = 0, actualSeconds = 600.0, referenceSeconds = 100.0))
            citizens.tripFinished(TripResult(homeZone = 1, actualSeconds = 110.0, referenceSeconds = 100.0))
        }

        val moods = citizens.moods(argumentsCampaign = false)
        assertTrue(moods[0].isUnhappy)
        assertFalse(moods[1].isUnhappy)
        assertEquals(0.25, citizens.unhappyShare(moods), 1e-9)

        // 3 € pro Fahrt: spürbar, aber dank Totschlagargumenten weniger schlimm
        repeat(10) { citizens.fined(homeZone = 1, euros = 30) }
        val plain = citizens.moods(argumentsCampaign = false)[1].mobilitySatisfaction
        val calmed = citizens.moods(argumentsCampaign = true)[1].mobilitySatisfaction
        assertTrue(plain < 1.0)
        assertTrue(calmed > plain)
    }
}
