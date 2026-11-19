package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.ParkingOfficeState
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.ParkingLotState
import de.bfi.trafficmaniac.sim.ParkingSummary
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.MinTouch
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.SettingRow
import de.bfi.trafficmaniac.ui.components.StatTile
import de.bfi.trafficmaniac.ui.components.Stepper
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

/**
 * Parken: Belegung und Einnahmen, Politessen, Regeln am Straßenrand (Brötchentaste, Anwohnerparken, Blumenkübel),
 * Gebühren und die größten Parkplätze und Parkhäuser.
 */
@Composable
fun ParkingPanel(
    office: ParkingOfficeState,
    summary: ParkingSummary,
    lots: List<ParkingLotState>,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    PanelScaffold(tr("Amt"), tr("Parken & Politessen"), modifier, onClose, floating = false) {
        PanelSection(divider = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(tr("Parkplätze"), "${summary.lotOccupied}/${summary.lotCapacity}", Modifier.weight(1f))
                StatTile(tr("Straßenrand"), "${summary.curbOccupied}/${summary.curbCapacity}", Modifier.weight(1f))
                StatTile(
                    tr("Knöllchen"),
                    "${office.tickets}",
                    Modifier.weight(1f),
                    footer = formatEuro(office.ticketIncome),
                )
            }
            BasicText(
                tr(
                    "Auf Parkplatzsuche: {searching} · Gebühren bisher {fees} · Falschparker {illegal}",
                    "searching" to summary.searching,
                    "fees" to formatEuro(office.feeIncome),
                    "illegal" to office.illegalParkers,
                ),
                style = TmType.caption,
            )
        }
        PanelSection {
            SettingRow(
                tr("Politessen") + if (office.hiring > 0) tr(" (+{hiring} kommen)", "hiring" to office.hiring) else "",
                hint =
                    tr(
                        "{salary}/Woche je Kopf · unterwegs {start}–{end} Uhr",
                        "salary" to formatEuro(rules.wardenWeeklySalary),
                        "start" to rules.wardenStartHour,
                        "end" to rules.wardenEndHour,
                    ),
            ) {
                Stepper(
                    "${office.wardens}",
                    onMinus = { onCommand(GameCommand.FireWardens(1)) }.takeIf { office.wardens > 0 },
                    onPlus = { onCommand(GameCommand.HireWardens(1)) },
                    bigStep = "+$FIVE" to { onCommand(GameCommand.HireWardens(FIVE)) },
                )
            }
        }
        CurbSection(office, summary, rules, onCommand)
        FeeSection(summary, lots, rules, onCommand)
        LotSection(lots, rules, onCommand)
    }
}

/** Straßenrand: Brötchentaste und Anwohnerparken zum Umschalten, Parkplätze zu Blumenkübeln. */
@Composable
private fun CurbSection(
    office: ParkingOfficeState,
    summary: ParkingSummary,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Straßenrand")) {
        Row(
            Modifier.fillMaxWidth().background(TmColors.Ground, RoundedCornerShape(12.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Toggle(tr("Brötchentaste"), office.breadButton, Modifier.weight(1f)) {
                onCommand(GameCommand.SetBreadButton(!office.breadButton))
            }
            Toggle(tr("Anwohnerparken"), office.residentParking, Modifier.weight(1f)) {
                onCommand(GameCommand.SetResidentParking(!office.residentParking))
            }
        }
        DashedButton(
            tr("Parkplätze → Blumenkübel ({cost})", "cost" to formatEuro(rules.curbConversionCost)),
            Modifier.fillMaxWidth(),
        ) { onCommand(GameCommand.ConvertCurbParking) }
        BasicText(
            tr("Noch {share} % der Parkplätze am Straßenrand", "share" to (summary.curbShare * PERCENT).roundToInt()),
            style = TmType.caption,
        )
    }
}

@Composable
private fun FeeSection(
    summary: ParkingSummary,
    lots: List<ParkingLotState>,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Gebühren (€/Std.)")) {
        BasicText(tr("Parkscheinautomat am Straßenrand"), style = TmType.small)
        SegmentedControl(
            rules.parkingFeeOptions,
            rules.parkingFeeOptions.firstOrNull { it == summary.curbFee },
            ::euro,
            { onCommand(GameCommand.SetCurbFee(it)) },
            Modifier.fillMaxWidth(),
        )
        BasicText(tr("Alle Parkplätze und Parkhäuser"), style = TmType.small)
        SegmentedControl(
            rules.parkingFeeOptions,
            rules.parkingFeeOptions.firstOrNull { fee -> lots.isNotEmpty() && lots.all { it.feePerHour == fee } },
            ::euro,
            { onCommand(GameCommand.SetParkingFee(null, it)) },
            Modifier.fillMaxWidth(),
        )
    }
}

/** Die größten Parkplätze: Belegung, Preis-Stepper, abschaffen bzw. wieder öffnen. */
@Composable
private fun LotSection(
    lots: List<ParkingLotState>,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Größte Parkplätze und Parkhäuser"), divider = false) {
        val options = rules.parkingFeeOptions
        for (lot in lots.sortedByDescending { it.baseCapacity }.take(VISIBLE_LOTS)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        lot.name ?: tr("Parkplatz {id}", "id" to lot.id + 1),
                        style = TmType.text(13.sp, FontWeight.SemiBold),
                    )
                    BasicText(
                        if (lot.capacity == 0) tr("abgeschafft") else "${lot.occupied}/${lot.capacity}",
                        style = TmType.mono(11.sp, bold = false, color = TmColors.Muted),
                    )
                }
                if (lot.capacity > 0) {
                    val index = options.indexOfFirst { it >= lot.feePerHour }.coerceAtLeast(0)
                    Stepper(
                        euro(lot.feePerHour),
                        onMinus =
                            { onCommand(GameCommand.SetParkingFee(lot.id, options[index - 1])) }.takeIf {
                                index >
                                    0
                            },
                        onPlus =
                            { onCommand(GameCommand.SetParkingFee(lot.id, options[index + 1])) }.takeIf {
                                index <
                                    options.lastIndex
                            },
                    )
                    Box(
                        Modifier
                            .size(MinTouch)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { onCommand(GameCommand.SetParkingCapacity(lot.id, 0)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Trash, tr("Abschaffen"), tint = TmColors.DangerText, size = 20.dp) }
                } else {
                    TmButton(tr("öffnen"), { onCommand(GameCommand.SetParkingCapacity(lot.id, lot.baseCapacity)) })
                }
            }
        }
    }
}

/** Umschalter in der Wanne: an = dunkel gefüllt. */
@Composable
private fun Toggle(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = MinTouch)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) TmColors.Ink else Color.Transparent)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style =
                TmType
                    .text(
                        13.sp,
                        if (on) FontWeight.Bold else FontWeight.SemiBold,
                        if (on) TmColors.Surface else TmColors.Ink,
                    ).copy(textAlign = TextAlign.Center),
        )
    }
}

/** Gestrichelter Knopf für eine einmalige Umbaumaßnahme. */
@Composable
private fun DashedButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = MinTouch)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                drawRoundRect(
                    TmColors.Muted,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = dash),
                )
            }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = TmType.text(13.sp, FontWeight.SemiBold)) }
}

private fun euro(amount: Double): String =
    de.bfi.trafficmaniac.i18n
        .formatEuro(amount)

private const val VISIBLE_LOTS = 6
private const val FIVE = 5
private const val PERCENT = 100
