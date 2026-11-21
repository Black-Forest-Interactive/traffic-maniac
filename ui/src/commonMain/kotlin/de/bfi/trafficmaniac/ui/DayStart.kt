package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

/** Stand zu Beginn eines Tags: Einnahmen und Ausgaben bis dahin und die Kennzahlen. */
internal class DayStart(
    val income: Long,
    val expenses: Long,
    val reputation: Int,
    val anger: Int,
    val recall: Int,
) {
    /** Bilanz des Tags bis zum Stand [now]. */
    fun balance(now: GameState): DayBalance {
        val end = of(now)
        return DayBalance(
            income = end.income - income,
            expenses = end.expenses - expenses,
            balance = now.balance,
            reputation = reputation to end.reputation,
            anger = anger to end.anger,
            recall = recall to end.recall,
            tone = now.press.toneLabel,
            openAssignments = now.assignments.size,
        )
    }

    companion object {
        fun of(game: GameState): DayStart =
            DayStart(
                // Aus dem Journal: jede Geldbewegung ist dort gebucht – so gilt Kasse neu = Kasse alt + Saldo
                income =
                    game.journal.totals
                        .filterKeys { it.income }
                        .values
                        .sum(),
                expenses =
                    -game.journal.totals
                        .filterKeys { !it.income }
                        .values
                        .sum(),
                reputation = game.reputation.roundToInt(),
                anger = game.anger.roundToInt(),
                recall = game.recallIndex.roundToInt(),
            )
    }
}

/**
 * Tagesabschluss weggeklickt: Das Spiel steht weiter. Oben in der Mitte ein Hinweis mit „Zeitung“ (öffnet den
 * Abschluss wieder) und „Weiter“ (Leertaste oder Enter).
 */
@Composable
internal fun ReportWaiting(
    day: Int,
    onContinue: () -> Unit,
    onReopen: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .shadow(10.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(TmColors.Ink)
                .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                tr("Tag {day} beendet – das Spiel wartet", "day" to day),
                style = TmType.text(14.sp, FontWeight.SemiBold, TmColors.OnDark),
            )
            BasicText(
                tr("Zeitung"),
                style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.Active),
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(8.dp),
                        ).clickable(role = Role.Button, onClick = onReopen)
                        .padding(8.dp),
            )
            TmButton(tr("Weiter (Leertaste)"), onContinue, kind = ButtonKind.PRIMARY)
        }
    }
}
