package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.bfi.trafficmaniac.city.RoadNode
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

/** Busspuren: rötlicher Streifen wie die eingefärbte Fahrbahn, darauf weiße Striche (in Weltkoordinaten). */
internal fun DrawScope.drawBusLanes(
    segmentPaths: SegmentPaths,
    segments: Set<Int>,
    scale: Float,
) {
    if (segments.isEmpty()) return
    val band = Stroke(LANE_PIXELS / scale, cap = StrokeCap.Butt)
    val dash = PathEffect.dashPathEffect(floatArrayOf(LANE_DASH / scale, LANE_GAP / scale))
    val marks = Stroke(LANE_MARK_PIXELS / scale, pathEffect = dash)
    for (segment in segments) {
        drawPath(segmentPaths[segment], BUS_LANE_RED, style = band)
        drawPath(segmentPaths[segment], Color.White, style = marks)
    }
}

/** Busvorrang: kleines Bussignal (weißer Balken auf Schwarz, wie das echte „Bus frei“) an der Kreuzung. */
internal fun DrawScope.drawBusPriority(
    nodes: List<RoadNode>,
    scale: Float,
) {
    val size = signRadius(scale) * PRIORITY_SIZE
    for (node in nodes) {
        val topLeft = Offset(node.x - size / 2, node.y - size / 2)
        drawRoundRect(PRIORITY_BOX, topLeft, Size(size, size), CornerRadius(size * PRIORITY_CORNER))
        drawLine(
            Color.White,
            Offset(node.x - size * PRIORITY_BAR, node.y),
            Offset(node.x + size * PRIORITY_BAR, node.y),
            size * PRIORITY_BAR_WIDTH,
        )
    }
}

private val BUS_LANE_RED = Color(0x99B5332E)
private val PRIORITY_BOX = Color(0xFF15202B)
private const val LANE_PIXELS = 8f
private const val LANE_MARK_PIXELS = 1.5f
private const val LANE_DASH = 10f
private const val LANE_GAP = 14f
private const val PRIORITY_SIZE = 1.1f
private const val PRIORITY_CORNER = 0.2f
private const val PRIORITY_BAR = 0.3f
private const val PRIORITY_BAR_WIDTH = 0.18f
private val SIGN_YELLOW = Color(0xFFFFD500)
private val SIGN_GREEN = Color(0xFF00843D)
private const val STOP_SIZE = 0.75f
private const val RING = 0.16f
private const val BAR = 0.16f
private const val LETTER = 0.5f
private const val WIDTH = 0.6f
