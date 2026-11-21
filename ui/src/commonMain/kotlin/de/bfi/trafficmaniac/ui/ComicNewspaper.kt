package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.CameraBalance
import de.bfi.trafficmaniac.game.NewspaperEdition
import de.bfi.trafficmaniac.i18n.tr

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
    /** Groß über die volle Höhe (Tagesabschluss): Schlagzeile, Unterzeile, zwei Spalten, Zeichnung. */
    large: Boolean = false,
    /** Kurzer Artikel aus der Tagesbilanz unter „Kurz gemeldet“ (nur [large]). */
    article: String? = null,
) {
    if (large) return LargeNewspaper(edition, modifier, firstDay, article)
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
                        tr("LESERBRIEFE"),
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
        PracticeBox(edition.day)
    }
}

/** Kasten „Aus der Praxis“: ein echter Messwert zum Blitzen – die Zeitung als Lehrbuch wider Willen. */
@Composable
private fun PracticeBox(day: Int) {
    val ink = ComicTheme.ink
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.5.dp, ink)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(tr("AUS DER PRAXIS"), style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Black))
        BasicText(
            CameraBalance.practiceFact(day),
            style = TextStyle(color = ink, fontSize = 12.sp, fontFamily = FontFamily.Serif),
        )
    }
}

/** Zeitungskopf zwischen Doppellinie und Linie, darunter Ausgabe und Tonlage. */
@Composable
private fun Masthead(
    edition: NewspaperEdition,
    firstDay: Int,
    large: Boolean = false,
) {
    val ink = ComicTheme.ink
    BasicText(
        edition.masthead,
        style =
            TextStyle(
                color = ink,
                fontSize = if (large) 40.sp else 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
    DoubleRule()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val small = TextStyle(color = ink, fontSize = 11.sp, fontFamily = FontFamily.Serif)
        BasicText(
            tr("Ausgabe Tag {firstDay} · Preis: Ihr Vertrauen", "firstDay" to edition.day - firstDay + 1),
            style = small,
        )
        if (edition.tone.isNotEmpty()) {
            BasicText(
                tr("Tonlage: {tone}", "tone" to edition.tone),
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

/**
 * Die Zeitung über die volle Höhe: Kopf, Aufmacher groß mit der zweiten Meldung als Unterzeile, darunter zwei Spalten
 * – „Kurz gemeldet“ links, Zeichnung und Leserbriefe rechts – und unten „Aus der Praxis“.
 */
@Composable
private fun LargeNewspaper(
    edition: NewspaperEdition,
    modifier: Modifier,
    firstDay: Int,
    article: String?,
) {
    val ink = ComicTheme.ink
    val serif = TextStyle(color = ink, fontFamily = FontFamily.Serif)
    Column(
        modifier.fillMaxWidth().comicPanel(background = Newsprint, shadow = 4.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Masthead(edition, firstDay, large = true)
        edition.headlines.firstOrNull()?.let {
            BasicText(it, style = serif.copy(fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp))
        }
        edition.headlines.getOrNull(1)?.let {
            BasicText(it, style = serif.copy(fontSize = 17.sp, fontStyle = FontStyle.Italic))
        }
        Rule()
        NewsColumns(edition, article, Modifier)
        PracticeBox(edition.day)
    }
}

/** „Kurz gemeldet“ links, Zeichnung und Leserbriefe rechts. */
@Composable
private fun NewsColumns(
    edition: NewspaperEdition,
    article: String?,
    modifier: Modifier,
) {
    val ink = ComicTheme.ink
    val serif = TextStyle(color = ink, fontFamily = FontFamily.Serif)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                tr("KURZ GEMELDET"),
                style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black),
            )
            val rest = edition.headlines.drop(2)
            if (rest.isEmpty()) {
                BasicText(
                    tr("Sonst war es ruhig in der Stadt."),
                    style = serif.copy(fontSize = 15.sp),
                )
            }
            for (headline in rest) {
                BasicText(
                    "■ $headline",
                    style = serif.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                )
            }
            article?.let {
                BasicText(
                    tr("AUS DEM RATHAUS"),
                    style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black),
                )
                BasicText(it, style = serif.copy(fontSize = 14.sp, lineHeight = 20.sp))
            }
        }
        Box(Modifier.width(1.5.dp).fillMaxHeight().background(ink))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CameraCartoon(Modifier.fillMaxWidth().height(CARTOON_HEIGHT.dp))
            if (edition.letters.isNotEmpty()) {
                BasicText(
                    tr("LESERBRIEFE"),
                    style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Black),
                )
                for (letter in edition.letters) {
                    BasicText(
                        letter,
                        style =
                            TextStyle(
                                color = ink,
                                fontSize = 14.sp,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 19.sp,
                            ),
                    )
                }
            }
        }
    }
}

/** Kleine Zeichnung im Comic-Stil: ein Blitzerkasten mit Blitz, davor ein Auto. */
@Composable
private fun CameraCartoon(modifier: Modifier) {
    val ink = ComicTheme.ink
    Canvas(modifier.border(1.5.dp, ink).background(Color.White.copy(alpha = CARTOON_PAPER))) {
        val stroke = Stroke(2.5.dp.toPx())
        val unit = size.height / CARTOON_UNITS
        // Straße
        drawLine(ink, Offset(0f, size.height * ROAD), Offset(size.width, size.height * ROAD), stroke.width)
        // Blitzerkasten auf Pfahl
        val boxLeft = size.width * BOX_X
        drawLine(ink, Offset(boxLeft + unit, size.height * ROAD), Offset(boxLeft + unit, unit * POLE_TOP), stroke.width)
        drawRect(ComicTheme.red, Offset(boxLeft, unit), Size(unit * 2, unit * 2))
        drawRect(ink, Offset(boxLeft, unit), Size(unit * 2, unit * 2), style = stroke)
        drawCircle(ink, unit * LENS, Offset(boxLeft + unit, unit * 2), style = stroke)
        // Blitz: Strahlen nach links
        for (i in -1..1) {
            drawLine(
                TmColors.Active,
                Offset(boxLeft - unit * RAY_START, unit * 2f + i * unit * RAY_SPREAD),
                Offset(boxLeft - unit * RAY_END, unit * 2f + i * unit * RAY_SPREAD * 2),
                stroke.width * 2,
            )
        }
        // Auto: Karosserie und zwei Räder
        val carLeft = size.width * CAR_X
        val carTop = size.height * ROAD - unit * CAR_HEIGHT
        drawRect(Color.White, Offset(carLeft, carTop), Size(unit * CAR_LENGTH, unit * CAR_HEIGHT * WHEEL_LINE))
        drawRect(ink, Offset(carLeft, carTop), Size(unit * CAR_LENGTH, unit * CAR_HEIGHT * WHEEL_LINE), style = stroke)
        for (x in listOf(carLeft + unit, carLeft + unit * (CAR_LENGTH - 1))) {
            drawCircle(ink, unit * WHEEL, Offset(x, size.height * ROAD - unit * WHEEL))
        }
    }
}

private const val CARTOON_HEIGHT = 150
private const val CARTOON_PAPER = 0.6f
private const val CARTOON_UNITS = 8f
private const val ROAD = 0.86f
private const val BOX_X = 0.68f
private const val POLE_TOP = 3f
private const val LENS = 0.45f
private const val RAY_START = 0.4f
private const val RAY_END = 1.8f
private const val RAY_SPREAD = 0.6f
private const val CAR_X = 0.12f
private const val CAR_HEIGHT = 2.2f
private const val CAR_LENGTH = 5f
private const val WHEEL_LINE = 0.75f
private const val WHEEL = 0.55f
