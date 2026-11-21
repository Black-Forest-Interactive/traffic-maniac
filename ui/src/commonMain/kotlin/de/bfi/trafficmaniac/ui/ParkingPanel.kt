package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.ParkingOfficeState
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.ParkingLotState
import de.bfi.trafficmaniac.sim.ParkingSummary
import de.bfi.trafficmaniac.ui.components.ChartAxis
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.MinTouch
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.SettingRow
import de.bfi.trafficmaniac.ui.components.StatTile
import de.bfi.trafficmaniac.ui.components.Stepper
import de.bfi.trafficmaniac.ui.components.TimeAxis
import de.bfi.trafficmaniac.ui.components.TimeChart
import de.bfi.trafficmaniac.ui.components.TmButton
import de.bfi.trafficmaniac.ui.components.threeTicks
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Parken: Belegung und Einnahmen, Politessen, Regeln am Straßenrand (Brötchentaste, Anwohnerparken, Blumenkübel),
 * Gebühren und die größten Parkplätze und Parkhäuser.
 */
@Composable
fun ParkingPanel(
    office: ParkingOfficeState,
    summary: ParkingSummary,
    lots: List<ParkingLotState>,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    /** Aktuelle Spielstunde (Tag × 24 + Stunde) und erster Spieltag – für die Zeitachse. */
    nowHour: Int = 0,
    firstDay: Int = 0,
) {
    PanelScaffold(tr("Amt"), tr("Parken & Politessen"), modifier, onClose, floating = false) {
        // Die Zahlen von früher (belegt, Knöllchen …) stehen jetzt oben in den Kennzahlen
        PanelSection(title = tr("Belegung über die Zeit")) { OccupancyChart(office, nowHour, firstDay) }
        PanelSection(title = tr("Welcher Preis bringt was?")) { PriceTable(office, summary, lots, rules, nowHour) }
        PanelSection(title = tr("Politessen")) { Wardens(office, rules, onCommand) }
        CurbSection(office, summary, rules, onCommand)
        FeeSection(summary, lots, rules, onCommand)
        LotSection(lots, rules, onCommand)
    }
}

/**
 * Belegung je Stunde seit Tag 1: Parkhäuser und Straßenrand (Anteil), Falschparker gestrichelt, Preisänderungen
 * als Striche.
 */
@Composable
private fun OccupancyChart(
    office: ParkingOfficeState,
    nowHour: Int,
    firstDay: Int,
) {
    val history = office.history
    if (history.size < 2) {
        BasicText(tr("Noch keine volle Stunde gemessen."), style = TmType.small)
        return
    }
    val maxIllegal = history.maxOf { it.illegal }.coerceAtLeast(1).toFloat()
    // Links die Belegung in %, rechts in Rot die Falschparker je Stunde (eigene Skala)
    TimeChart(
        TimeAxis(nowHour - (history.size - 1), history.size, firstDay),
        ChartAxis(0f, 1f, threeTicks(0f, 1f)) { "${(it * PERCENT).roundToInt()} %" },
        right = ChartAxis(0f, maxIllegal, threeTicks(0f, maxIllegal), TmColors.DangerText) { "${it.roundToInt()}" },
        height = CHART_HEIGHT.dp,
    ) {
        for (hour in office.priceChanges) {
            drawLine(TmColors.Ink, Offset(x(hour.toFloat()), y(1f)), Offset(x(hour.toFloat()), y(0f)), 1.dp.toPx())
        }
        for (i in 1 until history.size) {
            val (a, b) = history[i - 1] to history[i]
            val (xa, xb) = x(i - 1f) to x(i.toFloat())
            drawLine(LotColor, Offset(xa, y(a.lotShare)), Offset(xb, y(b.lotShare)), 2.dp.toPx())
            drawLine(CurbColor, Offset(xa, y(a.curbShare)), Offset(xb, y(b.curbShare)), 2.dp.toPx())
            drawLine(
                TmColors.Danger,
                Offset(xa, yRight(a.illegal.toFloat())),
                Offset(xb, yRight(b.illegal.toFloat())),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Legend(LotColor, tr("Parkhäuser"))
        Legend(CurbColor, tr("Straßenrand"))
        Legend(TmColors.Danger, tr("Falschparker/Std. (rechte Achse)"))
        if (office.priceChanges.isNotEmpty()) Legend(TmColors.Ink, tr("Preisänderung"))
    }
}

@Composable
private fun Legend(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(width = 12.dp, height = 3.dp).background(color))
        BasicText(text, style = TmType.caption)
    }
}

