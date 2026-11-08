package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.CameraRecord
import de.bfi.trafficmaniac.game.FineTier
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import kotlin.math.roundToInt

private val PanelBackground = ComicTheme.paper
private val PanelText = ComicTheme.ink
private val Muted = ComicTheme.muted
private val TierColors = listOf(Color(0xFF2A9D8F), Color(0xFFFFBE0B), Color(0xFFF4A261), Color(0xFFE63946))
private const val PERCENT = 100

/**
 * Blitzer-Statistik: je Gerät Anzahl und Einnahmen pro Stufe. Macht transparent, dass das Geld von den
 * vielen kleinen Überschreitungen kommt – während die Presse lieber den Rekord-Raser zitiert.
 */
@Composable
fun CameraStatsPanel(
    cameras: List<CameraRecord>,
    modifier: Modifier = Modifier,
) {
    val title = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(380.dp)
            .padding(12.dp)
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(tr("Blitzer-Statistik"), style = title)
        if (cameras.isEmpty()) {
            BasicText(tr("Noch kein Blitzer aufgestellt."), style = muted)
            return@Column
        }
        val totalRevenue = cameras.sumOf { it.totalRevenue }
        val revenueByTier = FineTier.entries.map { tier -> cameras.sumOf { it.revenuePerTier[tier.ordinal] } }
        BasicText(
            tr("Einnahmen gesamt: {totalRevenue}", "totalRevenue" to formatEuro(totalRevenue)),
            style = text.copy(fontWeight = FontWeight.Bold),
        )
        TierBar(revenueByTier, Modifier.fillMaxWidth().height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FineTier.entries.forEach { tier ->
                val share = if (totalRevenue > 0) revenueByTier[tier.ordinal] * PERCENT / totalRevenue else 0
                BasicText("${tier.shortLabel}: $share %", style = muted.copy(color = TierColors[tier.ordinal]))
            }
        }
        for (camera in cameras) {
            BasicText(
                tr(
                    "{streetName}: {totalCount}× geblitzt · {totalRevenue}",
                    "streetName" to camera.streetName,
                    "totalCount" to camera.totalCount,
                    "totalRevenue" to formatEuro(camera.totalRevenue),
                ),
                style = text,
            )
            BasicText(
                tr("km/h zu schnell: ") +
                    FineTier.entries.joinToString(" · ") { "${it.shortLabel}: ${camera.countPerTier[it.ordinal]}" },
                style = muted,
            )
            if (camera.totalCount > 0) {
                BasicText(
                    tr(
                        "Rekord: {fastestMeasuredKmh} km/h bei Tempo {fastestLimitKmh}",
                        "fastestMeasuredKmh" to camera.fastestMeasuredKmh.roundToInt(),
                        "fastestLimitKmh" to camera.fastestLimitKmh,
                    ),
                    style = muted,
                )
            }
        }
    }
}

/** Gestapelter Balken: Anteil der Einnahmen je Stufe. */
@Composable
private fun TierBar(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val total = values.sum().coerceAtLeast(1)
    Canvas(modifier) {
        var x = 0f
        values.forEachIndexed { i, value ->
            val width = size.width * value / total
            drawRect(TierColors[i], topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
    }
}
