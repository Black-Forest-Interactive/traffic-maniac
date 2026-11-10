package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon

/** Was die linke Leiste per Finger aufklappt: die Ämter oder die Berichte. */
internal enum class RailMenu(
    val labelText: String,
    val icon: TmIcon,
) {
    OFFICES(translatable("Ämter"), Icons.Scale),
    REPORTS(translatable("Berichte"), Icons.Clipboard),
    ;

    /** Fenster im Menü; Einsätze haben einen eigenen Knopf mit Zähler. */
    val items: List<RailEntry>
        get() =
            if (this == OFFICES) {
                de.bfi.trafficmaniac.ui.OFFICES
            } else {
                de.bfi.trafficmaniac.ui.REPORTS
                    .filter { it.panel != SidePanel.EMERGENCIES }
            }
}

/** Was in der linken Leiste gerade offen ist. */
internal class RailState(
    val panel: SidePanel,
    val menu: RailMenu?,
    val feed: Boolean,
    val views: Boolean,
    val emergencies: Int,
)

/** Was die Knöpfe der linken Leiste auslösen. */
internal class RailActions(
    val onMenu: (RailMenu) -> Unit,
    val onPanel: (SidePanel) -> Unit,
    val onFeed: () -> Unit,
    val onViews: () -> Unit,
)

/**
 * Schmale linke Leiste für den Finger: Ämter und Berichte klappen ein Menü auf, Einsätze (mit Zähler) öffnet das
 * Fenster direkt, „Lage“ blendet Verkehrslage und Meldungen ein, „Karte“ die Kartenansichten.
 */
@Composable
internal fun TouchRail(
    state: RailState,
    actions: RailActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(TOUCH_RAIL_WIDTH.dp)
            .fillMaxHeight()
            .background(TmColors.Ink2)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (menu in RailMenu.entries) {
            val inside = menu.items.any { it.panel == state.panel }
            TouchRailButton(menu.icon, tr(menu.labelText), state.menu == menu || inside) { actions.onMenu(menu) }
        }
        TouchRailButton(Icons.Siren, tr("Einsätze"), state.panel == SidePanel.EMERGENCIES, state.emergencies) {
            actions.onPanel(SidePanel.EMERGENCIES)
        }
        TouchRailButton(Icons.Radio, tr("Lage"), state.feed, onClick = actions.onFeed)
        TouchRailButton(Icons.Layers, tr("Karte"), state.views, onClick = actions.onViews)
    }
}

@Composable
private fun TouchRailButton(
    icon: TmIcon,
    label: String,
    active: Boolean,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    val color = if (active) TmColors.Ink else TmColors.OnDark
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = RAIL_BUTTON_HEIGHT.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) TmColors.Active else Color.Transparent)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, tint = color, size = 20.dp)
            BasicText(
                label,
                style = TmType.text(10.sp, FontWeight.SemiBold, color).copy(textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
        if (badge > 0) {
            BasicText(
                "$badge",
                style = TmType.text(10.sp, FontWeight.Bold, TmColors.Surface),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 2.dp)
                        .background(TmColors.Danger, RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp),
            )
        }
    }
}

/** Aufgeklapptes Menü neben der Leiste: je Fenster eine Zeile mit Symbol und Namen; das offene ist hervorgehoben. */
@Composable
internal fun RailMenuCard(
    menu: RailMenu,
    panel: SidePanel,
    onPanel: (SidePanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .width(MENU_WIDTH.dp)
            .shadow(10.dp, shape)
            .background(TmColors.Surface, shape)
            .border(1.dp, TmColors.Line, shape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(
            tr(menu.labelText).uppercase(),
            style = TmType.eyebrow,
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
        )
        for (entry in menu.items) {
            val open = entry.panel == panel
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MENU_ROW_HEIGHT.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (open) TmColors.PrimaryTint else Color.Transparent)
                    .clickable(role = Role.Tab) { onPanel(entry.panel) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(entry.icon, null, tint = TmColors.Ink, size = 20.dp)
                // „Ordnungs-\namt“ steht in der Leiste zweizeilig, hier in einer Zeile
                BasicText(tr(entry.labelText).replace("-\n", ""), style = TmType.text(14.sp, FontWeight.SemiBold))
            }
        }
    }
}

internal const val TOUCH_RAIL_WIDTH = 64
private const val RAIL_BUTTON_HEIGHT = 52
private const val MENU_WIDTH = 220
private const val MENU_ROW_HEIGHT = 44
