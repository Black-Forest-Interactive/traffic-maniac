package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.BookingCategory
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageResponse
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.MaintenanceBudget
import de.bfi.trafficmaniac.game.RepairMethod
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.sim.VehicleType
import de.bfi.trafficmaniac.ui.components.ChartAxis
import de.bfi.trafficmaniac.ui.components.CheckBox
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.PageMode
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.TimeAxis
import de.bfi.trafficmaniac.ui.components.TimeChart
import de.bfi.trafficmaniac.ui.components.threeTicks
import de.bfi.trafficmaniac.ui.map.MapStyle
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Filter über der Tabelle. */
private enum class DamageFilter { ALL, UNSECURED, SIGNED, REPAIRING }

private val CardShape = RoundedCornerShape(14.dp)

private fun Modifier.card() = clip(CardShape).background(TmColors.Surface).border(1.dp, TmColors.LineSoft, CardShape)

/**
 * Bauamt als Seite (Entwurf 2, `V2-Bauamt`): links die Schäden als Tabelle mit Auswahl, Filtern und Sammelaktion,
 * darunter das Detail genau eines Schadens; rechts die Statistik – Zustand im Verlauf mit Prognose je Budget,
 * neu vs. behoben, Verschleiß nach Ursache, Kosten und Klagerisiko.
 */
@Composable
internal fun MaintenancePage(
    data: MaintenancePageData,
    page: PageMode,
) {
    val preset = LocalMaintenancePreset.current
    val sorted =
        data.game.maintenance.reports
            .sortedBy { it.hoursLeft }
    var selected by remember { mutableStateOf(sorted.take(preset.select).map { it.id }.toSet()) }
    var open by remember { mutableStateOf(sorted.firstOrNull()?.id?.takeIf { preset.open }) }
    val statsScroll = rememberScrollState()
    val tableScroll = rememberScrollState()
    var barHeight by remember { mutableStateOf(0) }
    if (preset.tableToEnd) LaunchedEffect(tableScroll.maxValue) { tableScroll.scrollTo(tableScroll.maxValue) }
    if (preset.statsToEnd) LaunchedEffect(statsScroll.maxValue) { statsScroll.scrollTo(statsScroll.maxValue) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(page.eyebrow ?: tr("Amt"), tr("Bauamt"), page.onMap)
        page.kpis?.let { KpiRow(it) }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Die Sammelaktion haftet unten an der Spalte – sonst läge sie hinter 42 Zeilen
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(tableScroll)) {
                    DamageTable(data, selected, { selected = it }, open) { open = if (open == it) null else it }
                    // Unten Platz in Leistenhöhe + 16 dp, damit sich die letzte Zeile hervorscrollen lässt
                    if (selected.isNotEmpty()) {
                        Box(
                            Modifier.height(with(LocalDensity.current) { barHeight.toDp() } + BULK_GAP.dp),
                        )
                    }
                }
                val chosen =
                    data.game.maintenance.reports
                        .filter { it.id in selected }
                if (chosen.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(
                                Alignment.BottomCenter,
                            ).fillMaxWidth()
                            .onSizeChanged { barHeight = it.height }
                            .padding(12.dp),
                    ) {
                        BulkBar(chosen, data) { selected = emptySet() }
                    }
                }
            }
            Column(
                Modifier.width(STATS_WIDTH.dp).fillMaxHeight().verticalScroll(statsScroll),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatCard(tr("Straßenzustand"), tr("seit Tag 1 · Prognose bis Tag {day}", "day" to data.game.termDays)) {
                    ConditionForecast(data)
                }
                StatCard(tr("Schäden neu vs. behoben"), tr("je 6 Std.")) { NewVsFixed(data) }
                StatCard(tr("Verschleiß nach Ursache"), tr("Amtszeit")) { WearCauses(data) }
                StatCard(tr("Kosten & Klagen"), tr("Amtszeit")) { CostsAndRisk(data) }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    period: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(title, style = TmType.text(16.sp, FontWeight.Bold))
        BasicText(period, style = TmType.caption)
        content()
    }
}

