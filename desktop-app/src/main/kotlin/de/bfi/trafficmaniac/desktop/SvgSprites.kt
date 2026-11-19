package de.bfi.trafficmaniac.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.bfi.trafficmaniac.ui.map.SpriteSource
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength
import org.jetbrains.skia.svg.SVGLengthUnit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Lädt SVG-Grafiken aus einem Ordner (Standard: `assets/sprites/`, Dateiname = Grafikname) und rastert sie mit Skia
 * in der angefragten Breite. Fehlt eine Datei oder lässt sie sich nicht lesen, zeichnet das Spiel das Symbol selbst.
 * Gerasterte Bilder werden je Name und Breite zwischengespeichert.
 */
class SvgSprites(
    folder: File,
) : SpriteSource {
    private val files: Map<String, File> =
        folder
            .listFiles { file -> file.extension.equals("svg", ignoreCase = true) }
            .orEmpty()
            .associateBy { it.nameWithoutExtension }
    private val documents = ConcurrentHashMap<String, SVGDOM>()
    private val images = ConcurrentHashMap<Pair<String, Int>, ImageBitmap>()
    private val broken = ConcurrentHashMap.newKeySet<String>()

    /** Wie viele Grafiken gefunden wurden (für die Startmeldung). */
    val count: Int get() = files.size

    override fun has(name: String): Boolean = name in files && name !in broken

    override fun image(
        name: String,
        widthPx: Int,
    ): ImageBitmap? {
        if (!has(name)) return null
        images[name to widthPx]?.let { return it }
        return render(name, widthPx)?.also { images[name to widthPx] = it }
    }

    private fun render(
        name: String,
        widthPx: Int,
    ): ImageBitmap? {
        val dom = document(name) ?: return null
        val root = dom.root
        if (root == null) {
            broken += name
            return null
        }
        val box = root.viewBox
        val aspect = if (box != null && box.width > 0f) box.height / box.width else 1f
        val heightPx = (widthPx * aspect).toInt().coerceAtLeast(1)
        root.width = SVGLength(widthPx.toFloat(), SVGLengthUnit.PX)
        root.height = SVGLength(heightPx.toFloat(), SVGLengthUnit.PX)
        val surface = Surface.makeRasterN32Premul(widthPx, heightPx)
        dom.setContainerSize(widthPx.toFloat(), heightPx.toFloat())
        dom.render(surface.canvas)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    private fun document(name: String): SVGDOM? {
        documents[name]?.let { return it }
        return runCatching { SVGDOM(Data.makeFromBytes(files.getValue(name).readBytes())) }
            .onFailure {
                broken += name
                println("Grafik $name.svg nicht lesbar: ${it.message} – wird gezeichnet")
            }.getOrNull()
            ?.also { documents[name] = it }
    }

    companion object {
        const val DEFAULT_FOLDER = "assets/sprites"
    }
}
