package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import de.bfi.trafficmaniac.ui.components.TmIcon

/**
 * Gruppen im Werkzeug-Dock, je mit Taste. Die Arten einer Gruppe ([ToolKind]) wählt man in der Ausklappliste über
 * der Kachel; die Kachel zeigt die zuletzt gewählte.
 */
enum class Tool(
    private val labelText: String,
    /** Taste, die die Gruppe wählt (erneut gedrückt: die nächste Art), und ihre Beschriftung. */
    val key: Key,
    internal val keyLabel: String,
    /** Icon der Kachel – fest je Gruppe, egal welche Art gewählt ist. */
    internal val icon: TmIcon,
) {
    LOOK(translatable("Ansehen"), Key.Escape, "Esc", Icons.Eye),
    SPEED_LIMIT(translatable("Tempo"), Key.Q, "Q", Icons.Gauge),
    SIGNAL(translatable("Ampel"), Key.W, "W", Icons.TrafficLight),
    CAMERA(translatable("Kontrolle"), Key.E, "E", Icons.Shield),
    ROUTE(translatable("Umleitung"), Key.A, "A", Icons.Detour),
    ROADWORKS(translatable("Sperrung"), Key.S, "S", Icons.Ban),
    REMOVE(translatable("Abbauen"), Key.X, "X", Icons.Eraser),
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

    /** Enter ohne offenen Tagesabschluss: den Bauplan bauen. */
    var buildRequest by mutableStateOf(false)

    /** Strg+Z: die zuletzt geplante Maßnahme zurücknehmen. */
    var undoRequest by mutableStateOf(false)
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
private val TileShape = RoundedCornerShape(12.dp)
private val PopoverShape = RoundedCornerShape(14.dp)
private val CardShape = RoundedCornerShape(10.dp)
private val DockShadow = TmColors.Ink.copy(alpha = 0.38f)
private val PopoverShadow = TmColors.Ink.copy(alpha = 0.25f)

/** Abbauen: Icon und Name hellrot, solange es nicht gewählt ist. */
private val RemoveTint = Color(0xFFFF9AA2)

/**
 * Bauleiste unten mittig: Ansehen | Tempo · Ampel · Kontrolle · Umleitung · Sperrung | Abbauen, je Kachel Icon und
 * Name. Darüber das Popover des Werkzeugs mit Pfeil auf seine Kachel: die Tempo-Schilder bzw. die Arten einer Gruppe
 * als Karten. Per Finger steht die Planungsleiste mit im Block, am Desktop hat der Bauplan einen eigenen Platz.
 */
@Composable
internal fun ToolDock(
    state: ToolDockState,
    rules: GameRules,
    modifier: Modifier = Modifier,
) {
    val tools = Tool.entries.filter { it in state.available }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        state.plan?.let {
            PlanBar(it)
            Spacer(Modifier.height(10.dp))
        }
        val open = state.openGroup?.takeIf { it in tools }
        val popover = open ?: state.selection.tool.takeIf { it == Tool.SPEED_LIMIT && it in tools }
        if (popover != null) {
            ToolPopover(popover, tools, state, rules)
            Spacer(Modifier.height(POPOVER_GAP.dp))
        }
        Row(
            Modifier
                .shadow(
                    12.dp,
                    DockShape,
                    spotColor = DockShadow,
                ).background(TmColors.Ink, DockShape)
                .padding(DOCK_PADDING.dp),
            horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tool in tools) {
                if (separatorBefore(tool, tools)) DockSeparator()
                val kind = state.selection.chosen[tool] ?: tool.kinds.first()
                val look =
                    when {
                        state.selection.tool == tool -> TileLook.ACTIVE
                        state.openGroup == tool -> TileLook.OPEN
                        else -> TileLook.IDLE
                    }
                ToolTile(tool, kind, look, kind.price(rules, state.idleTrailers)) { state.onTile(tool) }
            }
        }
    }
}

/** Trennstrich nach „Ansehen“ und vor „Abbauen“. */
private fun separatorBefore(
    tool: Tool,
    tools: List<Tool>,
): Boolean =
    tools.firstOrNull() != tool && (tool == Tool.REMOVE || tools.getOrNull(tools.indexOf(tool) - 1) == Tool.LOOK)

@Composable
private fun DockSeparator() {
    Box(
        Modifier
            .padding(horizontal = SEPARATOR_MARGIN.dp)
            .width(1.dp)
            .height(40.dp)
            .background(TmColors.LineDark),
    )
}

