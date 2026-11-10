package de.bfi.trafficmaniac.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Strich-Icon im 24er-Raster (SVG-Pfade nach Lucide, ISC-Lizenz). Die Pfade werden einmal geparst. */
class TmIcon(
    vararg paths: String,
) {
    internal val parsed: List<Path> by lazy { paths.map { PathParser().parsePathString(it).toPath() } }
}

/** Zeichnet [icon] in [size] mit Strichstärke 2 (im 24er-Raster) und runden Enden. */
@Composable
fun Icon(
    icon: TmIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 22.dp,
) {
    val description = contentDescription?.let { Modifier.semantics { this.contentDescription = it } } ?: Modifier
    Canvas(modifier.size(size).then(description)) {
        val factor = this.size.minDimension / GRID
        val stroke = Stroke(STROKE * factor, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val color = if (tint == Color.Unspecified) Color.Black else tint
        scale(factor, factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            for (path in icon.parsed) {
                drawPath(
                    path,
                    color,
                    style = Stroke(STROKE, cap = stroke.cap, join = stroke.join),
                )
            }
        }
    }
}

/** Kreis als SVG-Pfad (Lucide beschreibt Kreise als <circle>). */
private fun circle(
    cx: Float,
    cy: Float,
    r: Float,
) = "M${cx - r} ${cy}a$r $r 0 1 0 ${r + r} 0a$r $r 0 1 0 ${-r - r} 0"

/** Die Icons der Oberfläche. */
object Icons {
    val Close = TmIcon("M18 6 6 18", "m6 6 12 12")
    val Check = TmIcon("M20 6 9 17l-5-5")
    val ChevronDown = TmIcon("m6 9 6 6 6-6")
    val ChevronUp = TmIcon("m18 15-6-6-6 6")
    val ChevronRight = TmIcon("m9 18 6-6-6-6")
    val ArrowRight = TmIcon("M5 12h14", "m12 5 7 7-7 7")
    val Plus = TmIcon("M5 12h14", "M12 5v14")
    val Minus = TmIcon("M5 12h14")
    val Play = TmIcon("M6 3 20 12 6 21Z")
    val Pause = TmIcon("M6 4h4v16H6z", "M14 4h4v16h-4z")
    val Menu = TmIcon("M4 12h16", "M4 6h16", "M4 18h16")
    val Save =
        TmIcon(
            "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7",
            "M7 3v4a1 1 0 0 0 1 1h7",
        )
    val Folder =
        TmIcon(
            "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5" +
                "a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2",
        )
    val Award = TmIcon(circle(12f, 8f, 6f), "M15.477 12.89 17 22l-5-3-5 3 1.523-9.11")
    val Sliders =
        TmIcon(
            "M21 4h-7",
            "M10 4H3",
            "M21 12h-9",
            "M8 12H3",
            "M21 20h-5",
            "M12 20H3",
            "M14 2v4",
            "M8 10v4",
            "M16 18v4",
        )
    val Info = TmIcon(circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01")
    val Help = TmIcon(circle(12f, 12f, 10f), "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3", "M12 17h.01")
    val LogOut = TmIcon("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9")
    val Trash =
        TmIcon("M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2")
    val Lock =
        TmIcon(
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    val MapPin =
        TmIcon(
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            circle(12f, 10f, 3f),
        )
    val Layers =
        TmIcon(
            "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9" +
                "a1 1 0 0 0 0-1.83Z",
            "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65",
            "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65",
        )

    // Werkzeuge
    val Eye =
        TmIcon(
            "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
            circle(12f, 12f, 3f),
        )
    val Gauge = TmIcon("m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0")
    val TrafficLight =
        TmIcon(
            "M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z",
            circle(12f, 7f, 1.5f),
            circle(12f, 12f, 1.5f),
            circle(12f, 17f, 1.5f),
        )
    val Barrier =
        TmIcon(
            "M3 6h18a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z",
            "M17 14v7",
            "M7 14v7",
            "M17 3v3",
            "M7 3v3",
            "M10 14 2.3 6.3",
            "m14 6 7.7 7.7",
            "m8 6 8 8",
        )
    val Shield =
        TmIcon(
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 " +
                "6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        )
    val Waves =
        TmIcon(
            "M2 8c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
            "M2 16c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1",
        )
    val Monitor =
        TmIcon("M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8 21h8", "M12 17v4")
    val Ban = TmIcon("M4.9 4.9l14.2 14.2", circle(12f, 12f, 10f))
    val Timer = TmIcon("M10 2h4", "m12 14 3-3", circle(12f, 14f, 8f))
    val Camera =
        TmIcon(
            "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
            circle(12f, 13f, 3f),
        )
    val Zap =
        TmIcon(
            "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63" +
                "l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z",
        )
    val Trailer =
        TmIcon(
            "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2",
            "M15 18H9",
            "M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14",
            circle(17f, 18f, 2f),
            circle(7f, 18f, 2f),
        )
    val Diamond =
        TmIcon(
            "M2.7 10.3a2.41 2.41 0 0 0 0 3.41l7.59 7.59a2.41 2.41 0 0 0 3.41 0l7.59-7.59a2.41 2.41 0 0 0 0-3.41" +
                "l-7.59-7.59a2.41 2.41 0 0 0-3.41 0Z",
        )
    val Bike =
        TmIcon(
            circle(18.5f, 17.5f, 3.5f),
            circle(5.5f, 17.5f, 3.5f),
            circle(15f, 5f, 1f),
            "M12 17.5V14l-3-3 4-3 2 3h2",
        )
    val Detour = TmIcon("m15 14 5-5-5-5", "M4 20v-7a4 4 0 0 1 4-4h12")
    val Eraser =
        TmIcon(
            "m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21",
            "M22 21H7",
            "m5 11 9 9",
        )

    // Ämter und Berichte
    val Scale =
        TmIcon(
            "m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z",
            "m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z",
            "M7 21h10",
            "M12 3v18",
            "M3 7h2c2 0 5-1 7-2 2 1 5 2 7 2h2",
        )
    val HardHat =
        TmIcon(
            "M10 10V5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v5",
            "M14 6a6 6 0 0 1 6 6v3",
            "M4 15v-3a6 6 0 0 1 6-6",
            "M2 15h20v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1z",
        )
    val Parking =
        TmIcon(
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M9 17V7h4a3 3 0 0 1 0 6H9",
        )
    val Wallet =
        TmIcon(
            "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2" +
                "a1 1 0 0 0-1-1",
            "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
        )
    val Users =
        TmIcon(
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            circle(9f, 7f, 4f),
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M16 3.13a4 4 0 0 1 0 7.75",
        )

    /** Klemmbrett: Berichte. */
    val Clipboard =
        TmIcon(
            "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z",
            "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
            "M8 12h8",
            "M8 16h5",
        )

    /** Funkwellen: Lage und Meldungen. */
    val Radio =
        TmIcon(
            "M4.9 19.1C1 15.2 1 8.8 4.9 4.9",
            "M7.8 16.2c-2.3-2.3-2.3-6.1 0-8.5",
            "M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M16.2 7.8c2.3 2.3 2.3 6.1 0 8.5",
            "M19.1 4.9C23 8.8 23 15.1 19.1 19",
        )

    val Calendar =
        TmIcon(
            "M8 2v4",
            "M16 2v4",
            "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M3 10h18",
        )
    val Siren =
        TmIcon(
            "M7 18v-6a5 5 0 1 1 10 0v6",
            "M5 21a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-1a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2z",
            "M21 12h1",
            "M18.5 4.5 18 5",
            "M2 12h1",
            "M12 2v1",
            "m4.929 4.929.707.707",
            "M12 12v6",
        )
}

private const val GRID = 24f
private const val STROKE = 2f
