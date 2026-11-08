package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.*
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import kotlin.math.roundToInt

private val PanelBackground = ComicTheme.paper
private val PanelText = ComicTheme.ink
private val Muted = ComicTheme.muted
private val Active = ComicTheme.red
private val Idle = ComicTheme.button
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
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(tr("Ordnungsamt & Bußgeldstelle"), style = title)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                tr("Sachbearbeiter: {staff}", "staff" to office.staff) +
                    if (office.hiring > 0) tr(" (+{hiring} in Einarbeitung)", "hiring" to office.hiring) else "",
                style = text,
            )
            Choice("−1", false) { onCommand(GameCommand.FireStaff(1)) }
            Choice("+1", false) { onCommand(GameCommand.HireStaff(1)) }
            Choice("+5", false) { onCommand(GameCommand.HireStaff(FIVE)) }
        }
        BasicText(
            tr(
                "Gehalt {staffWeeklySalary}/Woche je Kopf · {casesPerStaffHour} Fälle/Stunde, " +
                    "werktags {officeStartHour}–{officeEndHour} Uhr",
                "staffWeeklySalary" to formatEuro(rules.staffWeeklySalary),
                "casesPerStaffHour" to rules.casesPerStaffHour,
                "officeStartHour" to rules.officeStartHour,
                "officeEndHour" to rules.officeEndHour,
            ),
            style = muted,
        )
        CaseStats(office, text, muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                tr(
                    "Bearbeitungsfrist: {deadlineDays} Tage (max. {legalMaxDeadlineDays})",
                    "deadlineDays" to office.deadlineDays,
                    "legalMaxDeadlineDays" to rules.legalMaxDeadlineDays,
                ),
                style = text,
            )
            Choice("−", false) { onCommand(GameCommand.SetDeadlineDays(office.deadlineDays - 1)) }
            Choice("+", false) { onCommand(GameCommand.SetDeadlineDays(office.deadlineDays + 1)) }
        }
        OfficePolicies(office, onCommand, text)
        BasicText(
            tr("Fahrverbote und entzogene Führerscheine: {bannedDrivers}", "bannedDrivers" to bannedDrivers),
            style = muted,
        )
    }
}

/** Aufklärungsquote, Aktenstapel und was aus den Fällen wurde. */
@Composable
private fun CaseStats(
    office: FineOfficeState,
    text: TextStyle,
    muted: TextStyle,
) {
    BasicText(
        tr(
            "Aufklärungsquote: {identificationRate} %",
            "identificationRate" to (office.identificationRate * PERCENT).roundToInt(),
        ),
        style = text,
    )
    val stack =
        tr("Aktenstapel: {backlog} Fotos", "backlog" to office.backlog) +
            if (office.backlog >
                0
            ) {
                tr(", ältestes {oldestCaseHours}", "oldestCaseHours" to age(office.oldestCaseHours))
            } else {
                ""
            }
    BasicText(stack, style = text.copy(fontWeight = FontWeight.Bold))
    BasicText(
        tr(
            "Bearbeitet {processed} · bezahlt {paid} · " +
                "verjährt {expired} · eingestellt {dropped}",
            "processed" to office.processed,
            "paid" to office.paid,
            "expired" to office.expired,
            "dropped" to office.dropped,
        ),
        style = muted,
    )
}

@Composable
private fun OfficePolicies(
    office: FineOfficeState,
    onCommand: (GameCommand) -> Unit,
    text: TextStyle,
) {
    BasicText(tr("Schärfe:"), style = text)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (strictness in Strictness.entries) {
            Choice(
                tr(
                    "{strictness} (±{toleranceKmh} km/h)",
                    "strictness" to strictness.label,
                    "toleranceKmh" to strictness.toleranceKmh.roundToInt(),
                ),
                office.strictness == strictness,
            ) {
                onCommand(GameCommand.SetStrictness(strictness))
            }
        }
    }
    BasicText(tr("Fahrer nicht ermittelbar:"), style = text)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (policy in UnidentifiedPolicy.entries) {
            Choice(policy.label, office.policy == policy) { onCommand(GameCommand.SetUnidentifiedPolicy(policy)) }
        }
    }
}

private fun age(hours: Double): String =
    if (hours <
        HOURS_PER_DAY
    ) {
        tr("{hours} Std.", "hours" to hours.roundToInt())
    } else {
        tr(
            "{hours} Tage",
            "hours" to formatDecimal(hours / HOURS_PER_DAY),
        )
    }

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
                .comicButton(active)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
