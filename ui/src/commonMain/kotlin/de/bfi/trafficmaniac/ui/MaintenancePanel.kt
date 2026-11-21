package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageResponse
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.MaintenanceBudget
import de.bfi.trafficmaniac.game.MaintenanceState
import de.bfi.trafficmaniac.game.RepairMethod
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.OptionButton
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.SettingRow
import de.bfi.trafficmaniac.ui.components.StatTile
import de.bfi.trafficmaniac.ui.components.Stepper
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(12.dp)
private val SIGN_LIMITS = listOf(30, 10)

/**
 * Das Bauamt: Zustand, Reparaturen und Klagen, das Erhaltungsbudget und die offenen Schäden als Akkordeon – immer
 * nur einer aufgeklappt, mit den Wegen zur Reparatur und den Möglichkeiten, die Stelle abzusichern.
 */
@Composable
fun MaintenancePanel(
    state: MaintenanceState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
    /** „Auf Karte zeigen“: die Karte fliegt zur Stelle. */
    onShow: (DamageReport) -> Unit = {},
    onClose: (() -> Unit)? = null,
    /** Räumfahrzeuge im Winterdienst; `null` = Szenario ohne Schnee. */
    ploughs: Int? = null,
) {
    PanelScaffold(tr("Amt"), tr("Bauamt"), modifier, onClose, floating = false) {
        PanelSection(divider = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(tr("Zustand Ø"), "${state.meanConditionPercent} %", Modifier.weight(1f))
                StatTile(tr("Repariert"), "${state.repaired}", Modifier.weight(1f))
                StatTile(tr("Klagen"), "${state.lawsuits} · ${formatEuro(state.lawsuitCosts)}", Modifier.weight(1f))
            }
            if (state.turboContracts > 0) {
                BasicText(
                    tr("Turbo-Wartungsverträge: {count}", "count" to state.turboContracts),
                    style = TmType.caption,
                )
            }
        }
        PanelSection(title = tr("Erhaltungsbudget pro Woche")) {
            SegmentedControl(
                MaintenanceBudget.entries,
                state.budget,
                { it.label },
                { onCommand(GameCommand.SetMaintenanceBudget(it)) },
                Modifier.fillMaxWidth(),
                detail = { formatEuro(budgetCost(it, rules)) },
            )
        }
        ploughs?.let { WinterService(it, rules, onCommand) }
        DamageList(state, rules, onCommand, onShow)
    }
}

/** Winterdienst: wie viele Räumfahrzeuge räumen und streuen (kosten je Tag). */
@Composable
private fun WinterService(
    ploughs: Int,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Winterdienst")) {
        SettingRow(
            tr("Räumfahrzeuge"),
            hint =
                tr(
                    "{cost}/Tag je Fahrzeug · Hauptstraßen zuerst, im Stau stecken sie mit fest",
                    "cost" to formatEuro(rules.ploughDailyCost),
                ),
        ) {
            Stepper(
                "$ploughs",
                onMinus = { onCommand(GameCommand.SetPloughs(ploughs - 1)) }.takeIf { ploughs > 0 },
                onPlus = { onCommand(GameCommand.SetPloughs(ploughs + 1)) }.takeIf { ploughs < rules.maxPloughs },
            )
        }
    }
}

private fun budgetCost(
    budget: MaintenanceBudget,
    rules: GameRules,
): Long =
    when (budget) {
        MaintenanceBudget.NONE -> 0L
        MaintenanceBudget.BASIC -> rules.maintenanceBudgetBasic
        MaintenanceBudget.FULL -> rules.maintenanceBudgetFull
    }

@Composable
private fun DamageList(
    state: MaintenanceState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    onShow: (DamageReport) -> Unit,
) {
    val reports = state.reports.sortedWith(compareBy({ it.status != DamageStatus.OPEN }, { it.hoursLeft }))
    var expanded by remember { mutableStateOf<Int?>(null) }
    val open = reports.firstOrNull { it.id == expanded } ?: reports.firstOrNull()
    PanelSection(divider = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(tr("Offene Schäden"), style = TmType.label, modifier = Modifier.weight(1f))
            if (reports.isNotEmpty()) Badge("${reports.size}", tone = BadgeTone.DANGER)
        }
        if (reports.isEmpty()) BasicText(tr("Keine Schadensmeldungen – noch."), style = TmType.small)
        for (report in reports.take(MAX_REPORTS)) {
            if (report == open) {
                ExpandedDamage(
                    report,
                    rules,
                    { onCommand(GameCommand.RespondToDamage(report.id, it)) },
                    { onShow(report) },
                )
            } else {
                CollapsedDamage(report) { expanded = report.id }
            }
        }
        if (reports.size > MAX_REPORTS) {
            BasicText(tr("… und {reports} weitere", "reports" to reports.size - MAX_REPORTS), style = TmType.small)
        }
    }
}

