package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.TransitLineStatus
import de.bfi.trafficmaniac.sim.TransitSnapshot
import de.bfi.trafficmaniac.ui.components.Divider
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bericht „ÖPNV“: wie viele Busse unterwegs sind und wie pünktlich jede Linie heute ist. Verspätung entsteht im Stau
 * und hinter Lieferwagen – was den Autos hilft, hilft meist auch den Bussen.
 */
@Composable
fun TransitPanel(
    transit: TransitSnapshot,
    /** Anteil der Autofahrten, die heute mit dem Bus stattfinden. */
    modeShift: Double = 0.0,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val served = transit.lines.sumOf { it.stopsServed }
    val punctual = transit.lines.sumOf { it.punctualShare * it.stopsServed }
    PanelScaffold(
        tr("Bericht"),
        tr("ÖPNV"),
        modifier,
        onClose,
        floating = false,
        headerExtra = {
            if (served > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(tr("Pünktlich heute"), style = TmType.caption)
                    BasicText(
                        percent(punctual / served),
                        style =
                            TmType.mono(
                                18.sp,
                                color =
                                    shareColor(punctual / served),
                            ),
                    )
                }
            }
        },
    ) {
        if (transit.lines.isEmpty()) {
            PanelSection(divider = false) {
                BasicText(
                    tr(
                        "Für diese Stadt sind keine Buslinien bekannt – " +
                            "die Karte neu importieren (scripts/import-city.sh).",
                    ),
                    style = TmType.small,
                )
            }
            return@PanelScaffold
        }
        TransitSummary(transit, served, modeShift)
        PanelSection(divider = false) {
            val byLine = transit.lines.groupBy { it.ref }
            val refs = byLine.keys.sortedWith(compareBy({ it.length }, { it }))
            for ((index, ref) in refs.withIndex()) {
                if (index > 0) Divider()
                LineBlock(ref, byLine.getValue(ref))
            }
        }
    }
}

/** Busse unterwegs, Halte heute und was die Pünktlichkeit bewirkt. */
@Composable
private fun TransitSummary(
    transit: TransitSnapshot,
    served: Int,
    modeShift: Double,
) {
    PanelSection {
        BasicText(
            tr(
                "{buses} Busse unterwegs, {stops} Halte heute. Pünktlich heißt: höchstens drei Minuten zu spät.",
                "buses" to transit.lines.sumOf { it.buses },
                "stops" to served,
            ),
            style = TmType.small,
        )
        BasicText(
            if (modeShift > 0) {
                tr(
                    "Weil die Busse gestern pünktlich waren, bleiben heute {percent} % der Autos stehen.",
                    "percent" to formatDecimal(modeShift * PERCENT, 1),
                )
            } else {
                tr(
                    "Ab 60 % Pünktlichkeit lassen die ersten das Auto stehen. " +
                        "Am meisten hilft Busvorrang an den Ampeln.",
                )
            },
            style = TmType.small.copy(color = TmColors.Muted),
        )
    }
}

/** Eine Linie: Nummer in Linienfarbe, darunter je Richtung Ziel, Pünktlichkeit und mittlere Verspätung. */
@Composable
private fun LineBlock(
    ref: String,
    directions: List<TransitLineStatus>,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LineBadge(ref, Color(directions.first().color))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (direction in directions) DirectionRow(direction)
        }
    }
}

@Composable
private fun LineBadge(
    ref: String,
    color: Color,
) {
    Box(
        Modifier
            .widthIn(min = BADGE_WIDTH.dp)
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(ref, style = TmType.mono(13.sp, color = Color.White).copy(textAlign = TextAlign.Center))
    }
}

@Composable
private fun DirectionRow(line: TransitLineStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                destination(line.name),
                style = TmType.text(13.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                if (line.stopsServed == 0) "–" else delayText(line.averageDelaySeconds),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
            )
        }
        if (line.stopsServed > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meter(
                    line.punctualShare.toFloat(),
                    Modifier.weight(1f),
                    height = 6.dp,
                    color = shareColor(line.punctualShare),
                )
                BasicText(percent(line.punctualShare), style = TmType.mono(11.sp), modifier = Modifier.width(36.dp))
            }
        }
    }
}

/** „Bus 1: Arlinger => Eutingen“ → „Arlinger → Eutingen“. */
private fun destination(name: String): String = name.substringAfter(": ").replace("=>", "→")

/** „+2:30“ bzw. „−0:40“ (Minuten:Sekunden). */
private fun delayText(seconds: Double): String {
    val total = abs(seconds).roundToInt()
    val sign = if (seconds < 0) "−" else "+"
    return "$sign${total / SECONDS}:${(total % SECONDS).toString().padStart(2, '0')}"
}

private fun percent(share: Double): String = "${(share * PERCENT).roundToInt()} %"

private fun shareColor(share: Double): Color =
    when {
        share >= GOOD_SHARE -> TmColors.Success
        share >= FAIR_SHARE -> TmColors.Warn
        else -> TmColors.DangerText
    }

private const val BADGE_WIDTH = 34
private const val SECONDS = 60
private const val PERCENT = 100
private const val GOOD_SHARE = 0.85
private const val FAIR_SHARE = 0.6
