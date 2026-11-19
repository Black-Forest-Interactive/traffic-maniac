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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.MinTouch

/**
 * Gruppen im Werkzeug-Dock, je mit Taste. Die Arten einer Gruppe ([ToolKind]) wählt man in der Ausklappliste über
 * der Kachel; die Kachel zeigt die zuletzt gewählte.
 */
enum class Tool(
    private val labelText: String,
    /** Taste, die die Gruppe wählt (erneut gedrückt: die nächste Art), und ihre Beschriftung. */
    val key: Key,
    internal val keyLabel: String,
) {
    LOOK(translatable("Ansehen"), Key.Escape, "Esc"),
    SPEED_LIMIT(translatable("Tempolimit"), Key.Q, "Q"),
    SIGNAL(translatable("Ampel"), Key.W, "W"),
    CAMERA(translatable("Überwachung"), Key.E, "E"),
    ROUTE(translatable("Verkehrsführung"), Key.A, "A"),
    ROADWORKS(translatable("Baustelle"), Key.B, "B"),
    REMOVE(translatable("Abbauen"), Key.X, "X"),
    ;

    val label: String get() = tr(labelText)

    /** Die Arten dieser Gruppe in Listenreihenfolge. */
    val kinds: List<ToolKind> get() = ToolKind.entries.filter { it.group == this }

    /** Mehrere Arten: Kachel mit Pfeil und Ausklappliste. */
    val isGroup: Boolean get() = kinds.size > 1

    companion object {
        /** Gruppe zur Taste, sonst `null`. */
        fun forKey(key: Key): Tool? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Was gerade gewählt ist: die Gruppe, je Gruppe die zuletzt gewählte Art und die Einstellungen des Tempolimits
 * ([speedLimit] `null` = ursprüngliches Limit wiederherstellen).
 */
data class ToolSelection(
    val tool: Tool = Tool.LOOK,
    val speedLimit: Int? = DEFAULT_SPEED_LIMIT,
    /** Tempolimit nur nachts (22–6 Uhr). */
    val nightOnly: Boolean = false,
    /** Zuletzt gewählte Art je Gruppe; ohne Eintrag die erste. */
    val chosen: Map<Tool, ToolKind> = emptyMap(),
) {
    /** Die Art, mit der ein Klick auf die Karte gerade plant. */
    val kind: ToolKind get() = chosen[tool] ?: tool.kinds.first()

    /** Art in ihrer Gruppe wählen (und die Gruppe aktiv machen). */
    fun with(kind: ToolKind): ToolSelection = copy(tool = kind.group, chosen = chosen + (kind.group to kind))

    /** Die nächste Art der Gruppe (Gruppentaste erneut gedrückt). */
    fun next(): ToolSelection = tool.kinds.let { with(it[(it.indexOf(kind) + 1) % it.size]) }

    companion object {
        const val DEFAULT_SPEED_LIMIT = 30

        fun of(kind: ToolKind): ToolSelection = ToolSelection().with(kind)
    }
}

/**
 * Eingaben aus dem Fenster an die Spieloberfläche: Werkzeug per Taste, Art per Ziffer bei offener Liste, die
 * Entwickler-Anzeige (F3) und der Straßennetz-Modus (F4). Das Fenster setzt die Werte, die Oberfläche übernimmt sie.
 */
class HudInput {
    var toolRequest by mutableStateOf<Tool?>(null)
    var debug by mutableStateOf(false)
    var network by mutableStateOf(false)

    /** Ausklappliste offen: Ziffern wählen dann eine Art statt das Tempo. */
    var listOpen by mutableStateOf(false)

    /** Ziffer 1–9 bei offener Liste: die Art an dieser Stelle. */
    var kindRequest by mutableStateOf<Int?>(null)

    /** Fenster rechts öffnen (z. B. für Screenshots); die Oberfläche übernimmt es und setzt es zurück. */
    internal var panelRequest by mutableStateOf<SidePanel?>(null)

    /** Ausklappliste einer Gruppe öffnen (z. B. für Screenshots), wie ein Klick auf ihre Kachel. */
    internal var listRequest by mutableStateOf<Tool?>(null)
}

/** Was das Dock braucht: Auswahl, freigeschaltete Gruppen, freie Anhänger, offene Liste und die Rückmeldungen. */
internal class ToolDockState(
    val selection: ToolSelection,
    val available: Set<Tool>,
    val idleTrailers: Int,
    /** Gruppe, deren Ausklappliste offen ist. */
    val openGroup: Tool?,
    /** Kachel angeklickt: Gruppe wählen bzw. ihre Liste auf- oder zuklappen. */
    val onTile: (Tool) -> Unit,
    /** Art in der Liste gewählt (oder Tempolimit-Einstellung geändert). */
    val onSelect: (ToolSelection) -> Unit,
    /** Planungsleiste über dem Dock, solange etwas geplant ist. */
    val plan: PlanBarState? = null,
)

private val DockShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(11.dp)
private val DockShadow = TmColors.Ink.copy(alpha = 0.32f)

/** Werkzeug-Dock unten mittig; darüber die Ausklappliste der offenen Gruppe bzw. die Tempolimit-Schilder. */
@Composable
internal fun ToolDock(
    state: ToolDockState,
    rules: GameRules,
    modifier: Modifier = Modifier,
) {
    val tools = Tool.entries.filter { it in state.available }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.plan?.let { PlanBar(it) }
        val open = state.openGroup?.takeIf { it in tools }
        if (open != null) {
            // Über der eigenen Kachel: Abstand der Kachelmitte zur Dockmitte (per Finger zweispaltig in der Mitte)
            val shift =
                if (LocalTouch.current) 0f else (tools.indexOf(open) - (tools.size - 1) / 2f) * (TILE_WIDTH + TILE_GAP)
            ToolFlyout(open, state.selection, rules, state.idleTrailers, state.onSelect, Modifier.offset(x = shift.dp))
        } else if (state.selection.tool == Tool.SPEED_LIMIT) {
            SpeedLimitOptions(state.selection, rules, state.onSelect)
        }
        Row(
            Modifier.shadow(12.dp, DockShape, spotColor = DockShadow).background(TmColors.Ink, DockShape).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
        ) {
            for (tool in tools) {
                val kind = state.selection.chosen[tool] ?: tool.kinds.first()
                val look =
                    when {
                        state.selection.tool == tool -> TileLook.ACTIVE
                        state.openGroup == tool -> TileLook.OPEN
                        else -> TileLook.IDLE
                    }
                ToolTile(tool, kind, look, kind.price(rules, state.idleTrailers, short = true)) { state.onTile(tool) }
            }
        }
    }
}

private enum class TileLook { IDLE, OPEN, ACTIVE }

@Composable
private fun ToolTile(
    tool: Tool,
    kind: ToolKind,
    look: TileLook,
    price: String,
    onClick: () -> Unit,
) {
    val active = look == TileLook.ACTIVE
    val touch = LocalTouch.current
    val color = if (active) TmColors.Ink else TmColors.OnDark
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background =
        when {
            active -> TmColors.Active
            look == TileLook.OPEN -> TmColors.Ink3
            hovered -> TmColors.Ink2
            else -> Color.Transparent
        }
    // Alle Kacheln gleich hoch: Icon, Name (einzeilig), Preis – auch wenn es keinen gibt
    Box(
        Modifier
            .width(TILE_WIDTH.dp)
            .height(TILE_HEIGHT.dp)
            .clip(TileShape)
            .background(background)
            .hoverable(interaction)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = tool.label + ": " + kind.label },
    ) {
        if (!touch) {
            BasicText(
                tool.keyLabel,
                style = TmType.mono(10.sp, color = color.copy(alpha = KEY_ALPHA)),
                modifier = Modifier.padding(start = 6.dp, top = 3.dp),
            )
        }
        // Gruppe: kleiner Pfeil – ein Klick klappt die Liste auf
        if (tool.isGroup) {
            Icon(
                Icons.ChevronUp,
                null,
                Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 5.dp),
                tint = color.copy(alpha = CHEVRON_ALPHA),
                size = 12.dp,
            )
        }
        Column(
            Modifier.fillMaxSize().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(kind.icon, null, tint = color, size = 22.dp)
            BasicText(
                kind.short,
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
        if (hovered && look != TileLook.OPEN && !touch) ToolTooltip("${tool.label}: ${kind.label} (${tool.keyLabel})")
    }
}

private val FlyoutShape = RoundedCornerShape(14.dp)
private val RowShape = RoundedCornerShape(10.dp)

/**
 * Ausklappliste einer Gruppe: je Art Ziffer, Icon, Name, eine Zeile Erklärung und Preis; die gewählte blau
 * umrandet. Immer gleich breit – egal wie viele Arten die Gruppe hat.
 */
@Composable
private fun ToolFlyout(
    group: Tool,
    selection: ToolSelection,
    rules: GameRules,
    idleTrailers: Int,
    onSelect: (ToolSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = selection.chosen[group] ?: group.kinds.first()
    val touch = LocalTouch.current
    Column(
        modifier
            .width(if (touch) TOUCH_FLYOUT_WIDTH.dp else FLYOUT_WIDTH.dp)
            .shadow(10.dp, FlyoutShape, spotColor = DockShadow)
            .background(TmColors.Surface, FlyoutShape)
            .border(1.dp, TmColors.Line, FlyoutShape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(group.label.uppercase(), style = TmType.eyebrow)
            if (!touch) {
                BasicText(
                    tr("{key} wechselt · 1–{count} wählt", "key" to group.keyLabel, "count" to group.kinds.size),
                    style = TmType.mono(11.sp, bold = false, color = TmColors.Muted),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
        val row: @Composable (Int, ToolKind, Modifier) -> Unit = { index, kind, rowModifier ->
            FlyoutRow(
                (index + 1).takeIf { !touch },
                kind,
                kind == current,
                kind.price(rules, idleTrailers),
                rowModifier,
            ) { onSelect(selection.with(kind)) }
        }
        // Per Finger zweispaltig: so passt auch die längste Gruppe über das Dock eines Handys im Querformat
        val columns = if (touch) 2 else 1
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            group.kinds.withIndex().chunked(columns).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    line.forEach { (index, kind) -> row(index, kind, Modifier.weight(1f)) }
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FlyoutRow(
    number: Int?,
    kind: ToolKind,
    selected: Boolean,
    price: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (LocalTouch.current) TOUCH_ROW_HEIGHT.dp else ROW_HEIGHT.dp)
            .clip(RowShape)
            .background(if (selected) TmColors.PrimaryTint else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, TmColors.Primary, RowShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (number != null) {
            BasicText(
                "$number",
                style = TmType.mono(11.sp, color = TmColors.Muted).copy(textAlign = TextAlign.Center),
                modifier =
                    Modifier
                        .width(22.dp)
                        .background(TmColors.Ground, RoundedCornerShape(6.dp))
                        .padding(vertical = 3.dp),
            )
        }
        Icon(kind.icon, null, tint = TmColors.Ink, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(kind.label, style = TmType.text(14.sp, FontWeight.SemiBold))
            // Per Finger einzeilig: die Liste muss über das Dock eines Handys passen
            BasicText(
                kind.note,
                style = TmType.text(12.sp, color = TmColors.Muted),
                maxLines = if (LocalTouch.current) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(price, style = TmType.mono(12.sp, bold = false), maxLines = 1)
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
internal fun OptionChip(
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

internal const val WALKING_SPEED = 7
private const val TILE_WIDTH = 78
private const val TILE_HEIGHT = 68
private const val TILE_GAP = 2
private const val KEY_ALPHA = 0.6f
private const val CHEVRON_ALPHA = 0.7f
private const val TOOLTIP_OFFSET = 34
private const val FLYOUT_WIDTH = 340
private const val TOUCH_FLYOUT_WIDTH = 600
private const val ROW_HEIGHT = 52
private const val TOUCH_ROW_HEIGHT = 44
