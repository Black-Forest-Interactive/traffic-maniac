package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.bfi.trafficmaniac.sim.VehicleRole
import de.bfi.trafficmaniac.sim.VehicleType
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Fertige Grafiken (z. B. SVGs aus `assets/sprites/`), gerastert in der gewünschten Pixelbreite.
 * Gibt es eine Grafik nicht, liefert [image] `null` – dann zeichnet das Spiel das Symbol wie bisher selbst.
 */
interface SpriteSource {
    /** Grafik [name] (Dateiname ohne Endung), gerastert auf etwa [widthPx] Pixel Breite. */
    fun image(
        name: String,
        widthPx: Int,
    ): ImageBitmap?

    /** Gibt es die Grafik überhaupt? (ohne sie zu rastern) */
    fun has(name: String): Boolean
}

/** Keine Grafiken: alles wird gezeichnet. */
object NoSprites : SpriteSource {
    override fun image(
        name: String,
        widthPx: Int,
    ): ImageBitmap? = null

    override fun has(name: String) = false
}

/**
 * Zeichnet Grafik [name] mittig bei [center] in Weltkoordinaten, [width] × [height] Meter groß, um [angle] (Bogenmaß,
 * 0 = nach Osten) gedreht. Liefert `false`, wenn es die Grafik nicht gibt – der Aufrufer zeichnet dann selbst.
 *
 * Gerastert wird in Zweierpotenz-Stufen der Bildschirmgröße, damit der Zwischenspeicher klein bleibt und es trotzdem
 * scharf aussieht.
 */
internal fun DrawScope.drawSprite(
    sprites: SpriteSource,
    name: String,
    center: Offset,
    width: Float,
    height: Float,
    scale: Float,
    angle: Float = 0f,
    alpha: Float = 1f,
): Boolean {
    if (!sprites.has(name)) return false
    val pixels = width * scale
    val bucket = 2.0.pow(ceil(log2(pixels.coerceIn(MIN_PIXELS, MAX_PIXELS).toDouble()))).roundToInt()
    val image = sprites.image(name, bucket) ?: return false
    withTransform({
        translate(center.x, center.y)
        rotate((angle * DEGREES_PER_RADIAN).toFloat(), pivot = Offset.Zero)
        // Weltkoordinaten zeigen nach Norden, Bilder nach unten: zurückspiegeln
        scale(width / image.width, -height / image.height, pivot = Offset.Zero)
    }) {
        drawImage(
            image,
            dstOffset = IntOffset(-image.width / 2, -image.height / 2),
            dstSize = IntSize(image.width, image.height),
            alpha = alpha,
        )
    }
    return true
}

/** Dateiname der Fahrzeuggrafik, z. B. `car_red`, `car_electric`, `ambulance`, `fire_engine`. */
internal fun vehicleSpriteName(
    type: VehicleType,
    role: VehicleRole,
    color: Color,
    electric: Boolean,
): String =
    when (role) {
        VehicleRole.AMBULANCE -> {
            "ambulance"
        }

        VehicleRole.POLICE -> {
            "police"
        }

        VehicleRole.FIRE_BRIGADE -> {
            "fire_engine"
        }

        VehicleRole.TOW_TRUCK -> {
            "tow_truck"
        }

        VehicleRole.SNOW_PLOUGH -> {
            "snow_plough"
        }

        VehicleRole.STREET_SERVICE -> {
            "road_service"
        }

        VehicleRole.BIKE_CORSO -> {
            "bike_corso"
        }

        VehicleRole.HEAVY_TRANSPORT -> {
            "heavy_transport"
        }

        VehicleRole.PRIVATE -> {
            when (type) {
                VehicleType.VAN -> "van"
                VehicleType.TRUCK -> "truck"
                else -> if (electric) "car_electric" else "car_" + nearestCarColor(color)
            }
        }
    }

/** Die Autofarben der Grafiken; die Fahrzeugfarbe wird der nächstliegenden zugeordnet. */
private val CAR_COLORS =
    listOf(
        "red" to Color(0xFFE63946),
        "blue" to Color(0xFF457B9D),
        "yellow" to Color(0xFFFFBE0B),
        "green" to Color(0xFF2A9D8F),
        "purple" to Color(0xFF8338EC),
        "white" to Color(0xFFE9ECEF),
    )

private fun nearestCarColor(color: Color): String =
    CAR_COLORS
        .minBy { (_, c) ->
            val dr = c.red - color.red
            val dg = c.green - color.green
            val db = c.blue - color.blue
            dr * dr + dg * dg + db * db
        }.first

private const val MIN_PIXELS = 16f
private const val MAX_PIXELS = 512f
private const val DEGREES_PER_RADIAN = 180.0 / PI
