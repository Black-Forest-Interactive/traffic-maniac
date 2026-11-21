package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.ParkingLotState
import de.bfi.trafficmaniac.sim.ParkingSummary
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.MinTouch
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.SettingRow
import de.bfi.trafficmaniac.ui.components.StatTile
import de.bfi.trafficmaniac.ui.components.Stepper
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Die größten Parkplätze: Belegung als Balken, Preis-Stepper, ≈ Gebühren je Tag; abschaffen erst beim Überfahren. */
@Composable
internal fun LotSection(
    lots: List<ParkingLotState>,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    PanelSection(title = tr("Größte Parkplätze und Parkhäuser"), divider = false) {
        for (lot in lots
            .sortedByDescending {
                it.baseCapacity
            }.take(VISIBLE_LOTS)) {
            LotRow(lot, rules.parkingFeeOptions, onCommand)
        }
    }
}

@Composable
private fun LotRow(
    lot: ParkingLotState,
    options: List<Double>,
    onCommand: (GameCommand) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().hoverable(interaction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                lot.name ?: tr("Parkplatz {id}", "id" to lot.id + 1),
                style = TmType.text(13.sp, FontWeight.SemiBold),
            )
            if (lot.capacity == 0) {
                BasicText(tr("abgeschafft"), style = TmType.caption)
            } else {
                LotOccupancy(lot)
            }
        }
        if (lot.capacity == 0) {
            TmButton(tr("öffnen"), { onCommand(GameCommand.SetParkingCapacity(lot.id, lot.baseCapacity)) })
            return@Row
        }
        val index = options.indexOfFirst { it >= lot.feePerHour }.coerceAtLeast(0)
        Stepper(
            euro(lot.feePerHour),
            onMinus = { onCommand(GameCommand.SetParkingFee(lot.id, options[index - 1])) }.takeIf { index > 0 },
            onPlus =
                { onCommand(GameCommand.SetParkingFee(lot.id, options[index + 1])) }.takeIf {
                    index <
                        options.lastIndex
                },
        )
        // Abschaffen ist selten – der rote Papierkorb erst, wenn die Maus auf der Zeile steht
        Box(
            Modifier
                .size(MinTouch)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { onCommand(GameCommand.SetParkingCapacity(lot.id, 0)) },
            contentAlignment = Alignment.Center,
        ) {
            if (hovered ||
                LocalTouch.current
            ) {
                Icon(Icons.Trash, tr("Abschaffen"), tint = TmColors.DangerText, size = 20.dp)
            }
        }
    }
}

/** Belegung als Balken mit „belegt/Plätze“, darunter die geschätzten Gebühren für 24 Stunden. */
@Composable
private fun LotOccupancy(lot: ParkingLotState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Meter(lot.occupied.toFloat() / lot.capacity, Modifier.weight(1f), height = 6.dp, color = LotColor)
        BasicText("${lot.occupied}/${lot.capacity}", style = TmType.mono(11.sp, bold = false, color = TmColors.Muted))
    }
    BasicText(
        tr("≈ {euro}/Tag", "euro" to formatEuro((lot.occupied * lot.feePerHour * PAID_HOURS).roundToLong())),
        style = TmType.caption,
    )
}

private const val VISIBLE_LOTS = 6
