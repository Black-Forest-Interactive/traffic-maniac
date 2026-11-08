package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.StreetStretches
import de.bfi.trafficmaniac.sim.SignalMarkers
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Was im Straßennetz-Modus angeklickt wurde. */
sealed interface NetworkPick {
    data class Segment(
        val id: Int,
    ) : NetworkPick

    data class Node(
        val id: Int,
    ) : NetworkPick
}

/** Art eines Knotens im Straßennetz-Modus. */
enum class NodeKind {
    /** Genau zwei Abschnitte, keine Ampel: nur ein Knick in der Straße. */
    THROUGH,

    /** Kreuzung oder Einmündung – hier endet eine Strecke. */
    JUNCTION,

    /** Nur ein Abschnitt: Sackgasse oder Rand der Karte. */
    DEAD_END,

    /** Kein befahrener Abschnitt, nur passive Nebenstraßen. */
    PASSIVE,
}

/**
 * Vorberechnete Daten für den Straßennetz-Modus (F4): Strecken, Art jedes Knotens, Abschnitte je Knoten.
 * Jede Strecke bekommt eine eigene Farbe – so sieht man, wo eine Strecke endet und die nächste beginnt.
 */
class NetworkDebug(
    val city: CityMap,
) {
    val stretches = StreetStretches(city.roads)

    /** Alle Abschnitte je Knoten (auch passive). */
    val segmentsAtNode: List<List<Int>> =
        List(city.roads.nodes.size) { mutableListOf<Int>() }.also { lists ->
            for (s in city.roads.segments) {
                lists[s.from] += s.id
                if (s.to != s.from) lists[s.to] += s.id
            }
        }

    /** Nummer der Strecke je Abschnitt (passive Abschnitte: −1). */
    val stretchOfSegment: IntArray = IntArray(city.roads.segments.size) { -1 }

    /** Abschnitte je Strecke. */
    val stretchSegments: List<List<Int>>

    init {
        val list = ArrayList<List<Int>>()
        for (segment in city.roads.segments) {
            if (segment.passive || stretchOfSegment[segment.id] >= 0) continue
            val stretch = stretches.stretchOf(segment.id)
            for (id in stretch) stretchOfSegment[id] = list.size
            list += stretch
        }
        stretchSegments = list
    }

    val nodeKinds: Array<NodeKind> =
        Array(city.roads.nodes.size) { node ->
            val active = segmentsAtNode[node].count { !city.roads.segments[it].passive }
            when {
                active == 0 -> NodeKind.PASSIVE
                active == 1 && segmentsAtNode[node].size == 1 -> NodeKind.DEAD_END
                stretches.isJunction(node) -> NodeKind.JUNCTION
                else -> NodeKind.THROUGH
            }
        }

    /** Knoten, die überhaupt an einem Abschnitt hängen (der Rest ist Ballast aus dem Import). */
    val usedNodes: List<Int> = segmentsAtNode.indices.filter { segmentsAtNode[it].isNotEmpty() }

    fun color(segment: RoadSegment): Color {
        val stretch = stretchOfSegment[segment.id]
        return if (stretch < 0) PASSIVE_COLOR else STRETCH_PALETTE[stretch % STRETCH_PALETTE.size]
    }

    /** Abschnitte, die bei einer Auswahl hervorgehoben werden: die ganze Strecke bzw. alle am Knoten. */
    fun highlighted(pick: NetworkPick?): Set<Int> =
        when (pick) {
            is NetworkPick.Segment -> stretches.stretchOf(pick.id).toSet()
            is NetworkPick.Node -> segmentsAtNode.getOrNull(pick.id)?.toSet().orEmpty()
            null -> emptySet()
        }

    private val picker = MapPicker(city)

    /** Knoten (bevorzugt) oder Abschnitt in der Nähe eines Klicks – auch passive Nebenstraßen. */
    fun pick(
        x: Float,
        y: Float,
        radius: Float,
    ): NetworkPick? {
        val node =
            usedNodes
                .map { it to hypot(city.roads.nodes[it].x - x, city.roads.nodes[it].y - y) }
                .filter { it.second <= radius * NODE_PICK_SHARE }
                .minByOrNull { it.second }
                ?.first
        if (node != null) return NetworkPick.Node(node)
        return picker.nearestSegment(x, y, radius) { true }?.let { NetworkPick.Segment(it) }
    }

    /** Länge eines Abschnitts in Metern. */
    fun length(segment: Int): Float {
        val g = city.roads.segments[segment].geometry
        return (1 until g.pointCount).fold(0f) { sum, i -> sum + hypot(g.x(i) - g.x(i - 1), g.y(i) - g.y(i - 1)) }
    }

    companion object {
        /** Knoten gewinnen nur, wenn der Klick deutlich näher dran ist als der Fangradius für Abschnitte. */
        private const val NODE_PICK_SHARE = 0.6f

        val PASSIVE_COLOR = Color(0xFF9AA5B1)
        val JUNCTION_COLOR = Color(0xFFC62B3B)
        val THROUGH_COLOR = Color(0xFFFFFFFF)
        val DEAD_END_COLOR = Color(0xFFF28C28)
        val SIGNAL_COLOR = Color(0xFFFFC21A)
        val SELECTION_COLOR = Color(0xFF15202B)

        /** Gut unterscheidbare, kräftige Farben für benachbarte Strecken. */
        private val STRETCH_PALETTE =
            listOf(
                Color(0xFF2257B8),
                Color(0xFF1E7A44),
                Color(0xFF8E3FB5),
                Color(0xFFD9480F),
                Color(0xFF0B7285),
                Color(0xFFB8860B),
                Color(0xFFC2255C),
                Color(0xFF5C940D),
            )
    }
}

