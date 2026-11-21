package de.bfi.trafficmaniac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.TmColors
import de.bfi.trafficmaniac.ui.TmType

private val PanelShape = RoundedCornerShape(14.dp)

/**
 * Als Vollbildseite statt Fenster: [onMap] führt zurück zur Karte. Gesetzt von der Seite anstelle der Karte –
 * [PanelScaffold] und [PanelSection] zeichnen sich dann als Seite mit Karten im Raster.
 */
class PageMode(
    val onMap: () -> Unit,
    /** Eyebrow aller Seiten statt der des Fensters, z. B. „Rathaus“. */
    val eyebrow: String? = null,
    /** Kennzahlen der Seite unter dem Kopf; ersetzt die des Fensters. */
    val kpis: (@Composable RowScope.() -> Unit)? = null,
    /** Titel der Seite statt des Fenstertitels (z. B. „Kämmerei“, solange sie das Haushaltsfenster zeigt). */
    val title: String? = null,
)

val LocalPage = staticCompositionLocalOf<PageMode?> { null }

/**
 * Fenster nach dem Muster aller Ämter und Berichte: Kopf mit Eyebrow („Amt“, „Bericht“) und Display-Titel, rechts
 * optional eine Kennzahl ([headerExtra]) und das Schließen-Kreuz, darunter [kpis] und scrollbarer Inhalt aus
 * [PanelSection]s. [floating] = schwebendes Fenster mit Rahmen und Schatten; sonst füllt es den Platz.
 *
 * Als Seite ([LocalPage]): Kopf mit Eyebrow „Rathaus“, Titel 32 sp und „Zur Karte M“, darunter die Kennzahlen als
 * Kacheln und die Abschnitte als weiße Karten in Spalten über die ganze Breite.
 */
@Composable
fun PanelScaffold(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    floating: Boolean = true,
    headerExtra: (@Composable RowScope.() -> Unit)? = null,
    kpis: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val page = LocalPage.current
    if (page != null) {
        PageScaffold(eyebrow, title, page, modifier, headerExtra, kpis, content)
        return
    }
    val frame =
        if (floating) {
            Modifier
                .shadow(6.dp, PanelShape, spotColor = Shadow, ambientColor = Shadow)
                .background(TmColors.Surface, PanelShape)
                .border(1.dp, TmColors.Line, PanelShape)
        } else {
            Modifier.background(TmColors.Surface)
        }
    Column(modifier.then(frame)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(eyebrow.uppercase(), style = TmType.eyebrow)
                BasicText(title, style = TmType.display(22.sp))
            }
            headerExtra?.invoke(this)
            onClose?.let { CloseButton(it) }
        }
        Divider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            kpis?.let {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = it,
                )
            }
            content()
        }
    }
}

/** Die Seite: Kopf, Kennzahlen, Abschnitte als Karten in Spalten (je nach Breite eine bis drei). */
@Composable
private fun PageScaffold(
    eyebrow: String,
    title: String,
    page: PageMode,
    modifier: Modifier,
    headerExtra: (@Composable RowScope.() -> Unit)?,
    kpis: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(PAGE_GAP.dp),
    ) {
        PageHeader(page.eyebrow ?: eyebrow, page.title ?: title, page.onMap, headerExtra)
        (page.kpis ?: kpis)?.let { KpiRow(it) }
        PageColumns { PageColumnScope.content() }
    }
}

/** Kopf einer Seite: Eyebrow, Titel 32 sp, rechts [extra] und „Zur Karte M“. */
@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    onMap: () -> Unit,
    extra: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(eyebrow.uppercase(), style = TmType.eyebrow)
            BasicText(title, style = TmType.display(32.sp))
        }
        extra?.invoke(this)
        Row(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TmColors.Surface)
                .border(1.dp, TmColors.Line, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onMap)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Map, null, tint = TmColors.Ink, size = 18.dp)
            BasicText(tr("Zur Karte"), style = TmType.text(13.sp, FontWeight.SemiBold))
            // Tastenkürzel nur mit Tastatur
            if (!de.bfi.trafficmaniac.ui.LocalTouch.current) {
                BasicText("M", style = TmType.mono(11.sp, bold = false, color = TmColors.Muted))
            }
        }
    }
}

/**
 * Verteilt die Kinder (Karten) auf Spalten: jedes Kind kommt in die gerade kürzeste Spalte. Ab [WIDE] dp drei
 * Spalten, ab [MEDIUM] zwei, sonst eine.
 */
