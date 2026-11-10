package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Fahrradstraßen: breiter blauer Streifen mit gestrichelter weißer Mitte (in Weltkoordinaten). */
internal fun DrawScope.drawBikeStreets(
    segmentPaths: SegmentPaths,
    segments: Set<Int>,
    scale: Float,
) {
    if (segments.isEmpty()) return
    val band = Stroke(BAND_PIXELS / scale, cap = StrokeCap.Round)
    val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_PIXELS / scale, DASH_PIXELS / scale))
    val centre = Stroke(CENTRE_PIXELS / scale, pathEffect = dash)
    for (segment in segments) {
        drawPath(segmentPaths[segment], BIKE_BLUE, style = band)
        drawPath(segmentPaths[segment], Color.White, style = centre)
    }
}

/** Blau wie das Zusatzzeichen „Fahrradstraße“. */
private val BIKE_BLUE = Color(0xB31F5FAD)
private const val BAND_PIXELS = 10f
private const val CENTRE_PIXELS = 2f
private const val DASH_PIXELS = 6f
