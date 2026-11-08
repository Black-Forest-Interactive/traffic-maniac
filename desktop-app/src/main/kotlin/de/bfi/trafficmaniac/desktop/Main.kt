package de.bfi.trafficmaniac.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.SaveInfo
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.CityChoice
import de.bfi.trafficmaniac.ui.MainMenu
import de.bfi.trafficmaniac.ui.NoSound
import de.bfi.trafficmaniac.ui.SaveSlots
import de.bfi.trafficmaniac.ui.SoundBoard
import de.bfi.trafficmaniac.ui.Tool
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

private const val NOTICE_MILLIS = 2_500L
private val SAVE_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** Tasten 1–4 → Zeitraffer 1x, 2x, 4x, 8x. */
private val speedKeys = mapOf(Key.One to 1, Key.Two to 2, Key.Three to 4, Key.Four to 8)

/**
 * Startet das Spiel mit dem Startmenü (Szenario, Schwierigkeit, Highscores, Einstellungen, Credits).
 *
 * Argumente: `[--city <id>] [--scenario <id>] [--warmup <Minuten>] [--peak-trips <Anzahl>]`
 * - `--city` wählt das Stadtpaket aus `cities/` vor (Standard: das erste, z. B. `pforzheim`).
 * - `--scenario` startet ohne Menü direkt in ein Szenario (z. B. `erste-amtszeit`, `berufsverkehr`).
 * - `--warmup` simuliert zusätzlich zum Vorlauf des Szenarios vor (volle Straßen).
 * - `--peak-trips` überschreibt die Fahrten pro Stunde in der Spitzenstunde (z. B. für Lasttests).
 */
fun main(args: Array<String>) {
    val options = parseOptions(args)
    Translations.use(Translations.languageFor(UserData().settings().language))
    val packs = CityPacks.discover()
    val sound = if (packs.isNotEmpty()) SynthSoundBoard.open() else NoSound
    val sprites = loadSprites()
    val library =
        Library(packs, Balancing.scenarios(), UserData(), SaveGames({ id -> packs.firstOrNull { it.id == id }?.map }))
    application {
        // Die laufende Partie: Das Fenster leitet Tasten an sie weiter
        val session = remember { mutableStateOf<Session?>(null) }
        val scope = rememberCoroutineScope()
        Window(
            onCloseRequest = ::exitApplication,
            title = session.value?.let { "${GameInfo.TITLE} – ${it.pack.name}" } ?: GameInfo.TITLE,
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
            onKeyEvent = { event ->
                session.value?.let { current ->
                    handleKey(event, current, sound) { scope.save(current, library.saves, SaveInfo.QUICK_SAVE) }
                } ?: false
            },
        ) {
            if (packs.isEmpty()) {
                TrafficManiacApp(city = null, missingCityMessage = missingCitiesMessage())
            } else {
                Game(library, options, sound, sprites, session, ::exitApplication)
            }
        }
    }
}

/** Was das Spiel an Inhalten kennt: Städte, Szenarien, Einstellungen und Spielstände. */
private class Library(
    val packs: List<CityPack>,
    val scenarios: List<Scenario>,
    val userData: UserData,
    val saves: SaveGames,
)

private fun missingCitiesMessage(): String {
    val missing = CityPacks.missingMaps().joinToString(" ") { it.name }.ifEmpty { "pforzheim" }
    return tr(
        "Keine Stadt mit Karte gefunden (Ordner {absolutePath}).\n\n" +
            "Karte erzeugen mit: scripts/import-city.sh {missing}",
        "absolutePath" to File(CityPacks.DIRECTORY).absolutePath,
        "missing" to missing,
    )
}

