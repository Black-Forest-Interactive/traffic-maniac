package de.bfi.trafficmaniac.osmimport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

private const val USAGE = "Aufruf: ./gradlew importCity -Pcity=<id> [-Prefresh]   (z. B. -Pcity=pforzheim)"
private const val BUFFER_BYTES = 1 shl 16
private const val BYTES_PER_MB = 1024 * 1024
private const val PERCENT = 100
private const val PROGRESS_STEP_MB = 10
private const val HTTP_OK = 200

/** Der Teil von `cities/<id>/city.json`, den der Import braucht. */
@Serializable
internal data class CityImportInfo(
    val texts: Texts,
    val import: Source,
) {
    @Serializable
    data class Texts(
        val name: String,
    )

    @Serializable
    data class Source(
        /** Geofabrik-Auszug, der die Stadt enthält. */
        val extract: String,
        /** Ausschnitt `minLon,minLat,maxLon,maxLat`. */
        val bbox: String,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): CityImportInfo = json.decodeFromString(serializer(), text)
    }
}

/**
 * Erzeugt die Karte eines Stadtpakets ohne Shell-Skript (läuft so auch unter Windows): liest
 * `cities/<id>/city.json`, lädt den OSM-Auszug nach `data/raw/` (nur wenn er fehlt oder mit `--refresh`) und schreibt
 * `cities/<id>/map.city`. Argumente: `<id> [--refresh]`, Arbeitsverzeichnis ist das Projektverzeichnis.
 */
fun main(args: Array<String>) {
    val id = args.firstOrNull { !it.startsWith("--") }
    if (id == null) {
        println(USAGE)
        exitProcess(1)
    }
    val pack = File("cities", id)
    val infoFile = File(pack, "city.json")
    if (!infoFile.isFile) {
        println("$infoFile fehlt")
        exitProcess(1)
    }
    val info = CityImportInfo.parse(infoFile.readText())
    val raw = File("data/raw", info.import.extract.substringAfterLast('/'))
    if (!raw.isFile || "--refresh" in args) download(info.import.extract, raw)
    val config = ImportConfig(name = info.texts.name, box = GeoBox.parse(info.import.bbox))
    importCity(raw, File(pack, "map.city"), config)
}

/** Lädt [url] nach [target]; erst in eine Teildatei, damit ein Abbruch keinen halben Auszug hinterlässt. */
internal fun download(
    url: String,
    target: File,
) {
    println("Lade $url …")
    target.parentFile?.mkdirs()
    val part = File(target.path + ".part")
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    val response = client.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofInputStream())
    check(response.statusCode() == HTTP_OK) { "Download fehlgeschlagen: HTTP ${response.statusCode()} für $url" }
    val total = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
    var loaded = 0L
    var nextReport = PROGRESS_STEP_MB.toLong() * BYTES_PER_MB
    response.body().use { input ->
        part.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                loaded += read
                if (loaded >= nextReport) {
                    println(progress(loaded, total))
                    nextReport += PROGRESS_STEP_MB.toLong() * BYTES_PER_MB
                }
            }
        }
    }
    Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    println("Geladen: $target (${loaded / BYTES_PER_MB} MB)")
}

private fun progress(
    loaded: Long,
    total: Long,
): String {
    val mb = loaded / BYTES_PER_MB
    return if (total > 0) "  $mb / ${total / BYTES_PER_MB} MB (${loaded * PERCENT / total} %)" else "  $mb MB"
}
