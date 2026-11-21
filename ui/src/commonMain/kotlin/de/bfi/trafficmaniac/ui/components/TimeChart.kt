package de.bfi.trafficmaniac.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.TmColors
import de.bfi.trafficmaniac.ui.TmType
import kotlin.math.ceil

/** Eine Y-Achse: Wertebereich, beschriftete Hilfslinien und ihre Farbe. */
internal class ChartAxis(
    val min: Float,
    val max: Float,
    val ticks: List<Float>,
    val color: Color = TmColors.Muted,
    val label: (Float) -> String,
)

/** Die X-Achse in Spielstunden: der erste Punkt, Stunden je Punkt, wie viele Punkte, erster Spieltag (für „Tag n“). */
internal class TimeAxis(
    /** Absolute Stunde (Tag × 24 + Stunde) des ersten Punkts. */
    val firstHour: Int,
    val points: Int,
    val firstDay: Int,
    val hoursPerPoint: Int = 1,
    /** Uhrzeit an jedem Punkt (für 6-Stunden-Balken); sonst nur „Tag n“ mittags. */
    val clock: Boolean = false,
)

/** Zeichenfläche mit Umrechnung in Pixel – [x] für den Punkt-Index, [y] links, [yRight] rechts. */
internal class ChartScope(
    scope: DrawScope,
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    val x: (Float) -> Float,
    val y: (Float) -> Float,
    val yRight: (Float) -> Float,
    /** Breite eines Punkts in Pixeln (für Balken). */
    val slot: Float,
) : DrawScope by scope {
    /** Beschriftung links neben einem Punkt (z. B. Endwert einer Linie). */
    fun label(
        text: String,
        at: Offset,
        color: Color,
    ) {
        val layout = measurer.measure(text, style.copy(color = color, fontWeight = FontWeight.SemiBold))
        drawText(layout, topLeft = Offset(at.x + 4.dp.toPx(), at.y - layout.size.height / 2f))
    }
}

/**
 * Zeitverlauf mit Achsen: dünne Hilfslinien mit Werten links (und optional rechts in eigener Farbe), senkrechte
 * Linien um Mitternacht, darunter „Tag n“ bzw. die Uhrzeit. [endSpace] hält rechts Platz für Endwerte frei.
 */
@Composable
internal fun TimeChart(
    time: TimeAxis,
    left: ChartAxis,
    modifier: Modifier = Modifier,
    right: ChartAxis? = null,
    height: Dp = 160.dp,
    endSpace: Dp = 0.dp,
    draw: ChartScope.() -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val style = TmType.caption
    val dayText = tr("Tag {day}", "day" to "#")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val axisWidth = AXIS_WIDTH.dp.toPx()
        val plot =
            Plot(
                left = axisWidth,
                top = TOP.dp.toPx(),
                width = size.width - axisWidth - (if (right != null) axisWidth else 0f) - endSpace.toPx(),
                height = size.height - BOTTOM.dp.toPx() - TOP.dp.toPx(),
                span = (time.points - 1).coerceAtLeast(1),
            )
        val y = plot.scale(left)
        val yRight = right?.let(plot::scale) ?: y
        yAxis(left, y, plot, measurer, style, onRight = false)
        right?.let { yAxis(it, yRight, plot, measurer, style, onRight = true) }
        timeAxis(time, plot, measurer, style, dayText)
        ChartScope(this, measurer, style, plot::x, y, yRight, plot.width / plot.span).draw()
    }
}

/** Zeichenfläche ohne die Achsenbeschriftungen. */
private class Plot(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val span: Int,
) {
    fun x(i: Float) = left + width * i / span

    fun scale(axis: ChartAxis): (Float) -> Float =
        { v ->
            top +
                height * (1 - ((v - axis.min) / (axis.max - axis.min)).coerceIn(0f, 1f))
        }
}

/** Hilfslinien mit Werten – links (mit Linie) oder rechts (nur Werte, in der Farbe der Achse). */
private fun DrawScope.yAxis(
    axis: ChartAxis,
    y: (Float) -> Float,
    plot: Plot,
    measurer: TextMeasurer,
    style: TextStyle,
    onRight: Boolean,
) {
    for (tick in axis.ticks) {
        if (!onRight) {
            drawLine(
                TmColors.LineSoft,
                Offset(plot.left, y(tick)),
                Offset(plot.left + plot.width, y(tick)),
                1.dp.toPx(),
            )
        }
        val text = measurer.measure(axis.label(tick), style.copy(color = axis.color))
        val x = if (onRight) plot.left + plot.width + GAP.dp.toPx() else plot.left - text.size.width - GAP.dp.toPx()
        drawText(text, topLeft = Offset(x, y(tick) - text.size.height / 2f))
    }
}

/** Mitternacht als senkrechte Linie, darunter „Tag n“ (mittags) bzw. bei Balken die Uhrzeit je Punkt. */
private fun DrawScope.timeAxis(
    time: TimeAxis,
    plot: Plot,
    measurer: TextMeasurer,
    style: TextStyle,
    dayText: String,
) {
    // Zu eng für jeden Tag? Dann nur jeden zweiten, dritten … beschriften
    val dayWidth = plot.width / plot.span * HOURS_PER_DAY / time.hoursPerPoint
    val step = if (time.clock) 1 else ceil(DAY_LABEL.dp.toPx() / dayWidth).toInt().coerceAtLeast(1)
    for (i in 0 until time.points) {
        val hour = time.firstHour + i * time.hoursPerPoint
        val x = plot.x(i.toFloat())
        if (hour.mod(HOURS_PER_DAY) == 0 &&
            i > 0
        ) {
            drawLine(TmColors.Line, Offset(x, plot.top), Offset(x, plot.top + plot.height), 1.dp.toPx())
        }
        // Bei Balken ist der letzte Punkt nur das rechte Ende des letzten Balkens
        val text = timeLabel(time, hour, dayText, step).takeIf { !time.clock || i < time.points - 1 } ?: continue
        val bold = time.clock && hour.mod(HOURS_PER_DAY) == 0
        val layout = measurer.measure(text, style.copy(fontWeight = if (bold) FontWeight.Bold else null))
        val center = if (time.clock) x + plot.width / plot.span / 2 else x
        val textX = (center - layout.size.width / 2f).coerceIn(plot.left, plot.left + plot.width - layout.size.width)
        drawText(layout, topLeft = Offset(textX, plot.top + plot.height + GAP.dp.toPx()))
    }
}

/** „Tag n“ um Mitternacht (Balken) bzw. mittags (Linien, nur jeder [step]-te Tag), sonst bei Balken die Uhrzeit. */
private fun timeLabel(
    time: TimeAxis,
    hour: Int,
    dayText: String,
    step: Int,
): String? {
    val clock = hour.mod(HOURS_PER_DAY)
    val day = hour.floorDiv(HOURS_PER_DAY) - time.firstDay + 1
    return when {
        time.clock && clock == 0 -> dayText.replace("#", "$day")
        time.clock -> tr("{hour} Uhr", "hour" to clock)
        clock == NOON && day >= 1 && (day - 1) % step == 0 -> dayText.replace("#", "$day")
        else -> null
    }
}

/** Drei Hilfslinien: unten, Mitte, oben. */
internal fun threeTicks(
    min: Float,
    max: Float,
) = listOf(min, (min + max) / 2, max)

private const val AXIS_WIDTH = 40
private const val BOTTOM = 18
private const val TOP = 6
private const val GAP = 4
private const val HOURS_PER_DAY = 24
private const val NOON = 12

/** Platz für „Tag 14“ samt Abstand. */
private const val DAY_LABEL = 52
