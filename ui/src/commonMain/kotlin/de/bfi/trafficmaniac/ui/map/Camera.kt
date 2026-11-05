package de.bfi.trafficmaniac.ui.map

import de.bfi.trafficmaniac.city.Bounds
import kotlin.math.min

/**
 * Kamera der 2D-Draufsicht: welcher Weltpunkt (Meter) in der Bildschirmmitte liegt und wie stark gezoomt ist.
 * Weltkoordinaten zeigen mit y nach Norden, Bildschirmkoordinaten mit y nach unten.
 */
data class Camera(
    val centerX: Float,
    val centerY: Float,
    /** Pixel pro Meter. */
    val scale: Float,
) {
    fun worldToScreenX(
        x: Float,
        viewportWidth: Float,
    ): Float = (x - centerX) * scale + viewportWidth / 2

    fun worldToScreenY(
        y: Float,
        viewportHeight: Float,
    ): Float = (centerY - y) * scale + viewportHeight / 2

    fun screenToWorldX(
        x: Float,
        viewportWidth: Float,
    ): Float = (x - viewportWidth / 2) / scale + centerX

    fun screenToWorldY(
        y: Float,
        viewportHeight: Float,
    ): Float = centerY - (y - viewportHeight / 2) / scale

    /** Verschiebt die Karte um die angegebenen Pixel (z. B. beim Ziehen mit der Maus). */
    fun pan(
        dxPixels: Float,
        dyPixels: Float,
    ): Camera = copy(centerX = centerX - dxPixels / scale, centerY = centerY + dyPixels / scale)

    /** Zoomt um [factor], wobei der Weltpunkt unter dem Mauszeiger an derselben Stelle bleibt. */
    fun zoomAt(
        factor: Float,
        screenX: Float,
        screenY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Camera {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val worldX = screenToWorldX(screenX, viewportWidth)
        val worldY = screenToWorldY(screenY, viewportHeight)
        return Camera(
            centerX = worldX - (screenX - viewportWidth / 2) / newScale,
            centerY = worldY + (screenY - viewportHeight / 2) / newScale,
            scale = newScale,
        )
    }

    companion object {
        /** Ganze Stadt bei kleinen Fenstern. */
        const val MIN_SCALE = 0.01f

        /** Etwa ein Auto bildschirmfüllend. */
        const val MAX_SCALE = 50f

        private const val FIT_MARGIN = 0.95f

        /** Kamera, die [bounds] vollständig im Fenster zeigt. */
        fun fit(
            bounds: Bounds,
            viewportWidth: Float,
            viewportHeight: Float,
        ): Camera =
            Camera(
                centerX = bounds.centerX,
                centerY = bounds.centerY,
                scale =
                    (
                        min(
                            viewportWidth / bounds.width,
                            viewportHeight / bounds.height,
                        ) * FIT_MARGIN
                    ).coerceIn(MIN_SCALE, MAX_SCALE),
            )
    }
}
