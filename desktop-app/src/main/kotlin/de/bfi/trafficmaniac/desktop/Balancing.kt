package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Spielwerte aus den JSON-Dateien im Ordner `balancing`. Fehlt eine Datei, wird sie mit den Standardwerten angelegt –
 * so stehen immer alle Stellschrauben mit ihren aktuellen Werten im Repo.
 */
object Balancing {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun gameRules(directory: File = File(DIRECTORY)): GameRules =
        load(File(directory, "game-rules.json"), GameRules.serializer(), GameRules())

    fun simulationConfig(
        overrides: Map<String, String> = emptyMap(),
        directory: File = File(DIRECTORY),
    ): SimulationConfig {
        val config = load(File(directory, "simulation.json"), SimulationConfig.serializer(), SimulationConfig())
        if (overrides.isEmpty()) return config
        // Einzelne Werte überschreiben: als JSON-Objekt ändern und neu einlesen
        val tree = json.encodeToJsonElement(SimulationConfig.serializer(), config).jsonObject.toMutableMap()
        overrides.forEach { (key, value) ->
            tree[key] =
                value.toLongOrNull()?.let(::JsonPrimitive) ?: value.toDoubleOrNull()?.let(::JsonPrimitive)
                    ?: JsonPrimitive(value)
        }
        return json.decodeFromJsonElement(SimulationConfig.serializer(), JsonObject(tree))
    }

    private fun <T> load(
        file: File,
        serializer: KSerializer<T>,
        defaults: T,
    ): T {
        if (!file.isFile) {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(serializer, defaults) + "\n")
        }
        return json.decodeFromString(serializer, file.readText())
    }

    private const val DIRECTORY = "balancing"
}
