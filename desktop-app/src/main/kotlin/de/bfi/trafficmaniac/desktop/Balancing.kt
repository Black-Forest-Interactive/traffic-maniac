package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
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
        val values =
            overrides.mapValues { (_, value) ->
                value.toLongOrNull()?.let(::JsonPrimitive) ?: value.toDoubleOrNull()?.let(::JsonPrimitive)
                    ?: JsonPrimitive(value)
            }
        return override(SimulationConfig.serializer(), config, JsonObject(values))
    }

    /** Ersetzt in [base] die Werte aus [overrides] (gleiche Namen wie in den JSON-Dateien), der Rest bleibt. */
    fun <T> override(
        serializer: KSerializer<T>,
        base: T,
        overrides: JsonObject,
    ): T {
        if (overrides.isEmpty()) return base
        val tree = json.encodeToJsonElement(serializer, base).jsonObject.toMutableMap()
        val unknown = overrides.keys - tree.keys
        require(unknown.isEmpty()) { "Unbekannte Stellschrauben: ${unknown.joinToString()}" }
        tree.putAll(overrides)
        return json.decodeFromJsonElement(serializer, JsonObject(tree))
    }

    /** Szenarien für das Startmenü (`balancing/scenarios.json`, beim ersten Start mit den Standards angelegt). */
    fun scenarios(directory: File = File(DIRECTORY)): List<Scenario> {
        val own = load(File(directory, "scenarios.json"), ListSerializer(Scenario.serializer()), Scenario.DEFAULTS)
        // Neue Standardszenarien (z. B. das Tutorial) erscheinen auch bei einer älteren eigenen Datei
        val ids = own.map { it.id }.toSet()
        val defaults = Scenario.DEFAULTS.associateBy { it.id }
        // Etiketten für die Menü-Karten aus den Standards, solange die eigene Datei keine hat
        val tagged = own.map { if (it.tag == null) it.copy(tag = defaults[it.id]?.tag) else it }
        return Scenario.DEFAULTS.filter { it.id !in ids } + tagged
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
