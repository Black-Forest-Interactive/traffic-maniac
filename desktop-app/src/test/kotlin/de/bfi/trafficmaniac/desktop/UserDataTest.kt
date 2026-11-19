package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.Difficulty
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.Highscores
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class UserDataTest {
    private val directory: File = createTempDirectory("user").toFile()

    @Test
    fun settingsAndHighscoresSurviveARestart() {
        val settings =
            GameSettings(sound = false, music = true, sprites = false, difficulty = Difficulty.HARD, startSpeed = 4)
        val scores =
            Highscores().add(
                HighscoreEntry("berufsverkehr", Difficulty.HARD, 1234, 5000, 61, "2026-11-07", true),
            )
        UserData(directory).apply {
            save(settings)
            save(scores)
        }
        val reloaded = UserData(directory)
        assertEquals(settings, reloaded.settings())
        assertEquals(scores, reloaded.highscores())
    }

    @Test
    fun missingOrBrokenFilesMeanDefaults() {
        assertEquals(GameSettings(), UserData(File(directory, "neu")).settings())
        File(directory, "highscores.json").writeText("{ kaputt")
        assertEquals(Highscores(), UserData(directory).highscores())
    }
}
