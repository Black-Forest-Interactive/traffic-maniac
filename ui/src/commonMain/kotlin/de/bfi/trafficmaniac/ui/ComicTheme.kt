package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Einheitlicher Comic-Stil für HUD, Werkzeugleiste, Fenster und Dialoge: cremefarbenes Papier, dunkle Tusche,
 * kräftige Akzente und ein versetzter harter Schatten wie im Comicheft.
 */
object ComicTheme {
    val paper = Color(0xFFFFF4D6)
    val paperDark = Color(0xFFF3E2B8)
    val ink = Color(0xFF2B2121)
    val muted = Color(0xFF7A6A5C)
    val red = Color(0xFFE63946)
    val orange = Color(0xFFE76F00)
    val green = Color(0xFF2A9D58)
    val button = Color(0xFFFFE2A8)
    val track = Color(0xFFE8D9B5)
    val shadow = Color(0x592B2121)
    val scrim = Color(0x99000000)

    val panelShape = RoundedCornerShape(12.dp)
    val buttonShape = RoundedCornerShape(8.dp)
    val border: Dp = 2.5.dp
    val shadowOffset: Dp = 5.dp
}

/** Comic-Kasten: Papier, Tusche-Rand und harter Schatten nach rechts unten. */
fun Modifier.comicPanel(
    background: Color = ComicTheme.paper,
    shadow: Dp = ComicTheme.shadowOffset,
): Modifier =
    drawBehind {
        val offset = shadow.toPx()
        val radius = CORNER_DP.dp.toPx()
        drawRoundRect(ComicTheme.shadow, Offset(offset, offset), size, CornerRadius(radius))
    }.background(background, ComicTheme.panelShape)
        .border(ComicTheme.border, ComicTheme.ink, ComicTheme.panelShape)

/** Knopf im Comic-Stil: aktiv rot, sonst cremegelb, immer mit Tusche-Rand. */
fun Modifier.comicButton(active: Boolean): Modifier =
    background(if (active) ComicTheme.red else ComicTheme.button, ComicTheme.buttonShape)
        .border(BUTTON_BORDER_DP.dp, ComicTheme.ink, ComicTheme.buttonShape)

private const val CORNER_DP = 12
private const val BUTTON_BORDER_DP = 1.5f
