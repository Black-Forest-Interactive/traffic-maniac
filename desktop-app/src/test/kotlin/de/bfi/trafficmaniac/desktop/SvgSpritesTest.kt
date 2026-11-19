package de.bfi.trafficmaniac.desktop

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SvgSpritesTest {
    private val folder: File =
        createTempDirectory("sprites").toFile().apply {
            resolve("car_red.svg").writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 50">
                  <rect x="5" y="5" width="90" height="40" rx="10" fill="#E63946" stroke="#3B2F2F" stroke-width="4"/>
                </svg>""",
            )
            resolve("broken.svg").writeText("das ist kein SVG")
            resolve("notes.txt").writeText("keine Grafik")
        }

    @Test
    fun rendersSvgAtTheRequestedWidthKeepingTheAspectRatio() {
        val sprites = SvgSprites(folder)
        assertTrue(sprites.has("car_red"))
        val image = assertNotNull(sprites.image("car_red", 64))
        assertEquals(64, image.width)
        assertEquals(32, image.height)
        assertSame(image, sprites.image("car_red", 64), "Zwischengespeichert")
    }

    @Test
    fun missingOrBrokenFilesFallBackToDrawing() {
        val sprites = SvgSprites(folder)
        assertFalse(sprites.has("police"), "Nicht vorhanden")
        assertFalse(sprites.has("notes"), "Nur SVG-Dateien zählen")
        assertNull(sprites.image("broken", 64))
        assertFalse(sprites.has("broken"), "Kaputte Datei wird danach gezeichnet")
    }

    @Test
    fun aMissingFolderMeansNoSprites() {
        val sprites = SvgSprites(File(folder, "gibt-es-nicht"))
        assertEquals(0, sprites.count)
        assertFalse(sprites.has("car_red"))
    }
}
