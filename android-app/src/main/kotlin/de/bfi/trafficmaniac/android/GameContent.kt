package de.bfi.trafficmaniac.android

import android.content.res.AssetManager
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.CityPackInfo
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.Overrides
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.i18n.Language
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.util.Locale

/** Eine Stadt aus den Assets (`cities/<id>/`); die Karte wird beim ersten Zugriff geladen. */
class AssetCity(
    val info: CityPackInfo,
    private val assets: AssetManager,
    private val folder: String,
) {
    val id: String get() = info.id
    val name: String get() = info.texts(I18n.language).name
    val description: String get() = info.description(I18n.language)

    val map: CityMap by lazy { assets.open("$folder/map.city").use { CityMapCodec.decode(it.readBytes()) } }
}

/**
 * Was die APK mitbringt (siehe `gameAssets` in `android-app/build.gradle.kts`): Städte, Balancing und Übersetzungen –
 * dieselben Dateien wie am Desktop, nur aus den Assets statt aus dem Arbeitsordner.
 */
class GameContent(
    private val assets: AssetManager,
) {
    val cities: List<AssetCity> =
        assets
            .list("cities")
            .orEmpty()
            .mapNotNull { id ->
                runCatching { CityPackInfo.parse(text("cities/$id/city.json")) }
                    .getOrNull()
                    ?.let { AssetCity(it, assets, "cities/$id") }
            }

    val scenarios: List<Scenario> =
        Overrides.scenarios(
            parse("balancing/scenarios.json", ListSerializer(Scenario.serializer()), Scenario.DEFAULTS),
        )

    fun simulationConfig(): SimulationConfig =
        parse("balancing/simulation.json", SimulationConfig.serializer(), SimulationConfig())

    fun gameRules(): GameRules = parse("balancing/game-rules.json", GameRules.serializer(), GameRules())

    /** Die eingestellte Sprache, sonst die des Geräts, sonst Englisch. */
    fun languageFor(setting: String?): Language =
        Language.ofCode(setting) ?: Language.ofCode(Locale.getDefault().language) ?: Language.EN

    /** Schaltet die Texte auf [language] um; Deutsch steht im Code und braucht keine Datei. */
    fun use(language: Language) {
        val table =
            if (language == Language.DE) {
                emptyMap()
            } else {
                runCatching { Overrides.translations(text("lang/${language.code}.json")) }.getOrDefault(emptyMap())
            }
        I18n.use(language, table)
    }

    private fun <T> parse(
        path: String,
        serializer: KSerializer<T>,
        default: T,
    ): T = runCatching { Overrides.parse(serializer, text(path)) }.getOrDefault(default)

    private fun text(path: String): String = assets.open(path).use { it.readBytes().decodeToString() }
}
