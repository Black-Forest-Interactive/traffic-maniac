package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.bfi.trafficmaniac.sim.WeatherSnapshot
import kotlin.math.max
import kotlin.math.min

/** Schnee auf den Straßen: weiß, je dicker, desto deckender (in Weltkoordinaten). */
internal fun DrawScope.drawSnow(
    segmentPaths: SegmentPaths,
    weather: WeatherSnapshot,
    scale: Float,
) {
    val width = max(SNOW_WIDTH_METERS, SNOW_MIN_PIXELS / scale)
    val stroke = Stroke(width, cap = StrokeCap.Round)
    weather.snow.forEachIndexed { segment, cm ->
        if (cm >= MIN_VISIBLE_CM) {
            val alpha = MIN_ALPHA + min(cm / FULL_WHITE_CM, 1f) * (MAX_ALPHA - MIN_ALPHA)
            drawPath(segmentPaths[segment], Color.White.copy(alpha = alpha), style = stroke)
        }
    }
}

/** Fallende Flocken über der Karte, solange es schneit (in Bildschirm-Pixeln, [seconds] treibt sie an). */
internal fun DrawScope.drawSnowfall(seconds: Double) {
    drawRect(Color.White.copy(alpha = VEIL_ALPHA), size = size)
    for (i in 0 until FLAKES) {
        // Feste Spalte je Flocke, Fallhöhe aus der Zeit – Fibonacci-Streuung, damit es nicht nach Raster aussieht
        val column = (i * GOLDEN) % 1.0
        val phase = (i * PHASE_STEP) % 1.0
        val speed = FALL_MIN + (i % SPEED_CLASSES) * FALL_STEP
        val y = ((seconds * speed / size.height + phase) % 1.0) * size.height
        val drift = kotlin.math.sin(seconds * DRIFT_SPEED + i) * DRIFT_PIXELS
        val x = column * size.width + drift
        drawCircle(Color.White.copy(alpha = FLAKE_ALPHA), FLAKE_RADIUS + (i % 2), Offset(x.toFloat(), y.toFloat()))
    }
}

private const val SNOW_WIDTH_METERS = 9f
private const val SNOW_MIN_PIXELS = 3f
private const val MIN_VISIBLE_CM = 0.3f
private const val FULL_WHITE_CM = 6f
private const val MIN_ALPHA = 0.35f
private const val MAX_ALPHA = 0.9f
private const val VEIL_ALPHA = 0.1f
private const val FLAKES = 160
private const val GOLDEN = 0.6180339887
private const val PHASE_STEP = 0.3819660113
private const val SPEED_CLASSES = 3
private const val FALL_MIN = 40.0
private const val FALL_STEP = 25.0
private const val DRIFT_SPEED = 0.8
private const val DRIFT_PIXELS = 12.0
private const val FLAKE_ALPHA = 0.85f
private const val FLAKE_RADIUS = 1.6f
