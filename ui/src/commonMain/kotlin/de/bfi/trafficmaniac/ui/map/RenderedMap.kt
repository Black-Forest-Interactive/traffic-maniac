package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Die statische Karte als fertig gerastertes Bild für eine bestimmte Kamera.
 * Zehntausende Gebäude jedes Frame neu zu zeichnen wäre zu teuer; ein Bild kostet fast nichts.
 */
internal class RenderedMap(
    val image: ImageBitmap,
    val camera: Camera,
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
        withTransform({
            translate(camera.worldToScreenX(worldX, size.width), camera.worldToScreenY(worldY, size.height))
            scale(zoom, zoom, pivot = Offset.Zero)
        }) {
            drawImage(image)
        }
    }

    companion object {
        fun render(
            layers: MapLayers,
            camera: Camera,
            width: Int,
            height: Int,
            density: Density,
            showStaticSignals: Boolean,
        ): RenderedMap {
            val image = ImageBitmap(width, height)
            CanvasDrawScope().draw(
                density,
                LayoutDirection.Ltr,
                Canvas(image),
                Size(width.toFloat(), height.toFloat()),
            ) {
                drawRect(MapStyle.land)
                drawStaticMap(layers, camera, showStaticSignals)
            }
            return RenderedMap(image, camera)
        }
    }
}
