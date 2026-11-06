package de.bfi.trafficmaniac.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.TrafficFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

private const val ZOOM_PER_SCROLL_STEP = 1.2f
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val WALKING_SPEED_KMH = 7
private const val NIGHT_SIGN_OFFSET = 14f
private val RERENDER_DELAY = 150.milliseconds

/**
 * 2D-Draufsicht der Stadt mit Zoom (Mausrad) und Verschieben (Ziehen).
 * Mit [traffic] werden Fahrzeuge und Ampelphasen gezeichnet; [onFps] meldet die Bildrate.
 *
 * [traffic] wird erst beim Zeichnen abgefragt: So muss die Oberfläche nicht bei jedem Simulationsschritt
 * neu aufgebaut werden, sondern nur der Verkehrs-Layer in jedem Bildschirm-Frame neu gezeichnet.
 *
 * [onMapClick] erhält die Weltkoordinaten eines Klicks und die aktuelle Größe eines Pixels in Metern.
 */
@Composable
fun MapView(
    city: CityMap,
    modifier: Modifier = Modifier,
    traffic: (() -> TrafficFrame?)? = null,
    onFps: (Int) -> Unit = {},
    onMapClick: (x: Float, y: Float, metersPerPixel: Float) -> Unit = { _, _, _ -> },
    showMoods: Boolean = false,
    environmentLayer: EnvironmentLayer = EnvironmentLayer.NONE,
) {
    val layers = remember(city) { MapLayers(city) }
    val picker = remember(city) { MapPicker(city) }
    val environmentCache = remember(city) { EnvironmentCache() }
    val textMeasurer = rememberTextMeasurer()
    val clickHandler by rememberUpdatedState(onMapClick)
    val frameTick = rememberFrameTick(onFps)
    val view = remember(city) { ViewState() }
    val camera = view.camera

    Box(
        modifier
            .fillMaxSize()
            .background(MapStyle.land)
            .onSizeChanged {
                view.width = it.width.toFloat()
                view.height = it.height.toFloat()
                if (view.camera == null && it.width > 0) view.camera = Camera.fit(city.bounds, view.width, view.height)
            }.mapGestures(city, view) { x, y, metersPerPixel -> clickHandler(x, y, metersPerPixel) },
    ) {
        val currentCamera = camera
        val renderedMap = rememberRenderedMap(layers, currentCamera, view.width, view.height, traffic == null)
        Canvas(Modifier.fillMaxSize()) {
            if (currentCamera != null) renderedMap?.draw(this, currentCamera)
        }
        // Verkehr: in jedem Bildschirm-Frame neu, damit die Fahrzeuge flüssig interpoliert werden
        if (traffic != null) {
            Canvas(Modifier.fillMaxSize()) {
                frameTick.value
                val frame = traffic()
                if (currentCamera != null && frame != null) {
                    if (environmentLayer != EnvironmentLayer.NONE) {
                        inWorld(
                            currentCamera,
                        ) { environmentCache.draw(this, frame.current.environment, environmentLayer) }
                    }
                    if (showMoods) inWorld(currentCamera) { drawMoods(frame.game.zoneMoods, currentCamera.scale) }
                    drawTraffic(currentCamera, frame, picker, textMeasurer)
                }
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

/** Kamera und Fenstergröße der Karte (beobachtbarer Compose-Zustand). */
private class ViewState {
    var camera by mutableStateOf<Camera?>(null)
    var width by mutableStateOf(0f)
    var height by mutableStateOf(0f)
}

/** Klick (Werkzeug), Ziehen (Verschieben) und Mausrad (Zoom um den Mauszeiger). */
private fun Modifier.mapGestures(
    key: Any,
    view: ViewState,
    onClick: (x: Float, y: Float, metersPerPixel: Float) -> Unit,
): Modifier =
    pointerInput(key) {
        detectTapGestures { position ->
            view.camera?.let {
                onClick(
                    it.screenToWorldX(position.x, view.width),
                    it.screenToWorldY(position.y, view.height),
                    1f / it.scale,
                )
            }
        }
    }.pointerInput(key) {
        detectDragGestures { _, drag -> view.camera = view.camera?.pan(drag.x, drag.y) }
    }.pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull()
                if (event.type == PointerEventType.Scroll && change != null) {
                    val factor = ZOOM_PER_SCROLL_STEP.pow(-change.scrollDelta.y)
                    view.camera =
                        view.camera?.zoomAt(factor, change.position.x, change.position.y, view.width, view.height)
                    change.consume()
                }
            }
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
    picker: MapPicker,
    textMeasurer: TextMeasurer,
) = inWorld(camera) {
    drawIncidents(traffic.current.incidents, camera.scale)
    if (camera.scale >= MapStyle.SIGNAL_MIN_SCALE) drawChargingStations(traffic.current.chargingStations, camera.scale)
    drawVehicles(traffic, camera.scale)
    if (camera.scale >= MapStyle.SIGNAL_MIN_SCALE) {
        drawSignals(traffic.current.signalMarkers, traffic.current.signalStates, camera.scale)
    }
    drawCameras(traffic.current.cameras, camera.scale)
    val signs =
        traffic.game.speedLimits.map { (segment, kmh) ->
            val (x, y) = picker.midpoint(segment)
            SpeedSign(x, y, if (kmh <= WALKING_SPEED_KMH) "S" else "$kmh")
        } +
            traffic.game.nightLimits.map { (segment, kmh) ->
                val (x, y) = picker.midpoint(segment)
                // Nachtschild leicht versetzt, damit es neben einem Tagesschild sichtbar bleibt
                SpeedSign(x + NIGHT_SIGN_OFFSET, y + NIGHT_SIGN_OFFSET, "$kmh☾", night = true)
            }
    drawSpeedSigns(signs, camera.scale, textMeasurer)
    val priority = traffic.game.priorityRoads.map { picker.midpoint(it) }
    val crossings = traffic.game.bikeCrossings.map { picker.midpoint(it) }
    drawRoadMarkers(priority, crossings, camera.scale)
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
