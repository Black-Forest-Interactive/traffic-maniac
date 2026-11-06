package de.bfi.trafficmaniac.game

/**
 * Stufen für Geschwindigkeitsverstöße (angelehnt an einen Bußgeldkatalog, Beträge fiktiv).
 * Die kleinen Fische der ersten Stufe sind zahlreich – dort wird das Geld verdient.
 */
enum class FineTier(
    val label: String,
    val shortLabel: String,
    /** Obergrenze der Überschreitung in km/h (einschließlich). */
    val maxExcessKmh: Double,
    /** Bußgeld in Euro. */
    val fine: Int,
) {
    UP_TO_10("bis 10 km/h", "≤ 10", maxExcessKmh = 10.0, fine = 30),
    UP_TO_20("11–20 km/h", "11–20", maxExcessKmh = 20.0, fine = 70),
    UP_TO_30("21–30 km/h", "21–30", maxExcessKmh = 30.0, fine = 150),
    OVER_30("über 30 km/h", "> 30", maxExcessKmh = Double.POSITIVE_INFINITY, fine = 400),
    ;

    companion object {
        fun of(excessKmh: Double): FineTier = entries.first { excessKmh <= it.maxExcessKmh }
    }
}

/** Statistik eines Blitzers: Anzahl und Einnahmen je Stufe sowie der "Rekord" für die Presse. */
data class CameraRecord(
    val cameraId: Int,
    val segment: Int,
    val streetName: String,
    val countPerTier: List<Int>,
    val revenuePerTier: List<Long>,
    val fastestMeasuredKmh: Double,
    val fastestLimitKmh: Int,
) {
    val totalCount: Int get() = countPerTier.sum()
    val totalRevenue: Long get() = revenuePerTier.sum()
}

/** Veränderliche Zähler eines Blitzers; nach außen gibt es nur [CameraRecord]. */
internal class CameraCounter(
    val cameraId: Int,
    val segment: Int,
    val streetName: String,
) {
    private val counts = IntArray(FineTier.entries.size)
    private val revenue = LongArray(FineTier.entries.size)
    private var fastestKmh = 0.0
    private var fastestLimit = 0

    fun record(
        tier: FineTier,
        measuredKmh: Double,
        limitKmh: Int,
    ) {
        counts[tier.ordinal]++
        revenue[tier.ordinal] += tier.fine.toLong()
        if (measuredKmh - limitKmh > fastestKmh - fastestLimit) {
            fastestKmh = measuredKmh
            fastestLimit = limitKmh
        }
    }

    fun toRecord() =
        CameraRecord(cameraId, segment, streetName, counts.toList(), revenue.toList(), fastestKmh, fastestLimit)
}