/** Die Schäden als Tabelle: Filter, Kopf, Zeilen (46 dp) mit Auswahl; bei Auswahl die Sammelaktion darunter. */
@Composable
private fun DamageTable(
    data: MaintenancePageData,
    selected: Set<Int>,
    onSelect: (Set<Int>) -> Unit,
    open: Int?,
    onOpen: (Int) -> Unit,
) {
    var filter by remember { mutableStateOf(DamageFilter.ALL) }
    val reports =
        data.game.maintenance.reports
            .filter { matches(it, filter) }
            .sortedBy { it.hoursLeft }
    val days = max(data.snapshot.time / SECONDS_PER_DAY, MIN_DAYS)
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(tr("Schäden"), style = TmType.text(16.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
            for (option in DamageFilter.entries) {
                val count =
                    data.game.maintenance.reports
                        .count { matches(it, option) }
                Chip("${option.label} · $count", filter == option) { filter = option }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Alle sichtbaren auswählen bzw. die Auswahl aufheben
            val ids = reports.map { it.id }.toSet()
            val count = ids.count { it in selected }
            val state =
                when {
                    count == 0 -> ToggleableState.Off
                    count == ids.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
            // Teilweise ausgewählt → Klick wählt alle
            CheckBox(state) { onSelect(if (state == ToggleableState.On) selected - ids else selected + ids) }
            Head(tr("Straße"), Modifier.weight(2f))
            Head(tr("Schaden"), Modifier.weight(KIND_WEIGHT))
            Head(tr("Zustand"), Modifier.width(ZUSTAND.dp))
            Head(tr("Status"), Modifier.weight(1f))
            Head(tr("Frist"), Modifier.width(FRIST.dp), TextAlign.End)
            Head(tr("Verfall/Tag"), Modifier.width(DECAY.dp), TextAlign.End)
        }
        if (reports.isEmpty()) BasicText(tr("Keine Schäden in dieser Auswahl."), style = TmType.small)
        // Zeilen ohne Abstand, mit Trennlinie – so passen 12–14 Schäden ohne Scrollen
        Column(Modifier.fillMaxWidth()) {
            for (report in reports) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
                DamageRow(report, report.id in selected, report.id == open, decayOf(report, data, days), {
                    onSelect(if (it) selected + report.id else selected - report.id)
                }) { onOpen(report.id) }
                // Nur ein Schaden zur Zeit aufgeklappt – direkt unter seiner Zeile
                if (report.id == open) {
                    Column(Modifier.fillMaxWidth().background(TmColors.PrimaryTint).padding(16.dp)) {
                        ExpandedDamage(
                            report,
                            data.rules,
                            { data.onCommand(GameCommand.RespondToDamage(report.id, it)) },
                            { data.onShow(report) },
                            framed = false,
                            balance = data.game.balance,
                        )
                    }
                }
            }
        }
    }
}

private val DamageFilter.label: String
    get() =
        when (this) {
            DamageFilter.ALL -> tr("alle")
            DamageFilter.UNSECURED -> tr("ungesichert")
            DamageFilter.SIGNED -> tr("mit Schild")
            DamageFilter.REPAIRING -> tr("in Reparatur")
        }

private fun matches(
    report: DamageReport,
    filter: DamageFilter,
): Boolean =
    when (filter) {
        DamageFilter.ALL -> true
        DamageFilter.UNSECURED -> report.status in UNSECURED_DAMAGE
        DamageFilter.SIGNED -> report.status == DamageStatus.SIGNED
        DamageFilter.REPAIRING -> report.status == DamageStatus.TENDER || report.status == DamageStatus.CONSTRUCTION
    }

@Composable
private fun Chip(
    text: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        text,
        style = TmType.text(12.sp, FontWeight.SemiBold, if (chosen) TmColors.Surface else TmColors.Ink),
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (chosen) TmColors.Ink else TmColors.Ground)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun RowScope.Head(
    text: String,
    modifier: Modifier,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text,
        style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Muted).copy(textAlign = align),
        modifier = modifier,
    )
}

