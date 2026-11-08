package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.MinTouch
import de.bfi.trafficmaniac.ui.components.TmIcon

/** Werkzeuge im Dock, mit Icon und Tastenkürzel. */
enum class Tool(
    private val labelText: String,
    internal val icon: TmIcon,
    /** Taste, die das Werkzeug wählt, und ihre Beschriftung im Dock. */
    val key: Key,
    internal val keyLabel: String,
    /** Kürzerer Name fürs Dock, wenn der volle umbrechen würde; der volle steht dann im Tooltip. */
    private val shortText: String? = null,
) {
    LOOK(translatable("Ansehen"), Icons.Eye, Key.Escape, "Esc"),
    SPEED_LIMIT(translatable("Tempolimit"), Icons.Gauge, Key.Q, "Q"),
    SIGNAL(translatable("Ampel"), Icons.TrafficLight, Key.W, "W"),
    CAMERA(translatable("Blitzer"), Icons.Camera, Key.E, "E"),
    LASER(translatable("Laser"), Icons.Zap, Key.R, "R"),
    TRAILER(translatable("Anhänger"), Icons.Trailer, Key.T, "T"),
    PRIORITY(translatable("Vorfahrt"), Icons.Diamond, Key.A, "A"),
    BIKE_CROSSING(translatable("Radweg-Querung"), Icons.Bike, Key.S, "S", translatable("Radweg")),
    DETOUR(translatable("Umleitung"), Icons.Detour, Key.D, "D"),
    REMOVE(translatable("Abbauen"), Icons.Eraser, Key.X, "X"),
    ;

    val label: String get() = tr(labelText)

    /** Name im Dock – einzeilig. */
    internal val dockLabel: String get() = tr(shortText ?: labelText)

    companion object {
        /** Werkzeug zur Taste, sonst `null`. */
        fun forKey(key: Key): Tool? = entries.firstOrNull { it.key == key }
    }
}

/** Gewählter Wert des Tempolimit-Werkzeugs; `null` = ursprüngliches Limit wiederherstellen. */
data class ToolSelection(
    val tool: Tool = Tool.LOOK,
    val speedLimit: Int? = DEFAULT_SPEED_LIMIT,
    /** Tempolimit nur nachts (22–6 Uhr). */
    val nightOnly: Boolean = false,
) {
    companion object {
        const val DEFAULT_SPEED_LIMIT = 30
    }
}

/**
 * Eingaben aus dem Fenster an die Spieloberfläche: Werkzeug per Taste, die Entwickler-Anzeige (F3) und der
 * Straßennetz-Modus (F4). Das Fenster
 * setzt die Werte, die Oberfläche übernimmt sie.
 */
class HudInput {
    var toolRequest by mutableStateOf<Tool?>(null)
    var debug by mutableStateOf(false)
    var network by mutableStateOf(false)

    /** Fenster rechts öffnen (z. B. für Screenshots); die Oberfläche übernimmt es und setzt es zurück. */
    internal var panelRequest by mutableStateOf<SidePanel?>(null)
}

/** Was das Dock braucht: Auswahl, freigeschaltete Werkzeuge, freie Anhänger und was bei einer Auswahl passiert. */
internal class ToolDockState(
    val selection: ToolSelection,
    val available: Set<Tool>,
    val idleTrailers: Int,
    val onSelect: (ToolSelection) -> Unit,
    /** Planungsleiste über dem Dock, solange etwas geplant ist. */
    val plan: PlanBarState? = null,
)

private val DockShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(11.dp)
private val DockShadow = TmColors.Ink.copy(alpha = 0.32f)

/** Werkzeug-Dock unten mittig, darüber die Optionen des gewählten Werkzeugs. */
@Composable
internal fun ToolDock(
    state: ToolDockState,
    rules: GameRules,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.plan?.let { PlanBar(it) }
        if (state.selection.tool == Tool.SPEED_LIMIT) SpeedLimitOptions(state.selection, rules, state.onSelect)
        Row(
            Modifier.shadow(12.dp, DockShape, spotColor = DockShadow).background(TmColors.Ink, DockShape).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (tool in Tool.entries.filter { it in state.available }) {
                ToolTile(tool, state.selection.tool == tool, price(tool, rules, state.idleTrailers)) {
                    state.onSelect(state.selection.copy(tool = tool))
                }
            }
        }
    }
}

