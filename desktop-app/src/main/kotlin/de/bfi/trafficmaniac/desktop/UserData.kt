package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highscores
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Einstellungen und Bestenliste des Spielers, gespeichert als JSON im Ordner `~/.traffic-maniac/`.
 * Fehlt eine Datei oder ist sie beschädigt, gelten die Standardwerte – das Spiel startet in jedem Fall.
 */
class UserData(
    private val directory: File = File(System.getProperty("user.home"), ".traffic-maniac"),
) {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun settings(): GameSettings = read("settings.json", GameSettings.serializer()) ?: GameSettings()

    fun save(settings: GameSettings) = write("settings.json", GameSettings.serializer(), settings)

    fun highscores(): Highscores = read("highscores.json", Highscores.serializer()) ?: Highscores()

    fun save(highscores: Highscores) = write("highscores.json", Highscores.serializer(), highscores)

    private fun <T> read(
        name: String,
        serializer: KSerializer<T>,
    ): T? {
        val file = File(directory, name)
        if (!file.isFile) return null
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .onFailure { println("$name ist beschädigt und wird ignoriert: ${it.message}") }
            .getOrNull()
    }

    private fun <T> write(
        name: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        runCatching {
            directory.mkdirs()
            // Erst in eine Zwischendatei, dann umbenennen: ein Absturz beim Schreiben zerstört nichts
            val temp = File(directory, "$name.tmp")
            temp.writeText(json.encodeToString(serializer, value) + "\n")
            temp.renameTo(File(directory, name).also { it.delete() })
        }.onFailure { println("$name konnte nicht gespeichert werden: ${it.message}") }
    }
}
