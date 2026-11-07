package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.sim.InfrastructureSnapshot
import de.bfi.trafficmaniac.sim.ParkingLotState
import kotlin.math.max

/** Straßenzustand als Einfärbung: grün = gut, gelb = Schlaglöcher, rot = Krater. */
internal fun DrawScope.drawCondition(
    paths: SegmentPaths,
    infrastructure: InfrastructureSnapshot,
    scale: Float,
) {
    val conditions = infrastructure.segmentCondition
    if (conditions.isEmpty()) return
    val width = max(CONDITION_WIDTH_METERS, CONDITION_MIN_PIXELS / scale)
    for (segment in paths.mainSegments) {
        val c = conditions[segment]
        val color =
            if (c >= GOOD) {
                lerp(MapStyle.conditionFair, MapStyle.conditionGood, ((c - GOOD) / (1 - GOOD)).coerceIn(0f, 1f))
            } else {
                lerp(MapStyle.conditionBad, MapStyle.conditionFair, ((c - BAD) / (GOOD - BAD)).coerceIn(0f, 1f))
            }
        drawPath(paths[segment], color.copy(alpha = CONDITION_ALPHA), style = Stroke(width, cap = StrokeCap.Round))
    }
}

/** Baustellen (orange-weiß gestrichelt), Sperrungen (rot), eingestürzte Brücken (schwarz), Umleitung (orange). */
internal fun DrawScope.drawWorksAndDetours(
    paths: SegmentPaths,
    infrastructure: InfrastructureSnapshot,
    scale: Float,
) {
    val width = max(WORKS_WIDTH_METERS, WORKS_MIN_PIXELS / scale)
    for (segment in infrastructure.detourSegments) {
        drawPath(
            paths[segment],
            MapStyle.detour.copy(alpha = DETOUR_ALPHA),
            style = Stroke(width * DETOUR_WIDTH_FACTOR, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(width * DASH, width * DASH))
    for (segment in infrastructure.worksSegments) {
        drawPath(paths[segment], Color.White, style = Stroke(width, cap = StrokeCap.Butt))
        drawPath(paths[segment], MapStyle.works, style = Stroke(width, cap = StrokeCap.Butt, pathEffect = dash))
    }
    for (segment in infrastructure.closedSegments - infrastructure.worksSegments) {
        drawPath(paths[segment], MapStyle.trafficSignal, style = Stroke(width, pathEffect = dash))
    }
    for (segment in infrastructure.collapsedSegments) {
        drawPath(paths[segment], MapStyle.collapsed, style = Stroke(width * COLLAPSE_WIDTH_FACTOR))
    }
}

/**
 * Gemeldete Schäden: Schlaglöcher (braune Punkte), daneben ein Warnschild (Dreieck), wenn eines steht,
 * und ein Paragraf-Kasten für die Planfeststellung.
 */
internal fun DrawScope.drawDamage(
    reports: List<DamageReport>,
    picker: MapPicker,
    scale: Float,
) {
    val size = max(DAMAGE_SIZE_METERS, DAMAGE_MIN_PIXELS / scale)
    for (report in reports) {
        for (segment in report.segments) drawDamageAt(report, picker.midpoint(segment), size)
    }
}

private fun DrawScope.drawDamageAt(
    report: DamageReport,
    point: Pair<Float, Float>,
    size: Float,
) {
    run {
        val (x, y) = point
        val holes = if (report.conditionPercent < CRATER_PERCENT) CRATER_HOLES else POTHOLE_HOLES
        for (i in 0 until holes) {
            val dx = (i - holes / 2f) * size * HOLE_SPACING
            drawCircle(MapStyle.pothole, size * HOLE_SIZE, Offset(x + dx, y - size * HOLE_OFFSET))
        }
        when (report.status) {
            DamageStatus.SIGNED -> {
                // Das Schild steht gern jahrelang: schief und verblasst
                val tilt = ((report.id * SIGN_TILT_STEP) % (2 * SIGN_MAX_TILT)) - SIGN_MAX_TILT
                rotate(tilt, pivot = Offset(x - size * SIGN_OFFSET, y + size * SIGN_OFFSET)) {
                    drawWarningTriangle(x - size * SIGN_OFFSET, y + size * SIGN_OFFSET, size)
                }
            }

            DamageStatus.PLANNING -> {
                val topLeft = Offset(x + size, y + size)
                drawRect(MapStyle.planning, topLeft, Size(size * 2, size * 2))
            }

            DamageStatus.OPEN -> {
                drawCircle(
                    MapStyle.hazardLight,
                    size * HOLE_SIZE * 2,
                    Offset(x, y + size),
                    style =
                        Stroke(size * OPEN_RING),
                )
            }

            else -> {
                Unit
            }
        }
    }
}

private fun DrawScope.drawWarningTriangle(
    x: Float,
    y: Float,
    size: Float,
) {
    val triangle =
        Path().apply {
            moveTo(x, y + size)
            lineTo(x + size, y - size * TRIANGLE_BASE)
            lineTo(x - size, y - size * TRIANGLE_BASE)
            close()
        }
    drawPath(triangle, Color.White.copy(alpha = FADED))
    drawPath(triangle, MapStyle.trafficSignal.copy(alpha = FADED), style = Stroke(size * TRIANGLE_RING))
    drawLine(MapStyle.ink, Offset(x, y - size * TRIANGLE_BASE), Offset(x, y - size * POLE), size * POLE_WIDTH)
}

/** Parkplätze: blaues Quadrat, rot umrandet, wenn voll; grau, wenn abgeschafft. */
internal fun DrawScope.drawParkingLots(
    lots: List<ParkingLotState>,
    scale: Float,
) {
    val size = max(LOT_SIZE_METERS, LOT_MIN_PIXELS / scale)
    for (lot in lots) {
        val topLeft = Offset(lot.x - size / 2, lot.y - size / 2)
        val fill = if (lot.capacity == 0) MapStyle.chargingFull else MapStyle.parking
        drawRect(fill, topLeft, Size(size, size))
        if (lot.capacity > 0 && lot.occupied >= lot.capacity) {
            drawRect(MapStyle.trafficSignal, topLeft, Size(size, size), style = Stroke(size * LOT_RING))
        }
    }
}

private const val GOOD = 0.6f
private const val BAD = 0.2f
private const val CONDITION_WIDTH_METERS = 9f
private const val CONDITION_MIN_PIXELS = 3f
private const val CONDITION_ALPHA = 0.75f
private const val WORKS_WIDTH_METERS = 8f
private const val WORKS_MIN_PIXELS = 4f
private const val DETOUR_WIDTH_FACTOR = 1.6f
private const val DETOUR_ALPHA = 0.6f
private const val COLLAPSE_WIDTH_FACTOR = 1.5f
private const val DASH = 1.5f
private const val DAMAGE_SIZE_METERS = 4f
private const val DAMAGE_MIN_PIXELS = 5f
private const val CRATER_PERCENT = 35
private const val CRATER_HOLES = 3
private const val POTHOLE_HOLES = 2
private const val HOLE_SPACING = 0.9f
private const val HOLE_SIZE = 0.45f
private const val HOLE_OFFSET = 0.5f
private const val SIGN_OFFSET = 1.8f
private const val TRIANGLE_BASE = 0.7f
private const val TRIANGLE_RING = 0.25f
private const val SIGN_TILT_STEP = 7f
private const val SIGN_MAX_TILT = 14f
private const val FADED = 0.7f
private const val POLE = 1.6f
private const val POLE_WIDTH = 0.15f
private const val LOT_SIZE_METERS = 10f
private const val LOT_MIN_PIXELS = 6f
private const val LOT_RING = 0.25f
private const val OPEN_RING = 0.33f
