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
            for (camera in cameras.sortedByDescending { it.totalRevenue }) {
                Divider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        camera.streetName,
                        style = TmType.text(13.sp, FontWeight.SemiBold),
                        modifier = Modifier.weight(2f),
                    )
                    BasicText(
                        "${camera.totalCount}",
                        style = TmType.mono(13.sp, bold = false).copy(textAlign = TextAlign.End),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        formatEuro(camera.totalRevenue),
                        style = TmType.mono(13.sp, bold = false).copy(textAlign = TextAlign.End),
                        modifier = Modifier.weight(1f),
                    )
                }
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
    val counts = FineTier.entries.map { tier -> cameras.sumOf { it.countPerTier[tier.ordinal] }.toLong() }
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
        FineTier.entries.forEachIndexed { i, tier ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).background(TierColors[i], RoundedCornerShape(3.dp)))
                BasicText("${tier.label} · ${counts[i] * PERCENT / total} %", style = TmType.text(12.sp))
            }
        }
    }
}

private const val PERCENT = 100
