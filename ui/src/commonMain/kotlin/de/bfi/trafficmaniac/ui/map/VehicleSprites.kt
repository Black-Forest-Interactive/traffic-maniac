package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.FLAG_BRAKING
import de.bfi.trafficmaniac.sim.FLAG_DISABLED
import de.bfi.trafficmaniac.sim.FLAG_ELECTRIC
import de.bfi.trafficmaniac.sim.FLAG_EMERGENCY
import de.bfi.trafficmaniac.sim.FLAG_JAMMED
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.VehicleRole
import de.bfi.trafficmaniac.sim.VehicleType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Fahrzeuge in drei Detailstufen:
 * - **Gesamtansicht**: keine Fahrzeuge, stattdessen Staufarben auf den Straßen ([drawFlow]).
 * - **Mittlerer Zoom**: farbige Striche von Heck zu Front, mit Blaulicht und Warnblinker.
 * - **Nah dran**: knubbelige Cartoon-Sprites je Klasse und Rolle mit Tusche-Umriss, Bremslichtern, Dampf im Stau,
 *   Ladekringel bei der Softwarepanne, leerem Akku und Sternchen nach dem Unfall.
 *
 * Positionen werden zwischen den beiden Snapshots interpoliert; was außerhalb von [viewport] liegt, wird übersprungen.
 * Animationen laufen nach Wanduhrzeit ([seconds]), damit sie im Zeitraffer nicht hektisch werden.
 */
internal fun DrawScope.drawVehicles(
    frame: TrafficFrame,
    scale: Float,
    viewport: Rect,
    seconds: Double,
    sprites: SpriteSource = NoSprites,
) {
    if (scale < MapStyle.FLOW_MAX_SCALE) return
    val previous = frame.previous
    val current = frame.current
    val t = frame.interpolation()
    val detailed = scale >= SPRITE_MIN_SCALE
    val sprite = SpriteBuilder(scale, seconds, sprites)
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
        if (!viewport.inflate(VIEW_MARGIN_METERS).contains(Offset(x, y))) continue
        val vehicle =
            VehicleView(
                id = id,
                x = x,
                y = y,
                dirX = current.dirX[i],
                dirY = current.dirY[i],
                // Cartoon: Sprites sind deutlich breiter und etwas länger als in echt
                width = current.widths[i] * if (detailed) SPRITE_WIDTH_FACTOR else 1f,
                length = current.lengths[i] * if (detailed) SPRITE_LENGTH_FACTOR else 1f,
                color = Color(current.colors[i]),
                flags = current.flags[i].toInt(),
                type = VehicleType.entries.getOrElse(current.types[i].toInt()) { VehicleType.CAR },
                role = VehicleRole.entries.getOrElse(current.roles[i].toInt()) { VehicleRole.PRIVATE },
                trouble = IncidentKind.entries.getOrNull(current.troubles[i] - 1),
            )
        if (detailed) sprite.draw(this, vehicle) else drawStroke(vehicle, scale, blinkOn(seconds))
    }
}

/**
 * Gesamtansicht: Straßen mit zähem Verkehr gelb, mit Stau rot – freie Straßen bleiben, wie sie sind.
 * Je Snapshot werden die Abschnitte einmal in wenige Farbstufen gebündelt; gezeichnet wird dann ein Pfad je Stufe.
 */
