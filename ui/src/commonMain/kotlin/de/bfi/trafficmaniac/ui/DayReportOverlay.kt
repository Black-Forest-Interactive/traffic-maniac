package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.NewspaperEdition
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.DayReport
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val Bar = TmColors.Primary
private val BarBackground = TmColors.Ground
private val DialogShape = RoundedCornerShape(18.dp)
private const val CHART_GAP_FRACTION = 0.2f
private const val AXIS_STEP_HOURS = 6
private const val PERCENT = 100
private const val DIALOG_WIDTH = 1280
private const val DIALOG_HEIGHT = 860
private const val BALANCE_WIDTH = 440
private const val CLOSE_SIZE = 44
private const val DIALOG_SHARE = 0.94f

/** Tagesbilanz für den Abschluss: Geld des Tags und die Kennzahlen vorher → nachher. */
class DayBalance(
    val income: Long,
    val expenses: Long,
    val balance: Long,
    val reputation: Pair<Int, Int>,
    val anger: Pair<Int, Int>,
    val recall: Pair<Int, Int>,
    /** Ton der Zeitung („wohlwollend“, „bissig“ …). */
    val tone: String,
    /** Offene Aufträge am Tagesende. */
    val openAssignments: Int,
) {
    /** Kasse zu Beginn des Tags: neu minus Saldo. */
    val balanceBefore: Long get() = balance - income + expenses
}

/** Was der Tagesabschluss außer „Weiter“ auslösen kann; `null` blendet den jeweiligen Knopf aus. */
class DayReportActions(
    /** Schließen (✕, Esc, Klick daneben, „Schließen“): Das Spiel bleibt stehen, bis es mit „Weiter“ losgeht. */
    val onClose: (() -> Unit)? = null,
    val onTreasury: (() -> Unit)? = null,
)

/**
 * Tagesabschluss (Entwurf 2, `V2-Zeitung`): über dem abgedunkelten Spiel links groß die Zeitung vom nächsten Morgen,
 * rechts die Tagesbilanz (Geld, Kasse, Reputation/Zorn/Abwahl alt → neu, Ton der Zeitung, Aufträge, Verkehr), unten
 * „Schließen“, „Zur Kämmerei“ und „Weiter zu Tag n ↵“. Das Spiel steht, solange der Abschluss offen ist.
 */
@Composable
fun DayReportOverlay(
    report: DayReport,
    edition: NewspaperEdition?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    /** Erster Spieltag des Szenarios. */
    firstDay: Int = 0,
    /** Woher das Blitzer-Geld kommt („82 % aus Verstößen bis 10 km/h“), sobald es welches gibt. */
    cameraSummary: String? = null,
    /** Erster Spieltag als Bezug für die Verkehrsbilanz. */
    reference: DayReport? = null,
    /** Heute freigeschaltete Erfolge. */
    achievements: List<String> = emptyList(),
    balance: DayBalance? = null,
    actions: DayReportActions = DayReportActions(),
) {
    val close = actions.onClose
    Box(
        modifier
            .fillMaxSize()
            .background(TmColors.Scrim)
            .then(
                if (close !=
                    null
                ) {
                    Modifier.clickable(interactionSource = null, indication = null, onClick = close)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(DIALOG_SHARE)
                .widthIn(max = DIALOG_WIDTH.dp)
                // Höhe nach Inhalt (Zeitung bzw. Bilanz), höchstens 860 dp
                .heightIn(max = DIALOG_HEIGHT.dp)
                .shadow(16.dp, DialogShape)
                .clip(DialogShape)
                .background(TmColors.Surface)
                // Klicks im Dialog schließen nicht
                .clickable(interactionSource = null, indication = null) {},
        ) {
            val day = report.day - firstDay + 1
            Header(report, day, close)
            ReportBody(
                report,
                edition,
                firstDay,
                cameraSummary,
                reference,
                achievements,
                balance,
                Modifier.weight(1f, fill = false),
            )
            Footer(day + 1, onContinue, actions)
        }
    }
}

/** Mitte: links die Zeitung, rechts Bilanz, Erfolge und Verkehr – beide scrollen für sich. */
@Composable
private fun ReportBody(
    report: DayReport,
    edition: NewspaperEdition?,
    firstDay: Int,
    cameraSummary: String?,
    reference: DayReport?,
    achievements: List<String>,
    balance: DayBalance?,
    modifier: Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            // Die Zeitung so hoch wie ihr Inhalt – kein Leerraum unter Artikel und Leserbriefen
            edition?.let {
                ComicNewspaper(
                    it,
                    Modifier.fillMaxWidth(),
                    firstDay,
                    large = true,
                    article = article(report, balance),
                )
            }
                ?: BasicText(tr("Heute keine Zeitung."), style = TmType.small)
        }
        Column(
            Modifier
                .width(BALANCE_WIDTH.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            balance?.let { Balance(it) }
            if (achievements.isNotEmpty()) {
                BasicText(
                    tr("Heute freigeschaltet: {titles}", "titles" to achievements.joinToString(", ")),
                    style = TmType.text(13.sp, FontWeight.Bold, TmColors.SuccessText),
                )
            }
            cameraSummary?.let { BasicText(it, style = TmType.text(13.sp, FontWeight.SemiBold)) }
            Traffic(report, reference)
        }
    }
}