/**
 * Straßennetz-Ebene: Abschnitte (Farbe = Strecke, grau gestrichelt = passiv), Stützpunkte der Geometrie,
 * Knoten nach Art, Pfeile bei Einbahnstraßen, Spuren je Richtung, die Ampel-Positionen der Simulation und
 * ab etwa Straßenebene die Nummern von Abschnitten und Knoten. Gezeichnet in Bildschirm-Pixeln, damit Linien
 * und Punkte bei jedem Zoom gleich dick bleiben.
 */
internal fun DrawScope.drawNetwork(
    debug: NetworkDebug,
    camera: Camera,
    textMeasurer: TextMeasurer,
    pick: NetworkPick?,
    signals: SignalMarkers?,
) {
    val screen = Screen(camera, size.width, size.height)
    drawRect(SCRIM, size = size)
    drawSegments(debug, screen, debug.highlighted(pick))
    drawNodes(debug, screen, pick)
    if (signals != null && screen.detailed) drawSignalMarkers(signals, screen)
    if (camera.scale >= LABEL_SCALE) drawNetworkLabels(debug, textMeasurer, screen::x, screen::y, screen.view)
}

/** Umrechnung Welt → Bildschirm und der sichtbare Ausschnitt (mit etwas Rand). */
private class Screen(
    private val camera: Camera,
    private val width: Float,
    private val height: Float,
) {
    val view = Rect(-CULL_MARGIN, -CULL_MARGIN, width + CULL_MARGIN, height + CULL_MARGIN)

    /** Ab Straßenebene: Stützpunkte, Pfeile, Durchgangsknoten und Ampel-Positionen. */
    val detailed = camera.scale >= DETAIL_SCALE

    fun x(worldX: Float) = camera.worldToScreenX(worldX, width)

    fun y(worldY: Float) = camera.worldToScreenY(worldY, height)
}

private fun DrawScope.drawSegments(
    debug: NetworkDebug,
    screen: Screen,
    highlighted: Set<Int>,
) {
    for (segment in debug.city.roads.segments) {
        val g = segment.geometry
        if (!g.visibleIn(screen.view, screen::x, screen::y)) continue
        val color = debug.color(segment)
        val path = Path()
        path.moveTo(screen.x(g.x(0)), screen.y(g.y(0)))
        for (i in 1 until g.pointCount) path.lineTo(screen.x(g.x(i)), screen.y(g.y(i)))
        if (segment.id in highlighted) drawPath(path, NetworkDebug.SELECTION_COLOR, style = Stroke(HIGHLIGHT_WIDTH))
        drawPath(path, color, style = segmentStroke(segment))
        if (!screen.detailed) continue
        for (i in 1 until g.pointCount - 1) drawCircle(color, VERTEX_RADIUS, Offset(screen.x(g.x(i)), screen.y(g.y(i))))
        if (!segment.passive) drawDirections(segment, screen::x, screen::y, color)
    }
}

