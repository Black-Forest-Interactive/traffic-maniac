package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon
import kotlin.math.roundToInt

/**
 * Schmale Kopfleiste für den Finger (48 dp): Menü, Tag und Uhr, Pause, Tempo (antippen = nächste Stufe) und die
 * Kennzahlen nur als Symbol und Zahl – die Namen stehen in der Legende.
 */
@Composable
internal fun TouchTopBar(
    frame: TrafficFrame,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT.dp)
            .background(TmColors.Ink)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        actions.onMenu?.let { BarButton(Icons.Menu, tr("Menü"), onClick = it) }
        TouchClock(frame)
        actions.onSpeed?.let { SpeedButtons(frame.speed, it) }
        Spacer(Modifier.weight(1f))
        TouchKpis(frame)
        actions.onSave?.let { BarButton(Icons.Save, tr("Speichern"), onClick = it) }
    }
}

@Composable
private fun TouchClock(frame: TrafficFrame) {
    val time = frame.current.gameTime
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Logo(size = LOGO_SIZE)
        Column {
            BasicText(
                tr(
                    "{weekday} · Tag {day}/{termDays}",
                    "weekday" to time.weekday.germanName.take(2),
                    "day" to frame.game.termDay(time.day),
                    "termDays" to frame.game.termDays,
                ),
                style = TmType.text(10.sp, color = TmColors.OnDarkMuted),
            )
            BasicText(time.clockText, style = TmType.mono(16.sp, color = TmColors.OnDark))
        }
    }
}

/** Pause/Weiter und daneben das Tempo; ein Tipp auf das Tempo nimmt die nächste Stufe (1× → 2× → 4× → 8× → 1×). */
@Composable
private fun SpeedButtons(
    speed: Int,
    onSpeed: (Int) -> Unit,
) {
    var last by remember { mutableIntStateOf(1) }
    if (speed > 0) last = speed
    val paused = speed == 0
    Row(
        Modifier.background(TmColors.Ink3, ChipShape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BarButton(
            if (paused) Icons.Play else Icons.Pause,
            if (paused) tr("Weiter") else tr("Pause"),
            active = paused,
        ) { onSpeed(if (paused) last else 0) }
        Box(
            Modifier
                .widthIn(min = BUTTON_SIZE.dp)
                .height(BUTTON_SIZE.dp)
                .clip(ButtonShape)
                .background(if (paused) Color.Transparent else TmColors.Active)
                .clickable(role = Role.Button) { onSpeed(SPEEDS[(SPEEDS.indexOf(last) + 1) % SPEEDS.size]) },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "$last×",
                style = TmType.mono(14.sp, color = if (paused) TmColors.OnDarkMuted else TmColors.Ink),
            )
        }
    }
}

@Composable
private fun RowScope.TouchKpis(frame: TrafficFrame) {
    val game = frame.game
    Kpi(Icons.Wallet, MoneyIcon, formatEuro(game.balance))
    Kpi(Icons.Award, ReputationIcon, "${game.reputation.roundToInt()}")
    Kpi(Icons.Siren, AlarmIcon, "${game.anger.roundToInt()}")
    // Abwahlindex: ab 50 rot hinterlegt wie am Desktop
    val danger = game.recallIndex > RECALL_DANGER
    Kpi(
        Icons.Users,
        AlarmIcon,
        "${game.recallIndex.roundToInt()}",
        if (danger) TmColors.Danger.copy(alpha = DANGER_ALPHA) else TmColors.Ink3,
    )
}

@Composable
private fun Kpi(
    icon: TmIcon,
    tint: Color,
    value: String,
    background: Color = TmColors.Ink3,
) {
    Row(
        Modifier.height(BUTTON_SIZE.dp).background(background, ChipShape).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = tint, size = 16.dp)
        BasicText(value, style = TmType.mono(14.sp, color = TmColors.OnDark), maxLines = 1)
    }
}

/** Quadratischer Knopf mit Symbol, mindestens so groß wie eine Fingerkuppe. */
@Composable
internal fun BarButton(
    icon: TmIcon,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(BUTTON_SIZE.dp)
            .clip(ButtonShape)
            .background(if (active) TmColors.Active else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (active) TmColors.Ink else TmColors.OnDark, size = 20.dp) }
}

private val ButtonShape = RoundedCornerShape(8.dp)
private val SPEEDS = listOf(1, 2, 4, GameRunner.MAX_SPEED)
internal const val TOP_BAR_HEIGHT = 48
private const val BUTTON_SIZE = 40
private const val LOGO_SIZE = 26
private const val DANGER_ALPHA = 0.55f
