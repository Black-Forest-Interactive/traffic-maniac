package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.CameraBalance
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.TmButton
import de.bfi.trafficmaniac.ui.map.MapFocus
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.MapView
import de.bfi.trafficmaniac.ui.map.MapZoom
import de.bfi.trafficmaniac.ui.map.NetworkDebug
import de.bfi.trafficmaniac.ui.map.NetworkPick
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.PlanPreview
import de.bfi.trafficmaniac.ui.map.SpriteSource
import de.bfi.trafficmaniac.ui.map.StreetRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val BackgroundColor = TmColors.Ink
private val HUD_REFRESH = 250.milliseconds

/** Unter dem Kartenumschalter. */
private const val WARMUP_TOP = 64

/** „Tempo 30 gewünscht“: das Tempolimit-Werkzeug gleich auf 30 stellen. */
private const val ASSIGNMENT_LIMIT = 30

/** Symbole sind größer als der Fangradius für Straßen – etwas großzügiger treffen. */
private const val INSPECT_RADIUS_FACTOR = 1.5f

/** Eine Meldung zeigt auf eine Straße; das Ziel liegt höchstens so weit davon entfernt. */
private const val MESSAGE_INSPECT_METERS = 150f

/** Einblendungen unter Kartenumschalter und Zähler-Chips. */
private const val TOAST_TOP = 104
private const val PICK_RADIUS_PIXELS = 14f
private const val MIN_PICK_RADIUS_METERS = 6f
private const val MILLIS_PER_SECOND = 1_000.0
private const val MONEY_RAIN_WIDTH = 360
private const val MONEY_RAIN_HEIGHT = 260
private const val FEED_GAP = 28
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

    /** Geplante, noch nicht gebaute Maßnahmen. */
    val plan = Plan()

    /** Posteingang: Einträge, die schon einmal auf der Seite zu sehen waren (für das Badge „ungelesen“). */
    var seenInbox by mutableStateOf(emptySet<String>())

    /** Ebene „Buslinien“: Liniennummern am zuletzt angeklickten Abschnitt. */
    var transitPick by mutableStateOf<List<String>?>(null)

    /** Stand zu Beginn jedes Tags (so weit gesehen) – für die Tagesbilanz im Abschluss. */
    val dayStarts = HashMap<Int, DayStart>()

    /** Tag des offenen Tagesabschlusses bzw. der, dessen Abschluss weggeklickt wurde. */
    var reportDay by mutableStateOf<Int?>(null)
    var hiddenReport by mutableStateOf<Int?>(null)

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
        inspectRequest = place
    }

    /** Auftragskarten: „Hin“ fliegt zum Ort und wählt das passende Werkzeug, „Ablehnen“ schickt den Befehl. */
    fun assignmentActions(command: (GameCommand) -> Unit) =
        AssignmentActions(
            onGo = { assignment ->
                show(assignment.place)
                assignment.kind.tool()?.let { kind ->
                    val next = selection.with(kind)
                    pick(
                        if (kind ==
                            ToolKind.SPEED_LIMIT
                        ) {
                            next.copy(speedLimit = ASSIGNMENT_LIMIT, nightOnly = false)
                        } else {
                            next
                        },
                    )
                }
            },
            onDecline = { command(GameCommand.DeclineAssignment(it.id)) },
        )

    /** Aktionskarte zu einem angeklickten Schaden oder Vorfall. */
    var inspected by mutableStateOf<Inspected?>(null)

    /** Meldung angeklickt: dort nachsehen, was es gibt (die Karte sucht das Ziel). */
    var inspectRequest by mutableStateOf<MapPlace?>(null)

    /** Zähler-Chips: wo die Reihe beim nächsten Klick weitergeht. */
    private var nextTarget = 0

    /** Zum nächsten Schaden ([damage]) bzw. Vorfall springen und die Aktionskarte öffnen. */
    fun showNext(
        targets: List<InspectTarget>,
        damage: Boolean,
    ) {
        val matching = targets.filter { (it.target is Inspected.Damage) == damage }.distinctBy { it.target }
        if (matching.isEmpty()) return
        val target = matching[nextTarget++ % matching.size]
        focus = MapFocus(target.x, target.y)
        inspected = target.target
    }

    /** Erfolge, die der Spieler beim letzten Öffnen des Fensters schon gesehen hat (−1 = noch kein Bild). */
    private var seenAchievements by mutableStateOf(-1)

    /** Neue Erfolge seit dem letzten Blick ins Fenster – für den Punkt in der Leiste. */
    fun newAchievements(game: GameState?): Int {
        val unlocked = game?.achievementList?.count { it.unlocked } ?: return 0
        if (seenAchievements < 0 || panel == SidePanel.ACHIEVEMENTS) seenAchievements = unlocked
        return unlocked - seenAchievements
    }

    /** Gruppe, deren Ausklappliste über dem Dock offen ist. */
    var openGroup by mutableStateOf<Tool?>(null)

    /** Neue Auswahl; eine andere Art bricht ein angefangenes „Anfang gesetzt“ ab. */
    fun select(next: ToolSelection) {
        if (next.kind != selection.kind) plan.routeStart = null
        selection = next
    }

    /** Kachel angeklickt: Gruppe wählen; bei mehreren Arten die Liste auf- bzw. zuklappen. */
    fun tile(tool: Tool) {
        openGroup = if (tool.isGroup && openGroup != tool) tool else null
        select(selection.copy(tool = tool))
    }

    /** Art in der Liste gewählt: übernehmen und die Liste schließen. */
    fun pick(next: ToolSelection) {
        select(next)
        openGroup = null
    }
}

