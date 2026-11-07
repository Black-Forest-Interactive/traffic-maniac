package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.bfi.trafficmaniac.sim.SignalMarkers
import de.bfi.trafficmaniac.sim.SignalState
import de.bfi.trafficmaniac.sim.SpeedCamera
import kotlin.math.max

/**
 * Ampeln mit Gesicht: grün lächelt, gelb staunt, rot schaut grimmig, eine ausgefallene Ampel ist benommen.
 * Das Gesicht gibt es erst nah dran; weiter weg bleibt ein farbiger Punkt mit Umriss.
 */
internal fun DrawScope.drawSignals(
    markers: SignalMarkers,
    states: Array<SignalState>,
    scale: Float,
    blink: Boolean,
    viewport: Rect,
) {
    val radius = max(MapStyle.SIGNAL_RADIUS_METERS, MapStyle.MIN_SIGNAL_PIXELS / scale)
    val faces = radius * scale >= FACE_MIN_PIXELS
    val ink = Stroke(max(INK_METERS, 1f / scale))
    val visible = viewport.inflate(radius)
    for (i in states.indices) {
        val c = Offset(markers.x[i], markers.y[i])
        if (!visible.contains(c)) continue
        val state = states[i]
        // Ausgefallene Ampeln blinken gelb
        if (state == SignalState.FLASHING && !blink && !faces) continue
        val color =
            when (state) {
                SignalState.GREEN -> MapStyle.signalGreen
                SignalState.RED -> MapStyle.trafficSignal
                SignalState.FLASHING -> if (blink) MapStyle.signalYellow else MapStyle.signalOff
                else -> MapStyle.signalYellow
            }
        drawCircle(color, radius, c)
        drawCircle(MapStyle.ink, radius, c, style = ink)
        if (faces) drawFace(c, radius, state, ink)
    }
}

/** Augen und Mund. Weltkoordinaten: y zeigt nach oben, Winkel 90° ist "oben" auf dem Bildschirm. */
private fun DrawScope.drawFace(
    c: Offset,
    r: Float,
    state: SignalState,
    ink: Stroke,
) {
    val eye = r * EYE_SIZE
    val eyeY = c.y + r * EYE_HEIGHT
    if (state == SignalState.FLASHING) {
        // Benommen: Kreuzchen statt Augen
        for (side in listOf(-1f, 1f)) {
            val ex = c.x + side * r * EYE_SPACING
            drawLine(MapStyle.ink, Offset(ex - eye, eyeY - eye), Offset(ex + eye, eyeY + eye), ink.width)
            drawLine(MapStyle.ink, Offset(ex - eye, eyeY + eye), Offset(ex + eye, eyeY - eye), ink.width)
        }
    } else {
        drawCircle(MapStyle.ink, eye, Offset(c.x - r * EYE_SPACING, eyeY))
        drawCircle(MapStyle.ink, eye, Offset(c.x + r * EYE_SPACING, eyeY))
    }
    val mouth = r * MOUTH_SIZE
    val mouthStyle = Stroke(ink.width * MOUTH_STROKE, cap = StrokeCap.Round)
    when (state) {
        SignalState.GREEN -> {
            // Lächeln: untere Hälfte eines Kreises
            drawArc(
                MapStyle.ink,
                HALF_TURN,
                HALF_TURN,
                false,
                Offset(c.x - mouth, c.y - mouth * SMILE_DROP),
                Size(2 * mouth, 2 * mouth),
                style = mouthStyle,
            )
        }

        SignalState.RED -> {
            // Grimmig: obere Hälfte, tief im Gesicht
            drawArc(
                MapStyle.ink,
                0f,
                HALF_TURN,
                false,
                Offset(c.x - mouth, c.y - mouth * FROWN_DROP),
                Size(
                    2 * mouth,
                    2 * mouth,
                ),
                style = mouthStyle,
            )
        }

        else -> {
            drawCircle(MapStyle.ink, mouth * O_MOUTH, Offset(c.x, c.y - r * O_MOUTH_DROP), style = mouthStyle)
        }
    }
}

/**
 * Riesige Blitzer: grauer Kasten mit Linse, Glanzpunkt und Blitzbirne oben drauf; Lasermessungen als blauer Kasten auf
 * einem Stativ. Bewusst überzeichnet – man soll sie aus jeder Zoomstufe sehen.
 */
