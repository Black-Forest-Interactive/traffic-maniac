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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.NewspaperEdition
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.DayReport
import de.bfi.trafficmaniac.sim.GameTime

private val Ink = ComicTheme.ink
private val Bar = ComicTheme.red
private val BarBackground = ComicTheme.track
private const val CHART_GAP_FRACTION = 0.2f
private const val AXIS_STEP_HOURS = 6

/** Tagesabschluss: Kennzahlen des Tags, die Nachfragekurve als Balkendiagramm und die Zeitung vom nächsten Morgen. */
@Composable
fun DayReportOverlay(
    report: DayReport,
    edition: NewspaperEdition?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    /** Erster Spieltag des Szenarios. */
    firstDay: Int = 0,
) {
    Box(modifier.fillMaxSize().background(ComicTheme.scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(600.dp)
                .comicPanel(shadow = 8.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                tr(
                    "Tagesabschluss: {germanName}, Tag {firstDay}",
                    "germanName" to report.weekday.germanName,
                    "firstDay" to report.day - firstDay + 1,
                ),
                style = TextStyle(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            val text = TextStyle(color = Ink, fontSize = 15.sp)
            DayFigures(report, text)
            BasicText(
                tr("Fahrten je Stunde"),
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
            edition?.let { ComicNewspaper(it, Modifier.padding(top = 8.dp), firstDay) }
            BasicText(
                tr("Weiter zum nächsten Tag (Enter)"),
                style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .comicButton(active = true)
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Kennzahlen des Tags: Fahrten, Fahrzeit, Spitze. */
@Composable
private fun DayFigures(
    report: DayReport,
    text: TextStyle,
) {
    BasicText(
        tr(
            "Fahrten gestartet: {tripsStarted} · angekommen: {tripsCompleted}",
            "tripsStarted" to report.tripsStarted,
            "tripsCompleted" to report.tripsCompleted,
        ),
        style = text,
    )
    BasicText(
        tr(
            "Ø Fahrzeit: {meanTripMinutes} min · aufgegeben: {stuckRemovals}",
            "meanTripMinutes" to formatDecimal(report.meanTripMinutes),
            "stuckRemovals" to report.stuckRemovals,
        ),
        style = text,
    )
    BasicText(
        tr(
            "Spitze: {peakVehicles} Fahrzeuge gleichzeitig um {peakHour} Uhr",
            "peakVehicles" to report.peakVehicles,
            "peakHour" to report.peakHour,
        ),
        style = text,
    )
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
