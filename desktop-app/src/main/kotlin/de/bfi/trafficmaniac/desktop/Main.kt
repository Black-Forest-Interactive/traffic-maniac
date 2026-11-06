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
import de.bfi.trafficmaniac.game.GameInfo
import de.bfi.trafficmaniac.game.SimulationRunner
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.ui.TrafficManiacApp
import java.io.File

private const val DEFAULT_CITY_FILE = "data/cities/pforzheim.city"

/** Tasten 1–4 → Zeitraffer 1x, 2x, 4x, 8x. */
private val speedKeys = mapOf(Key.One to 1, Key.Two to 2, Key.Three to 4, Key.Four to 8)

private const val TICKS_PER_MINUTE = 60

/**
 * Startet das Spiel.
 *
 * Argumente: `[Stadtdatei] [--warmup <Minuten>] [--trips-per-hour <Anzahl>]`
 * - `--warmup` simuliert vor dem Öffnen des Fensters ohne Darstellung vor (nicht auf volle Straßen warten).
 * - `--trips-per-hour` überschreibt die Verkehrsnachfrage (z. B. für Lasttests).
 */
fun main(args: Array<String>) {
    val options =
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
    val warmupMinutes = options["--warmup"]?.toInt() ?: 0
    val config =
        options["--trips-per-hour"]?.let { SimulationConfig(tripsPerHour = it.toDouble()) } ?: SimulationConfig()
    val cityFile = File(positional.firstOrNull() ?: DEFAULT_CITY_FILE)
    val city: CityMap? = cityFile.takeIf { it.isFile }?.let { CityMapCodec.decode(it.readBytes()) }
    val missingMessage =
        "Keine Stadtdatei gefunden:\n${cityFile.absolutePath}\n\n" +
            "Erzeugen mit: scripts/import-pforzheim.sh"
    val simulation = city?.let { Simulation(it, config) }
    simulation?.let { sim -> repeat(warmupMinutes * TICKS_PER_MINUTE * sim.config.ticksPerSecond) { sim.step() } }
    val runner = simulation?.let { SimulationRunner(it) }

    application {
        runner?.let { LaunchedEffect(it) { it.run() } }
        Window(
            onCloseRequest = ::exitApplication,
            title = city?.let { "${GameInfo.TITLE} – ${it.name}" } ?: GameInfo.TITLE,
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
            onKeyEvent = { event -> runner != null && handleKey(event, runner) },
        ) {
            TrafficManiacApp(city, missingMessage, runner?.frames, simulation?.signalMarkers)
        }
    }
}

private fun handleKey(
    event: KeyEvent,
    runner: SimulationRunner,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val speed = speedKeys[event.key]
    when {
        event.key == Key.Spacebar -> runner.togglePause()
        speed != null -> runner.setSpeed(speed)
        else -> return false
    }
    return true
}
