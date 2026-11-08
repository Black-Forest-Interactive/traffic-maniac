package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameMessage
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.Divider
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(12.dp)
private val CardShadow = TmColors.Ink.copy(alpha = 0.18f)

private fun Modifier.card() =
    shadow(6.dp, CardShape, spotColor = CardShadow, ambientColor = CardShadow)
        .background(TmColors.Surface, CardShape)
        .border(1.dp, TmColors.Line, CardShape)
        .clip(CardShape)

/** Linke Spalte über der Karte: Verkehrslage (einklappbar) und die Meldungen. */
@Composable
internal fun HudFeed(
    frame: TrafficFrame,
    onShow: (MapPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(FEED_WIDTH.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TrafficStatus(frame)
        Messages(frame.game.messages, frame.game.maintenance.reports.size, onShow)
    }
}

@Composable
private fun TrafficStatus(frame: TrafficFrame) {
    var open by remember { mutableStateOf(false) }
    val stats = frame.current.stats
    Column(Modifier.fillMaxWidth().card()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                ) { open = !open }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(tr("Verkehrslage"), style = TmType.text(13.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            BasicText(
                tr(
                    "{vehicles} Kfz · Ø {speed} km/h",
                    "vehicles" to formatNumber(stats.activeVehicles.toLong()),
                    "speed" to stats.meanSpeedKmh.roundToInt(),
                ),
                style = TmType.text(12.sp, FontWeight.Medium, TmColors.Muted),
            )
            Icon(if (open) Icons.ChevronUp else Icons.ChevronDown, null, tint = TmColors.Muted, size = 16.dp)
        }
        if (open) {
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Figure(tr("Wartend"), formatNumber(stats.pendingVehicles.toLong()), Modifier.weight(1f))
                    Figure(tr("Angekommen"), formatNumber(stats.completedTrips.toLong()), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Figure(tr("Ø Fahrzeit"), "${formatDecimal(stats.meanTripMinutes)} min", Modifier.weight(1f))
                    Figure(tr("Aufgegeben"), formatNumber(stats.stuckRemovals.toLong()), Modifier.weight(1f))
                }
                for (line in listOf(
                    environmentLine(frame.current.environment),
                    commuteLine(frame),
                    incidentLine(frame.current),
                )) {
                    BasicText(line, style = TmType.caption)
                }
            }
        }
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        BasicText(label, style = TmType.caption)
        BasicText(value, style = TmType.mono(13.sp))
    }
}

/** Die letzten Meldungen, die neueste oben; mit Ort anklickbar – die Karte springt hin. */
@Composable
private fun Messages(
    messages: List<GameMessage>,
    openDamage: Int,
    onShow: (MapPlace) -> Unit,
) {
    Column(Modifier.fillMaxWidth().card()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(tr("Meldungen"), style = TmType.text(13.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            if (openDamage > 0) {
                Badge(tr("{count} Schäden offen", "count" to openDamage), tone = BadgeTone.DANGER)
            }
        }
        if (messages.isEmpty()) {
            Divider()
            BasicText(tr("Noch keine Meldungen."), style = TmType.small, modifier = Modifier.padding(14.dp))
        }
        for (message in messages.asReversed()) {
            Divider()
            MessageRow(message, onShow)
        }
    }
}

@Composable
private fun MessageRow(
    message: GameMessage,
    onShow: (MapPlace) -> Unit,
) {
    val place = message.place
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (place != null) Modifier.clickable(role = Role.Button) { onShow(place) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(if (place != null) TmColors.Primary else TmColors.Line, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(message.text, style = TmType.text(13.sp, FontWeight.SemiBold))
            BasicText(message.time.clockText, style = TmType.caption)
        }
        if (place != null) Icon(Icons.ChevronRight, tr("Auf Karte zeigen"), tint = TmColors.Muted, size = 16.dp)
    }
}

/** Entwickler-Anzeige (F3): Rechenzeit je Simulationsschritt und Bildrate. */
@Composable
internal fun DebugOverlay(
    frame: TrafficFrame,
    fps: Int,
    modifier: Modifier = Modifier,
) {
    BasicText(
        tr(
            "Rechenzeit {stepMillis} ms/Tick · {fps} FPS",
            "stepMillis" to formatDecimal(frame.stepMillis, 2),
            "fps" to fps,
        ) + " · " + tr("nachts automatisch ×{boost}", "boost" to GameRunner.NIGHT_BOOST),
        style = TmType.mono(11.sp, bold = false, color = TmColors.OnDark),
        modifier = modifier.background(TmColors.Ink.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(6.dp),
    )
}

private const val FEED_WIDTH = 270
