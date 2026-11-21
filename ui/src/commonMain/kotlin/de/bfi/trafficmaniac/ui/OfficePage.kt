package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.CameraRecord
import de.bfi.trafficmaniac.game.FineOfficeState
import de.bfi.trafficmaniac.game.FineTier
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.PageMode
import kotlin.math.ceil
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(14.dp)

private fun Modifier.card() = clip(CardShape).background(TmColors.Surface).border(1.dp, TmColors.LineSoft, CardShape)

/**
 * Ordnungsamt & Bußgeldstelle als Seite (Entwurf 2, `V2-Ordnungsamt`): Kennzahlen, links die Statistik (Weg eines
 * Falls mit Kapazität, Fotos pro Stunde, wie viel zu schnell – Fotos und Geld –, Standorte), rechts die feste Spalte
 * „Steuerung“ (400 dp). Beide Spalten scrollen für sich.
 */
@Composable
internal fun OfficePage(
    data: OfficePageData,
    onCommand: (GameCommand) -> Unit,
    page: PageMode,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(page.eyebrow ?: tr("Amt"), tr("Ordnungsamt & Bußgeldstelle"), page.onMap)
        page.kpis?.let { KpiRow(it) }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ScrollColumn(Modifier.weight(1f).fillMaxHeight()) {
                Card(tr("Weg eines Falls")) { Funnel(data) }
                Card(tr("Fotos pro Stunde")) { PhotosPerHour(data.office.photosPerHour) }
                Card(tr("Wie viel zu schnell?")) { Tiers(data.cameras) }
                Card(tr("Standorte")) { Sites(data.cameras) }
                // Luft unten: das Ende der Spalte ist sichtbar, wenn ganz nach unten gescrollt ist
                Spacer(Modifier.height(24.dp))
            }
            ScrollColumn(Modifier.width(CONTROL_WIDTH.dp).fillMaxHeight()) {
                BasicText(tr("Steuerung").uppercase(), style = TmType.eyebrow)
                OfficeControls(data.office, data.rules, data.bannedDrivers, onCommand)
                data.habits?.let { HabitsSection(it, data.rules, onCommand) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Spalte, die für sich scrollt; solange unten noch etwas kommt, blendet ein Verlauf den Rand aus – so sieht man, dass
 * es weitergeht.
 */
@Composable
private fun ScrollColumn(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FADE_HEIGHT.dp)
                    .background(Brush.verticalGradient(listOf(TmColors.Ground.copy(alpha = 0f), TmColors.Ground))),
            )
        }
    }
}

@Composable
private fun Card(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().card().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(title, style = TmType.text(16.sp, FontWeight.Bold))
        content()
    }
}

