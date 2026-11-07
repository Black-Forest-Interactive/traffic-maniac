package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource

/** Werkzeuge der Werkzeugleiste. */
enum class Tool(
    val label: String,
) {
    LOOK("Ansehen"),
    SPEED_LIMIT("Tempolimit"),
    SIGNAL("Ampel"),
    CAMERA("Blitzer"),
    LASER("Laser"),
    TRAILER("Anhänger"),
    PRIORITY("Vorfahrt"),
    BIKE_CROSSING("Radweg-Querung"),
    DETOUR("Umleitung"),
    REMOVE("Abbauen"),
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

private val BarBackground = ComicTheme.paper
private val ButtonIdle = ComicTheme.button
private val ButtonActive = ComicTheme.red
private val TextColor = ComicTheme.ink
private const val WALKING_SPEED = 7

/** Werkzeugleiste am unteren Rand: Werkzeug wählen, beim Tempolimit zusätzlich den Wert. */
@Composable
fun ToolBar(
    selection: ToolSelection,
    rules: GameRules,
    onSelect: (ToolSelection) -> Unit,
    modifier: Modifier = Modifier,
    sprites: SpriteSource = NoSprites,
    /** Blitzeranhänger im Depot (für den Kostenhinweis). */
    idleTrailers: Int = 0,
) {
    Column(
        modifier
            .padding(12.dp)
            .comicPanel()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (selection.tool == Tool.SPEED_LIMIT) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (kmh in rules.speedLimitOptions) {
                    ToolButton(if (kmh == WALKING_SPEED) "Schritt" else "$kmh", selection.speedLimit == kmh) {
                        onSelect(selection.copy(speedLimit = kmh))
                    }
                }
                ToolButton("Original", selection.speedLimit == null) { onSelect(selection.copy(speedLimit = null)) }
                ToolButton(
                    "nur nachts",
                    selection.nightOnly,
                ) { onSelect(selection.copy(nightOnly = !selection.nightOnly)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tool in Tool.entries) {
                ToolButton(
                    "${tool.label}${costHint(tool, rules, idleTrailers)}",
                    selection.tool == tool,
                    sprites,
                    toolIcon(tool, sprites),
                ) { onSelect(selection.copy(tool = tool)) }
            }
        }
    }
}

/** Icon des Werkzeugs; für den Anhänger notfalls das Blitzer-Icon. */
private fun toolIcon(
    tool: Tool,
    sprites: SpriteSource,
): String {
    val name = "tool_" + tool.name.lowercase()
    return if (tool == Tool.TRAILER && !sprites.has(name)) "tool_camera" else name
}

private fun costHint(
    tool: Tool,
    rules: GameRules,
    idleTrailers: Int,
): String =
    when (tool) {
        Tool.TRAILER -> {
            if (idleTrailers > 0) {
                " (umsetzen ${formatEuro(rules.trailerMoveCost)}, $idleTrailers frei)"
            } else {
                " (${formatEuro(rules.trailerCost)})"
            }
        }

        Tool.SPEED_LIMIT -> {
            " (${formatEuro(rules.speedLimitSignCost)})"
        }

        Tool.SIGNAL -> {
            " (${formatEuro(rules.signalCost)})"
        }

        Tool.CAMERA -> {
            " (${formatEuro(rules.cameraCost)})"
        }

        Tool.LASER -> {
            " (${formatEuro(rules.laserDailyCost)}/Tag)"
        }

        Tool.PRIORITY -> {
            " (${formatEuro(rules.priorityRoadCost)})"
        }

        Tool.BIKE_CROSSING -> {
            " (${formatEuro(rules.bikeCrossingCost)})"
        }

        else -> {
            ""
        }
    }

@Composable
private fun ToolButton(
    label: String,
    active: Boolean,
    sprites: SpriteSource = NoSprites,
    icon: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.comicButton(active).clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { SpriteIcon(sprites, it, TOOL_ICON.dp) }
        BasicText(
            label,
            style =
                TextStyle(
                    color = TextColor,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                ),
        )
    }
}

private const val TOOL_ICON = 20
