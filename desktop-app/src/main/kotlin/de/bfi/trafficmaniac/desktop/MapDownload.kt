package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMapCodec
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.moveTo

/**
 * Lädt fertige Stadtkarten vom GitHub-Release der laufenden Version – für Spieler, denen die Karte fehlt (oder die
 * eine weitere Stadt wollen), ohne Import und ohne Shell-Skript. Die Karte landet im Benutzerordner
 * `~/.traffic-maniac/cities/<id>/map.city`; der Installationsordner bleibt unangetastet (keine Schreibrechte nötig).
 */
object MapDownload {
    private const val RELEASES = "https://github.com/Black-Forest-Interactive/traffic-maniac/releases/download"
    private const val HTTP_OK = 200
    private const val MAX_BYTES = 100L * 1024 * 1024
    private val TIMEOUT: Duration = Duration.ofMinutes(2)

    /** Wohin geladene Karten kommen. */
    val directory: File = File(System.getProperty("user.home"), ".traffic-maniac/cities")

    /** Version aus dem Installer (`-Dtrafficmaniac.version`, siehe `desktop-app/build.gradle.kts`); sonst `null`. */
    val version: String? = System.getProperty("trafficmaniac.version")?.takeIf { it.isNotBlank() }

    /** Ob es für diese Version überhaupt ein Release zum Herunterladen gibt. */
    val isAvailable: Boolean get() = version != null

    fun url(cityId: String): String = "$RELEASES/v$version/$cityId.city"

    /** Die geladene Karte zu [cityId], falls es sie schon gibt. */
    fun downloaded(cityId: String): File? = File(directory, "$cityId/${CityPacks.MAP_FILE}").takeIf { it.isFile }

    /**
     * Lädt die Karte und prüft sie, bevor sie übernommen wird: Eine abgebrochene oder fremde Datei würde sonst beim
     * nächsten Start als kaputte Stadt auftauchen. Blockiert – im Hintergrund aufrufen.
     */
    fun download(cityId: String): Result<File> =
        runCatching {
            check(isAvailable) { "Entwicklerversion – keine Release-Karte vorhanden" }
            val target = File(directory, "$cityId/${CityPacks.MAP_FILE}")
            target.parentFile.mkdirs()
            val part = File(target.parentFile, "${CityPacks.MAP_FILE}.part")
            val client =
                HttpClient
                    .newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(TIMEOUT)
                    .build()
            val request =
                HttpRequest
                    .newBuilder(URI(url(cityId)))
                    .timeout(TIMEOUT)
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(part.toPath()))
            if (response.statusCode() != HTTP_OK) {
                part.delete()
                throw IOException("Server antwortet mit ${response.statusCode()} für ${url(cityId)}")
            }
            if (part.length() > MAX_BYTES) {
                part.delete()
                throw IOException("Karte ist unerwartet groß (${part.length()} Bytes)")
            }
            runCatching { CityMapCodec.decode(part.readBytes()) }.onFailure {
                part.delete()
                throw IOException("Geladene Karte ist beschädigt: ${it.message}", it)
            }
            part.toPath().moveTo(target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            target
        }
}
