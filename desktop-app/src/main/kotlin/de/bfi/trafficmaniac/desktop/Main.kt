package de.bfi.trafficmaniac.desktop

import androidx.compose.runtime.LaunchedEffect
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
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.GameInfo
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.ui.NoSound
import de.bfi.trafficmaniac.ui.SoundBoard
import de.bfi.trafficmaniac.ui.TrafficManiacApp
import java.io.File

private const val DEFAULT_CITY_FILE = "data/cities/pforzheim.city"

/** Tasten 1–4 → Zeitraffer 1x, 2x, 4x, 8x. */
private val speedKeys = mapOf(Key.One to 1, Key.Two to 2, Key.Three to 4, Key.Four to 8)

private const val TICKS_PER_MINUTE = 60

/**
 * Startet das Spiel.
 *
 * Argumente: `[Stadtdatei] [--warmup <Minuten>] [--peak-trips <Anzahl>]`
 * - `--warmup` simuliert vor dem Öffnen des Fensters ohne Darstellung vor (nicht auf volle Straßen warten).
 * - `--peak-trips` überschreibt die Fahrten pro Stunde in der Spitzenstunde (z. B. für Lasttests).
 *
 * Das Spiel beginnt montags um 5 Uhr; `--warmup 120` startet also mitten im Berufsverkehr.
 */
fun main(args: Array<String>) {
    val options = parseOptions(args)
    val balancing = Balancing.simulationConfig()
    val config = options.peakTrips?.let { balancing.copy(peakTripsPerHour = it) } ?: balancing
    val cityFile = File(options.cityFile ?: DEFAULT_CITY_FILE)
    val city: CityMap? = cityFile.takeIf { it.isFile }?.let { CityMapCodec.decode(it.readBytes()) }
    val missingMessage =
        "Keine Stadtdatei gefunden:\n${cityFile.absolutePath}\n\n" +
            "Erzeugen mit: scripts/import-pforzheim.sh"
    val game = city?.let { CityGame(it, config, Balancing.gameRules()) }
    game?.let { repeat(options.warmupMinutes * TICKS_PER_MINUTE * config.ticksPerSecond) { _ -> it.step() } }
    val runner = game?.let { GameRunner(it) }
    val sound = if (city != null) SynthSoundBoard.open() else NoSound
    val sprites = loadSprites()

    application {
        runner?.let { LaunchedEffect(it) { it.run() } }
        Window(
            onCloseRequest = ::exitApplication,
            title = city?.let { "${GameInfo.TITLE} – ${it.name}" } ?: GameInfo.TITLE,
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
            onKeyEvent = { event -> runner != null && handleKey(event, runner, sound) },
        ) {
            TrafficManiacApp(
                city = city,
                missingCityMessage = missingMessage,
                traffic = runner?.frames,
                rules = game?.rules ?: GameRules(),
                onCommand = { game?.submit(it) },
                onContinueDay = { runner?.acknowledgeReport() },
                sound = sound,
                sprites = sprites,
            )
        }
    }
}

/** Grafiken aus `assets/sprites/`; fehlende zeichnet das Spiel selbst. */
private fun loadSprites(): SvgSprites =
    SvgSprites(File(SvgSprites.DEFAULT_FOLDER)).also {
        if (it.count > 0) println("${it.count} Grafiken aus ${SvgSprites.DEFAULT_FOLDER} geladen")
    }

private class Options(
    val cityFile: String?,
    val warmupMinutes: Int,
    val peakTrips: Double?,
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
    return Options(positional.firstOrNull(), named["--warmup"]?.toInt() ?: 0, named["--peak-trips"]?.toDouble())
}

/** Leertaste: Pause, Enter: Tagesabschluss schließen, 1–4: Tempo, M: stumm, N: Musik an/aus. */
private fun handleKey(
    event: KeyEvent,
    runner: GameRunner,
    sound: SoundBoard,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val speed = speedKeys[event.key]
    when {
        event.key == Key.Spacebar -> runner.togglePause()
        event.key == Key.Enter -> runner.acknowledgeReport()
        speed != null -> runner.setSpeed(speed)
        event.key == Key.M -> sound.muted = !sound.muted
        event.key == Key.N -> sound.music = !sound.music
        else -> return false
    }
    return true
}