/** Geblitzt → im Stapel → bearbeitet → bezahlt, darunter Abgänge und ob das Personal reicht. */
@Composable
private fun Funnel(data: OfficePageData) {
    val office = data.office
    // Alle Stufen über die Amtszeit: eingegangene Fotos der Bußgeldstelle (auch von entfernten Blitzern)
    val photos = office.received
    val steps =
        listOf(
            tr("Geblitzt") to photos,
            tr("Im Stapel") to office.backlog,
            tr("Bearbeitet") to office.processed,
            tr("Bezahlt") to office.paid,
        )
    for ((label, count) in steps) {
        val share = if (photos > 0) count.toFloat() / photos else 0f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(label, style = TmType.text(13.sp, FontWeight.SemiBold), modifier = Modifier.width(LABEL_WIDTH.dp))
            Meter(share.coerceIn(0f, 1f), Modifier.weight(1f), height = 10.dp)
            BasicText(
                formatNumber(count.toLong()),
                style = TmType.mono(13.sp),
                modifier = Modifier.width(NUMBER_WIDTH.dp),
            )
            BasicText(
                percent(share.coerceIn(0f, 1f)),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(FUNNEL_SHARE_WIDTH.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Figure(tr("Eingestellt"), office.dropped)
        Figure(tr("Verjährt"), office.expired, if (office.expired > 0) TmColors.DangerText else TmColors.Ink)
        Figure(tr("Fahrverbote"), data.bannedDrivers)
    }
    BasicText(capacityHint(data), style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.Primary))
}

/** Sachbearbeiter × Fälle/Std. × Bürostunden gegen den Eingang der letzten 24 Stunden. */
private fun capacityHint(data: OfficePageData): String {
    val office = data.office
    val rules = data.rules
    val capacity = office.staff * rules.casesPerStaffHour * (rules.officeEndHour - rules.officeStartHour)
    val intake = office.photosPerHour.sum()
    return when {
        capacity <= 0 -> {
            tr("Kein Personal – jedes Foto verjährt.")
        }

        office.backlog == 0 && intake <= capacity -> {
            tr(
                "Der Stapel ist leer – das Personal schafft {capacity} Fälle am Tag.",
                "capacity" to formatNumber(capacity.toLong()),
            )
        }

        intake > capacity -> {
            val left = (office.deadlineDays - office.oldestCaseHours / HOURS_PER_DAY).coerceAtLeast(0.0)
            tr(
                "Eingang {intake}/Tag, das Personal schafft {capacity}/Tag – der Stapel wächst. " +
                    "Ältester Fall verjährt in {days} Tagen.",
                "intake" to formatNumber(intake.toLong()),
                "capacity" to formatNumber(capacity.toLong()),
                "days" to ceil(left).toInt(),
            )
        }

        else -> {
            val days = ceil(office.backlog.toDouble() / capacity).toInt()
            tr(
                "Das Personal schafft {capacity}/Tag bei {intake} Eingang – der Stapel ist in {days} weg.",
                "intake" to formatNumber(intake.toLong()),
                "capacity" to formatNumber(capacity.toLong()),
                "days" to if (days == 1) tr("1 Arbeitstag") else tr("{count} Arbeitstagen", "count" to days),
            )
        }
    }
}

@Composable
private fun Figure(
    label: String,
    value: Int,
    color: Color = TmColors.Ink,
) {
    Column {
        BasicText(label, style = TmType.caption)
        BasicText(formatNumber(value.toLong()), style = TmType.mono(16.sp, color = color))
    }
}

/** 24 Balken (die laufende Stunde rechts), die Spitze rot und beschriftet. */
@Composable
private fun PhotosPerHour(values: List<Int>) {
    if (values.sum() == 0) {
        BasicText(tr("In den letzten 24 Stunden kein Foto."), style = TmType.small)
        return
    }
    val max = values.max()
    val peak = values.indexOf(max)
    BasicText(
        if (max == 1) {
            tr("Spitze: 1 Foto vor {hours} Std.", "hours" to values.size - 1 - peak)
        } else {
            tr("Spitze: {count} Fotos vor {hours} Std.", "count" to max, "hours" to values.size - 1 - peak)
        },
        style = TmType.caption,
    )
    Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT.dp)) {
        val slot = size.width / values.size
        val bar = slot * BAR_SHARE
        values.forEachIndexed { i, value ->
            val height = size.height * value / max
            drawRect(
                // Eine Farbe; die Spitze nur etwas dunkler (Rot hieße „Problem“)
                if (i == peak) TmColors.PrimaryHover else TmColors.Primary,
                Offset(i * slot + (slot - bar) / 2, size.height - height),
                Size(bar, height),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(tr("vor 24 Std."), style = TmType.caption)
        BasicText(tr("jetzt"), style = TmType.caption)
    }
}

/** Je Klasse: Anteil an den Fotos und am Geld, dazu der Bußgeldsatz. */
@Composable
private fun Tiers(cameras: List<CameraRecord>) {
    val photos = FineTier.entries.indices.map { tier -> cameras.sumOf { it.countPerTier.getOrElse(tier) { 0 } } }
    val money = FineTier.entries.indices.map { tier -> cameras.sumOf { it.revenuePerTier.getOrElse(tier) { 0L } } }
    val totalPhotos = photos.sum().coerceAtLeast(1)
    val totalMoney = money.sum().coerceAtLeast(1)
    Row(Modifier.fillMaxWidth()) {
        Header(tr("Klasse"), Modifier.width(LABEL_WIDTH.dp))
        Header(tr("Anteil Fotos"), Modifier.weight(1f))
        Header(tr("Anteil Geld"), Modifier.weight(1f))
        Header(tr("Bußgeld"), Modifier.width(NUMBER_WIDTH.dp), TextAlign.End)
    }
    for ((index, tier) in FineTier.entries.withIndex()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(tier.label, style = TmType.text(13.sp), modifier = Modifier.width(LABEL_WIDTH.dp))
            ShareBar(photos[index].toFloat() / totalPhotos, TmColors.Primary)
            ShareBar(money[index].toFloat() / totalMoney, TmColors.Success)
            BasicText(
                formatEuro(tier.fine.toLong()),
                style = TmType.mono(12.sp),
                modifier = Modifier.width(NUMBER_WIDTH.dp),
            )
        }
    }
    BasicText(
        tr("Das Geld kommt von den vielen kleinen Überschreitungen – „gnadenlos“ macht daraus noch mehr Fälle."),
        style = TmType.caption,
    )
}

