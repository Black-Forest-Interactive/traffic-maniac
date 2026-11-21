package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.CalendarEntry
import de.bfi.trafficmaniac.game.EventsState
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.Weekday
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.PageMode
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(14.dp)

private fun Modifier.card() = clip(CardShape).background(TmColors.Surface).border(1.dp, TmColors.LineSoft, CardShape)

/** Störungen kommen ungeplant (Demo, Korso, Schwertransport); alles andere steht vorher im Kalender. */
private val DISRUPTIONS = setOf(EventKind.DEMO, EventKind.BIKE_CORSO, EventKind.HEAVY_TRANSPORT)

/**
 * Kalender als Seite (Entwurf 2, `V2-Kalender`): der Wochenplan Mo–So, 6–24 Uhr (vergangene Tage blass, heute
 * hervorgehoben, Ereignisse als Blöcke – geplant blau, Störungen rot), rechts die kommenden Termine und „Was kann
 * passieren?“, unten die Chronik.
 */
@Composable
internal fun EventsPage(
    game: GameState,
    today: Int,
    nowHour: Int,
    rules: GameRules,
    page: PageMode,
    onShow: (Set<Int>) -> Unit,
) {
    val events = game.events
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(page.eyebrow ?: tr("Bericht"), tr("Kalender & Ereignisse"), page.onMap)
        page.kpis?.let { KpiRow(it) }
        // Links Wochenplan und „Was kann passieren?“, rechts die Liste der Termine und die Chronik
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WeekCard(events, today, nowHour, game)
                Card(tr("Was kann passieren?"), Modifier.fillMaxWidth()) { Risks(events, rules) }
            }
            Column(Modifier.width(SIDE_WIDTH.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(tr("Geplant"), Modifier.fillMaxWidth()) { Upcoming(events, game.firstDay, onShow) }
                Card(tr("Chronik"), Modifier.fillMaxWidth()) { Chronicle(events, game.firstDay, onShow) }
            }
        }
    }
}

@Composable
private fun Card(
    title: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(title, style = TmType.text(16.sp, FontWeight.Bold))
        content()
    }
}

/** Wochenplan mit Umschalter „‹ Woche 1 · Woche 2 ›“ – anfangs die Woche von heute. */
@Composable
private fun WeekCard(
    events: EventsState,
    today: Int,
    nowHour: Int,
    game: GameState,
) {
    val lastDay = game.firstDay + game.termDays - 1
    val firstWeek = weekStart(game.firstDay)
    val weeks = (weekStart(lastDay) - firstWeek) / DAYS + 1
    var week by remember { mutableStateOf((weekStart(today) - firstWeek) / DAYS) }
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(tr("Wochenplan"), style = TmType.text(16.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            Arrow("‹", enabled = week > 0) { week-- }
            BasicText(
                tr("Woche {week} von {weeks}", "week" to week + 1, "weeks" to weeks),
                style = TmType.text(13.sp, FontWeight.SemiBold),
            )
            Arrow("›", enabled = week < weeks - 1) { week++ }
        }
        WeekPlan(events, firstWeek + week * DAYS, today, nowHour, lastDay)
    }
}

/** Pfeil zum Blättern; am Rand blass statt eines grauen Knopfs. */
@Composable
private fun Arrow(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        text,
        style = TmType.text(18.sp, FontWeight.Bold, if (enabled) TmColors.Primary else TmColors.Line),
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(6.dp),
                ).clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** Montag der Woche von [day] (der Spieltag 0 ist ein Montag, siehe [de.bfi.trafficmaniac.sim.GameTime.weekday]). */
private fun weekStart(day: Int) = day - day.mod(DAYS)

