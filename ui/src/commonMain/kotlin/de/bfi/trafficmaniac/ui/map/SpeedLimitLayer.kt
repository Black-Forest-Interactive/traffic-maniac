package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.bfi.trafficmaniac.city.TransitStop
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Tempozonen: Jeder Abschnitt mit eigenem Tempolimit bekommt ein durchscheinendes Band (rot, Nachtlimit blau),
 * damit man sieht, für welche Strecke das Limit gilt – nicht nur, wo das Schild steht.
 */
internal fun DrawScope.drawLimitZones(
    paths: SegmentPaths,
    dayLimits: Collection<Int>,
    nightLimits: Collection<Int>,
    scale: Float,
) {
    if (scale < MapStyle.FLOW_MAX_SCALE) return
    val width = max(ZONE_WIDTH_METERS, ZONE_MIN_PIXELS / scale)
    val day = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (segment in dayLimits) drawPath(paths[segment], MapStyle.limitZone, style = day)
    // Nachtlimit als gestrichelte blaue Linie in der Mitte – bleibt neben einer roten Zone unterscheidbar
    val dash = PathEffect.dashPathEffect(floatArrayOf(width, width * NIGHT_DASH_GAP))
    val night = Stroke(width * NIGHT_WIDTH, cap = StrokeCap.Butt, pathEffect = dash)
    for (segment in nightLimits) drawPath(paths[segment], MapStyle.nightZone, style = night)
}

/** Schildgröße in Metern: in echt etwa 6 m (überzeichnet), aber nie kleiner als ein lesbares Bildschirmschild. */
internal fun signRadius(scale: Float): Float = max(SIGN_RADIUS_METERS, SIGN_MIN_PIXELS / scale)

/**
 * Wo die Tempolimit-Schilder stehen: am Anfang und am Ende der begrenzten Strecke, die Mitte bleibt dem Blitzer.
 * Ist der Abschnitt zu kurz für zwei Schilder, steht eines im ersten Viertel. Ein Nachtlimit steht quer zur Straße
 * neben dem Tagschild.
 */
internal fun speedSigns(
    dayLimits: Map<Int, Int>,
    nightLimits: Map<Int, Int>,
    picker: MapPicker,
    scale: Float,
): List<SpeedSign> {
    val radius = signRadius(scale)
    val signs = ArrayList<SpeedSign>()
    for ((segment, kmh) in dayLimits) {
        for (fraction in signFractions(segment, dayLimits, picker, radius)) {
            val (x, y) = picker.pointAt(segment, fraction)
            signs += SpeedSign(x, y, if (kmh <= WALKING_SPEED_KMH) "S" else "$kmh")
        }
    }
    for ((segment, kmh) in nightLimits) {
        val shift = if (segment in dayLimits) radius * NIGHT_SHIFT else 0f
        for (fraction in signFractions(segment, nightLimits, picker, radius)) {
            val (x, y) = beside(picker, segment, fraction, shift)
            signs += SpeedSign(x, y, "$kmh", night = true)
        }
    }
    return avoidStops(signs, picker.city.transitStops, radius * (1 + STOP_CLEARANCE))
}

/**
 * Schilder, die auf einer Haltestelle stehen würden, rücken von ihr weg, bis sie sich nicht mehr berühren – sonst
 * verdeckt das Tempo-Schild das „H“ (bzw. den Bus davor).
 */
internal fun avoidStops(
    signs: List<SpeedSign>,
    stops: List<TransitStop>,
    distance: Float,
): List<SpeedSign> = if (signs.isEmpty()) signs else pushAway(signs, stops.map { Offset(it.x, it.y) }, distance)

/** Schilder näher als [distance] an einem der [points] rücken von ihm weg, bis sie es nicht mehr berühren. */
internal fun pushAway(
    signs: List<SpeedSign>,
    points: List<Offset>,
    distance: Float,
): List<SpeedSign> =
    signs.map { sign ->
        var x = sign.x
        var y = sign.y
        for (point in points) {
            val dx = x - point.x
            val dy = y - point.y
            val d = hypot(dx, dy)
            if (d >= distance) continue
            // Genau darauf: nach unten ausweichen (weg von Tafeln, die über ihrem Standort stehen), sonst in
            // Verlängerung des Abstands
            val (ux, uy) = if (d < MIN_DISTANCE) 0f to -1f else dx / d to dy / d
            x = point.x + ux * distance
            y = point.y + uy * distance
        }
        if (x == sign.x && y == sign.y) sign else SpeedSign(x, y, sign.label, sign.night)
    }