/** Tasten und Anfragen aus dem Fenster: Werkzeug (Blitzer zweimal = nächste Art) und Fenster rechts. */
@Composable
internal fun InputRequests(
    input: HudInput,
    hud: HudState,
) {
    LaunchedEffect(input.toolRequest) {
        // Esc: Tagesabschluss schließen, auf einer Seite zurück zur Karte
        if (input.toolRequest == Tool.LOOK) {
            hud.reportDay?.let { hud.hiddenReport = it }
            hud.panel = SidePanel.NONE
        }
        input.toolRequest?.takeIf { it in hud.availableTools }?.let { tool ->
            val selection = hud.selection
            // Gruppentaste noch einmal: die nächste Art; sonst die Gruppe wählen (Liste zu)
            if (tool == selection.tool &&
                tool.isGroup
            ) {
                hud.select(selection.next())
            } else {
                hud.pick(selection.copy(tool = tool))
            }
        }
        input.toolRequest = null
    }
    // Ziffer bei offener Liste: die Art an dieser Stelle
    LaunchedEffect(input.kindRequest) {
        val group = hud.openGroup
        val kind = input.kindRequest?.let { group?.kinds?.getOrNull(it - 1) }
        if (kind != null) hud.pick(hud.selection.with(kind))
        input.kindRequest = null
    }
    LaunchedEffect(hud.openGroup) { input.listOpen = hud.openGroup != null }
    LaunchedEffect(input.listRequest) {
        input.listRequest?.let { hud.tile(it) }
        input.listRequest = null
    }
    LaunchedEffect(input.panelRequest) {
        input.panelRequest?.let { hud.panel = it }
        input.panelRequest = null
    }
}

