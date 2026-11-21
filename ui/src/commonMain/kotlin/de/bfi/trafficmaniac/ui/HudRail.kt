package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Icon

/**
 * Linke Leiste (80 dp): Karte, Posteingang, die Ämter unter „Rathaus“ und unten Erfolge und Hilfe. Jeder Eintrag
 * öffnet seine Seite anstelle der Karte ([SidePanel.NONE] = Karte); der aktive ist Ampelgelb. Reicht die Höhe nicht,
 * scrollt der obere Teil, Erfolge und Hilfe bleiben unten.
 */
@Composable
internal fun HudRail(
    panel: SidePanel,
    onPanel: (SidePanel) -> Unit,
    badges: RailBadges,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .width(RAIL_WIDTH.dp)
            .fillMaxHeight()
            .background(TmColors.Ink2)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        // Einträge 44–62 dp hoch, je nachdem wie viel Platz die Leiste hat
        val entries = RAIL_TOP.size + RAIL_TOWN_HALL.size + RAIL_BOTTOM.size
        val free = maxHeight.value - GROUP_LABEL_HEIGHT - GROUP_GAP - entries * ITEM_GAP
        val height = (free / entries).coerceIn(MIN_ITEM_HEIGHT, MAX_ITEM_HEIGHT)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ITEM_GAP.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ITEM_GAP.dp),
            ) {
                for (entry in RAIL_TOP) RailButton(entry, panel, badges, height, onPanel)
                GroupLabel(tr("Rathaus"))
                for (entry in RAIL_TOWN_HALL) RailButton(entry, panel, badges, height, onPanel)
            }
            for (entry in RAIL_BOTTOM) RailButton(entry, panel, badges, height, onPanel)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    BasicText(
        text.uppercase(),
        style =
            TmType
                .text(9.sp, FontWeight.Bold, TmColors.OnDarkMuted)
                .copy(letterSpacing = 1.sp, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun RailButton(
    entry: RailEntry,
    panel: SidePanel,
    badges: RailBadges,
    height: Float,
    onPanel: (SidePanel) -> Unit,
) {
    val active = panel == entry.panel
    val badge = badges.of(entry.panel)
    val onClick = { onPanel(entry.panel) }
    val color = if (active) TmColors.Ink else TmColors.OnDark
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val label = tr(entry.labelText)
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        active -> TmColors.Active
                        hovered -> TmColors.Ink3
                        else -> Color.Transparent
                    },
                ).hoverable(interaction)
                .clickable(interaction, LocalIndication.current, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = "$label (${entry.keyLabel})" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icon(entry.icon, null, tint = color, size = 22.dp)
            BasicText(
                label,
                style = TmType.text(11.sp, FontWeight.SemiBold, color).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badge > 0) {
            BasicText(
                if (badge > MAX_BADGE) "$MAX_BADGE+" else "$badge",
                style = TmType.text(10.sp, FontWeight.Bold, TmColors.Surface).copy(textAlign = TextAlign.Center),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 4.dp)
                        .background(TmColors.Danger, RoundedCornerShape(9.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (hovered) RailTooltip("$label · ${entry.keyLabel}")
    }
}

/** Name und Taste rechts neben dem Eintrag, solange die Maus darauf steht. */
@Composable
private fun RailTooltip(text: String) {
    val offset = with(LocalDensity.current) { IntOffset((RAIL_WIDTH - 2).dp.roundToPx(), 0) }
    Popup(alignment = Alignment.CenterStart, offset = offset) {
        BasicText(
            text,
            style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.OnDark),
            modifier =
                Modifier
                    .background(
                        TmColors.Ink,
                        RoundedCornerShape(6.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private const val RAIL_WIDTH = 80
private const val MIN_ITEM_HEIGHT = 44f
private const val MAX_ITEM_HEIGHT = 62f
private const val GROUP_LABEL_HEIGHT = 22f

/** Luft über „Erfolge“: mindestens so viel bleibt zwischen den Ämtern und dem unteren Teil. */
private const val GROUP_GAP = 24f
private const val ITEM_GAP = 2
private const val MAX_BADGE = 99
