package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.GameTelemetry
import de.bfi.trafficmaniac.game.TelemetryJson
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * Anonyme Statistiken (Paket F): Pakete landen im Postausgang `~/.traffic-maniac/outbox/` – ein Stand je Partie – und
 * werden beim Start, zu Beginn einer Partie und nach jedem Spieltag gesendet, nur mit Zustimmung und nur, wenn das
 * Spiel eine Server-Adresse kennt (`-Dtrafficmaniac.statsUrl`, beim Release gesetzt). Ohne Netz bleiben sie liegen
 * und gehen beim nächsten Mal raus.
 */
class Statistics(
    private val directory: File = File(System.getProperty("user.home"), ".traffic-maniac"),
    private val url: String? = System.getProperty("trafficmaniac.statsUrl")?.takeIf { it.isNotBlank() },
) {
    private val outbox = File(directory, "outbox")
    private val idFile = File(directory, "statistics-id.txt")

    /** Zufällige Kennung dieser Installation; entsteht beim ersten Gebrauch. */
    val installation: String
        get() =
            idFile
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: newInstallation()

    /** Neue Kennung: frühere Pakete lassen sich danach nicht mehr dieser Installation zuordnen. */
    fun newInstallation(): String {
        val id = UUID.randomUUID().toString()
        runCatching {
            directory.mkdirs()
            idFile.writeText(id)
        }
        return id
    }

    /** Legt [telemetry] in den Postausgang und merkt es sich als zuletzt erzeugtes Paket. */
    @Synchronized
    fun store(telemetry: GameTelemetry) {
        runCatching {
            outbox.mkdirs()
            val text = TelemetryJson.encodeToString(GameTelemetry.serializer(), telemetry)
            // Ein Stand je Partie: der neuere ersetzt den älteren, solange er noch nicht gesendet ist
            File(outbox, "${telemetry.game}.json").writeText(text)
            File(directory, LAST_FILE).writeText(text)
        }.onFailure { println("Statistik konnte nicht abgelegt werden: ${it.message}") }
    }

    /** Das zuletzt erzeugte Paket im Klartext – für „Anzeigen, was gesendet wird“. */
    fun lastPacket(): String? = File(directory, LAST_FILE).takeIf { it.isFile }?.readText()

    /** Wartende Pakete im Postausgang. */
    fun pending(): List<File> = outbox.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()

    /**
     * Sendet alle wartenden Pakete; gesendete werden gelöscht. Liefert die Zahl der gesendeten. Wirft nie: Die
     * Statistik darf den Spielstart nicht verhindern, auch wenn z. B. der Laufzeit ein Modul fehlt.
     */
    @Synchronized
    fun sendPending(): Int =
        runCatching { send() }
            .onFailure { println("Statistik konnte nicht gesendet werden: $it") }
            .getOrDefault(0)

    private fun send(): Int {
        val target = url ?: return 0
        val client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
        var sent = 0
        for (file in pending()) {
            val request =
                HttpRequest
                    .newBuilder(URI("$target/v1/games"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(file.readText()))
                    .build()
            val status = runCatching { client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() }
            when (status.getOrNull()) {
                in SUCCESS -> {
                    file.delete()
                    sent++
                }

                // Vom Server abgelehnt (z. B. altes Format): nicht ewig wiederholen
                in REJECTED -> {
                    file.delete()
                }

                // Kein Netz oder Server weg: später wieder versuchen
                else -> {
                    return sent
                }
            }
        }
        return sent
    }

    /** Alles Gesammelte löschen (Zustimmung zurückgezogen). */
    fun clear() {
        pending().forEach { it.delete() }
        File(directory, LAST_FILE).delete()
    }

    private companion object {
        const val LAST_FILE = "statistics-last.json"
        val TIMEOUT: Duration = Duration.ofSeconds(10)
        val SUCCESS = 200..299
        val REJECTED = 400..499
    }
}
