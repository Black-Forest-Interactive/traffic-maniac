package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.CityTexts
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Beschreibung eines Stadtpakets (`cities/<id>/city.json`): Texte, Feinabstimmung und woher die Kartendaten kommen.
 * Die Karte selbst liegt daneben als `map.city` und wird mit `scripts/import-city.sh <id>` erzeugt.
 */
@Serializable
data class CityPackInfo(
    val id: String,
    val texts: CityTexts = CityTexts(),
    /** Ein, zwei Sätze für die Stadtauswahl. */
    val description: String = "",
    /** Einzelne Werte aus `balancing/simulation.json`, die für diese Stadt anders sind. */
    val simulation: JsonObject = JsonObject(emptyMap()),
    /** Einzelne Werte aus `balancing/game-rules.json`, die für diese Stadt anders sind. */
    val rules: JsonObject = JsonObject(emptyMap()),
    /** Woher die Kartendaten kommen; nur für das Import-Skript. */
    val import: CityImport? = null,
)

/** OSM-Auszug (Geofabrik) und Ausschnitt `minLon,minLat,maxLon,maxLat`. */
@Serializable
data class CityImport(
    val extract: String,
    val bbox: String,
)

/** Ein Stadtpaket mit fertiger Karte. */
class CityPack(
    val info: CityPackInfo,
    private val mapFile: File,
) {
    val id: String get() = info.id
    val name: String get() = info.texts.name

    /** Die Karte; wird beim ersten Zugriff geladen. */
    val map: CityMap by lazy { CityMapCodec.decode(mapFile.readBytes()) }

    fun simulationConfig(base: SimulationConfig): SimulationConfig =
        Balancing.override(SimulationConfig.serializer(), base, info.simulation)

    fun gameRules(base: GameRules): GameRules =
        Balancing.override(GameRules.serializer(), base, info.rules).copy(city = info.texts)
}

/** Findet die Stadtpakete im Ordner `cities/`. */
object CityPacks {
    const val DIRECTORY = "cities"
    const val INFO_FILE = "city.json"
    const val MAP_FILE = "map.city"

    private val json = Json { ignoreUnknownKeys = true }

    /** Alle Pakete mit Karte, alphabetisch; beschädigte Beschreibungen werden gemeldet und übersprungen. */
    fun discover(root: File = File(DIRECTORY)): List<CityPack> =
        root
            .listFiles { file -> File(file, INFO_FILE).isFile }
            .orEmpty()
            .mapNotNull { directory -> load(directory) }
            .sortedBy { it.name }

    /** Pakete, deren Karte noch fehlt (für den Hinweis beim Start). */
    fun missingMaps(root: File = File(DIRECTORY)): List<File> =
        root
            .listFiles { file -> File(file, INFO_FILE).isFile && mapOf(file) == null }
            .orEmpty()
            .toList()

    private fun load(directory: File): CityPack? {
        val map = mapOf(directory) ?: return null
        return runCatching { json.decodeFromString(CityPackInfo.serializer(), File(directory, INFO_FILE).readText()) }
            .onFailure { println("${directory.name}/$INFO_FILE ist beschädigt: ${it.message}") }
            .getOrNull()
            ?.let { CityPack(it, map) }
    }

    /** `map.city` im Paket; ältere Checkouts haben die Karte noch unter `data/cities/<id>.city`. */
    private fun mapOf(directory: File): File? =
        File(directory, MAP_FILE).takeIf { it.isFile }
            ?: File("data/cities/${directory.name}.city").takeIf { it.isFile }
}
