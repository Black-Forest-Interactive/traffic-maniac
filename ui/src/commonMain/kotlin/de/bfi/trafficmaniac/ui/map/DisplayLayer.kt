package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.sim.SpeedCamera
import kotlin.math.roundToInt

/**
 * Geschwindigkeits-Anzeigetafeln: schwarze Tafel auf einem Pfahl, darauf der Anteil der zu Schnellen in LED-Farbe –
 * grün (wenige), gelb, rot (viele). So sieht man auf der Karte, wo gerast wird. In Weltkoordinaten gezeichnet.
 */
internal fun DrawScope.drawDisplays(
    cameras: List<SpeedCamera>,
    scale: Float,
    textMeasurer: TextMeasurer,
) {
    val height = signRadius(scale) * BOARD_HEIGHT
    val width = height * BOARD_ASPECT
    val ink = Stroke(height * INK)
    for (camera in cameras) {
        if (!camera.display) continue
        // Tafel etwas über dem Standort (y nach Norden), der Pfahl führt hinunter
        val center = Offset(camera.x, camera.y + height * LIFT)
        drawLine(MapStyle.ink, Offset(camera.x, camera.y), center, height * POST)
        val topLeft = center - Offset(width / 2, height / 2)
        drawRoundRect(BOARD, topLeft, Size(width, height), CornerRadius(height * CORNER))
        drawRoundRect(MapStyle.ink, topLeft, Size(width, height), CornerRadius(height * CORNER), style = ink)
        val share = if (camera.passed == 0) null else camera.speeding.toDouble() / camera.passed
        val text = share?.let { "${(it * PERCENT).roundToInt()} %" } ?: "– –"
        val color =
            when {
                share == null -> LED_IDLE
                share < LOW_SHARE -> LED_GREEN
                share < HIGH_SHARE -> LED_AMBER
                else -> LED_RED
            }
        val label =
            textMeasurer.measure(
                text,
                TextStyle(fontSize = FONT_SIZE.sp, fontWeight = FontWeight.Bold, color = color),
            )
        // Text in Bildschirmgröße: Weltskalierung (y nach Norden) für das Label wieder aufheben
        val factor = height / FONT_SIZE * TEXT_FACTOR
        withTransform({
            translate(center.x, center.y)
            scale(factor, -factor, pivot = Offset.Zero)
        }) {
            drawText(label, topLeft = Offset(-label.size.width / 2f, -label.size.height / 2f))
        }
    }
}

private val BOARD = Color(0xFF15202B)
private val LED_IDLE = Color(0xFF586674)
private val LED_GREEN = Color(0xFF3DDC84)
private val LED_AMBER = Color(0xFFFFC21A)
private val LED_RED = Color(0xFFFF5A5F)
private const val LOW_SHARE = 0.15
private const val HIGH_SHARE = 0.35
private const val PERCENT = 100
private const val BOARD_HEIGHT = 1.4f
private const val BOARD_ASPECT = 2.2f
private const val LIFT = 1.2f
private const val POST = 0.12f
private const val CORNER = 0.15f
private const val INK = 0.06f
private const val FONT_SIZE = 20f
private const val TEXT_FACTOR = 0.75f