private fun segmentStroke(segment: RoadSegment): Stroke =
    when {
        segment.passive -> Stroke(PASSIVE_WIDTH, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)))
        segment.bridge -> Stroke(BRIDGE_WIDTH)
        else -> Stroke(LINE_WIDTH)
    }

private fun DrawScope.drawNodes(
    debug: NetworkDebug,
    screen: Screen,
    pick: NetworkPick?,
) {
    for (node in debug.usedNodes) {
        val n = debug.city.roads.nodes[node]
        val center = Offset(screen.x(n.x), screen.y(n.y))
        val kind = debug.nodeKinds[node]
        if (!screen.view.contains(center) || (!screen.detailed && kind == NodeKind.THROUGH)) continue
        val radius = if (kind == NodeKind.JUNCTION || kind == NodeKind.DEAD_END) JUNCTION_RADIUS else THROUGH_RADIUS
        drawCircle(nodeColor(kind), radius, center)
        drawCircle(OUTLINE, radius, center, style = Stroke(1f))
        if (n.trafficSignals) drawCircle(NetworkDebug.SIGNAL_COLOR, SIGNAL_RING, center, style = Stroke(SIGNAL_STROKE))
        if (pick == NetworkPick.Node(node)) {
            drawCircle(NetworkDebug.SELECTION_COLOR, SELECTION_RING, center, style = Stroke(SIGNAL_STROKE))
        }
    }
}

private fun nodeColor(kind: NodeKind): Color =
    when (kind) {
        NodeKind.JUNCTION -> NetworkDebug.JUNCTION_COLOR
        NodeKind.DEAD_END -> NetworkDebug.DEAD_END_COLOR
        NodeKind.THROUGH -> NetworkDebug.THROUGH_COLOR
        NodeKind.PASSIVE -> NetworkDebug.PASSIVE_COLOR
    }

/** Ampel-Positionen, wie die Simulation sie berechnet: Punkt an der Haltelinie, Strich zur Straßenmitte. */
private fun DrawScope.drawSignalMarkers(
    signals: SignalMarkers,
    screen: Screen,
) {
    for (i in signals.links.indices) {
        val at = Offset(screen.x(signals.x[i]), screen.y(signals.y[i]))
        if (!screen.view.contains(at)) continue
        val mid = Offset(screen.x(signals.centerX[i]), screen.y(signals.centerY[i]))
        drawLine(NetworkDebug.SIGNAL_COLOR, mid, at, SIGNAL_STROKE)
        drawCircle(OUTLINE, SIGNAL_DOT + 1f, at)
        drawCircle(NetworkDebug.SIGNAL_COLOR, SIGNAL_DOT, at)
    }
}

/** Pfeile in Fahrtrichtung in der Mitte des Abschnitts: einer bei Einbahnstraßen, sonst je Richtung einer. */
private fun DrawScope.drawDirections(
    segment: RoadSegment,
    sx: (Float) -> Float,
    sy: (Float) -> Float,
    color: Color,
) {
    val g = segment.geometry
    val mid = (g.pointCount - 1) / 2
    val a = Offset(sx(g.x(mid)), sy(g.y(mid)))
    val b = Offset(sx(g.x(mid + 1)), sy(g.y(mid + 1)))
    if ((b - a).getDistance() < MIN_ARROW_SPAN) return
    val center = (a + b) / 2f
    val angle = atan2(b.y - a.y, b.x - a.x)
    val arrows = if (segment.isOneway) listOf(angle) else listOf(angle, angle + PI_F)
    for ((index, direction) in arrows.withIndex()) {
        // Gegenrichtung leicht versetzt, damit sich die Pfeile nicht überdecken
        val side =
            if (arrows.size == 1) {
                0f
            } else if (index == 0) {
                ARROW_SIDE
            } else {
                -ARROW_SIDE
            }
        val base = center + Offset(-sin(angle) * side, cos(angle) * side)
        val tip = base + Offset(cos(direction), sin(direction)) * ARROW_LENGTH
        val left = tip + Offset(cos(direction + ARROW_WING), sin(direction + ARROW_WING)) * -ARROW_HEAD
        val right = tip + Offset(cos(direction - ARROW_WING), sin(direction - ARROW_WING)) * -ARROW_HEAD
        drawLine(color, base, tip, ARROW_STROKE)
        drawLine(color, tip, left, ARROW_STROKE)
        drawLine(color, tip, right, ARROW_STROKE)
    }
}