@Composable
private fun RowScope.ShareBar(
    share: Float,
    color: Color,
) {
    Row(
        Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Meter(share, Modifier.weight(1f), height = 8.dp, color = color)
        BasicText(percent(share), style = TmType.mono(11.sp, bold = false), modifier = Modifier.width(SHARE_WIDTH.dp))
    }
}

/** Standorte: Ort und Art, Fotos, Einnahmen, Bekanntheit in Warn-Apps und wie viele dort zu schnell sind. */
@Composable
private fun Sites(cameras: List<CameraRecord>) {
    if (cameras.isEmpty()) {
        BasicText(tr("Noch kein Blitzer aufgestellt."), style = TmType.small)
        return
    }
    Row(Modifier.fillMaxWidth()) {
        Header(tr("Standort"), Modifier.weight(2f))
        Header(tr("Fotos"), Modifier.weight(1f), TextAlign.End)
        Header(tr("Einnahmen"), Modifier.weight(1f), TextAlign.End)
        Header(tr("Bekannt"), Modifier.weight(1f), TextAlign.End)
        Header(tr("zu schnell"), Modifier.weight(1f), TextAlign.End)
    }
    for (camera in cameras.sortedByDescending { it.totalRevenue }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(2f)) {
                BasicText(
                    camera.streetName,
                    style = TmType.text(13.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                kindLabel(camera)?.let { BasicText(it, style = TmType.caption) }
            }
            Cell(if (camera.display) "–" else formatNumber(camera.totalCount.toLong()))
            Cell(if (camera.display) "–" else formatEuro(camera.totalRevenue))
            Cell("${camera.familiarityPercent} %")
            Cell(if (camera.passed > 0) percent(camera.speeding.toFloat() / camera.passed) else "–")
        }
    }
}

@Composable
private fun RowScope.Cell(text: String) {
    BasicText(text, style = TmType.mono(12.sp).copy(textAlign = TextAlign.End), modifier = Modifier.weight(1f))
}

@Composable
private fun Header(
    text: String,
    modifier: Modifier,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text,
        style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Muted).copy(textAlign = align),
        modifier = modifier,
    )
}

private fun percent(share: Float): String = "${(share * PERCENT).roundToInt()} %"

private const val CONTROL_WIDTH = 400
private const val LABEL_WIDTH = 140
private const val FADE_HEIGHT = 40
private const val NUMBER_WIDTH = 72
private const val SHARE_WIDTH = 40
private const val FUNNEL_SHARE_WIDTH = 52
private const val CHART_HEIGHT = 110
private const val BAR_SHARE = 0.7f
private const val PERCENT = 100
private const val HOURS_PER_DAY = 24.0
