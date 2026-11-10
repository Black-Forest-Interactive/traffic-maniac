package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.CameraRecord
import de.bfi.trafficmaniac.game.FineTier
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Divider
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import kotlin.math.roundToInt

/** Stufen von hell nach dunkel, die höchste rot. */
private val TierColors = listOf(Color(0xFF8DB8F2), TmColors.Primary, TmColors.Warn, TmColors.DangerText)

/**
 * Blitzer-Statistik: Einnahmen, wie viel zu schnell die Erwischten waren, die Geräte im Einzelnen und der Rekord.
 * Macht sichtbar, dass das Geld von den vielen kleinen Überschreitungen kommt – die Presse zitiert lieber den Rekord.
 */
@Composable
fun CameraStatsPanel(
    cameras: List<CameraRecord>,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val totalRevenue = cameras.sumOf { it.totalRevenue }
    PanelScaffold(
        tr("Bericht"),
        tr("Blitzer-Statistik"),
        modifier,
        onClose,
        floating = false,
        headerExtra = {
            Column(horizontalAlignment = Alignment.End) {
                BasicText(tr("Einnahmen gesamt"), style = TmType.caption)
                BasicText(formatEuro(totalRevenue), style = TmType.mono(18.sp, color = TmColors.Success))
            }
        },
    ) {
        if (cameras.isEmpty()) {
            PanelSection(divider = false) { BasicText(tr("Noch kein Blitzer aufgestellt."), style = TmType.small) }
            return@PanelScaffold
        }
        PanelSection { TierDistribution(cameras) }
        PanelSection(divider = false) {
            Row(Modifier.fillMaxWidth()) {
                Header(tr("Standort"), Modifier.weight(2f), TextAlign.Start)
                Header(tr("Fotos"), Modifier.weight(1f), TextAlign.End)
                Header(tr("Einnahmen"), Modifier.weight(1f), TextAlign.End)
            }
            for (camera in cameras.sortedWith(compareBy({ it.display }, { -it.totalRevenue }))) {
                Divider()
                CameraRow(camera)
            }
            cameras.filter { it.totalCount > 0 }.maxByOrNull { it.fastestMeasuredKmh - it.fastestLimitKmh }?.let {
                BasicText(
                    tr(
                        "Rekord: {fastestMeasuredKmh} km/h bei Tempo {fastestLimitKmh}",
                        "fastestMeasuredKmh" to it.fastestMeasuredKmh.roundToInt(),
                        "fastestLimitKmh" to it.fastestLimitKmh,
                    ),
                    style = TmType.small,
                )
            }
        }
    }
}

@Composable
private fun Header(
    text: String,
    modifier: Modifier,
    align: TextAlign,
) {
    BasicText(
        text,
        style = TmType.text(11.sp, FontWeight.Bold, TmColors.Muted).copy(textAlign = align),
        modifier = modifier,
    )
}

/** Gestapelter Balken: Anteil der Fotos je Stufe, darunter die Legende. */
@Composable
private fun TierDistribution(cameras: List<CameraRecord>) {
    val counts = FineTier.SPEED.map { tier -> cameras.sumOf { it.countPerTier[tier.ordinal] }.toLong() }
    val total = counts.sum().coerceAtLeast(1)
    BasicText(tr("Wie viel zu schnell? (km/h)"), style = TmType.small)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(TmColors.Ground),
    ) {
        var x = 0f
        counts.forEachIndexed { i, count ->
            val width = size.width * count / total
            drawRect(TierColors[i], topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FineTier.SPEED.forEachIndexed { i, tier ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).background(TierColors[i], RoundedCornerShape(3.dp)))
                BasicText("${tier.label} · ${counts[i] * PERCENT / total} %", style = TmType.text(12.sp))
            }
        }
    }
    // Rotlicht zählt nicht als „zu schnell“ – eigene Zeile
    val red = cameras.sumOf { it.countPerTier[FineTier.RED_LIGHT.ordinal] }
    val late = cameras.sumOf { it.countPerTier[FineTier.RED_LIGHT_LATE.ordinal] }
    if (red + late > 0) {
        BasicText(
            tr("Über Rot: {count} Fotos, davon {late} länger als 1 s", "count" to red + late, "late" to late),
            style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.DangerText),
        )
    }
}

/** Zeile in der Tabelle: Standort (mit Art), Fotos und Einnahmen – Anzeigetafeln zeigen ihren Raser-Anteil. */
@Composable
private fun CameraRow(camera: CameraRecord) {
    val kind =
        when {
            camera.display -> tr("Anzeigetafel")
            camera.redLight && camera.measuresSpeed -> tr("Rotlicht + Tempo")
            camera.redLight -> tr("Rotlicht")
            camera.trailer -> tr("Anhänger")
            camera.mobile -> tr("Laser")
            else -> null
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(2f)) {
            BasicText(camera.streetName, style = TmType.text(13.sp, FontWeight.SemiBold))
            kind?.let { BasicText(it, style = TmType.caption) }
        }
        val mono = TmType.mono(13.sp, bold = false).copy(textAlign = TextAlign.End)
        if (camera.display) {
            BasicText(
                if (camera.passed == 0) {
                    tr("noch keine Messung")
                } else {
                    tr("{share} % zu schnell", "share" to (camera.speedingShare * PERCENT).roundToInt())
                },
                style = mono,
                modifier = Modifier.weight(2f),
            )
        } else {
            BasicText("${camera.totalCount}", style = mono, modifier = Modifier.weight(1f))
            BasicText(formatEuro(camera.totalRevenue), style = mono, modifier = Modifier.weight(1f))
        }
    }
}

private const val PERCENT = 100
