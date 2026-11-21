package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.Polyline
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.TransitLineStatus
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.roundToInt

/** Wie die Buslinien-Ebene aussieht: Färbung und hervorgehobene Linie. */
class TransitStyle(
    /** `true`: Abschnitte nach Verspätung (grün/gelb/rot); sonst in Linienfarbe. */
    val byDelay: Boolean = false,
    /** Hervorgehobene Linie (Nummer): farbig und mit beschrifteten Halten, alle anderen grau. */
    val highlight: String? = null,
)

/**
 * Buslinien nebeneinander: Jede Linie (Nummer, beide Richtungen zusammen) bekommt auf jedem Abschnitt ihren eigenen
 * Platz quer zur Straße – 3,5 px Strich, 1 px Luft. Sind es zu viele (nah dran ab 9, weit weg ab 5), wird daraus ein
 * Bündel aus Farbstreifen (höchstens 6) mit Zahl („28 Linien“) – einmal je zusammenhängendem Straßenzug.
 * Die Versätze hängen vom Zoom ab und werden je Zoomstufe einmal berechnet.
 */
internal class TransitLines(
    private val city: CityMap,
) {
    /** Linien je Abschnitt (sortierte Nummern); gilt für [source]. */
    private var refsBySegment: Map<Int, List<String>> = emptyMap()
    private var source: List<TransitLineStatus>? = null
    private val offsetPaths = HashMap<Long, Path>()
    private var zoomBucket = Int.MIN_VALUE

    private fun update(
        lines: List<TransitLineStatus>,
        scale: Float,
    ) {
        if (lines !== source && lines.map { it.ref } != source?.map { it.ref }) {
            val map = HashMap<Int, MutableSet<String>>()
            for (line in lines) {
                for (section in line.sections) {
                    for (segment in section.segments) map.getOrPut(segment) { sortedSetOf() } += lineNumber(line.ref)
                }
            }
            refsBySegment = map.mapValues { it.value.toList() }
            offsetPaths.clear()
            bundleStretches = emptyList()
            stretchesFor = -1
        }
        source = lines
        val bucket = (log2(scale) * ZOOM_BUCKETS).roundToInt()
        if (bucket != zoomBucket) {
            zoomBucket = bucket
            offsetPaths.clear()
        }
    }

    fun draw(
        scope: DrawScope,
        lines: List<TransitLineStatus>,
        scale: Float,
        style: TransitStyle,
        textMeasurer: TextMeasurer,
    ) = with(scope) {
        if (lines.isEmpty()) return@with
        update(lines, scale)
        // Runde Enden: an Abschnittsgrenzen und Knicken entstehen keine Lücken
        val stroke = Stroke(STROKE_PIXELS / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val spacing = (STROKE_PIXELS + GAP_PIXELS) / scale
        val byRef = lines.groupBy { lineNumber(it.ref) }
        val highlight = style.highlight?.let(::lineNumber)
        if (highlight != null) {
            // Eine Linie gewählt: das übrige Netz nur dünn grau, ohne Bündel und Beschriftung
            val thin = Stroke(DIM_PIXELS / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (segment in refsBySegment.keys) drawPath(paths(segment, 0f), DimColor, style = thin)
        } else {
            drawNetwork(byRef, scale, style.byDelay, stroke, spacing, textMeasurer)
        }
        highlight?.let { ref -> byRef[ref]?.let { drawHighlight(it, scale, style.byDelay) } }
        drawStops(lines, scale, style.highlight, textMeasurer)
    }

    /** Alle Linien: einzeln nebeneinander oder – zu viele – als Bündel mit einer Zahl je Straßenzug. */
    private fun DrawScope.drawNetwork(
        byRef: Map<String, List<TransitLineStatus>>,
        scale: Float,
        byDelay: Boolean,
        stroke: Stroke,
        spacing: Float,
        textMeasurer: TextMeasurer,
    ) {
        // Nah dran passen mehr Linien nebeneinander; weit weg wird früher gebündelt
        val bundleFrom = if (scale >= NEAR_SCALE) NEAR_BUNDLE else FAR_BUNDLE
        for ((segment, refs) in refsBySegment) {
            val colors = refs.map { ref -> segmentColor(byRef.getValue(ref), segment, byDelay) }
            if (refs.size >= bundleFrom) {
                drawBundle(segment, colors, scale)
                continue
            }
            // Viele Linien: schmalere Striche, damit das Band höchstens ≈ 20 px breit wird
            val narrow = refs.size > NARROW_FROM
            val lane = if (narrow) (NARROW_PIXELS + NARROW_GAP) / scale else spacing
            val style =
                if (narrow) {
                    Stroke(
                        NARROW_PIXELS / scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                } else {
                    stroke
                }
            for ((slot, ref) in refs.withIndex()) {
                drawPath(paths(segment, (slot - (refs.size - 1) / 2f) * lane), colors[slot], style = style)
            }
        }
        val labels = stretchLabels(bundleFrom)
        drawBundleLabels(labels, scale, textMeasurer)
    }

    /** Bündel: Streifen in den Farben der ersten [MAX_STRIPES] Linien dicht nebeneinander. */
    private fun DrawScope.drawBundle(
        segment: Int,
        colors: List<Color>,
        scale: Float,
    ) {
        val shown = colors.take(MAX_STRIPES)
        val stripe = Stroke(STRIPE_PIXELS / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val casing =
            Stroke(
                (shown.size * STRIPE_PIXELS + 2 * CASING_PIXELS) / scale,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Round,
            )
        drawPath(paths(segment, 0f), Color.White, style = casing)
        for ((slot, color) in shown.withIndex()) {
            drawPath(paths(segment, (slot - (shown.size - 1) / 2f) * STRIPE_PIXELS / scale), color, style = stripe)
        }
    }

    /** Zusammenhängende Bündel-Strecken (gleiche Schwelle), je eine Beschriftung in der Mitte. */
    private var bundleStretches: List<Pair<Offset, Int>> = emptyList()
    private var stretchesFor = -1

    private fun stretchLabels(bundleFrom: Int): List<Pair<Offset, Int>> {
        if (stretchesFor == bundleFrom) return bundleStretches
        val bundled = refsBySegment.filterValues { it.size >= bundleFrom }
        val roads = city.roads.segments
        val byNode = HashMap<Int, MutableList<Int>>()
        for (segment in bundled.keys) {
            byNode.getOrPut(roads[segment].from) { ArrayList() } += segment
            byNode.getOrPut(roads[segment].to) { ArrayList() } += segment
        }
        val seen = HashSet<Int>()
        val result = ArrayList<Pair<Offset, Int>>()
        for (start in bundled.keys) {
            if (!seen.add(start)) continue
            // Breitensuche über gemeinsame Knoten: alles, was als Bündel zusammenhängt
            val stretch = arrayListOf(start)
            var i = 0
            while (i < stretch.size) {
                val segment = stretch[i++]
                for (node in listOf(roads[segment].from, roads[segment].to)) {
                    for (next in byNode[node].orEmpty()) if (seen.add(next)) stretch += next
                }
            }
            val longest = stretch.maxBy { roads[it].geometry.pointCount }
            result += midpoint(roads[longest].geometry) to stretch.maxOf { bundled.getValue(it).size }
        }
        bundleStretches = result
        stretchesFor = bundleFrom
        return result
    }

    /** Linien (Nummern) auf dem Abschnitt – für die Liste beim Klick auf ein Bündel. */
    fun linesOn(segment: Int): List<String> = refsBySegment[segment].orEmpty()

    /** Die gewählte Linie über allem: mittig auf der Straße, kräftig. */
    private fun DrawScope.drawHighlight(
        directions: List<TransitLineStatus>,
        scale: Float,
        byDelay: Boolean,
    ) {
        val stroke = Stroke(HIGHLIGHT_PIXELS / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val casing =
            Stroke((HIGHLIGHT_PIXELS + 2 * CASING_PIXELS) / scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val color = lineColor(directions.first())
        for (direction in directions) {
            for (section in direction.sections) {
                for (segment in section.segments) drawPath(paths(segment, 0f), Color.White, style = casing)
            }
        }
        for (direction in directions) {
            for (section in direction.sections) {
                val sectionColor = if (byDelay) delayColor(section.delaySeconds) else color
                for (segment in section.segments) drawPath(paths(segment, 0f), sectionColor, style = stroke)
            }
        }
    }

    /** Jede Haltestelle einmal: weißer Punkt, Ring in der Farbe der ersten Linie; bei Hervorhebung mit Namen. */
    private fun DrawScope.drawStops(
        lines: List<TransitLineStatus>,
        scale: Float,
        highlight: String?,
        textMeasurer: TextMeasurer,
    ) {
        val radius = STOP_PIXELS / scale
        val drawn = HashSet<Int>()
        val names = HashSet<String>()
        val placed = ArrayList<Rect>()
        val labels = ArrayList<Pair<String, Offset>>()
        val number = highlight?.let(::lineNumber)
        val ordered = lines.sortedByDescending { lineNumber(it.ref) == number }
        for (line in ordered) {
            val chosen = lineNumber(line.ref) == number
            // Dauerhaft beschriftet nur Start- und Endhalt der gewählten Linie
            val ends = setOf(line.sections.firstOrNull()?.stop, line.sections.lastOrNull()?.stop)
            val ring = if (highlight == null || chosen) lineColor(line) else DimColor
            for (section in line.sections) {
                if (!drawn.add(section.stop)) continue
                val stop = city.transitStops.getOrNull(section.stop) ?: continue
                val center = Offset(stop.x, stop.y)
                drawCircle(Color.White, radius, center)
                drawCircle(ring, radius, center, style = Stroke(RING_PIXELS / scale))
                if (chosen && section.stop in ends && names.add(stop.name)) labels += stop.name to center
            }
        }
        // Namen zuletzt: über allen Haltepunkten, nicht von ihnen verdeckt
        for ((name, center) in labels) labelBeside(name, center, radius, scale, textMeasurer, placed)
    }

    /**
     * Name neben dem Haltepunkt (rechts, sonst links), nie auf ihm; überlappt er eine schon gesetzte
     * Beschriftung, fällt er weg. [placed] sammelt die Flächen in Weltkoordinaten.
     */
    private fun DrawScope.labelBeside(
        name: String,
        center: Offset,
        radius: Float,
        scale: Float,
        textMeasurer: TextMeasurer,
        placed: MutableList<Rect>,
    ) {
        val layout = textMeasurer.measure(name, StopLabel)
        val width = (layout.size.width + 2 * LABEL_PAD) / scale
        val height = (layout.size.height + LABEL_PAD) / scale
        val gap = radius + LABEL_GAP_PIXELS / scale
        val candidates =
            listOf(
                Rect(center.x + gap, center.y - height / 2, center.x + gap + width, center.y + height / 2),
                Rect(center.x - gap - width, center.y - height / 2, center.x - gap, center.y + height / 2),
            )
        val rect = candidates.firstOrNull { candidate -> placed.none { it.overlaps(candidate) } } ?: return
        placed += rect
        drawWorldText(name, Offset(rect.left + LABEL_PAD / scale, center.y), scale, textMeasurer, StopLabel)
    }

    private fun DrawScope.drawBundleLabels(
        labels: List<Pair<Offset, Int>>,
        scale: Float,
        textMeasurer: TextMeasurer,
    ) {
        for ((point, count) in labels) {
            drawWorldText(
                tr("{count} Linien", "count" to count),
                point,
                scale,
                textMeasurer,
                BundleLabel,
                centered = true,
            )
        }
    }

    private fun segmentColor(
        directions: List<TransitLineStatus>,
        segment: Int,
        byDelay: Boolean,
    ): Color {
        if (!byDelay) return lineColor(directions.first())
        // Schlechteste Verspätung der Abschnitte (beider Richtungen), die über diesen Straßenabschnitt führen
        val delays =
            directions.flatMap { direction ->
                direction.sections.filter { segment in it.segments }.mapNotNull { it.delaySeconds }
            }
        return delayColor(delays.maxOrNull())
    }

    /** Der Abschnitt um [offset] Meter quer zur Fahrtrichtung versetzt (links positiv). */
    private fun paths(
        segment: Int,
        offset: Float,
    ): Path {
        val key =
            segment.toLong() shl OFFSET_BITS or ((offset * OFFSET_RESOLUTION).roundToInt().toLong() and OFFSET_MASK)
        return offsetPaths.getOrPut(key) { offsetPath(city.roads.segments[segment].geometry, offset) }
    }
}

/**
 * Farbe einer Linie: ihre eigene aus OpenStreetMap; ohne (dann liefert die Simulation das Stadtbus-Rot) eine feste
 * Farbe aus [LinePalette] nach der Liniennummer – sonst wären fast alle Linien gleich rot.
 */
fun lineColor(line: TransitLineStatus): Color =
    if (line.color != TransitLineStatus.DEFAULT_COLOR) {
        Color(line.color)
    } else {
        LinePalette[lineNumber(line.ref).fold(0) { hash, c -> hash * HASH_PRIME + c.code }.mod(LinePalette.size)]
    }

/** Gut unterscheidbare, kräftige Farben für Linien ohne eigene Farbe. */
private val LinePalette =
    listOf(
        Color(0xFFD62828),
        Color(0xFF1D5FD1),
        Color(0xFF2A9D8F),
        Color(0xFFF77F00),
        Color(0xFF7B2CBF),
        Color(0xFF2B9348),
        Color(0xFFE63995),
        Color(0xFF0096C7),
        Color(0xFF9C6644),
        Color(0xFF5A189A),
        Color(0xFFB5838D),
        Color(0xFF008000),
    )

private const val HASH_PRIME = 31

/** Liniennummer ohne Variantenbuchstaben („722s“ ist die 722). */
internal fun lineNumber(ref: String): String = ref.trimEnd { it.isLetter() }.ifEmpty { ref }

/** Parallele zur Linie im Abstand [offset]: jeder Punkt um die gemittelte Normale der angrenzenden Stücke versetzt. */
private fun offsetPath(
    line: Polyline,
    offset: Float,
): Path {
    val path = Path()
    val count = line.pointCount
    // Einheitliche Seite: „links“ gilt für die Richtung West→Ost (bzw. Süd→Nord bei steilen Abschnitten). Sonst
    // tauschen zwei gegenläufig gespeicherte Nachbarabschnitte die Reihenfolge der Linien – das Band „verdreht“ sich.
    val dx = line.x(count - 1) - line.x(0)
    val dy = line.y(count - 1) - line.y(0)
    val flipped = if (abs(dx) >= abs(dy)) dx < 0 else dy < 0
    val side = if (flipped) -offset else offset
    for (i in 0 until count) {
        val (nx, ny) = normalAt(line, i)
        val x = line.x(i) + nx * side
        val y = line.y(i) + ny * side
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private fun normalAt(
    line: Polyline,
    i: Int,
): Pair<Float, Float> {
    val a = if (i > 0) i - 1 else i
    val b = if (i < line.pointCount - 1) i + 1 else i
    val dx = line.x(b) - line.x(a)
    val dy = line.y(b) - line.y(a)
    val length = hypot(dx, dy)
    return if (length == 0f) 0f to 0f else -dy / length to dx / length
}

private fun midpoint(line: Polyline): Offset {
    val i = line.pointCount / 2
    return if (line.pointCount % 2 == 1 || i == 0) {
        Offset(line.x(i), line.y(i))
    } else {
        Offset((line.x(i - 1) + line.x(i)) / 2, (line.y(i - 1) + line.y(i)) / 2)
    }
}

/** Text in Bildschirmgröße an einem Weltpunkt (die Welt hat y nach Norden). */
internal fun DrawScope.drawWorldText(
    text: String,
    at: Offset,
    scale: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    centered: Boolean = false,
) {
    val layout = textMeasurer.measure(text, style)
    withTransform({
        translate(at.x, at.y)
        scale(1 / scale, -1 / scale, pivot = Offset.Zero)
    }) {
        val left = if (centered) -layout.size.width / 2f else 0f
        drawRoundRect(
            Color.White.copy(alpha = LABEL_BACKGROUND),
            topLeft = Offset(left - LABEL_PAD, -layout.size.height / 2f - LABEL_PAD / 2),
            size =
                androidx.compose.ui.geometry
                    .Size(layout.size.width + 2 * LABEL_PAD, layout.size.height + LABEL_PAD),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(LABEL_PAD, LABEL_PAD),
        )
        drawText(layout, topLeft = Offset(left, -layout.size.height / 2f))
    }
}

/** Grauer Schleier über der Grundkarte, solange eine Ebene die Aufmerksamkeit auf ihre eigenen Linien lenkt. */
internal fun DrawScope.drawVeil() = drawRect(VeilColor.copy(alpha = VEIL_ALPHA))

private val VeilColor = Color(0xFFEEF1F4)
private val DimColor = Color(0xFFB8C2CC)
private val StopLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15202B))
private val BundleLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15202B))

/** Ab so vielen Linien auf einem Abschnitt: Bündel – nah dran später, weit weg früher. */
private const val NEAR_BUNDLE = 9
private const val FAR_BUNDLE = 5
private const val NEAR_SCALE = 0.8f
private const val MAX_STRIPES = 6

/** Ab so vielen Linien schmale Striche (2 px, 0,5 px Luft): 8 Linien ergeben ≈ 20 px. */
private const val NARROW_FROM = 4
private const val NARROW_PIXELS = 2f
private const val NARROW_GAP = 0.5f
private const val STRIPE_PIXELS = 2.5f
private const val STROKE_PIXELS = 3.5f
private const val GAP_PIXELS = 1f
private const val HIGHLIGHT_PIXELS = 8f
private const val DIM_PIXELS = 2f
private const val CASING_PIXELS = 1.5f
private const val STOP_PIXELS = 4.5f
private const val RING_PIXELS = 2f
private const val LABEL_GAP_PIXELS = 6f
private const val LABEL_PAD = 3f
private const val LABEL_BACKGROUND = 0.85f
private const val VEIL_ALPHA = 0.72f
private const val ZOOM_BUCKETS = 4f
private const val OFFSET_BITS = 16
private const val OFFSET_RESOLUTION = 100f
private const val OFFSET_MASK = 0xFFFFL
