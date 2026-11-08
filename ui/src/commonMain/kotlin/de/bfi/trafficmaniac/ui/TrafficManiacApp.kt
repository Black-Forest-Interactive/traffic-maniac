package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameMessage
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.SpeedCamera
import de.bfi.trafficmaniac.ui.map.EnvironmentLayer
import de.bfi.trafficmaniac.ui.map.MapFocus
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.MapView
import de.bfi.trafficmaniac.ui.map.MapZoom
import de.bfi.trafficmaniac.ui.map.NetworkDebug
import de.bfi.trafficmaniac.ui.map.NetworkPick
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val BackgroundColor = TmColors.Ink
private val HUD_REFRESH = 250.milliseconds
private const val PICK_RADIUS_PIXELS = 14f
private const val MIN_PICK_RADIUS_METERS = 6f
private const val MILLIS_PER_SECOND = 1_000.0
private const val MONEY_RAIN_WIDTH = 360
private const val MONEY_RAIN_HEIGHT = 260
private const val INSPECTOR_MAX_HEIGHT = 560

/** Zustand der Spieloberfläche: Werkzeug, offenes Fenster, Tutorial-Schritt, Kartenansicht, Flug- und Zoomziel. */
internal class HudState(
    tutorial: Boolean,
) {
    var fps by mutableStateOf(0)
    var selection by mutableStateOf(ToolSelection())
    var panel by mutableStateOf(SidePanel.NONE)
    var step by mutableStateOf(if (tutorial) TutorialStep.WELCOME else null)
    val toggles = MapToggles()
    var focus by mutableStateOf<MapFocus?>(null)
    var zoom by mutableStateOf<MapZoom?>(null)

    /** Im Straßennetz-Modus (F4) angeklickter Knoten bzw. Abschnitt. */
    var networkPick by mutableStateOf<NetworkPick?>(null)

    /** Aktuelle Zoomstufe der Karte – für die Lautstärke der Geräuschkulisse. */
    val zoomLevel = floatArrayOf(1f)

    /** Im Tutorial nur die freigeschalteten Werkzeuge. */
    val availableTools: Set<Tool> get() = step?.unlocked ?: Tool.entries.toSet()

    fun togglePanel(target: SidePanel) {
        panel = if (panel == target) SidePanel.NONE else target
    }

    fun show(place: MapPlace) {
        focus = MapFocus(place.x, place.y)
    }
}

/** Wurzel der Spieloberfläche. Ohne Stadt wird ein Hinweis angezeigt, wie man sie erzeugt. */
@Composable
fun TrafficManiacApp(
    city: CityMap?,
    missingCityMessage: String = "",
    traffic: StateFlow<TrafficFrame?>? = null,
    rules: GameRules = GameRules(),
    onCommand: (GameCommand) -> Unit = {},
    onContinueDay: () -> Unit = {},
    sound: SoundBoard = NoSound,
    /** Fertige Grafiken für Karte und Fahrzeuge; fehlende werden gezeichnet. */
    sprites: SpriteSource = NoSprites,
    /** Zurück ins Startmenü (Knopf oben rechts und nach der Wertung); `null` = kein Menü. */
    onMenu: (() -> Unit)? = null,
    /** Spiel speichern (Knopf oben rechts); `null` = kein Speichern. */
    onSave: (() -> Unit)? = null,
    /** Geführte Schritte, die die Werkzeuge nach und nach freischalten. */
    tutorial: Boolean = false,
    /** Zeitraffer wählen (0 = Pause); `null` blendet die Tempo-Knöpfe aus. */
    onSpeed: ((Int) -> Unit)? = null,
    /** Tasten aus dem Fenster: Werkzeuge und Entwickler-Anzeige. */
    input: HudInput = remember { HudInput() },
) {
    if (city == null) {
        MissingCity(missingCityMessage)
        return
    }
    val hud = remember(tutorial) { HudState(tutorial) }
    val picker = remember(city) { MapPicker(city) }
    val command: (GameCommand) -> Unit = {
        sound.play(SoundEffect.CLICK)
        onCommand(it)
    }
    LaunchedEffect(input.toolRequest) {
        input.toolRequest?.let { if (it in hud.availableTools) hud.selection = hud.selection.copy(tool = it) }
        input.toolRequest = null
    }
    LaunchedEffect(input.panelRequest) {
        input.panelRequest?.let { hud.panel = it }
        input.panelRequest = null
    }
    val hudFrame by rememberHudFrame(traffic, sound, hud.zoomLevel)
    TmTheme {
        Box(Modifier.fillMaxSize().background(TmColors.Ground)) {
            Column(Modifier.fillMaxSize()) {
                hudFrame?.let { HudTopBar(it, TopBarActions(onSpeed, onSave, onMenu)) }
                Row(Modifier.weight(1f)) {
                    HudRail(hud.panel, hud::togglePanel, hudFrame?.current?.incidents?.size ?: 0)
                    MapArea(
                        city,
                        traffic,
                        hudFrame,
                        hud,
                        picker,
                        rules,
                        command,
                        sprites,
                        input,
                        Modifier.weight(1f),
                    )
                    hudFrame?.let { frame ->
                        if (hud.panel != SidePanel.NONE) {
                            val context =
                                PanelContext(frame, rules, command, picker, hud::show, sprites) {
                                    hud.panel =
                                        SidePanel.NONE
                                }
                            SidePanelSlot(hud.panel, context)
                        }
                    }
                }
            }
            hudFrame?.let { EndOfDayOverlays(it, onContinueDay, onMenu, sprites) }
        }
    }
}

