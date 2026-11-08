package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.AchievementStatus
import de.bfi.trafficmaniac.game.CalendarEntry
import de.bfi.trafficmaniac.game.EventsState
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection

private val RowShape = RoundedCornerShape(12.dp)

/** Spielkalender: was gerade los ist, was ansteht und was die Korsos und Demos antreibt. */
@Composable
fun EventsPanel(
    events: EventsState,
    today: Int,
    modifier: Modifier = Modifier,
    /** Erster Spieltag des Szenarios (für "Tag n"). */
    firstDay: Int = 0,
    onClose: (() -> Unit)? = null,
    /** Klick auf ein Ereignis: die Karte zeigt die Strecke. */
    onShow: (Set<Int>) -> Unit = {},
) {
    PanelScaffold(tr("Bericht"), tr("Kalender & Ereignisse"), modifier, onClose, floating = false) {
        PanelSection(title = tr("Jetzt")) {
            if (events.active.isEmpty()) BasicText(tr("Alles ruhig."), style = TmType.small)
            for (event in events.active) {
                val since = if (event.since.isNotEmpty()) tr("seit {since}, ", "since" to event.since) else ""
                EventRow(event.kind.label, event.place, "$since${event.until}", event.progress) {
                    onShow(event.segments)
                }
            }
        }
        PanelSection(title = tr("Geplant")) {
            if (events.calendar.isEmpty()) BasicText(tr("Keine Termine mehr in dieser Amtszeit."), style = TmType.small)
            for (entry in events.calendar.take(CALENDAR_ROWS)) {
                EventRow(entry.kind.label, entry.place, whenText(entry, today, firstDay), progress = null) {
                    onShow(entry.segments)
                }
            }
        }
        PanelSection(divider = false) {
            BasicText(
                tr(
                    "Dauerstau-Tage in Folge: {jamStreak} – danach und am Wochenende kommen die Rad-Korsos. " +
                        "Spontandemos kommen bei hohem Bürgerzorn.",
                    "jamStreak" to events.jamStreak,
                ),
                style = TmType.small,
            )
            if (events.totals.isNotEmpty()) {
                BasicText(
                    tr("Bisher: ") + events.totals.entries.joinToString { (kind, count) -> "$count× ${kind.label}" },
                    style = TmType.small,
                )
            }
        }
    }
}

private fun whenText(
    entry: CalendarEntry,
    today: Int,
    firstDay: Int,
): String {
    val day =
        if (entry.day == today) {
            tr("heute")
        } else {
            tr(
                "{germanName} (Tag {firstDay})",
                "germanName" to entry.weekday.germanName,
                "firstDay" to entry.day - firstDay + 1,
            )
        }
    return tr("{day}, {start}–{end} Uhr", "day" to day, "start" to entry.startHour, "end" to entry.endHour)
}

/** Ereignis als anklickbare Zeile; laufende ([progress] gesetzt) mit oranger Seitenleiste und Fortschrittsbalken. */
@Composable
private fun EventRow(
    kind: String,
    place: String,
    time: String,
    progress: Float?,
    onClick: () -> Unit,
) {
    val live = progress != null
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RowShape)
            .border(1.dp, TmColors.Line, RowShape)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(if (live) TmColors.Warn else TmColors.Primary))
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText("$kind · $place", style = TmType.text(13.sp, FontWeight.SemiBold))
            BasicText(time, style = TmType.caption)
            progress?.let {
                Meter(
                    it,
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    height = 5.dp,
                    color = TmColors.Warn,
                )
            }
        }
        Icon(
            Icons.ChevronRight,
            tr("Auf Karte zeigen"),
            Modifier.padding(end = 10.dp),
            tint = TmColors.Muted,
            size = 16.dp,
        )
    }
}

/** Erfolge für Stadt-Extreme – zählen nicht für die Wertung. Zwei Spalten, gesperrte mit Schloss. */
@Composable
fun AchievementsPanel(
    achievements: List<AchievementStatus>,
    modifier: Modifier = Modifier,
    firstDay: Int = 0,
    onClose: (() -> Unit)? = null,
) {
    PanelScaffold(
        tr("Bericht"),
        tr("Erfolge"),
        modifier,
        onClose,
        floating = false,
        headerExtra = {
            Badge("${achievements.count { it.unlocked }}/${achievements.size}", tone = BadgeTone.INFO)
        },
    ) {
        PanelSection(divider = false) {
            BasicText(tr("Zählen nicht für die Wertung – aber für den Ruhm."), style = TmType.small)
            for (pair in achievements.sortedBy { !it.unlocked }.chunked(2)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (status in pair) AchievementTile(status, firstDay, Modifier.weight(1f))
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AchievementTile(
    status: AchievementStatus,
    firstDay: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(if (status.unlocked) TmColors.Surface else TmColors.Ground, RowShape)
            .border(1.dp, if (status.unlocked) TmColors.Line else TmColors.LineSoft, RowShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status.unlocked) {
            Box(Modifier.size(32.dp).background(TmColors.Active, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Award, null, tint = TmColors.Ink, size = 18.dp)
            }
        } else {
            LockedSeal()
        }
        BasicText(
            status.achievement.title,
            style = TmType.text(13.sp, FontWeight.Bold, if (status.unlocked) TmColors.Ink else TmColors.Muted),
        )
        BasicText(status.achievement.description, style = TmType.caption)
        status.unlockedDay?.let {
            BasicText(
                tr("Tag {day}", "day" to it - firstDay + 1),
                style = TmType.mono(11.sp, bold = false, color = TmColors.Success),
            )
        }
    }
}

/** Gestrichelter Kreis mit Schloss für noch gesperrte Erfolge. */
@Composable
private fun LockedSeal() {
    Box(
        Modifier.size(32.dp).drawBehind {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            drawCircle(
                TmColors.Muted,
                size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(1.5.dp.toPx(), pathEffect = dash),
            )
        },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Lock, tr("Gesperrt"), tint = TmColors.Muted, size = 15.dp) }
}

private const val CALENDAR_ROWS = 8
