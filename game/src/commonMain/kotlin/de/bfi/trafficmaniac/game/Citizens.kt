package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.Zone
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.EnvironmentSnapshot
import de.bfi.trafficmaniac.sim.Pollutant
import de.bfi.trafficmaniac.sim.TripResult
import kotlin.math.max
import kotlin.math.pow

/** Haushaltstypen gewichten die Bedürfnisse unterschiedlich. */
enum class HouseholdType(
    private val labelText: String,
    val speedWeight: Double,
    val mobilityWeight: Double,
    val quietWeight: Double,
    val airWeight: Double,
) {
    COMMUTER(translatable("Pendler"), speedWeight = 0.5, mobilityWeight = 0.3, quietWeight = 0.1, airWeight = 0.1),
    FAMILY(translatable("Familien"), speedWeight = 0.25, mobilityWeight = 0.2, quietWeight = 0.25, airWeight = 0.3),
    RETIREE(translatable("Rentner"), speedWeight = 0.1, mobilityWeight = 0.2, quietWeight = 0.4, airWeight = 0.3),
    ;

    val label: String get() = tr(labelText)
}

/** Stimmung eines Wohngebiets. Alle Bedürfnisse werden nur aus der *eigenen* Erfahrung bewertet. */
data class ZoneMood(
    val zoneId: Int,
    val x: Float,
    val y: Float,
    val area: Float,
    /** "Schnell am Ziel": 1 = alles wie immer, 0 = Fahrten dauern ein Vielfaches. */
    val speedSatisfaction: Double,
    /** "Mobilität": 1 = keine Bußgelder, 0 = man wird ständig zur Kasse gebeten. */
    val mobilitySatisfaction: Double,
    /** "Ruhe": Lärm vor der eigenen Haustür. */
    val quietSatisfaction: Double,
    /** "Frische Luft": Abgase und Feinstaub in der Nachbarschaft. */
    val airSatisfaction: Double,
    /** Anteile der Haushaltstypen im Gebiet. */
    val households: Map<HouseholdType, Double>,
) {
    val satisfaction: Double
        get() =
            households.entries.sumOf { (type, share) ->
                share *
                    (
                        type.speedWeight * speedSatisfaction + type.mobilityWeight * mobilitySatisfaction +
                            type.quietWeight * quietSatisfaction + type.airWeight * airSatisfaction
                    )
            }

    val isUnhappy: Boolean get() = satisfaction < UNHAPPY_BELOW

    companion object {
        const val UNHAPPY_BELOW = 0.55
    }
}

/** Pünktlichkeit der Pendler an einem Tag. */
data class CommuteStats(
    val trips: Int = 0,
    val late: Int = 0,
    val lostMinutes: Double = 0.0,
) {
    val lateShare: Double get() = if (trips > 0) late.toDouble() / trips else 0.0
}

/**
 * Die Bürger, zusammengefasst je Wohngebiet. Sie merken sich, wie viel länger ihre Fahrten dauern als
 * gewohnt und wie viel Bußgeld sie zahlen – beides klingt mit der Zeit wieder ab. Lärm und Luft kommen
 * aus dem Umweltraster rund um das Wohngebiet.
 */
