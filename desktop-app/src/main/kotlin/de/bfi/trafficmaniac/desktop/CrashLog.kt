package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameInfo
import de.bfi.trafficmaniac.i18n.tr
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/**
 * Ein installiertes Spiel hat keine Konsole: Alles, was auf stdout/stderr geht, landet zusätzlich in
 * `~/.traffic-maniac/logs/traffic-maniac.log` (die vorige Sitzung bleibt als `.1.log` erhalten). Ein Absturz zeigt
 * einen Dialog mit dem Fehler und dem Pfad zum Protokoll, statt dass das Fenster kommentarlos verschwindet.
 */
object CrashLog {
    private const val EXIT_CRASH = 3
    val directory: File = File(System.getProperty("user.home"), ".traffic-maniac/logs")
    val file: File = File(directory, "traffic-maniac.log")

    fun install() {
        runCatching {
            directory.mkdirs()
            if (file.isFile) file.copyTo(File(directory, "traffic-maniac.1.log"), overwrite = true)
            val log = PrintStream(FileOutputStream(file), true, Charsets.UTF_8)
            log.println("${GameInfo.TITLE} ${MapDownload.version ?: GameInfo.VERSION} – Start ${LocalDateTime.now()}")
            val os = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}"
            log.println("Java ${System.getProperty("java.version")}, $os")
            System.setOut(PrintStream(Tee(System.out, log), true, Charsets.UTF_8))
            System.setErr(PrintStream(Tee(System.err, log), true, Charsets.UTF_8))
        }.onFailure { System.err.println("Protokoll nicht möglich: ${it.message}") }
        Thread.setDefaultUncaughtExceptionHandler { thread, error -> crashed(thread, error) }
    }

    private fun crashed(
        thread: Thread,
        error: Throwable,
    ) {
        System.err.println("Absturz im Thread ${thread.name}:")
        error.printStackTrace()
        if (GraphicsEnvironment.isHeadless()) exitProcess(EXIT_CRASH)
        val message =
            tr(
                "Traffic Maniac ist leider abgestürzt:\n{error}\n\nDas Protokoll liegt unter\n{path}\n" +
                    "Bitte bei einer Fehlermeldung mitschicken.",
                "error" to (error.message ?: error::class.simpleName.orEmpty()),
                "path" to file.absolutePath,
            )
        val show = {
            JOptionPane.showMessageDialog(null, message, GameInfo.TITLE, JOptionPane.ERROR_MESSAGE)
            exitProcess(EXIT_CRASH)
        }
        if (SwingUtilities.isEventDispatchThread()) show() else SwingUtilities.invokeAndWait(show)
    }

    /** Schreibt in beide Ströme; fällt einer aus (z. B. keine Konsole), läuft der andere weiter. */
    private class Tee(
        private val first: OutputStream,
        private val second: OutputStream,
    ) : OutputStream() {
        override fun write(b: Int) {
            runCatching { first.write(b) }
            runCatching { second.write(b) }
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            runCatching { first.write(b, off, len) }
            runCatching { second.write(b, off, len) }
        }

        override fun flush() {
            runCatching { first.flush() }
            runCatching { second.flush() }
        }
    }
}
