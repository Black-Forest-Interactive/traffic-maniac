package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageResponse
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.MaintenanceBudget
import de.bfi.trafficmaniac.game.MaintenanceState
import de.bfi.trafficmaniac.game.RepairMethod
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.OptionButton
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.StatTile
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
        DamageList(state, rules, onCommand, onShow)
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
                ExpandedDamage(report, rules, { onCommand(GameCommand.RespondToDamage(report.id, it)) }) {
                    onShow(report)
                }
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

@Composable
private fun ExpandedDamage(
    report: DamageReport,
    rules: GameRules,
    respond: (DamageResponse) -> Unit,
    onShow: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().border(2.dp, TmColors.Ink, CardShape).padding(14.dp),
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
            RepairActions(report, rules, respond)
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
            TmButton(tr("Auf Karte zeigen"), onShow, kind = ButtonKind.TEXT)
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
) {
    val base = report.repairCost
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OptionButton(
            tr("Ausschreibung"),
            { respond(DamageResponse.Repair(RepairMethod.TENDER)) },
            Modifier.weight(1f),
            formatEuro(base),
        )
        OptionButton(
            tr("Turbo"),
            { respond(DamageResponse.Repair(RepairMethod.TURBO)) },
            Modifier.weight(1f),
            formatEuro((base * rules.turboCostFactor).toLong()),
        )
        if (report.status != DamageStatus.PLANNING) {
            OptionButton(
                tr("Planfeststellung"),
                { respond(DamageResponse.Repair(RepairMethod.PLANNING)) },
                Modifier.weight(1f),
                formatEuro(rules.planningCost),
            )
        }
    }
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
