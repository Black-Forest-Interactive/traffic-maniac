package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.TransitLineStatus
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.PageMode
import de.bfi.trafficmaniac.ui.map.MapExtras
import de.bfi.trafficmaniac.ui.map.MapView
import de.bfi.trafficmaniac.ui.map.TransitStyle
import de.bfi.trafficmaniac.ui.map.delayColor
import de.bfi.trafficmaniac.ui.map.lineColor
import kotlin.math.max
import kotlin.math.min

private val CardShape = RoundedCornerShape(14.dp)

private fun Modifier.card() = clip(CardShape).background(TmColors.Surface).border(1.dp, TmColors.LineSoft, CardShape)

/**
 * ÖPNV als Seite (Entwurf 2, `V2-OePNV`): Kennzahlen, links die Linien (unpünktlichste oben, je Richtung Balken und
 * Ø-Verspätung), rechts die Netzkarte – alle Linien blass, die gewählte hervorgehoben, ihre Abschnitte nach
 * Verspätung gefärbt – und darunter die Verspätung an jedem Halt der gewählten Linie.
 */
@Composable
internal fun TransitPage(
    frame: TrafficFrame,
    city: CityMap,
    page: PageMode,
) {
    val lines = frame.current.transit.lines
    val byLine = lines.groupBy { it.ref }
    // Unpünktlichste Linie zuerst; ohne Fahrt heute ans Ende
    val refs =
        byLine.keys.sortedWith(
            compareBy<String> { ref -> byLine.getValue(ref).none { it.stopsServed > 0 } }
                .thenByDescending { ref -> byLine.getValue(ref).maxOf { it.averageDelaySeconds } },
        )
    var selected by remember { mutableStateOf<String?>(null) }
    val current = selected?.takeIf { it in byLine } ?: refs.firstOrNull()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(page.eyebrow ?: tr("Bericht"), tr("ÖPNV"), page.onMap)
        page.kpis?.let { KpiRow(it) }
        if (lines.isEmpty()) {
            BasicText(
                tr("Für diese Stadt sind keine Buslinien bekannt – die Karte stammt aus einer Version ohne Busse."),
                style = TmType.small,
            )
            return@Column
        }
        TransitBody(frame, byLine, refs, current, city, { selected = it }, Modifier.weight(1f))
    }
}

/** Linien links, Netzkarte und Verspätung je Halt rechts. */
@Composable
private fun TransitBody(
    frame: TrafficFrame,
    byLine: Map<String, List<TransitLineStatus>>,
    refs: List<String>,
    current: String?,
    city: CityMap,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    var byDelay by remember { mutableStateOf(false) }
    val list: @Composable (Modifier) -> Unit = { listModifier ->
        Column(listModifier.card().verticalScroll(rememberScrollState()).padding(12.dp)) {
            // Kurz nach Mitternacht stammen die Zahlen noch von gestern – das dazusagen
            val period = if (frame.current.transit.fromYesterday) tr("gestern") else tr("heute")
            Row(
                Modifier.padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(tr("Linien"), style = TmType.text(16.sp, FontWeight.Bold))
                BasicText(period, style = TmType.caption)
            }
            for (ref in refs) LineEntry(ref, byLine.getValue(ref), ref == current) { onSelect(ref) }
        }
    }
    val map: @Composable (Modifier) -> Unit = { mapModifier ->
        Box(mapModifier.card()) {
            NetworkMap(frame, city, current, byDelay, Modifier.fillMaxSize())
            NetworkMapHeader(byDelay) { byDelay = it }
        }
    }
    val delays: @Composable () -> Unit = {
        current?.let { StopDelays(byLine.getValue(it), city, Modifier.fillMaxWidth().height(STOPS_HEIGHT.dp).card()) }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < STACK_BELOW.dp) {
            // Wenig Breite (Handy): Liste, Karte und Halte untereinander, die Seite scrollt
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                list(Modifier.fillMaxWidth().height(STACKED_LIST.dp))
                map(Modifier.fillMaxWidth().height(STACKED_MAP.dp))
                delays()
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                list(Modifier.width(LIST_WIDTH.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    map(Modifier.weight(1f).fillMaxWidth())
                    delays()
                }
            }
        }
    }
}

@Composable
private fun LineEntry(
    ref: String,
    directions: List<TransitLineStatus>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TmColors.PrimaryTint else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineBadge(ref, lineColor(directions.first()))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (direction in directions) DirectionRow(direction)
        }
    }
}

/**
 * Netzkarte: die gedämpfte Stadtkarte, auf die gewählte Linie gezoomt – sie farbig mit beschrifteten Halten, alle
 * anderen Linien grau. Eine andere Linie wählen zoomt neu.
 */
@Composable
private fun NetworkMap(
    frame: TrafficFrame,
    city: CityMap,
    highlight: String?,
    byDelay: Boolean,
    modifier: Modifier,
) {
    val lines = frame.current.transit.lines
    val bounds = remember(city, highlight, lines.size) { lineBounds(lines.filter { it.ref == highlight }, city) }
    key(highlight) {
        MapView(
            city,
            modifier,
            traffic = { frame },
            initialBounds = bounds,
            extras =
                MapExtras(
                    transit = true,
                    signs = false,
                    cameras = false,
                    incidents = false,
                    assignments = false,
                    transitStyle = TransitStyle(byDelay, highlight),
                ),
        )
    }
}

