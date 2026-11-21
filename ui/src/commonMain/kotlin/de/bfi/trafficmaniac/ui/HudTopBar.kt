package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.TmIcon
import kotlin.math.roundToInt

internal val ChipShape = RoundedCornerShape(10.dp)
internal val RecallFill = Color(0xFFFF8A8F)

/** Icon-Farben der Kennzahlen wie im Entwurf: Kasse gelb, Reputation hellblau, Zorn und Abwahl hellrot. */
internal val MoneyIcon = TmColors.Active
internal val ReputationIcon = Color(0xFF8DB8F2)
internal val AlarmIcon = RecallFill

/** Ab diesem Abwahlindex wird der Chip rot hinterlegt. */
internal const val RECALL_DANGER = 50.0

/** Was die Kopfleiste auslösen kann; `null` blendet den jeweiligen Knopf aus. */
internal class TopBarActions(
    val onSpeed: ((Int) -> Unit)?,
    val onSave: (() -> Unit)?,
    val onMenu: (() -> Unit)?,
    /** Langes Tippen aufs Tempo (Handy): Entwickler-Anzeige ein/aus. */
    val onDebug: (() -> Unit)? = null,
    /** Nach der Auto-Bremse mit dem alten Tempo weiter. */
    val onResume: (() -> Unit)? = null,
    /** Vorspulen bis zum nächsten Ereignis. */
    val onFastForward: (() -> Unit)? = null,
)

/**
 * Dunkle Kopfleiste (56 dp): Logo, Tag und Uhrzeit, Tempo, rechts die Kennzahlen (Kasse, Reputation, Bürgerzorn,
 * Abwahlindex), Speichern und Menü. Alle Elemente sind 40 dp hoch.
 */
@Composable
internal fun HudTopBar(
    frame: TrafficFrame,
    actions: TopBarActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(
                DESKTOP_BAR_HEIGHT.dp,
            ).background(TmColors.Ink)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Clock(frame)
        actions.onSpeed?.let { SpeedSegments(frame.speed, it) }
        SpeedStatus(frame, actions)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            KpiChips(frame)
        }
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(32.dp)
                .background(TmColors.LineDark),
        )
        actions.onSave?.let { IconButton(Icons.Save, tr("Speichern (F5)"), it) }
        actions.onMenu?.let { IconButton(Icons.Menu, tr("Menü"), it) }
    }
}

@Composable
private fun Clock(frame: TrafficFrame) {
    val time = frame.current.gameTime
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Logo()
        Column(Modifier.widthIn(min = CLOCK_MIN_WIDTH.dp)) {
            BasicText(
                tr(
                    "{weekday} · Tag {day} von {termDays}",
                    "weekday" to time.weekday.germanName,
                    "day" to frame.game.termDay(time.day),
                    "termDays" to frame.game.termDays,
                ),
                style = TmType.text(11.sp, color = TmColors.OnDarkMuted),
                maxLines = 1,
            )
            BasicText(time.clockText, style = TmType.mono(18.sp, color = TmColors.OnDark))
        }
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

/**
 * Rechts neben dem Tempo: Vorspulen bis zum nächsten Ereignis, „weiter mit 32ד nach der Auto-Bremse und – wenn das
 * Gerät nicht nachkommt – das tatsächlich erreichte Tempo.
 */
@Composable
internal fun SpeedStatus(
    frame: TrafficFrame,
    actions: TopBarActions,
    compact: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val braked = frame.brakedFrom
        val resume = actions.onResume
        val forward = actions.onFastForward
        // Immer an derselben Stelle, nur ausgegraut, wenn es gerade nicht geht – die Kopfleiste springt nicht
        if (forward != null && !compact) ForwardButton(forward, enabled = frame.speed > 0 && !frame.fastForward)
        when {
            braked != null && resume != null -> {
                StatusChip(tr("gebremst · zurück auf {speed}×", "speed" to braked), TmColors.Active, resume)
            }

            frame.fastForward -> {
                StatusChip(tr("Spult vor …"), TmColors.OnDarkMuted, null)
            }
        }
        if (frame.lagging) {
            BasicText(
                tr("schafft {actual}×", "actual" to frame.actualSpeed.roundToInt().coerceAtLeast(1)),
                style = TmType.mono(11.sp, bold = false, color = TmColors.Warn),
            )
        }
    }
}

/** Vorspulen bis zum nächsten Ereignis – mit kurzer Beschriftung, das Symbol allein war unklar. */
@Composable
private fun ForwardButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    // Mit Beschriftung: das Symbol allein war unklar
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TmColors.Ink3)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { contentDescription = tr("Vorspulen bis zum nächsten Ereignis (V)") }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.FastForward, null, tint = TmColors.OnDark, size = 16.dp)
        BasicText(tr("bis Ereignis"), style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.OnDark))
        BasicText("V", style = TmType.mono(10.sp, bold = false, color = TmColors.OnDarkMuted))
    }
}

@Composable
private fun StatusChip(
    text: String,
    color: Color,
    onClick: (() -> Unit)?,
    icon: TmIcon? = null,
) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TmColors.Ink3)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, null, tint = color, size = 14.dp) }
        BasicText(text, style = TmType.text(12.sp, FontWeight.SemiBold, color))
    }
}

