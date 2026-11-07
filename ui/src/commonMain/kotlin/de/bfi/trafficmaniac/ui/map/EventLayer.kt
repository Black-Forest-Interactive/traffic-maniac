package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.EventMarker
import kotlin.math.max

/**
 * Ereignisse auf der Karte: Sperrungen (Demo, Fest, Marathon, Markt) magenta gestrichelt, die Route von Korso
 * und Schwertransport halbtransparent; an der Spitze bzw. in der Mitte ein Ring in der Farbe des Ereignisses.
 */
internal fun DrawScope.drawEvents(
    paths: SegmentPaths,
    events: List<EventMarker>,
    scale: Float,
    sprites: SpriteSource = NoSprites,
) {
    if (events.isEmpty()) return
    val width = max(EVENT_WIDTH_METERS, EVENT_MIN_PIXELS / scale)
    val dash = PathEffect.dashPathEffect(floatArrayOf(width, width * DASH_GAP))
    for (event in events) {
        val color = colorOf(event.kind)
        for (segment in event.segments) {
            if (event.kind.isClosure) {
                drawPath(paths[segment], Color.White, style = Stroke(width, cap = StrokeCap.Butt))
                drawPath(paths[segment], color, style = Stroke(width, cap = StrokeCap.Butt, pathEffect = dash))
            } else {
                drawPath(
                    paths[segment],
                    color.copy(alpha = ROUTE_ALPHA),
                    style = Stroke(width * ROUTE_WIDTH_FACTOR, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        drawEventMarker(event.kind, Offset(event.x, event.y), scale, sprites)
    }
}

/** Marker eines Ereignisses: Grafik (z. B. `event_demo`) oder ein Ring in der Farbe des Ereignisses. */
internal fun DrawScope.drawEventMarker(
    kind: EventKind,
    center: Offset,
    scale: Float,
    sprites: SpriteSource = NoSprites,
) {
    val radius = max(MARKER_METERS, MARKER_MIN_PIXELS / scale)
    val icon = radius * EVENT_SPRITE
    if (drawSprite(sprites, "event_" + EVENT_SPRITES.getValue(kind), center, icon, icon, scale)) return
    val color = colorOf(kind)
    drawCircle(Color.White, radius, center)
    drawCircle(color, radius, center, style = Stroke(radius * MARKER_RING))
    drawCircle(color, radius * MARKER_DOT, center)
}

private fun colorOf(kind: EventKind): Color =
    when (kind) {
        EventKind.BIKE_CORSO -> MapStyle.corso
        EventKind.HEAVY_TRANSPORT -> MapStyle.heavyTransport
        else -> MapStyle.eventClosure
    }

private const val EVENT_WIDTH_METERS = 8f
private const val EVENT_MIN_PIXELS = 4f
private const val DASH_GAP = 0.8f
private const val ROUTE_ALPHA = 0.35f
private const val ROUTE_WIDTH_FACTOR = 1.8f
private const val MARKER_METERS = 12f
private const val MARKER_MIN_PIXELS = 7f
private const val MARKER_RING = 0.3f
private const val MARKER_DOT = 0.4f
private const val EVENT_SPRITE = 2.8f
private val EVENT_SPRITES =
    mapOf(
        EventKind.DEMO to "demo",
        EventKind.CITY_FESTIVAL to "festival",
        EventKind.MARATHON to "marathon",
        EventKind.MARKET to "market",
        EventKind.BIKE_CORSO to "corso",
        EventKind.HEAVY_TRANSPORT to "heavy_transport",
    )