/** Wurzel der Spieloberfläche. Ohne Stadt wird ein Hinweis angezeigt, wie man sie erzeugt. */
@Composable
fun TrafficManiacApp(
    city: CityMap?,
    missingCityMessage: String = "",
    /** Knopf unter dem Hinweis, der die fehlende Karte herunterlädt; `null` = kein Knopf. */
    cityDownload: CityDownload? = null,
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
    /** Nach der Auto-Bremse mit dem alten Tempo weiter. */
    onResumeSpeed: (() -> Unit)? = null,
    /** Vorspulen bis zum nächsten Ereignis. */
    onFastForward: (() -> Unit)? = null,
    /** Tasten aus dem Fenster: Werkzeuge und Entwickler-Anzeige. */
    input: HudInput = remember { HudInput() },
) {
    if (city == null) {
        MissingCity(missingCityMessage, cityDownload)
        return
    }
    val hud = remember(tutorial) { HudState(tutorial) }
    val picker = remember(city) { MapPicker(city) }
    val command: (GameCommand) -> Unit = {
        sound.play(SoundEffect.CLICK)
        onCommand(it)
    }
    InputRequests(input, hud)
    PlanKeys(input, hud.plan, command)
    val hudFrame by rememberHudFrame(traffic, sound, hud.zoomLevel)
    // Feste Zonen: Kopfleiste oben, Leiste links, rechts daneben die Karte oder eine Seite – nie beides
    TmTheme {
        Box(Modifier.fillMaxSize().background(TmColors.Ground)) {
            Column(Modifier.fillMaxSize()) {
                hudFrame?.let {
                    HudTopBar(
                        it,
                        TopBarActions(
                            onSpeed,
                            onSave,
                            onMenu,
                            onResume = onResumeSpeed,
                            onFastForward = onFastForward,
                        ),
                    )
                }
                Row(Modifier.weight(1f)) {
                    HudRail(hud.panel, { hud.panel = it }, railBadges(hudFrame, hud))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MapArea(city, traffic, hudFrame, hud, picker, rules, command, sprites, input)
                        hudFrame?.takeIf { hud.panel != SidePanel.NONE }?.let { frame ->
                            Page(frame, hud, rules, command, picker, sprites)
                        }
                    }
                }
            }
            hudFrame?.let { frame ->
                DayStarts(frame, hud)
                EndOfDayOverlays(frame, onContinueDay, onMenu, sprites, hud)
            }
        }
    }
}

/** Merkt sich den Stand zu Beginn jedes Tags und welcher Tagesabschluss gerade offen ist. */
@Composable
private fun DayStarts(
    frame: TrafficFrame,
    hud: HudState,
) {
    val day = frame.current.gameTime.day
    LaunchedEffect(day) { hud.dayStarts.getOrPut(day) { DayStart.of(frame.game) } }
    hud.reportDay = frame.pendingReport?.day
}

/** Die Seite anstelle der Karte; „Auf Karte zeigen“ und „Hin“ wechseln zurück zur Karte. */
@Composable
private fun Page(
    frame: TrafficFrame,
    hud: HudState,
    rules: GameRules,
    command: (GameCommand) -> Unit,
    picker: MapPicker,
    sprites: SpriteSource,
) {
    val toMap = { hud.panel = SidePanel.NONE }
    val showOnMap = { place: MapPlace ->
        toMap()
        hud.show(place)
    }
    val base = hud.assignmentActions(command)
    val assignments =
        AssignmentActions(
            onGo = {
                toMap()
                base.onGo(it)
            },
            onDecline = base.onDecline,
        )
    // Was auf der Posteingang-Seite zu sehen ist, gilt als gelesen
    LaunchedEffect(hud.panel, frame) {
        if (hud.panel == SidePanel.INBOX) hud.seenInbox = hud.seenInbox + inboxKeys(frame)
    }
    HudPage(
        hud.panel,
        PanelContext(frame, rules, command, picker, showOnMap, sprites, toMap, onEnvironment = {
            hud.toggles.layer = MapLayerKind.ENVIRONMENT
            hud.toggles.environment = it
            toMap()
        }) { hud.panel = it },
        InboxActions(showOnMap, assignments, { hud.panel = SidePanel.MAINTENANCE }, toMap) { hud.panel = it },
    )
}

/** Zähler der Leiste: Posteingang (Aufträge, Einsätze, ungesicherte Schäden), Bauamt, Einsätze, neue Erfolge. */
@Composable
private fun railBadges(
    frame: TrafficFrame?,
    hud: HudState,
): RailBadges {
    val achievements = hud.newAchievements(frame?.game)
    frame ?: return RailBadges()
    val unsecured =
        frame.game.maintenance.reports
            .count { it.status in UNSECURED_DAMAGE }
    val incidents = frame.current.incidents.size
    return RailBadges(
        // Ungelesen: was seit dem letzten Blick in den Posteingang neu ist
        inbox = (inboxKeys(frame) - hud.seenInbox).size,
        unsecured = unsecured,
        emergencies = incidents,
        achievements = achievements,
    )
}

