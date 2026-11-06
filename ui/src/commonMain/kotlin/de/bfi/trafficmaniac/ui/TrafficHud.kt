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
import de.bfi.trafficmaniac.game.TrafficFrame
import kotlin.math.roundToInt

private val HudBackground = Color(0xCC1D1F2E)
private val HudText = Color(0xFFF1F1F1)
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** Debug-Anzeige für M2: Uhr, Tempo, Fahrzeuge, Kennzahlen, Rechenzeit und Bildrate. */
@Composable
fun TrafficHud(
    frame: TrafficFrame,
    fps: Int,
    modifier: Modifier = Modifier,
) {
    val snapshot = frame.current
    val stats = snapshot.stats
    val tempo = if (frame.speed == 0) "Pause" else "${frame.speed}x"
    val vehicles = "Fahrzeuge ${stats.activeVehicles} (+${stats.pendingVehicles} wartend)"
    val tripMinutes = "%.1f".format(stats.meanTripMinutes)
    val lines =
        listOf(
            "Simulationszeit ${clock(snapshot.time)} · Tempo $tempo",
            "$vehicles · Ø ${stats.meanSpeedKmh.roundToInt()} km/h",
            "Angekommen ${stats.completedTrips} (Ø $tripMinutes min) · aufgegeben ${stats.stuckRemovals}",
            "Rechenzeit ${"%.2f".format(frame.stepMillis)} ms/Tick · $fps FPS",
            "Leertaste: Pause · 1–4: Tempo 1x/2x/4x/8x",
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

private fun clock(seconds: Double): String {
    val total = seconds.toInt()
    val hours = total / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val secs = total % SECONDS_PER_MINUTE
    return "%02d:%02d:%02d".format(hours, minutes, secs)
}
