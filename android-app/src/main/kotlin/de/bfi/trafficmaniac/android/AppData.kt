package de.bfi.trafficmaniac.android

import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highscores
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Einstellungen und Bestenliste als JSON im privaten App-Speicher; beschädigt oder fehlend = Standardwerte. */
class AppData(
    private val directory: File,
) {
    private val json =
        Json {
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
    ): T? =
        File(directory, name)
            .takeIf { it.isFile }
            ?.let { file -> runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull() }

    private fun <T> write(
        name: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        runCatching {
            val temp = File(directory, "$name.tmp")
            temp.writeText(json.encodeToString(serializer, value))
            temp.renameTo(File(directory, name))
        }
    }
}
