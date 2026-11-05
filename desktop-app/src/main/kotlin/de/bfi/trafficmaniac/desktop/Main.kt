package de.bfi.trafficmaniac.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.GameInfo
import de.bfi.trafficmaniac.ui.TrafficManiacApp
import java.io.File

private const val DEFAULT_CITY_FILE = "data/cities/pforzheim.city"

/** Startet das Spiel. Optional: Pfad zur Stadtdatei als erstes Argument. */
fun main(args: Array<String>) {
    val cityFile = File(args.firstOrNull() ?: DEFAULT_CITY_FILE)
    val city: CityMap? = cityFile.takeIf { it.isFile }?.let { CityMapCodec.decode(it.readBytes()) }
    val missingMessage =
        "Keine Stadtdatei gefunden:\n${cityFile.absolutePath}\n\n" +
            "Erzeugen mit: scripts/import-pforzheim.sh"

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = city?.let { "${GameInfo.TITLE} – ${it.name}" } ?: GameInfo.TITLE,
            state = rememberWindowState(size = DpSize(1280.dp, 800.dp)),
        ) {
            TrafficManiacApp(city, missingMessage)
        }
    }
}
