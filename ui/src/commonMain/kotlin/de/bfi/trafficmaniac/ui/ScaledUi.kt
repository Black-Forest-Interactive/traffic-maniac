package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Dichte ohne UI-Skalierung: Die Karte zeichnet damit, Zoom und Linien hängen nicht von der Skalierung ab. */
internal val LocalBaseDensity = compositionLocalOf<Density?> { null }

/** Wählbare Oberflächengrößen in Prozent; 0 = automatisch nach Fensterbreite. */
val UI_SCALES = listOf(0, 90, 100, 125, 150)

/**
 * Skaliert Menü und HUD um [percent] (0 = automatisch: Fensterbreite / 1440 dp, zwischen 100 % und 150 %).
 * Der Entwurf ist für 1440 dp gezeichnet; auf breiten Fenstern wächst die Oberfläche so mit.
 */
@Composable
fun ScaledUi(
    percent: Int,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = uiScale(percent, maxWidth.value)
        CompositionLocalProvider(
            LocalBaseDensity provides base,
            LocalDensity provides Density(base.density * scale, base.fontScale),
            content = content,
        )
    }
}

/** Faktor für [percent] bei einem Fenster von [widthDp] (ohne Skalierung gemessen). */
fun uiScale(
    percent: Int,
    widthDp: Float,
): Float =
    if (percent > 0) {
        percent / PERCENT
    } else {
        (widthDp / DESIGN_WIDTH_DP).coerceIn(1f, MAX_AUTO_SCALE)
    }

/** Inhalt wieder ohne UI-Skalierung, z. B. die Karte. */
@Composable
internal fun Unscaled(content: @Composable () -> Unit) {
    val base = LocalBaseDensity.current
    if (base == null) content() else CompositionLocalProvider(LocalDensity provides base, content = content)
}

private const val PERCENT = 100f
private const val DESIGN_WIDTH_DP = 1440f
private const val MAX_AUTO_SCALE = 1.5f

/**
 * Passt Inhalte, die für mindestens [minWidthDp] × [minHeightDp] gezeichnet sind (Menü, Tagesabschluss), auf einen
 * kleineren Bildschirm ein, indem alles verkleinert wird; auf großen Bildschirmen bleibt alles, wie es ist.
 */
@Composable
fun FitToDesign(
    minWidthDp: Float = FIT_WIDTH_DP,
    minHeightDp: Float = FIT_HEIGHT_DP,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val factor = minOf(maxWidth.value / minWidthDp, maxHeight.value / minHeightDp, 1f)
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * factor, base.fontScale),
            content = content,
        )
    }
}

/** Bedienung per Finger statt Maus: keine Tastenkürzel und Tooltips, größere Ziele, Texte ohne Mausrad. */
internal val LocalTouch = staticCompositionLocalOf { false }

private const val FIT_WIDTH_DP = 1200f
private const val FIT_HEIGHT_DP = 700f
