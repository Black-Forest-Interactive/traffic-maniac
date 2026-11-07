package de.bfi.trafficmaniac.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.GameInfo
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.SaveInfo
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.ui.MainMenu
import de.bfi.trafficmaniac.ui.NoSound
import de.bfi.trafficmaniac.ui.SaveSlots
import de.bfi.trafficmaniac.ui.SoundBoard
import de.bfi.trafficmaniac.ui.TrafficManiacApp
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val DEFAULT_CITY_FILE = "data/cities/pforzheim.city"
private const val NOTICE_MILLIS = 2_500L
private val SAVE_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** Tasten 1–4 → Zeitraffer 1x, 2x, 4x, 8x. */
private val speedKeys = mapOf(Key.One to 1, Key.Two to 2, Key.Three to 4, Key.Four to 8)

/**
 * Startet das Spiel mit dem Startmenü (Szenario, Schwierigkeit, Highscores, Einstellungen, Credits).
 *
 * Argumente: `[Stadtdatei] [--scenario <id>] [--warmup <Minuten>] [--peak-trips <Anzahl>]`
 * - `--scenario` startet ohne Menü direkt in ein Szenario (z. B. `erste-amtszeit`, `berufsverkehr`).
 * - `--warmup` simuliert zusätzlich zum Vorlauf des Szenarios vor (volle Straßen).
 * - `--peak-trips` überschreibt die Fahrten pro Stunde in der Spitzenstunde (z. B. für Lasttests).
 */
fun main(args: Array<String>) {
    val options = parseOptions(args)
    val cityFile = File(options.cityFile ?: DEFAULT_CITY_FILE)
    val city: CityMap? = cityFile.takeIf { it.isFile }?.let { CityMapCodec.decode(it.readBytes()) }
    val missingMessage =
        "Keine Stadtdatei gefunden:\n${cityFile.absolutePath}\n\nErzeugen mit: scripts/import-pforzheim.sh"
    val sound = if (city != null) SynthSoundBoard.open() else NoSound
    val sprites = loadSprites()
    val scenarios = Balancing.scenarios()
    val userData = UserData()
    val saves = city?.let { SaveGames(it) }
    application {
        // Die laufende Partie: Das Fenster leitet Tasten an sie weiter
        val session = remember { mutableStateOf<Session?>(null) }
        val scope = rememberCoroutineScope()
        Window(
            onCloseRequest = ::exitApplication,
            title = city?.let { "${GameInfo.TITLE} – ${it.name}" } ?: GameInfo.TITLE,
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
            onKeyEvent = { event ->
                session.value?.let { current ->
                    handleKey(event, current, sound) { saves?.let { scope.save(current, it, SaveInfo.QUICK_SAVE) } }
                } ?: false
            },
        ) {
            if (city == null) {
                TrafficManiacApp(city = null, missingCityMessage = missingMessage)
            } else {
                Game(city, scenarios, options, userData, sound, sprites, session, saves!!, ::exitApplication)
            }
        }
    }
}

/** Menü oder laufendes Spiel; nach der Wertung geht es zurück ins Menü. */
@Composable
private fun Game(
    city: CityMap,
    scenarios: List<Scenario>,
    options: Options,
    userData: UserData,
    sound: SoundBoard,
    sprites: SpriteSource,
    sessionState: MutableState<Session?>,
    saves: SaveGames,
    onQuit: () -> Unit,
) {
    var settings by remember { mutableStateOf(userData.settings()) }
    var highscores by remember { mutableStateOf(userData.highscores()) }
    var session by sessionState
    var loading by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings) {
        sound.muted = !settings.sound
        sound.music = settings.music
        userData.save(settings)
    }
    val start: (Scenario) -> Unit = { scenario ->
        loading = "${scenario.title}: Pforzheim wird vorbereitet …"
        scope.launch {
            val prepared = withContext(Dispatchers.Default) { Session.start(city, scenario, settings, options) }
            session = prepared
            loading = null
        }
    }
    LaunchedEffect(Unit) { options.scenario?.let { id -> scenarios.firstOrNull { it.id == id }?.let(start) } }
    val load: (SaveInfo) -> Unit = { info ->
        loading = "Spielstand wird geladen …"
        scope.launch {
            val loaded = resume(saves, info, scenarios)
            loading = if (loaded == null) "Dieser Spielstand passt nicht mehr zum Spiel." else null
            session = loaded
            if (loaded == null) {
                delay(NOTICE_MILLIS)
                loading = null
            }
        }
    }
    val current = session
    var saveList by remember { mutableStateOf(saves.list()) }
    LaunchedEffect(current == null) { saveList = saves.list() }
    if (current == null) {
        MainMenu(
            scenarios = scenarios,
            highscores = highscores,
            settings = settings,
            onSettings = { settings = it },
            onStart = start,
            onQuit = onQuit,
            attribution = city.attribution,
            loading = loading,
            saves =
                SaveSlots(saveList, load) {
                    saves.delete(it)
                    saveList = saves.list()
                },
        )
        return
    }
    Playing(current, city, sound, if (settings.sprites) sprites else NoSprites, saves, { session = null }) { entry ->
        highscores = highscores.add(entry)
        userData.save(highscores)
    }
}