/** Die Karte mit allem, was darüber schwebt: Ansicht, Lage und Meldungen, Tutorial, Zoom, Werkzeug-Dock. */
@Composable
private fun MapArea(
    city: CityMap,
    traffic: StateFlow<TrafficFrame?>?,
    hudFrame: TrafficFrame?,
    hud: HudState,
    picker: MapPicker,
    rules: GameRules,
    command: (GameCommand) -> Unit,
    sprites: SpriteSource,
    input: HudInput,
    modifier: Modifier = Modifier,
) {
    val networkData = remember(city) { lazy { NetworkDebug(city) } }
    val network = if (input.network) networkData.value else null
    Box(modifier.fillMaxSize()) {
        // Die Karte ohne UI-Skalierung: Zoom, Linien und Sprites bleiben gleich, nur die Bedienung wächst
        Unscaled {
            MapView(
                focus = hud.focus,
                zoom = hud.zoom,
                sprites = sprites,
                city = city,
                traffic = traffic?.let { flow -> { flow.value } },
                onFps = { hud.fps = it },
                showMoods = hud.toggles.showMoods,
                environmentLayer = hud.toggles.environmentLayer,
                showCondition = hud.toggles.showCondition || hud.selection.tool == Tool.DETOUR,
                onZoom = { hud.zoomLevel[0] = it },
                onMapClick = { x, y, metersPerPixel ->
                    val radius = max(MIN_PICK_RADIUS_METERS, PICK_RADIUS_PIXELS * metersPerPixel)
                    if (network != null) {
                        hud.networkPick = network.pick(x, y, radius)
                    } else {
                        commandFor(hud.selection, picker, traffic?.value, x, y, radius)?.let(command)
                    }
                },
                network = network,
                networkPick = hud.networkPick,
            )
        }
        MapViewSwitcher(hud.toggles, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
        ZoomControls({ hud.zoom = MapZoom(it) }, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        hudFrame?.let { frame ->
            HudFeed(frame, hud::show, Modifier.align(Alignment.TopStart).padding(14.dp))
            val game = frame.game
            val income = game.fineIncome + game.parkingOffice.feeIncome + game.parkingOffice.ticketIncome
            MoneyRain(
                income,
                Modifier.align(Alignment.TopCenter).size(MONEY_RAIN_WIDTH.dp, MONEY_RAIN_HEIGHT.dp),
                sprites,
            )
            hud.step?.let { s ->
                TutorialPanel(s, game, { hud.step = it }, Modifier.align(Alignment.BottomStart).padding(bottom = 14.dp))
            }
            DeveloperOverlays(frame, hud, network, input)
            val dock = ToolDockState(hud.selection, hud.availableTools, game.idleTrailers) { hud.selection = it }
            ToolDock(dock, rules, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        }
    }
}

/** Entwickler-Werkzeuge: Straßennetz-Inspektor (F4) unten links, Rechenzeit und Bildrate (F3) unten rechts. */
@Composable
private fun BoxScope.DeveloperOverlays(
    frame: TrafficFrame,
    hud: HudState,
    network: NetworkDebug?,
    input: HudInput,
) {
    if (network != null) {
        NetworkInspector(
            network,
            hud.networkPick,
            frame.game.speedLimits,
            { hud.networkPick = it },
            { x, y -> hud.focus = MapFocus(x, y) },
            { input.network = false },
            Modifier.align(Alignment.BottomStart).padding(14.dp).heightIn(max = INSPECTOR_MAX_HEIGHT.dp),
        )
    }
    if (input.debug) {
        DebugOverlay(
            frame,
            hud.fps,
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 26.dp),
        )
    }
}

/**
 * Die Kennzahlen (und der Ton) reichen viermal pro Sekunde – die Karte liest den Verkehr selbst in jedem Frame.
 * [zoom] hält die aktuelle Zoomstufe der Karte für die Lautstärke der Geräuschkulisse.
 */
@Composable
private fun rememberHudFrame(
    traffic: StateFlow<TrafficFrame?>?,
    sound: SoundBoard,
    zoom: FloatArray,
): State<TrafficFrame?> {
    val director = remember(sound) { SoundDirector(sound) }
    return produceState<TrafficFrame?>(null, traffic) {
        val started = TimeSource.Monotonic.markNow()
        while (traffic != null) {
            value = traffic.value
            value?.let { director.update(it, zoom[0], started.elapsedNow().inWholeMilliseconds / MILLIS_PER_SECOND) }
            delay(HUD_REFRESH)
        }
    }
}

/** Wertung am Ende der Amtszeit bzw. Tagesabschluss mit Zeitung. */
@Composable
private fun EndOfDayOverlays(
    frame: TrafficFrame,
    onContinueDay: () -> Unit,
    onMenu: (() -> Unit)?,
    sprites: SpriteSource,
) {
    val edition = frame.game.latestEdition
    val report = frame.pendingReport
    when {
        frame.game.isGameOver || frame.game.isTermOver -> {
            ResultOverlay(frame.game, onMenu = onMenu, sprites = sprites)
        }

        report != null -> {
            DayReportOverlay(
                report,
                edition?.takeIf {
                    it.day == report.day
                },
                onContinueDay,
                firstDay = frame.game.firstDay,
            )
        }
    }
}

/** Übersetzt einen Klick auf die Karte mit dem gewählten Werkzeug in einen Spielbefehl. */
private fun commandFor(
    selection: ToolSelection,
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? =
    when (selection.tool) {
        Tool.LOOK -> {
            null
        }

        Tool.SIGNAL -> {
            picker.nearestNode(x, y, radius)?.let { GameCommand.BuildSignal(it) }
        }

        Tool.REMOVE -> {
            removeCommandFor(picker, frame, x, y, radius)
        }

        // Klick auf einen aufgestellten Anhänger lädt ihn auf, der nächste Klick auf eine Straße stellt ihn dort auf
        Tool.TRAILER -> {
            nearestCamera(frame, x, y, radius) { it.trailer }?.let { GameCommand.RemoveCamera(it.id) }
                ?: picker.nearestSegment(x, y, radius)?.let { GameCommand.PlaceTrailer(it) }
        }

        // Für die Umleitung kommen auch Nebenstraßen in Frage
        Tool.DETOUR -> {
            picker.nearestSegment(x, y, radius, allowPassive = { true })?.let {
                GameCommand.SetDetour(it, it !in frame?.game?.detourSegments.orEmpty())
            }
        }

        else -> {
            // Zugeschaltete Nebenstraßen (Umleitung) lassen sich wie Hauptstraßen beschildern
            val detour = frame?.game?.detourSegments.orEmpty()
            picker
                .nearestSegment(
                    x,
                    y,
                    radius,
                    allowPassive = { it in detour },
                )?.let { segmentCommand(selection, it, frame) }
        }
    }

/** Werkzeuge, die auf einen Straßenabschnitt wirken. */
private fun segmentCommand(
    selection: ToolSelection,
    segment: Int,
    frame: TrafficFrame?,
): GameCommand? =
    when (selection.tool) {
        Tool.SPEED_LIMIT -> GameCommand.SetSpeedLimit(segment, selection.speedLimit, selection.nightOnly)
        Tool.CAMERA -> GameCommand.PlaceCamera(segment)
        Tool.LASER -> GameCommand.PlaceCamera(segment, mobile = true)
        Tool.PRIORITY -> GameCommand.SetPriorityRoad(segment, segment !in frame?.game?.priorityRoads.orEmpty())
        Tool.BIKE_CROSSING -> GameCommand.SetBikeCrossing(segment, segment !in frame?.game?.bikeCrossings.orEmpty())
        else -> null
    }

/** Nächster Blitzer im Umkreis, der [accept] erfüllt. */
private fun nearestCamera(
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
    accept: (SpeedCamera) -> Boolean,
): SpeedCamera? =
    frame
        ?.current
        ?.cameras
        ?.filter { accept(it) && hypot(it.x - x, it.y - y) <= radius }
        ?.minByOrNull { hypot(it.x - x, it.y - y) }

/** Abbauen: zuerst Blitzer, dann Tempolimit (Tag, dann Nacht), dann Ampel. */
private fun removeCommandFor(
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? {
    val signalNodes = frame?.game?.signalNodes.orEmpty()
    val camera = nearestCamera(frame, x, y, radius) { true }
    val detour = frame?.game?.detourSegments.orEmpty()
    val nearest = picker.nearestSegment(x, y, radius, allowPassive = { it in detour })
    val segment = nearest?.takeIf { it in frame?.game?.speedLimits.orEmpty() }
    val nightSegment = nearest?.takeIf { it in frame?.game?.nightLimits.orEmpty() }
    val node = picker.nearestNode(x, y, radius)?.takeIf { it in signalNodes }
    return when {
        camera != null -> {
            GameCommand.RemoveCamera(camera.id)
        }

        segment != null -> {
            GameCommand.SetSpeedLimit(segment, null)
        }

        nightSegment != null -> {
            GameCommand.SetSpeedLimit(nightSegment, null, nightOnly = true)
        }

        nearest != null && nearest in frame?.game?.priorityRoads.orEmpty() -> {
            GameCommand.SetPriorityRoad(
                nearest,
                false,
            )
        }

        nearest != null && nearest in frame?.game?.bikeCrossings.orEmpty() -> {
            GameCommand.SetBikeCrossing(
                nearest,
                false,
            )
        }

        node != null -> {
            GameCommand.RemoveSignal(node)
        }

        else -> {
            null
        }
    }
}

@Composable
private fun MissingCity(message: String) =
    TmTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(BackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = message,
                style = TmType.text(18.sp, color = TmColors.OnDark).copy(textAlign = TextAlign.Center),
            )
        }
    }
