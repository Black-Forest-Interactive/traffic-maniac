package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import de.bfi.trafficmaniac.ui.map.EnvironmentLayer
import de.bfi.trafficmaniac.ui.map.MapFocus
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.MapView
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val BackgroundColor = Color(0xFF2B2D42)
private val HUD_REFRESH = 250.milliseconds
private val MessageBackground = ComicTheme.paper
private val MessageText = ComicTheme.ink
private const val PICK_RADIUS_PIXELS = 14f
private const val MIN_PICK_RADIUS_METERS = 6f
private const val VISIBLE_MESSAGES = 3
private const val MILLIS_PER_SECOND = 1_000.0
private const val MONEY_RAIN_WIDTH = 360
private const val MONEY_RAIN_HEIGHT = 260

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
) {
    if (city == null) {
        MissingCity(missingCityMessage)
        return
    }
    var fps by remember { mutableStateOf(0) }
    var selection by remember { mutableStateOf(ToolSelection()) }
    var panel by remember { mutableStateOf(SidePanel.NONE) }
    var step by remember { mutableStateOf(if (tutorial) TutorialStep.WELCOME else null) }
    val toggles = remember { MapToggles() }
    val picker = remember(city) { MapPicker(city) }
    val zoom = remember { floatArrayOf(1f) }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    val show: (MapPlace) -> Unit = { focus = MapFocus(it.x, it.y) }
    val command: (GameCommand) -> Unit = {
        sound.play(SoundEffect.CLICK)
        onCommand(it)
    }
    val hudFrame by rememberHudFrame(traffic, sound, zoom)
    Box(Modifier.fillMaxSize()) {
        MapView(
            focus = focus,
            sprites = sprites,
            city = city,
            traffic = traffic?.let { flow -> { flow.value } },
            onFps = { fps = it },
            showMoods = toggles.showMoods,
            environmentLayer = toggles.environmentLayer,
            showCondition = toggles.showCondition || selection.tool == Tool.DETOUR,
            onZoom = { zoom[0] = it },
            onMapClick = { x, y, metersPerPixel ->
                val radius = max(MIN_PICK_RADIUS_METERS, PICK_RADIUS_PIXELS * metersPerPixel)
                commandFor(selection, picker, traffic?.value, x, y, radius)?.let(command)
            },
        )
        hudFrame?.let { frame ->
            HudWithMoneyRain(frame, fps, sprites, Modifier.align(Alignment.TopStart))
            SidePanels(
                frame = frame,
                rules = rules,
                panel = panel,
                onPanel = { panel = if (panel == it) SidePanel.NONE else it },
                toggles = toggles,
                onCommand = command,
                picker = picker,
                onShow = show,
                onMenu = onMenu,
                onSave = onSave,
                sprites = sprites,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Messages(frame.game.messages, show, Modifier.align(Alignment.BottomStart).padding(bottom = 24.dp))
            step?.let { s -> TutorialPanel(s, frame.game, { step = it }, Modifier.align(Alignment.CenterStart)) }
        }
        val tools = ToolBarState(selection, hudFrame?.game, step) { selection = it }
        if (traffic != null) ToolBar(tools, rules, sprites, Modifier.align(Alignment.BottomCenter))
        hudFrame?.let { EndOfDayOverlays(it, onContinueDay, onMenu, sprites) }
    }
}

/** Was die Werkzeugleiste unten braucht: Auswahl, Spielstand, Tutorial-Schritt und was bei einer Auswahl passiert. */
private class ToolBarState(
    val selection: ToolSelection,
    game: GameState?,
    step: TutorialStep?,
    val onSelect: (ToolSelection) -> Unit,
) {
    val idleTrailers = game?.idleTrailers ?: 0

    /** Im Tutorial nur die freigeschalteten Werkzeuge. */
    val available = step?.unlocked ?: Tool.entries.toSet()
}

@Composable
private fun ToolBar(
    state: ToolBarState,
    rules: GameRules,
    sprites: SpriteSource,
    modifier: Modifier,
) = ToolBar(state.selection, rules, state.onSelect, modifier, sprites, state.idleTrailers, state.available)

/** HUD oben links; bei neuen Einnahmen regnet es Münzen darüber. */
@Composable
private fun HudWithMoneyRain(
    frame: TrafficFrame,
    fps: Int,
    sprites: SpriteSource,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        TrafficHud(frame, fps, sprites = sprites)
        val game = frame.game
        val income = game.fineIncome + game.parkingOffice.feeIncome + game.parkingOffice.ticketIncome
        MoneyRain(income, Modifier.size(MONEY_RAIN_WIDTH.dp, MONEY_RAIN_HEIGHT.dp), sprites)
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
        Tool.TRAILER -> GameCommand.PlaceTrailer(segment)
        Tool.PRIORITY -> GameCommand.SetPriorityRoad(segment, segment !in frame?.game?.priorityRoads.orEmpty())
        Tool.BIKE_CROSSING -> GameCommand.SetBikeCrossing(segment, segment !in frame?.game?.bikeCrossings.orEmpty())
        else -> null
    }

/** Abbauen: zuerst Blitzer, dann Tempolimit (Tag, dann Nacht), dann Ampel. */
private fun removeCommandFor(
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? {
    val signalNodes = frame?.game?.signalNodes.orEmpty()
    val camera =
        frame?.current?.cameras?.filter { hypot(it.x - x, it.y - y) <= radius }?.minByOrNull {
            hypot(
                it.x - x,
                it.y - y,
            )
        }
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
private fun Messages(
    messages: List<GameMessage>,
    onShow: (MapPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (message in messages.takeLast(VISIBLE_MESSAGES)) {
            // Meldungen mit Ort: Klick zeigt die Stelle auf der Karte
            val place = message.place
            BasicText(
                "${message.time.clockText}  ${message.text}" + if (place != null) "  ▸" else "",
                style = TextStyle(color = MessageText, fontSize = 13.sp),
                modifier =
                    Modifier
                        .comicPanel(shadow = 3.dp)
                        .then(if (place != null) Modifier.clickable { onShow(place) } else Modifier)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun MissingCity(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = message,
            style = TextStyle(color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center),
        )
    }
}
