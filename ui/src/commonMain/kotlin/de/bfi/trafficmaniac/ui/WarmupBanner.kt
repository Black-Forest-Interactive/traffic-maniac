package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Meter
import kotlin.math.roundToInt

/**
 * Hinweis, solange der Vorlauf des Szenarios läuft: Die Stadt füllt sich im Zeitraffer, das Spiel ist schon bedienbar.
 */
@Composable
internal fun WarmupBanner(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(BANNER_WIDTH.dp)
            .background(TmColors.Ink.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicText(
            tr("Die Stadt erwacht … {percent} %", "percent" to (progress * PERCENT).roundToInt()),
            style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.OnDark),
        )
        BasicText(
            tr("Vorlauf im Zeitraffer – Sie können schon loslegen."),
            style = TmType.caption.copy(color = TmColors.OnDarkMuted),
        )
        Meter(progress, Modifier.padding(top = 6.dp).width(BANNER_WIDTH.dp), height = 6.dp)
    }
}

private const val BANNER_WIDTH = 260
private const val PERCENT = 100
