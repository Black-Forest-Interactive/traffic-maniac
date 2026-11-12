package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.bfi.trafficmaniac.city.TransitStop

/**
 * Bushaltestellen wie das Schild an der Straße: gelber Kreis mit grünem Rand und grünem „H“. In Weltkoordinaten
 * gezeichnet, nur was im Bild ist.
 */
internal fun DrawScope.drawTransitStops(
    stops: List<TransitStop>,
    scale: Float,
    viewport: Rect,
) {
    val radius = signRadius(scale) * STOP_SIZE
    val ring = Stroke(radius * RING)
    val bar = radius * BAR
    for (stop in stops) {
        if (!viewport.contains(Offset(stop.x, stop.y))) continue
        val center = Offset(stop.x, stop.y)
        drawCircle(SIGN_YELLOW, radius, center)
        drawCircle(SIGN_GREEN, radius, center, style = ring)
        // „H“: zwei Striche und der Querbalken
        val half = radius * LETTER
        drawLine(SIGN_GREEN, center + Offset(-half * WIDTH, -half), center + Offset(-half * WIDTH, half), bar)
        drawLine(SIGN_GREEN, center + Offset(half * WIDTH, -half), center + Offset(half * WIDTH, half), bar)
        drawLine(SIGN_GREEN, center + Offset(-half * WIDTH, 0f), center + Offset(half * WIDTH, 0f), bar)
    }
}

private val SIGN_YELLOW = Color(0xFFFFD500)
private val SIGN_GREEN = Color(0xFF00843D)
private const val STOP_SIZE = 0.75f
private const val RING = 0.16f
private const val BAR = 0.16f
private const val LETTER = 0.5f
private const val WIDTH = 0.6f