/** Raster einer Woche: je Tag eine Spalte, 6–24 Uhr; Termine und Störungen als Blöcke. */
@Composable
private fun WeekPlan(
    events: EventsState,
    weekStart: Int,
    today: Int,
    nowHour: Int,
    lastDay: Int,
) {
    val entries = events.calendar + events.chronicle
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(HOUR_LABEL.dp))
        for (offset in 0 until DAYS) {
            val isToday = weekStart + offset == today
            val color = if (isToday) TmColors.Primary else TmColors.Muted
            BasicText(
                Weekday.entries[offset].germanName.take(2) + if (isToday) " · " + tr("heute") else "",
                style = TmType.text(12.sp, FontWeight.SemiBold, color).copy(textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(Modifier.fillMaxWidth().height((HOURS * HOUR_HEIGHT).dp)) {
        Column(Modifier.width(HOUR_LABEL.dp)) {
            for (hour in FIRST_HOUR until LAST_HOUR step LABEL_STEP) {
                BasicText("$hour", style = TmType.caption, modifier = Modifier.height((LABEL_STEP * HOUR_HEIGHT).dp))
            }
        }
        for (offset in 0 until DAYS) {
            val day = weekStart + offset
            DayColumn(day, today, nowHour, after = day > lastDay, entries.filter { it.day == day })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendBox(TmColors.Primary, tr("geplant"))
        LegendBox(TmColors.Danger, tr("Störung"))
        LegendBox(TmColors.Active, tr("jetzt"))
        LegendBox(TmColors.Ground, tr("vergangen"))
        LegendBox(TmColors.Line, tr("nach Amtsende (schraffiert)"))
    }
}

/** Ein Tag: vergangen blass, heute blau hinterlegt mit Linie für „jetzt“, kommend weiß, nach Amtsende schraffiert. */
@Composable
private fun RowScope.DayColumn(
    day: Int,
    today: Int,
    nowHour: Int,
    after: Boolean,
    entries: List<CalendarEntry>,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .alpha(if (day < today) PAST_ALPHA else 1f)
            .clip(shape)
            .background(
                when {
                    day == today -> TmColors.PrimaryTint
                    day < today -> TmColors.Ground
                    else -> TmColors.Surface
                },
            ).border(1.dp, TmColors.LineSoft, shape)
            .drawBehind {
                // Dünne Stundenlinien alle 3 Stunden, nach Amtsende schraffiert
                val step = LABEL_STEP * HOUR_HEIGHT.dp.toPx()
                var y = step
                while (y < size.height) {
                    drawLine(TmColors.LineSoft, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y += step
                }
                if (after) hatch()
            },
    ) {
        for (entry in entries) Block(entry)
        if (day == today) {
            val hour = nowHour.coerceIn(FIRST_HOUR, LAST_HOUR)
            Box(
                Modifier
                    .offset(
                        y = ((hour - FIRST_HOUR) * HOUR_HEIGHT).dp,
                    ).fillMaxWidth()
                    .height(3.dp)
                    .background(TmColors.Active),
            )
        }
    }
}

/** Schräge Striche über die ganze Fläche. */
private fun DrawScope.hatch() {
    val step = HATCH.dp.toPx()
    var start = -size.height
    while (start < size.width) {
        drawLine(TmColors.Line, Offset(start, size.height), Offset(start + size.height, 0f), 1.dp.toPx())
        start += step
    }
}

@Composable
private fun Block(entry: CalendarEntry) {
    val start = entry.startHour.coerceIn(FIRST_HOUR, LAST_HOUR)
    val end = maxOf(entry.endHour, entry.startHour + 1).coerceIn(start + 1, LAST_HOUR)
    val color = if (entry.kind in DISRUPTIONS) TmColors.Danger else TmColors.Primary
    Column(
        Modifier
            .offset(y = ((start - FIRST_HOUR) * HOUR_HEIGHT).dp)
            .fillMaxWidth()
            .height(((end - start) * HOUR_HEIGHT).dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = BLOCK_ALPHA))
            .padding(horizontal = 3.dp),
    ) {
        BasicText(
            entry.kind.label,
            style = TmType.text(10.sp, FontWeight.SemiBold, TmColors.Surface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Uhrzeit nur, wenn der Block hoch genug ist (ab zwei Stunden)
        if (end - start >= 2 && entry.endHour > entry.startHour) {
            BasicText(
                "${entry.startHour}–${entry.endHour}",
                style = TmType.text(10.sp, color = TmColors.Surface),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LegendBox(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        BasicText(text, style = TmType.caption)
    }
}

/** Kommende Termine, je zwei Zeilen: Art und Tag/Uhrzeit, darunter der Ort. */
@Composable
private fun Upcoming(
    events: EventsState,
    firstDay: Int,
    onShow: (Set<Int>) -> Unit,
) {
    if (events.calendar.isEmpty()) BasicText(tr("Nichts geplant."), style = TmType.small)
    for (entry in events.calendar.take(MAX_UPCOMING)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) {
                    onShow(entry.segments)
                }.padding(vertical = 3.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(entry.kind.label, style = TmType.text(13.sp, FontWeight.SemiBold))
                BasicText(
                    tr(
                        "Tag {day} · {from}–{to} Uhr",
                        "day" to entry.day - firstDay + 1,
                        "from" to entry.startHour,
                        "to" to entry.endHour,
                    ),
                    style = TmType.mono(11.sp, bold = false, color = TmColors.Muted),
                )
            }
            BasicText(entry.place, style = TmType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Je ungeplanter Ereignisart: Auslöser → Folge, dazu das Risiko von gerade. */
@Composable
private fun Risks(
    events: EventsState,
    rules: GameRules,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Risk(
            Modifier.weight(1f),
            tr("Rad-Korso"),
            tr("Auslöser: Dauerstau ({days} Tage in Folge), Wochenende, Fahrverbote", "days" to events.jamStreak),
            tr("Folge: Kolonne blockiert Hauptstraßen"),
            tr("{percent} % je Tag", "percent" to (events.corsoChance * PERCENT).roundToInt()),
        )
        Risk(
            Modifier.weight(1f),
            tr("Spontandemo"),
            tr("Auslöser: {rule}", "rule" to demoRule(rules)),
            tr("Folge: Sperrung vor dem unzufriedensten Viertel"),
            tr("{percent} % je Std.", "percent" to (events.demoChancePerHour * PERCENT).roundToInt()),
        )
        Risk(
            Modifier.weight(1f),
            tr("Schwertransport"),
            tr("Auslöser: kommt nachts von selbst"),
            tr("Folge: Langsam über die Hauptachsen, Brücken leiden"),
            null,
        )
    }
}

@Composable
private fun Risk(
    modifier: Modifier,
    title: String,
    trigger: String,
    consequence: String,
    chance: String?,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(title, style = TmType.text(13.sp, FontWeight.Bold))
            chance?.let { BasicText(it, style = TmType.mono(12.sp, color = TmColors.WarnText)) }
        }
        BasicText(trigger, style = TmType.caption)
        BasicText(consequence, style = TmType.caption)
    }
}

/** Was passiert ist, als eine Zeile je Ereignis: Tag, Uhrzeit, Art, Ort. */
@Composable
private fun Chronicle(
    events: EventsState,
    firstDay: Int,
    onShow: (Set<Int>) -> Unit,
) {
    if (events.chronicle.isEmpty()) BasicText(tr("Noch ist nichts passiert."), style = TmType.small)
    for (entry in events.chronicle.asReversed().take(MAX_CHRONICLE)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) {
                    onShow(entry.segments)
                }.padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                tr(
                    "Tag {day} · {hour}:00",
                    "day" to entry.day - firstDay + 1,
                    "hour" to entry.startHour.toString().padStart(2, '0'),
                ),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
                modifier = Modifier.width(CHRONICLE_WHEN.dp),
            )
            BasicText(
                entry.kind.label,
                style =
                    TmType.text(
                        13.sp,
                        FontWeight.SemiBold,
                        if (entry.kind in
                            DISRUPTIONS
                        ) {
                            TmColors.DangerText
                        } else {
                            TmColors.Ink
                        },
                    ),
                modifier = Modifier.width(CHRONICLE_KIND.dp),
            )
            BasicText(
                entry.place,
                style = TmType.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val DAYS = 7
private const val FIRST_HOUR = 6
private const val LAST_HOUR = 24
private const val HOURS = LAST_HOUR - FIRST_HOUR
private const val HOUR_HEIGHT = 18
private const val HOUR_LABEL = 28
private const val LABEL_STEP = 3
private const val PAST_ALPHA = 0.5f
private const val HATCH = 8
private const val BLOCK_ALPHA = 0.85f
private const val SIDE_WIDTH = 380
private const val MAX_UPCOMING = 8
private const val MAX_CHRONICLE = 20
private const val CHRONICLE_WHEN = 110
private const val CHRONICLE_KIND = 140
private const val PERCENT = 100
