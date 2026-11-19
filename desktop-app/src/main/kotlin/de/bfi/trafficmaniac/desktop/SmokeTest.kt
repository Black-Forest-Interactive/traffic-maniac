package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.CityGame
import java.net.http.HttpClient
import javax.net.ssl.SSLContext

private const val SMOKE_STEPS = 100
private const val EXIT_NO_CITY = 2
private const val EXIT_CRASH = 3
private const val EXIT_WRONG_RUNTIME = 4

/** Hersteller der Java-Laufzeit, die Installer und ZIP mitbringen dürfen (Lizenz, siehe licenses/THIRD-PARTY.md). */
private const val BUNDLED_JAVA_VENDOR = "Eclipse Adoptium"

/**
 * `--smoke-test`: Prüft ohne Fenster, ob ein installiertes Spiel startfähig ist – Laufzeit prüfen ([runtimeProblem]),
 * Stadtpaket finden, Karte laden, Balancing lesen, [SMOKE_STEPS] Simulationsschritte rechnen. Liefert den Exit-Code:
 * 0 = alles gut, 2 = keine Stadt mit Karte, 3 = Absturz, 4 = falsche Java-Laufzeit. Läuft im Release-Workflow gegen
 * ZIP und installiertes MSI.
 */
internal fun smokeTest(): Int {
    runtimeProblem()?.let { return it }
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

/**
 * Prüft die Java-Laufzeit, mit der das Spiel läuft; liefert einen Exit-Code, wenn etwas nicht stimmt.
 * - Im installierten Spiel muss es die mitgelieferte Eclipse Temurin sein: Ein anderes JDK (z. B. von Oracle) hätte
 *   andere Lizenzbedingungen (siehe licenses/THIRD-PARTY.md).
 * - Ein HTTPS-Client muss sich anlegen lassen (Statistik, Karten-Download): Fehlt `java.net.http` in der gebündelten
 *   Laufzeit, fiele das sonst erst beim Spieler auf.
 */
private fun runtimeProblem(): Int? {
    val vendor = System.getProperty("java.vendor").orEmpty()
    println("Java-Laufzeit: $vendor ${System.getProperty("java.runtime.version")}")
    if (GameFiles.isInstalled && vendor != BUNDLED_JAVA_VENDOR) {
        System.err.println("Smoke-Test: mitgelieferte Java-Laufzeit ist von „$vendor“, erwartet „$BUNDLED_JAVA_VENDOR“")
        return EXIT_WRONG_RUNTIME
    }
    return runCatching { HttpClient.newBuilder().sslContext(SSLContext.getDefault()).build() }
        .exceptionOrNull()
        ?.let {
            System.err.println("Smoke-Test: kein HTTPS-Client – ${it.stackTraceToString()}")
            EXIT_CRASH
        }
}