/**
 * Wo auf [segment] Schilder stehen: am Anfang bzw. Ende nur, wenn dort die Strecke beginnt oder endet (Kreuzung oder
 * anderes Limit dahinter) – und an einer Kreuzung, über die die Straße mit gleichem Limit weiterläuft, nur einmal.
 * Dazwischen wie an echten Straßen etwa alle 300 m eine Wiederholung – außer rund um die Mitte, wo ein Blitzer
 * stehen kann.
 */
private fun signFractions(
    segment: Int,
    limits: Map<Int, Int>,
    picker: MapPicker,
    radius: Float,
): List<Float> {
    val (from, to) = picker.endsOf(segment)
    val kmh = limits[segment]

    // Strecke endet hier – außer es geht mit demselben Limit weiter; geht dieselbe Straße hinter der Kreuzung mit
    // gleichem Limit weiter, steht nur ein Schild für beide Seiten (beim Abschnitt mit der kleineren Nummer)
    fun boundary(node: Int): Boolean {
        val stretches = picker.stretches
        val continues = stretches.continuation(segment, node)?.let { limits[it] == kmh } ?: false
        val sharedSign = stretches.sameStreetAcross(segment, node).any { limits[it] == kmh && it < segment }
        return !continues && !sharedSign
    }
    val atStart = boundary(from)
    val atEnd = boundary(to)
    val length = picker.lengthOf(segment)
    if (length < radius * MIN_LENGTH_FOR_TWO) {
        return when {
            atStart -> listOf(SINGLE_SIGN)
            atEnd -> listOf(1 - SINGLE_SIGN)
            else -> emptyList()
        }
    }
    val inset = (radius * END_INSET / length).coerceIn(MIN_INSET, MAX_INSET)
    val step = max(REPEAT_METERS, radius * MIN_REPEAT_RADII) / length
    val keepClear = radius * CAMERA_CLEARANCE / length
    val fractions = ArrayList<Float>()
    if (atStart) fractions += inset
    var f = inset + step
    while (f < 1 - inset - step / 2) {
        if (abs(f - CENTER) > keepClear) fractions += f
        f += step
    }
    if (atEnd) fractions += 1 - inset
    return fractions
}

/** Punkt bei [fraction], um [shift] Meter quer zur Fahrtrichtung versetzt. */
private fun beside(
    picker: MapPicker,
    segment: Int,
    fraction: Float,
    shift: Float,
): Pair<Float, Float> {
    val (x, y) = picker.pointAt(segment, fraction)
    if (shift == 0f) return x to y
    val (ax, ay) = picker.pointAt(segment, (fraction - DIRECTION_STEP).coerceAtLeast(0f))
    val (bx, by) = picker.pointAt(segment, (fraction + DIRECTION_STEP).coerceAtMost(1f))
    val length = hypot(bx - ax, by - ay)
    if (length == 0f) return x + shift to y
    return x - (by - ay) / length * shift to y + (bx - ax) / length * shift
}

private const val WALKING_SPEED_KMH = 7
private const val SIGN_RADIUS_METERS = 6f
private const val SIGN_MIN_PIXELS = 13f
private const val ZONE_WIDTH_METERS = 22f
private const val ZONE_MIN_PIXELS = 7f
private const val NIGHT_SHIFT = 2.3f
private const val MIN_LENGTH_FOR_TWO = 8f
private const val SINGLE_SIGN = 0.25f
private const val END_INSET = 1.8f
private const val MIN_INSET = 0.08f
private const val MAX_INSET = 0.3f
private const val DIRECTION_STEP = 0.02f
private const val REPEAT_METERS = 300f
private const val MIN_REPEAT_RADII = 10f
private const val CAMERA_CLEARANCE = 3f
private const val CENTER = 0.5f
private const val NIGHT_WIDTH = 0.3f
private const val NIGHT_DASH_GAP = 0.6f

/** Abstand Schild ↔ Haltestelle über die beiden Radien hinaus (Anteil des Schildradius). */
private const val STOP_CLEARANCE = 0.9f
private const val MIN_DISTANCE = 0.01f
