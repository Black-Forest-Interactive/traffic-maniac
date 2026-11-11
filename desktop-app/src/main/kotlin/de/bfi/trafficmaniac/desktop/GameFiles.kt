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

    /** Der Ordner [name], z. B. `cities`. */
    fun dir(name: String): File = if (File(name).isDirectory || installed == null) File(name) else File(installed, name)
}
