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
import androidx.compose.ui.geometry.Rect
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
private const val SIGNAL_BLINKS_PER_SECOND = 1.0
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
    /** Straßenzustand einfärben (grün/gelb/rot). */
    showCondition: Boolean = false,
    /** Meldet die Zoomstufe (Pixel pro Meter), z. B. für die Lautstärke der Geräuschkulisse. */
    onZoom: (Float) -> Unit = {},
    /** Ausschnitt beim Start; ohne Angabe die ganze Stadt. */
    initialCamera: Camera? = null,
) {
    val layers = remember(city) { MapLayers(city) }
    val picker = remember(city) { MapPicker(city) }
    val segmentPaths = remember(city) { SegmentPaths(city) }
    val environmentCache = remember(city) { EnvironmentCache() }
    val effects = remember(city) { TrafficEffects(FlashTracker(), FlowLayer(segmentPaths), AngerClouds()) }
    val textMeasurer = rememberTextMeasurer()
    val clickHandler by rememberUpdatedState(onMapClick)
    val frameTick = rememberFrameTick(onFps)
    val view = remember(city) { ViewState() }
    val camera = view.camera
    LaunchedEffect(camera?.scale) { camera?.let { onZoom(it.scale) } }

    Box(
        modifier
            .fillMaxSize()
            .background(MapStyle.land)
            .onSizeChanged {
                view.width = it.width.toFloat()
                view.height = it.height.toFloat()
                if (view.camera == null && it.width > 0) {
                    view.camera = initialCamera ?: Camera.fit(city.bounds, view.width, view.height)
                }
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
                val nanos = frameTick.value
                val frame = traffic()
                if (currentCamera != null && frame != null) {
                    val overlays = Overlays(environmentLayer, environmentCache, showMoods, showCondition)
                    drawOverlays(currentCamera, frame, segmentPaths, overlays)
                    val viewport = currentCamera.viewport(size.width, size.height)
                    val seconds = nanos / NANOS_PER_SECOND.toDouble()
                    effects.flashes.update(frame.game.cameras, seconds)
                    drawTraffic(currentCamera, frame, picker, textMeasurer, viewport, seconds, effects)
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
    // Eigener Messer für den Hintergrund-Thread: TextMeasurer ist nicht threadsicher
    val labelMeasurer = rememberTextMeasurer()
    var rendered by remember(layers) { mutableStateOf<RenderedMap?>(null) }
    LaunchedEffect(layers, camera, width, height, showStaticSignals) {
        if (camera == null || width < 1f || height < 1f) return@LaunchedEffect
        if (rendered != null) delay(RERENDER_DELAY)
        rendered =
            withContext(Dispatchers.Default) {
                RenderedMap.render(
                    layers,
                    camera,
                    width.toInt(),
                    height.toInt(),
                    density,
                    showStaticSignals,
                    labelMeasurer,
                )
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
internal inline fun DrawScope.inWorld(
    camera: Camera,
    block: DrawScope.() -> Unit,
) {
    withTransform({
        translate(size.width / 2, size.height / 2)
        scale(camera.scale, -camera.scale, pivot = Offset.Zero)
        translate(-camera.centerX, -camera.centerY)
    }, block)
}

/** Zwischenspeicher der Effekte: Blitzer-Blitze, Staufarben und Wutwolken (je Snapshot berechnet). */
internal class TrafficEffects(
    val flashes: FlashTracker,
    val flow: FlowLayer,
    val clouds: AngerClouds,
)

/** Was unter dem Verkehr eingeblendet wird: Umwelt-Ebene, Zufriedenheit, Straßenzustand. */
private class Overlays(
    val environmentLayer: EnvironmentLayer,
    val environmentCache: EnvironmentCache,
    val showMoods: Boolean,
    val showCondition: Boolean,
)

/** Ebenen unter dem Verkehr: Umwelt, Zufriedenheit, Zustand, Baustellen, Umleitungen und Ereignisse. */
private fun DrawScope.drawOverlays(
    camera: Camera,
    frame: TrafficFrame,
    segmentPaths: SegmentPaths,
    overlays: Overlays,
) = inWorld(camera) {
    val layer = overlays.environmentLayer
    if (layer != EnvironmentLayer.NONE) overlays.environmentCache.draw(this, frame.current.environment, layer)
    if (overlays.showMoods) drawMoods(frame.game.zoneMoods, camera.scale)
    val infrastructure = frame.current.infrastructure
    if (overlays.showCondition) drawCondition(segmentPaths, infrastructure, camera.scale)
    drawWorksAndDetours(segmentPaths, infrastructure, camera.scale)
    drawEvents(segmentPaths, frame.current.events, camera.scale)
    drawLimitZones(segmentPaths, frame.game.speedLimits.keys, frame.game.nightLimits.keys, camera.scale)
}

private fun DrawScope.drawTraffic(
    camera: Camera,
    traffic: TrafficFrame,
    picker: MapPicker,
    textMeasurer: TextMeasurer,
    viewport: Rect,
    seconds: Double,
    effects: TrafficEffects,
) = inWorld(camera) {
    effects.flow.draw(this, traffic.current.segmentFlow, camera.scale)
    drawIncidents(traffic.current.incidents, camera.scale)
    drawDamage(traffic.game.maintenance.reports, picker, camera.scale)
    drawExcavators(
        traffic.current.infrastructure.worksSegments
            .map { picker.midpoint(it) },
        camera.scale,
    )
    if (camera.scale >= MapStyle.SIGNAL_MIN_SCALE) {
        drawChargingStations(traffic.current.chargingStations, camera.scale)
        drawParkingLots(traffic.current.parkingLots, camera.scale)
    }
    drawVehicles(traffic, camera.scale, viewport, seconds)
    if (camera.scale >= MapStyle.SIGNAL_MIN_SCALE) {
        val blink = (traffic.current.time * SIGNAL_BLINKS_PER_SECOND).toInt() % 2 == 0
        drawSignals(traffic.current.signalMarkers, traffic.current.signalStates, camera.scale, blink, viewport)
    }
    drawCameras(traffic.current.cameras, camera.scale)
    drawCameraFlashes(traffic.current.cameras, effects.flashes, camera.scale, seconds)
    effects.clouds.draw(this, traffic.current, camera.scale, seconds)
    val signs = speedSigns(traffic.game.speedLimits, traffic.game.nightLimits, picker, camera.scale)
    drawSpeedSigns(signs, camera.scale, textMeasurer)
    val priority = traffic.game.priorityRoads.map { picker.midpoint(it) }
    val crossings = traffic.game.bikeCrossings.map { picker.midpoint(it) }
    drawRoadMarkers(priority, crossings, camera.scale)
}
