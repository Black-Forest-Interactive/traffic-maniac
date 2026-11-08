package de.bfi.trafficmaniac.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.ui.TmColors
import de.bfi.trafficmaniac.ui.TmType

/** Mindesthöhe jeder Klickfläche (DESIGN.md §3). */
val MinTouch = 44.dp

private val SegmentShape = RoundedCornerShape(8.dp)
private val GroupShape = RoundedCornerShape(12.dp)
private val ButtonShape = RoundedCornerShape(10.dp)

/**
 * Auswahl aus wenigen gleichwertigen Möglichkeiten: Segmente gleicher Breite in einer Wanne. Gewählt = dunkel
 * gefüllt mit heller Schrift; auf dunklem Grund ([onDark]) Ampelgelb. [detail] liefert eine zweite Zeile in Mono
 * (z. B. Preis oder Toleranz).
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    detail: ((T) -> String?)? = null,
    onDark: Boolean = false,
) {
    val trough = if (onDark) TmColors.Ink3 else TmColors.Ground
    Row(
        modifier.background(trough, GroupShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in options) {
            val chosen = option == selected
            val fill =
                when {
                    !chosen -> Color.Transparent
                    onDark -> TmColors.Active
                    else -> TmColors.Ink
                }
            val textColor =
                when {
                    chosen && !onDark -> TmColors.Surface
                    onDark && !chosen -> TmColors.OnDark
                    else -> TmColors.Ink
                }
            val second = detail?.invoke(option)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = if (second != null) SEGMENT_TWO_LINES.dp else MinTouch)
                    .clip(SegmentShape)
                    .background(fill)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    label(option),
                    style =
                        TmType
                            .text(13.sp, if (chosen) FontWeight.Bold else FontWeight.SemiBold, textColor)
                            .copy(textAlign = TextAlign.Center),
                )
                if (second != null) {
                    BasicText(second, style = TmType.mono(11.sp, bold = false, color = textColor))
                }
            }
        }
    }
}

/**
 * Zahl mit − und + (und optional einem großen Schritt wie „+5“): Sachbearbeiter, Politessen, Fristen, Preise.
 * [value] wird in Mono angezeigt; nicht mögliche Schritte gibt man als `null` an.
 */
@Composable
fun Stepper(
    value: String,
    onMinus: (() -> Unit)?,
    onPlus: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bigStep: Pair<String, () -> Unit>? = null,
) {
    Row(
        modifier.clip(ButtonShape).border(1.dp, TmColors.Line, ButtonShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", onMinus)
        BasicText(
            value,
            style = TmType.mono(15.sp).copy(textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(min = 36.dp).padding(horizontal = 6.dp),
        )
        StepButton("+", onPlus)
        bigStep?.let { (text, action) ->
            Box(Modifier.size(1.dp, STEP_SIZE.dp).background(TmColors.Line))
            Box(
                Modifier
                    .height(STEP_SIZE.dp)
                    .background(TmColors.Ground)
                    .clickable(role = Role.Button, onClick = action)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { BasicText(text, style = TmType.mono(12.sp)) }
        }
    }
}

@Composable
private fun StepButton(
    symbol: String,
    onClick: (() -> Unit)?,
) {
    val enabled = onClick != null
    Box(
        Modifier
            .size(STEP_SIZE.dp)
            .background(TmColors.Surface)
            .then(if (enabled) Modifier.clickable(role = Role.Button) { onClick?.invoke() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            symbol,
            style = TmType.text(18.sp, FontWeight.SemiBold, if (enabled) TmColors.Ink else TmColors.Line),
        )
    }
}

/**
 * Entscheidung mit Satz-Beschriftungen als Liste von Radio-Zeilen (mind. 44 dp). Gewählt = Amtsblau-Rahmen auf
 * hellblauem Grund.
 */
@Composable
fun <T> RadioCardList(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (option in options) {
            val chosen = option == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .clip(ButtonShape)
                    .background(if (chosen) TmColors.PrimaryTint else TmColors.Surface)
                    .border(
                        if (chosen) 2.dp else 1.dp,
                        if (chosen) TmColors.Primary else TmColors.Line,
                        ButtonShape,
                    ).clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RadioDot(chosen)
                BasicText(label(option), style = TmType.text(13.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun RadioDot(chosen: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        val radius = size.minDimension / 2
        drawCircle(if (chosen) TmColors.Primary else TmColors.Muted, radius - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        if (chosen) drawCircle(TmColors.Primary, radius * DOT_SHARE)
    }
}

/** Art eines Knopfs: Hauptaktion (Amtsblau), zweitrangig (weiß mit Rahmen), Gefahr (Rot) oder nur Text. */
enum class ButtonKind { PRIMARY, SECONDARY, DANGER, TEXT }

/** Knopf mit mindestens 44 dp Höhe; [large] für die große Aktion eines Bildschirms (56 dp, 20 sp Display). */
@Composable
fun TmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val (fill, textColor, border) =
        when (kind) {
            ButtonKind.PRIMARY -> Triple(TmColors.Primary, TmColors.Surface, TmColors.Primary)
            ButtonKind.SECONDARY -> Triple(TmColors.Surface, TmColors.Ink, TmColors.Line)
            ButtonKind.DANGER -> Triple(TmColors.Danger, TmColors.Surface, TmColors.Danger)
            ButtonKind.TEXT -> Triple(Color.Transparent, TmColors.Primary, Color.Transparent)
        }
    val style =
        if (large) TmType.display(20.sp, textColor) else TmType.text(13.sp, FontWeight.SemiBold, textColor)
    Box(
        modifier
            .heightIn(min = if (large) LARGE_BUTTON.dp else MinTouch)
            .clip(ButtonShape)
            .background(if (enabled) fill else fill.copy(alpha = DISABLED_ALPHA))
            .border(1.dp, if (enabled) border else border.copy(alpha = DISABLED_ALPHA), ButtonShape)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = if (large) 24.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = if (enabled) style else style.copy(color = textColor.copy(alpha = DISABLED_ALPHA)))
    }
}

private const val SEGMENT_TWO_LINES = 50
private const val STEP_SIZE = 40
private const val LARGE_BUTTON = 56
private const val DOT_SHARE = 0.45f
private const val DISABLED_ALPHA = 0.4f
