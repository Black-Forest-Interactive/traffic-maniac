package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.RoadClass
import kotlin.math.PI
import kotlin.math.max

/**
 * Die statische Karte im Cartoon-Stil, von hinten nach vorn: Grün und Wasser mit Kontur, Gebäude mit Schlagschatten
 * (ab mittlerem Zoom mit Tusche-Umriss), Nebenstraßen, Hauptstraßen mit dickem dunklem Umriss und – nah dran –
 * gestrichelter Mittellinie, zuletzt die Straßennamen (in Bildschirmgröße, ohne Überlappung).
 */
internal fun DrawScope.drawStaticMap(
    layers: MapLayers,
    camera: Camera,
    showStaticSignals: Boolean,
    textMeasurer: TextMeasurer?,
) {
    inWorld(camera) {
        val scale = camera.scale
        val detailed = scale >= MapStyle.OUTLINE_MIN_SCALE
        drawPath(layers.green, MapStyle.green)
        if (detailed) outline(layers.green, MapStyle.greenOutline, MapStyle.SCENERY_OUTLINE_METERS, scale)
        drawPath(layers.water, MapStyle.water)
        outline(layers.water, MapStyle.waterOutline, MapStyle.SCENERY_OUTLINE_METERS, scale)
        drawWaterways(layers, scale)
        drawBuildings(layers, scale, detailed)
        drawMinorRoads(layers, scale)
        drawRoads(layers, scale)
        if (showStaticSignals && scale >= MapStyle.SIGNAL_MIN_SCALE) {
            drawPoints(
                points = layers.trafficSignals,
                pointMode = PointMode.Points,
                color = MapStyle.trafficSignal,
                strokeWidth = max(2 * MapStyle.SIGNAL_RADIUS_METERS, MapStyle.MIN_SIGNAL_PIXELS / scale),
                cap = StrokeCap.Round,
            )
        }
    }
    if (textMeasurer != null && camera.scale >= MapStyle.LABEL_MIN_SCALE) drawLabels(layers, camera, textMeasurer)
}

private fun DrawScope.drawWaterways(
    layers: MapLayers,
    scale: Float,
) {
    val width = MapStyle.WATERWAY_WIDTH_METERS
    drawLine(layers.waterways, MapStyle.waterOutline, width + 2 * MapStyle.SCENERY_OUTLINE_METERS, scale, 2f)
    drawLine(layers.waterways, MapStyle.water, width, scale, MapStyle.MIN_LINE_PIXELS)
}

/** Gebäude mit Schlagschatten nach Südosten; nah dran zusätzlich mit Tusche-Umriss. */
private fun DrawScope.drawBuildings(
    layers: MapLayers,
    scale: Float,
    detailed: Boolean,
) {
    if (detailed) {
        translate(MapStyle.SHADOW_OFFSET_METERS, -MapStyle.SHADOW_OFFSET_METERS) {
            drawPath(layers.buildings, MapStyle.buildingShadow)
        }
    }
    drawPath(layers.buildings, MapStyle.building)
    if (detailed) outline(layers.buildings, MapStyle.ink, BUILDING_OUTLINE_METERS, scale)
}

private fun DrawScope.drawMinorRoads(
    layers: MapLayers,
    scale: Float,
) {
    val minPixels = MapStyle.MIN_LINE_PIXELS
    val width = MapStyle.MINOR_ROAD_WIDTH_METERS
    drawLine(layers.minorRoads, MapStyle.minorRoadOutline, width + MapStyle.OUTLINE_METERS, scale, minPixels)
    drawLine(layers.minorRoads, MapStyle.minorRoad, width, scale, minPixels)
    drawLine(layers.motorways, MapStyle.ink, MapStyle.MOTORWAY_WIDTH_METERS + 2 * MapStyle.OUTLINE_METERS, scale, 2f)
    drawLine(layers.motorways, MapStyle.motorway, MapStyle.MOTORWAY_WIDTH_METERS, scale, minPixels)
}

