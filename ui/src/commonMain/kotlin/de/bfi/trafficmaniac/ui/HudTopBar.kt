package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.MinTouch
import kotlin.math.roundToInt

private val ChipShape = RoundedCornerShape(10.dp)
private val RecallFill = Color(0xFFFF8A8F)

/** Icon-Farben der Kennzahlen wie im Entwurf: Kasse gelb, Reputation hellblau, Zorn und Abwahl hellrot. */
private val MoneyIcon = TmColors.Active
private val ReputationIcon = Color(0xFF8DB8F2)
private val AlarmIcon = RecallFill

/** Ab diesem Abwahlindex wird der Chip rot hinterlegt. */
private const val RECALL_DANGER = 50.0

/** Was die Kopfleiste auslösen kann; `null` blendet den jeweiligen Knopf aus. */
internal class TopBarActions(
    val onSpeed: ((Int) -> Unit)?,
    val onSave: (() -> Unit)?,
    val onMenu: (() -> Unit)?,
)

/** Dunkle Kopfleiste: Tag und Uhrzeit, Zeitraffer, Kennzahlen (Kasse, Reputation, Zorn, Abwahl), Speichern, Menü. */
@Composable
internal fun HudTopBar(
    frame: TrafficFrame,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(TmColors.Ink).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Clock(frame)
        actions.onSpeed?.let { SpeedSegments(frame.speed, it) }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) { KpiChips(frame) }
        actions.onSave?.let { DarkButton(it, tr("Speichern")) }
        actions.onMenu?.let { DarkButton(it, null) }
    }
}

@Composable
private fun Clock(frame: TrafficFrame) {
    val time = frame.current.gameTime
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Logo()
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(
                tr(
                    "{weekday} · Tag {day} von {termDays}",
                    "weekday" to time.weekday.germanName,
                    "day" to frame.game.termDay(time.day),
                    "termDays" to frame.game.termDays,
                ),
                style = TmType.text(12.sp, color = TmColors.OnDarkMuted),
            )
            BasicText(time.clockText, style = TmType.mono(20.sp, color = TmColors.OnDark))
        }
        Box(Modifier.width(1.dp).height(36.dp).background(TmColors.LineDark))
    }
}

/** Kleine Ampel als Logo. */
@Composable
internal fun Logo(size: Int = LOGO_HEIGHT) {
    Canvas(Modifier.size((size * LOGO_ASPECT).dp, size.dp)) {
        val radius = this.size.width / 2
        drawRoundRect(TmColors.Ink3, cornerRadius = CornerRadius(radius * LOGO_CORNER))
        val lamp = this.size.width * LOGO_LAMP
        val step = this.size.height / LOGO_LAMPS
        listOf(TmColors.LogoRed, TmColors.Active, TmColors.Success).forEachIndexed { i, color ->
            drawCircle(color, lamp, Offset(this.size.width / 2, step * (i + HALF)))
        }
    }
}

/** Pause und Zeitraffer 1×/2×/4×/8× – das gewählte Tempo ist Ampelgelb. */
@Composable
private fun SpeedSegments(
    speed: Int,
    onSpeed: (Int) -> Unit,
) {
    Row(
        Modifier.background(TmColors.Ink3, ChipShape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (option in listOf(0) + SPEEDS) {
            val chosen = option == speed
            val color = if (chosen) TmColors.Ink else TmColors.OnDark
            Box(
                Modifier
                    .widthIn(min = 40.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (chosen) TmColors.Active else Color.Transparent)
                    .clickable(role = Role.Button) { onSpeed(option) },
                contentAlignment = Alignment.Center,
            ) {
                if (option == 0) {
                    Icon(Icons.Pause, tr("Pause"), tint = color, size = 16.dp)
                } else {
                    BasicText("$option×", style = TmType.mono(13.sp, color = color))
                }
            }
        }
    }
}

@Composable
private fun RowScope.KpiChips(frame: TrafficFrame) {
    val game = frame.game
    KpiChip(Icons.Wallet, MoneyIcon) {
        KpiValue(tr("Kasse"), formatEuro(game.balance))
        Box(Modifier.width(1.dp).height(28.dp).background(TmColors.LineDark))
        BasicText(tr("Bußgelder"), style = TmType.text(12.sp, color = TmColors.OnDarkMuted))
        BasicText(formatEuro(game.fineIncome), style = TmType.mono(12.sp, color = TmColors.OnDark))
    }
    KpiChip(Icons.Award, ReputationIcon) { KpiValue(tr("Reputation"), "${game.reputation.roundToInt()}", "/100") }
    KpiChip(Icons.Siren, AlarmIcon) { KpiValue(tr("Bürgerzorn"), "${game.anger.roundToInt()}") }
    // Feste Breite: der Meter bleibt im Chip, statt die Kopfleiste zu füllen; ab 50 rot hinterlegt
    val danger = game.recallIndex > RECALL_DANGER
    KpiChip(
        Icons.Users,
        AlarmIcon,
        Modifier.width(RECALL_CHIP_WIDTH.dp),
        background = if (danger) TmColors.Danger.copy(alpha = DANGER_ALPHA) else TmColors.Ink3,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(tr("Abwahlindex"), style = TmType.text(11.sp, color = TmColors.OnDarkMuted))
                BasicText("${game.recallIndex.roundToInt()}/100", style = TmType.mono(11.sp, color = TmColors.OnDark))
            }
            Meter(
                (game.recallIndex / MAX_INDEX).toFloat(),
                Modifier.fillMaxWidth(),
                height = 6.dp,
                color = RecallFill,
                track = TmColors.LineDark,
            )
        }
    }
}

@Composable
private fun KpiChip(
    icon: de.bfi.trafficmaniac.ui.components.TmIcon,
    iconTint: Color,
    modifier: Modifier = Modifier,
    background: Color = TmColors.Ink3,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.background(background, ChipShape).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = iconTint, size = 18.dp)
        content()
    }
}

@Composable
private fun KpiValue(
    label: String,
    value: String,
    suffix: String = "",
) {
    Column {
        BasicText(label, style = TmType.text(11.sp, color = TmColors.OnDarkMuted))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(value, style = TmType.mono(15.sp, color = TmColors.OnDark))
            if (suffix.isNotEmpty()) {
                BasicText(suffix, style = TmType.mono(15.sp, bold = false, color = TmColors.OnDarkMuted))
            }
        }
    }
}

/** Umrandeter Knopf auf dunklem Grund; ohne [label] ein quadratischer Menü-Knopf. */
@Composable
private fun DarkButton(
    onClick: () -> Unit,
    label: String?,
) {
    Row(
        Modifier
            .heightIn(min = MinTouch)
            .widthIn(min = MinTouch)
            .clip(ChipShape)
            .border(1.dp, TmColors.LineDark, ChipShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (label != null) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(if (label != null) Icons.Save else Icons.Menu, label ?: tr("Menü"), tint = TmColors.OnDark, size = 20.dp)
        label?.let { BasicText(it, style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.OnDark)) }
    }
}

private val SPEEDS = listOf(1, 2, 4, GameRunner.MAX_SPEED)
private const val MAX_INDEX = 100.0
private const val RECALL_CHIP_WIDTH = 180
private const val DANGER_ALPHA = 0.55f
private const val LOGO_HEIGHT = 34
private const val LOGO_ASPECT = 0.5f
private const val LOGO_CORNER = 0.7f
private const val LOGO_LAMP = 0.28f
private const val LOGO_LAMPS = 3
private const val HALF = 0.5f
