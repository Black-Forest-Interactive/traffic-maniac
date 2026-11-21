package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.TransitLineStatus
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.lineColor
import de.bfi.trafficmaniac.ui.map.lineNumber

/** Liniennummern, die über den Abschnitt am Klickpunkt fahren; `null`, wenn dort keine Linie ist. */
internal fun linesAt(
    frame: TrafficFrame,
    picker: MapPicker,
    x: Float,
    y: Float,
    radius: Float,
): List<String>? {
    val segment = picker.nearestSegment(x, y, radius) ?: return null
    return frame.current.transit.lines
        .filter { line -> line.sections.any { segment in it.segments } }
        .map { lineNumber(it.ref) }
        .distinct()
        .sortedWith(compareBy({ it.length }, { it }))
        .takeIf { it.isNotEmpty() }
}

/** Karte rechts unten: die Linien am angeklickten Abschnitt als Badges – ein Klick hebt eine hervor. */
@Composable
internal fun LinesHere(
    refs: List<String>,
    lines: List<TransitLineStatus>,
    toggles: MapToggles,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(CARD_WIDTH.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(TmColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                if (refs.size == 1) tr("1 Linie hier") else tr("{count} Linien hier", "count" to refs.size),
                style = TmType.text(14.sp, FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Close,
                tr("Schließen"),
                Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClose),
                tint = TmColors.Muted,
                size = 16.dp,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (ref in refs) {
                val line = lines.firstOrNull { lineNumber(it.ref) == ref } ?: continue
                val chosen = toggles.transitHighlight?.let(::lineNumber) == ref
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (chosen) TmColors.PrimaryTint else Color.Transparent)
                        .clickable(role = Role.Button) { toggles.transitHighlight = if (chosen) null else line.ref }
                        .padding(3.dp),
                ) { LineBadge(ref, lineColor(line)) }
            }
        }
        BasicText(tr("Linie anklicken: hervorheben, noch einmal: alle zeigen"), style = TmType.caption)
    }
}

private const val CARD_WIDTH = 300
