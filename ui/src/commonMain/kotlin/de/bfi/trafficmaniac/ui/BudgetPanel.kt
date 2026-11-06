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

private val PanelBackground = Color(0xF21D1F2E)
private val PanelText = Color(0xFFF1F1F1)
private val Muted = Color(0xFFB0B3C6)
private val Active = Color(0xFFE63946)
private val Idle = Color(0xFF3A3D52)

/** Haushalt & PR: Agenturvertrag und Kampagne "Totschlagargumente". Abgerechnet wird sonntags. */
@Composable
fun BudgetPanel(
    state: GameState,
    rules: GameRules,
    onContract: (PrContract) -> Unit,
    onArguments: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(380.dp)
            .padding(12.dp)
            .background(PanelBackground, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText("Haushalt & PR", style = title)
        BasicText(
            "Wöchentlicher Haushalt: +${formatEuro(rules.weeklyAllowance)} (sonntags um Mitternacht)",
            style = muted,
        )
        BasicText("PR-Agentur – solange bezahlt wird, schreibt die Lokalzeitung Jubel-Stories:", style = text)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (contract in PrContract.entries) {
                Choice("${contract.label} (${formatEuro(contract.weeklyCost)}/Woche)", state.prContract == contract) {
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
        BasicText(
            "Unzufrieden: ${(state.unhappyShare * PERCENT).roundToInt()} % der Bürger · " +
                "Zorn ${state.anger.roundToInt()} · Abwahlindex ${state.recallIndex.roundToInt()}/100",
            style = text,
        )
        state.lastBudgetRound?.let {
            BasicText(
                "Letzte Haushaltsrunde (${it.time.weekday.germanName}): +${formatEuro(it.allowance)}, " +
                    "PR −${formatEuro(it.prCost)}, Kampagne −${formatEuro(it.argumentsCost)}",
                style = muted,
            )
        }
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
                .background(if (active) Active else Idle, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

private const val PERCENT = 100