internal fun DrawScope.drawCameras(
    cameras: List<SpeedCamera>,
    scale: Float,
) {
    val size = max(CAMERA_SIZE_METERS, CAMERA_MIN_PIXELS / scale)
    val ink = Stroke(max(INK_METERS, 1.5f / scale))
    for (camera in cameras) {
        val c = Offset(camera.x, camera.y)
        if (camera.mobile) {
            // Stativ
            for (dx in TRIPOD_LEGS) {
                drawLine(MapStyle.ink, c, c + Offset(dx * size, -size * TRIPOD), ink.width)
            }
        }
        val body = if (camera.mobile) MapStyle.laser else MapStyle.camera
        val topLeft = c - Offset(size / 2, size * BODY_HEIGHT / 2)
        val bodySize = Size(size, size * BODY_HEIGHT)
        drawRoundRect(body, topLeft, bodySize, CornerRadius(size * CORNER))
        drawRoundRect(MapStyle.ink, topLeft, bodySize, CornerRadius(size * CORNER), style = ink)
        // Blitzbirne oben (Weltkoordinaten: y nach oben)
        val bulb = Offset(c.x + size * BULB_X, c.y + size * BODY_HEIGHT / 2)
        drawRect(MapStyle.cameraFlash, bulb - Offset(size * BULB_W / 2, 0f), Size(size * BULB_W, size * BULB_H))
        drawRect(MapStyle.ink, bulb - Offset(size * BULB_W / 2, 0f), Size(size * BULB_W, size * BULB_H), style = ink)
        // Linse mit Glanzpunkt
        drawCircle(MapStyle.lens, size * LENS, c)
        drawCircle(MapStyle.ink, size * LENS, c, style = ink)
        drawCircle(Color.White, size * GLINT, c + Offset(size * GLINT, size * GLINT))
    }
}

/** Baustelle: kleiner gelber Bagger mit Schaufel an jedem Abschnitt im Bau. */
internal fun DrawScope.drawExcavators(
    points: List<Pair<Float, Float>>,
    scale: Float,
) {
    val size = max(EXCAVATOR_METERS, EXCAVATOR_MIN_PIXELS / scale)
    val ink = Stroke(max(INK_METERS, 1f / scale))
    for ((x, y) in points) {
        val base = Offset(x - size / 2, y - size / 2)
        // Ketten, Aufbau, Kabine, Arm und Schaufel
        drawRoundRect(MapStyle.ink, base, Size(size, size * TRACK_H), CornerRadius(size * TRACK_H / 2))
        val body = base + Offset(size * BODY_INSET, size * TRACK_H)
        drawRect(MapStyle.excavator, body, Size(size * BODY_W, size * BODY_H))
        drawRect(MapStyle.ink, body, Size(size * BODY_W, size * BODY_H), style = ink)
        val cab = body + Offset(size * CAB_X, size * BODY_H)
        drawRect(MapStyle.excavator, cab, Size(size * CAB_W, size * CAB_H))
        drawRect(MapStyle.ink, cab, Size(size * CAB_W, size * CAB_H), style = ink)
        val shoulder = body + Offset(size * BODY_W, size * BODY_H)
        val elbow = shoulder + Offset(size * ARM_X, size * ARM_Y)
        val bucket = elbow + Offset(size * ARM_X / 2, -size * ARM_Y * BUCKET_REACH)
        drawLine(MapStyle.excavator, shoulder, elbow, size * ARM_W, StrokeCap.Round)
        drawLine(MapStyle.excavator, elbow, bucket, size * ARM_W, StrokeCap.Round)
        drawCircle(MapStyle.ink, size * BUCKET, bucket)
    }
}

private const val FACE_MIN_PIXELS = 7f
private const val INK_METERS = 0.4f
private const val EYE_SIZE = 0.13f
private const val EYE_HEIGHT = 0.25f
private const val EYE_SPACING = 0.35f
private const val MOUTH_SIZE = 0.4f
private const val MOUTH_STROKE = 1.2f
private const val SMILE_DROP = 1.3f
private const val FROWN_DROP = 2.1f
private const val O_MOUTH = 0.35f
private const val O_MOUTH_DROP = 0.35f
private const val HALF_TURN = 180f
private const val CAMERA_SIZE_METERS = 10f
private const val CAMERA_MIN_PIXELS = 18f
private const val BODY_HEIGHT = 0.75f
private const val CORNER = 0.15f
private const val TRIPOD = 0.8f
private val TRIPOD_LEGS = listOf(-0.5f, 0f, 0.5f)
private const val BULB_X = 0.25f
private const val BULB_W = 0.25f
private const val BULB_H = 0.18f
private const val LENS = 0.24f
private const val GLINT = 0.08f
private const val EXCAVATOR_METERS = 9f
private const val EXCAVATOR_MIN_PIXELS = 16f
private const val TRACK_H = 0.22f
private const val BODY_INSET = 0.05f
private const val BODY_W = 0.6f
private const val BODY_H = 0.3f
private const val CAB_X = 0.05f
private const val CAB_W = 0.3f
private const val CAB_H = 0.3f
private const val ARM_X = 0.35f
private const val ARM_Y = 0.35f
private const val ARM_W = 0.1f
private const val BUCKET = 0.12f
private const val BUCKET_REACH = 1.4f
