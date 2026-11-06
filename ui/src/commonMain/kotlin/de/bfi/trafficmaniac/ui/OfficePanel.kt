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
import de.bfi.trafficmaniac.game.FineOfficeState
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.Strictness
import de.bfi.trafficmaniac.game.UnidentifiedPolicy
import de.bfi.trafficmaniac.game.formatEuro
import kotlin.math.roundToInt

private val PanelBackground = Color(0xF21D1F2E)
private val PanelText = Color(0xFFF1F1F1)
private val Muted = Color(0xFFB0B3C6)
private val Active = Color(0xFFE63946)
private val Idle = Color(0xFF3A3D52)
private const val PERCENT = 100
private const val HOURS_PER_DAY = 24

/**
 * Ordnungsamt und Bußgeldstelle: Personal, Aufklärungsquote, Aktenstapel, Bearbeitungsfrist,
 * Schärfe und Umgang mit Fotos, auf denen niemand zu erkennen ist.
 */
@Composable
fun OfficePanel(
    office: FineOfficeState,
    rules: GameRules,
    bannedDrivers: Int,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(400.dp)
            .padding(12.dp)
            .background(PanelBackground, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText("Ordnungsamt & Bußgeldstelle", style = title)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Sachbearbeiter: ${office.staff}" +
                    if (office.hiring > 0) " (+${office.hiring} in Einarbeitung)" else "",
                style = text,
            )
            Choice("−1", false) { onCommand(GameCommand.FireStaff(1)) }
            Choice("+1", false) { onCommand(GameCommand.HireStaff(1)) }
            Choice("+5", false) { onCommand(GameCommand.HireStaff(FIVE)) }
        }
        BasicText(
            "Gehalt ${formatEuro(rules.staffWeeklySalary)}/Woche je Kopf · ${rules.casesPerStaffHour} Fälle/Stunde, " +
                "werktags ${rules.officeStartHour}–${rules.officeEndHour} Uhr",
            style = muted,
        )
        BasicText("Aufklärungsquote: ${(office.identificationRate * PERCENT).roundToInt()} %", style = text)
        val stack =
            "Aktenstapel: ${office.backlog} Fotos" +
                if (office.backlog > 0) ", ältestes ${age(office.oldestCaseHours)}" else ""
        BasicText(stack, style = text.copy(fontWeight = FontWeight.Bold))
        BasicText(
            "Bearbeitet ${office.processed} · bezahlt ${office.paid} · " +
                "verjährt ${office.expired} · eingestellt ${office.dropped}",
            style = muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Bearbeitungsfrist: ${office.deadlineDays} Tage (max. ${rules.legalMaxDeadlineDays})",
                style = text,
            )
            Choice("−", false) { onCommand(GameCommand.SetDeadlineDays(office.deadlineDays - 1)) }
            Choice("+", false) { onCommand(GameCommand.SetDeadlineDays(office.deadlineDays + 1)) }
        }
        OfficePolicies(office, onCommand, text)
        BasicText("Fahrverbote und entzogene Führerscheine: $bannedDrivers", style = muted)
    }
}

@Composable
private fun OfficePolicies(
    office: FineOfficeState,
    onCommand: (GameCommand) -> Unit,
    text: TextStyle,
) {
    BasicText("Schärfe:", style = text)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (strictness in Strictness.entries) {
            Choice(
                "${strictness.label} (±${strictness.toleranceKmh.roundToInt()} km/h)",
                office.strictness == strictness,
            ) {
                onCommand(GameCommand.SetStrictness(strictness))
            }
        }
    }
    BasicText("Fahrer nicht ermittelbar:", style = text)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (policy in UnidentifiedPolicy.entries) {
            Choice(policy.label, office.policy == policy) { onCommand(GameCommand.SetUnidentifiedPolicy(policy)) }
        }
    }
}

private fun age(hours: Double): String =
    if (hours < HOURS_PER_DAY) "${hours.roundToInt()} Std." else "${"%.1f".format(hours / HOURS_PER_DAY)} Tage"

private const val FIVE = 5

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
