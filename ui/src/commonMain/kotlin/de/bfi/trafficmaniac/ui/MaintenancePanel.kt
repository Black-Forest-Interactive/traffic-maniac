package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import de.bfi.trafficmaniac.sim.DamageKind
import kotlin.math.roundToInt

private val PanelBackground = ComicTheme.paper
private val PanelText = ComicTheme.ink
private val Muted = ComicTheme.muted
private val Warning = ComicTheme.orange
private val Danger = ComicTheme.red
private val Active = ComicTheme.red
private val Idle = ComicTheme.button
private const val MAX_REPORTS = 6
private val SIGN_LIMITS = listOf(30, 10)

/**
 * Das Bauamt: Schadensmeldungen mit Frist und die Reaktion darauf – reparieren (drei Wege),
 * Warnschild mit Tempolimit, Brücke sperren oder nichts tun. Dazu das Erhaltungsbudget.
 */
@Composable
fun MaintenancePanel(
    state: MaintenanceState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
    /** Klick auf eine Meldung: die Karte zeigt die Stelle. */
    onShow: (DamageReport) -> Unit = {},
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(460.dp)
            .padding(12.dp)
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(tr("Bauamt"), style = title)
        BasicText(
            tr(
                "Straßenzustand im Mittel: {meanConditionPercent} % · repariert {repaired} · " +
                    "Klagen {lawsuits} ({lawsuitCosts}) · Turbo-Wartungsverträge {turboContracts}",
                "meanConditionPercent" to state.meanConditionPercent,
                "repaired" to state.repaired,
                "lawsuits" to state.lawsuits,
                "lawsuitCosts" to
                    formatEuro(
                        state.lawsuitCosts,
                    ),
                "turboContracts" to state.turboContracts,
            ),
            style = muted,
        )
        BasicText(tr("Erhaltungsbudget pro Woche:"), style = text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (budget in MaintenanceBudget.entries) {
                val cost =
                    when (budget) {
                        MaintenanceBudget.NONE -> 0L
                        MaintenanceBudget.BASIC -> rules.maintenanceBudgetBasic
                        MaintenanceBudget.FULL -> rules.maintenanceBudgetFull
                    }
                Choice("${budget.label} (${formatEuro(cost)})", state.budget == budget) {
                    onCommand(GameCommand.SetMaintenanceBudget(budget))
                }
            }
        }
        val reports =
            state.reports
                .sortedWith(compareBy({ it.status != DamageStatus.OPEN }, { it.hoursLeft }))
                .take(MAX_REPORTS)
        if (reports.isEmpty()) BasicText(tr("Keine Schadensmeldungen – noch."), style = muted)
        for (report in reports) ReportRow(report, rules, onCommand, text, muted, onShow)
        if (state.reports.size >
            MAX_REPORTS
        ) {
            BasicText(tr("… und {reports} weitere", "reports" to state.reports.size - MAX_REPORTS), style = muted)
        }
    }
}

@Composable
private fun ReportRow(
    report: DamageReport,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    text: TextStyle,
    muted: TextStyle,
    onShow: (DamageReport) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(
            "▸ " + headline(report),
            style = text.copy(color = colorOf(report), fontWeight = FontWeight.Bold),
            modifier = Modifier.clickable { onShow(report) },
        )
        BasicText(statusText(report), style = muted)
        val busy = report.status == DamageStatus.TENDER || report.status == DamageStatus.CONSTRUCTION
        if (report.isActionable && !busy) {
            RepairActions(report, rules) { onCommand(GameCommand.RespondToDamage(report.id, it)) }
            OtherActions(report) { onCommand(GameCommand.RespondToDamage(report.id, it)) }
        }
    }
}

private fun headline(report: DamageReport): String {
    val sections =
        if (report.segments.size >
            1
        ) {
            tr(", {segments} Abschnitte", "segments" to report.segments.size)
        } else {
            ""
        }
    return "${report.kind.label}: ${report.place} (${report.conditionPercent} %$sections)"
}

private fun colorOf(report: DamageReport): Color {
    val severe = report.kind == DamageKind.CRATER || report.kind == DamageKind.BRIDGE_UNSAFE
    return when {
        report.status == DamageStatus.COLLAPSED || severe -> Danger
        report.status == DamageStatus.OPEN -> Warning
        else -> PanelText
    }
}

private fun statusText(report: DamageReport): String {
    val status =
        when (report.status) {
            DamageStatus.OPEN -> {
                tr(
                    "Frist: noch {hoursLeft} Std.",
                    "hoursLeft" to report.hoursLeft.coerceAtLeast(0.0).roundToInt(),
                )
            }

            DamageStatus.SIGNED -> {
                tr("Warnschild, Tempo {signKmh}", "signKmh" to (report.signKmh ?: "–"))
            }

            else -> {
                report.status.label
            }
        }
    return status + (report.progress?.let { " · $it" } ?: "")
}

/** Ausschreibung, Turbo-Baukonsortium, Planfeststellung. */
@Composable
private fun RepairActions(
    report: DamageReport,
    rules: GameRules,
    respond: (DamageResponse) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val base = report.repairCost
        Choice(tr("Ausschreibung {base}", "base" to formatEuro(base)), false) {
            respond(DamageResponse.Repair(RepairMethod.TENDER))
        }
        Choice(
            tr("Turbo {turboCostFactor}", "turboCostFactor" to formatEuro((base * rules.turboCostFactor).toLong())),
            false,
        ) {
            respond(DamageResponse.Repair(RepairMethod.TURBO))
        }
        if (report.status != DamageStatus.PLANNING) {
            Choice(tr("Planfeststellung {planningCost}", "planningCost" to formatEuro(rules.planningCost)), false) {
                respond(DamageResponse.Repair(RepairMethod.PLANNING))
            }
        }
    }
}

/** Warnschild, Brücke sperren, nichts tun. */
@Composable
private fun OtherActions(
    report: DamageReport,
    respond: (DamageResponse) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val unsigned = report.canSign && report.status != DamageStatus.SIGNED
        when {
            unsigned && report.node >= 0 -> {
                Choice(
                    tr("Schild „Ampel ausgefallen“"),
                    false,
                ) { respond(DamageResponse.WarningSign(0)) }
            }

            unsigned -> {
                SIGN_LIMITS.forEach { kmh ->
                    Choice(tr("Schild {kmh}", "kmh" to kmh), false) { respond(DamageResponse.WarningSign(kmh)) }
                }
            }
        }
        if (report.canClose &&
            report.status != DamageStatus.CLOSED
        ) {
            Choice(tr("Sperren"), false) { respond(DamageResponse.Close) }
        }
        if (report.status == DamageStatus.OPEN) Choice(tr("Nichts tun"), false) { respond(DamageResponse.Ignore) }
    }
}

@Composable
private fun Choice(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        style =
            TextStyle(
                color = PanelText,
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            ),
        modifier =
            Modifier
                .comicButton(active)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
