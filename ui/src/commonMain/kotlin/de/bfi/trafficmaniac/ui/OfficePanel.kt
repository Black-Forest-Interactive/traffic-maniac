package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.FineOfficeState
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.Strictness
import de.bfi.trafficmaniac.game.UnidentifiedPolicy
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.OptionButton
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.RadioCardList
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.SettingRow
import de.bfi.trafficmaniac.ui.components.StatTile
import de.bfi.trafficmaniac.ui.components.Stepper
import kotlin.math.roundToInt

/**
 * Ordnungsamt und Bußgeldstelle: Aufklärungsquote und Aktenstapel, Personal, Bearbeitungsfrist, Schärfe der Blitzer
 * und der Umgang mit Fotos, auf denen niemand zu erkennen ist.
 */
@Composable
fun OfficePanel(
    office: FineOfficeState,
    rules: GameRules,
    bannedDrivers: Int,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    habits: HabitsState? = null,
) {
    PanelScaffold(tr("Amt"), tr("Ordnungsamt & Bußgeldstelle"), modifier, onClose, floating = false) {
        PanelSection(divider = false) { CaseTiles(office) }
        habits?.let { HabitsSection(it, rules, onCommand) }
        StaffSection(office, rules, onCommand)
        PanelSection(title = tr("Schärfe der Blitzer")) {
            SegmentedControl(
                Strictness.entries,
                office.strictness,
                { it.label },
                { onCommand(GameCommand.SetStrictness(it)) },
                Modifier.fillMaxWidth(),
                detail = { "±${it.toleranceKmh.roundToInt()} km/h" },
            )
        }
        PanelSection(title = tr("Wenn der Fahrer nicht ermittelbar ist")) {
            RadioCardList(
                UnidentifiedPolicy.entries,
                office.policy,
                { it.label },
                { onCommand(GameCommand.SetUnidentifiedPolicy(it)) },
            )
        }
        PanelSection(divider = false) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(
                    tr("Fahrverbote & entzogene Führerscheine"),
                    style = TmType.text(13.sp, color = TmColors.Muted),
                )
                BasicText("$bannedDrivers", style = TmType.mono(13.sp))
            }
        }
    }
}

/** Sachbearbeiter und Bearbeitungsfrist. */
@Composable
private fun StaffSection(
    office: FineOfficeState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection {
        SettingRow(
            tr("Sachbearbeiter") +
                if (office.hiring > 0) tr(" (+{hiring} in Einarbeitung)", "hiring" to office.hiring) else "",
            hint =
                tr(
                    "{salary}/Woche je Kopf · {cases} Fälle/Std. · werktags {start}–{end} Uhr",
                    "salary" to formatEuro(rules.staffWeeklySalary),
                    "cases" to rules.casesPerStaffHour,
                    "start" to rules.officeStartHour,
                    "end" to rules.officeEndHour,
                ),
        ) {
            Stepper(
                "${office.staff}",
                onMinus = { onCommand(GameCommand.FireStaff(1)) }.takeIf { office.staff > 0 },
                onPlus = { onCommand(GameCommand.HireStaff(1)) },
                bigStep = "+$FIVE" to { onCommand(GameCommand.HireStaff(FIVE)) },
            )
        }
        SettingRow(
            tr("Bearbeitungsfrist"),
            hint = tr("höchstens {max} Tage", "max" to rules.legalMaxDeadlineDays),
        ) {
            Stepper(
                tr("{days} Tage", "days" to office.deadlineDays),
                onMinus =
                    { onCommand(GameCommand.SetDeadlineDays(office.deadlineDays - 1)) }.takeIf {
                        office.deadlineDays >
                            1
                    },
                onPlus =
                    { onCommand(GameCommand.SetDeadlineDays(office.deadlineDays + 1)) }
                        .takeIf { office.deadlineDays < rules.legalMaxDeadlineDays },
            )
        }
    }
}

/** Aufklärungsquote mit Balken und Aktenstapel mit dem, was aus den Fällen wurde. */
@Composable
private fun CaseTiles(office: FineOfficeState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            tr("Aufklärungsquote"),
            "${(office.identificationRate * PERCENT).roundToInt()} %",
            Modifier.weight(1f),
            meter = office.identificationRate.toFloat(),
        )
        StatTile(
            tr("Aktenstapel"),
            tr("{backlog} Fotos", "backlog" to office.backlog),
            Modifier.weight(1f),
            footer =
                tr(
                    "{processed} bearbeitet · {paid} bezahlt · {expired} verjährt · {dropped} eingestellt",
                    "processed" to office.processed,
                    "paid" to office.paid,
                    "expired" to office.expired,
                    "dropped" to office.dropped,
                ) + if (office.backlog > 0) tr(" · ältestes {age}", "age" to age(office.oldestCaseHours)) else "",
        )
    }
}

private fun age(hours: Double): String =
    if (hours < HOURS_PER_DAY) {
        tr("{hours} Std.", "hours" to hours.roundToInt())
    } else {
        tr("{hours} Tage", "hours" to formatDecimal(hours / HOURS_PER_DAY))
    }

private const val PERCENT = 100
private const val HOURS_PER_DAY = 24
private const val FIVE = 5

/** Was das Ordnungsamt über Warn-Apps weiß. */
class HabitsState(
    val warnerShare: Double,
    val marathonActive: Boolean,
    val campaignInDays: Int,
)

/**
 * Blitzer sprechen sich herum: Warner-Quote und die zwei Gegenmittel – Polizeiaktion „Blitzerwarner“ (Bußgeld, Quote
 * sinkt, Presse spricht von Razzia) und Blitzermarathon (ein Tag brav, Lob, danach etwas weniger Warner).
 */
@Composable
private fun HabitsSection(
    habits: HabitsState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Blitzer-Warn-Apps")) {
        BasicText(
            tr(
                "{percent} % der Fahrer warnt eine App vor bekannten festen Blitzern. " +
                    "Jeder feste Blitzer wird jeden Tag bekannter.",
                "percent" to (habits.warnerShare * PERCENT).roundToInt(),
            ),
            style = TmType.small,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionButton(
                if (habits.campaignInDays > 0) {
                    tr("Polizeiaktion (in {days} Tagen)", "days" to habits.campaignInDays)
                } else {
                    tr("Polizeiaktion „Blitzerwarner“")
                },
                { onCommand(GameCommand.WarnerCampaign) },
                Modifier.weight(1f),
                formatEuro(rules.habits.campaignCost),
            )
            OptionButton(
                if (habits.marathonActive) tr("Blitzermarathon läuft") else tr("Blitzermarathon ankündigen"),
                { onCommand(GameCommand.AnnounceMarathon) },
                Modifier.weight(1f),
                tr("kostenlos"),
            )
        }
    }
}
