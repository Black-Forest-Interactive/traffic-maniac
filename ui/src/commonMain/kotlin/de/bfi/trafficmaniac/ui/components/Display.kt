package de.bfi.trafficmaniac.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.ui.TmColors
import de.bfi.trafficmaniac.ui.TmType

/** Kennzahl-Kachel: Beschriftung klein und grau, Wert in Mono, optional [Meter] oder [footer]-Zeile darunter. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TmColors.Ink,
    meter: Float? = null,
    meterColor: Color = TmColors.Primary,
    footer: String? = null,
) {
    Column(
        modifier.background(TmColors.Ground, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(label, style = TmType.caption)
        BasicText(value, style = TmType.mono(17.sp, color = valueColor))
        meter?.let { Meter(it, Modifier.fillMaxWidth().padding(top = 4.dp), height = 6.dp, color = meterColor) }
        footer?.let { BasicText(it, style = TmType.caption) }
    }
}

/**
 * Füllstand 0..1 als Leiste. [threshold] markiert eine Schwelle (z. B. „Zorn ab 35 %“) mit einem roten Strich.
 */
@Composable
fun Meter(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = TmColors.Primary,
    track: Color = TmColors.Track,
    threshold: Float? = null,
) {
    Canvas(modifier.height(height)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val filled = size.width * fraction.coerceIn(0f, 1f)
        if (filled > 0) drawRoundRect(color, size = Size(filled, size.height), cornerRadius = radius)
        threshold?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            val mark = 2.dp.toPx()
            drawRect(TmColors.DangerText, Offset(x - mark / 2, -mark), Size(mark, size.height + 2 * mark))
        }
    }
}

/** Tonlage eines [Badge]. */
enum class BadgeTone(
    val background: Color,
    val text: Color,
) {
    DANGER(TmColors.DangerTint, TmColors.DangerText),
    WARN(TmColors.WarnTint, TmColors.WarnText),
    SUCCESS(TmColors.SuccessTint, TmColors.SuccessText),
    NEUTRAL(TmColors.Ground, TmColors.Muted),
    INFO(TmColors.PrimaryTint, TmColors.Primary),
}

/** Kleines Etikett: Zähler, Fristen, Zustände. [pill] = rund (Zähler), sonst Chip mit 6 dp Ecken (Fristen). */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.NEUTRAL,
    pill: Boolean = true,
) {
    BasicText(
        text,
        style = TmType.text(11.sp, FontWeight.Bold, tone.text),
        modifier =
            modifier
                .background(tone.background, RoundedCornerShape(if (pill) 10.dp else 6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