internal class Citizens(
    zones: List<Zone>,
    private val rules: GameRules,
) {
    private val homes = zones.filter { it.kind == ZoneKind.RESIDENTIAL }
    private val indexOfZone = homes.withIndex().associate { (i, zone) -> zone.id to i }
    private val households = homes.map { householdMix(it.id) }
    private val delayRatio = DoubleArray(homes.size) { 1.0 }
    private val fineSum = DoubleArray(homes.size)
    private val tripCount = DoubleArray(homes.size)
    var commuteToday = CommuteStats()
        private set

    fun tripFinished(trip: TripResult) {
        val i = indexOfZone[trip.homeZone] ?: return
        val ratio = trip.actualSeconds / max(trip.referenceSeconds, 1.0)
        delayRatio[i] = delayRatio[i] * (1 - RATIO_WEIGHT) + ratio * RATIO_WEIGHT
        tripCount[i] += 1.0
        // Zu spät ist, wer deutlich länger braucht, als er eingeplant hat
        val planned = trip.referenceSeconds * rules.plannedTimeFactor
        val late = trip.actualSeconds > planned
        val lost = if (late) (trip.actualSeconds - planned) / SECONDS_PER_MINUTE else 0.0
        commuteToday =
            CommuteStats(
                commuteToday.trips + 1,
                commuteToday.late + if (late) 1 else 0,
                commuteToday.lostMinutes + lost,
            )
    }

    fun fined(
        homeZone: Int,
        euros: Int,
    ) {
        val i = indexOfZone[homeZone] ?: return
        fineSum[i] += euros.toDouble()
    }

    /** Führerschein weg: wiegt im Wohngebiet wie ein großes Bußgeld. */
    fun lostLicense(
        homeZone: Int,
        pain: Double,
    ) {
        val i = indexOfZone[homeZone] ?: return
        fineSum[i] += pain
    }

    /** Erinnerung an Bußgelder verblasst (Halbwertszeit [GameRules.finePainHalfLifeHours]). */
    fun forget(hours: Double) {
        val factor = HALF.pow(hours / rules.finePainHalfLifeHours)
        for (i in homes.indices) {
            fineSum[i] *= factor
            tripCount[i] *= factor
        }
    }

    fun startNewDay(): CommuteStats = commuteToday.also { commuteToday = CommuteStats() }

    fun moods(
        argumentsCampaign: Boolean,
        environment: EnvironmentSnapshot? = null,
    ): List<ZoneMood> =
        homes.mapIndexed { i, zone ->
            val speed = 1 - scale(delayRatio[i], rules.acceptableDelayRatio, rules.unbearableDelayRatio)
            val finesPerTrip = fineSum[i] / max(tripCount[i], 1.0)
            val pain =
                finesPerTrip / rules.unbearableFinePerTrip * (if (argumentsCampaign) rules.argumentsPainFactor else 1.0)
            val noise =
                environment?.let {
                    EnvironmentSnapshot.energyToDb(
                        it.neighborhood(Pollutant.NOISE, zone.x, zone.y, NEIGHBORHOOD),
                    )
                }
                    ?: 0f
            val dirt =
                environment?.let { env ->
                    AIR_POLLUTANTS.sumOf { env.neighborhood(it, zone.x, zone.y, NEIGHBORHOOD).toDouble() }
                }
                    ?: 0.0
            ZoneMood(
                zoneId = zone.id,
                x = zone.x,
                y = zone.y,
                area = zone.area,
                speedSatisfaction = speed,
                mobilitySatisfaction = 1 - pain.coerceIn(0.0, 1.0),
                quietSatisfaction = 1 - scale(noise.toDouble(), rules.quietGoodDb, rules.quietBadDb),
                airSatisfaction = 1 - scale(dirt, rules.airGoodGrams, rules.airBadGrams),
                households = households[i],
            )
        }

    /** Anteil der Bevölkerung (gewichtet nach Fläche des Wohngebiets), die unzufrieden ist. */
    fun unhappyShare(moods: List<ZoneMood>): Double {
        val total = moods.sumOf { it.area.toDouble() }
        return if (total > 0) moods.filter { it.isUnhappy }.sumOf { it.area.toDouble() } / total else 0.0
    }

    /** 0 bis zum Wert [good], 1 ab [bad], dazwischen linear. */
    private fun scale(
        value: Double,
        good: Double,
        bad: Double,
    ) = ((value - good) / (bad - good)).coerceIn(0.0, 1.0)

    private companion object {
        /** Jede Fahrt verschiebt den gleitenden Mittelwert um diesen Anteil. */
        const val RATIO_WEIGHT = 0.05
        const val HALF = 0.5
        const val SECONDS_PER_MINUTE = 60.0

        /** Nachbarschaft: 5 × 5 Zellen, also rund 250 m. */
        const val NEIGHBORHOOD = 2
        val AIR_POLLUTANTS = listOf(Pollutant.NOX, Pollutant.PM_EXHAUST, Pollutant.PM_TIRES, Pollutant.PM_BRAKES)

        /** Feste, aber je Gebiet unterschiedliche Mischung: grob halb Pendler, ein Drittel Familien, Rest Rentner. */
        fun householdMix(zoneId: Int): Map<HouseholdType, Double> {
            val wobble = ((zoneId * GOLDEN_RATIO_FRACTION) % 1.0 - HALF) * WOBBLE
            val commuters = BASE_COMMUTERS + wobble
            val families = BASE_FAMILIES - wobble / 2
            return mapOf(
                HouseholdType.COMMUTER to commuters,
                HouseholdType.FAMILY to families,
                HouseholdType.RETIREE to 1 - commuters - families,
            )
        }

        const val GOLDEN_RATIO_FRACTION = 0.6180339887
        const val WOBBLE = 0.3
        const val BASE_COMMUTERS = 0.5
        const val BASE_FAMILIES = 0.3
    }
}
