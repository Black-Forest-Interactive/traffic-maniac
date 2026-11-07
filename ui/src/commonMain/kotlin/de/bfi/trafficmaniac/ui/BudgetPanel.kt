package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.PrContract
import de.bfi.trafficmaniac.game.formatEuro
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
        BasicText("Haushalt & PR", style = title)
        BasicText(
            "Wöchentlicher Haushalt: +${formatEuro(rules.weeklyAllowance)} (sonntags um Mitternacht)",
            style = muted,
        )
        BasicText(
            "PR-Agentur (Kosten je Woche) – solange bezahlt wird, schreibt die Lokalzeitung Jubel-Stories:",
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
        BasicText("Kampagne „Totschlagargumente“ (${formatEuro(rules.argumentsWeeklyCost)}/Woche):", style = text)
        BasicText("„Wer sich an die Geschwindigkeit hält, muss sich um Blitzer keine Sorgen machen.“", style = muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("An", state.argumentsCampaign) { onArguments(true) }
            Choice("Aus", !state.argumentsCampaign) { onArguments(false) }
        }
        PressSection(state, rules, onCeremony)
        BasicText(
            "Unzufrieden: ${(state.unhappyShare * PERCENT).roundToInt()} % der Bürger · " +
                "Zorn ${state.anger.roundToInt()} · Abwahlindex ${state.recallIndex.roundToInt()}/100",
            style = text,
        )
        BasicText(
            "Einnahmen bisher: Bußgelder ${formatEuro(state.fineIncome)} · Parkgebühren " +
                "${formatEuro(
                    state.parkingOffice.feeIncome,
                )} · Knöllchen ${formatEuro(state.parkingOffice.ticketIncome)}",
            style = text,
        )
        state.lastBudgetRound?.let {
            BasicText(
                "Letzte Haushaltsrunde (${it.time.weekday.germanName}): +${formatEuro(it.allowance)}, " +
                    "PR −${formatEuro(it.prCost)}, Kampagne −${formatEuro(it.argumentsCost)}, " +
                    "Gehälter −${formatEuro(it.salaries)}, Erhalt −${formatEuro(it.maintenance)}" +
                    if (it.permits > 0) ", Anwohnerausweise +${formatEuro(it.permits)}" else "",
                style = muted,
            )
        }
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
        "Lokalzeitung: ${press.toneLabel} · Reputation ${state.reputation.roundToInt()}/100",
        style = text.copy(fontWeight = FontWeight.Bold),
    )
    val effect = (press.prEffect * PERCENT).roundToInt()
    BasicText(
        "Die Story wirkt zu $effect %" +
            (if (effect < PERCENT) " – bei so viel Leid glaubt der Agentur keiner mehr" else "") +
            ". Aufschlag für die Lücke zwischen Story und Realität: " +
            "+${(press.prGapSurcharge * PERCENT).roundToInt()} %",
        style = muted,
    )
    val next = press.nextCeremony
    if (next != null) {
        Choice("Spatenstich / Eröffnung (${formatEuro(rules.ceremonyCost)})", active = false, onClick = onCeremony)
        BasicText("Nächster Anlass: $next", style = muted)
    } else {
        BasicText("Heute wurde schon gefeiert – morgen gibt es die nächste Schaufel.", style = muted)
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
