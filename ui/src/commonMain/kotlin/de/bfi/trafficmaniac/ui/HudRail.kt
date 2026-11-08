package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Einträge der linken Leiste: Kurzname, Icon und das Fenster, das sie öffnen. */
private class RailEntry(
    val panel: SidePanel,
    val labelText: String,
    val icon: TmIcon,
)

private val OFFICES =
    listOf(
        // Bewusst getrennt: ein weiches Trennzeichen bricht um, zeigt aber keinen Bindestrich („Ordnungs / amt“)
        RailEntry(SidePanel.OFFICE, translatable("Ordnungs-\namt"), Icons.Scale),
        RailEntry(SidePanel.MAINTENANCE, translatable("Bauamt"), Icons.HardHat),
        RailEntry(SidePanel.PARKING, translatable("Parken"), Icons.Parking),
    )

private val REPORTS =
    listOf(
        RailEntry(SidePanel.CAMERAS, translatable("Blitzer"), Icons.Camera),
        RailEntry(SidePanel.BUDGET, translatable("Haushalt & PR"), Icons.Wallet),
        RailEntry(SidePanel.CITIZENS, translatable("Bürger"), Icons.Users),
        RailEntry(SidePanel.EVENTS, translatable("Kalender"), Icons.Calendar),
        RailEntry(SidePanel.EMERGENCIES, translatable("Einsätze"), Icons.Siren),
        RailEntry(SidePanel.ACHIEVEMENTS, translatable("Erfolge"), Icons.Award),
        RailEntry(SidePanel.HELP, translatable("Hilfe"), Icons.Help),
    )

/**
 * Linke Leiste mit den Ämtern und Berichten. Ein Klick öffnet das Fenster rechts, ein zweiter schließt es; das
 * offene ist Ampelgelb. [emergencies] erscheint als roter Zähler an „Einsätze“.
 */
@Composable
internal fun HudRail(
    panel: SidePanel,
    onPanel: (SidePanel) -> Unit,
    emergencies: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(RAIL_WIDTH.dp)
            .fillMaxHeight()
            .background(TmColors.Ink2)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GroupLabel(tr("Ämter"))
        for (entry in OFFICES) RailButton(entry, panel == entry.panel, 0) { onPanel(entry.panel) }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 6.dp,
                ).height(1.dp)
                .background(TmColors.LineDark),
        )
        GroupLabel(tr("Berichte"))
        for (entry in REPORTS) {
            val badge = if (entry.panel == SidePanel.EMERGENCIES) emergencies else 0
            RailButton(entry, panel == entry.panel, badge) { onPanel(entry.panel) }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    BasicText(
        text.uppercase(),
        style =
            TmType
                .text(
                    10.sp,
                    FontWeight.Bold,
                    TmColors.OnDarkMuted,
                ).copy(letterSpacing = 1.sp, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun RailButton(
    entry: RailEntry,
    active: Boolean,
    badge: Int,
    onClick: () -> Unit,
) {
    val color = if (active) TmColors.Ink else TmColors.OnDark
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) TmColors.Active else Color.Transparent)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(entry.icon, null, tint = color, size = 22.dp)
            BasicText(
                tr(entry.labelText),
                style = TmType.text(11.sp, FontWeight.SemiBold, color).copy(textAlign = TextAlign.Center),
            )
        }
        if (badge > 0) {
            BasicText(
                "$badge",
                style = TmType.text(11.sp, FontWeight.Bold, TmColors.Surface).copy(textAlign = TextAlign.Center),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 4.dp)
                        .background(TmColors.Danger, RoundedCornerShape(9.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

private const val RAIL_WIDTH = 92
