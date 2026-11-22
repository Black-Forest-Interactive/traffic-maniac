package de.bfi.trafficmaniac.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clipToBounds
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
import de.bfi.trafficmaniac.city.Bounds
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.TrafficFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val ZOOM_PER_SCROLL_STEP = 1.2f
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val SIGNAL_BLINKS_PER_SECOND = 1.0
private val RERENDER_DELAY = 150.milliseconds
private const val FOCUS_SCALE = 3f
private const val FLIGHT_SECONDS = 0.5
private const val EASE_POWER = 3f
private const val PING_SECONDS = 2.5
private const val PING_RINGS = 3
private const val PING_SPEED = 1.2
private const val PING_MIN_RADIUS = 8f
private const val PING_GROWTH = 50f
private const val PING_STROKE = 4f

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
    /** Ausschnitt beim Start als Bereich, der ganz zu sehen sein soll (statt [initialCamera]). */
    initialBounds: Bounds? = null,
    /** "Zeig mir das": Die Karte fliegt zu diesem Ort und markiert ihn kurz. */
    focus: MapFocus? = null,
    /** Fertige Grafiken (z. B. aus `assets/sprites/`); fehlende werden gezeichnet. */
    sprites: SpriteSource = NoSprites,
    /** Zoom-Knopf: Jede neue Instanz zoomt um ihren Faktor um die Bildmitte. */
    zoom: MapZoom? = null,
    /** Straßennetz-Modus (F4): Knoten, Abschnitte und Strecken über der Karte; `null` = aus. */
    network: NetworkDebug? = null,
    /** Im Straßennetz-Modus angeklickter Knoten bzw. Abschnitt. */
    networkPick: NetworkPick? = null,
    /** Geplante, noch nicht gebaute Maßnahmen als Vorschau. */
    plan: PlanPreview? = null,
    /** Weitere Ebenen (Dichte, Buslinien, Geld) und was eingeblendet ist. */
    extras: MapExtras = MapExtras(),
) {
    val layers = remember(city) { MapLayers(city) }
    val picker = remember(city) { MapPicker(city) }
    val segmentPaths = remember(city) { SegmentPaths(city) }
    val environmentCache = remember(city) { EnvironmentCache() }
    val effects = remember(city, sprites) { TrafficEffects(FlowLayer(segmentPaths), sprites) }
    val transitLines = remember(city) { TransitLines(city) }
    val textMeasurer = rememberTextMeasurer()
    val clickHandler by rememberUpdatedState(onMapClick)
    val frameTick = rememberFrameTick(onFps)
    val view = remember(city) { ViewState() }
    val camera = view.camera
    LaunchedEffect(camera?.scale) { camera?.let { onZoom(it.scale) } }
    val ping = remember { longArrayOf(0L) }
    LaunchedEffect(focus) { focus?.let { flyTo(view, it, ping) } }
    ZoomRequests(view, zoom)

    // Abschneiden am Rand: sonst malt der Verkehrs-Layer Autos über Kopfleiste und Seitenleiste
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MapStyle.land)
            .onSizeChanged {
                view.width = it.width.toFloat()
                view.height = it.height.toFloat()
                if (view.camera == null && it.width > 0) {
                    view.camera = initialCamera ?: Camera.fit(initialBounds ?: city.bounds, view.width, view.height)
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
            TrafficCanvas(
                traffic,
                currentCamera,
                TrafficDraw(
                    Overlays(environmentLayer, environmentCache, showMoods, showCondition, sprites, extras),
                    segmentPaths,
                    picker,
                    effects,
                    transitLines,
                    textMeasurer,
                ),
                frameTick,
                focus,
                ping,
            )
            SnowfallCanvas(traffic, frameTick)
        }
        if (plan != null && currentCamera != null) {
            Canvas(Modifier.fillMaxSize()) { drawPlan(plan, currentCamera, picker, segmentPaths, textMeasurer) }
        }
        if (network != null) NetworkCanvas(network, currentCamera, textMeasurer, networkPick, traffic)
        Attribution(city.attribution, Modifier.align(Alignment.BottomEnd))
    }
}

/** Was der Verkehrs-Layer zum Zeichnen braucht. */
private class TrafficDraw(
    val overlays: Overlays,
    val segmentPaths: SegmentPaths,
    val picker: MapPicker,
    val effects: TrafficEffects,
    val transitLines: TransitLines,
    val textMeasurer: TextMeasurer,
)

