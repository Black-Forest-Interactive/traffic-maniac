package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Grüne Wellen als breiter, halbtransparenter grüner Streifen unter dem Verkehr (in Weltkoordinaten). */
internal fun DrawScope.drawGreenWaves(
    segmentPaths: SegmentPaths,
    waves: List<List<Int>>,
    scale: Float,
) {
    val stroke = Stroke(WAVE_WIDTH / scale, cap = StrokeCap.Round)
    for (wave in waves) {
        for (segment in wave) drawPath(segmentPaths[segment], WAVE_COLOR, style = stroke)
    }
}

private val WAVE_COLOR = Color(0x6631A354)

/** Breite in Bildschirm-Pixeln. */
private const val WAVE_WIDTH = 12f
