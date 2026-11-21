package de.bfi.trafficmaniac.android

import de.bfi.trafficmaniac.game.GameTelemetry
import de.bfi.trafficmaniac.game.TelemetryJson
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Anonyme Statistiken wie am Desktop (Paket F): Pakete landen im Postausgang `outbox/` im privaten App-Speicher – ein
 * Stand je Partie – und werden beim Start, zu Beginn einer Partie und nach jedem Spieltag gesendet, nur mit
 * Zustimmung und nur, wenn die App eine Server-Adresse kennt (`-PstatsUrl` beim Bauen). Ohne Netz bleiben sie
 * liegen und gehen beim nächsten Mal raus.
 */
class Statistics(
    private val directory: File,
    private val url: String? = BuildConfig.STATS_URL.takeIf { it.isNotBlank() },
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
        }
    }

    /** Das zuletzt erzeugte Paket im Klartext – für „Anzeigen, was gesendet wird“. */
    fun lastPacket(): String? = File(directory, LAST_FILE).takeIf { it.isFile }?.readText()

    /** Wartende Pakete im Postausgang. */
    fun pending(): List<File> = outbox.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()

    /** Sendet alle wartenden Pakete; gesendete werden gelöscht. Liefert die Zahl der gesendeten. Nicht im UI-Thread. */
    @Synchronized
    fun sendPending(): Int {
        val target = url ?: return 0
        var sent = 0
        for (file in pending()) {
            when (post("$target/v1/games", file.readText())) {
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

    /** HTTP-Status der Antwort, `null` ohne Verbindung. */
    private fun post(
        address: String,
        body: String,
    ): Int? {
        val connection = runCatching { URL(address).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            connection.responseCode
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LAST_FILE = "statistics-last.json"
        const val TIMEOUT_MILLIS = 10_000
        val SUCCESS = 200..299
        val REJECTED = 400..499
    }
}
