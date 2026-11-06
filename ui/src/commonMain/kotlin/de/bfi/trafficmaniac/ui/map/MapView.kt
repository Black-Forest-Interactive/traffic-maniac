package de.bfi.trafficmaniac.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.SignalMarkers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

private const val ZOOM_PER_SCROLL_STEP = 1.2f
private const val NANOS_PER_SECOND = 1_000_000_000L
private val RERENDER_DELAY = 150.milliseconds

/**
 * 2D-Draufsicht der Stadt mit Zoom (Mausrad) und Verschieben (Ziehen).
 * Mit [traffic] werden Fahrzeuge und Ampelphasen gezeichnet; [onFps] meldet die Bildrate.
 *
 * [traffic] wird erst beim Zeichnen abgefragt: So muss die Oberfläche nicht bei jedem Simulationsschritt
 * neu aufgebaut werden, sondern nur der Verkehrs-Layer in jedem Bildschirm-Frame neu gezeichnet.
 */
@Composable
fun MapView(
    city: CityMap,
    modifier: Modifier = Modifier,
    traffic: (() -> TrafficFrame?)? = null,
    signalMarkers: SignalMarkers? = null,
    onFps: (Int) -> Unit = {},
) {
    val layers = remember(city) { MapLayers(city) }
    val frameTick = rememberFrameTick(onFps)
    var camera by remember(city) { mutableStateOf<Camera?>(null) }
    var viewportWidth by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0f) }

    Box(
        modifier
            .fillMaxSize()
            .background(MapStyle.land)
            .onSizeChanged {
                viewportWidth = it.width.toFloat()
                viewportHeight = it.height.toFloat()
                if (camera == null && it.width > 0) camera = Camera.fit(city.bounds, viewportWidth, viewportHeight)
            }.pointerInput(city) {
                detectDragGestures { _, drag -> camera = camera?.pan(drag.x, drag.y) }
            }.pointerInput(city) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (event.type == PointerEventType.Scroll && change != null) {
                            val factor = ZOOM_PER_SCROLL_STEP.pow(-change.scrollDelta.y)
                            camera =
                                camera?.zoomAt(
                                    factor,
                                    change.position.x,
                                    change.position.y,
                                    viewportWidth,
                                    viewportHeight,
                                )
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val currentCamera = camera
        val renderedMap = rememberRenderedMap(layers, currentCamera, viewportWidth, viewportHeight, traffic == null)
        Canvas(Modifier.fillMaxSize()) {
            if (currentCamera != null) renderedMap?.draw(this, currentCamera)
        }
        // Verkehr: in jedem Bildschirm-Frame neu, damit die Fahrzeuge flüssig interpoliert werden
        if (traffic != null) {
            Canvas(Modifier.fillMaxSize()) {
                frameTick.value
                val frame = traffic()
                if (currentCamera != null && frame != null) drawTraffic(currentCamera, frame, signalMarkers)
            }
        }
        BasicText(
            text = city.attribution,
            style = TextStyle(color = MapStyle.attribution, fontSize = 11.sp),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .background(MapStyle.attributionBackground)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Rastert die statische Karte im Hintergrund, sobald sich Kamera oder Fenstergröße ändern.
 * Während des Zoomens/Verschiebens wird das alte Bild passend transformiert und erst nach einer kurzen
 * Pause neu gerendert.
 */
@Composable
private fun rememberRenderedMap(
    layers: MapLayers,
    camera: Camera?,
    width: Float,
    height: Float,
    showStaticSignals: Boolean,
): RenderedMap? {
    val density = LocalDensity.current
    var rendered by remember(layers) { mutableStateOf<RenderedMap?>(null) }
    LaunchedEffect(layers, camera, width, height, showStaticSignals) {
        if (camera == null || width < 1f || height < 1f) return@LaunchedEffect
        if (rendered != null) delay(RERENDER_DELAY)
        rendered =
            withContext(Dispatchers.Default) {
                RenderedMap.render(layers, camera, width.toInt(), height.toInt(), density, showStaticSignals)
            }
    }
    return rendered
}

/** Zählt Bildschirm-Frames und meldet einmal pro Sekunde die Bildrate. */
@Composable
private fun rememberFrameTick(onFps: (Int) -> Unit): State<Long> =
    produceState(0L) {
        var frames = 0
        var windowStart = 0L
        while (true) {
            withFrameNanos { now ->
                value = now
                frames++
                if (now - windowStart >= NANOS_PER_SECOND) {
                    if (windowStart > 0) onFps(frames)
                    frames = 0
                    windowStart = now
                }
            }
        }
    }

/** Zeichnet [block] in Weltkoordinaten (Meter, y nach Norden). */
private inline fun DrawScope.inWorld(
    camera: Camera,
    block: DrawScope.() -> Unit,
) {
    withTransform({
        translate(size.width / 2, size.height / 2)
        scale(camera.scale, -camera.scale, pivot = Offset.Zero)
        translate(-camera.centerX, -camera.centerY)
    }, block)
}

internal fun DrawScope.drawStaticMap(
    layers: MapLayers,
    camera: Camera,
    showStaticSignals: Boolean,
) = inWorld(camera) {
    val scale = camera.scale
    drawPath(layers.green, MapStyle.green)
    drawPath(layers.water, MapStyle.water)
    drawPath(layers.buildings, MapStyle.building)
    drawSceneryLines(layers, scale)
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

private fun DrawScope.drawTraffic(
    camera: Camera,
    traffic: TrafficFrame,
    signalMarkers: SignalMarkers?,
) = inWorld(camera) {
    drawVehicles(traffic, camera.scale)
    if (signalMarkers != null && camera.scale >= MapStyle.SIGNAL_MIN_SCALE) {
        drawSignals(signalMarkers, traffic.current.signalStates, camera.scale)
    }
}

private fun DrawScope.drawSceneryLines(
    layers: MapLayers,
    scale: Float,
) {
    val minPixels = MapStyle.MIN_LINE_PIXELS
    drawLine(layers.waterways, MapStyle.water, MapStyle.WATERWAY_WIDTH_METERS, scale, minPixels)
    drawLine(
        layers.minorRoads,
        MapStyle.minorRoadOutline,
        MapStyle.MINOR_ROAD_WIDTH_METERS + MapStyle.OUTLINE_METERS,
        scale,
        minPixels,
    )
    drawLine(layers.minorRoads, MapStyle.minorRoad, MapStyle.MINOR_ROAD_WIDTH_METERS, scale, minPixels)
    drawLine(layers.motorways, MapStyle.motorway, MapStyle.MOTORWAY_WIDTH_METERS, scale, minPixels)
}

/** Simulierte Straßen: erst alle Umrandungen, dann alle Füllungen – so verschmelzen Kreuzungen sauber. */
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
