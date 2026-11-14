package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.CityGame

private const val SMOKE_STEPS = 100
private const val EXIT_NO_CITY = 2
private const val EXIT_CRASH = 3

/**
 * `--smoke-test`: Prüft ohne Fenster, ob ein installiertes Spiel startfähig ist – Stadtpaket finden, Karte laden,
 * Balancing lesen, [SMOKE_STEPS] Simulationsschritte rechnen. Liefert den Exit-Code: 0 = alles gut,
 * 2 = keine Stadt mit Karte, 3 = Absturz. Läuft im Release-Workflow gegen das installierte MSI.
 */
internal fun smokeTest(): Int {
    val packs = CityPacks.discover()
    val pack = packs.firstOrNull()
    if (pack == null) {
        val missing = CityPacks.missingMaps().joinToString { it.name }.ifEmpty { "keine Stadtpakete" }
        System.err.println("Smoke-Test: keine Stadt mit Karte in ${GameFiles.dir(CityPacks.DIRECTORY)} ($missing)")
        return EXIT_NO_CITY
    }
    return runCatching {
        val game =
            CityGame(
                pack.map,
                pack.simulationConfig(Balancing.simulationConfig()),
                pack.gameRules(Balancing.gameRules()),
            )
        repeat(SMOKE_STEPS) { game.step() }
        val state = game.state()
        val segments = pack.map.roads.segments.size
        println("Smoke-Test ok: ${pack.name}, $segments Straßenabschnitte, Kasse ${state.balance} €")
        0
    }.getOrElse {
        System.err.println("Smoke-Test: Absturz – ${it.stackTraceToString()}")
        EXIT_CRASH
    }
}