internal class FlowLayer(
    private val paths: SegmentPaths,
) {
    private var source: FloatArray? = null
    private val bins = Array(FLOW_BINS) { Path() }
    private val filled = BooleanArray(FLOW_BINS)

    fun draw(
        scope: DrawScope,
        flow: FloatArray,
        scale: Float,
    ) {
        if (scale >= MapStyle.FLOW_MAX_SCALE || flow.isEmpty()) return
        if (flow !== source) rebuild(flow)
        // Nur in der Gesamtansicht: bei wenigen Pixeln Breite reichen einfache Linienenden (deutlich billiger)
        val stroke = Stroke(max(FLOW_WIDTH_METERS, FLOW_MIN_PIXELS / scale), join = StrokeJoin.Bevel)
        for (bin in bins.indices) {
            if (filled[bin]) {
                scope.drawPath(
                    bins[bin],
                    flowColor((bin + HALF_BIN) / FLOW_BINS * FLOW_SHOWN_BELOW),
                    style = stroke,
                )
            }
        }
    }

    private fun rebuild(flow: FloatArray) {
        source = flow
        bins.forEach { it.reset() }
        filled.fill(false)
        for (segment in paths.mainSegments) {
            val f = flow.getOrElse(segment) { 1f }
            if (f >= FLOW_SHOWN_BELOW) continue
            val bin = (f / FLOW_SHOWN_BELOW * FLOW_BINS).toInt().coerceIn(0, FLOW_BINS - 1)
            bins[bin].addPath(paths[segment])
            filled[bin] = true
        }
    }

    private fun flowColor(f: Float) =
        if (f > FLOW_SLOW) {
            lerp(MapStyle.flowSlow, MapStyle.flowFree, (f - FLOW_SLOW) / (FLOW_SHOWN_BELOW - FLOW_SLOW))
        } else {
            lerp(MapStyle.flowJam, MapStyle.flowSlow, f / FLOW_SLOW)
        }
}

/** Ein einzelnes Sprite, nach Osten fahrend, mit der Mitte bei (0, 0) – für die Legende. */
internal fun DrawScope.drawVehicleSprite(
    type: VehicleType,
    role: VehicleRole,
    color: Color,
    flags: Int,
    trouble: IncidentKind?,
    scale: Float,
    seconds: Double,
) {
    val length = type.length * SPRITE_LENGTH_FACTOR
    val vehicle =
        VehicleView(
            id = 0,
            x = length / 2,
            y = 0f,
            dirX = 1f,
            dirY = 0f,
            length = length,
            width = type.width * SPRITE_WIDTH_FACTOR,
            color = color,
            flags = flags,
            type = type,
            role = role,
            trouble = trouble,
        )
    SpriteBuilder(scale, seconds).draw(this, vehicle)
}

/** Ein Fahrzeug, fertig interpoliert. [x], [y] ist die Front, [dirX], [dirY] die Fahrtrichtung. */
private class VehicleView(
    val id: Int,
    val x: Float,
    val y: Float,
    val dirX: Float,
    val dirY: Float,
    val length: Float,
    val width: Float,
    val color: Color,
    val flags: Int,
    val type: VehicleType,
    val role: VehicleRole,
    val trouble: IncidentKind?,
) {
    fun has(flag: Int) = flags and flag != 0

    /** Punkt auf dem Fahrzeug: [along] 0 = Heck … 1 = Front, [side] −1 = links … +1 = rechts. */
    fun point(
        along: Float,
        side: Float,
    ): Offset {
        val back = (1 - along) * length
        val half = width / 2 * side
        return Offset(x - dirX * back - dirY * half, y - dirY * back + dirX * half)
    }
}

private fun blinkOn(seconds: Double) = (seconds * BLINKS_PER_SECOND).toInt() % 2 == 0

/** Mittlerer Zoom: Strich von Heck zu Front, Blaulicht bzw. Warnblinker als Punkt an der Front. */
private fun DrawScope.drawStroke(
    v: VehicleView,
    scale: Float,
    blink: Boolean,
) {
    val width = max(v.width, MIN_VEHICLE_PIXELS / scale)
    drawLine(v.color, v.point(0f, 0f), Offset(v.x, v.y), strokeWidth = width, cap = StrokeCap.Butt)
    val light =
        when {
            v.has(FLAG_EMERGENCY) -> if (blink) MapStyle.blueLight else MapStyle.redLight
            v.has(FLAG_DISABLED) && blink -> MapStyle.hazardLight
            else -> return
        }
    drawCircle(light, width * LIGHT_SIZE, Offset(v.x, v.y))
}

