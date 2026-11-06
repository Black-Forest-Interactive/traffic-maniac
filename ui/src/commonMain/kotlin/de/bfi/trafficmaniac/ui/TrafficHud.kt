package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.sim.EnvironmentSnapshot
import de.bfi.trafficmaniac.sim.Pollutant
import kotlin.math.roundToInt

private val HudBackground = Color(0xCC1D1F2E)
private val HudText = Color(0xFFF1F1F1)

/** Debug-Anzeige für M2: Uhr, Tempo, Fahrzeuge, Kennzahlen, Rechenzeit und Bildrate. */
@Composable
fun TrafficHud(
    frame: TrafficFrame,
    fps: Int,
    modifier: Modifier = Modifier,
) {
    val snapshot = frame.current
    val stats = snapshot.stats
    val time = snapshot.gameTime
    val tempo =
        when {
            frame.speed == 0 -> "Pause"
            frame.effectiveSpeed > frame.speed -> "${frame.speed}x (Nacht: ${frame.effectiveSpeed}x)"
            else -> "${frame.speed}x"
        }
    val vehicles = "Fahrzeuge ${stats.activeVehicles} (+${stats.pendingVehicles} wartend)"
    val tripMinutes = "%.1f".format(stats.meanTripMinutes)
    val day = "${time.weekday.germanName}, Tag ${time.day + 1}/${frame.game.termDays}"
    val lines =
        listOf(
            "$day · ${time.clockText} Uhr · Tempo $tempo",
            "Kasse ${formatEuro(frame.game.balance)} · Bußgelder ${formatEuro(frame.game.fineIncome)}",
            "Bürgerzorn ${frame.game.anger.roundToInt()} · Abwahlindex ${frame.game.recallIndex.roundToInt()}/100 · " +
                "Reputation ${frame.game.reputation.roundToInt()}",
            "$vehicles · Ø ${stats.meanSpeedKmh.roundToInt()} km/h",
            "Angekommen ${stats.completedTrips} (Ø $tripMinutes min) · aufgegeben ${stats.stuckRemovals}",
            environmentLine(snapshot.environment),
            commuteLine(frame),
            "Rechenzeit ${"%.2f".format(frame.stepMillis)} ms/Tick · $fps FPS",
            "Leertaste: Pause · 1–4: Tempo 1x/2x/4x/8x · nachts automatisch ×${GameRunner.NIGHT_BOOST}",
        )
    Column(
        modifier
            .padding(12.dp)
            .background(HudBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        for (line in lines) BasicText(line, style = TextStyle(color = HudText, fontSize = 13.sp))
    }
}

private fun environmentLine(environment: EnvironmentSnapshot): String {
    val totals = environment.totals
    val particulates = PARTICULATES.sumOf { totals[it] ?: 0.0 } / GRAMS_PER_KG
    val co2 = (totals[Pollutant.CO2] ?: 0.0) / GRAMS_PER_TONNE
    return "Sprit ${"%.0f".format(
        environment.totalFuelLiters,
    )} l · CO₂ ${"%.1f".format(co2)} t · Feinstaub ${"%.2f".format(particulates)} kg"
}

private fun commuteLine(frame: TrafficFrame): String {
    val yesterday = frame.game.commuteYesterday
    if (yesterday.trips == 0) return "Pendler: noch keine Tagesbilanz"
    return "Zu spät gestern: ${(yesterday.lateShare * PERCENT).roundToInt()} % · Schaden ${formatEuro(
        frame.game.commuteDamage,
    )}"
}

private val PARTICULATES = listOf(Pollutant.PM_EXHAUST, Pollutant.PM_TIRES, Pollutant.PM_BRAKES)
private const val GRAMS_PER_KG = 1_000.0
private const val GRAMS_PER_TONNE = 1_000_000.0
private const val PERCENT = 100