/** Menü oder laufendes Spiel; nach der Wertung geht es zurück ins Menü. */
@Composable
private fun Game(
    library: Library,
    options: Options,
    sound: SoundBoard,
    sprites: SpriteSource,
    sessionState: MutableState<Session?>,
    onQuit: () -> Unit,
) {
    val userData = library.userData
    var settings by remember { mutableStateOf(userData.settings()) }
    var highscores by remember { mutableStateOf(userData.highscores()) }
    var pack by remember {
        mutableStateOf(library.packs.firstOrNull { it.id == options.city } ?: library.packs.first())
    }
    var session by sessionState
    var loading by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings) {
        sound.muted = !settings.sound
        sound.music = settings.music
        userData.save(settings)
    }
    val start: (Scenario) -> Unit = { scenario ->
        loading = tr("{title}: {name} wird vorbereitet …", "title" to tr(scenario.title), "name" to pack.name)
        scope.launch {
            session = withContext(Dispatchers.Default) { Session.start(pack, scenario, settings, options) }
            loading = null
        }
    }
    LaunchedEffect(Unit) { options.scenario?.let { id -> library.scenarios.firstOrNull { it.id == id }?.let(start) } }
    val load: (SaveInfo) -> Unit = { info ->
        loading = tr("Spielstand wird geladen …")
        scope.launch {
            val loaded = resume(library, info)
            loading = if (loaded == null) tr("Dieser Spielstand passt nicht mehr zum Spiel.") else null
            session = loaded
            if (loaded == null) {
                delay(NOTICE_MILLIS)
                loading = null
            }
        }
    }
    val current = session
    // Sprache wechseln: Tabelle laden und die Oberfläche neu aufbauen, damit jeder Text neu übersetzt wird
    val language = Translations.languageFor(settings.language)
    remember(language) { Translations.use(language) }
    key(language) {
        if (current == null) {
            Menu(library, pack, { pack = it }, settings, { settings = it }, highscores, start, load, loading, onQuit)
        } else {
            Playing(current, sound, if (settings.sprites) sprites else NoSprites, library.saves, { session = null }) {
                highscores = highscores.add(it)
                userData.save(highscores)
            }
        }
    }
}

/** Startmenü mit Stadtauswahl und Spielständen. */
@Composable
private fun Menu(
    library: Library,
    pack: CityPack,
    onPack: (CityPack) -> Unit,
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
    highscores: Highscores,
    onStart: (Scenario) -> Unit,
    onLoad: (SaveInfo) -> Unit,
    loading: String?,
    onQuit: () -> Unit,
) {
    val saves = library.saves
    var saveList by remember { mutableStateOf(saves.list()) }
    MainMenu(
        scenarios = library.scenarios,
        highscores = highscores,
        settings = settings,
        onSettings = onSettings,
        onStart = onStart,
        onQuit = onQuit,
        attribution = pack.map.attribution,
        loading = loading,
        saves =
            SaveSlots(saveList, onLoad) {
                saves.delete(it)
                saveList = saves.list()
            },
        tagline = pack.texts.tagline,
        cities =
            CityChoice(library.packs.map { it.name }, library.packs.indexOf(pack), pack.description) {
                onPack(library.packs[it])
            },
    )
}

/** Die laufende Partie: Takt, Bestenliste am Ende und die Spieloberfläche. */
@Composable
private fun Playing(
    current: Session,
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
        city = current.pack.map,
        traffic = current.runner.frames,
        rules = current.game.rules,
        onCommand = { current.game.submit(it) },
        onContinueDay = { current.runner.acknowledgeReport() },
        sound = sound,
        sprites = sprites,
        onMenu = onMenu,
        onSave = { scope.save(current, saves, "spiel-" + LocalDateTime.now().format(SAVE_ID_FORMAT)) },
        tutorial = current.scenario.tutorial,
        onSpeed = current.runner::setSpeed,
        input = current.input,
    )
}

/** Lädt [info] im Hintergrund; `null`, wenn der Spielstand nicht (mehr) passt. */
private suspend fun resume(
    library: Library,
    info: SaveInfo,
): Session? {
    val scenario = library.scenarios.firstOrNull { it.id == info.scenarioId } ?: return null
    val pack = library.packs.firstOrNull { it.id == info.cityId } ?: return null
    return withContext(Dispatchers.IO) {
        runCatching { Session.resume(library.saves, info, pack, scenario) }
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
            session.runner.paused { game ->
                game.announce(
                    tr(
                        "Speichern fehlgeschlagen: {message}",
                        "message" to it.message,
                    ),
                )
            }
        }
    }
}

/** Grafiken aus `assets/sprites/`; fehlende zeichnet das Spiel selbst. */
private fun loadSprites(): SvgSprites =
    SvgSprites(File(SvgSprites.DEFAULT_FOLDER)).also {
        if (it.count > 0) println("${it.count} Grafiken aus ${SvgSprites.DEFAULT_FOLDER} geladen")
    }

internal class Options(
    /** Stadtpaket, das im Menü vorgewählt ist (`--city <id>`). */
    val city: String?,
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
    return Options(
        named["--city"],
        named["--warmup"]?.toInt() ?: 0,
        named["--peak-trips"]?.toDouble(),
        named["--scenario"],
    )
}

/**
 * Tasten im Spiel: Leertaste Pause, Enter Tagesabschluss schließen, 1–4 Tempo, M Ton, N Musik, F5 Schnellspeichern,
 * F3 Entwickler-Anzeige, Esc und Q/W/E/R/T/A/S/D/X Werkzeuge.
 */
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
        event.key == Key.F3 -> session.input.debug = !session.input.debug
        Tool.forKey(event.key) != null -> session.input.toolRequest = Tool.forKey(event.key)
        else -> return false
    }
    return true
}
