package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.AchievementStatus
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.KpiTile
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.PageMode

private val CardShape = RoundedCornerShape(14.dp)

/**
 * Erfolge als Seite (Entwurf 2, `V2-Erfolge`): wie viele erreicht, die drei, die fast geschafft sind, Filter und
 * das Raster über die volle Breite (5 Spalten).
 */
@Composable
internal fun AchievementsPage(
    achievements: List<AchievementStatus>,
    firstDay: Int,
    page: PageMode,
) {
    var filter by remember { mutableStateOf<Boolean?>(null) }
    val done = achievements.count { it.unlocked }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(tr("Amtszeit"), tr("Erfolge"), page.onMap)
        BasicText(tr("Zählen nicht für die Wertung – aber für den Ruhm."), style = TmType.small)
        KpiRow {
            KpiTile(
                tr("Erreicht"),
                "$done/${achievements.size}",
                meter = if (achievements.isEmpty()) 0f else done.toFloat() / achievements.size,
            )
            AlmostDone(achievements, Modifier.weight(2f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, label) in listOf(null to tr("Alle"), true to tr("Erreicht"), false to tr("Offen"))) {
                val chosen = filter == value
                BasicText(
                    label,
                    style = TmType.text(12.sp, FontWeight.SemiBold, if (chosen) TmColors.Surface else TmColors.Ink),
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (chosen) TmColors.Ink else TmColors.Surface)
                            .clickable(role = Role.RadioButton) { filter = value }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        val shown = achievements.filter { filter == null || it.unlocked == filter }.sortedBy { !it.unlocked }
        for (row in shown.chunked(COLUMNS)) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (status in row) AchievementTile(status, firstDay, Modifier.weight(1f))
                repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Die drei offenen Erfolge mit dem größten Fortschritt, je mit Balken. */
@Composable
private fun AlmostDone(
    achievements: List<AchievementStatus>,
    modifier: Modifier,
) {
    val close =
        achievements
            .filter { !it.unlocked && (it.progress ?: 0) > 0 && it.achievement.target > 0 }
            .sortedByDescending { (it.progress ?: 0).toFloat() / it.achievement.target }
            .take(ALMOST)
    Column(
        modifier
            .clip(
                CardShape,
            ).background(
                TmColors.Surface,
            ).border(1.dp, TmColors.LineSoft, CardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(tr("Fast geschafft"), style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Muted))
        if (close.isEmpty()) BasicText(tr("Noch nichts in Reichweite."), style = TmType.small)
        for (status in close) {
            val target = status.achievement.target
            val progress = status.progress ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(
                    status.achievement.title,
                    style = TmType.text(13.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(TITLE_WIDTH.dp),
                )
                Meter(progress.toFloat() / target, Modifier.weight(1f), height = 6.dp)
                BasicText(
                    "${formatNumber(progress.toLong())}/${formatNumber(target.toLong())}",
                    style = TmType.mono(11.sp, bold = false, color = TmColors.Muted),
                )
            }
        }
    }
}

private const val COLUMNS = 5
private const val ALMOST = 3
private const val TITLE_WIDTH = 160