@Composable
private fun Header(
    report: DayReport,
    day: Int,
    onClose: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(tr("Tagesabschluss").uppercase(), style = TmType.eyebrow)
            BasicText(
                tr("{weekday}, Tag {day}", "weekday" to report.weekday.germanName, "day" to day),
                style = TmType.display(24.sp),
            )
        }
        onClose?.let {
            Box(
                Modifier.size(CLOSE_SIZE.dp).clip(CircleShape).clickable(role = Role.Button, onClick = it),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Close, tr("Schließen (Esc)"), tint = TmColors.Muted, size = 22.dp) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
}

@Composable
private fun Footer(
    nextDay: Int,
    onContinue: () -> Unit,
    actions: DayReportActions,
) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.onClose?.let { TmButton(tr("Schließen"), it) }
        actions.onTreasury?.let { TmButton(tr("Zur Kämmerei"), it, kind = ButtonKind.TEXT) }
        Spacer(Modifier.weight(1f))
        TmButton(tr("Weiter zu Tag {day} ↵", "day" to nextDay), onContinue, kind = ButtonKind.PRIMARY)
    }
}

/** Geld des Tags, Kasse, Kennzahlen vorher → nachher, Ton der Zeitung, Aufträge. */
@Composable
private fun Balance(balance: DayBalance) {
    BasicText(tr("Tagesbilanz"), style = TmType.text(16.sp, FontWeight.Bold))
    val saldo = balance.income - balance.expenses
    MoneyLine(tr("Einnahmen"), "+" + formatEuro(balance.income), TmColors.SuccessText)
    MoneyLine(tr("Ausgaben"), "−" + formatEuro(balance.expenses), TmColors.DangerText)
    Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
    MoneyLine(
        tr("Saldo"),
        (
            if (saldo >=
                0
            ) {
                "+"
            } else {
                "−"
            }
        ) + formatEuro(abs(saldo)),
        if (saldo >= 0) TmColors.SuccessText else TmColors.DangerText,
    )
    MoneyLine(
        tr("Kasse"),
        "${formatEuro(balance.balanceBefore)} → ${formatEuro(balance.balance)}",
        if (balance.balance < 0) TmColors.DangerText else TmColors.Ink,
    )
    if (balance.balance < 0) {
        BasicText(
            tr("Die Kasse ist im Minus – eine Haushaltssperre droht."),
            style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.DangerText),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
    Change(tr("Reputation"), balance.reputation, upIsGood = true)
    Change(tr("Bürgerzorn"), balance.anger, upIsGood = false)
    Change(tr("Abwahlindex"), balance.recall, upIsGood = false)
    Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
    MoneyLine(tr("Ton der Zeitung"), balance.tone, TmColors.Ink, mono = false)
    MoneyLine(tr("Offene Aufträge"), "${balance.openAssignments}", TmColors.Ink)
}

@Composable
private fun MoneyLine(
    label: String,
    value: String,
    color: Color,
    mono: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(label, style = TmType.text(14.sp))
        BasicText(
            value,
            style = if (mono) TmType.mono(14.sp, color = color) else TmType.text(14.sp, FontWeight.SemiBold, color),
        )
    }
}

/** „Reputation 50 → 53 (+3)“, das Delta grün, wenn es gut ist. */
@Composable
private fun Change(
    label: String,
    values: Pair<Int, Int>,
    upIsGood: Boolean,
) {
    val delta = values.second - values.first
    val good = if (upIsGood) delta >= 0 else delta <= 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = TmType.text(14.sp), modifier = Modifier.weight(1f))
        BasicText("${values.first} → ${values.second}", style = TmType.mono(14.sp))
        BasicText(
            if (delta > 0) {
                " +$delta"
            } else if (delta < 0) {
                " −${-delta}"
            } else {
                " ±0"
            },
            style =
                TmType.mono(
                    12.sp,
                    color =
                        if (delta ==
                            0
                        ) {
                            TmColors.Muted
                        } else if (good) {
                            TmColors.SuccessText
                        } else {
                            TmColors.DangerText
                        },
                ),
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}