/** Mitte der Kachel [tool] gemessen von der Mitte der Leiste (für den Pfeil des Popovers). */
private fun tileOffset(
    tool: Tool,
    tools: List<Tool>,
): Float {
    var x = DOCK_PADDING.toFloat()
    var center = 0f
    for ((i, t) in tools.withIndex()) {
        if (i > 0) x += TILE_GAP
        if (separatorBefore(t, tools)) x += SEPARATOR_WIDTH + TILE_GAP
        if (t == tool) center = x + TILE_WIDTH / 2f
        x += TILE_WIDTH
    }
    val width = x + DOCK_PADDING
    return center - width / 2f
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
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color =
        when {
            active -> TmColors.Ink
            tool == Tool.REMOVE -> RemoveTint
            else -> TmColors.OnDark
        }
    val background =
        when {
            active -> TmColors.Active
            look == TileLook.OPEN || hovered -> TmColors.Ink3
            else -> Color.Transparent
        }
    val tooltip =
        listOfNotNull(
            if (tool.isGroup) "${tool.label}: ${kind.label}" else tool.label,
            price.ifEmpty { null },
        ).joinToString(" · ") + " (${tool.keyLabel})"
    Box(
        Modifier
            .width(TILE_WIDTH.dp)
            .height(TILE_HEIGHT.dp)
            .clip(TileShape)
            .background(background)
            .hoverable(interaction)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = tooltip },
    ) {
        // Punkt oben rechts: das Werkzeug hat Varianten
        if (tool.isGroup) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 7.dp)
                    .size(5.dp)
                    .background(color.copy(alpha = DOT_ALPHA), CircleShape),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(tool.icon, null, tint = color, size = 22.dp)
            BasicText(
                tool.label,
                style = TmType.text(11.sp, FontWeight.SemiBold, color).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hovered && look != TileLook.OPEN && !LocalTouch.current) ToolTooltip(tooltip)
    }
}

/**
 * Popover über der Leiste (680 dp), der Pfeil zeigt auf die Kachel von [tool]: Titel und Bedienhinweis, darunter
 * die Tempo-Schilder bzw. die Arten der Gruppe als Karten (Icon, Name, Preis, ein Satz Wirkung, Taste).
 */
@Composable
private fun ToolPopover(
    tool: Tool,
    tools: List<Tool>,
    state: ToolDockState,
    rules: GameRules,
) {
    val touch = LocalTouch.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .width(POPOVER_WIDTH.dp)
                .shadow(10.dp, PopoverShape, spotColor = PopoverShadow)
                .background(TmColors.Surface, PopoverShape)
                .border(1.dp, TmColors.Line, PopoverShape)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText(
                    if (tool ==
                        Tool.SPEED_LIMIT
                    ) {
                        tr("Tempolimit")
                    } else {
                        tool.label
                    },
                    style = TmType.text(14.sp, FontWeight.Bold),
                )
                val hint =
                    when {
                        tool == Tool.SPEED_LIMIT -> {
                            tr("Anfang und Ende anklicken · Esc beendet")
                        }

                        touch -> {
                            tr("Art antippen")
                        }

                        else -> {
                            tr(
                                "{key} wechselt · 1–{count} wählt",
                                "key" to tool.keyLabel,
                                "count" to minOf(tool.kinds.size, MAX_DIGIT),
                            )
                        }
                    }
                BasicText(hint, style = TmType.text(11.sp, color = TmColors.Muted))
            }
            if (tool == Tool.SPEED_LIMIT) {
                SpeedLimitOptions(state.selection, rules, state.onSelect)
            } else {
                VariantCards(tool, state, rules, COLUMNS)
            }
        }
        PopoverArrow(Modifier.offset(x = tileOffset(tool, tools).dp, y = (-1).dp))
    }
}

/** Spitze unter dem Popover: weißes Dreieck, die beiden schrägen Kanten mit dem Rahmen des Popovers. */
@Composable
private fun PopoverArrow(modifier: Modifier) {
    Canvas(modifier.size(ARROW_WIDTH.dp, ARROW_HEIGHT.dp)) {
        val path =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2, size.height)
                lineTo(size.width, 0f)
                close()
            }
        drawPath(path, TmColors.Surface)
        val stroke = 1.dp.toPx()
        drawLine(TmColors.Line, Offset(0f, 0f), Offset(size.width / 2, size.height), stroke)
        drawLine(TmColors.Line, Offset(size.width / 2, size.height), Offset(size.width, 0f), stroke)
    }
}

