package de.bfi.trafficmaniac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import de.bfi.trafficmaniac.ui.TmColors

/** Kästchen zum Ankreuzen (z. B. für Sammelaktionen in Tabellen). */
@Composable
internal fun CheckBox(
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
) {
    CheckBox(if (checked) ToggleableState.On else ToggleableState.Off) { onCheck(!checked) }
}

/** Mit drittem Zustand „teilweise“ (Strich), z. B. für „Alle auswählen“ im Tabellenkopf. */
@Composable
internal fun CheckBox(
    state: ToggleableState,
    onClick: () -> Unit,
) {
    val checked = state != ToggleableState.Off
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(CHECK_SIZE.dp)
            .clip(shape)
            .background(if (checked) TmColors.Primary else TmColors.Surface)
            .border(1.5.dp, if (checked) TmColors.Primary else TmColors.Line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.On -> {
                Icon(Icons.Check, null, tint = TmColors.Surface, size = 14.dp)
            }

            ToggleableState.Indeterminate -> {
                Box(
                    Modifier.size(width = 10.dp, height = 2.dp).background(TmColors.Surface),
                )
            }

            ToggleableState.Off -> {
                Unit
            }
        }
    }
}

private const val CHECK_SIZE = 20