@Composable
private fun DamageRow(
    report: DamageReport,
    checked: Boolean,
    open: Boolean,
    decayPerDay: Double?,
    onCheck: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    // Ungesicherte Schäden rot, alle anderen grau
    val statusColor = if (report.status in UNSECURED_DAMAGE) TmColors.DangerText else TmColors.Muted
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (open) TmColors.PrimaryTint else Color.Transparent)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CheckBox(checked, onCheck)
        BasicText(
            report.place,
            style = TmType.text(13.sp, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f),
        )
        BasicText(
            report.kind.label,
            style = TmType.text(13.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(KIND_WEIGHT),
        )
        Meter(
            report.conditionPercent / PERCENT_F,
            Modifier.width(ZUSTAND.dp),
            height = 6.dp,
            color =
                when {
                    report.conditionPercent < MapStyle.CONDITION_BAD * PERCENT_F -> TmColors.Danger
                    report.conditionPercent < MapStyle.CONDITION_FAIR * PERCENT_F -> TmColors.Warn
                    else -> TmColors.Success
                },
        )
        BasicText(
            report.status.label,
            style = TmType.text(12.sp, color = statusColor),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        BasicText(
            tr("{hours} Std.", "hours" to report.hoursLeft.coerceAtLeast(0.0).roundToInt()),
            style = TmType.mono(12.sp, bold = false).copy(textAlign = TextAlign.End),
            modifier = Modifier.width(FRIST.dp),
        )
        BasicText(
            decayPerDay?.let { "−${formatDecimal(it * PERCENT, 1)} %" } ?: "–",
            style = TmType.mono(12.sp, bold = false, color = TmColors.Muted).copy(textAlign = TextAlign.End),
            modifier = Modifier.width(DECAY.dp),
        )
    }
}

/** Sammelaktion für die ausgewählten Schäden: flicken (nur Schlaglöcher), Schild 30, ausschreiben – mit Gesamtpreis. */
@Composable
private fun BulkBar(
    chosen: List<DamageReport>,
    data: MaintenancePageData,
    onDone: () -> Unit,
) {
    val rules = data.rules
    val patchable = chosen.filter { it.canPatch }
    val respond = { reports: List<DamageReport>, response: DamageResponse ->
        reports.forEach { data.onCommand(GameCommand.RespondToDamage(it.id, response)) }
        onDone()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(10.dp),
            ).background(TmColors.Ink)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            tr("{count} ausgewählt", "count" to chosen.size),
            style = TmType.text(13.sp, FontWeight.Bold, TmColors.OnDark),
            softWrap = false,
        )
        // Die Knöpfe dürfen umbrechen – die Anzahl links bleibt einzeilig
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { BulkButtons(chosen, patchable, data, respond, onDone) }
    }
}

@Composable
private fun BulkButtons(
    chosen: List<DamageReport>,
    patchable: List<DamageReport>,
    data: MaintenancePageData,
    respond: (List<DamageReport>, DamageResponse) -> Unit,
    onDone: () -> Unit,
) {
    val rules = data.rules
    val balance = data.game.balance
    if (patchable.isNotEmpty()) {
        BulkButton(
            tr("Flicken"),
            rules.patchCost * patchable.size,
            balance,
        ) { respond(patchable, DamageResponse.Patch) }
    }
    BulkButton(tr("Schild 30"), rules.warningSignCost * chosen.size, balance) {
        respond(chosen, DamageResponse.WarningSign(SIGN_KMH))
    }
    BulkButton(tr("Ausschreiben"), chosen.sumOf { it.repairCost }, balance) {
        respond(chosen, DamageResponse.Repair(RepairMethod.TENDER))
    }
    BulkButton(tr("Abbrechen"), onClick = onDone)
}

