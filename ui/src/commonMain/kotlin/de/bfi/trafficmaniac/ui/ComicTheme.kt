package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Brücke vom alten Comic-Stil zum Design „Leitstelle“: Die Namen bleiben, damit noch nicht umgebaute Fenster
 * weiter funktionieren, die Farben kommen aus [TmColors]. Neue Oberflächen verwenden [TmColors], [TmType] und die
 * Bausteine aus `ui.components` direkt.
 */
object ComicTheme {
    val paper = TmColors.Surface
    val paperDark = TmColors.Ground
    val ink = TmColors.Ink
    val muted = TmColors.Muted
    val red = TmColors.Danger
    val orange = TmColors.Warn
    val green = TmColors.Success
    val button = TmColors.Surface
    val track = TmColors.Track
    val shadow = TmColors.Ink.copy(alpha = SHADOW_ALPHA)
    val scrim = TmColors.Scrim

    val panelShape = RoundedCornerShape(14.dp)
    val buttonShape = RoundedCornerShape(8.dp)
}

/** Fenster über der Karte: weiß, feiner Rahmen, weicher Schatten. [shadow] ist die Schattenhöhe. */
fun Modifier.comicPanel(
    background: Color = ComicTheme.paper,
    shadow: Dp = PANEL_ELEVATION.dp,
): Modifier =
    shadow(shadow, ComicTheme.panelShape, spotColor = ComicTheme.shadow, ambientColor = ComicTheme.shadow)
        .background(background, ComicTheme.panelShape)
        .border(1.dp, TmColors.Line, ComicTheme.panelShape)

/** Knopf: gewählt Ampelgelb (Text dunkel), sonst weiß mit feinem Rahmen. */
fun Modifier.comicButton(active: Boolean): Modifier =
    background(if (active) TmColors.Active else TmColors.Surface, ComicTheme.buttonShape)
        .border(1.dp, if (active) TmColors.Active else TmColors.Line, ComicTheme.buttonShape)

private const val PANEL_ELEVATION = 6
private const val SHADOW_ALPHA = 0.18f