/**
 * Geschätzte Belegung und Gebühren je Preis: Wer höchstens [ParkingOfficeState.maxFeeTolerance] €/Std. zahlt, ist
 * gleich verteilt – also bleiben bei Preis p noch (1 − p/max) der Autofahrer. Ausgehend von der heutigen Belegung.
 */
@Composable
private fun PriceTable(
    office: ParkingOfficeState,
    summary: ParkingSummary,
    lots: List<ParkingLotState>,
    rules: GameRules,
    nowHour: Int,
) {
    // Straßenrand und Parkhäuser haben eigene Preise und eigene Auslastung – getrennt rechnen
    var curb by remember { mutableStateOf(true) }
    SegmentedControl(listOf(true, false), curb, {
        if (it) tr("Straßenrand") else tr("Parkhäuser")
    }, { curb = it }, Modifier.fillMaxWidth())
    val open = lots.filter { it.capacity > 0 }
    val market =
        if (curb) {
            Market(summary.curbCapacity, summary.curbFee, office.history.map { it.curbShare.toDouble() })
        } else {
            // Heutiger Preis: der mittlere der offenen Parkhäuser
            val fee = open.map { it.feePerHour }.sorted().getOrNull(open.size / 2) ?: 0.0
            Market(summary.lotCapacity, fee, office.history.map { it.lotShare.toDouble() })
        }
    if (market.capacity == 0 || market.history.isEmpty()) {
        BasicText(tr("Noch keine Daten."), style = TmType.small)
        return
    }
    val rows = priceRows(office, market, rules, nowHour)
    val best = rows.maxByOrNull { it.perDay }?.fee
    Row(Modifier.fillMaxWidth()) {
        Head(tr("€/Std."), Modifier.weight(1f))
        Head(
            tr(
                "Belegung Ø {from}–{until} Uhr (geschätzt)",
                "from" to office.paidFromHour,
                "until" to office.paidUntilHour,
            ),
            Modifier.weight(2f),
        )
        Head(tr("Gebühren/Tag"), Modifier.weight(1f), TextAlign.End)
    }
    for (row in rows) PriceRow(row, current = row.fee == market.fee, best = row.fee == best)
    BasicText(
        tr(
            "{places} Plätze × Belegung × {hours} Std. Gebührenpflicht ({from}–{until} Uhr) × Preis. " +
                "Blau: heutiger Preis · grün: bringt am meisten. Höhere Preise schicken mehr Autos auf Parkplatzsuche.",
            "places" to formatNumber(market.capacity.toLong()),
            "hours" to office.paidUntilHour - office.paidFromHour,
            "from" to office.paidFromHour,
            "until" to office.paidUntilHour,
        ),
        style = TmType.caption,
    )
}

/** Plätze, heutiger Preis und Belegung je Stunde (Anteil) eines Teilmarkts. */
private class Market(
    val capacity: Int,
    val fee: Double,
    val history: List<Double>,
)

private class PriceEstimate(
    val fee: Double,
    val share: Double,
    val perDay: Long,
)

/**
 * Geschätzte Belegung je Preis: Mittel der letzten 24 Stunden, aber nur in der Gebührenzeit (nachts ist Parken frei).
 * Die Zahlungsbereitschaft der Fahrer ist gleich verteilt bis [ParkingOfficeState.maxFeeTolerance], bei Preis p
 * bleiben (1 − p/max).
 */
