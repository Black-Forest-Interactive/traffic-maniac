package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.ui.map.MapLegend
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource

private val CONTROLS =
    listOf(
        "Mausrad: zoomen · Ziehen: Karte verschieben",
        "Werkzeug unten wählen, dann auf Straße bzw. Kreuzung klicken",
        "Leertaste: Pause · 1–4: Tempo 1×/2×/4×/8× · Enter: Tagesabschluss schließen",
        "M: Ton aus/an · N: Musik aus/an · F5: schnell speichern (Laden im Startmenü)",
        "Meldungen mit ▸, Schäden im Bauamt und Ereignisse im Kalender anklicken: die Karte zeigt die Stelle",
    )

/** Hilfe: Bedienung in Kürze und die Legende aller Kartensymbole. */
@Composable
fun HelpPanel(
    modifier: Modifier = Modifier,
    sprites: SpriteSource = NoSprites,
) {
    val title = TextStyle(color = ComicTheme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = ComicTheme.ink, fontSize = 11.sp)
    Column(
        modifier
            .width(PANEL_WIDTH.dp)
            .padding(12.dp)
            .comicPanel()
            .heightIn(max = MAX_HEIGHT.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("Hilfe & Legende", style = title)
        for (line in CONTROLS) BasicText("• $line", style = text)
        MapLegend(ComicTheme.ink, ComicTheme.muted, Modifier.padding(top = 6.dp), sprites)
    }
}

private const val PANEL_WIDTH = 420
private const val MAX_HEIGHT = 760
