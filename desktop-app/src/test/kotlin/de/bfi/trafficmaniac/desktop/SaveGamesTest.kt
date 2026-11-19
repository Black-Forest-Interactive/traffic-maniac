package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.Difficulty
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.SaveInfo
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveGamesTest {
    private val directory: File = createTempDirectory("saves").toFile()

    /** Die echte Stadt (nicht im Repository, wird importiert); fehlt sie, wird der Test übersprungen. */
    private val cityFile =
        File("../cities/pforzheim/map.city").takeIf { it.isFile } ?: File("../data/cities/pforzheim.city")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun aLoadedGameContinuesExactlyLikeTheOriginal() {
        if (!cityFile.isFile) return println("Pforzheim fehlt – Spielstand-Test übersprungen")
        val city = CityMapCodec.decode(cityFile.readBytes())
        val config = Balancing.simulationConfig()
        val original = CityGame(city, config, Balancing.gameRules())
        val segment =
            city.roads.segments
                .first { !it.passive && it.name != null }
                .id
        original.submit(GameCommand.PlaceTrailer(segment))
        original.submit(GameCommand.SetSpeedLimit(segment, 30))
        repeat(TEN_MINUTES * config.ticksPerSecond) { original.step() }

        val saves = SaveGames({ id -> city.takeIf { id == "pforzheim" } }, directory)
        val info = info()
        saves.save(original, info)
        File(directory, "kaputt.json").writeText("{ kaputt")
        File(directory, "ohne-spielstand.json").writeText("{}")
        assertEquals(listOf(info), saves.list(), "Beschädigte Dateien werden übersprungen")
        val loaded = saves.load(info)

        // Beide laufen weiter – zufällige Entscheidungen inklusive müssen identisch ausfallen
        repeat(TEN_MINUTES * config.ticksPerSecond) {
            original.step()
            loaded.step()
        }
        val a = original.state()
        val b = loaded.state()
        assertEquals(a.balance, b.balance)
        assertEquals(a.cameras, b.cameras)
        assertEquals(a.messages, b.messages)
        val snapA = original.simulation.snapshot()
        val snapB = loaded.simulation.snapshot()
        assertTrue(snapA.ids.isNotEmpty())
        assertTrue(snapA.ids.contentEquals(snapB.ids), "Dieselben Fahrzeuge")
        assertTrue(snapA.x.contentEquals(snapB.x) && snapA.y.contentEquals(snapB.y), "an denselben Stellen")

        // Neue Befehle wirken auch im geladenen Spiel
        loaded.submit(GameCommand.RemoveCamera(b.cameras.single().cameraId))
        loaded.step()
        assertEquals(1, loaded.state().idleTrailers)
    }

    private fun info() =
        SaveInfo(
            id = SaveInfo.QUICK_SAVE,
            scenarioId = "erste-amtszeit",
            scenarioTitle = "Erste Amtszeit",
            difficulty = Difficulty.NORMAL,
            savedAt = "2026-11-07T12:00:00",
            gameTime = "Montag, Tag 1",
            balance = 0,
            reputation = 50,
        )

    private companion object {
        const val TEN_MINUTES = 600
    }
}
