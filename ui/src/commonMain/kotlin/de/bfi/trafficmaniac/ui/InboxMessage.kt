package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameMessage
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.TmButton
import de.bfi.trafficmaniac.ui.map.Camera
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.MapView

/** Stichworte im Meldungstext und die Seite, die dazu passt – die erste passende gewinnt. */
private val PAGES =
    listOf(
        listOf("Erfolg") to SidePanel.ACHIEVEMENTS,
        listOf("Geld", "Haushalt", "€") to SidePanel.TREASURY,
        listOf("Schaden", "Schlagloch", "Reparatur") to SidePanel.MAINTENANCE,
        listOf("Blitzer", "Laser", "Sachbearbeiter", "Frist") to SidePanel.OFFICE,
        listOf("Bus") to SidePanel.TRANSIT,
        listOf("Demo", "Korso") to SidePanel.EVENTS,
    )

/** Welche Seite zu einer Meldung passt und wie der Knopf heißt; `null` = keine. */
private fun pageFor(message: GameMessage): Pair<String, SidePanel>? {
    val panel =
        PAGES.firstOrNull { (words, _) -> words.any { message.text.contains(it, ignoreCase = true) } }?.second
            ?: return null
    val label =
        when (panel) {
            SidePanel.ACHIEVEMENTS -> tr("Zu den Erfolgen")
            SidePanel.TREASURY -> tr("In der Kämmerei ansehen")
            SidePanel.MAINTENANCE -> tr("Zum Bauamt")
            SidePanel.OFFICE -> tr("Zum Ordnungsamt")
            SidePanel.TRANSIT -> tr("Zum ÖPNV")
            else -> tr("Zum Kalender")
        }
    return label to panel
}

/**
 * Detail einer Meldung im Posteingang: was über den Titel hinaus drinsteht, bei einem Ort ein Kartenausschnitt, die
 * passende Seite und – bei gebündelten Meldungen – alle Vorkommen mit Uhrzeit.
 */
@Composable
internal fun MessageDetail(
    item: InboxItem.Message,
    picker: MapPicker,
    actions: InboxActions,
) {
    val message = item.message
    // Der Titel steht schon darüber – hier nur, was er nicht sagt
    val rest = message.text.removePrefix(messageTitle(message)).trimStart(':', ' ')
    if (rest.isNotEmpty()) BasicText(rest, style = TmType.body)
    // Ohne gespeicherten Ort: die Straße, die im Text genannt ist
    val streets = remember(picker.city) { streetPlaces(picker) }
    val place = remember(message) { message.place ?: placeInText(message.text, streets) }
    place?.let { place ->
        MapView(
            picker.city,
            Modifier
                .fillMaxWidth()
                .height(
                    SNIPPET_HEIGHT.dp,
                ).clip(SnippetShape)
                .border(1.dp, TmColors.LineSoft, SnippetShape),
            initialCamera = Camera(place.x, place.y, SNIPPET_SCALE),
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        place?.let { TmButton(tr("Auf der Karte zeigen"), { actions.onShow(it) }, kind = ButtonKind.PRIMARY) }
        pageFor(message)?.let { (label, panel) -> TmButton(label, { actions.onPanel(panel) }) }
    }
    if (item.messages.size > 1) {
        BasicText(
            tr("Alle {count} Meldungen", "count" to item.messages.size),
            style = TmType.text(14.sp, FontWeight.Bold),
        )
        for (entry in item.messages.take(MAX_OCCURRENCES)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(entry.time.clockText, style = TmType.mono(12.sp, bold = false, color = TmColors.Muted))
                BasicText(entry.text.substringAfter(": "), style = TmType.text(13.sp))
            }
        }
    }
}

/** Je Straßenname die Mitte eines seiner Abschnitte. */
private fun streetPlaces(picker: MapPicker): Map<String, MapPlace> =
    picker.city.roads.segments
        .filter { !it.passive }
        .mapNotNull { segment -> segment.name?.let { it to segment.id } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, segments) -> picker.place(segments[segments.size / 2]) }

/** Die längste im Text genannte Straße (so gewinnt „Östliche Karl-Friedrich-Straße“ vor „Karl-Friedrich-Straße“). */
private fun placeInText(
    text: String,
    streets: Map<String, MapPlace>,
): MapPlace? =
    streets.keys
        .filter { it.length >= MIN_STREET && it in text }
        .maxByOrNull { it.length }
        ?.let(streets::getValue)

private const val MIN_STREET = 5

private val SnippetShape = RoundedCornerShape(10.dp)
private const val SNIPPET_HEIGHT = 240
private const val SNIPPET_SCALE = 0.6f
private const val MAX_OCCURRENCES = 20
