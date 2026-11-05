package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.ui.map.Camera
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraTest {
    private val width = 800f
    private val height = 600f

    private fun assertClose(
        expected: Float,
        actual: Float,
    ) = assertTrue(abs(expected - actual) < 1e-3f, "expected $expected but was $actual")

    @Test
    fun centerOfWorldIsCenterOfScreen() {
        val camera = Camera(centerX = 100f, centerY = 50f, scale = 2f)

        assertEquals(400f, camera.worldToScreenX(100f, width))
        assertEquals(300f, camera.worldToScreenY(50f, height))
        // Norden ist oben: größeres y in der Welt → kleineres y auf dem Bildschirm
        assertEquals(280f, camera.worldToScreenY(60f, height))
    }

    @Test
    fun zoomKeepsPointUnderCursor() {
        val camera = Camera(centerX = 0f, centerY = 0f, scale = 1f)
        val worldX = camera.screenToWorldX(700f, width)
        val worldY = camera.screenToWorldY(100f, height)

        val zoomed = camera.zoomAt(2f, 700f, 100f, width, height)

        assertEquals(2f, zoomed.scale)
        assertClose(700f, zoomed.worldToScreenX(worldX, width))
        assertClose(100f, zoomed.worldToScreenY(worldY, height))
    }

    @Test
    fun panMovesMapWithTheMouse() {
        val camera = Camera(centerX = 0f, centerY = 0f, scale = 2f).pan(20f, 10f)

        // Ein Weltpunkt, der vorher in der Mitte lag, ist der Maus gefolgt
        assertClose(420f, camera.worldToScreenX(0f, width))
        assertClose(310f, camera.worldToScreenY(0f, height))
    }

    @Test
    fun fitShowsWholeBounds() {
        val camera = Camera.fit(Bounds(-1000f, -500f, 1000f, 500f), width, height)

        assertClose(0.38f, camera.scale)
        assertEquals(0f, camera.centerX)
    }
}