/** Enter baut den Bauplan, Strg+Z nimmt die letzte geplante Maßnahme zurück. */
@Composable
private fun PlanKeys(
    input: HudInput,
    plan: Plan,
    command: (GameCommand) -> Unit,
) {
    LaunchedEffect(input.buildRequest) {
        if (input.buildRequest) plan.take().forEach(command)
        input.buildRequest = false
    }
    LaunchedEffect(input.undoRequest) {
        if (input.undoRequest) plan.undo()
        input.undoRequest = false
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
) {
    val networkData = remember(city) { lazy { NetworkDebug(city) } }
    val network = if (input.network) networkData.value else null
    Box(Modifier.fillMaxSize()) {
        MapLayer(city, traffic, hud, picker, rules, sprites, network)
        // Oben links die Kartenansicht, oben rechts Lage und Posteingang, rechts mittig der Zoom, unten links Meldungen
        MapViewPanel(
            hud.toggles,
            Modifier.align(Alignment.TopStart).padding(16.dp),
            hudFrame
                ?.current
                ?.transit
                ?.lines
                .orEmpty(),
        )
        ZoomControls({ hud.zoom = MapZoom(it) }, Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
        hudFrame?.let { frame ->
            val game = frame.game
            StatusColumn(frame, hud, Modifier.align(Alignment.TopEnd).padding(16.dp))
            val income = game.fineIncome + game.parkingOffice.feeIncome + game.parkingOffice.ticketIncome
            MoneyRain(
                income,
                Modifier.align(Alignment.TopCenter).size(MONEY_RAIN_WIDTH.dp, MONEY_RAIN_HEIGHT.dp),
                sprites,
            )
            frame.warmup?.let {
                WarmupBanner(it, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
            }
            HighlightToasts(
                game.highlights,
                hud::show,
                Modifier.align(Alignment.BottomStart).padding(16.dp),
                onInbox = { hud.panel = SidePanel.INBOX },
            )
            DeveloperOverlays(frame, hud, network, input)
            // Rechts unten: Aktionskarte des angeklickten Objekts über dem Bauplan – nichts überlappt
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                hud.inspected?.let { inspected ->
                    InspectCard(frame, inspected, rules, command, { hud.inspected = null })
                }
                hud.transitPick?.takeIf { hud.toggles.layer == MapLayerKind.TRANSIT }?.let { refs ->
                    LinesHere(refs, frame.current.transit.lines, hud.toggles) { hud.transitPick = null }
                }
                BuildPlan(hud.plan, game.balance, picker, command)
            }
            val dock =
                ToolDockState(
                    hud.selection,
                    hud.availableTools,
                    game.idleTrailers,
                    hud.openGroup,
                    hud::tile,
                    hud::pick,
                )
            ToolDock(dock, rules, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
}

/** Oben rechts: Lage und Posteingang-Hinweis, im Tutorial darunter der aktuelle Schritt. */
@Composable
private fun StatusColumn(
    frame: TrafficFrame,
    hud: HudState,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MapStatus(frame, { hud.panel = SidePanel.INBOX })
        hud.step?.let { s -> TutorialPanel(s, frame.game, { hud.step = it }) }
    }
}

/** Die Karte ohne UI-Skalierung: Zoom, Linien und Sprites bleiben gleich, nur die Bedienung wächst. */
@Composable
internal fun MapLayer(
    city: CityMap,
    traffic: StateFlow<TrafficFrame?>?,
    hud: HudState,
    picker: MapPicker,
    rules: GameRules,
    sprites: SpriteSource,
    network: NetworkDebug?,
    /** Fangradius für Klicks in Bildpunkten – mit dem Finger größer als mit der Maus. */
    pickRadiusPixels: Float = PICK_RADIUS_PIXELS,
) {
    val route = remember(city) { StreetRoute(city) }
    // Meldung angeklickt: am Ort nachsehen, ob dort ein Schaden oder Vorfall ist
    LaunchedEffect(hud.inspectRequest) {
        val place = hud.inspectRequest ?: return@LaunchedEffect
        val frame = traffic?.value ?: return@LaunchedEffect
        val targets = inspectTargets(frame.game, frame.current.incidents, picker, city)
        hud.inspected = inspectAt(targets, place.x, place.y, MESSAGE_INSPECT_METERS) ?: hud.inspected
        hud.inspectRequest = null
    }
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
            showCondition = hud.toggles.showCondition || hud.selection.kind == ToolKind.DETOUR,
            onZoom = { hud.zoomLevel[0] = it },
            onMapClick = { x, y, metersPerPixel ->
                val radius = max(MIN_PICK_RADIUS_METERS, pickRadiusPixels * metersPerPixel)
                val frame = traffic?.value
                if (network != null) {
                    hud.networkPick = network.pick(x, y, radius)
                } else if (hud.selection.kind == ToolKind.LOOK && frame != null) {
                    // Ansehen: Schaden oder Vorfall unter dem Klick öffnen, daneben schließt die Karte
                    val targets = inspectTargets(frame.game, frame.current.incidents, picker, city)
                    hud.inspected = inspectAt(targets, x, y, radius * INSPECT_RADIUS_FACTOR)
                    // Ebene „Buslinien“: welche Linien fahren hier?
                    hud.transitPick =
                        if (hud.toggles.layer == MapLayerKind.TRANSIT) linesAt(frame, picker, x, y, radius) else null
                } else {
                    hud.plan.click(
                        hud.selection,
                        PlanContext(city, picker, route, rules, traffic?.value),
                        x,
                        y,
                        radius,
                    )
                }
            },
            network = network,
            networkPick = hud.networkPick,
            plan = PlanPreview(hud.plan.actions.map { it.marker }, hud.plan.routeStart, tr("Anfang")),
            extras = hud.toggles.extras,
        )
    }
}

/** Planungsleiste, solange etwas geplant ist; „Bauen“ schickt alle Befehle in der geplanten Reihenfolge. */
internal fun planBar(
    plan: Plan,
    balance: Long,
    command: (GameCommand) -> Unit,
): PlanBarState? =
    if (!plan.isActive) {
        null
    } else {
        PlanBarState(
            plan.actions.size,
            plan.total,
            balance,
            plan.routeStart != null,
            plan::undo,
            plan::clear,
        ) { plan.take().forEach(command) }
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
internal fun rememberHudFrame(
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
internal fun EndOfDayOverlays(
    frame: TrafficFrame,
    onContinueDay: () -> Unit,
    onMenu: (() -> Unit)?,
    sprites: SpriteSource,
    /** Am Desktop: Tagesbilanz und Schließen; per Finger `null`. */
    hud: HudState? = null,
) {
    val edition = frame.game.latestEdition
    val report = frame.pendingReport
    when {
        frame.game.isGameOver || frame.game.isTermOver -> {
            ResultOverlay(frame.game, onMenu = onMenu, sprites = sprites)
        }

        report != null && hud != null && hud.hiddenReport == report.day -> {
            ReportWaiting(report.day - frame.game.firstDay + 1, onContinueDay) { hud.hiddenReport = null }
        }

        report != null -> {
            DayReportOverlay(
                report,
                edition?.takeIf {
                    it.day == report.day
                },
                onContinueDay,
                firstDay = frame.game.firstDay,
                cameraSummary = CameraBalance.summary(frame.game.cameras),
                reference = frame.game.firstDayReport,
                achievements =
                    frame.game.achievementList
                        .filter { it.unlockedDay == report.day }
                        .map { it.achievement.title },
                balance = hud?.dayStarts?.get(report.day)?.balance(frame.game),
                actions =
                    hud?.let {
                        DayReportActions(
                            onClose = { it.hiddenReport = report.day },
                            onTreasury = {
                                it.hiddenReport = report.day
                                it.panel = SidePanel.TREASURY
                            },
                        )
                    } ?: DayReportActions(),
            )
        }
    }
}

@Composable
private fun MissingCity(
    message: String,
    download: CityDownload?,
) = TmTheme {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BasicText(
                text = message,
                style = TmType.text(18.sp, color = TmColors.OnDark).copy(textAlign = TextAlign.Center),
            )
            download?.let {
                TmButton(it.label, it.onClick, kind = ButtonKind.PRIMARY, large = true, enabled = !it.busy)
            }
        }
    }
}

/** Fehlende Karte herunterladen: Beschriftung, ob gerade geladen wird, und was der Knopf tut. */
class CityDownload(
    val label: String,
    val busy: Boolean,
    val onClick: () -> Unit,
)