/** Zeichnet Sprites; ein [Path] wird für alle Fahrzeuge wiederverwendet. */
private class SpriteBuilder(
    private val scale: Float,
    private val seconds: Double,
    private val sprites: SpriteSource = NoSprites,
) {
    private val path = Path()
    private val outline = Stroke(max(OUTLINE_METERS, MIN_OUTLINE_PIXELS / scale), join = StrokeJoin.Round)
    private val blink = blinkOn(seconds)

    fun draw(
        scope: DrawScope,
        v: VehicleView,
    ) = with(scope) {
        // Fertige Grafik, wenn es sie gibt – Lichter und Zustände kommen in jedem Fall darüber
        val name = vehicleSpriteName(v.type, v.role, v.color, v.has(FLAG_ELECTRIC))
        val angle = atan2(v.dirY, v.dirX)
        if (drawSprite(sprites, name, v.point(CENTER, 0f), v.length, v.width, scale, angle)) {
            drawLights(v)
            drawStatus(v)
            return@with
        }
        if (v.type == VehicleType.BIKE_CORSO) return@with drawCorso(v)
        drawBody(v)
        drawDetails(v)
        drawLights(v)
        drawStatus(v)
    }

    /** Karosserie mit abgerundeten Ecken (abgeschrägt), Farbe nach Rolle. */
    private fun DrawScope.drawBody(v: VehicleView) {
        // Einsatzfahrzeuge im deutschen Stil: RTW weiß, Polizei silber, Feuerwehr feuerrot
        val body =
            when (v.role) {
                VehicleRole.AMBULANCE -> Color.White
                VehicleRole.POLICE -> POLICE_SILVER
                VehicleRole.FIRE_BRIGADE -> FIRE_RED
                VehicleRole.TOW_TRUCK, VehicleRole.STREET_SERVICE -> SERVICE_ORANGE
                else -> v.color
            }
        when (v.type) {
            VehicleType.TRUCK -> {
                quad(v, 0f, CARGO_END, -1f, 1f, if (v.role == VehicleRole.FIRE_BRIGADE) FIRE_RED else CARGO)
                quad(v, CAB_START, 1f, -CAB_WIDTH, CAB_WIDTH, body)
            }

            VehicleType.HEAVY_TRANSPORT -> {
                quad(v, 0f, CARGO_END, -1f, 1f, FLATBED)
                quad(v, LOAD_START, LOAD_END, -LOAD_WIDTH, LOAD_WIDTH, LOAD)
                quad(v, HEAVY_CAB_START, 1f, -CAB_WIDTH, CAB_WIDTH, body)
            }

            else -> {
                rounded(v, 0f, 1f, -1f, 1f, body)
            }
        }
    }

    private fun DrawScope.drawDetails(v: VehicleView) {
        val front = if (v.type == VehicleType.CAR) CAR_WINDSHIELD else VAN_WINDSHIELD
        quad(v, front, front + WINDSHIELD_DEPTH, -WINDOW_WIDTH, WINDOW_WIDTH, GLASS, outlined = false)
        if (v.type == VehicleType.CAR) {
            quad(
                v,
                REAR_WINDOW,
                REAR_WINDOW + WINDSHIELD_DEPTH / 2,
                -WINDOW_WIDTH,
                WINDOW_WIDTH,
                GLASS,
                outlined = false,
            )
        }

        // Farbige Streifen längs an beiden Fahrzeugseiten
        fun sideBands(
            v: VehicleView,
            color: Color,
        ) {
            quad(v, BAND_START, BAND_END, -1f, -BAND_INNER, color, outlined = false)
            quad(v, BAND_START, BAND_END, BAND_INNER, 1f, color, outlined = false)
        }
        when (v.role) {
            // Leuchtrote Streifen längs an beiden Seiten
            VehicleRole.AMBULANCE -> {
                sideBands(v, RESCUE_RED)
            }

            // Blaues Band längs, wie beim blau-silbernen Streifenwagen
            VehicleRole.POLICE -> {
                sideBands(v, POLICE_BLUE)
            }

            VehicleRole.FIRE_BRIGADE -> {
                sideBands(v, Color.White)
                // Steckleiter auf dem Dach
                quad(v, LADDER_START, CARGO_END, -LADDER_WIDTH, LADDER_WIDTH, LADDER_SILVER, outlined = false)
            }

            VehicleRole.TOW_TRUCK -> {
                drawLine(MapStyle.ink, v.point(0f, 0f), v.point(-HOOK, 0f), v.width * HOOK_WIDTH)
            }

            else -> {
                Unit
            }
        }
        if (v.has(FLAG_ELECTRIC)) drawCircle(ELECTRIC_GREEN, v.width * ROOF_DOT, v.point(ROOF, 0f))
    }

    private fun DrawScope.drawLights(v: VehicleView) {
        val size = v.width * LAMP
        if (v.has(FLAG_BRAKING)) {
            drawCircle(BRAKE_RED, size, v.point(LAMP_INSET, -LAMP_SIDE))
            drawCircle(BRAKE_RED, size, v.point(LAMP_INSET, LAMP_SIDE))
        }
        if (v.has(FLAG_EMERGENCY)) {
            val (left, right) =
                if (blink) {
                    MapStyle.blueLight to MapStyle.redLight
                } else {
                    MapStyle.redLight to
                        MapStyle.blueLight
                }
            val roof = if (v.type == VehicleType.HEAVY_TRANSPORT) HEAVY_ROOF else ROOF
            drawCircle(left, size * BEACON, v.point(roof, -LAMP_SIDE / 2))
            drawCircle(right, size * BEACON, v.point(roof, LAMP_SIDE / 2))
        }
        if (v.has(FLAG_DISABLED) && blink) {
            for (along in listOf(LAMP_INSET, 1 - LAMP_INSET)) {
                drawCircle(MapStyle.hazardLight, size * HAZARD, v.point(along, -LAMP_SIDE))
                drawCircle(MapStyle.hazardLight, size * HAZARD, v.point(along, LAMP_SIDE))
            }
        }
    }

    /** Dampf im Dauerstau, Ladekringel (Softwarepanne), Batterie (Akku leer), Sternchen (Unfall). */
    private fun DrawScope.drawStatus(v: VehicleView) {
        val center = v.point(CENTER, 0f)
        val icon = max(v.width, ICON_MIN_METERS)
        when (v.trouble) {
            IncidentKind.SOFTWARE_FAILURE -> spinner(center + Offset(0f, icon * ICON_LIFT), icon)
            IncidentKind.EMPTY_BATTERY -> battery(center + Offset(0f, icon * ICON_LIFT), icon)
            IncidentKind.ACCIDENT -> stars(center, icon)
            else -> if (v.has(FLAG_JAMMED)) steam(v, icon)
        }
    }

    private fun DrawScope.steam(
        v: VehicleView,
        size: Float,
    ) {
        // Vor der Motorhaube aufsteigend, damit das Auto selbst sichtbar bleibt
        val hood = v.point(1f + STEAM_AHEAD, 0f)
        for (k in 0 until PUFFS) {
            val phase = ((seconds * STEAM_SPEED + v.id * GOLDEN + k.toDouble() / PUFFS) % 1.0).toFloat()
            val c = hood + Offset(sin(phase * WIGGLE) * size * DRIFT, phase * size * RISE)
            drawCircle(STEAM.copy(alpha = STEAM.alpha * (1 - phase)), size * (PUFF_MIN + phase * PUFF_GROWTH), c)
        }
    }

    private fun DrawScope.spinner(
        c: Offset,
        size: Float,
    ) {
        drawCircle(Color.White, size * SPINNER_BACK, c)
        val start = ((seconds * SPIN_DEGREES_PER_SECOND) % FULL_TURN).toFloat()
        drawArc(
            SPINNER_BLUE,
            start,
            SPINNER_SWEEP,
            useCenter = false,
            topLeft = c - Offset(size / 2, size / 2),
            size = Size(size, size),
            style = Stroke(size * SPINNER_STROKE, cap = StrokeCap.Round),
        )
    }

    private fun DrawScope.battery(
        c: Offset,
        size: Float,
    ) {
        val w = size * BATTERY_W
        val h = size * BATTERY_H
        drawRect(Color.White, c - Offset(w / 2, h / 2), Size(w, h))
        drawRect(SIREN_RED, c - Offset(w / 2, h / 2), Size(w * BATTERY_LEVEL, h))
        drawRect(MapStyle.ink, c - Offset(w / 2, h / 2), Size(w, h), style = outline)
        drawRect(MapStyle.ink, c + Offset(w / 2, -h / BATTERY_TIP), Size(w * BATTERY_TIP_W, h / 2))
    }

    /** Comic-Sternchen, die sich um die Unfallstelle drehen. */
    private fun DrawScope.stars(
        c: Offset,
        size: Float,
    ) {
        val turn = seconds * STAR_TURNS_PER_SECOND
        for (k in 0 until STARS) {
            val angle = turn + k * 2 * PI / STARS
            val p = c + Offset((cos(angle) * size).toFloat(), (sin(angle) * size * STAR_ELLIPSE).toFloat())
            star(p, size * STAR_SIZE)
        }
    }

    private fun DrawScope.star(
        c: Offset,
        r: Float,
    ) {
        path.reset()
        for (k in 0 until STAR_POINTS * 2) {
            val radius = if (k % 2 == 0) r else r * STAR_INNER
            val angle = PI / 2 + k * PI / STAR_POINTS
            val px = c.x + (cos(angle) * radius).toFloat()
            val py = c.y + (sin(angle) * radius).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, STAR_YELLOW)
        drawPath(path, MapStyle.ink, style = outline)
    }

    /** Rad-Korso: ein Pulk Radler in grünen Trikots, zwei nebeneinander. */
    private fun DrawScope.drawCorso(v: VehicleView) {
        for (k in 0 until RIDER_ROWS * 2) {
            val along = (k / 2 + RIDER_OFFSET) / RIDER_ROWS.toFloat()
            val side = if (k % 2 == 0) -RIDER_SIDE else RIDER_SIDE
            // Leichtes Schlingern
            val wobble = sin(seconds * WOBBLE_SPEED + v.id + k).toFloat() * RIDER_WOBBLE
            val p = v.point(along, side + wobble)
            drawCircle(MapStyle.corso, v.width * RIDER_SIZE, p)
            drawCircle(MapStyle.ink, v.width * RIDER_SIZE, p, style = outline)
            drawCircle(Color.White, v.width * HELMET, p)
        }
    }

    private fun DrawScope.quad(
        v: VehicleView,
        from: Float,
        to: Float,
        left: Float,
        right: Float,
        color: Color,
        outlined: Boolean = true,
    ) {
        path.reset()
        path.moveTo(v.point(from, left))
        path.lineTo(v.point(to, left))
        path.lineTo(v.point(to, right))
        path.lineTo(v.point(from, right))
        path.close()
        drawPath(path, color)
        if (outlined) drawPath(path, MapStyle.ink, style = outline)
    }

    /** Achteck mit abgeschrägten Ecken: wirkt aus der Entfernung wie ein knubbeliges Auto. */
    private fun DrawScope.rounded(
        v: VehicleView,
        from: Float,
        to: Float,
        left: Float,
        right: Float,
        color: Color,
    ) {
        val cutAlong = (v.width * CORNER / v.length).coerceAtMost(MAX_CUT)
        val cutSide = CORNER * 2
        path.reset()
        path.moveTo(v.point(from + cutAlong, left))
        path.lineTo(v.point(to - cutAlong, left))
        path.lineTo(v.point(to, left + cutSide))
        path.lineTo(v.point(to, right - cutSide))
        path.lineTo(v.point(to - cutAlong, right))
        path.lineTo(v.point(from + cutAlong, right))
        path.lineTo(v.point(from, right - cutSide))
        path.lineTo(v.point(from, left + cutSide))
        path.close()
        drawPath(path, color)
        drawPath(path, MapStyle.ink, style = outline)
    }

    private fun Path.moveTo(p: Offset) = moveTo(p.x, p.y)

    private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)
}

