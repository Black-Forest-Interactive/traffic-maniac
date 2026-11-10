package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.SpeedCamera

/**
 * Stufen für Geschwindigkeitsverstöße (angelehnt an einen Bußgeldkatalog, Beträge fiktiv).
 * Die kleinen Fische der ersten Stufe sind zahlreich – dort wird das Geld verdient.
 */
enum class FineTier(
    private val labelText: String,
    private val shortLabelText: String,
    /** Obergrenze der Überschreitung in km/h (einschließlich). */
    val maxExcessKmh: Double,
    /** Bußgeld in Euro. */
    val fine: Int,
    /** Punkte in der (fiktiven) Verkehrssünderkartei. */
    val points: Int,
    /** Fahrverbot in Spieltagen. */
    val drivingBanDays: Int,
) {
    UP_TO_10(translatable("bis 10 km/h"), "≤ 10", maxExcessKmh = 10.0, fine = 30, points = 0, drivingBanDays = 0),
    UP_TO_20(translatable("11–20 km/h"), "11–20", maxExcessKmh = 20.0, fine = 70, points = 0, drivingBanDays = 0),
    UP_TO_30(translatable("21–30 km/h"), "21–30", maxExcessKmh = 30.0, fine = 150, points = 1, drivingBanDays = 0),
    OVER_30(
        translatable("über 30 km/h"),
        "> 30",
        maxExcessKmh = Double.POSITIVE_INFINITY,
        fine = 400,
        points = 2,
        drivingBanDays = 2,
    ),

    /** Über Rot – Rotlichtblitzer; keine Überschreitung in km/h ([maxExcessKmh] NaN: [of] liefert sie nie). */
    RED_LIGHT(translatable("Rotlicht"), "Rot", maxExcessKmh = Double.NaN, fine = 90, points = 1, drivingBanDays = 0),
    RED_LIGHT_LATE(
        translatable("Rotlicht über 1 s"),
        "Rot > 1 s",
        maxExcessKmh = Double.NaN,
        fine = 200,
        points = 2,
        drivingBanDays = 1,
    ),
    ;

    val label: String get() = tr(labelText)
    val shortLabel: String get() = tr(shortLabelText)
    val isRedLight: Boolean get() = maxExcessKmh.isNaN()

    companion object {
        fun of(excessKmh: Double): FineTier = entries.first { excessKmh <= it.maxExcessKmh }

        /** Rotlicht: ab einer Sekunde Rot wird es teuer (und es gibt Fahrverbot). */
        fun ofRedLight(redSeconds: Double): FineTier = if (redSeconds > LATE_RED_SECONDS) RED_LIGHT_LATE else RED_LIGHT

        /** Die Stufen für zu schnelles Fahren (ohne Rotlicht). */
        val SPEED: List<FineTier> get() = entries.filter { !it.isRedLight }

        private const val LATE_RED_SECONDS = 1.0
    }
}

/** Statistik eines Blitzers: Anzahl und Einnahmen je Stufe sowie der "Rekord" für die Presse. */
data class CameraRecord(
    val cameraId: Int,
    val segment: Int,
    val streetName: String,
    val mobile: Boolean,
    val countPerTier: List<Int>,
    val revenuePerTier: List<Long>,
    val fastestMeasuredKmh: Double,
    val fastestLimitKmh: Int,
    /** Blitzeranhänger (umsetzbar). */
    val trailer: Boolean = false,
    /** Rotlichtblitzer (an der Haltelinie), auf Wunsch auch mit Tempomessung. */
    val redLight: Boolean = false,
    val measuresSpeed: Boolean = true,
    /** Anzeigetafel: zählt nur – [passed] Fahrzeuge, davon [speeding] zu schnell. */
    val display: Boolean = false,
    val passed: Int = 0,
    val speeding: Int = 0,
) {
    /** Anteil der zu Schnellen an einer Anzeigetafel (0..1). */
    val speedingShare: Double get() = if (passed == 0) 0.0 else speeding.toDouble() / passed

    val totalCount: Int get() = countPerTier.sum()
    val totalRevenue: Long get() = revenuePerTier.sum()
}

/** Veränderliche Zähler eines Blitzers; nach außen gibt es nur [CameraRecord]. */
internal class CameraCounter(
    val cameraId: Int,
    val segment: Int,
    val streetName: String,
    val mobile: Boolean = false,
    val trailer: Boolean = false,
    /** Gerät in der Simulation – für die Zählung der Anzeigetafel und die Art des Blitzers. */
    private val camera: SpeedCamera? = null,
) {
    /** Anzeigetafel – zählt nicht als Blitzer. */
    val isDisplay: Boolean get() = camera?.display == true

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
        if (!tier.isRedLight && measuredKmh - limitKmh > fastestKmh - fastestLimit) {
            fastestKmh = measuredKmh
            fastestLimit = limitKmh
        }
    }

    fun toRecord() =
        CameraRecord(
            cameraId,
            segment,
            streetName,
            mobile,
            counts.toList(),
            revenue.toList(),
            fastestKmh,
            fastestLimit,
            trailer,
            redLight = camera?.redLight == true,
            measuresSpeed = camera?.measuresSpeed != false,
            display = camera?.display == true,
            passed = camera?.passed ?: 0,
            speeding = camera?.speeding ?: 0,
        )
}
