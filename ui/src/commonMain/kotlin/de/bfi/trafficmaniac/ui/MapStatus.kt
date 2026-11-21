package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import kotlin.math.roundToInt

private val BoxShape = RoundedCornerShape(12.dp)
private val BoxShadow = TmColors.Ink.copy(alpha = 0.25f)

/**
 * Oben rechts auf der Karte: die Lage in einer Zeile (Kfz · Ø km/h · Staus, bei Schnee eine zweite) und darunter der
 * Posteingang-Hinweis – je Typ eine Zeile mit Farbpunkt. Ein Klick öffnet den Posteingang.
 */
@Composable
internal fun MapStatus(
    frame: TrafficFrame,
    onInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(STATUS_WIDTH.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SituationLine(frame)
        InboxHint(frame, onInbox)
    }
}

@Composable
private fun SituationLine(frame: TrafficFrame) {
    val stats = frame.current.stats
    // Stau: wenig Fluss und gerade Autos darauf (ohne Verkehr ist die Fahrzeit-Schätzung nur veraltet)
    val flow = frame.current.segmentFlow
    val volume = frame.current.segmentVolume
    val jams = flow.indices.count { flow[it] < JAM_FLOW && volume.getOrElse(it) { 0f } > JAM_VOLUME }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, BoxShape, spotColor = BoxShadow)
            .background(TmColors.Ink, BoxShape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Figure(formatNumber(stats.activeVehicles.toLong()), tr("Kfz"))
            Figure("Ø ${stats.meanSpeedKmh.roundToInt()}", tr("km/h"))
            Figure("${jams / JAM_SEGMENTS}", tr("Staus"))
        }
        val weather = frame.current.weather
        if (weather.snow.isNotEmpty()) {
            val covered = weather.snow.filter { it > 0f }
            BasicText(
                tr(
                    "{state} · Ø {cm} cm · {ploughs} Räumfahrzeuge",
                    "state" to if (weather.snowing) tr("Es schneit") else tr("Schnee"),
                    "cm" to formatDecimal(if (covered.isEmpty()) 0.0 else covered.average(), 1),
                    "ploughs" to weather.ploughs,
                ),
                style = TmType.text(11.sp, color = TmColors.OnDarkMuted),
            )
        }
    }
}

@Composable
private fun Figure(
    value: String,
    unit: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        BasicText(value, style = TmType.mono(14.sp, color = TmColors.OnDark))
        BasicText(unit, style = TmType.text(11.sp, color = TmColors.OnDarkMuted))
    }
}

@Composable
private fun InboxHint(
    frame: TrafficFrame,
    onInbox: () -> Unit,
) {
    val game = frame.game
    val incidents = frame.current.incidents
    val unsecured = game.maintenance.reports.count { it.status in UNSECURED_DAMAGE }
    val lines =
        buildList {
            incidents.firstOrNull()?.let {
                val text =
                    if (incidents.size ==
                        1
                    ) {
                        tr("1 Einsatz · {kind}", "kind" to it.kind.label)
                    } else {
                        tr(
                            "{count} Einsätze",
                            "count" to incidents.size,
                        )
                    }
                add(text to TmColors.Danger)
            }
            game.assignments.maxOfOrNull { it.reward }?.let {
                add(
                    tr(
                        "{count} Aufträge · bis zu +{euro}",
                        "count" to game.assignments.size,
                        "euro" to formatEuro(it),
                    ) to
                        TmColors.Primary,
                )
            }
            if (unsecured > 0) add(tr("{count} Schäden ungesichert", "count" to unsecured) to TmColors.Warn)
        }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, BoxShape, spotColor = BoxShadow)
            .clip(BoxShape)
            .background(TmColors.Surface)
            .clickable(role = Role.Button, onClick = onInbox)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(tr("Posteingang"), style = TmType.text(13.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            BasicText(tr("öffnen · I"), style = TmType.text(11.sp, color = TmColors.Primary))
        }
        if (lines.isEmpty()) BasicText(tr("Nichts Dringendes."), style = TmType.caption)
        for ((text, color) in lines) HintLine(text, color)
    }
}

@Composable
private fun HintLine(
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        BasicText(text, style = TmType.text(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val STATUS_WIDTH = 300

/** Abschnitte mit weniger Fluss gelten als Stau … */
private const val JAM_FLOW = 0.25f

/** … und so viele davon zählen als ein Stau (ein Stau zieht sich über mehrere Abschnitte). */
private const val JAM_SEGMENTS = 5

/** Kfz/Std., ab denen ein langsamer Abschnitt als Stau zählt. */
private const val JAM_VOLUME = 60f
