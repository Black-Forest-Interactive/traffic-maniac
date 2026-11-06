package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.formatEuro
import kotlin.math.roundToInt

private val Scrim = Color(0xB3000000)
private val Paper = Color(0xFFFFF8E7)
private val Ink = Color(0xFF1D1F2E)
private val Alarm = Color(0xFFE63946)
private const val HONORARY_CITIZEN = 70.0

/** Spielende: abgewählt oder Amtszeit vorbei – im zweiten Fall mit Wertung und Abschiedsrede. */
@Composable
fun ResultOverlay(
    state: GameState,
    modifier: Modifier = Modifier,
) {
    val recalled = state.isGameOver
    val text = TextStyle(color = Ink, fontSize = 14.sp)
    Box(modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(560.dp)
                .background(Paper, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                if (recalled) "Abgewählt!" else "Abschiedsrede nach ${state.termDays} Tagen im Amt",
                style = TextStyle(color = if (recalled) Alarm else Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold),
            )
            BasicText(
                if (recalled) {
                    "Der Gemeinderat hat Ihnen nach ${state.daysPlayed} Tagen das Vertrauen entzogen."
                } else {
                    "„Liebe Pforzheimerinnen und Pforzheimer, ich hinterlasse diese Stadt besser, als ich sie vorgefunden habe.“"
                },
                style = text.copy(fontSize = 15.sp),
            )
            BasicText(
                "Kasse: ${formatEuro(state.balance)} · selbst verdient: ${formatEuro(state.score.earnings)}",
                style = text,
            )
            BasicText("Bußgelder eingenommen: ${formatEuro(state.fineIncome)}", style = text)
            BasicText(
                "Reputation: ${state.reputation.roundToInt()} / 100 (Faktor ×${"%.2f".format(
                    state.score.reputationFactor,
                )})",
                style = text,
            )
            if (state.achievements.isNotEmpty()) {
                BasicText(
                    "Achievements: ${state.achievements.joinToString()}",
                    style = text,
                )
            }
            if (!recalled) {
                BasicText(
                    "Punkte: ${state.score.points}",
                    style = text.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    if (state.reputation >=
                        HONORARY_CITIZEN
                    ) {
                        "Ehrenbürgerschaft verliehen!"
                    } else {
                        "Keine Ehrenbürgerschaft. Schade."
                    },
                    style = text,
                )
            }
        }
    }
}