/** Bereich, den die Linien abdecken, mit etwas Rand; `null` ohne Linien. */
private fun lineBounds(
    lines: List<TransitLineStatus>,
    city: CityMap,
): Bounds? {
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (segment in lines.flatMap { line -> line.sections.flatMap { it.segments.asList() } }) {
        val geometry = city.roads.segments[segment].geometry
        for (i in 0 until geometry.pointCount) {
            left = min(left, geometry.x(i))
            right = max(right, geometry.x(i))
            top = min(top, geometry.y(i))
            bottom = max(bottom, geometry.y(i))
        }
    }
    if (left > right) return null
    val margin = max(right - left, bottom - top) * BOUNDS_MARGIN
    return Bounds(left - margin, top - margin, right + margin, bottom + margin)
}

/** Titel über der Netzkarte mit Umschalter der Färbung. */
@Composable
private fun NetworkMapHeader(
    byDelay: Boolean,
    onByDelay: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .padding(
                12.dp,
            ).clip(
                RoundedCornerShape(10.dp),
            ).background(TmColors.Surface)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(tr("Netzkarte"), style = TmType.text(13.sp, FontWeight.Bold))
        for ((value, label) in listOf(true to tr("nach Verspätung"), false to tr("nach Linie"))) {
            BasicText(
                label,
                style =
                    TmType.text(
                        12.sp,
                        FontWeight.SemiBold,
                        if (byDelay ==
                            value
                        ) {
                            TmColors.Surface
                        } else {
                            TmColors.Ink
                        },
                    ),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (byDelay == value) TmColors.Ink else TmColors.Ground)
                        .clickable(role = Role.RadioButton) { onByDelay(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Verspätung an jedem Halt der gewählten Linie (erste Richtung, dann die Rückrichtung). */
@Composable
private fun StopDelays(
    directions: List<TransitLineStatus>,
    city: CityMap,
    modifier: Modifier,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        StopDelayList(directions, city, Modifier.fillMaxSize().verticalScroll(scroll))
        // Geht es weiter unten weiter, blendet der Rand aus (wie im Ordnungsamt)
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FADE_HEIGHT.dp)
                    .background(Brush.verticalGradient(listOf(TmColors.Surface.copy(alpha = 0f), TmColors.Surface))),
            )
        }
    }
}

@Composable
private fun StopDelayList(
    directions: List<TransitLineStatus>,
    city: CityMap,
    modifier: Modifier,
) {
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(tr("Verspätung an jedem Halt"), style = TmType.text(16.sp, FontWeight.Bold))
        DelayLegend()
        if (directions.all { direction -> direction.sections.all { it.delaySeconds == null } }) {
            BasicText(tr("Heute noch keine Fahrt auf dieser Linie."), style = TmType.small)
            return@Column
        }
        for (direction in directions) {
            BasicText(destination(direction.name), style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Muted))
            if (direction.sections.all { it.delaySeconds == null }) {
                BasicText(tr("Heute noch keine Fahrt in dieser Richtung."), style = TmType.small)
                continue
            }
            for (section in direction.sections) {
                val delay = section.delaySeconds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(
                        city.transitStops.getOrNull(section.stop)?.name ?: "–",
                        style = TmType.text(13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(STOP_NAME_WIDTH.dp),
                    )
                    Meter(
                        delay?.let { (it / MAX_DELAY_SECONDS).toFloat().coerceIn(0f, 1f) } ?: 0f,
                        Modifier.weight(1f),
                        height = 6.dp,
                        color = delayColor(delay),
                    )
                    // Ohne Wert: grauer Balken (oben) und „–“, einzeilig in fester Breite
                    BasicText(
                        delay?.let(::delayText) ?: "–",
                        style = TmType.mono(12.sp, color = if (delay != null) TmColors.Ink else TmColors.Muted),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(DELAY_WIDTH.dp),
                    )
                }
            }
        }
    }
}

private const val LIST_WIDTH = 480
private const val FADE_HEIGHT = 40
private const val STACK_BELOW = 1000
private const val STACKED_LIST = 280
private const val STACKED_MAP = 360
private const val BOUNDS_MARGIN = 0.15f
private const val STOPS_HEIGHT = 260
private const val STOP_NAME_WIDTH = 200
private const val DELAY_WIDTH = 96
private const val MAX_DELAY_SECONDS = 600.0

/** Die Schwellen einmal erklärt – gelten für Liste, Halte und Karte. */
@Composable
private fun DelayLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((seconds, label) in listOf(
            0.0 to tr("bis 1 min"),
            LATE_SAMPLE to tr("bis 3 min"),
            BAD_SAMPLE to tr("darüber"),
        )) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(delayColor(seconds), CircleShape))
                BasicText(label, style = TmType.caption)
            }
        }
    }
}

/** Beispielwerte für die Legende: in der orangen bzw. roten Stufe. */
private const val LATE_SAMPLE = 120.0
private const val BAD_SAMPLE = 600.0
