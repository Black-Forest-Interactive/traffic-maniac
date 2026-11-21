package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Booking
import de.bfi.trafficmaniac.game.BookingCategory
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.TreasuryJournal
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import kotlin.math.abs
import kotlin.math.roundToInt

private val IncomeColor = Color(0xFF2F6BD0)
private val ExpenseColor = Color(0xFFD9701F)

/**
 * Kämmerei (Entwurf 2, `V2-Kaemmerei`): Geldfluss pro Stunde, woher das Geld kommt und wohin es geht, wo es verdient
 * wird, die Buchungen und die Haushaltsrunde. Alles ist eine Auswertung des Journals der Stadtkasse.
 */
@Composable
internal fun TreasuryPanel(
    state: GameState,
    rules: GameRules,
    onClose: () -> Unit,
    /** Uhrzeit fürs „Nächste Abrechnung in …“. */
    now: GameTime? = null,
    /** Klick auf eine Buchung mit Ort: die Karte zeigt die Stelle. */
    onPlace: ((Int) -> Unit)? = null,
) {
    val journal = state.journal
    PanelScaffold(tr("Rathaus"), tr("Kämmerei"), onClose = onClose, floating = false) {
        PanelSection(title = tr("Geldfluss pro Stunde")) {
            Period(tr("letzte 24 Std."))
            HourlyFlow(journal)
        }
        PanelSection(title = tr("Woher kommt das Geld?")) {
            Period(tr("Amtszeit"))
            IncomeBars(journal.totals, rules, now)
        }
        PanelSection(title = tr("Wohin geht es?")) {
            Period(tr("Amtszeit"))
            ExpenseBars(journal.expenseItems)
        }
        PanelSection(title = tr("Wo wird verdient?")) { Places(state) }
        PanelSection(title = tr("Buchungen")) {
            Period(tr("die letzten {count}", "count" to journal.bookings.size))
            Bookings(journal.bookings, onPlace)
        }
        PanelSection(title = tr("Haushaltsrunde"), divider = false) { BudgetRound(state, rules, now) }
    }
}

/** Einnahmen nach oben (blau), Ausgaben nach unten (orange) an einer Achse, die laufende Stunde rechts. */
@Composable
private fun HourlyFlow(journal: TreasuryJournal) {
    val income = journal.hourlyIncome
    val expenses = journal.hourlyExpenses
    if (income.isEmpty() || (income.sum() == 0L && expenses.sum() == 0L)) {
        BasicText(tr("In den letzten 24 Stunden ist kein Geld geflossen."), style = TmType.small)
        return
    }
    val max = maxOf(income.max(), expenses.max()).coerceAtLeast(1)
    val measurer = rememberTextMeasurer()
    val axis = TmType.caption
    Canvas(Modifier.fillMaxWidth().height(FLOW_HEIGHT.dp)) {
        // Links Platz für die Werte der y-Achse, unten für die Uhrzeiten
        val left = AXIS_WIDTH.dp.toPx()
        val bottom = HOUR_LABELS.dp.toPx()
        val width = size.width - left
        val height = size.height - bottom
        val slot = width / income.size
        val bar = slot * BAR_SHARE
        val middle = height / 2
        drawLine(TmColors.Line, Offset(left, middle), Offset(size.width, middle), 1.dp.toPx())
        for ((text, y) in listOf("+" + formatEuro(max) to 0f, "0" to middle, "−" + formatEuro(max) to height)) {
            val label = measurer.measure(text, axis)
            drawText(label, topLeft = Offset(0f, (y - label.size.height / 2f).coerceIn(0f, height - label.size.height)))
        }
        for (i in income.indices) {
            val x = left + i * slot + (slot - bar) / 2
            val up = middle * income[i] / max
            val down = middle * expenses[i] / max
            drawRect(IncomeColor, Offset(x, middle - up), Size(bar, up))
            drawRect(ExpenseColor, Offset(x, middle), Size(bar, down))
            // Uhrzeiten 0, 6, 12, 18 an ihrer Stunde
            val hour = (journal.lastHour - (income.size - 1 - i)).mod(GameTime.HOURS_PER_DAY)
            if (hour % HOUR_STEP == 0) {
                val label = measurer.measure("$hour Uhr", axis)
                drawText(label, topLeft = Offset(x + bar / 2 - label.size.width / 2f, height + 2.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(IncomeColor, tr("Einnahmen {euro}", "euro" to formatEuro(income.sum())))
            Legend(ExpenseColor, tr("Ausgaben {euro}", "euro" to formatEuro(expenses.sum())))
        }
    }
}

@Composable
private fun Legend(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.width(10.dp).height(10.dp)) { drawRect(color) }
        BasicText(text, style = TmType.caption)
    }
}

/** Einnahmen der Amtszeit je Posten – die erwarteten auch mit 0 €, der Haushalt mit der nächsten Überweisung. */
@Composable
private fun IncomeBars(
    totals: Map<BookingCategory, Long>,
    rules: GameRules,
    now: GameTime?,
) {
    val shown =
        INCOME.associateWith { totals[it] ?: 0L } +
            totals.filterKeys { it.income && it !in INCOME && (totals[it] ?: 0L) > 0 }
    val max = shown.values.max().coerceAtLeast(1)
    for ((category, amount) in shown) {
        val hint =
            if (category ==
                BookingCategory.ALLOWANCE
            ) {
                // Gerade nach Mitternacht ist die Rate schon da – die nächste kommt erst morgen
                val justPaid = now != null && now.hour == 0
                tr(
                    "nächste: +{euro} {when}",
                    "euro" to formatEuro(rules.weeklyAllowance / DAYS_PER_WEEK),
                    "when" to if (justPaid) tr("morgen 00:00") else tr("heute Nacht"),
                )
            } else {
                null
            }
        BarRow(category.label, amount, max, IncomeColor, hint)
    }
}

/** Ausgaben der Amtszeit nach Posten (Blitzer, Schilder, Busspur, Personal …): die größten sechs, der Rest zusammen. */
@Composable
private fun ExpenseBars(items: Map<String, Long>) {
    if (items.isEmpty()) {
        BasicText(tr("Noch nichts ausgegeben."), style = TmType.small)
        return
    }
    val sorted = items.entries.sortedByDescending { it.value }
    // Die sechs größten Posten, dazu der größte vom Rest einzeln – „Sonstiges“ soll nicht der größte Balken sein
    val shown = sorted.take(MAX_ITEMS + 1).map { it.key to it.value }
    val rest = sorted.drop(MAX_ITEMS + 1).sumOf { it.value }
    val rows = if (rest > 0) shown + (tr("Sonstiges") to rest) else shown
    val max = rows.maxOf { it.second }
    for ((label, amount) in rows) BarRow(label, amount, max, ExpenseColor)
}

@Composable
private fun BarRow(
    label: String,
    amount: Long,
    max: Long,
    color: Color,
    hint: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(LABEL_WIDTH.dp)) {
            BasicText(label, style = TmType.text(13.sp), maxLines = 1)
            hint?.let { BasicText(it, style = TmType.caption, maxLines = 1) }
        }
        Meter(amount.toFloat() / max, Modifier.weight(1f), height = 8.dp, color = color)
        BasicText(formatEuro(amount), style = TmType.mono(12.sp), modifier = Modifier.width(AMOUNT_WIDTH.dp))
    }
}

