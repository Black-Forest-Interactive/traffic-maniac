package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.Overrides
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Spielwerte aus den JSON-Dateien im Ordner `balancing`. Fehlt eine Datei, wird sie mit den Standardwerten angelegt –
 * so stehen immer alle Stellschrauben mit ihren aktuellen Werten im Repo.
 */
object Balancing {
    fun gameRules(directory: File = GameFiles.dir(DIRECTORY)): GameRules =
        load(File(directory, "game-rules.json"), GameRules.serializer(), GameRules())

    fun simulationConfig(
        overrides: Map<String, String> = emptyMap(),
        directory: File = GameFiles.dir(DIRECTORY),
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
    ): T = Overrides.apply(serializer, base, overrides)

    /** Szenarien für das Startmenü (`balancing/scenarios.json`, beim ersten Start mit den Standards angelegt). */
    fun scenarios(directory: File = GameFiles.dir(DIRECTORY)): List<Scenario> =
        Overrides.scenarios(
            load(File(directory, "scenarios.json"), ListSerializer(Scenario.serializer()), Scenario.DEFAULTS),
        )

    private fun <T> load(
        file: File,
        serializer: KSerializer<T>,
        defaults: T,
    ): T {
        if (!file.isFile) {
            file.parentFile?.mkdirs()
            file.writeText(Overrides.format(serializer, defaults))
        }
        return Overrides.parse(serializer, file.readText())
    }

    private const val DIRECTORY = "balancing"
}