/** Verkehr: in jedem Bildschirm-Frame neu, damit die Fahrzeuge flüssig interpoliert werden. */
@Composable
private fun TrafficCanvas(
    traffic: () -> TrafficFrame?,
    currentCamera: Camera?,
    draw: TrafficDraw,
    frameTick: State<Long>,
    focus: MapFocus?,
    ping: LongArray,
) {
    Canvas(Modifier.fillMaxSize()) {
        val nanos = frameTick.value
        val frame = traffic()
        val drawStart = TimeSource.Monotonic.markNow()
        if (currentCamera != null && frame != null) {
            val overlays = draw.overlays
            val extras = overlays.extras
            // Buslinien: Grundkarte dämpfen, damit die Linien lesbar sind
            if (extras.transit) drawVeil()
            val segmentPaths = draw.segmentPaths
            val picker = draw.picker
            val effects = draw.effects
            val textMeasurer = draw.textMeasurer
            drawOverlays(currentCamera, frame, segmentPaths, overlays)
            val viewport = currentCamera.viewport(size.width, size.height)
            val seconds = nanos / NANOS_PER_SECOND.toDouble()
            effects.update(frame, picker, seconds)
            drawTraffic(currentCamera, frame, picker, textMeasurer, viewport, seconds, effects, extras)
            if (extras.transit) {
                inWorld(currentCamera) {
                    draw.transitLines.draw(
                        this,
                        frame.current.transit.lines,
                        currentCamera.scale,
                        extras.transitStyle,
                        textMeasurer,
                    )
                }
            }
            inWorld(currentCamera) { effects.drawPops(this, textMeasurer, currentCamera.scale, seconds) }
            focus?.let { drawPing(currentCamera, it, (nanos - ping[0]) / NANOS_PER_SECOND.toDouble()) }
            MapDrawTime.record(drawStart.elapsedNow().inWholeMicroseconds)
        }
    }
}

/** Fallende Flocken über der Karte, solange es schneit. */
@Composable
private fun SnowfallCanvas(
    traffic: () -> TrafficFrame?,
    frameTick: State<Long>,
) {
    Canvas(Modifier.fillMaxSize()) {
        val seconds = frameTick.value / NANOS_PER_SECOND.toDouble()
        if (traffic()?.current?.weather?.snowing == true) drawSnowfall(seconds)
    }
}

/** Straßennetz-Modus über allem anderen; die Ampel-Positionen kommen aus dem aktuellen Simulationsschritt. */
@Composable
private fun NetworkCanvas(
    network: NetworkDebug,
    camera: Camera?,
    textMeasurer: TextMeasurer,
    pick: NetworkPick?,
    traffic: (() -> TrafficFrame?)?,
) {
    Canvas(Modifier.fillMaxSize()) {
        if (camera != null) drawNetwork(network, camera, textMeasurer, pick, traffic?.invoke()?.current?.signalMarkers)
    }
}

