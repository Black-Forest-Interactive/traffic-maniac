package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.AchievementStatus
import de.bfi.trafficmaniac.game.EventsState
import de.bfi.trafficmaniac.i18n.tr

private val PanelBackground = ComicTheme.paper
private val PanelText = ComicTheme.ink
private val Muted = ComicTheme.muted
private val Highlight = ComicTheme.orange
private const val CALENDAR_ROWS = 8

/** Spielkalender: was gerade los ist und was ansteht. */
@Composable
fun EventsPanel(
    events: EventsState,
    today: Int,
    modifier: Modifier = Modifier,
    /** Erster Spieltag des Szenarios (für "Tag n"). */
    firstDay: Int = 0,
    /** Klick auf ein Ereignis: die Karte zeigt die Strecke. */
    onShow: (Set<Int>) -> Unit = {},
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val heading = TextStyle(color = PanelText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(380.dp)
            .padding(12.dp)
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BasicText(tr("Kalender & Ereignisse"), style = title)
        BasicText(tr("Jetzt"), style = heading)
        if (events.active.isEmpty()) BasicText(tr("Alles ruhig."), style = muted)
        for (event in events.active) {
            BasicText(
                "▸ ${event.kind.label}: ${event.place} (" +
                    (if (event.since.isNotEmpty()) tr("seit {since}, ", "since" to event.since) else "") +
                    "${event.until})",
                style = text.copy(color = Highlight),
                modifier = Modifier.clickable { onShow(event.segments) },
            )
        }
        BasicText(tr("Geplant"), style = heading)
        if (events.calendar.isEmpty()) BasicText(tr("Keine Termine mehr in dieser Amtszeit."), style = muted)
        for (entry in events.calendar.take(CALENDAR_ROWS)) {
            val day =
                if (entry.day ==
                    today
                ) {
                    "heute"
                } else {
                    tr(
                        "{germanName} (Tag {firstDay})",
                        "germanName" to entry.weekday.germanName,
                        "firstDay" to entry.day - firstDay + 1,
                    )
                }
            BasicText(
                tr(
                    "{day} {startHour}–{endHour} Uhr · {kind}: {place}",
                    "day" to day,
                    "startHour" to entry.startHour,
                    "endHour" to entry.endHour,
                    "kind" to entry.kind.label,
                    "place" to entry.place,
                ),
                style = text,
                modifier = Modifier.clickable { onShow(entry.segments) },
            )
        }
        BasicText(
            tr(
                "Dauerstau-Tage in Folge: {jamStreak} – danach und am Wochenende kommen die Rad-Korsos. " +
                    "Spontandemos kommen bei hohem Bürgerzorn.",
                "jamStreak" to events.jamStreak,
            ),
            style = muted,
        )
        if (events.totals.isNotEmpty()) {
            BasicText(
                tr("Bisher: ") + events.totals.entries.joinToString { (kind, count) -> "$count× ${kind.label}" },
                style = muted,
            )
        }
    }
}

/** Achievements für Stadt-Extreme – zählen nicht für die Wertung. */
@Composable
fun AchievementsPanel(
    achievements: List<AchievementStatus>,
    modifier: Modifier = Modifier,
    firstDay: Int = 0,
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val unlocked = TextStyle(color = Highlight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val locked = TextStyle(color = Muted, fontSize = 12.sp)
    val description = TextStyle(color = Muted, fontSize = 10.sp)
    Column(
        modifier
            .width(380.dp)
            .padding(12.dp)
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(
            tr(
                "Achievements ({unlocked}/{achievements}) – zählen nicht für die Wertung",
                "unlocked" to achievements.count { it.unlocked },
                "achievements" to achievements.size,
            ),
            style = title,
        )
        for (status in achievements) {
            val day = status.unlockedDay?.let { tr(" · Tag {firstDay}", "firstDay" to it - firstDay + 1) } ?: ""
            BasicText(
                (if (status.unlocked) "★ " else "☆ ") + status.achievement.title + day,
                style = if (status.unlocked) unlocked else locked,
            )
            BasicText(status.achievement.description, style = description)
        }
    }
}
