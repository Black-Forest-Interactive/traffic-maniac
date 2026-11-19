package de.bfi.trafficmaniac.i18n

import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.roundToLong

/** Sprachen des Spiels. Deutsch ist die Ausgangssprache: Alle Texte stehen deutsch im Code. */
enum class Language(
    val code: String,
    val label: String,
) {
    DE("de", "Deutsch"),
    EN("en", "English"),
    ;

    companion object {
        fun ofCode(code: String?): Language? = entries.firstOrNull { it.code == code?.lowercase()?.take(2) }
    }
}

/**
 * Übersetzungen nach dem gettext-Prinzip: Der deutsche Text ist der Schlüssel, die Tabelle der gewählten Sprache
 * liefert die Übersetzung. Fehlt ein Eintrag, bleibt es beim deutschen Text – das Spiel ist nie „kaputt“ übersetzt.
 * Platzhalter stehen als `{name}` im Text und werden in jeder Sprache gleich benannt.
 */
object I18n {
    private class Table(
        val language: Language,
        val translations: Map<String, String>,
    )

    @Volatile
    private var table = Table(Language.DE, emptyMap())

    val language: Language get() = table.language

    /** Schaltet auf [language] um; [translations] bildet deutsche Vorlagen auf die Übersetzung ab. */
    fun use(
        language: Language,
        translations: Map<String, String> = emptyMap(),
    ) {
        table = Table(language, if (language == Language.DE) emptyMap() else translations)
    }

    fun translate(template: String): String = table.translations[template] ?: template
}

/** Übersetzt [template] und setzt die Platzhalter `{name}` aus [args] ein. */
fun tr(
    template: String,
    vararg args: Pair<String, Any?>,
): String {
    var text = I18n.translate(template)
    for ((name, value) in args) text = text.replace("{$name}", value.toString())
    return text
}

/**
 * Markiert einen festen Text (z. B. eine Beschriftung in einem Enum), der erst bei der Anzeige mit [tr] übersetzt
 * wird. Liefert den Text unverändert – die Markierung findet der Vollständigkeits-Test.
 */
fun translatable(text: String): String = text

/** Ganze Zahl mit Tausendertrennzeichen der Sprache: 158.000 bzw. 158,000. */
fun formatNumber(value: Long): String {
    val separator = if (I18n.language == Language.DE) "." else ","
    val digits =
        abs(value)
            .toString()
            .reversed()
            .chunked(GROUP)
            .joinToString(separator)
            .reversed()
    return (if (value < 0) "-" else "") + digits
}

/** Kommazahl mit [decimals] Nachkommastellen: 19,5 bzw. 19.5. */
fun formatDecimal(
    value: Double,
    decimals: Int = 1,
): String {
    var factor = 1L
    repeat(decimals) { factor *= TEN }
    val scaled = (abs(value) * factor).roundToLong()
    val whole = formatNumber(scaled / factor)
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    if (decimals == 0) return sign + whole
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    val comma = if (I18n.language == Language.DE) "," else "."
    return "$sign$whole$comma$fraction"
}

/** Geldbetrag: 158.000 € bzw. €158,000. */
fun formatEuro(amount: Long): String {
    val number = formatNumber(abs(amount))
    val sign = if (amount < 0) "-" else ""
    return if (I18n.language == Language.DE) "$sign$number €" else "$sign€$number"
}

/** Betrag mit Cent (z. B. Parkgebühr): ganze Euro ohne Nachkommastellen, sonst zwei – 2,50 € bzw. €2.50. */
fun formatEuro(amount: Double): String {
    val cents = (abs(amount) * CENTS).roundToLong()
    if (cents % CENTS == 0L) return formatEuro((if (amount < 0) -cents else cents) / CENTS)
    val number = formatDecimal(abs(amount), 2)
    val sign = if (amount < 0) "-" else ""
    return if (I18n.language == Language.DE) "$sign$number €" else "$sign€$number"
}

private const val GROUP = 3
private const val CENTS = 100L
private const val TEN = 10L
