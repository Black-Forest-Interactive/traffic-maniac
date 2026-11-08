package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.PrContract
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import kotlin.math.roundToInt

private val PanelBackground = ComicTheme.paper
private val PanelText = ComicTheme.ink
private val Muted = ComicTheme.muted
private val Active = ComicTheme.red
private val Idle = ComicTheme.button

/** Haushalt & PR: Agenturvertrag und Kampagne "Totschlagargumente". Abgerechnet wird sonntags. */
@Composable
fun BudgetPanel(
    state: GameState,
    rules: GameRules,
    onContract: (PrContract) -> Unit,
    onArguments: (Boolean) -> Unit,
    onCeremony: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(380.dp)
            .padding(12.dp)
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(tr("Haushalt & PR"), style = title)
        BasicText(
            tr(
                "Wöchentlicher Haushalt: +{weeklyAllowance} (sonntags um Mitternacht)",
                "weeklyAllowance" to formatEuro(rules.weeklyAllowance),
            ),
            style = muted,
        )
        PrSection(state, rules, onContract, onArguments)
        PressSection(state, rules, onCeremony)
        BudgetSummary(state)
    }
}

/** Tonlage der Zeitung, Wirkung und Preis der Story, Spatenstich. */
@Composable
private fun PressSection(
    state: GameState,
    rules: GameRules,
    onCeremony: () -> Unit,
) {
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    val press = state.press
    BasicText(
        tr(
            "Lokalzeitung: {toneLabel} · Reputation {reputation}/100",
            "toneLabel" to press.toneLabel,
            "reputation" to state.reputation.roundToInt(),
        ),
        style = text.copy(fontWeight = FontWeight.Bold),
    )
    val effect = (press.prEffect * PERCENT).roundToInt()
    BasicText(
        tr("Die Story wirkt zu {effect} %", "effect" to effect) +
            (if (effect < PERCENT) tr(" – bei so viel Leid glaubt der Agentur keiner mehr") else "") +
            tr(
                ". Aufschlag für die Lücke zwischen Story und Realität: " +
                    "+{prGapSurcharge} %",
                "prGapSurcharge" to (press.prGapSurcharge * PERCENT).roundToInt(),
            ),
        style = muted,
    )
    val next = press.nextCeremony
    if (next != null) {
        Choice(
            tr("Spatenstich / Eröffnung ({ceremonyCost})", "ceremonyCost" to formatEuro(rules.ceremonyCost)),
            active = false,
            onClick = onCeremony,
        )
        BasicText(tr("Nächster Anlass: {next}", "next" to next), style = muted)
    } else {
        BasicText(tr("Heute wurde schon gefeiert – morgen gibt es die nächste Schaufel."), style = muted)
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

private const val PERCENT = 100

/** PR-Agentur und Kampagne „Totschlagargumente“. */
@Composable
private fun PrSection(
    state: GameState,
    rules: GameRules,
    onContract: (PrContract) -> Unit,
    onArguments: (Boolean) -> Unit,
) {
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    BasicText(
        tr("PR-Agentur (Kosten je Woche) – solange bezahlt wird, schreibt die Lokalzeitung Jubel-Stories:"),
        style = text,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (contract in PrContract.entries) {
            val price = if (contract.weeklyCost > 0) " ${formatEuro(contract.weeklyCost)}" else ""
            Choice("${contract.label}$price", state.prContract == contract) {
                onContract(contract)
            }
        }
    }
    BasicText(
        tr(
            "Kampagne „Totschlagargumente“ ({argumentsWeeklyCost}/Woche):",
            "argumentsWeeklyCost" to formatEuro(rules.argumentsWeeklyCost),
        ),
        style = text,
    )
    BasicText(
        tr("„Wer sich an die Geschwindigkeit hält, muss sich um Blitzer keine Sorgen machen.“"),
        style = muted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Choice(tr("An"), state.argumentsCampaign) { onArguments(true) }
        Choice(tr("Aus"), !state.argumentsCampaign) { onArguments(false) }
    }
}

/** Stimmung, Einnahmen und die letzte Haushaltsrunde. */
@Composable
private fun BudgetSummary(state: GameState) {
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    BasicText(
        tr(
            "Unzufrieden: {unhappyShare} % der Bürger · " +
                "Zorn {anger} · Abwahlindex {recallIndex}/100",
            "unhappyShare" to (state.unhappyShare * PERCENT).roundToInt(),
            "anger" to state.anger.roundToInt(),
            "recallIndex" to state.recallIndex.roundToInt(),
        ),
        style = text,
    )
    BasicText(
        tr(
            "Einnahmen bisher: Bußgelder {fineIncome} · Parkgebühren " +
                "{feeIncome} · Knöllchen {ticketIncome}",
            "fineIncome" to formatEuro(state.fineIncome),
            "feeIncome" to
                formatEuro(
                    state.parkingOffice.feeIncome,
                ),
            "ticketIncome" to formatEuro(state.parkingOffice.ticketIncome),
        ),
        style = text,
    )
    state.lastBudgetRound?.let {
        BasicText(
            tr(
                "Letzte Haushaltsrunde ({germanName}): +{allowance}, " +
                    "PR −{prCost}, Kampagne −{argumentsCost}, " +
                    "Gehälter −{salaries}, Erhalt −{maintenance}",
                "germanName" to it.time.weekday.germanName,
                "allowance" to formatEuro(it.allowance),
                "prCost" to formatEuro(it.prCost),
                "argumentsCost" to formatEuro(it.argumentsCost),
                "salaries" to formatEuro(it.salaries),
                "maintenance" to formatEuro(it.maintenance),
            ) +
                if (it.permits >
                    0
                ) {
                    tr(", Anwohnerausweise +{permits}", "permits" to formatEuro(it.permits))
                } else {
                    ""
                },
            style = muted,
        )
    }
}
