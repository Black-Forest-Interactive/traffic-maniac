package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.BudgetRound
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.PrContract
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Divider
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.StatTile
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

/** Haushalt & PR: Agenturvertrag, Kampagne „Totschlagargumente“, Presse, Einnahmen und die letzte Haushaltsrunde. */
@Composable
fun BudgetPanel(
    state: GameState,
    rules: GameRules,
    onContract: (PrContract) -> Unit,
    onArguments: (Boolean) -> Unit,
    onCeremony: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    PanelScaffold(tr("Bericht"), tr("Haushalt & PR"), modifier, onClose, floating = false) {
        PanelSection(divider = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    tr("Haushalt/Woche"),
                    "+${formatEuro(rules.weeklyAllowance)}",
                    Modifier.weight(1f),
                    TmColors.Success,
                )
                StatTile(tr("Reputation"), "${state.reputation.roundToInt()}/100", Modifier.weight(1f))
            }
            BasicText(tr("Abgerechnet wird sonntags um Mitternacht."), style = TmType.caption)
        }
        PanelSection(title = tr("PR-Agentur")) {
            BasicText(tr("Solange bezahlt wird, schreibt die Lokalzeitung Jubel-Stories."), style = TmType.small)
            SegmentedControl(
                PrContract.entries,
                state.prContract,
                { it.label },
                onContract,
                Modifier.fillMaxWidth(),
                detail = { if (it.weeklyCost > 0) tr("{cost}/Woche", "cost" to formatEuro(it.weeklyCost)) else "–" },
            )
        }
        PanelSection(title = tr("Kampagne „Totschlagargumente“")) {
            BasicText(
                tr("„Wer sich an die Geschwindigkeit hält, muss sich um Blitzer keine Sorgen machen.“"),
                style = TmType.small.copy(fontStyle = FontStyle.Italic),
            )
            SegmentedControl(
                listOf(false, true),
                state.argumentsCampaign,
                { if (it) tr("An") else tr("Aus") },
                onArguments,
                Modifier.fillMaxWidth(),
                detail = { if (it) tr("{cost}/Woche", "cost" to formatEuro(rules.argumentsWeeklyCost)) else "–" },
            )
        }
        PressSection(state, rules, onCeremony)
        IncomeSection(state)
    }
}

/** Wie gut die Story wirkt, was die Lücke zur Wirklichkeit kostet, Spatenstich. */
@Composable
private fun PressSection(
    state: GameState,
    rules: GameRules,
    onCeremony: () -> Unit,
) {
    val press = state.press
    PanelSection(title = tr("Presse")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(tr("Ton der Lokalzeitung"), style = TmType.small)
            BasicText(press.toneLabel, style = TmType.label)
        }
        val effect = (press.prEffect * PERCENT).roundToInt()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(tr("Die Story wirkt"), style = TmType.small)
            BasicText("$effect %", style = TmType.mono(12.sp))
        }
        Meter(
            press.prEffect.toFloat(),
            Modifier.fillMaxWidth(),
            color =
                if (effect <
                    PERCENT
                ) {
                    TmColors.Warn
                } else {
                    TmColors.Primary
                },
        )
        if (effect < PERCENT) {
            BasicText(tr("Bei so viel Leid glaubt der Agentur keiner mehr."), style = TmType.caption)
        }
        BasicText(
            tr(
                "Aufschlag für die Lücke zwischen Story und Realität: +{prGapSurcharge} %",
                "prGapSurcharge" to (press.prGapSurcharge * PERCENT).roundToInt(),
            ),
            style = TmType.caption,
        )
        val next = press.nextCeremony
        if (next != null) {
            TmButton(
                tr("Spatenstich / Eröffnung ({ceremonyCost})", "ceremonyCost" to formatEuro(rules.ceremonyCost)),
                onCeremony,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.PRIMARY,
            )
            BasicText(tr("Nächster Anlass: {next}", "next" to next), style = TmType.caption)
        } else {
            BasicText(tr("Heute wurde schon gefeiert – morgen gibt es die nächste Schaufel."), style = TmType.small)
        }
    }
}

/** Einnahmen bisher und die letzte Haushaltsrunde als Aufstellung. */
@Composable
private fun IncomeSection(state: GameState) {
    PanelSection(title = tr("Einnahmen bisher"), divider = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(tr("Bußgelder"), formatEuro(state.fineIncome), Modifier.weight(1f), TmColors.Success)
            StatTile(
                tr("Parkgebühren"),
                formatEuro(state.parkingOffice.feeIncome),
                Modifier.weight(1f),
                TmColors.Success,
            )
            StatTile(
                tr("Knöllchen"),
                formatEuro(state.parkingOffice.ticketIncome),
                Modifier.weight(1f),
                TmColors.Success,
            )
        }
        state.lastBudgetRound?.let { BudgetRoundLines(it) }
    }
}

@Composable
private fun BudgetRoundLines(round: BudgetRound) {
    BasicText(
        tr("Letzte Haushaltsrunde ({weekday})", "weekday" to round.time.weekday.germanName),
        style = TmType.label,
    )
    Line(tr("Haushalt"), round.allowance)
    if (round.permits > 0) Line(tr("Anwohnerausweise"), round.permits)
    Line(tr("PR-Agentur"), -round.prCost)
    Line(tr("Kampagne"), -round.argumentsCost)
    Line(tr("Gehälter"), -round.salaries)
    Line(tr("Erhalt"), -round.maintenance)
    Divider()
    Line(
        tr("Saldo"),
        round.allowance + round.permits - round.prCost - round.argumentsCost - round.salaries - round.maintenance,
    )
}

@Composable
private fun Line(
    label: String,
    amount: Long,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(label, style = TmType.text(13.sp))
        BasicText(
            (if (amount > 0) "+" else "") + formatEuro(amount),
            style = TmType.mono(13.sp, bold = false, color = if (amount < 0) TmColors.DangerText else TmColors.Success),
        )
    }
}

private const val PERCENT = 100
