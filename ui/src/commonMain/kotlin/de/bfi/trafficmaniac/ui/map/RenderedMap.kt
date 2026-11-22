package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Die statische Karte als fertig gerastertes Bild für eine bestimmte Kamera.
 * Zehntausende Gebäude jedes Frame neu zu zeichnen wäre zu teuer; ein Bild kostet fast nichts.
 */
internal class RenderedMap(
    val image: ImageBitmap,
    val camera: Camera,
    /** Mit gezeichneten Ampeln gerendert (ohne Verkehrsebene). */
    val showStaticSignals: Boolean = false,
) {
    /** Zeichnet das Bild so verschoben und skaliert, dass es zur aktuellen [camera] passt. */
    fun draw(
        scope: DrawScope,
        camera: Camera,
    ) = with(scope) {
        val width = image.width.toFloat()
        val height = image.height.toFloat()
        // Wo liegt die linke obere Bildecke in der aktuellen Ansicht?
        val worldX = this@RenderedMap.camera.screenToWorldX(0f, width)
        val worldY = this@RenderedMap.camera.screenToWorldY(0f, height)
        val zoom = camera.scale / this@RenderedMap.camera.scale
        val x = camera.worldToScreenX(worldX, size.width)
        val y = camera.worldToScreenY(worldY, size.height)
        if (abs(zoom - 1f) < SAME_ZOOM) {
            // Gleiche Zoomstufe (Verschieben, Stillstand): auf ganze Pixel runden – dann wird nur kopiert statt
            // jedes Pixel interpoliert, das spart ohne Grafikkarte rund 30 ms pro Frame
            drawImage(image, topLeft = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat()))
            return@with
        }
        withTransform({
            translate(x, y)
            scale(zoom, zoom, pivot = Offset.Zero)
        }) {
            drawImage(image)
        }
    }

    /**
     * Deckt das Bild den Ausschnitt [camera] ([width] × [height]) noch mit Reserve ab? Dann ist kein neues Rendern
     * nötig – beim Verschieben reicht der vorgerenderte Rand. Bei anderer Zoomstufe immer neu (sonst unscharf).
     */
    fun covers(
        camera: Camera,
        width: Float,
        height: Float,
    ): Boolean {
        if (abs(camera.scale / this.camera.scale - 1f) >= SAME_ZOOM) return false
        // Abstand der Mittelpunkte in Bildpixeln; erlaubt ist der halbe Rand
        val dx = abs(camera.centerX - this.camera.centerX) * camera.scale
        val dy = abs(camera.centerY - this.camera.centerY) * camera.scale
        return dx <= (image.width - width) / 2 * REUSE_SHARE && dy <= (image.height - height) / 2 * REUSE_SHARE
    }

    companion object {
        private const val SAME_ZOOM = 1e-4f

        /** Rand um den sichtbaren Ausschnitt, je Seite als Anteil der Breite bzw. Höhe. */
        const val MARGIN = 0.25f

        /** Wie viel des Rands beim Verschieben aufgebraucht werden darf, bevor neu gerendert wird. */
        private const val REUSE_SHARE = 0.5f

        fun render(
            layers: MapLayers,
            camera: Camera,
            width: Int,
            height: Int,
            density: Density,
            showStaticSignals: Boolean,
            textMeasurer: TextMeasurer? = null,
        ): RenderedMap {
            val image = ImageBitmap(width, height)
            CanvasDrawScope().draw(
                density,
                LayoutDirection.Ltr,
                Canvas(image),
                Size(width.toFloat(), height.toFloat()),
            ) {
                drawRect(MapStyle.land)
                drawStaticMap(layers, camera, showStaticSignals, textMeasurer)
            }
            return RenderedMap(image, camera, showStaticSignals)
        }
    }
}