@Composable
private fun CollapsedDamage(
    report: DamageReport,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, TmColors.Track, CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText("${report.kind.label} · ${report.place}", style = TmType.text(13.sp, FontWeight.Bold))
            BasicText(tr("Zustand {percent} %", "percent" to report.conditionPercent), style = TmType.small)
        }
        StatusBadge(report)
        Icon(Icons.ChevronDown, null, tint = TmColors.Muted, size = 16.dp)
    }
}

/** Ein Schaden mit allen Möglichkeiten – im Bauamt und als Aktionskarte auf der Karte (dort ohne [onShow]). */
@Composable
internal fun ExpandedDamage(
    report: DamageReport,
    rules: GameRules,
    respond: (DamageResponse) -> Unit,
    onShow: (() -> Unit)?,
    /** Mit eigenem Rahmen (in der Liste des Bauamts); im Inspektor ohne – der hat schon einen. */
    framed: Boolean = true,
    /** Kassenstand: Was mehr kostet, ist aus und nennt, was fehlt. */
    balance: Long = Long.MAX_VALUE,
) {
    Column(
        Modifier.fillMaxWidth().then(
            if (framed) Modifier.border(2.dp, TmColors.Ink, CardShape).padding(14.dp) else Modifier,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(report.kind.label, style = TmType.text(14.sp, FontWeight.Bold))
                BasicText(placeLine(report), style = TmType.small)
                report.progress?.let { BasicText(it, style = TmType.small) }
            }
            StatusBadge(report)
        }
        val actionable =
            report.isActionable && report.status != DamageStatus.TENDER &&
                report.status != DamageStatus.CONSTRUCTION
        if (actionable) {
            GroupLabel(tr("Reparieren"))
            RepairActions(report, rules, respond, balance)
            GroupLabel(tr("Oder absichern"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (actionable) SecureActions(report, respond)
            }
            onShow?.let { TmButton(tr("Auf Karte zeigen"), it, kind = ButtonKind.TEXT) }
        }
    }
}

private fun placeLine(report: DamageReport): String {
    val sections =
        if (report.segments.size >
            1
        ) {
            tr(", {segments} Abschnitte", "segments" to report.segments.size)
        } else {
            ""
        }
    return tr("{place} · Zustand {percent} %", "place" to report.place, "percent" to report.conditionPercent) + sections
}

@Composable
private fun GroupLabel(text: String) {
    BasicText(text.uppercase(), style = TmType.eyebrow.copy(letterSpacing = GROUP_SPACING.sp))
}

/** Frist als gelbes Etikett, sonst der Stand („Warnschild“, „Baustelle“ …). */
@Composable
private fun StatusBadge(report: DamageReport) {
    when (report.status) {
        DamageStatus.OPEN -> {
            val hours = report.hoursLeft.coerceAtLeast(0.0).roundToInt()
            Badge(tr("noch {hours} Std.", "hours" to hours), tone = BadgeTone.WARN, pill = false)
        }

        DamageStatus.COLLAPSED -> {
            Badge(report.status.label, tone = BadgeTone.DANGER, pill = false)
        }

        DamageStatus.SIGNED -> {
            Badge(tr("Schild, Tempo {kmh}", "kmh" to (report.signKmh ?: "–")), pill = false)
        }

        else -> {
            Badge(report.status.label, pill = false)
        }
    }
}

