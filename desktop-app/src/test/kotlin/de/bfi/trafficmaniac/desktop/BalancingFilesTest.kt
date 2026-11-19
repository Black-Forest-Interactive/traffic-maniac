package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Balancing-Dateien im Repo sollen alle Stellschrauben mit ihren aktuellen Werten zeigen. Fehlt ein neuer Wert,
 * gilt still der Standard aus dem Code – und niemand sieht ihn beim Balancing.
 */
class BalancingFilesTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun gameRulesFileListsEveryRule() = assertComplete("game-rules.json", GameRules.serializer(), GameRules(), CITY)

    @Test
    fun simulationFileListsEveryValue() =
        assertComplete("simulation.json", SimulationConfig.serializer(), SimulationConfig(), WEATHER)

    private fun <T> assertComplete(
        name: String,
        serializer: KSerializer<T>,
        defaults: T,
        optional: String,
    ) {
        val file = File(GameFiles.dir("balancing"), name)
        val inFile = json.parseToJsonElement(file.readText()).jsonObject.keys
        val inCode = json.encodeToJsonElement(serializer, defaults).jsonObject.keys - optional
        assertEquals(emptySet(), inCode - inFile, "In $file fehlen Stellschrauben")
        assertEquals(emptySet(), inFile - inCode - optional, "In $file stehen unbekannte Stellschrauben")
    }

    private companion object {
        /** Kommt aus dem Stadtpaket (`city.json`), nicht aus den Spielregeln. */
        const val CITY = "city"

        /** Nur Szenarien mit Wetter setzen es. */
        const val WEATHER = "weather"
    }
}
