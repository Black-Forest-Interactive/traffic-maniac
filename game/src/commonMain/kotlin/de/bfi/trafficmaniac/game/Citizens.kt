package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.sim.TripResult
import kotlin.math.max
import kotlin.math.pow

/** Stimmung eines Wohngebiets. Beide Bedürfnisse werden nur aus der *eigenen* Erfahrung bewertet. */
data class ZoneMood(
    val zoneId: Int,
    val x: Float,
    val y: Float,
    val area: Float,
    /** "Schnell am Ziel": 1 = alles wie immer, 0 = Fahrten dauern ein Vielfaches. */
    val speedSatisfaction: Double,
    /** "Mobilität": 1 = keine Bußgelder, 0 = man wird ständig zur Kasse gebeten. */
    val mobilitySatisfaction: Double,
) {
    val satisfaction: Double get() = SPEED_WEIGHT * speedSatisfaction + (1 - SPEED_WEIGHT) * mobilitySatisfaction
    val isUnhappy: Boolean get() = satisfaction < UNHAPPY_BELOW

    companion object {
        const val SPEED_WEIGHT = 0.6
        const val UNHAPPY_BELOW = 0.5
    }
}

/**
 * Die Bürger, zusammengefasst je Wohngebiet. Sie merken sich, wie viel länger ihre Fahrten dauern als
 * gewohnt und wie viel Bußgeld sie zahlen – beides klingt mit der Zeit wieder ab.
 */
internal class Citizens(
    zones: List<Zone>,
    private val rules: GameRules,
) {
    private val homes = zones.filter { it.kind == ZoneKind.RESIDENTIAL }
    private val indexOfZone = homes.withIndex().associate { (i, zone) -> zone.id to i }
    private val delayRatio = DoubleArray(homes.size) { 1.0 }
    private val fineSum = DoubleArray(homes.size)
    private val tripCount = DoubleArray(homes.size)

    fun tripFinished(trip: TripResult) {
        val i = indexOfZone[trip.homeZone] ?: return
        val ratio = trip.actualSeconds / max(trip.referenceSeconds, 1.0)
        delayRatio[i] = delayRatio[i] * (1 - RATIO_WEIGHT) + ratio * RATIO_WEIGHT
        tripCount[i] += 1.0
    }

    fun fined(
        homeZone: Int,
        euros: Int,
    ) {
        val i = indexOfZone[homeZone] ?: return
        fineSum[i] += euros.toDouble()
    }

    /** Erinnerung an Bußgelder verblasst (Halbwertszeit [GameRules.finePainHalfLifeHours]). */
    fun forget(hours: Double) {
        val factor = HALF.pow(hours / rules.finePainHalfLifeHours)
        for (i in homes.indices) {
            fineSum[i] *= factor
            tripCount[i] *= factor
        }
    }

    fun moods(argumentsCampaign: Boolean): List<ZoneMood> =
        homes.mapIndexed { i, zone ->
            val speed =
                1 -
                    (
                        (delayRatio[i] - rules.acceptableDelayRatio) /
                            (rules.unbearableDelayRatio - rules.acceptableDelayRatio)
                    ).coerceIn(0.0, 1.0)
            val finesPerTrip = fineSum[i] / max(tripCount[i], 1.0)
            val pain =
                finesPerTrip / rules.unbearableFinePerTrip * (if (argumentsCampaign) rules.argumentsPainFactor else 1.0)
            ZoneMood(zone.id, zone.x, zone.y, zone.area, speed, 1 - pain.coerceIn(0.0, 1.0))
        }

    /** Anteil der Bevölkerung (gewichtet nach Fläche des Wohngebiets), die unzufrieden ist. */
    fun unhappyShare(moods: List<ZoneMood>): Double {
        val total = moods.sumOf { it.area.toDouble() }
        return if (total > 0) moods.filter { it.isUnhappy }.sumOf { it.area.toDouble() } / total else 0.0
    }

    private companion object {
        /** Jede Fahrt verschiebt den gleitenden Mittelwert um diesen Anteil. */
        const val RATIO_WEIGHT = 0.05
        const val HALF = 0.5
    }
}
