package de.bfi.trafficmaniac.desktop

import java.io.File

/**
 * Wo die Spieldaten liegen (`cities/`, `balancing/`, `lang/`, `assets/`): im Arbeitsverzeichnis, solange es dort
 * `cities/` gibt (Entwicklung, `./gradlew :desktop-app:run`), sonst im Installationsordner, in den der Installer sie
 * als App-Ressourcen gelegt hat.
 */
object GameFiles {
    private val installed: File? =
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }

    /** Ob das Spiel aus Installer oder ZIP läuft (mit eigener Java-Laufzeit) statt aus der Entwicklungsumgebung. */
    val isInstalled: Boolean get() = installed != null

    /**
     * Der Ordner [name], z. B. `cities`. Startet die Entwicklungsumgebung im Modulordner `desktop-app/`, gilt der
     * Ordner eine Ebene höher – sonst entstünde dort eine zweite `balancing/` mit Standardwerten.
     */
    fun dir(name: String): File =
        when {
            File(name).isDirectory -> File(name)
            File("..", name).isDirectory && File("..", "cities").isDirectory -> File("..", name)
            installed != null -> File(installed, name)
            else -> File(name)
        }
}
