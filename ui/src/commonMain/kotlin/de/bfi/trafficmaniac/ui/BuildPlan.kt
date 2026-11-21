package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.map.MapPicker

private val PlanShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(10.dp)
private val PlanShadow = TmColors.Ink.copy(alpha = 0.25f)
private val SumBackground = Color(0xFFF8F9FB)

/**
 * Bauplan rechts unten (340 dp), solange etwas geplant ist: die geplanten Maßnahmen mit Ort, Preis und ✕, darunter
 * „Sofort“ und „Kasse danach“ (rot im Minus), `↶` · „Verwerfen“ · „Bauen ↵“. Gebaut wird in der geplanten Reihenfolge.
 */
@Composable
internal fun BuildPlan(
    plan: Plan,
    balance: Long,
    picker: MapPicker,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!plan.isActive) return
    val actions = plan.actions
    val after = balance - plan.total
    Column(
        modifier
            .width(PLAN_WIDTH.dp)
            .shadow(10.dp, PlanShape, spotColor = PlanShadow)
            .background(TmColors.Surface, PlanShape)
            .border(2.dp, TmColors.Primary, PlanShape)
            .clip(PlanShape),
    ) {
        PlanHeader(actions.size, plan::undo)
        if (plan.routeStart != null) {
            BasicText(
                tr("Anfang gesetzt – jetzt das Ende anklicken (dieselbe Stelle: nur bis zur Kreuzung)"),
                style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Primary),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .heightIn(
                    max = LIST_MAX_HEIGHT.dp,
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            for (action in actions) {
                key(action) { PlanRow(action, picker) { plan.remove(action) } }
            }
        }
        PlanFooter(plan, after, onCommand)
    }
}

@Composable
private fun PlanHeader(
    count: Int,
    onUndo: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                tr("Bauplan").uppercase(),
                style = TmType.text(11.sp, FontWeight.Bold, TmColors.Primary).copy(letterSpacing = 1.sp),
            )
            BasicText(
                when (count) {
                    0 -> tr("Noch nichts geplant")
                    1 -> tr("1 Maßnahme geplant")
                    else -> tr("{count} Maßnahmen geplant", "count" to count)
                },
                style = TmType.text(15.sp, FontWeight.Bold),
            )
        }
        UndoButton(onUndo)
    }
}

/** Summen und Knöpfe unter der Liste. */
@Composable
private fun PlanFooter(
    plan: Plan,
    after: Long,
    onCommand: (GameCommand) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SumBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SumLine(tr("Sofort"), formatEuro(plan.total), TmColors.Ink)
        SumLine(tr("Kasse danach"), formatEuro(after), if (after < 0) TmColors.DangerText else TmColors.Ink)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlanButton(tr("Verwerfen"), null, primary = false, enabled = true, Modifier.weight(1f), plan::clear)
        PlanButton(
            tr("Bauen"),
            "↵",
            primary = true,
            enabled = plan.actions.isNotEmpty(),
            Modifier.weight(BUILD_WEIGHT),
        ) {
            plan.take().forEach(onCommand)
        }
    }
}

/** Eine geplante Maßnahme: Name, Ort (und wie viele Strecken), Preis, ✕ zum Streichen. */
@Composable
private fun PlanRow(
    action: PlannedAction,
    picker: MapPicker,
    onRemove: () -> Unit,
) {
    val place = remember(action) { placeOf(action, picker) }
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.Ground))
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    action.title,
                    style = TmType.text(13.sp, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    place,
                    style = TmType.text(11.sp, color = TmColors.Muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(
                action.price ?: if (action.cost == 0L) tr("kostenlos") else formatEuro(action.cost),
                style = TmType.mono(12.sp, bold = false),
            )
            Box(
                Modifier
                    .size(REMOVE_SIZE.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Close, tr("{name} entfernen", "name" to action.title), tint = TmColors.Muted, size = 14.dp)
            }
        }
    }
}

/** Straße der Maßnahme; bei mehreren Strecken mit ihrer Zahl. */
private fun placeOf(
    action: PlannedAction,
    picker: MapPicker,
): String {
    val marker = action.marker
    val segment =
        marker.segments.getOrNull(marker.segments.size / 2)
            ?: picker.nearestSegment(marker.x, marker.y, PLACE_RADIUS, allowPassive = { true })
    val street =
        segment?.let {
            picker.city.roads.segments[it]
                .name
        } ?: tr("Straße ohne Namen")
    val count = action.commands.size
    return if (count > 1) tr("{street} · {count} Strecken", "street" to street, "count" to count) else street
}

@Composable
private fun SumLine(
    label: String,
    value: String,
    color: Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(label, style = TmType.text(13.sp, color = color))
        BasicText(value, style = TmType.mono(13.sp, color = color))
    }
}

@Composable
private fun UndoButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(UNDO_SIZE.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, TmColors.Line, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Undo, tr("Letzte rückgängig (Strg+Z)"), tint = TmColors.Ink, size = 18.dp)
    }
}

@Composable
private fun PlanButton(
    text: String,
    keyHint: String?,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val fill =
        when {
            !primary -> TmColors.Surface
            enabled -> TmColors.Primary
            else -> TmColors.Track
        }
    val textColor = if (primary) TmColors.Surface else TmColors.Ink
    Row(
        modifier
            .height(BUTTON_HEIGHT.dp)
            .clip(ButtonShape)
            .background(fill)
            .then(if (primary) Modifier else Modifier.border(1.dp, TmColors.Line, ButtonShape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text,
            style =
                TmType.text(
                    if (primary) 14.sp else 13.sp,
                    if (primary) FontWeight.Bold else FontWeight.SemiBold,
                    textColor,
                ),
        )
        keyHint?.let {
            BasicText(
                it,
                style = TmType.mono(11.sp, bold = false, color = textColor.copy(alpha = HINT_ALPHA)),
            )
        }
    }
}

private const val PLAN_WIDTH = 340
private const val LIST_MAX_HEIGHT = 260
private const val REMOVE_SIZE = 28
private const val UNDO_SIZE = 36
private const val BUTTON_HEIGHT = 40
private const val BUILD_WEIGHT = 1.4f
private const val HINT_ALPHA = 0.8f
private const val PLACE_RADIUS = 60f
