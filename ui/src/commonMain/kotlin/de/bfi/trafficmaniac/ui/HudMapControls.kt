package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.map.EnvironmentLayer

/** Was die Karte über dem Verkehr zeigt: nichts, Umwelt (Lärm/Luft/CO₂), Zufriedenheit oder Straßenzustand. */
internal enum class MapMode { TRAFFIC, ENVIRONMENT, MOODS, CONDITION }

/** Gewählte Kartenansicht; „Umwelt“ merkt sich, welche der drei Ebenen zuletzt gezeigt wurde. */
internal class MapToggles {
    var mode by mutableStateOf(MapMode.TRAFFIC)
    var environment by mutableStateOf(EnvironmentLayer.AIR)

    val showMoods: Boolean get() = mode == MapMode.MOODS
    val showCondition: Boolean get() = mode == MapMode.CONDITION
    val environmentLayer: EnvironmentLayer
        get() = if (mode == MapMode.ENVIRONMENT) environment else EnvironmentLayer.NONE

    /** Umwelt ein zweites Mal gewählt: nächste Umwelt-Ebene. */
    fun select(choice: MapMode) {
        if (choice == MapMode.ENVIRONMENT && mode == MapMode.ENVIRONMENT) {
            val layers = EnvironmentLayer.entries.drop(1)
            environment = layers[(layers.indexOf(environment) + 1) % layers.size]
        }
        mode = choice
    }
}

private val Shape = RoundedCornerShape(12.dp)
private val ShadowColor = TmColors.Ink.copy(alpha = 0.3f)

/** Umschalter der Kartenansicht oben mittig: dunkles Segment, gewählt = weiß. */
@Composable
internal fun MapViewSwitcher(
    toggles: MapToggles,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.shadow(8.dp, Shape, spotColor = ShadowColor).background(TmColors.Ink, Shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (mode in MapMode.entries) {
            val chosen = toggles.mode == mode
            val label =
                when (mode) {
                    MapMode.TRAFFIC -> tr("Verkehr")
                    MapMode.ENVIRONMENT -> tr("Umwelt: {layer}", "layer" to toggles.environment.label)
                    MapMode.MOODS -> tr("Zufriedenheit")
                    MapMode.CONDITION -> tr("Straßenzustand")
                }
            Box(
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (chosen) TmColors.Surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { toggles.select(mode) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label,
                    style = TmType.text(13.sp, FontWeight.SemiBold, if (chosen) TmColors.Ink else TmColors.OnDark),
                )
            }
        }
    }
}

/** Zoom-Knöpfe rechts mittig. */
@Composable
internal fun ZoomControls(
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.shadow(8.dp, Shape, spotColor = ShadowColor).background(TmColors.Ink, Shape).clip(Shape)) {
        ZoomButton(Icons.Plus, tr("Hineinzoomen")) { onZoom(ZOOM_STEP) }
        Box(Modifier.width(44.dp).height(1.dp).background(TmColors.LineDark))
        ZoomButton(Icons.Minus, tr("Herauszoomen")) { onZoom(1 / ZOOM_STEP) }
    }
}

@Composable
private fun ZoomButton(
    icon: de.bfi.trafficmaniac.ui.components.TmIcon,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(44.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = TmColors.OnDark, size = 20.dp) }
}

private const val ZOOM_STEP = 1.6f