/** Einnahmen, die immer in der Liste stehen – auch bevor etwas hereinkommt. */
private val INCOME =
    listOf(
        BookingCategory.ALLOWANCE,
        BookingCategory.FINES,
        BookingCategory.TICKETS,
        BookingCategory.PARKING,
        BookingCategory.GRANTS,
    )

/** Die einträglichsten Blitzer-Standorte mit Anteil an allen Bußgeldern. */
@Composable
private fun Places(state: GameState) {
    Period(tr("Amtszeit"))
    val fines = state.journal.finesByStreet.filterValues { it > 0 }
    val tickets = state.journal.totals[BookingCategory.TICKETS] ?: 0L
    // Summe der Orte = Blitzer-Bußgelder; Knöllchen als eigene Zeile
    val rows =
        fines.entries.sortedByDescending { it.value }.map { (street, amount) ->
            (street.ifEmpty { tr("ohne Ort (Polizeiaktion)") }) to amount
        } + listOfNotNull((tr("Ordnungsamt (Knöllchen)") to tickets).takeIf { tickets > 0 })
    if (rows.isEmpty()) {
        BasicText(tr("Noch kein Blitzer hat etwas eingebracht."), style = TmType.small)
        return
    }
    val total = rows.sumOf { it.second }.coerceAtLeast(1)
    for ((label, amount) in rows.take(MAX_PLACES)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                label,
                style = TmType.text(13.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(formatEuro(amount), style = TmType.mono(12.sp))
            BasicText(
                // Kleine Posten nicht als „0 %“ – sie bringen ja etwas
                (amount * PERCENT / total).let { if (it == 0L && amount > 0) "< 1 %" else "$it %" },
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
                modifier = Modifier.width(SHARE_WIDTH.dp),
            )
        }
    }
}

/** Zeitraum einer Karte, grau über dem Inhalt: „heute“, „letzte 24 Std.“, „Amtszeit“. */
@Composable
private fun Period(text: String) {
    BasicText(text, style = TmType.caption)
}

/**
 * Journal: Zeit · Buchung · Ort · Betrag, Filter Alle / Einnahmen / Ausgaben. Kleine Einnahmen sind je Stunde
 * gebündelt.
 */