private const val SPRITE_MIN_SCALE = 1.2f
private const val SPRITE_WIDTH_FACTOR = 1.7f
private const val SPRITE_LENGTH_FACTOR = 1.1f
private const val VIEW_MARGIN_METERS = 30f
private const val MIN_VEHICLE_PIXELS = 2.5f
private const val LIGHT_SIZE = 0.7f
private const val BLINKS_PER_SECOND = 2.0
private const val FLOW_WIDTH_METERS = 14f
private const val FLOW_MIN_PIXELS = 3f
private const val FLOW_SHOWN_BELOW = 0.85f
private const val FLOW_SLOW = 0.5f
private const val FLOW_BINS = 6
private const val HALF_BIN = 0.5f
private const val OUTLINE_METERS = 0.3f
private const val MIN_OUTLINE_PIXELS = 1f

private val GLASS = Color(0xFF9FD8FF)
private val CARGO = Color(0xFFDEE2E6)
private val FLATBED = Color(0xFF495057)
private val LOAD = Color(0xFFF4A261)
private val SERVICE_ORANGE = Color(0xFFFF8C00)
private val POLICE_SILVER = Color(0xFFDCE1E6)
private val POLICE_BLUE = Color(0xFF1D4F91)
private val RESCUE_RED = Color(0xFFFF4F1F)
private val FIRE_RED = Color(0xFFC1121F)
private val LADDER_SILVER = Color(0xFFC9CED6)
private val SIREN_RED = Color(0xFFE63946)
private val BRAKE_RED = Color(0xFFFF1744)
private val ELECTRIC_GREEN = Color(0xFF06D6A0)
private val STEAM = Color(0xCCFFFFFF)
private val SPINNER_BLUE = Color(0xFF3A86FF)
private val STAR_YELLOW = Color(0xFFFFD60A)

