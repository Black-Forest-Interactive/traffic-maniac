package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.NewspaperEdition

private val Newsprint = Color(0xFFF5EEDC)

/**
 * Die Lokalzeitung im Comic-Zeitungslayout: Zeitungskopf zwischen Doppellinien, Ausgabe- und Tonlagenzeile,
 * Aufmacher in großen Lettern, darunter zwei Spalten – Meldungen links, Leserbriefe rechts.
 */
@Composable
fun ComicNewspaper(
    edition: NewspaperEdition,
    modifier: Modifier = Modifier,
    /** Erster Spieltag des Szenarios – die Ausgabe zählt ab Tag 1 der Amtszeit. */
    firstDay: Int = 0,
) {
    val ink = ComicTheme.ink
    Column(
        modifier.fillMaxWidth().comicPanel(background = Newsprint, shadow = 4.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Masthead(edition, firstDay)
        edition.headlines.firstOrNull()?.let {
            BasicText(
                it,
                style =
                    TextStyle(
                        color = ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                    ),
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (headline in edition.headlines.drop(1)) {
                    BasicText(
                        headline,
                        style =
                            TextStyle(
                                color = ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                            ),
                    )
                }
            }
            if (edition.letters.isNotEmpty()) {
                Box(Modifier.width(1.5.dp).fillMaxHeight().background(ink))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(
                        "LESERBRIEFE",
                        style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Black),
                    )
                    for (letter in edition.letters) {
                        BasicText(
                            letter,
                            style = TextStyle(color = ink, fontSize = 12.sp, fontStyle = FontStyle.Italic),
                        )
                    }
                }
            }
        }
    }
}

/** Zeitungskopf zwischen Doppellinie und Linie, darunter Ausgabe und Tonlage. */
@Composable
private fun Masthead(
    edition: NewspaperEdition,
    firstDay: Int,
) {
    val ink = ComicTheme.ink
    BasicText(
        edition.masthead,
        style =
            TextStyle(
                color = ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
    DoubleRule()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val small = TextStyle(color = ink, fontSize = 11.sp, fontFamily = FontFamily.Serif)
        BasicText("Ausgabe Tag ${edition.day - firstDay + 1} · Preis: Ihr Vertrauen", style = small)
        if (edition.tone.isNotEmpty()) {
            BasicText(
                "Tonlage: ${edition.tone}",
                style = small.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
    Rule()
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ComicTheme.ink))
}

@Composable
private fun DoubleRule() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(ComicTheme.ink))
        Rule()
    }
}