@Composable
private fun ToolTile(
    tool: Tool,
    active: Boolean,
    price: String,
    onClick: () -> Unit,
) {
    val color = if (active) TmColors.Ink else TmColors.OnDark
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Alle Kacheln gleich hoch: Icon, Name (einzeilig), Preis – auch wenn es keinen gibt
    Box(
        Modifier
            .width(TILE_WIDTH.dp)
            .height(TILE_HEIGHT.dp)
            .clip(TileShape)
            .background(if (active) TmColors.Active else Color.Transparent)
            .hoverable(interaction)
            .clickable(interaction, LocalIndication.current, role = Role.RadioButton, onClick = onClick),
    ) {
        BasicText(
            tool.keyLabel,
            style = TmType.mono(10.sp, color = color.copy(alpha = KEY_ALPHA)),
            modifier = Modifier.padding(start = 6.dp, top = 3.dp),
        )
        Column(
            Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(tool.icon, null, tint = color, size = 22.dp)
            BasicText(
                tool.dockLabel,
                style = TmType.text(11.sp, FontWeight.SemiBold, color).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                price.ifEmpty { " " },
                style = TmType.mono(10.sp, bold = false, color = if (active) TmColors.Ink else TmColors.OnDarkMuted),
                maxLines = 1,
            )
        }
        if (hovered) ToolTooltip("${tool.label} (${tool.keyLabel})")
    }
}

/** Voller Name über der Kachel, solange die Maus darauf steht. */
@Composable
private fun ToolTooltip(text: String) {
    val offset = with(LocalDensity.current) { IntOffset(0, -TOOLTIP_OFFSET.dp.roundToPx()) }
    Popup(alignment = Alignment.TopCenter, offset = offset) {
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

/** Tempolimit wählen: runde Schilder wie an der Straße (gewählt mit rotem Ring), „Original“ und „nur nachts“. */
@Composable
private fun SpeedLimitOptions(
    selection: ToolSelection,
    rules: GameRules,
    onSelect: (ToolSelection) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .shadow(8.dp, shape, spotColor = DockShadow)
            .background(TmColors.Surface, shape)
            .border(1.dp, TmColors.Line, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(tr("Tempolimit"), style = TmType.text(12.sp, FontWeight.Bold, TmColors.Muted))
        for (kmh in rules.speedLimitOptions) {
            SpeedSign(if (kmh == WALKING_SPEED) tr("Schritt") else "$kmh", selection.speedLimit == kmh) {
                onSelect(selection.copy(speedLimit = kmh))
            }
        }
        Box(Modifier.width(1.dp).height(28.dp).background(TmColors.Track))
        OptionChip(tr("Original"), selection.speedLimit == null) { onSelect(selection.copy(speedLimit = null)) }
        OptionChip(tr("nur nachts"), selection.nightOnly) { onSelect(selection.copy(nightOnly = !selection.nightOnly)) }
    }
}

@Composable
private fun SpeedSign(
    text: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(MinTouch)
            .clip(CircleShape)
            .background(TmColors.Surface)
            .border(if (chosen) 3.dp else 1.dp, if (chosen) TmColors.Danger else TmColors.Line, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style =
                TmType.text(
                    if (text.length >
                        2
                    ) {
                        9.sp
                    } else {
                        14.sp
                    },
                    FontWeight.Bold,
                    if (chosen) TmColors.Ink else TmColors.Muted,
                ),
        )
    }
}

@Composable
private fun OptionChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(if (active) TmColors.Active else TmColors.Surface)
            .border(1.dp, if (active) TmColors.Active else TmColors.Line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = TmType.text(12.sp, FontWeight.SemiBold)) }
}

/** Preis eines Werkzeugs für das Dock; leer, wenn es nichts kostet. */
private fun price(
    tool: Tool,
    rules: GameRules,
    idleTrailers: Int,
): String =
    when (tool) {
        Tool.SPEED_LIMIT -> {
            formatEuro(rules.speedLimitSignCost)
        }

        Tool.SIGNAL -> {
            formatEuro(rules.signalCost)
        }

        Tool.CAMERA -> {
            formatEuro(rules.cameraCost)
        }

        Tool.LASER -> {
            tr("{cost}/Tag", "cost" to formatEuro(rules.laserDailyCost))
        }

        Tool.TRAILER -> {
            if (idleTrailers > 0) {
                tr("{count} frei", "count" to idleTrailers)
            } else {
                formatEuro(rules.trailerCost)
            }
        }

        Tool.PRIORITY -> {
            formatEuro(rules.priorityRoadCost)
        }

        Tool.BIKE_CROSSING -> {
            formatEuro(rules.bikeCrossingCost)
        }

        else -> {
            ""
        }
    }

internal const val WALKING_SPEED = 7
private const val TILE_WIDTH = 78
private const val TILE_HEIGHT = 68
private const val KEY_ALPHA = 0.6f
private const val TOOLTIP_OFFSET = 34
