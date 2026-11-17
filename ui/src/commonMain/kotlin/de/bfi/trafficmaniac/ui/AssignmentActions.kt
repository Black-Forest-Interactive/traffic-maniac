package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Assignment
import de.bfi.trafficmaniac.game.AssignmentKind
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

/** Was die Auftragskarten auslösen: hinfliegen (samt passendem Werkzeug) oder ablehnen. */
internal class AssignmentActions(
    val onGo: (Assignment) -> Unit,
    val onDecline: (Assignment) -> Unit,
)

/** Welches Werkzeug „Hin“ vorwählt; `null` = nur hinsehen (die Aktionskarte zum Schaden öffnet sich von selbst). */
internal fun AssignmentKind.tool(): ToolKind? =
    when (this) {
        AssignmentKind.TEMPO_30 -> ToolKind.SPEED_LIMIT
        AssignmentKind.ACCIDENT_SPOT -> ToolKind.DISPLAY
        AssignmentKind.SPEEDER -> ToolKind.POLICE_CHECK
        AssignmentKind.POTHOLE -> null
    }

/** Offene Aufträge als Karten: Auftraggeber, ein Satz, Lohn, Frist, „Hin“ und „Ablehnen“. */
@Composable
internal fun AssignmentCards(
    assignments: List<Assignment>,
    actions: AssignmentActions,
    modifier: Modifier = Modifier,
) {
    if (assignments.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(tr("Aufträge"), style = TmType.label)
        for (assignment in assignments.sortedBy { it.hoursLeft }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(assignment.kind.title, style = TmType.text(13.sp, FontWeight.Bold))
                        BasicText(assignment.kind.client, style = TmType.caption)
                    }
                    BasicText(
                        "+" + formatEuro(assignment.reward),
                        style = TmType.mono(13.sp, color = TmColors.Success),
                    )
                }
                BasicText(assignment.text, style = TmType.text(12.sp))
                val share = (assignment.hoursLeft / assignment.hours).toFloat().coerceIn(0f, 1f)
                Meter(
                    share,
                    Modifier.fillMaxWidth(),
                    height = 4.dp,
                    color =
                        if (share <
                            URGENT
                        ) {
                            TmColors.Danger
                        } else {
                            TmColors.Primary
                        },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(
                        tr("noch {hours} Std.", "hours" to assignment.hoursLeft.coerceAtLeast(0.0).roundToInt()),
                        style = TmType.small,
                        modifier = Modifier.weight(1f),
                    )
                    TmButton(tr("Ablehnen"), { actions.onDecline(assignment) }, kind = ButtonKind.TEXT)
                    TmButton(tr("Hin"), { actions.onGo(assignment) }, kind = ButtonKind.PRIMARY)
                }
            }
        }
    }
}

/** Ab diesem Rest der Frist wird der Balken rot. */
private const val URGENT = 0.25f