private const val CARGO_END = 0.72f
private const val CAB_START = 0.75f
private const val CAB_WIDTH = 0.92f
private const val LOAD_START = 0.12f
private const val LOAD_END = 0.7f
private const val LOAD_WIDTH = 0.75f
private const val HEAVY_CAB_START = 0.87f
private const val CAR_WINDSHIELD = 0.6f
private const val VAN_WINDSHIELD = 0.8f
private const val WINDSHIELD_DEPTH = 0.15f
private const val REAR_WINDOW = 0.1f
private const val WINDOW_WIDTH = 0.72f
private const val BAND_START = 0.06f
private const val BAND_END = 0.94f
private const val BAND_INNER = 0.62f
private const val LADDER_START = 0.08f
private const val LADDER_WIDTH = 0.25f
private const val HOOK = 0.08f
private const val HOOK_WIDTH = 0.25f
private const val ROOF = 0.45f
private const val HEAVY_ROOF = 0.93f
private const val ROOF_DOT = 0.18f
private const val LAMP = 0.16f
private const val LAMP_INSET = 0.04f
private const val LAMP_SIDE = 0.7f
private const val BEACON = 1.3f
private const val HAZARD = 1.2f
private const val CENTER = 0.5f
private const val ICON_MIN_METERS = 2f
private const val ICON_LIFT = 1.4f
private const val PUFFS = 3
private const val STEAM_SPEED = 0.6
private const val GOLDEN = 0.618
private const val WIGGLE = 9f
private const val DRIFT = 0.25f
private const val RISE = 2.2f
private const val PUFF_MIN = 0.15f
private const val PUFF_GROWTH = 0.25f
private const val STEAM_AHEAD = 0.15f
private const val SPINNER_BACK = 0.75f
private const val SPIN_DEGREES_PER_SECOND = 360.0
private const val FULL_TURN = 360.0
private const val SPINNER_SWEEP = 270f
private const val SPINNER_STROKE = 0.2f
private const val BATTERY_W = 1.2f
private const val BATTERY_H = 0.6f
private const val BATTERY_LEVEL = 0.15f
private const val BATTERY_TIP = 4f
private const val BATTERY_TIP_W = 0.1f
private const val STARS = 3
private const val STAR_TURNS_PER_SECOND = 2.5
private const val STAR_ELLIPSE = 0.5
private const val STAR_SIZE = 0.35f
private const val STAR_POINTS = 5
private const val STAR_INNER = 0.45f
private const val RIDER_ROWS = 3
private const val RIDER_OFFSET = 0.5f
private const val RIDER_SIDE = 0.45f
private const val RIDER_WOBBLE = 0.08f
private const val WOBBLE_SPEED = 3.0
private const val RIDER_SIZE = 0.22f
private const val HELMET = 0.1f
private const val CORNER = 0.18f
private const val MAX_CUT = 0.12f
