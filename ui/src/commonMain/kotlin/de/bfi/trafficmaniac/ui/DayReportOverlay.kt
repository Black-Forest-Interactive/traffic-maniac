package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.NewspaperEdition
import de.bfi.trafficmaniac.sim.DayReport
import de.bfi.trafficmaniac.sim.GameTime

private val Scrim = Color(0x99000000)
private val Panel = Color(0xFFFFF8E7)
private val Ink = Color(0xFF1D1F2E)
private val Bar = Color(0xFFE63946)
private val BarBackground = Color(0xFFEDE3CC)
private val ButtonColor = Color(0xFF2A9D8F)
private val NewsPaper = Color(0xFFEDE6D6)
private const val CHART_GAP_FRACTION = 0.2f
private const val AXIS_STEP_HOURS = 6

/** Platzhalter für den Tagesabschluss: Kennzahlen des Tags und die Nachfragekurve als Balkendiagramm. */
@Composable
fun DayReportOverlay(
    report: DayReport,
    edition: NewspaperEdition?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                "Tagesabschluss: ${report.weekday.germanName}, Tag ${report.day + 1}",
                style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            val text = TextStyle(color = Ink, fontSize = 15.sp)
            BasicText("Fahrten gestartet: ${report.tripsStarted} · angekommen: ${report.tripsCompleted}", style = text)
            BasicText(
                "Ø Fahrzeit: ${"%.1f".format(report.meanTripMinutes)} min · aufgegeben: ${report.stuckRemovals}",
                style = text,
            )
            BasicText("Spitze: ${report.peakVehicles} Fahrzeuge gleichzeitig um ${report.peakHour} Uhr", style = text)
            BasicText(
                "Fahrten je Stunde",
                style = text.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp),
            )
            HourlyChart(report.tripsStartedPerHour, Modifier.fillMaxWidth().height(120.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (hour in 0..GameTime.HOURS_PER_DAY step AXIS_STEP_HOURS) {
                    BasicText(
                        "$hour",
                        style = text.copy(fontSize = 11.sp),
                    )
                }
            }
            edition?.let { Newspaper(it) }
            BasicText(
                "Weiter zum nächsten Tag (Enter)",
                style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .background(ButtonColor, RoundedCornerShape(8.dp))
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Die Lokalzeitung vom nächsten Morgen. */
@Composable
private fun Newspaper(edition: NewspaperEdition) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(NewsPaper, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(
            "PFORZHEIMER MORGENPOST",
            style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Black),
        )
        for (headline in edition.headlines) {
            BasicText("• $headline", style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun HourlyChart(
    values: IntArray,
    modifier: Modifier = Modifier,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier.background(BarBackground, RoundedCornerShape(4.dp))) {
        val slot = size.width / GameTime.HOURS_PER_DAY
        val barWidth = slot * (1 - CHART_GAP_FRACTION)
        values.forEachIndexed { hour, value ->
            val height = size.height * value / max
            drawRect(
                Bar,
                topLeft = Offset(hour * slot + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
            )
        }
    }
}