private fun priceRows(
    office: ParkingOfficeState,
    market: Market,
    rules: GameRules,
    nowHour: Int,
): List<PriceEstimate> {
    val paid = office.paidFromHour until office.paidUntilHour
    val last = market.history.takeLast(HOURS_PER_DAY)
    // Der letzte Eintrag gehört zur aktuellen Stunde – daraus die Uhrzeit jedes Eintrags
    val during = last.filterIndexed { i, _ -> (nowHour - (last.size - 1 - i)).mod(HOURS_PER_DAY) in paid }
    val occupied = during.ifEmpty { last }.average()
    val willing = { fee: Double -> (1 - fee / office.maxFeeTolerance).coerceAtLeast(0.0) }
    val now = willing(market.fee)
    val hours = paid.last - paid.first + 1
    return rules.parkingFeeOptions.map { fee ->
        val share = if (now > 0) (occupied * willing(fee) / now).coerceIn(0.0, 1.0) else willing(fee)
        PriceEstimate(fee, share, (market.capacity * share * hours * fee).roundToLong())
    }
}

@Composable
private fun PriceRow(
    row: PriceEstimate,
    current: Boolean,
    best: Boolean,
) {
    val color =
        when {
            current -> TmColors.Primary
            best -> TmColors.SuccessText
            else -> TmColors.Ink
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val label = if (current) euro(row.fee) + " · " + tr("jetzt") else euro(row.fee)
        BasicText(label, style = TmType.mono(12.sp, color = color), modifier = Modifier.weight(1f))
        Row(
            Modifier.weight(2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Meter(row.share.toFloat(), Modifier.weight(1f), height = 6.dp, color = LotColor)
            BasicText(
                "${(row.share * PERCENT).roundToInt()} %",
                style = TmType.mono(11.sp, bold = false),
                modifier = Modifier.width(SHARE_WIDTH.dp),
            )
        }
        BasicText(
            "≈ " + formatEuro(row.perDay),
            style = TmType.mono(12.sp, color = color).copy(textAlign = TextAlign.End),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Politessen: Anzahl, Knöllchen je Kopf und Tag, Kosten gegen Ertrag je Woche – lohnt es sich? */
@Composable
private fun Wardens(
    office: ParkingOfficeState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    SettingRow(
        tr("Politessen") + if (office.hiring > 0) tr(" (+{hiring} kommen)", "hiring" to office.hiring) else "",
        hint =
            tr(
                "{salary}/Woche je Kopf · unterwegs {start}–{end} Uhr",
                "salary" to formatEuro(rules.wardenWeeklySalary),
                "start" to rules.wardenStartHour,
                "end" to rules.wardenEndHour,
            ),
    ) {
        Stepper(
            "${office.wardens}",
            onMinus = { onCommand(GameCommand.FireWardens(1)) }.takeIf { office.wardens > 0 },
            onPlus = { onCommand(GameCommand.HireWardens(1)) },
            bigStep = tr("+{count} einstellen", "count" to FIVE) to { onCommand(GameCommand.HireWardens(FIVE)) },
        )
    }
    val days = max(office.daysPlayed, MIN_DAYS)
    val perHead = if (office.wardens > 0) office.tickets / office.wardens / days else 0.0
    val perTicket = if (office.tickets > 0) office.ticketIncome / office.tickets else 0L
    val cost = office.wardens * rules.wardenWeeklySalary
    val income = (office.ticketIncome / days * DAYS_PER_WEEK).roundToLong()
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Figure(tr("Knöllchen je Kopf/Tag"), formatDecimal(perHead, 1))
        Figure(tr("Ø je Knöllchen"), formatEuro(perTicket))
        Figure(tr("Kosten/Woche"), formatEuro(cost))
        Figure(tr("Ertrag/Woche"), formatEuro(income))
    }
    BasicText(
        if (income >=
            cost
        ) {
            tr("Lohnt sich: Die Knöllchen bringen mehr, als die Politessen kosten.")
        } else {
            tr("Lohnt sich nicht: Die Politessen kosten mehr, als sie einbringen.")
        },
        style =
            TmType.text(
                13.sp,
                FontWeight.SemiBold,
                if (income >=
                    cost
                ) {
                    TmColors.SuccessText
                } else {
                    TmColors.WarnText
                },
            ),
    )
}

@Composable
private fun Figure(
    label: String,
    value: String,
) {
    Column {
        BasicText(label, style = TmType.caption)
        BasicText(value, style = TmType.mono(14.sp))
    }
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

/**
 * Straßenrand: Brötchentaste und Anwohnerparken als zwei Schalter (beide zugleich möglich), Parkplätze zu
 * Blumenkübeln.
 */
@Composable
private fun CurbSection(
    office: ParkingOfficeState,
    summary: ParkingSummary,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Straßenrand")) {
        Switch(
            tr("Brötchentaste"),
            tr("Die erste halbe Stunde ist frei – kurze Einkäufe zahlen nichts."),
            office.breadButton,
        ) { onCommand(GameCommand.SetBreadButton(!office.breadButton)) }
        Switch(
            tr("Anwohnerparken"),
            tr("Wohnstraßen nur mit Ausweis – Pendler suchen woanders, die Ausweise bringen Geld."),
            office.residentParking,
        ) { onCommand(GameCommand.SetResidentParking(!office.residentParking)) }
        TmButton(
            tr("Parkplätze → Blumenkübel ({cost})", "cost" to formatEuro(rules.curbConversionCost)),
            { onCommand(GameCommand.ConvertCurbParking) },
        )
        BasicText(
            tr("Noch {share} % der Parkplätze am Straßenrand", "share" to (summary.curbShare * PERCENT).roundToInt()),
            style = TmType.caption,
        )
    }
}

/** Schalter mit Titel und einer Zeile Erklärung. */
@Composable
private fun Switch(
    title: String,
    hint: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(10.dp),
            ).clickable(role = Role.Switch, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TmType.text(14.sp, FontWeight.Bold))
            BasicText(hint, style = TmType.caption)
        }
        Box(
            Modifier
                .size(
                    width = SWITCH_WIDTH.dp,
                    height = SWITCH_HEIGHT.dp,
                ).clip(RoundedCornerShape(SWITCH_HEIGHT.dp))
                .background(if (on) TmColors.Primary else TmColors.Track),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(
                        3.dp,
                    ).size((SWITCH_HEIGHT - 2 * KNOB_INSET).dp)
                    .background(TmColors.Surface, RoundedCornerShape(SWITCH_HEIGHT.dp)),
            )
        }
    }
}