/** Pause und die Tempo-Knöpfe 1× bis 64× (Tasten 1–5) – das gewählte Tempo ist Ampelgelb. */
@Composable
private fun SpeedSegments(
    speed: Int,
    onSpeed: (Int) -> Unit,
) {
    Row(
        Modifier.background(TmColors.Ink3, ChipShape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((index, option) in (listOf(0) + GameRunner.TOP_SPEEDS).withIndex()) {
            val chosen = option == speed
            val color = if (chosen) TmColors.Ink else TmColors.OnDark
            val description =
                if (option ==
                    0
                ) {
                    tr("Pause (Leertaste)")
                } else {
                    tr("Tempo {speed}× ({key})", "speed" to option, "key" to index)
                }
            Box(
                Modifier
                    .widthIn(min = 40.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (chosen) TmColors.Active else Color.Transparent)
                    .clickable(role = Role.Button) { onSpeed(option) }
                    .semantics { contentDescription = description }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (option == 0) {
                    Icon(Icons.Pause, null, tint = color, size = 16.dp)
                } else {
                    BasicText("$option×", style = TmType.mono(13.sp, color = color))
                }
            }
        }
    }
}

/** Kasse (im Minus rot, daneben „heute ±x“), Reputation, Bürgerzorn und Abwahlindex mit Meter in fester Breite. */
@Composable
private fun KpiChips(frame: TrafficFrame) {
    val game = frame.game
    // Saldo heute aus dem Journal der Kämmerei – dieselbe Quelle wie dort „Saldo heute“
    val today =
        game.journal.today.values
            .sum()
    val minus = game.balance < 0
    Row(
        Modifier
            .height(
                CHIP_HEIGHT.dp,
            ).background(if (minus) MinusChip else TmColors.Ink3, ChipShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            BasicText(tr("Kasse"), style = TmType.text(10.sp, color = if (minus) MinusLabel else TmColors.OnDarkMuted))
            CountingEuro(game.balance, TmType.mono(15.sp, color = if (minus) MinusValue else TmColors.OnDark))
        }
        BasicText(
            tr("heute {delta}", "delta" to signedNumber(today)),
            style = TmType.mono(11.sp, bold = false, color = if (minus) MinusLabel else TmColors.OnDarkMuted),
        )
    }
    KpiValue(tr("Reputation"), "${game.reputation.roundToInt()}")
    KpiValue(tr("Bürgerzorn"), "${game.anger.roundToInt()}")
    // Feste Breite: das Label bricht nie um, der Meter bleibt im Chip; ab 50 rot hinterlegt
    val danger = game.recallIndex > RECALL_DANGER
    Column(
        Modifier
            .width(RECALL_CHIP_WIDTH.dp)
            .height(CHIP_HEIGHT.dp)
            .background(if (danger) TmColors.Danger.copy(alpha = DANGER_ALPHA) else TmColors.Ink3, ChipShape)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                tr("Abwahlindex"),
                style = TmType.text(10.sp, color = TmColors.OnDarkMuted),
                maxLines = 1,
                softWrap = false,
            )
            BasicText(
                "${game.recallIndex.roundToInt()}/100",
                style = TmType.mono(11.sp, color = TmColors.OnDark),
                maxLines = 1,
            )
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

/** „+1.234“ bzw. „−1.234“ (echtes Minuszeichen). */
private fun signedNumber(value: Long): String =
    when {
        value > 0 -> "+" + formatNumber(value)
        value < 0 -> "−" + formatNumber(-value)
        else -> "±0"
    }

@Composable
private fun KpiValue(
    label: String,
    value: String,
) {
    Column(
        Modifier.height(CHIP_HEIGHT.dp).background(TmColors.Ink3, ChipShape).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(label, style = TmType.text(10.sp, color = TmColors.OnDarkMuted), maxLines = 1)
        BasicText(value, style = TmType.mono(15.sp, color = TmColors.OnDark))
    }
}

/** Quadratischer Knopf (40 dp) auf dunklem Grund. */
@Composable
private fun IconButton(
    icon: TmIcon,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(CHIP_HEIGHT.dp)
            .clip(ChipShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = TmColors.OnDark, size = 20.dp)
    }
}

/** Kasse im Minus: dunkelroter Chip, helle Rottöne für Beschriftung und Wert. */
private val MinusChip = Color(0xFF3A1F28)
private val MinusLabel = Color(0xFFF3B7BD)
private val MinusValue = Color(0xFFFF9AA2)

private const val DESKTOP_BAR_HEIGHT = 56
private const val CHIP_HEIGHT = 40
private const val DISABLED_ALPHA = 0.4f
private const val CLOCK_MIN_WIDTH = 118
private const val MAX_INDEX = 100.0
private const val RECALL_CHIP_WIDTH = 150
private const val DANGER_ALPHA = 0.55f
private const val LOGO_HEIGHT = 34
private const val LOGO_ASPECT = 0.5f
private const val LOGO_CORNER = 0.7f
private const val LOGO_LAMP = 0.28f
private const val LOGO_LAMPS = 3
private const val HALF = 0.5f
