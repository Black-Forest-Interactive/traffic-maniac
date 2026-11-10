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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameMessage
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.MessageKind
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
        WeatherLine(frame)
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

/** Schnee auf einen Blick: ob es schneit, wie hoch er liegt, wie viele Räumfahrzeuge unterwegs sind. */
@Composable
private fun WeatherLine(frame: TrafficFrame) {
    val weather = frame.current.weather
    if (weather.snow.isEmpty()) return
    val covered = weather.snow.filter { it > 0f }
    val mean = if (covered.isEmpty()) 0.0 else covered.average()
    val text =
        tr(
            "{state} · Ø {cm} cm · {ploughs} Räumfahrzeuge",
            "state" to if (weather.snowing) tr("Es schneit") else tr("Schnee"),
            "cm" to formatDecimal(mean, 1),
            "ploughs" to weather.ploughs,
        )
    BasicText(
        text,
        style = TmType.text(12.sp, FontWeight.SemiBold, if (weather.snowing) TmColors.Primary else TmColors.Muted),
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
    )
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

/** Meldung, aufgeteilt in Was (Titel) und Wo bzw. Näheres (Unterzeile) – am Muster „Was: Wo“. */
private class MessageParts(
    val title: String,
    val detail: String?,
)

private fun parts(message: GameMessage): MessageParts {
    val colon = message.text.indexOf(": ")
    return if (colon in 1..MAX_TITLE_LENGTH) {
        MessageParts(message.text.substring(0, colon), message.text.substring(colon + 2))
    } else {
        MessageParts(message.text, null)
    }
}

/** Gleichartige Meldungen (gleicher Titel) zusammen, die neueste zuerst. */
private class MessageGroup(
    val title: String,
    val messages: List<GameMessage>,
) {
    val latest: GameMessage get() = messages.first()
}

private fun group(messages: List<GameMessage>): List<MessageGroup> =
    messages
        .asReversed()
        .groupBy { parts(it).title }
        .map { (title, items) -> MessageGroup(title, items) }

/**
 * Die Meldungen, die neueste oben; gleichartige gebündelt („3× Tempo 30“, aufklappbar). Höchstens fünf sichtbar,
 * der Rest hinter „Alle anzeigen“. Mit Ort anklickbar – die Karte springt hin.
 */
@Composable
private fun Messages(
    messages: List<GameMessage>,
    openDamage: Int,
    onShow: (MapPlace) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val groups = group(messages)
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
        if (groups.isEmpty()) {
            Divider()
            BasicText(tr("Noch keine Meldungen."), style = TmType.small, modifier = Modifier.padding(14.dp))
        }
        for (entry in if (showAll) groups else groups.take(VISIBLE_GROUPS)) {
            Divider()
            val open = expanded == entry.title
            GroupRow(entry, open, onShow) { expanded = if (open) null else entry.title }
            if (open) entry.messages.forEach { MessageRow(it, onShow, nested = true) }
        }
        if (groups.size > VISIBLE_GROUPS) {
            Divider()
            BasicText(
                if (showAll) tr("Weniger anzeigen") else tr("Alle anzeigen ({count})", "count" to groups.size),
                style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Primary),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { showAll = !showAll }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Eine Gruppe: einzeln wie eine Meldung, mehrfach als „3× Titel“ zum Aufklappen. */
@Composable
private fun GroupRow(
    group: MessageGroup,
    open: Boolean,
    onShow: (MapPlace) -> Unit,
    onToggle: () -> Unit,
) {
    if (group.messages.size == 1) return MessageRow(group.latest, onShow)
    val latest = group.latest
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KindDot(latest.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(
                "${group.messages.size}× ${group.title}",
                style = TmType.text(13.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(tr("zuletzt {time}", "time" to latest.time.clockText), style = TmType.caption)
        }
        Icon(if (open) Icons.ChevronUp else Icons.ChevronDown, null, tint = TmColors.Muted, size = 16.dp)
    }
}

@Composable
private fun MessageRow(
    message: GameMessage,
    onShow: (MapPlace) -> Unit,
    nested: Boolean = false,
) {
    val place = message.place
    val parts = parts(message)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (place != null) Modifier.clickable(role = Role.Button) { onShow(place) } else Modifier)
            .padding(start = if (nested) 32.dp else 14.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!nested) KindDot(message.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (!nested) {
                BasicText(
                    parts.title,
                    style = TmType.text(13.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(
                listOfNotNull(parts.detail, message.time.clockText).joinToString(" · "),
                style = TmType.caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (place != null) Icon(Icons.ChevronRight, tr("Auf Karte zeigen"), tint = TmColors.Muted, size = 16.dp)
    }
}

/** Farbe nach Art: Problem rot, eigene Maßnahme blau, Gutes grün, System grau. */
@Composable
private fun KindDot(kind: MessageKind) {
    val color =
        when (kind) {
            MessageKind.PROBLEM -> TmColors.Danger
            MessageKind.ACTION -> TmColors.Primary
            MessageKind.SUCCESS -> TmColors.Success
            MessageKind.SYSTEM -> TmColors.Muted
        }
    Box(Modifier.padding(top = 5.dp).size(8.dp).background(color, CircleShape))
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

/** Breite der linken Spalte über der Karte (Verkehrslage, Meldungen). */
internal const val FEED_WIDTH = 270
private const val VISIBLE_GROUPS = 5

/** Längerer Text vor dem ersten Doppelpunkt ist kein Titel, sondern ein Satz. */
private const val MAX_TITLE_LENGTH = 40
