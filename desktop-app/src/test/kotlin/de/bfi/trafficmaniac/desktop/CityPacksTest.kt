package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.sim.SimulationConfig
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CityPacksTest {
    private val root: File = createTempDirectory("cities").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun pack(
        id: String,
        json: String,
        withMap: Boolean = true,
    ) {
        val directory = File(root, id).apply { mkdirs() }
        File(directory, CityPacks.INFO_FILE).writeText(json)
        if (withMap) File(directory, CityPacks.MAP_FILE).writeText("Karte")
    }

    @Test
    fun packsBringTextsAndTuning() {
        pack(
            "musterstadt",
            """
            {
                "id": "musterstadt",
                "texts": { "name": "Musterstadt", "newspaper": "MUSTERSTÄDTER BOTE" },
                "simulation": { "peakTripsPerHour": 3000 },
                "rules": { "startBudget": 120000 }
            }
            """.trimIndent(),
        )
        pack("ohne-karte", """{ "id": "ohne-karte" }""", withMap = false)
        pack("kaputt", "{ kaputt")

        val packs = CityPacks.discover(root)
        assertEquals(listOf("Musterstadt"), packs.map { it.name }, "nur Pakete mit Karte und lesbarer Beschreibung")
        assertEquals(listOf("ohne-karte"), CityPacks.missingMaps(root).map { it.name })

        val pack = packs.single()
        val config = pack.simulationConfig(SimulationConfig())
        assertEquals(3000.0, config.peakTripsPerHour)
        assertEquals(SimulationConfig().ticksPerSecond, config.ticksPerSecond, "der Rest bleibt")
        val rules = pack.gameRules(GameRules())
        assertEquals(120_000, rules.startBudget)
        assertEquals("MUSTERSTÄDTER BOTE", rules.city.newspaper)
        assertEquals(GameRules().cameraCost, rules.cameraCost)
    }

    @Test
    fun typosInTuningAreReported() {
        pack("tippfehler", """{ "id": "tippfehler", "rules": { "startBugdet": 1 } }""")
        val pack = CityPacks.discover(root).single()
        assertFailsWith<IllegalArgumentException> { pack.gameRules(GameRules()) }
    }
}
