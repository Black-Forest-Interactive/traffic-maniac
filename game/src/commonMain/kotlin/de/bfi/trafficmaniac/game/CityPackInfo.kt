package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.Language
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Beschreibung eines Stadtpakets (`cities/<id>/city.json`): Texte, Feinabstimmung und woher die Kartendaten kommen.
 * Plattformfrei – woher die Datei kommt (Ordner am Desktop, Assets unter Android), entscheidet die App.
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
    /** Texte und Beschreibung in weiteren Sprachen, nach Sprachkürzel („en“). Fehlt eine, gilt die deutsche. */
    val translations: Map<String, CityTranslation> = emptyMap(),
) {
    /** Texte in [language]. */
    fun texts(language: Language): CityTexts = translations[language.code]?.texts ?: texts

    fun description(language: Language): String = translations[language.code]?.description ?: description

    /** Simulation dieser Stadt: [base] mit den Werten aus [simulation]. */
    fun simulationConfig(base: SimulationConfig): SimulationConfig =
        Overrides.apply(SimulationConfig.serializer(), base, simulation)

    /** Spielregeln dieser Stadt in [language]: [base] mit den Werten aus [rules] und den Stadttexten. */
    fun gameRules(
        base: GameRules,
        language: Language,
    ): GameRules = Overrides.apply(GameRules.serializer(), base, rules).copy(city = texts(language))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): CityPackInfo = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class CityTranslation(
    val texts: CityTexts,
    val description: String? = null,
)

/** OSM-Auszug (Geofabrik) und Ausschnitt `minLon,minLat,maxLon,maxLat`. */
@Serializable
data class CityImport(
    val extract: String,
    val bbox: String,
)

/** Einzelne Werte einer Konfiguration überschreiben (gleiche Namen wie in den Balancing-Dateien). */
object Overrides {
    private val json = Json { encodeDefaults = true }

    /** Ersetzt in [base] die Werte aus [overrides], der Rest bleibt; unbekannte Namen sind ein Fehler. */
    fun <T> apply(
        serializer: KSerializer<T>,
        base: T,
        overrides: JsonObject,
    ): T {
        if (overrides.isEmpty()) return base
        val tree = json.encodeToJsonElement(serializer, base).jsonObject.toMutableMap()
        val unknown = overrides.keys - tree.keys
        require(unknown.isEmpty()) { "Unbekannte Stellschrauben: ${unknown.joinToString()}" }
        tree.putAll(overrides)
        return json.decodeFromJsonElement(serializer, JsonObject(tree))
    }

    private val translationJson = Json { isLenient = true }

    private val balancingJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    /** Liest eine Balancing-Datei aus dem Ordner `balancing`; fehlende Werte bekommen ihren Standard. */
    fun <T> parse(
        serializer: KSerializer<T>,
        text: String,
    ): T = balancingJson.decodeFromString(serializer, text)

    /** Schreibt eine Balancing-Datei mit allen Werten. */
    fun <T> format(
        serializer: KSerializer<T>,
        value: T,
    ): String = balancingJson.encodeToString(serializer, value) + "\n"

    /**
     * Szenarien fürs Menü aus `balancing/scenarios.json` ([own]): neue Standardszenarien (z. B. das Tutorial)
     * erscheinen auch bei einer älteren Datei, fehlende Etiketten kommen aus den Standards.
     */
    fun scenarios(own: List<Scenario>): List<Scenario> {
        val ids = own.map { it.id }.toSet()
        val defaults = Scenario.DEFAULTS.associateBy { it.id }
        val tagged = own.map { if (it.tag == null) it.copy(tag = defaults[it.id]?.tag) else it }
        return Scenario.DEFAULTS.filter { it.id !in ids } + tagged
    }

    /** Übersetzungstabelle `lang/<Sprache>.json`: deutsche Vorlage → Übersetzung. */
    fun translations(text: String): Map<String, String> =
        translationJson.decodeFromString(MapSerializer(String.serializer(), String.serializer()), text)
}
