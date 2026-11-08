package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.i18n.Language
import de.bfi.trafficmaniac.i18n.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Jeder Text, den der Code mit `tr("…")` oder `translatable("…")` markiert, braucht eine englische Übersetzung mit
 * denselben Platzhaltern. Fehlende Einträge ergänzt `scripts/i18n-extract.py en` (mit leerer Übersetzung).
 */
class TranslationsTest {
    private val root = File("..")
    private val english = Translations.load(Language.EN, File(root, Translations.DIRECTORY))

    @AfterTest
    fun backToGerman() = I18n.use(Language.DE)

    @Test
    fun everyMarkedTextHasAnEnglishTranslation() {
        val keys = markedTexts()
        assertTrue(keys.size > 500, "Texte im Code gefunden: ${keys.size}")
        val missing = keys.filter { english[it].isNullOrBlank() }
        assertTrue(missing.isEmpty(), "Ohne Übersetzung (scripts/i18n-extract.py en):\n" + missing.joinToString("\n"))
    }

    @Test
    fun placeholdersMatch() {
        val wrong =
            english.filter { (german, translated) -> placeholders(german) != placeholders(translated) }.keys
        assertTrue(wrong.isEmpty(), "Platzhalter passen nicht:\n" + wrong.joinToString("\n"))
    }

    @Test
    fun switchingTheLanguageChangesTexts() {
        I18n.use(Language.EN, english)
        assertEquals("Speed limit", tr("Tempolimit"))
        assertEquals("Achievement unlocked: Stuck!", tr("Achievement freigeschaltet: {title}!", "title" to "Stuck"))
        assertEquals("€90,000", formatEuro(90_000))
    }

    private fun placeholders(text: String) = Regex("""\{(\w+)}""").findAll(text).map { it.groupValues[1] }.toSet()

    /** Dieselbe Suche wie `scripts/i18n-extract.py`: Literale (auch mit `+` verkettet) in tr(…) und translatable(…). */
    private fun markedTexts(): Set<String> {
        val string = """"(?:[^"\\]|\\.)*""""
        val call = Regex("""\b(?:tr|translatable)\(\s*($string(?:\s*\+\s*$string)*)""")
        val literal = Regex(string)
        return listOf("i18n", "city", "sim-engine", "game", "ui", "desktop-app")
            .flatMap { module -> File(root, "$module/src").walk().filter { it.path.contains("Main/") } }
            .filter { it.extension == "kt" }
            .flatMap { file ->
                call.findAll(file.readText()).map { match ->
                    literal.findAll(match.groupValues[1]).joinToString("") { unescape(it.value) }
                }
            }.toSet()
    }

    private fun unescape(quoted: String): String {
        val body = quoted.substring(1, quoted.length - 1)
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            val next = body[i + 1]
            if (next == 'u') {
                out.append(body.substring(i + 2, i + UNICODE_END).toInt(HEX).toChar())
                i += UNICODE_END
                continue
            }
            out.append(
                when (next) {
                    'n' -> '\n'
                    't' -> '\t'
                    else -> next
                },
            )
            i += 2
        }
        return out.toString()
    }

    private companion object {
        const val UNICODE_END = 6
        const val HEX = 16
    }
}