/** Verkehr des Tags: Kennzahlen, Verkehrsbilanz (wenn gemessen) und Fahrten je Stunde. */
@Composable
private fun Traffic(
    report: DayReport,
    reference: DayReport?,
) {
    // Einklappbar: die Spalte soll ohne Scrollen bis zu den Knöpfen reichen
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { open = !open },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(tr("Verkehr"), style = TmType.text(16.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
        Icon(
            if (open) Icons.ChevronUp else Icons.ChevronDown,
            tr("Ein- und ausklappen"),
            tint = TmColors.Muted,
            size = 18.dp,
        )
    }
    if (!open) return
    val text = TmType.text(13.sp)
    DayFigures(report, text)
    if (report.vehicleKm > 0.0) BasicText(trafficBalance(report, reference), style = text)
    BasicText(tr("Fahrten je Stunde"), style = text.copy(fontWeight = FontWeight.SemiBold))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HourlyChart(report.tripsStartedPerHour, Modifier.fillMaxWidth().height(80.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (hour in 0..GameTime.HOURS_PER_DAY step AXIS_STEP_HOURS) BasicText("$hour", style = TmType.caption)
        }
    }
}

/** Zwei, drei Sätze für die Zeitung aus der Tagesbilanz: Geld, Verkehr, Stimmung. */
internal fun article(
    report: DayReport,
    balance: DayBalance?,
): String {
    val traffic =
        tr(
            "{trips} Fahrten zählte die Stadt, im Schnitt {minutes} Minuten unterwegs – die meisten um {hour} Uhr.",
            "trips" to formatNumber(report.tripsStarted.toLong()),
            "minutes" to formatDecimal(report.meanTripMinutes),
            "hour" to report.peakHour,
        )
    balance ?: return traffic
    val money =
        tr(
            "Das Rathaus nahm am {weekday} {income} ein und gab {expenses} aus.",
            "weekday" to report.weekday.germanName,
            "income" to formatEuro(balance.income),
            "expenses" to formatEuro(balance.expenses),
        )
    val (before, after) = balance.anger
    val mood =
        when {
            after > before -> tr("Der Bürgerzorn stieg auf {anger}.", "anger" to after)
            after < before -> tr("Der Bürgerzorn sank auf {anger}.", "anger" to after)
            else -> tr("Der Bürgerzorn blieb bei {anger}.", "anger" to after)
        }
    return "$money $traffic $mood"
}

/**
 * Verkehrsbilanz (Lerneffekt B3): Wie viel Verkehr hat die Stadt heute erzeugt – Fahrzeugkilometer und
 * Fahrzeugstunden, ab Tag 2 im Vergleich zum ersten Tag. Der beste Verkehr ist der, der nicht stattfindet.
 */
internal fun trafficBalance(
    report: DayReport,
    reference: DayReport?,
): String {
    val line =
        tr(
            "Verkehrsbilanz: {km} Fahrzeug-km · {hours} Fahrzeugstunden",
            "km" to formatNumber(report.vehicleKm.roundToLong()),
            "hours" to formatNumber(report.vehicleHours.roundToLong()),
        )
    if (reference == null || reference.day == report.day || reference.vehicleHours <= 0.0) return line
    val change = ((report.vehicleHours / reference.vehicleHours - 1) * PERCENT).roundToInt()
    return line + " " +
        tr("({change} % Fahrzeugstunden gegenüber Tag 1)", "change" to (if (change > 0) "+$change" else "$change"))
}

/** Kennzahlen des Tags: Fahrten, Fahrzeit, Spitze. */
@Composable
private fun DayFigures(
    report: DayReport,
    text: TextStyle,
) {
    BasicText(
        tr(
            "Fahrten gestartet: {tripsStarted} · angekommen: {tripsCompleted}",
            "tripsStarted" to formatNumber(report.tripsStarted.toLong()),
            "tripsCompleted" to formatNumber(report.tripsCompleted.toLong()),
        ),
        style = text,
    )
    BasicText(
        tr(
            "Ø Fahrzeit: {meanTripMinutes} min · aufgegeben: {stuckRemovals}",
            "meanTripMinutes" to formatDecimal(report.meanTripMinutes),
            "stuckRemovals" to report.stuckRemovals,
        ),
        style = text,
    )
    BasicText(
        tr(
            "Spitze: {peakVehicles} Fahrzeuge gleichzeitig um {peakHour} Uhr",
            "peakVehicles" to formatNumber(report.peakVehicles.toLong()),
            "peakHour" to report.peakHour,
        ),
        style = text,
    )
}

@Composable
private fun HourlyChart(
    values: IntArray,
    modifier: Modifier = Modifier,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier.background(BarBackground, RoundedCornerShape(4.dp))) {
        val slot = size.width / GameTime.HOURS_PER_DAY
        val barWidth = slot * (1 - CHART_GAP_FRACTION)
        values.forEachIndexed { hour, value ->
            val height = size.height * value / max
            drawRect(
                Bar,
                topLeft = Offset(hour * slot + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
            )
        }
    }
}
