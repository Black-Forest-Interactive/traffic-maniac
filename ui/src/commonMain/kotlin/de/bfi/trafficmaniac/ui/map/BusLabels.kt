package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Liniennummern auf den Bussen: weißes Schild mit Rahmen in der Linienfarbe, in Bildschirmgröße (lesbar bei jedem
 * Zoom). Jede Nummer wird nur einmal gesetzt und dann wiederverwendet – sonst kostete das bei vielen Bussen in jedem
 * Bild eine Textmessung je Bus.
 */
internal class BusLabels(
    private val measurer: TextMeasurer,
) {
    private val layouts = HashMap<String, TextLayoutResult>()

    /** Zeichnet [line] mittig auf den Bus bei ([x], [y]) – Welt-Koordinaten, [scale] Pixel je Meter. */
    fun draw(
        scope: DrawScope,
        line: String,
        color: Color,
        x: Float,
        y: Float,
        scale: Float,
    ) = with(scope) {
        val text = layouts.getOrPut(line) { measurer.measure(line, STYLE) }
        val width = text.size.width + 2 * PADDING
        val height = text.size.height.toFloat()
        // Weltskalierung (y nach Norden) für das Schild wieder aufheben
        withTransform({
            translate(x, y)
            scale(1 / scale, -1 / scale, pivot = Offset.Zero)
        }) {
            val topLeft = Offset(-width / 2, -height / 2)
            val corner = CornerRadius(height / 2)
            drawRoundRect(Color.White, topLeft, Size(width, height), corner)
            drawRoundRect(color, topLeft, Size(width, height), corner, style = Stroke(BORDER))
            drawText(text, topLeft = Offset(-text.size.width / 2f, -height / 2))
        }
    }

    companion object {
        /** Erst ab dieser Buslänge auf dem Bildschirm (Pixel) – kleiner wäre die Nummer nicht lesbar. */
        const val MIN_BUS_PIXELS = 34f
        private const val PADDING = 4f
        private const val BORDER = 2f
        private val STYLE = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2330))
    }
}