/** Die laufende Partie: Takt, Bestenliste am Ende und die Spieloberfläche. */
@Composable
private fun Playing(
    current: Session,
    city: CityMap,
    sound: SoundBoard,
    sprites: SpriteSource,
    saves: SaveGames,
    onMenu: () -> Unit,
    onHighscore: (HighscoreEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(current) { current.runner.run() }
    LaunchedEffect(current) { current.result()?.let(onHighscore) }
    TrafficManiacApp(
        city = city,
        traffic = current.runner.frames,
        rules = current.game.rules,
        onCommand = { current.game.submit(it) },
        onContinueDay = { current.runner.acknowledgeReport() },
        sound = sound,
        sprites = sprites,
        onMenu = onMenu,
        onSave = { scope.save(current, saves, "spiel-" + LocalDateTime.now().format(SAVE_ID_FORMAT)) },
    )
}

/** Lädt [info] im Hintergrund; `null`, wenn der Spielstand nicht (mehr) passt. */
private suspend fun resume(
    saves: SaveGames,
    info: SaveInfo,
    scenarios: List<Scenario>,
): Session? {
    val scenario = scenarios.firstOrNull { it.id == info.scenarioId } ?: return null
    return withContext(Dispatchers.IO) {
        runCatching { Session.resume(saves, info, scenario) }
            .onFailure { println("Spielstand ${info.id} nicht ladbar: $it") }
            .getOrNull()
    }
}

/** Speichert im Hintergrund; schlägt es fehl, erscheint eine Meldung im Spiel. */
private fun CoroutineScope.save(
    session: Session,
    saves: SaveGames,
    id: String,
) {
    launch(Dispatchers.IO) {
        runCatching { session.save(saves, id) }.onFailure {
            println("Speichern fehlgeschlagen: $it")
            session.runner.paused { game -> game.announce("Speichern fehlgeschlagen: ${it.message}") }
        }
    }
}

/** Grafiken aus `assets/sprites/`; fehlende zeichnet das Spiel selbst. */
private fun loadSprites(): SvgSprites =
    SvgSprites(File(SvgSprites.DEFAULT_FOLDER)).also {
        if (it.count > 0) println("${it.count} Grafiken aus ${SvgSprites.DEFAULT_FOLDER} geladen")
    }

internal class Options(
    val cityFile: String?,
    val warmupMinutes: Int,
    val peakTrips: Double?,
    val scenario: String?,
)

private fun parseOptions(args: Array<String>): Options {
    val named =
        args
            .toList()
            .windowed(2)
            .filter { it[0].startsWith("--") }
            .associate { it[0] to it[1] }
    val positional =
        args.filterIndexed { i, arg ->
            !arg.startsWith("--") &&
                args.getOrNull(i - 1)?.startsWith("--") != true
        }
    return Options(
        positional.firstOrNull(),
        named["--warmup"]?.toInt() ?: 0,
        named["--peak-trips"]?.toDouble(),
        named["--scenario"],
    )
}

/** Tasten im Spiel: Leertaste Pause, Enter Tagesabschluss schließen, 1–4 Tempo, M Ton, N Musik, F5 Schnellspeichern. */
private fun handleKey(
    event: KeyEvent,
    session: Session,
    sound: SoundBoard,
    onQuickSave: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val runner = session.runner
    val speed = speedKeys[event.key]
    when {
        event.key == Key.Spacebar -> runner.togglePause()
        event.key == Key.Enter -> runner.acknowledgeReport()
        speed != null -> runner.setSpeed(speed)
        event.key == Key.M -> sound.muted = !sound.muted
        event.key == Key.N -> sound.music = !sound.music
        event.key == Key.F5 -> onQuickSave()
        else -> return false
    }
    return true
}