@Composable
private fun Bookings(
    bookings: List<Booking>,
    onPlace: ((Int) -> Unit)?,
) {
    var filter by remember { mutableStateOf<Boolean?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((value, label) in listOf(null to tr("Alle"), true to tr("Einnahmen"), false to tr("Ausgaben"))) {
            val chosen = filter == value
            BasicText(
                label,
                style = TmType.text(12.sp, FontWeight.SemiBold, if (chosen) TmColors.Surface else TmColors.Ink),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (chosen) TmColors.Ink else TmColors.Ground)
                        .clickable(role = Role.RadioButton) { filter = value }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    val shown = bookings.filter { filter == null || (it.amount > 0) == filter }.take(MAX_BOOKINGS)
    if (shown.isEmpty()) BasicText(tr("Noch nichts gebucht."), style = TmType.small)
    // Die Liste scrollt in ihrer Karte, statt die Seite zu verlängern
    Column(Modifier.heightIn(max = BOOKINGS_HEIGHT.dp).verticalScroll(rememberScrollState())) {
        for (booking in shown) BookingRow(booking, onPlace)
    }
}

@Composable
private fun BookingRow(
    booking: Booking,
    onPlace: ((Int) -> Unit)?,
) {
    val place = booking.place
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (place != null &&
                    onPlace != null
                ) {
                    Modifier.clickable(role = Role.Button) { onPlace(place.segment) }
                } else {
                    Modifier
                },
            ).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            if (booking.startup) tr("Start") else clock(booking.time),
            style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
            modifier = Modifier.width(TIME_WIDTH.dp),
        )
        Column(Modifier.weight(1f)) {
            val what = booking.text.ifEmpty { booking.category.label }
            BasicText(
                if (booking.count > 1) "${booking.count}× $what" else what,
                style = TmType.text(13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            place?.let { BasicText(it.street, style = TmType.caption, maxLines = 1) }
        }
        BasicText(
            (if (booking.amount > 0) "+" else "−") + formatEuro(abs(booking.amount)),
            style = TmType.mono(12.sp, color = if (booking.amount > 0) TmColors.SuccessText else TmColors.DangerText),
        )
    }
}

/** „07:30“ aus Simulationssekunden. */
private fun clock(seconds: Double): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).roundToInt()
    val hour = (minutes / MINUTES_PER_HOUR) % GameTime.HOURS_PER_DAY
    return "${hour.toString().padStart(2, '0')}:${(minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
}

/** Jede Nacht ein Siebtel der Wochenbeträge: wann die nächste Abrechnung kommt, was danach in der Kasse ist. */
@Composable
private fun BudgetRound(
    state: GameState,
    rules: GameRules,
    now: GameTime?,
) {
    Line(tr("Haushalt je Woche"), "+" + formatEuro(rules.weeklyAllowance), TmColors.SuccessText)
    now?.let {
        val minutes = MINUTES_PER_DAY - (it.hour * MINUTES_PER_HOUR + it.minute)
        Line(
            tr("Nächste Abrechnung in"),
            tr(
                "{hours}:{minutes} h",
                "hours" to minutes / MINUTES_PER_HOUR,
                "minutes" to (minutes % MINUTES_PER_HOUR).toString().padStart(2, '0'),
            ),
            TmColors.Ink,
        )
    }
    // Prognose: wie die letzte Runde, ohne eine bisher nur der Haushalt
    val round = state.lastBudgetRound
    val net =
        round?.let { it.allowance + it.permits - it.prCost - it.argumentsCost - it.salaries - it.maintenance }
            ?: (rules.weeklyAllowance / DAYS_PER_WEEK)
    val after = state.balance + net
    Line(tr("Kasse danach (Prognose)"), formatEuro(after), if (after < 0) TmColors.DangerText else TmColors.Ink)
    BasicText(
        tr("Abgerechnet wird jede Nacht um Mitternacht – je ein Siebtel der Wochenbeträge."),
        style = TmType.caption,
    )
    round?.let { BudgetRoundLines(it) }
}

@Composable
private fun Line(
    label: String,
    value: String,
    color: Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(label, style = TmType.text(13.sp))
        BasicText(value, style = TmType.mono(13.sp, color = color))
    }
}

private const val FLOW_HEIGHT = 170
private const val BAR_SHARE = 0.7f
private const val LABEL_WIDTH = 150
private const val AMOUNT_WIDTH = 90
private const val SHARE_WIDTH = 44
private const val MAX_PLACES = 6
private const val MAX_BOOKINGS = 60
private const val BOOKINGS_HEIGHT = 420
private const val TIME_WIDTH = 44
private const val AXIS_WIDTH = 72
private const val HOUR_LABELS = 18
private const val HOUR_STEP = 6
private const val MAX_ITEMS = 6
private const val DAYS_PER_WEEK = 7
private const val MINUTES_PER_DAY = 1_440
private const val PERCENT = 100
private const val SECONDS_PER_MINUTE = 60.0
private const val MINUTES_PER_HOUR = 60
