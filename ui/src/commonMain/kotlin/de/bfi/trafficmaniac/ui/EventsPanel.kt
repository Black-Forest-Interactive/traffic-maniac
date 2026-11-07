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
        BasicText("Kalender & Ereignisse", style = title)
        BasicText("Jetzt", style = heading)
        if (events.active.isEmpty()) BasicText("Alles ruhig.", style = muted)
        for (event in events.active) {
            BasicText(
                "▸ ${event.kind.label}: ${event.place} (" +
                    (if (event.since.isNotEmpty()) "seit ${event.since}, " else "") + "${event.until})",
                style = text.copy(color = Highlight),
                modifier = Modifier.clickable { onShow(event.segments) },
            )
        }
        BasicText("Geplant", style = heading)
        if (events.calendar.isEmpty()) BasicText("Keine Termine mehr in dieser Amtszeit.", style = muted)
        for (entry in events.calendar.take(CALENDAR_ROWS)) {
            val day = if (entry.day == today) "heute" else "${entry.weekday.germanName} (Tag ${entry.day + 1})"
            BasicText(
                "$day ${entry.startHour}–${entry.endHour} Uhr · ${entry.kind.label}: ${entry.place}",
                style = text,
                modifier = Modifier.clickable { onShow(entry.segments) },
            )
        }
        BasicText(
            "Dauerstau-Tage in Folge: ${events.jamStreak} – danach und am Wochenende kommen die Rad-Korsos. " +
                "Spontandemos kommen bei hohem Bürgerzorn.",
            style = muted,
        )
        if (events.totals.isNotEmpty()) {
            BasicText(
                "Bisher: " + events.totals.entries.joinToString { (kind, count) -> "$count× ${kind.label}" },
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
            "Achievements (${achievements.count { it.unlocked }}/${achievements.size}) – zählen nicht für die Wertung",
            style = title,
        )
        for (status in achievements) {
            val day = status.unlockedDay?.let { " · Tag ${it + 1}" } ?: ""
            BasicText(
                (if (status.unlocked) "★ " else "☆ ") + status.achievement.title + day,
                style = if (status.unlocked) unlocked else locked,
            )
            BasicText(status.achievement.description, style = description)
        }
    }
}
