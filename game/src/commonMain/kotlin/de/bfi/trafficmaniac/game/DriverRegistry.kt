package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr

/**
 * Die Verkehrssünderkartei: Punkte, Fahrverbote und Führerscheinentzug je Fahrer.
 * Wer nicht fahren darf, dessen Fahrten fallen weg – und sein Wohngebiet ist entsprechend sauer.
 */
internal class DriverRegistry(
    private val rules: GameRules,
    private val citizens: Citizens,
) {
    private val points = HashMap<Int, Int>()
    private val bannedUntil = HashMap<Int, Double>()
    var revocations = 0
        private set
    var revocationsToday = 0
        private set

    fun isAllowedToDrive(
        person: Int,
        time: Double,
    ): Boolean = person < 0 || (bannedUntil[person] ?: 0.0) <= time

    val bannedCount: Int get() = bannedUntil.size

    /** Verbucht Punkte und Fahrverbot; liefert eine Meldung, wenn jemand den Führerschein verliert. */
    fun penalize(
        person: Int,
        homeZone: Int,
        tier: FineTier,
        time: Double,
    ): String? {
        if (person < 0) return null
        if (tier.drivingBanDays > 0) ban(person, homeZone, time + tier.drivingBanDays * SECONDS_PER_DAY)
        val total = (points[person] ?: 0) + tier.points
        if (total < rules.revocationPoints) {
            points[person] = total
            return null
        }
        points.remove(person)
        ban(person, homeZone, time + rules.revocationDays * SECONDS_PER_DAY)
        revocations++
        revocationsToday++
        return tr(
            "Führerschein weg: Ein Fahrer hat {revocationPoints} Punkte gesammelt",
            "revocationPoints" to rules.revocationPoints,
        )
    }

    /** Abgelaufene Fahrverbote aufräumen; liefert die Entzüge des Tages und setzt den Zähler zurück. */
    fun startNewDay(time: Double): Int {
        bannedUntil.entries.removeAll { it.value <= time }
        return revocationsToday.also { revocationsToday = 0 }
    }

    private fun ban(
        person: Int,
        homeZone: Int,
        until: Double,
    ) {
        bannedUntil[person] = maxOf(bannedUntil[person] ?: 0.0, until)
        citizens.lostLicense(homeZone, rules.licenseLossPain)
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400.0
    }
}