@Composable
fun PageColumns(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap = PAGE_GAP.dp.roundToPx()
        val width = constraints.maxWidth
        val columns =
            when {
                width >= WIDE.dp.roundToPx() -> 3
                width >= MEDIUM.dp.roundToPx() -> 2
                else -> 1
            }
        val columnWidth = (width - gap * (columns - 1)) / columns
        val heights = IntArray(columns)
        val placed =
            measurables.map { measurable ->
                val placeable = measurable.measure(Constraints.fixedWidth(columnWidth))
                val column = heights.indices.minBy { heights[it] }
                val y = heights[column]
                heights[column] += placeable.height + if (placeable.height > 0) gap else 0
                Triple(placeable, column * (columnWidth + gap), y)
            }
        layout(width, (heights.maxOrNull() ?: 0).coerceAtLeast(0)) {
            placed.forEach { (placeable, x, y) -> placeable.place(x, y) }
        }
    }
}

/** Spaltenbereich der Seite: Abschnitte sind dort Karten, `weight` und `align` haben keine Wirkung. */
private object PageColumnScope : ColumnScope {
    override fun Modifier.weight(
        weight: Float,
        fill: Boolean,
    ): Modifier = this

    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this

    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this

    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/**
 * Reihe der Kennzahlen unter dem Seitenkopf: am Desktop gleich breite Kacheln, per Finger kompakte Chips nach Inhalt,
 * die seitlich scrollen statt abgeschnitten zu werden.
 */
@Composable
fun KpiRow(content: @Composable RowScope.() -> Unit) {
    if (de.bfi.trafficmaniac.ui.LocalTouch.current) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    } else {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(PAGE_GAP.dp),
            content = content,
        )
    }
}

/** Kennzahl einer Seite: Beschriftung, Zahl in Mono 26 sp, darunter eine Zeile; [accent] färbt den Rand. */
@Composable
fun RowScope.KpiTile(
    label: String,
    value: String,
    footer: String? = null,
    valueColor: Color = TmColors.Ink,
    accent: Color? = null,
    meter: Float? = null,
    meterColor: Color = TmColors.Primary,
    /** Strich auf dem Meter, z. B. „Zorn ab 35 %“. */
    threshold: Float? = null,
) {
    // Per Finger (wenig Höhe): kompakter Chip – Wert und Beschriftung nebeneinander
    if (de.bfi.trafficmaniac.ui.LocalTouch.current) {
        Row(
            Modifier
                .background(TmColors.Surface, PageShape)
                .border(if (accent != null) 2.dp else 1.dp, accent ?: TmColors.LineSoft, PageShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(value, style = TmType.mono(14.sp, color = valueColor), maxLines = 1)
            BasicText(
                label,
                style = TmType.text(11.sp, color = TmColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(TmColors.Surface, PageShape)
            .border(if (accent != null) 2.dp else 1.dp, accent ?: TmColors.LineSoft, PageShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(label, style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Muted))
        // Lange Werte („−12.345 €“) etwas kleiner, damit sie auch in schmalen Kacheln ganz zu sehen sind
        BasicText(
            value,
            style =
                TmType.mono(
                    if (value.length >
                        LONG_VALUE
                    ) {
                        20.sp
                    } else {
                        26.sp
                    },
                    color = valueColor,
                ),
            maxLines = 1,
        )
        meter?.let { Meter(it, Modifier.fillMaxWidth(), height = 6.dp, color = meterColor, threshold = threshold) }
        footer?.let { BasicText(it, style = TmType.caption) }
    }
}

/** Abschnitt in einem Fenster: 20 dp seitlich, optional mit Überschrift, unten eine feine Trennlinie. */
@Composable
fun PanelSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalPage.current != null) {
        // Als Seite: jeder Abschnitt ist eine weiße Karte
        Column(
            modifier
                .fillMaxWidth()
                .background(TmColors.Surface, PageShape)
                .border(1.dp, TmColors.LineSoft, PageShape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            title?.let { BasicText(it, style = TmType.text(16.sp, FontWeight.Bold)) }
            content()
        }
        return
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        title?.let { BasicText(it, style = TmType.text(14.sp, FontWeight.Bold)) }
        content()
    }
    if (divider) Divider()
}

/**
 * Zeile „Beschriftung links, Bedienelement rechts“ – z. B. Sachbearbeiter mit [Stepper]. [hint] steht klein darunter.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = TmType.text(14.sp, FontWeight.Bold))
            hint?.let { BasicText(it, style = TmType.caption) }
        }
        control()
    }
}

/** Feine Trennlinie in Fenstern. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(TmColors.Surface, CircleShape).clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Close, tr("Schließen"), tint = TmColors.Muted, size = 20.dp)
    }
}

private val Shadow = TmColors.Ink.copy(alpha = 0.18f)
private val PageShape = RoundedCornerShape(14.dp)
private const val PAGE_GAP = 16
private const val LONG_VALUE = 7
private const val WIDE = 1400
private const val MEDIUM = 880
