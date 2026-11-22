package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Assignment
import de.bfi.trafficmaniac.game.AssignmentKind
import de.bfi.trafficmaniac.game.AssignmentStatus
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameMessage
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.MessageKind
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.KpiTile
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.TmButton
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.roundToInt

/** Zeile eines Auftrags: offen mit Frist (und Fortschritt), erledigt mit Haken und gutgeschriebener Belohnung. */
internal fun jobRow(item: InboxItem.Job): RowText {
    val a = item.assignment
    if (item.done) {
        return RowText(
            tr("Auftrag · {client}", "client" to a.kind.client),
            tr("✓ Erledigt: {title} · {street}", "title" to a.kind.title, "street" to a.street),
            tr("gutgeschrieben"),
            "+" + formatEuro(a.reward),
            TmColors.Success,
        )
    }
    val hours = tr("noch {hours} Std.", "hours" to a.hoursLeft.coerceAtLeast(0.0).roundToInt())
    return RowText(
        tr("Auftrag · {client}", "client" to a.kind.client),
        "${a.kind.title} · ${a.street}",
        if (a.goal > 0) "$hours · ${a.progress}/${a.goal}" else hours,
        "+" + formatEuro(a.reward),
        TmColors.Primary,
    )
}

/** Wie weit ein Auftrag ist, der in Schritten erfüllt wird (Tempo 30 je Abschnitt, Kontrollstunden). */
private fun progressText(assignment: Assignment): String =
    when (assignment.kind) {
        AssignmentKind.TEMPO_30 -> {
            tr("{done} von {goal} Abschnitten mit Tempo 30", "done" to assignment.progress, "goal" to assignment.goal)
        }

        else -> {
            tr("{done} von {goal} Std. kontrolliert", "done" to assignment.progress, "goal" to assignment.goal)
        }
    }

@Composable
internal fun JobDetail(
    assignment: Assignment,
    actions: InboxActions,
) {
    BasicText(assignment.kind.client, style = TmType.small)
    if (assignment.status == AssignmentStatus.DONE) {
        BasicText(
            tr("Erledigt – {reward} sind in der Kasse.", "reward" to formatEuro(assignment.reward)),
            style = TmType.text(15.sp, FontWeight.SemiBold, TmColors.SuccessText),
        )
        TmButton(tr("Auf Karte zeigen"), { actions.onShow(assignment.place) })
        return
    }
    // Der Auftragstext bringt seine Anführungszeichen schon mit
    val quote = assignment.text.trim()
    val quoted = quote.startsWith("„") || quote.startsWith("\"")
    BasicText(if (quoted) quote else "„$quote“", style = TmType.text(14.sp).copy(lineHeight = 20.sp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            BasicText(tr("Belohnung"), style = TmType.caption)
            BasicText("+" + formatEuro(assignment.reward), style = TmType.mono(18.sp, color = TmColors.SuccessText))
        }
        Column {
            BasicText(tr("Frist"), style = TmType.caption)
            BasicText(
                tr("noch {hours} Std.", "hours" to assignment.hoursLeft.coerceAtLeast(0.0).roundToInt()),
                style = TmType.mono(18.sp),
            )
        }
    }
    val share = (assignment.hoursLeft / assignment.hours).toFloat().coerceIn(0f, 1f)
    Meter(
        share,
        Modifier.fillMaxWidth(),
        height = 6.dp,
        color =
            if (share <
                URGENT_SHARE
            ) {
                TmColors.Danger
            } else {
                TmColors.Primary
            },
    )
    if (assignment.goal > 0) {
        BasicText(progressText(assignment), style = TmType.text(13.sp, FontWeight.SemiBold))
        Meter(
            assignment.progress.toFloat() / assignment.goal,
            Modifier.fillMaxWidth(),
            height = 6.dp,
            color = TmColors.Success,
        )
    }
    assignment.kind.tool()?.let { kind ->
        BasicText(
            tr("Ziel: mit „{tool}“ erledigen", "tool" to kind.label),
            style = TmType.text(13.sp, FontWeight.SemiBold),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TmButton(tr("Ablehnen"), { actions.assignments.onDecline(assignment) }, kind = ButtonKind.TEXT)
        TmButton(tr("Zur Stelle"), { actions.assignments.onGo(assignment) }, kind = ButtonKind.PRIMARY)
    }
}

/** Ab diesem Rest der Frist wird der Balken rot. */
private const val URGENT_SHARE = 0.25f
