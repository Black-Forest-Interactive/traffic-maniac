package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.ZoneMood
import de.bfi.trafficmaniac.sim.SignalMarkers
import de.bfi.trafficmaniac.sim.SignalState
import de.bfi.trafficmaniac.sim.SpeedCamera
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

private const val MIN_VEHICLE_PIXELS = 2.5f

/**
 * Zeichnet alle Fahrzeuge als Striche von Heck zu Front (in Weltkoordinaten).
 * Positionen werden zwischen den beiden Snapshots des [TrafficFrame] interpoliert.
 */
internal fun DrawScope.drawVehicles(
    frame: TrafficFrame,
    scale: Float,
) {
    val previous = frame.previous
    val current = frame.current
    val t = frame.interpolation()
    var j = 0
    for (i in 0 until current.vehicleCount) {
        val id = current.ids[i]
        // Beide Snapshots sind nach ID sortiert: passendes Fahrzeug im vorherigen Snapshot suchen
        while (j < previous.vehicleCount && previous.ids[j] < id) j++
        var x = current.x[i]
        var y = current.y[i]
        if (j < previous.vehicleCount && previous.ids[j] == id) {
            x = previous.x[j] + (x - previous.x[j]) * t
            y = previous.y[j] + (y - previous.y[j]) * t
        }
        val length = current.lengths[i]
        drawLine(
            color = Color(current.colors[i]),
            start = Offset(x - current.dirX[i] * length, y - current.dirY[i] * length),
            end = Offset(x, y),
            strokeWidth = max(current.widths[i], MIN_VEHICLE_PIXELS / scale),
            cap = StrokeCap.Butt,
        )
    }
}

/** Ampeln als farbige Punkte an den Haltelinien. */
internal fun DrawScope.drawSignals(
    markers: SignalMarkers,
    states: Array<SignalState>,
    scale: Float,
) {
    val width = max(2 * MapStyle.SIGNAL_RADIUS_METERS, MapStyle.MIN_SIGNAL_PIXELS / scale)
    for (state in listOf(SignalState.GREEN, SignalState.YELLOW, SignalState.RED)) {
        val points = states.indices.filter { states[it] == state }.map { Offset(markers.x[it], markers.y[it]) }
        drawPoints(points, PointMode.Points, signalColor(state), strokeWidth = width, cap = StrokeCap.Round)
    }
}

private fun signalColor(state: SignalState): Color =
    when (state) {
        SignalState.GREEN -> MapStyle.signalGreen
        SignalState.YELLOW -> MapStyle.signalYellow
        else -> MapStyle.trafficSignal
    }

/** Tempolimit-Schilder (rot umrandete Kreise) an den vom Spieler geänderten Abschnitten. */
internal fun DrawScope.drawSpeedSigns(
    signs: List<SpeedSign>,
    scale: Float,
    textMeasurer: TextMeasurer,
) {
    val radius = max(SIGN_RADIUS_METERS, SIGN_MIN_PIXELS / scale)
    for (sign in signs) {
        val center = Offset(sign.x, sign.y)
        drawCircle(Color.White, radius, center)
        drawCircle(MapStyle.trafficSignal, radius, center, style = Stroke(width = radius * SIGN_RING))
        val label =
            textMeasurer.measure(
                sign.label,
                TextStyle(fontSize = SIGN_FONT_SIZE.sp, fontWeight = FontWeight.Bold),
            )
        // Text in Bildschirmgröße: Weltskalierung (y nach Norden) für das Label wieder aufheben
        withTransform({
            translate(center.x, center.y)
            scale(
                radius / SIGN_FONT_SIZE * SIGN_TEXT_FACTOR,
                -radius / SIGN_FONT_SIZE * SIGN_TEXT_FACTOR,
                pivot = Offset.Zero,
            )
        }) {
            drawText(label, topLeft = Offset(-label.size.width / 2f, -label.size.height / 2f))
        }
    }
}

/** Feste Blitzer als dunkle Kästen mit "Blitz". */
internal fun DrawScope.drawCameras(
    cameras: List<SpeedCamera>,
    scale: Float,
) {
    val size = max(CAMERA_SIZE_METERS, CAMERA_MIN_PIXELS / scale)
    for (camera in cameras) {
        drawRect(MapStyle.camera, topLeft = Offset(camera.x - size / 2, camera.y - size / 2), size = Size(size, size))
        drawCircle(MapStyle.cameraFlash, size / CAMERA_LENS_RATIO, Offset(camera.x, camera.y))
    }
}

/** Ein Schild an einem Abschnitt in Weltkoordinaten. */
class SpeedSign(
    val x: Float,
    val y: Float,
    val label: String,
)

private const val SIGN_RADIUS_METERS = 6f
private const val SIGN_MIN_PIXELS = 9f
private const val SIGN_RING = 0.3f
private const val SIGN_FONT_SIZE = 12f
private const val SIGN_TEXT_FACTOR = 0.9f
private const val CAMERA_SIZE_METERS = 8f
private const val CAMERA_MIN_PIXELS = 10f
private const val CAMERA_LENS_RATIO = 4f

/** Zufriedenheit je Wohngebiet als Kreis: grün = zufrieden, rot = unzufrieden. */
internal fun DrawScope.drawMoods(
    moods: List<ZoneMood>,
    scale: Float,
) {
    for (mood in moods) {
        val radius = max(sqrt(mood.area / PI.toFloat()), MOOD_MIN_PIXELS / scale)
        val color = lerp(MapStyle.moodBad, MapStyle.moodGood, mood.satisfaction.toFloat().coerceIn(0f, 1f))
        drawCircle(color.copy(alpha = MOOD_ALPHA), radius, Offset(mood.x, mood.y))
    }
}

private const val MOOD_MIN_PIXELS = 6f
private const val MOOD_ALPHA = 0.55f
