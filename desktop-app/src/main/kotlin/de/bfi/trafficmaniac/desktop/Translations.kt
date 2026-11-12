package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.game.Overrides
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.i18n.Language
import java.io.File
import java.util.Locale

/**
 * Übersetzungen aus `lang/<Sprache>.json`: ein JSON-Objekt, das deutsche Vorlagen auf die Übersetzung abbildet
 * (`"Tempo {kmh}: {street}": "Speed limit {kmh}: {street}"`). Deutsch braucht keine Datei – es steht im Code.
 */
object Translations {
    const val DIRECTORY = "lang"

    /** Die eingestellte Sprache, sonst die des Systems, sonst Englisch. */
    fun languageFor(setting: String?): Language =
        Language.ofCode(setting) ?: Language.ofCode(Locale.getDefault().language) ?: Language.EN

    /** Schaltet das Spiel auf [language] um; eine fehlende oder beschädigte Datei lässt die Texte deutsch. */
    fun use(
        language: Language,
        directory: File = GameFiles.dir(DIRECTORY),
    ) {
        I18n.use(language, load(language, directory))
    }

    fun load(
        language: Language,
        directory: File = GameFiles.dir(DIRECTORY),
    ): Map<String, String> {
        if (language == Language.DE) return emptyMap()
        val file = File(directory, "${language.code}.json")
        if (!file.isFile) return emptyMap()
        return runCatching {
            Overrides.translations(file.readText())
        }.onFailure { println("${file.path} ist beschädigt: ${it.message}") }
            .getOrDefault(emptyMap())
    }
}