/** Die Arten einer Gruppe als Karten im Raster; die gewählte blau umrandet. */
@Composable
private fun VariantCards(
    group: Tool,
    state: ToolDockState,
    rules: GameRules,
    columns: Int,
) {
    val selection = state.selection
    val current = selection.chosen[group] ?: group.kinds.first()
    val touch = LocalTouch.current
    Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP.dp)) {
        group.kinds.withIndex().chunked(columns).forEach { line ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp)) {
                line.forEach { (index, kind) ->
                    VariantCard(
                        kind,
                        (index + 1).takeIf { !touch && it <= MAX_DIGIT },
                        kind == current,
                        kind.price(rules, state.idleTrailers),
                        Modifier.weight(1f).fillMaxHeight(),
                    ) { state.onSelect(selection.with(kind)) }
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun VariantCard(
    kind: ToolKind,
    number: Int?,
    selected: Boolean,
    price: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val touch = LocalTouch.current
    Column(
        modifier
            .clip(CardShape)
            .background(if (selected) TmColors.PrimaryTint else TmColors.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) TmColors.Primary else TmColors.Line, CardShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = kind.label + ": " + kind.note }
            .padding(horizontal = 10.dp, vertical = if (touch) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(kind.icon, null, tint = TmColors.Ink, size = 20.dp)
            number?.let { BasicText("$it", style = TmType.mono(10.sp, color = TmColors.Muted)) }
        }
        BasicText(
            kind.short,
            style = TmType.text(13.sp, FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(price.ifEmpty { tr("kostenlos") }, style = TmType.mono(12.sp), maxLines = 1)
        if (!touch) {
            BasicText(
                kind.note,
                style = TmType.text(11.sp, color = TmColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Name, Preis und Taste über der Kachel, solange die Maus darauf steht. */
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (kmh in rules.speedLimitOptions) {
            SpeedSign(if (kmh == WALKING_SPEED) tr("Schritt") else "$kmh", selection.speedLimit == kmh) {
                onSelect(selection.copy(speedLimit = kmh))
            }
        }
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(30.dp)
                .background(TmColors.Track),
        )
        PopoverOption(tr("Original"), selection.speedLimit == null) { onSelect(selection.copy(speedLimit = null)) }
        PopoverOption(
            tr("nur nachts"),
            selection.nightOnly,
        ) { onSelect(selection.copy(nightOnly = !selection.nightOnly)) }
    }
}

@Composable
private fun SpeedSign(
    text: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val long = text.length > 2
    Box(
        Modifier
            .height(SIGN_SIZE.dp)
            .widthIn(min = SIGN_SIZE.dp)
            .clip(CircleShape)
            .background(TmColors.Surface)
            .border(if (chosen) 4.dp else 2.dp, if (chosen) TmColors.Danger else TmColors.Track, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = if (long) 10.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TmType.text(if (long) 11.sp else 14.sp, FontWeight.Bold))
    }
}

/** Schalter im Popover (40 dp): aktiv Ampelgelb. */
@Composable
private fun PopoverOption(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(40.dp)
            .clip(CardShape)
            .background(if (active) TmColors.Active else TmColors.Surface)
            .border(1.dp, if (active) TmColors.Active else TmColors.Line, CardShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = TmType.text(12.sp, FontWeight.SemiBold)) }
}

internal const val WALKING_SPEED = 7
private const val TILE_WIDTH = 76
private const val TILE_HEIGHT = 60
private const val TILE_GAP = 4
private const val DOCK_PADDING = 6
private const val SEPARATOR_MARGIN = 4
private const val SEPARATOR_WIDTH = 1 + 2 * SEPARATOR_MARGIN
private const val DOT_ALPHA = 0.55f
private const val TOOLTIP_OFFSET = 34
private const val POPOVER_WIDTH = 680
private const val POPOVER_GAP = 2
private const val ARROW_WIDTH = 22
private const val ARROW_HEIGHT = 10
private const val CARD_GAP = 8

/** Fünf Spalten: auch die größte Gruppe (Kontrolle) braucht höchstens zwei Reihen. */
private const val COLUMNS = 5
private const val MAX_DIGIT = 9
private const val SIGN_SIZE = 42