/** Ausschreibung, Turbo-Baukonsortium, Planfeststellung – jeweils mit Preis. */
@Composable
private fun RepairActions(
    report: DamageReport,
    rules: GameRules,
    respond: (DamageResponse) -> Unit,
    balance: Long,
) {
    val base = report.repairCost
    // Der Preis wächst mit der Länge – das soll man sehen
    if (report.segments.size > 1) {
        BasicText(
            tr(
                "Preise für {count} Abschnitte (+{percent} % je weiterem)",
                "count" to report.segments.size,
                "percent" to (rules.repairCostPerExtraSegment * PERCENT).roundToInt(),
            ),
            style = TmType.caption,
        )
    }
    if (report.canPatch) {
        Option(
            tr("Flicken (Kaltasphalt, sofort)"),
            rules.patchCost,
            balance,
            tr("sofort · hält kurz (doppelter Verschleiß)"),
            Modifier.fillMaxWidth(),
        ) {
            respond(DamageResponse.Patch)
        }
    }
    // Gleich hohe Karten nebeneinander, kurze Zusätze
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val days = (rules.tenderDays + rules.constructionDays).roundToInt()
        Option(
            tr("Ausschreibung"),
            base,
            balance,
            tr("{days} Tage · wie neu", "days" to days),
            Modifier.weight(1f).fillMaxHeight(),
        ) {
            respond(DamageResponse.Repair(RepairMethod.TENDER))
        }
        Option(
            tr("Turbo"),
            (base * rules.turboCostFactor).toLong(),
            balance,
            tr("schnell · kurzlebig"),
            Modifier.weight(1f).fillMaxHeight(),
        ) {
            respond(DamageResponse.Repair(RepairMethod.TURBO))
        }
        if (report.status != DamageStatus.PLANNING) {
            val year = tr("fertig {year}", "year" to rules.planningStartYear)
            Option(tr("Planfeststellung"), rules.planningCost, balance, year, Modifier.weight(1f).fillMaxHeight()) {
                respond(DamageResponse.Repair(RepairMethod.PLANNING))
            }
        }
    }
}

/** Option mit Preis; reicht die Kasse nicht, ist sie aus und sagt, wie viel fehlt. */
@Composable
private fun Option(
    label: String,
    cost: Long,
    balance: Long,
    detail: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val missing = cost - balance
    OptionButton(
        label,
        onClick,
        modifier,
        if (missing >
            0
        ) {
            // Zwei Zeilen statt Umbruch mitten im Text
            formatEuro(cost) + "\n" + tr("fehlen {missing}", "missing" to formatEuro(missing))
        } else {
            "${formatEuro(cost)} · $detail"
        },
        enabled = missing <= 0,
    )
}

/** Warnschild, Brücke sperren, nichts tun. */
@Composable
private fun SecureActions(
    report: DamageReport,
    respond: (DamageResponse) -> Unit,
) {
    val unsigned = report.canSign && report.status != DamageStatus.SIGNED
    if (unsigned && report.node >= 0) {
        OptionButton(tr("Schild „Ampel ausgefallen“"), { respond(DamageResponse.WarningSign(0)) })
    } else if (unsigned) {
        for (kmh in SIGN_LIMITS) {
            OptionButton(tr("Schild {kmh}", "kmh" to kmh), { respond(DamageResponse.WarningSign(kmh)) })
        }
    }
    if (report.canClose && report.status != DamageStatus.CLOSED) {
        OptionButton(tr("Sperren"), { respond(DamageResponse.Close) })
    }
    if (report.status == DamageStatus.OPEN) {
        BasicText(
            tr("Nichts tun"),
            style =
                TmType
                    .text(12.sp, FontWeight.SemiBold, TmColors.Muted)
                    .copy(textDecoration = TextDecoration.Underline),
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { respond(DamageResponse.Ignore) }
                    .padding(10.dp),
        )
    }
}

private const val MAX_REPORTS = 8
private const val GROUP_SPACING = 0.6f

/** Was die Seite „Bauamt“ braucht. */
internal class MaintenancePageData(
    val game: GameState,
    val snapshot: SimulationSnapshot,
    val city: CityMap,
    val rules: GameRules,
    val onCommand: (GameCommand) -> Unit,
    val onShow: (DamageReport) -> Unit,
)

/** Vorgaben für Screenshots: so viele Schäden ausgewählt, den ersten geöffnet, die Statistik bis unten gescrollt. */
internal class MaintenancePreset(
    val select: Int = 0,
    val open: Boolean = false,
    val statsToEnd: Boolean = false,
    /** Die Tabelle bis ganz unten gescrollt (prüft, dass die Sammelleiste die letzte Zeile frei lässt). */
    val tableToEnd: Boolean = false,
)

internal val LocalMaintenancePreset = staticCompositionLocalOf { MaintenancePreset() }

/** Verfall je Tag (Anteil) im Mittel der Abschnitte des Schadens. */
internal fun decayOf(
    report: DamageReport,
    data: MaintenancePageData,
    days: Double,
): Double? =
    report.segments
        .map {
            data.snapshot.wear.bySegment
                .getOrElse(it) { 0f }
        }.average()
        .takeIf { !it.isNaN() }
        ?.div(days)

private const val PERCENT = 100