/** Knopf der Sammelleiste mit Preis; reicht die Kasse nicht, ist er aus und nennt darunter, was fehlt. */
@Composable
private fun BulkButton(
    label: String,
    cost: Long,
    balance: Long,
    onClick: () -> Unit,
) {
    val missing = cost - balance
    val hint = if (missing > 0) tr("fehlen {missing}", "missing" to formatEuro(missing)) else null
    BulkButton("$label ${formatEuro(cost)}", hint, enabled = missing <= 0, onClick = onClick)
}

@Composable
private fun BulkButton(
    text: String,
    hint: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (enabled) TmColors.OnDark else TmColors.OnDark.copy(alpha = DISABLED_ALPHA)
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) TmColors.Ink3 else TmColors.Ink)
            .border(1.dp, TmColors.Ink3, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text, style = TmType.text(12.sp, FontWeight.SemiBold, color), softWrap = false)
        hint?.let { BasicText(it, style = TmType.text(11.sp, color = DangerOnDark), softWrap = false) }
    }
}

/** Rot, das auf der dunklen Leiste lesbar ist. */
private val DangerOnDark = Color(0xFFFF8A80)

/**
 * Zustand je Stunde seit Tag 1 und von jetzt bis Amtsende je Budget fortgeschrieben (gestrichelt; das gewählte
 * farbig). Der Verfall von heute wird dafür auf „ohne Budget“ zurückgerechnet und mit dem Faktor jedes Budgets
 * neu angesetzt. Darunter das Budget zum Umschalten.
 */
@Composable
private fun ConditionForecast(data: MaintenancePageData) {
    val state = data.game.maintenance
    val history = state.conditionHistory.ifEmpty { listOf(state.meanConditionPercent) }
    val now = history.last().toFloat()
    // Verfall je Stunde aus den letzten 24 Stunden (bzw. seit Beginn), zurückgerechnet auf „kein Budget“
    val window = minOf(HOURS_PER_DAY, history.size - 1)
    val perHour = if (window > 0) (history[history.size - 1 - window] - history.last()).toFloat() / window else 0f
    val base = perHour / state.budget.decayFactor.toFloat()
    // Zeitachse: der erste Messpunkt bis zum Ende der Amtszeit
    val firstHour = data.snapshot.gameTime.absoluteHour - (history.size - 1)
    val endHour = (data.game.firstDay + data.game.termDays) * HOURS_PER_DAY
    val start = history.size - 1
    val points = max(endHour - firstHour, start) + 1
    val left = points - 1 - start
    val targets =
        MaintenanceBudget.entries.associateWith {
            (now - base * it.decayFactor.toFloat() * left).coerceIn(0f, PERCENT_F)
        }
    // Y-Bereich an die Daten angepasst (in 10er-Schritten), damit die Prognosen auseinanderliegen
    val lowest = minOf(history.min().toFloat(), targets.values.min())
    val bottom = ((lowest - Y_MARGIN) / Y_STEP).toInt().coerceIn(0, MAX_BOTTOM) * Y_STEP
    TimeChart(
        TimeAxis(firstHour, points, data.game.firstDay),
        ChartAxis(bottom, PERCENT_F, threeTicks(bottom, PERCENT_F)) { "${it.roundToInt()} %" },
        endSpace = END_SPACE.dp,
    ) {
        for (i in 1 until history.size) {
            drawLine(
                TmColors.Ink,
                Offset(x(i - 1f), y(history[i - 1].toFloat())),
                Offset(x(i.toFloat()), y(history[i].toFloat())),
                2.dp.toPx(),
            )
        }
        for ((budget, target) in targets) {
            val chosen = budget == state.budget
            val color = if (chosen) TmColors.Primary else TmColors.Muted
            val end = Offset(x(points - 1f), y(target))
            drawLine(
                if (chosen) color else TmColors.Line,
                Offset(x(start.toFloat()), y(now)),
                end,
                if (chosen) 2.5.dp.toPx() else 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
            )
            label("${target.roundToInt()} %", end, color)
        }
    }
    // Zielwerte je Budget, das gewählte hervorgehoben – direkt darüber das Segment zum Umschalten
    SegmentedControl(
        MaintenanceBudget.entries,
        state.budget,
        { it.label },
        { data.onCommand(GameCommand.SetMaintenanceBudget(it)) },
        Modifier.fillMaxWidth(),
        detail = { tr("→ {percent} %", "percent" to targets.getValue(it).roundToInt()) },
    )
}

