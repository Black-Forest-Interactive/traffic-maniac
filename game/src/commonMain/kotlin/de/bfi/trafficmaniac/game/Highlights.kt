package de.bfi.trafficmaniac.game

/** Wie laut eine Einblendung ist: Errungenschaft (groß), Nachricht mit Betrag (mittel), Warnung (rot). */
enum class HighlightKind { BIG, MEDIUM, WARNING }

/**
 * Etwas, das der Spieler sehen und hören soll – die Oberfläche blendet jedes genau einmal ein (nach [id]) und spielt
 * einen Ton dazu. Ein Klick springt zu [place].
 */
data class Highlight(
    val id: Int,
    val kind: HighlightKind,
    val title: String,
    val text: String = "",
    val place: MapPlace? = null,
)

/** Die letzten Highlights mit fortlaufender Nummer. Nicht im Spielstand – nach dem Laden zählt es von vorn. */
internal class HighlightLog {
    private var next = 1
    private val recent = ArrayDeque<Highlight>()

    fun add(
        kind: HighlightKind,
        title: String,
        text: String = "",
        place: MapPlace? = null,
    ) {
        recent.addLast(Highlight(next++, kind, title, text, place))
        while (recent.size > KEEP) recent.removeFirst()
    }

    fun list(): List<Highlight> = recent.toList()

    private companion object {
        const val KEEP = 12
    }
}
