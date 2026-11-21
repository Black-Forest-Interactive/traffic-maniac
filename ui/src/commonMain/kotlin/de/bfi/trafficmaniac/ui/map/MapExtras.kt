package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import de.bfi.trafficmaniac.city.TransitStop
import de.bfi.trafficmaniac.game.CameraRecord
import de.bfi.trafficmaniac.sim.TransitLineStatus
import de.bfi.trafficmaniac.ui.TmColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Was die Karte außer dem Verkehr zeigt (Ebene der Kartenansicht) und was eingeblendet ist. Die Ebenen Verkehrsdichte,
 * Buslinien und Geld zeichnet diese Datei; die übrigen haben eigene Schalter in [MapView].
 */
class MapExtras(
    /** Verkehrsstärke je Abschnitt, einfarbig hell → dunkel. */
    val density: Boolean = false,
    /** Buslinien in Linienfarbe, Abschnitte nach Verspätung. */
    val transit: Boolean = false,
    /** Einnahmen je Ort. */
    val money: Boolean = false,
    /** Schilder, Ampeln und Markierungen. */
    val signs: Boolean = true,
    /** Blitzer, Laser, Kontrollen. */
    val cameras: Boolean = true,
    /** Unfälle, Pannen und Schäden. */
    val incidents: Boolean = true,
    val assignments: Boolean = true,
    /** Färbung und Hervorhebung der Buslinien. */
    val transitStyle: TransitStyle = TransitStyle(),
) {
    /** Die Staufarben der Gesamtansicht stören die einfarbigen Ebenen. */
    val hidesFlow: Boolean get() = density || transit || money
}

/** Verkehrsstärke (Kfz/Std.) je Abschnitt: hell bei wenig, dunkelblau ab [DENSITY_FULL]. */
internal fun DrawScope.drawDensity(
    paths: SegmentPaths,
    volume: FloatArray,
    scale: Float,
) {
    if (volume.isEmpty()) return
    val stroke =
        Stroke(max(LAYER_WIDTH_METERS, LAYER_MIN_PIXELS / scale), cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (segment in paths.mainSegments) {
        val v = volume.getOrElse(segment) { 0f }
        if (v < DENSITY_MIN) continue
        val share = min(v / DENSITY_FULL, 1f)
        val color =
            if (share < HALF) {
                lerp(DensityLow, DensityMid, share / HALF)
            } else {
                lerp(DensityMid, DensityHigh, (share - HALF) / HALF)
            }
        drawPath(paths[segment], color, style = stroke)
    }
}

/** Grün bis 1 min, gelb bis 3 min, rot darüber, grau ohne Daten. */
fun delayColor(seconds: Double?): Color =
    when {
        seconds == null -> DelayNone
        seconds <= PUNCTUAL_SECONDS -> DelayGood
        seconds <= LATE_SECONDS -> DelayLate
        else -> DelayBad
    }

/** Einnahmen je Blitzer als Kreis (Fläche ∝ Betrag), Amtsblau. */
internal fun DrawScope.drawMoney(
    cameras: List<CameraRecord>,
    picker: MapPicker,
    scale: Float,
) {
    val top = cameras.maxOfOrNull { it.totalRevenue }?.takeIf { it > 0 } ?: return
    val maxRadius = MONEY_MAX_PIXELS / scale
    for (camera in cameras) {
        if (camera.totalRevenue <= 0) continue
        val (x, y) = picker.midpoint(camera.segment)
        val radius = max(maxRadius * sqrt(camera.totalRevenue.toFloat() / top), MONEY_MIN_PIXELS / scale)
        drawCircle(MoneyIn.copy(alpha = MONEY_ALPHA), radius, Offset(x, y))
        drawCircle(MoneyIn, radius, Offset(x, y), style = Stroke(2f / scale))
    }
}

private val DensityLow = Color(0xFFEEF1F8)
private val DensityMid = Color(0xFF8FA9CC)
private val DensityHigh = Color(0xFF22355E)

// Dieselben Farben wie in der Linienliste und bei den Halten: grün pünktlich, orange leicht, rot stark verspätet
private val DelayGood = TmColors.Success
private val DelayLate = TmColors.Warn
private val DelayBad = TmColors.Danger
private val DelayNone = Color(0xFFB8C2CC)
private val MoneyIn = Color(0xFF2F6BD0)

/** Ab so vielen Kfz/Std. ist ein Abschnitt dunkel. */
const val DENSITY_FULL = 1200f
private const val DENSITY_MIN = 20f
private const val HALF = 0.5f
private const val LAYER_WIDTH_METERS = 8f
private const val LAYER_MIN_PIXELS = 3f
private const val LINE_WIDTH_METERS = 10f
private const val LINE_MIN_PIXELS = 5f
private const val INNER_SHARE = 0.45f
private const val DIM_ALPHA = 0.25f
private const val STOP_RADIUS = 0.6f
private const val STOP_RING = 0.45f
private const val PUNCTUAL_SECONDS = 60.0
private const val LATE_SECONDS = 180.0
private const val MONEY_MAX_PIXELS = 34f
private const val MONEY_MIN_PIXELS = 4f
private const val MONEY_ALPHA = 0.35f