/** Nummern: „S12 2|1“ an Abschnitten (Spuren vor|zurück), „N7“ an Kreuzungen und Sackgassen. */
private fun DrawScope.drawNetworkLabels(
    debug: NetworkDebug,
    textMeasurer: TextMeasurer,
    sx: (Float) -> Float,
    sy: (Float) -> Float,
    view: Rect,
) {
    val roads = debug.city.roads
    for (segment in roads.segments) {
        val g = segment.geometry
        val mid = (g.pointCount - 1) / 2
        val at = Offset((sx(g.x(mid)) + sx(g.x(mid + 1))) / 2, (sy(g.y(mid)) + sy(g.y(mid + 1))) / 2)
        if (!view.contains(at)) continue
        val lanes = if (segment.passive) "" else " ${segment.lanesForward}|${segment.lanesBackward}"
        val text = "S${segment.id}$lanes"
        label(textMeasurer, text, at + Offset(0f, LABEL_OFFSET), debug.color(segment))
    }
    for (node in debug.usedNodes) {
        if (debug.nodeKinds[node] == NodeKind.THROUGH || debug.nodeKinds[node] == NodeKind.PASSIVE) continue
        val n = roads.nodes[node]
        val at = Offset(sx(n.x), sy(n.y))
        if (!view.contains(at)) continue
        label(textMeasurer, "N$node", at - Offset(0f, LABEL_OFFSET), NetworkDebug.JUNCTION_COLOR)
    }
}

private fun DrawScope.label(
    textMeasurer: TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
) {
    val layout = textMeasurer.measure(text, LABEL_STYLE.copy(color = color))
    val topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    drawRect(
        LABEL_BACKGROUND,
        topLeft - Offset(LABEL_PADDING, 0f),
        androidx.compose.ui.geometry
            .Size(layout.size.width + 2 * LABEL_PADDING, layout.size.height.toFloat()),
    )
    drawText(layout, topLeft = topLeft)
}

private fun de.bfi.trafficmaniac.city.Polyline.visibleIn(
    view: Rect,
    sx: (Float) -> Float,
    sy: (Float) -> Float,
): Boolean {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in 0 until pointCount) {
        val x = sx(x(i))
        val y = sy(y(i))
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    return view.overlaps(Rect(minX, minY, maxX, maxY))
}

private val SCRIM = Color(0x66FFFFFF)
private val OUTLINE = Color(0xFF15202B)
private val LABEL_BACKGROUND = Color(0xD9FFFFFF)
private val LABEL_STYLE = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
private val PI_F = PI.toFloat()

/** Pixel pro Meter, ab denen Stützpunkte, Pfeile und Durchgangsknoten erscheinen. */
private const val DETAIL_SCALE = 0.6f

/** Pixel pro Meter, ab denen Nummern erscheinen. */
private const val LABEL_SCALE = 1.6f
private const val CULL_MARGIN = 40f
private const val LINE_WIDTH = 3f
private const val BRIDGE_WIDTH = 5f
private const val PASSIVE_WIDTH = 1.5f
private const val HIGHLIGHT_WIDTH = 10f
private const val DASH = 5f
private const val VERTEX_RADIUS = 2.2f
private const val JUNCTION_RADIUS = 5f
private const val THROUGH_RADIUS = 3f
private const val SIGNAL_RING = 8f
private const val SIGNAL_STROKE = 2f
private const val SELECTION_RING = 12f
private const val SIGNAL_DOT = 3.5f
private const val MIN_ARROW_SPAN = 24f
private const val ARROW_LENGTH = 14f
private const val ARROW_HEAD = 5f
private const val ARROW_WING = 0.5f
private const val ARROW_SIDE = 5f
private const val ARROW_STROKE = 2f
private const val LABEL_OFFSET = 12f
private const val LABEL_PADDING = 2f