/** OSM-Hinweis unten rechts, halbtransparent hinterlegt. */
@Composable
private fun Attribution(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = TextStyle(color = MapStyle.attribution, fontSize = 12.sp),
        modifier =
            modifier
                .background(MapStyle.attributionBackground, RoundedCornerShape(topStart = 6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Zoomt bei jedem neuen [zoom] um die Bildmitte. */
@Composable
private fun ZoomRequests(
    view: ViewState,
    zoom: MapZoom?,
) {
    LaunchedEffect(zoom) {
        val request = zoom ?: return@LaunchedEffect
        view.camera = view.camera?.zoomAt(request.factor, view.width / 2, view.height / 2, view.width, view.height)
    }
}

/** Zoom um [factor] (größer 1 = näher heran). Jede neue Instanz zoomt erneut. */
class MapZoom(
    val factor: Float,
)

/** Ziel für "zeig mir das" in Weltkoordinaten. Jede neue Instanz löst einen neuen Flug aus. */
class MapFocus(
    val x: Float,
    val y: Float,
)

/** Fliegt in einer halben Sekunde zum Ziel, zoomt mindestens auf Straßenebene und merkt sich die Ankunftszeit. */
private suspend fun flyTo(
    view: ViewState,
    focus: MapFocus,
    ping: LongArray,
) {
    val from = view.camera ?: return
    val toScale = max(from.scale, FOCUS_SCALE)
    val start = withFrameNanos { it }
    while (true) {
        val now = withFrameNanos { it }
        val progress = ((now - start) / (FLIGHT_SECONDS * NANOS_PER_SECOND)).toFloat().coerceIn(0f, 1f)
        val t = 1 - (1 - progress).pow(EASE_POWER)
        view.camera =
            Camera(
                from.centerX + (focus.x - from.centerX) * t,
                from.centerY + (focus.y - from.centerY) * t,
                from.scale * (toScale / from.scale).pow(t),
            )
        if (progress >= 1f) break
    }
    ping[0] = withFrameNanos { it }
}

/** Pulsierende Ringe an der Zielstelle, zweieinhalb Sekunden lang. */
private fun DrawScope.drawPing(
    camera: Camera,
    focus: MapFocus,
    age: Double,
) {
    if (age !in 0.0..PING_SECONDS) return
    val center = Offset(camera.worldToScreenX(focus.x, size.width), camera.worldToScreenY(focus.y, size.height))
    for (ring in 0 until PING_RINGS) {
        val phase = ((age * PING_SPEED + ring.toDouble() / PING_RINGS) % 1.0).toFloat()
        val alpha = (1 - phase) * (1 - (age / PING_SECONDS).toFloat())
        drawCircle(
            MapStyle.trafficSignal.copy(alpha = alpha),
            PING_MIN_RADIUS + phase * PING_GROWTH,
            center,
            style = Stroke(PING_STROKE),
        )
    }
}

/** Kamera und Fenstergröße der Karte (beobachtbarer Compose-Zustand). */
private class ViewState {
    var camera by mutableStateOf<Camera?>(null)
    var width by mutableStateOf(0f)
    var height by mutableStateOf(0f)
}

/** Klick/Tippen (Werkzeug), Ziehen (Verschieben), Mausrad und zwei Finger (Zoom um Mauszeiger bzw. Fingermitte). */
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
        detectTransformGestures { centroid, pan, zoom, _ ->
            view.camera =
                view.camera
                    ?.pan(pan.x, pan.y)
                    ?.let { if (zoom == 1f) it else it.zoomAt(zoom, centroid.x, centroid.y, view.width, view.height) }
        }
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
        // Beim Verschieben reicht oft der vorgerenderte Rand – dann gar nicht neu rendern
        if (rendered?.covers(camera, width, height) == true && rendered?.showStaticSignals == showStaticSignals) {
            return@LaunchedEffect
        }
        if (rendered != null) delay(RERENDER_DELAY)
        rendered =
            withContext(Dispatchers.Default) {
                // Mit Rand rendern: Beim Verschieben ist das Nachbargebiet schon da
                RenderedMap.render(
                    layers,
                    camera,
                    (width * (1 + 2 * RenderedMap.MARGIN)).toInt(),
                    (height * (1 + 2 * RenderedMap.MARGIN)).toInt(),
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
                    if (windowStart > 0) {
                        onFps(frames)
                        MapDrawTime.framesPerSecond = frames
                    }
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
    val flow: FlowLayer,
    val sprites: SpriteSource,
) {
    val flashes = FlashTracker()
    val pops = MoneyPops()
    val puffs = BuildPuffs()

    /** Liniennummern auf den Bussen – mit dem Textmesser der Karte, siehe [busLabels]. */
    private var labels: BusLabels? = null

    fun busLabels(measurer: TextMeasurer): BusLabels = labels ?: BusLabels(measurer).also { labels = it }

    val clouds = AngerClouds()

    /** Je Bild: neue Blitze, Einnahmen und Bauten erkennen. */
    fun update(
        frame: TrafficFrame,
        picker: MapPicker,
        seconds: Double,
    ) {
        flashes.update(frame.game.cameras, seconds)
        pops.update(frame.game.cameras, frame.current.cameras, seconds)
        puffs.update(frame.game, frame.current.cameras, picker, seconds)
    }

    /** Staubwolken und aufsteigende Beträge über allem anderen. */
    fun drawPops(
        scope: DrawScope,
        textMeasurer: TextMeasurer,
        scale: Float,
        seconds: Double,
    ) {
        puffs.draw(scope, scale, seconds)
        pops.draw(scope, textMeasurer, scale, seconds)
    }
}

/** Was unter dem Verkehr eingeblendet wird: Umwelt-Ebene, Zufriedenheit, Straßenzustand. */
private class Overlays(
    val environmentLayer: EnvironmentLayer,
    val environmentCache: EnvironmentCache,
    val showMoods: Boolean,
    val showCondition: Boolean,
    val sprites: SpriteSource,
    val extras: MapExtras,
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
    if (overlays.showMoods) drawMoods(frame.game.zoneMoods, camera.scale, overlays.sprites)
    val infrastructure = frame.current.infrastructure
    if (overlays.showCondition) drawCondition(segmentPaths, infrastructure, camera.scale)
    val extras = overlays.extras
    if (extras.density) drawDensity(segmentPaths, frame.current.segmentVolume, camera.scale)
    drawGreenWaves(segmentPaths, frame.game.greenWaves, camera.scale)
    drawBikeStreets(segmentPaths, frame.game.bikeStreets, camera.scale)
    drawBusLanes(segmentPaths, frame.game.busLanes, camera.scale)
    drawWorksAndDetours(segmentPaths, infrastructure, camera.scale)
    drawEvents(segmentPaths, frame.current.events, camera.scale, overlays.sprites)
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
    extras: MapExtras,
) = inWorld(camera) {
    if (!extras.hidesFlow) effects.flow.draw(this, traffic.current.segmentFlow, camera.scale)
    // Schnee über den Staufarben – sonst sähe man in der Gesamtansicht nichts davon
    drawSnow(effects.flow.paths, traffic.current.weather, camera.scale)
    if (extras.incidents) {
        drawIncidents(traffic.current.incidents, camera.scale, effects.sprites)
        drawDamage(traffic.game.maintenance.reports, picker, camera.scale, effects.sprites)
    }
    if (extras.assignments) drawAssignments(traffic.game.assignments, camera.scale, effects.sprites)
    drawInfrastructureIcons(traffic.current.infrastructure, picker, camera.scale, effects.sprites)
    drawExcavators(
        traffic.current.infrastructure.worksSegments
            .map { picker.midpoint(it) },
        camera.scale,
    )
    if (camera.scale >= MapStyle.SIGNAL_MIN_SCALE) {
        drawChargingStations(traffic.current.chargingStations, camera.scale, effects.sprites)
        drawParkingLots(traffic.current.parkingLots, camera.scale, effects.sprites)
        if (!extras.transit) drawTransitStops(picker.city.transitStops, camera.scale, viewport)
        drawBusPriority(traffic.game.busPriorityNodes.map { picker.city.roads.nodes[it] }, camera.scale)
    }
    drawVehicles(traffic, camera.scale, viewport, seconds, effects.sprites, effects.busLabels(textMeasurer))
    // In der Ebene „Buslinien“ keine Ampelpunkte – dort gibt es nur einen Punkt-Stil: die Haltestelle
    if (extras.signs && !extras.transit && camera.scale >= MapStyle.SIGNAL_MIN_SCALE) {
        val blink = (traffic.current.time * SIGNAL_BLINKS_PER_SECOND).toInt() % 2 == 0
        drawSignals(
            traffic.current.signalMarkers,
            traffic.current.signalStates,
            camera.scale,
            blink,
            viewport,
            effects.sprites,
        )
    }
    if (extras.cameras) {
        drawCameras(traffic.current.cameras, camera.scale, effects.sprites)
        drawCameraFlashes(traffic.current.cameras, effects.flashes, camera.scale, seconds, effects.sprites)
    }
    effects.clouds.draw(this, traffic.current, camera.scale, seconds, effects.sprites)
    if (extras.signs) {
        val signs =
            avoidDisplays(
                speedSigns(traffic.game.speedLimits, traffic.game.nightLimits, picker, camera.scale),
                traffic.current.cameras,
                camera.scale,
            )
        drawSpeedSigns(signs, camera.scale, textMeasurer, effects.sprites)
        val priority = traffic.game.priorityRoads.map { picker.midpoint(it) }
        val crossings = traffic.game.bikeCrossings.map { picker.midpoint(it) }
        drawRoadMarkers(priority, crossings, camera.scale, effects.sprites)
    }
    if (extras.cameras) drawDisplays(traffic.current.cameras, camera.scale, textMeasurer)
    if (extras.money) drawMoney(traffic.game.cameras, picker, camera.scale)
}

/**
 * Wie lange das Zeichnen des Verkehrs (Überlagerungen, Fahrzeuge, Effekte) je Bild dauert, gleitend gemittelt – für
 * die Entwickler-Anzeige. Gemessen wird das Aufzeichnen der Zeichenbefehle, nicht die Grafikkarte.
 */
object MapDrawTime {
    var millis = 0.0
        private set

    /** Zuletzt gemessene Bildrate (für die Statistik). */
    var framesPerSecond = 0
        internal set

    internal fun record(micros: Long) {
        millis = millis * (1 - BLEND) + micros / MICROS_PER_MILLI * BLEND
    }

    private const val BLEND = 0.05
    private const val MICROS_PER_MILLI = 1_000.0
}