/** Simulierte Straßen: erst alle Umrisse, dann alle Füllungen – so verschmelzen Kreuzungen sauber. */
private fun DrawScope.drawRoads(
    layers: MapLayers,
    scale: Float,
) {
    val roadsInOrder =
        MapStyle.roadClassesInDrawOrder.mapNotNull { roadClass ->
            layers.roads[roadClass]?.let {
                roadClass to
                    it
            }
        }
    val outlineMinPixels = MapStyle.MIN_ROAD_PIXELS + 2 * MapStyle.MIN_LINE_PIXELS
    for ((roadClass, path) in roadsInOrder) {
        val outlineWidth = MapStyle.road(roadClass).widthMeters + 2 * MapStyle.OUTLINE_METERS
        drawLine(path, MapStyle.roadOutline, outlineWidth, scale, outlineMinPixels)
    }
    for ((roadClass, path) in roadsInOrder) {
        val style = MapStyle.road(roadClass)
        drawLine(path, style.fill, style.widthMeters, scale, MapStyle.MIN_ROAD_PIXELS)
    }
    if (scale < MapStyle.CENTER_LINE_MIN_SCALE) return
    val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_METERS, DASH_METERS))
    for ((roadClass, path) in roadsInOrder) {
        if (roadClass.ordinal > RoadClass.SECONDARY.ordinal) continue
        drawPath(path, MapStyle.centerLine, style = Stroke(CENTER_LINE_METERS, pathEffect = dash))
    }
}

/** Straßennamen entlang der Straße, lesbar gedreht; Schilder, die sich überlappen würden, entfallen. */
private fun DrawScope.drawLabels(
    layers: MapLayers,
    camera: Camera,
    textMeasurer: TextMeasurer,
) {
    val placed = ArrayList<Rect>()
    val style = TextStyle(color = MapStyle.label, fontSize = LABEL_FONT_SIZE.sp, fontWeight = FontWeight.Bold)
    // Nebenstraßen erst nah dran beschriften; nur, was im Bild liegt
    val shown =
        layers.labels.filter {
            it.roadClass.ordinal <= RoadClass.SECONDARY.ordinal || camera.scale >= MINOR_LABEL_SCALE
        }
    for (label in shown) {
        val x = camera.worldToScreenX(label.x, size.width)
        val y = camera.worldToScreenY(label.y, size.height)
        if (!Rect(0f, 0f, size.width, size.height).contains(Offset(x, y))) continue
        val text = textMeasurer.measure(label.name, style)
        val w = text.size.width.toFloat()
        val h = text.size.height.toFloat()
        val box = Rect(x - w / 2 - LABEL_GAP, y - w / 2 - LABEL_GAP, x + w / 2 + LABEL_GAP, y + w / 2 + LABEL_GAP)
        if (placed.any { it.overlaps(box) }) continue
        placed += box
        // Bildschirm-y zeigt nach unten: Winkel umdrehen und so drehen, dass der Text nie auf dem Kopf steht
        var degrees = (-label.angle * DEGREES_PER_RADIAN).toFloat()
        if (degrees > HALF_TURN / 2) degrees -= HALF_TURN
        if (degrees < -HALF_TURN / 2) degrees += HALF_TURN
        rotate(degrees, pivot = Offset(x, y)) {
            val topLeft = Offset(x - w / 2, y - h / 2)
            drawRoundRect(
                MapStyle.labelHalo,
                topLeft = topLeft - Offset(HALO_PADDING, 0f),
                size =
                    Size(w + 2 * HALO_PADDING, h),
                cornerRadius =
                    CornerRadius(h / 2),
            )
            drawText(text, topLeft = topLeft)
        }
    }
}

private fun DrawScope.outline(
    path: Path,
    color: Color,
    widthMeters: Float,
    scale: Float,
) {
    drawPath(path, color, style = Stroke(max(widthMeters, MapStyle.MIN_LINE_PIXELS / scale), join = StrokeJoin.Round))
}

/** Zeichnet eine Linie mit Breite in Metern, aber nie dünner als [minPixels]. */
private fun DrawScope.drawLine(
    path: Path,
    color: Color,
    widthMeters: Float,
    scale: Float,
    minPixels: Float,
) {
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = max(widthMeters, minPixels / scale), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private const val BUILDING_OUTLINE_METERS = 0.9f
private const val DASH_METERS = 4f
private const val CENTER_LINE_METERS = 0.5f
private const val LABEL_FONT_SIZE = 11f
private const val LABEL_GAP = 6f
private const val HALO_PADDING = 4f
private const val MINOR_LABEL_SCALE = 1.8f
private const val DEGREES_PER_RADIAN = 180.0 / PI
private const val HALF_TURN = 180f
