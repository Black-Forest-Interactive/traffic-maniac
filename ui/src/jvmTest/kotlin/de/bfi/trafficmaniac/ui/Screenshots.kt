package de.bfi.trafficmaniac.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.ui.map.StreetRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Screenshots von Menü und Spiel-HUD für Design-Reviews – kein echter Test, läuft nur mit gesetzter Umgebungsvariable:
 *
 * `TM_SCREENSHOTS=/pfad/zum/ordner ./gradlew :ui:jvmTest --tests '*Screenshots*' --rerun`
 *
 * Gerendert wird bei 1440 × 900 und 2000 × 1050 Pixeln mit Dichte 1 (1 dp = 1 px), Stadt: `cities/pforzheim`.
 * UI-Skalierung wie im Spiel automatisch; `TM_UI_SCALE=100` erzwingt eine feste Größe in Prozent.
 */
class Screenshots {
    private val target = System.getenv("TM_SCREENSHOTS")?.let(::File)

    @Test
    fun render() {
        val out = target ?: return
        out.mkdirs()
        val city = loadCity() ?: return
        val frame = frame(city)
        for ((width, height) in SIZES) {
            val suffix = "${width}x$height"
            shoot(File(out, "menu-$suffix.png"), width, height) {
                MainMenu(
                    Scenario.DEFAULTS,
                    Highscores(),
                    GameSettings(),
                    {},
                    {},
                    {},
                    attribution = city.attribution,
                    cities = CityChoice(listOf("Pforzheim"), 0, "Goldstadt an Enz und Nagold", nickname = "Goldstadt"),
                    cityMap = city,
                )
            }
            for (panel in listOf(SidePanel.NONE, SidePanel.OFFICE, SidePanel.BUDGET)) {
                val input =
                    HudInput().apply {
                        panelRequest = panel
                        // Ohne Fenster: das Ampelprogramm-Werkzeug mit seiner Auswahl zeigen
                        toolRequest = if (panel == SidePanel.NONE) Tool.SIGNAL_TIMING else Tool.ROADWORKS
                    }
                shoot(File(out, "game-${panel.name.lowercase()}-$suffix.png"), width, height) {
                    TrafficManiacApp(
                        city,
                        traffic = MutableStateFlow(frame),
                        input = input,
                        onSpeed = {},
                        onSave = {},
                        onMenu = {},
                    )
                }
            }
        }
    }

    private fun shoot(
        file: File,
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ) {
        val scale = System.getenv("TM_UI_SCALE")?.toIntOrNull() ?: 0
        val scene = ImageComposeScene(width, height, Density(1f)) { ScaledUi(scale) { content() } }
        try {
            // Ein paar Frames, damit Effekte (Fenster öffnen, Kartenbild im Hintergrund) ankommen
            var image = scene.render(0)
            for (i in 1..FRAMES) {
                Thread.sleep(FRAME_PAUSE_MILLIS)
                image = scene.render(i * FRAME_NANOS)
            }
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("Screenshot: ${file.absolutePath}")
        } finally {
            scene.close()
        }
    }

    private fun loadCity(): CityMap? {
        var dir = File("").absoluteFile
        while (dir.parentFile != null && !File(dir, "cities").isDirectory) dir = dir.parentFile
        val file = File(dir, "cities/pforzheim/map.city")
        return if (file.isFile) CityMapCodec.decode(file.readBytes()) else null
    }

    private fun frame(city: CityMap): TrafficFrame {
        val game = CityGame(city)
        // Ein paar typische Meldungen: dreimal Tempo 30 (gebündelt), ein Blitzer, eine Einstellung im Amt
        val main =
            city.roads.segments
                .filter { !it.passive && it.name != null }
                .map { it.id }
        main.take(3).forEach { game.submit(GameCommand.SetSpeedLimit(it, 30)) }
        game.submit(GameCommand.PlaceCamera(main[3]))
        game.submit(GameCommand.SetDeadlineDays(5))
        // Grüne Welle über die Karlsruher Straße (getrennte Richtungsfahrbahnen, mehrere Ampeln)
        val street = city.roads.segments.filter { !it.passive && it.name == "Karlsruher Straße" }
        if (street.size > 1) {
            StreetRoute(city).between(street.first().id, street.last().id, directed = true)?.let {
                game.submit(GameCommand.SetGreenWave(it, 50))
            }
        }
        repeat(WARMUP_TICKS) { game.step() }
        val snapshot = game.simulation.snapshot()
        return TrafficFrame(
            snapshot,
            snapshot,
            TimeSource.Monotonic.markNow(),
            1.seconds,
            0.0,
            1,
            1,
            null,
            game.state(),
        )
    }

    private companion object {
        val SIZES = listOf(1440 to 900, 2000 to 1050)
        const val FRAMES = 8
        const val FRAME_PAUSE_MILLIS = 150L
        const val FRAME_NANOS = 16_000_000L
        const val WARMUP_TICKS = 600
    }
}
