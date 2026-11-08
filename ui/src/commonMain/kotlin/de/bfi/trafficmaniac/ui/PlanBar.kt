package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.TmButton

private val BarShape = RoundedCornerShape(14.dp)
private val BarShadow = TmColors.Ink.copy(alpha = 0.25f)

/**
 * Leiste über dem Dock, solange etwas geplant ist: Anzahl und Summe, „Rückgängig“, „Verwerfen“ und „Bauen“.
 * Reicht die Kasse nicht für alles, steht die Summe rot da (gebaut wird trotzdem, was bezahlbar ist).
 */
@Composable
internal fun PlanBar(
    state: PlanBarState,
    modifier: Modifier = Modifier,
) {
    val tooExpensive = state.total > state.balance
    Row(
        modifier
            .widthIn(min = MIN_WIDTH.dp)
            .shadow(10.dp, BarShape, spotColor = BarShadow)
            .background(TmColors.Surface, BarShape)
            .border(2.dp, TmColors.Primary, BarShape)
            .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(tr("Planung").uppercase(), style = TmType.eyebrow)
            if (state.waitingForEnd) {
                BasicText(
                    tr("Anfang gesetzt – jetzt das Ende anklicken (dieselbe Stelle: nur bis zur Kreuzung)"),
                    style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.Primary),
                )
            }
            if (state.count > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    BasicText(
                        if (state.count == 1) tr("1 Maßnahme") else tr("{count} Maßnahmen", "count" to state.count),
                        style = TmType.text(14.sp, FontWeight.Bold),
                    )
                    BasicText(
                        formatEuro(state.total),
                        style = TmType.mono(14.sp, color = if (tooExpensive) TmColors.DangerText else TmColors.Ink),
                    )
                    if (tooExpensive) {
                        BasicText(
                            tr("Kasse: {balance}", "balance" to formatEuro(state.balance)),
                            style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.DangerText),
                        )
                    }
                }
            }
        }
        TmButton(tr("Rückgängig"), state.onUndo, kind = ButtonKind.TEXT)
        TmButton(tr("Verwerfen"), state.onDiscard, kind = ButtonKind.TEXT)
        TmButton(tr("Bauen"), state.onBuild, kind = ButtonKind.PRIMARY, enabled = state.count > 0)
    }
}

private const val MIN_WIDTH = 420
