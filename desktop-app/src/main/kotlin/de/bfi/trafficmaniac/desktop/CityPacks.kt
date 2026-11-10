package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.CityPackInfo
import de.bfi.trafficmaniac.game.CityTexts
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.sim.SimulationConfig
import java.io.File

/** Ein Stadtpaket mit fertiger Karte. */
class CityPack(
    val info: CityPackInfo,
    private val mapFile: File,
) {
    val id: String get() = info.id
    val name: String get() = info.texts(I18n.language).name

    /** Texte und Beschreibung in der gerade eingestellten Sprache. */
    val texts: CityTexts get() = info.texts(I18n.language)
    val description: String get() = info.description(I18n.language)

    /** Die Karte; wird beim ersten Zugriff geladen. */
    val map: CityMap by lazy { CityMapCodec.decode(mapFile.readBytes()) }

    fun simulationConfig(base: SimulationConfig): SimulationConfig = info.simulationConfig(base)

    fun gameRules(base: GameRules): GameRules = info.gameRules(base, I18n.language)
}

/** Findet die Stadtpakete im Ordner `cities/`. */
object CityPacks {
    const val DIRECTORY = "cities"
    const val INFO_FILE = "city.json"
    const val MAP_FILE = "map.city"

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
        return runCatching { CityPackInfo.parse(File(directory, INFO_FILE).readText()) }
            .onFailure { println("${directory.name}/$INFO_FILE ist beschädigt: ${it.message}") }
            .getOrNull()
            ?.let { CityPack(it, map) }
    }

    /** `map.city` im Paket; ältere Checkouts haben die Karte noch unter `data/cities/<id>.city`. */
    private fun mapOf(directory: File): File? =
        File(directory, MAP_FILE).takeIf { it.isFile }
            ?: File("data/cities/${directory.name}.city").takeIf { it.isFile }
}