/** Balken je 6 Stunden: neu gemeldet (orange) und behoben (grün), die letzten 4 Tage. */
@Composable
private fun NewVsFixed(data: MaintenancePageData) {
    val state = data.game.maintenance
    val bins = max(state.newPer6h.size, state.fixedPer6h.size)
    if (bins == 0) {
        BasicText(tr("Noch keine Schäden gemeldet."), style = TmType.small)
        return
    }
    val from = (bins - MAX_BINS).coerceAtLeast(0)
    val new = (from until bins).map { state.newPer6h.getOrElse(it) { 0 } }
    val fixed = (from until bins).map { state.fixedPer6h.getOrElse(it) { 0 } }
    val top = max(new.max(), fixed.max()).coerceAtLeast(1).toFloat()
    // Fach 0 beginnt um Mitternacht des ersten Spieltags (siehe Maintenance.clockOffset)
    val time = data.snapshot.gameTime
    val startSeconds = time.day * SECONDS_PER_DAY + time.secondOfDay - data.snapshot.time
    val startHour = (startSeconds / SECONDS_PER_DAY).toInt() * HOURS_PER_DAY
    TimeChart(
        TimeAxis(startHour + from * HOURS_PER_BIN, new.size + 1, data.game.firstDay, HOURS_PER_BIN, clock = true),
        ChartAxis(0f, top, threeTicks(0f, top)) { "${it.roundToInt()}" },
        height = BARS_HEIGHT.dp,
    ) {
        val bar = slot * BAR_SHARE / 2
        for (i in new.indices) {
            val left = x(i.toFloat()) + (slot - 2 * bar) / 2
            drawRect(TmColors.Warn, Offset(left, y(new[i].toFloat())), Size(bar, y(0f) - y(new[i].toFloat())))
            drawRect(
                TmColors.Success,
                Offset(left + bar, y(fixed[i].toFloat())),
                Size(
                    bar,
                    y(0f) - y(fixed[i].toFloat()),
                ),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(TmColors.Warn, tr("neu {count}", "count" to new.sum()))
        LegendDot(TmColors.Success, tr("behoben {count}", "count" to fixed.sum()))
    }
}

@Composable
private fun LegendDot(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(2.dp)))
        BasicText(text, style = TmType.caption)
    }
}

private const val STATS_WIDTH = 480
private const val ROW_HEIGHT = 46
private const val CHECK = 20
private const val ZUSTAND = 80
private const val FRIST = 56
private const val DECAY = 76
private const val CHART_HEIGHT = 140
private const val BARS_HEIGHT = 80
private const val BAR_SHARE = 0.8f
private const val MAX_BINS = 16
private const val HOURS_PER_DAY = 24
private const val SECONDS_PER_DAY = 86_400.0
private const val MIN_DAYS = 0.1
private const val PERCENT = 100.0
private const val PERCENT_F = 100f
private const val POOR = 30
private const val SIGN_KMH = 30
private const val DASH = 8f
private const val KIND_WEIGHT = 1.2f
private const val Y_MARGIN = 5f
private const val Y_STEP = 10f
private const val MAX_BOTTOM = 9
private const val END_SPACE = 44
private const val HOURS_PER_BIN = 6
private const val BULK_GAP = 16
private const val DISABLED_ALPHA = 0.45f
