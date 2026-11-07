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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.ParkingOfficeState
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.sim.ParkingLotState
import de.bfi.trafficmaniac.sim.ParkingSummary
import kotlin.math.roundToInt

private val PanelBackground = Color(0xF21D1F2E)
private val PanelText = Color(0xFFF1F1F1)
private val Muted = Color(0xFFB0B3C6)
private val Active = Color(0xFFE63946)
private val Idle = Color(0xFF3A3D52)
private const val VISIBLE_LOTS = 6
private const val CAPACITY_STEP = 0.25
private const val FIVE = 5

/**
 * Parken: Gebühren und Stellplätze der Parkplätze/Parkhäuser, Straßenrand (Radspuren, Blumenkübel,
 * Anwohnerparken), Brötchentaste und Politessen.
 */
@Composable
fun ParkingPanel(
    office: ParkingOfficeState,
    summary: ParkingSummary,
    lots: List<ParkingLotState>,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(440.dp)
            .padding(12.dp)
            .background(PanelBackground, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText("Parken & Politessen", style = title)
        BasicText(
            "Parkplätze ${summary.lotOccupied}/${summary.lotCapacity} belegt · Straßenrand " +
                "${summary.curbOccupied}/${summary.curbCapacity} · auf Parkplatzsuche: ${summary.searching}",
            style = text,
        )
        BasicText(
            "Einnahmen: Gebühren ${formatEuro(office.feeIncome)} · Knöllchen ${formatEuro(office.ticketIncome)} " +
                "(${office.tickets}) · Falschparker bisher ${office.illegalParkers}",
            style = muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Politessen: ${office.wardens}" + if (office.hiring > 0) " (+${office.hiring} kommen)" else "",
                style = text,
            )
            Choice("−1", false) { onCommand(GameCommand.FireWardens(1)) }
            Choice("+1", false) { onCommand(GameCommand.HireWardens(1)) }
            Choice("+5", false) { onCommand(GameCommand.HireWardens(FIVE)) }
        }
        BasicText(
            "${formatEuro(
                rules.wardenWeeklySalary,
            )}/Woche je Kopf, unterwegs ${rules.wardenStartHour}–${rules.wardenEndHour} Uhr · " +
                "Knöllchen ab welcher Minute, regelt die Schärfe im Ordnungsamt",
            style = muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("Brötchentaste", office.breadButton) { onCommand(GameCommand.SetBreadButton(!office.breadButton)) }
            Choice("Anwohnerparken", office.residentParking) {
                onCommand(GameCommand.SetResidentParking(!office.residentParking))
            }
            Choice("Parkplätze → Blumenkübel (${formatEuro(rules.curbConversionCost)})", false) {
                onCommand(GameCommand.ConvertCurbParking)
            }
        }
        BasicText("Straßenrand: noch ${(summary.curbShare * PERCENT).roundToInt()} % der Parkplätze", style = muted)
        BasicText("Parkscheinautomat am Straßenrand (€/Std.):", style = text)
        FeeChoices(rules, { summary.curbFee == it }) { onCommand(GameCommand.SetCurbFee(it)) }
        BasicText("Gebühr für alle Parkplätze (€/Std.):", style = text)
        FeeChoices(rules, { fee -> lots.isNotEmpty() && lots.all { it.feePerHour == fee } }) {
            onCommand(GameCommand.SetParkingFee(null, it))
        }
        BasicText("Größte Parkplätze und Parkhäuser:", style = text)
        for (lot in lots.sortedByDescending { it.baseCapacity }.take(VISIBLE_LOTS)) LotRow(lot, rules, onCommand, muted)
    }
}

/** Eine Reihe Gebührenstufen. */
@Composable
private fun FeeChoices(
    rules: GameRules,
    isActive: (Double) -> Boolean,
    onSelect: (Double) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (fee in rules.parkingFeeOptions) Choice(euro(fee), isActive(fee)) { onSelect(fee) }
    }
}

@Composable
private fun LotRow(
    lot: ParkingLotState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    muted: TextStyle,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val name = lot.name ?: "Parkplatz ${lot.id + 1}"
        val state =
            if (lot.capacity ==
                0
            ) {
                "abgeschafft"
            } else {
                "${lot.occupied}/${lot.capacity}, ${euro(lot.feePerHour)}/Std."
            }
        BasicText("$name: $state", style = muted, modifier = Modifier.width(210.dp))
        val options = rules.parkingFeeOptions
        val index = options.indexOfFirst { it >= lot.feePerHour }.coerceAtLeast(0)
        Choice("€−", false) { onCommand(GameCommand.SetParkingFee(lot.id, options[(index - 1).coerceAtLeast(0)])) }
        Choice("€+", false) {
            onCommand(GameCommand.SetParkingFee(lot.id, options[(index + 1).coerceAtMost(options.lastIndex)]))
        }
        val step = (lot.baseCapacity * CAPACITY_STEP).roundToInt().coerceAtLeast(1)
        Choice("−", false) { onCommand(GameCommand.SetParkingCapacity(lot.id, lot.capacity - step)) }
        Choice("+", false) { onCommand(GameCommand.SetParkingCapacity(lot.id, lot.capacity + step)) }
        if (lot.capacity > 0) {
            Choice("weg", false) { onCommand(GameCommand.SetParkingCapacity(lot.id, 0)) }
        } else {
            Choice("öffnen", false) { onCommand(GameCommand.SetParkingCapacity(lot.id, lot.baseCapacity)) }
        }
    }
}

private fun euro(amount: Double): String =
    if (amount == amount.roundToInt().toDouble()) "${amount.roundToInt()} €" else "${"%.2f".format(amount)} €"

private const val PERCENT = 100

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
