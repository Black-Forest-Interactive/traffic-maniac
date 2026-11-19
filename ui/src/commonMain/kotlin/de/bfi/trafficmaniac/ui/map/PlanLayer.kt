package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Geplante Maßnahme auf der Karte: Ort, kurze Beschriftung, betroffene Abschnitte; [removal] = Abbau. */
data class PlanMarker(
    val x: Float,
    val y: Float,
    val label: String,
    val removal: Boolean = false,
    val segments: List<Int> = emptyList(),
)

/** Was geplant ist und wo beim Tempolimit der Anfang gesetzt wurde (Abschnitt) – für die Vorschau. */
class PlanPreview(
    val markers: List<PlanMarker>,
    val routeStart: Int?,
    val startLabel: String,
)

/**
 * Vorschau der Planung: betroffene Strecken gestrichelt, je Maßnahme ein gestrichelter Kreis mit Beschriftung,
 * Abbau in Rot. Gezeichnet in Bildschirm-Pixeln, damit es bei jedem Zoom gleich groß bleibt.
 */
internal fun DrawScope.drawPlan(
    preview: PlanPreview,
    camera: Camera,
    picker: MapPicker,
    segmentPaths: SegmentPaths,
    textMeasurer: TextMeasurer,
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))
    inWorld(camera) {
        for (marker in preview.markers) {
            val color = if (marker.removal) REMOVAL else PLANNED
            for (segment in marker.segments) {
                drawPath(
                    segmentPaths[segment],
                    color,
                    style =
                        Stroke(
                            ROUTE_WIDTH / camera.scale,
                            pathEffect =
                                PathEffect.dashPathEffect(
                                    floatArrayOf(DASH / camera.scale, DASH / camera.scale),
                                ),
                        ),
                )
            }
        }
    }
    for (marker in preview.markers) {
        val center = Offset(camera.worldToScreenX(marker.x, size.width), camera.worldToScreenY(marker.y, size.height))
        val color = if (marker.removal) REMOVAL else PLANNED
        drawCircle(Color.White.copy(alpha = FILL_ALPHA), RING, center)
        drawCircle(color, RING, center, style = Stroke(RING_STROKE, pathEffect = dash))
        label(textMeasurer, marker.label, center + Offset(0f, RING + LABEL_GAP), color)
    }
    preview.routeStart?.let { segment ->
        val (x, y) = picker.midpoint(segment)
        val center = Offset(camera.worldToScreenX(x, size.width), camera.worldToScreenY(y, size.height))
        drawCircle(PLANNED, START_DOT, center)
        drawCircle(Color.White, START_DOT, center, style = Stroke(RING_STROKE))
        label(textMeasurer, preview.startLabel, center + Offset(0f, START_DOT + LABEL_GAP), PLANNED)
    }
}

/** Beschriftung als weiße Pille mit farbiger Schrift, oben mittig an [top]. */
private fun DrawScope.label(
    textMeasurer: TextMeasurer,
    text: String,
    top: Offset,
    color: Color,
) {
    val layout = textMeasurer.measure(text, LABEL_STYLE.copy(color = color))
    val width = layout.size.width + 2 * PAD
    val height = layout.size.height.toFloat()
    val topLeft = top - Offset(width / 2, 0f)
    drawRoundRect(Color.White, topLeft, Size(width, height), CornerRadius(height / 2))
    drawRoundRect(color, topLeft, Size(width, height), CornerRadius(height / 2), style = Stroke(1f))
    drawText(layout, topLeft = topLeft + Offset(PAD, 0f))
}

/** Amtsblau wie im Design: geplant. */
private val PLANNED = Color(0xFF2257B8)

/** Alarmrot: geplanter Abbau. */
private val REMOVAL = Color(0xFFC62B3B)
private val LABEL_STYLE = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
private const val FILL_ALPHA = 0.7f
private const val RING = 13f
private const val RING_STROKE = 2.5f
private const val START_DOT = 7f
private const val DASH = 6f
private const val ROUTE_WIDTH = 6f
private const val LABEL_GAP = 3f
private const val PAD = 6f
