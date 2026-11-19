package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.map.MapLegend
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource

/** Tasten und Mausbedienung: Kürzel und was sie tun. */
private val CONTROLS =
    listOf(
        translatable("Mausrad") to translatable("Zoomen"),
        translatable("Ziehen") to translatable("Karte verschieben"),
        translatable("Leertaste") to translatable("Pause"),
        "1 – 4" to translatable("Zeitraffer 1×/2×/4×/8× (nachts automatisch schneller)"),
        "Esc · Q · X" to translatable("Ansehen, Tempolimit, Abbauen"),
        "W E A B" to
            translatable("Gruppen: Ampel, Überwachung, Verkehrsführung, Baustelle – Kachel klappt die Liste auf"),
        "E E …" to translatable("Gruppentaste noch einmal: nächste Art der Gruppe"),
        "1 – 9" to translatable("Bei offener Liste: Art wählen"),
        "E" to translatable("Anhänger umsetzen: aufgestellten Anhänger anklicken, dann die neue Straße"),
        "A" to translatable("Fahrradstraße, Baustelle, Sperrung, grüne Welle: Anfang und Ende anklicken"),
        "Enter" to translatable("Tagesabschluss schließen"),
        "M · N" to translatable("Ton bzw. Musik aus/an"),
        "F5" to translatable("Schnell speichern (Laden im Startmenü)"),
        "F3" to translatable("Rechenzeit und Bildrate einblenden"),
        "F4" to translatable("Straßennetz-Modus: Knoten, Abschnitte und Strecken, Klick zeigt die Daten"),
    )

/** Hilfe: Bedienung in Kürze und die Legende aller Kartensymbole. */
@Composable
fun HelpPanel(
    modifier: Modifier = Modifier,
    sprites: SpriteSource = NoSprites,
    onClose: (() -> Unit)? = null,
) {
    PanelScaffold(tr("Hilfe"), tr("Hilfe & Legende"), modifier, onClose, floating = false) {
        PanelSection(title = tr("Bedienung")) {
            for ((key, action) in CONTROLS) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(
                        tr(key),
                        style = TmType.mono(11.sp),
                        modifier =
                            Modifier
                                .width(KEY_WIDTH.dp)
                                .background(TmColors.Ground, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    BasicText(tr(action), style = TmType.text(13.sp), modifier = Modifier.weight(1f))
                }
            }
            BasicText(
                tr("Meldungen, Schäden und Ereignisse anklicken: die Karte zeigt die Stelle."),
                style = TmType.caption,
            )
        }
        PanelSection(title = tr("Legende"), divider = false) {
            MapLegend(TmColors.Ink, Modifier.fillMaxWidth(), sprites)
        }
    }
}

private const val KEY_WIDTH = 128