@Composable
private fun FeeSection(
    summary: ParkingSummary,
    lots: List<ParkingLotState>,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Gebühren (€/Std.)")) {
        BasicText(tr("Parkscheinautomat am Straßenrand"), style = TmType.small)
        SegmentedControl(
            rules.parkingFeeOptions,
            rules.parkingFeeOptions.firstOrNull { it == summary.curbFee },
            ::euro,
            { onCommand(GameCommand.SetCurbFee(it)) },
            Modifier.fillMaxWidth(),
        )
        val uniform =
            rules.parkingFeeOptions.firstOrNull { fee ->
                lots.isNotEmpty() &&
                    lots.all { it.feePerHour == fee }
            }
        BasicText(
            if (uniform ==
                null
            ) {
                tr("Alle Parkplätze und Parkhäuser – gemischt, ein Klick setzt alle")
            } else {
                tr("Alle Parkplätze und Parkhäuser")
            },
            style = TmType.small,
        )
        SegmentedControl(
            rules.parkingFeeOptions,
            uniform,
            ::euro,
            { onCommand(GameCommand.SetParkingFee(null, it)) },
            Modifier.fillMaxWidth(),
        )
    }
}

internal val LotColor = TmColors.Primary
private val CurbColor = Color(0xFF2A9D8F)

/** Bezahlte Stunden je belegtem Platz und Tag: die Gebührenzeit 8–20 Uhr (SimulationConfig.paidFromHour/-UntilHour). */
internal const val PAID_HOURS = 12.0
private const val HOURS_PER_DAY = 24
private const val CHART_HEIGHT = 140
private const val DASH = 6f
private const val SHARE_WIDTH = 40
private const val MIN_DAYS = 0.1
private const val DAYS_PER_WEEK = 7
private const val SWITCH_WIDTH = 44
private const val SWITCH_HEIGHT = 24
private const val KNOB_INSET = 3

internal fun euro(amount: Double): String =
    de.bfi.trafficmaniac.i18n
        .formatEuro(amount)

private const val FIVE = 5
private const val PERCENT = 100
