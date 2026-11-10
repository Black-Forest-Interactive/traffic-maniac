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
 * Ampeln im deutschen Stil: schwarzes Gehäuse, oben Rot, in der Mitte Gelb, unten Grün; nur die aktive Leuchte strahlt.
 * Ausgefallene Ampeln blinken gelb. Weiter weg bleibt ein farbiger Punkt mit Umriss.
 */
internal fun DrawScope.drawSignals(
    markers: SignalMarkers,
    states: Array<SignalState>,
    scale: Float,
    blink: Boolean,
    viewport: Rect,
    sprites: SpriteSource = NoSprites,
) {
    val radius = max(MapStyle.SIGNAL_RADIUS_METERS, MapStyle.MIN_SIGNAL_PIXELS / scale)
    val heads = radius * scale >= HEAD_MIN_PIXELS
    val ink = Stroke(max(INK_METERS, 1f / scale))
    val visible = viewport.inflate(radius * HEAD_HEIGHT)
    for (i in states.indices) {
        val c = Offset(markers.x[i], markers.y[i])
        if (!visible.contains(c)) continue
        if (heads) {
            val at = curbside(markers, i, radius)
            val name = signalSpriteName(states[i], blink)
            if (!drawSprite(sprites, name, at, radius * HEAD_WIDTH, radius * HEAD_HEIGHT, scale)) {
                drawSignalHead(at, radius, states[i], blink, ink)
            }
        } else {
            drawSignalDot(c, radius, states[i], blink, ink)
        }
    }
}

/**
 * Standort des Signalgebers: in Fahrtrichtung rechts neben der gezeichneten Fahrbahn (deren Breite hängt von der
 * Straßenklasse ab), ein Stück vor der Kreuzung – wie an einer echten Haltelinie.
 */
private fun curbside(
    markers: SignalMarkers,
    i: Int,
    radius: Float,
): Offset {
    val dx = markers.dirX[i]
    val dy = markers.dirY[i]
    val halfRoad = MapStyle.road(markers.roadClasses[i]).widthMeters / 2 + MapStyle.OUTLINE_METERS
    val side = halfRoad + CURB_GAP_METERS + radius * HEAD_WIDTH / 2
    val back = radius * HEAD_HEIGHT / 2 + SETBACK_METERS
    return Offset(markers.centerX[i] + dy * side - dx * back, markers.centerY[i] - dx * side - dy * back)
}

private fun signalSpriteName(
    state: SignalState,
    blink: Boolean,
): String =
    when (state) {
        SignalState.RED -> "signal_red"
        SignalState.RED_YELLOW -> "signal_red_yellow"
        SignalState.YELLOW -> "signal_yellow"
        SignalState.GREEN -> "signal_green"
        else -> if (blink) "signal_yellow" else "signal_off"
    }

/** Aus der Ferne: farbiger Punkt; ausgefallene Ampeln blinken gelb. */
private fun DrawScope.drawSignalDot(
    c: Offset,
    radius: Float,
    state: SignalState,
    blink: Boolean,
    ink: Stroke,
) {
    if (state == SignalState.FLASHING && !blink) return
    val color =
        when (state) {
            SignalState.GREEN -> MapStyle.signalGreen
            SignalState.RED, SignalState.RED_YELLOW -> MapStyle.trafficSignal
            else -> MapStyle.signalYellow
        }
    drawCircle(color, radius, c)
    drawCircle(MapStyle.ink, radius, c, style = ink)
}

/** Signalgeber: drei Leuchten übereinander (Weltkoordinaten: y nach oben, Rot also oben). */
private fun DrawScope.drawSignalHead(
    c: Offset,
    r: Float,
    state: SignalState,
    blink: Boolean,
    ink: Stroke,
) {
    val width = r * HEAD_WIDTH
    val height = r * HEAD_HEIGHT
    val topLeft = Offset(c.x - width / 2, c.y - height / 2)
    drawRoundRect(MapStyle.signalHousing, topLeft, Size(width, height), CornerRadius(width * HEAD_CORNER))
    drawRoundRect(Color.White, topLeft, Size(width, height), CornerRadius(width * HEAD_CORNER), style = ink)
    val lamp = r * LAMP_RADIUS
    val spacing = r * LAMP_SPACING
    // Leuchten von oben: Rot, Gelb, Grün
    val lit =
        when (state) {
            SignalState.RED -> setOf(0)
            SignalState.RED_YELLOW -> setOf(0, 1)
            SignalState.YELLOW -> setOf(1)
            SignalState.GREEN -> setOf(2)
            SignalState.FLASHING -> if (blink) setOf(1) else emptySet()
            else -> emptySet()
        }
    for (k in 0..2) {
        val on = k in lit
        val center = Offset(c.x, c.y + spacing * (1 - k))
        if (on) drawCircle(LAMP_ON[k].copy(alpha = GLOW_ALPHA), lamp * GLOW, center)
        drawCircle(if (on) LAMP_ON[k] else LAMP_OFF[k], lamp, center)
    }
}

/**
 * Riesige Blitzer: grauer Kasten mit Linse, Glanzpunkt und Blitzbirne oben drauf; Lasermessungen als blauer Kasten auf
 * einem Stativ. Bewusst überzeichnet – man soll sie aus jeder Zoomstufe sehen.
 */
internal fun DrawScope.drawCameras(
    cameras: List<SpeedCamera>,
    scale: Float,
    sprites: SpriteSource = NoSprites,
) {
    val size = max(CAMERA_SIZE_METERS, CAMERA_MIN_PIXELS / scale)
    val ink = Stroke(max(INK_METERS, 1.5f / scale))
    // Anzeigetafeln zeichnet drawDisplays (mit Zahl)
    for (camera in cameras.filter { !it.display }) {
        val c = Offset(camera.x, camera.y)
        if (camera.trailer) {
            drawTrailer(c, size, scale, ink, sprites)
            continue
        }
        if (drawSprite(sprites, cameraSprite(camera), c, size * SPRITE_CAMERA_WIDTH, size, scale)) continue
        drawStand(camera, c, size, ink)
        val body = cameraBody(camera)
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

/** Laser auf dem Stativ, Polizeikontrolle mit zwei Leitkegeln davor und dahinter; feste Blitzer stehen frei. */
private fun DrawScope.drawStand(
    camera: SpeedCamera,
    c: Offset,
    size: Float,
    ink: Stroke,
) {
    when {
        camera.policeCheck -> {
            for (dx in CONES) drawCircle(MapStyle.works, size * CONE, c + Offset(dx * size, -size * TRIPOD))
        }

        camera.mobile -> {
            for (dx in TRIPOD_LEGS) drawLine(MapStyle.ink, c, c + Offset(dx * size, -size * TRIPOD), ink.width)
        }
    }
}

private val CONES = listOf(-1.1f, 1.1f)
private const val CONE = 0.22f

private fun cameraSprite(camera: SpeedCamera): String =
    when {
        camera.policeCheck -> "police_check"
        camera.redLight -> "camera_red_light"
        camera.mobile -> "camera_laser"
        else -> "camera_fixed"
    }

/** Polizeikontrolle blau, Rotlichtblitzer rot, Laser hellblau, feste grau. */
private fun cameraBody(camera: SpeedCamera): Color =
    when {
        camera.policeCheck -> MapStyle.policeCheck
        camera.redLight -> MapStyle.redLightCamera
        camera.mobile -> MapStyle.laser
        else -> MapStyle.camera
    }

/** Blitzeranhänger: Grafik `camera_trailer`, sonst Anhänger mit Rädern und Deichsel, der Blitzer obendrauf. */
private fun DrawScope.drawTrailer(
    c: Offset,
    size: Float,
    scale: Float,
    ink: Stroke,
    sprites: SpriteSource,
) {
    if (drawSprite(sprites, "camera_trailer", c, size * TRAILER_WIDTH, size, scale)) return
    val body = Size(size * TRAILER_WIDTH * TRAILER_BODY, size * TRAILER_BODY_HEIGHT)
    val topLeft = Offset(c.x - body.width / 2, c.y - size * TRAILER_LOW)
    // Deichsel nach links, zwei Räder unten
    drawLine(
        MapStyle.ink,
        Offset(topLeft.x, c.y - size * TRAILER_HITCH),
        Offset(
            topLeft.x - size * TRAILER_HITCH_LENGTH,
            c.y - size * TRAILER_HITCH,
        ),
        ink.width * 2,
    )
    for (dx in listOf(TRAILER_WHEEL_X, 1 - TRAILER_WHEEL_X)) {
        drawCircle(MapStyle.ink, size * TRAILER_WHEEL, Offset(topLeft.x + body.width * dx, topLeft.y))
    }
    drawRoundRect(MapStyle.trailer, topLeft, body, CornerRadius(size * CORNER))
    drawRoundRect(MapStyle.ink, topLeft, body, CornerRadius(size * CORNER), style = ink)
    val head = c + Offset(0f, size * TRAILER_HEAD_LIFT)
    if (!drawSprite(sprites, "camera_fixed", head, size * TRAILER_HEAD, size * TRAILER_HEAD * BODY_HEIGHT, scale)) {
        drawCircle(MapStyle.lens, size * LENS, head)
        drawCircle(MapStyle.ink, size * LENS, head, style = ink)
    }
}

/** Baustelle: kleiner gelber Bagger mit Schaufel an jedem Abschnitt im Bau. */
internal fun DrawScope.drawExcavators(
    points: List<Pair<Float, Float>>,
    scale: Float,
    sprites: SpriteSource = NoSprites,
) {
    val size = max(EXCAVATOR_METERS, EXCAVATOR_MIN_PIXELS / scale)
    val ink = Stroke(max(INK_METERS, 1f / scale))
    for ((x, y) in points) {
        if (drawSprite(sprites, "excavator", Offset(x, y), size, size, scale)) continue
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

private const val HEAD_MIN_PIXELS = 7f
private const val SPRITE_CAMERA_WIDTH = 1.25f
private const val TRAILER_WIDTH = 1.5f
private const val TRAILER_BODY = 0.8f
private const val TRAILER_BODY_HEIGHT = 0.45f
private const val TRAILER_LOW = 0.45f
private const val TRAILER_HITCH = 0.25f
private const val TRAILER_HITCH_LENGTH = 0.35f
private const val TRAILER_WHEEL = 0.13f
private const val TRAILER_WHEEL_X = 0.25f
private const val TRAILER_HEAD = 0.85f
private const val TRAILER_HEAD_LIFT = 0.25f
private const val CURB_GAP_METERS = 1.5f
private const val SETBACK_METERS = 2f
private const val INK_METERS = 0.4f
private const val HEAD_WIDTH = 1.25f
private const val HEAD_HEIGHT = 3.3f
private const val HEAD_CORNER = 0.3f
private const val LAMP_RADIUS = 0.42f
private const val LAMP_SPACING = 1.0f
private const val GLOW = 1.5f
private const val GLOW_ALPHA = 0.35f
private val LAMP_ON = listOf(Color(0xFFFF2D2D), Color(0xFFFFC300), Color(0xFF2DE36B))
private val LAMP_OFF = listOf(Color(0xFF4A1A1A), Color(0xFF4A3A12), Color(0xFF173D25))
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
