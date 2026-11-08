package de.bfi.trafficmaniac.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.PlanMarker
import de.bfi.trafficmaniac.ui.map.StreetRoute
import kotlin.math.hypot

/**
 * Eine geplante Maßnahme: noch nicht gebaut, noch nicht bezahlt. [key] bezeichnet das Ziel (Strecke, Knoten,
 * Blitzer …) – eine neue Planung für dasselbe Ziel ersetzt die alte, dieselbe noch einmal nimmt sie heraus.
 */
internal class PlannedAction(
    val commands: List<GameCommand>,
    val key: String,
    val marker: PlanMarker,
    val cost: Long,
)

/**
 * Planungsmodus: Was man mit den Werkzeugen auf die Karte setzt, wird erst gesammelt und als Vorschau gezeigt;
 * gebaut und bezahlt wird alles zusammen mit „Bauen“. So kostet ein Verklicken nichts.
 */
internal class Plan {
    var actions by mutableStateOf(emptyList<PlannedAction>())
        private set

    /** Beim Tempolimit: Anfang der Strecke, das nächste Anklicken setzt das Ende. */
    var routeStart by mutableStateOf<Int?>(null)

    val total: Long get() = actions.sumOf { it.cost }

    val isActive: Boolean get() = actions.isNotEmpty() || routeStart != null

    fun add(action: PlannedAction) {
        val existing = actions.firstOrNull { it.key == action.key }
        actions =
            if (existing != null && existing.commands == action.commands) {
                actions - existing
            } else {
                actions.filter { it.key != action.key } + action
            }
    }

    /** Streicht die geplante Maßnahme an dieser Stelle; `true`, wenn es eine gab. */
    fun removeNear(
        x: Float,
        y: Float,
        radius: Float,
    ): Boolean {
        val hit =
            actions
                .filter { hypot(it.marker.x - x, it.marker.y - y) <= radius }
                .minByOrNull { hypot(it.marker.x - x, it.marker.y - y) } ?: return false
        actions = actions - hit
        return true
    }

    fun undo() {
        if (routeStart != null) routeStart = null else actions = actions.dropLast(1)
    }

    fun clear() {
        actions = emptyList()
        routeStart = null
    }

    /** Alle Befehle in der Reihenfolge der Planung; die Planung ist danach leer. */
    fun take(): List<GameCommand> = actions.flatMap { it.commands }.also { clear() }
}

/** Was die Planungsleiste zeigt und auslöst. */
internal class PlanBarState(
    val count: Int,
    val total: Long,
    val balance: Long,
    /** Beim Tempolimit nach dem ersten Klick: Hinweis, dass jetzt das Ende kommt. */
    val waitingForEnd: Boolean,
    val onUndo: () -> Unit,
    val onDiscard: () -> Unit,
    val onBuild: () -> Unit,
)

/** Was die Planung zum Beschriften und Bepreisen braucht. */
internal class PlanContext(
    val city: CityMap,
    val picker: MapPicker,
    val route: StreetRoute,
    val rules: GameRules,
    val frame: TrafficFrame?,
)

/** Ein Klick auf die Karte mit dem gewählten Werkzeug – wird zur geplanten Maßnahme. */
internal fun Plan.click(
    selection: ToolSelection,
    context: PlanContext,
    x: Float,
    y: Float,
    radius: Float,
) {
    when (selection.tool) {
        Tool.LOOK -> {
            Unit
        }

        // Abbauen auf eine geplante Maßnahme streicht sie aus der Planung
        Tool.REMOVE -> {
            if (!removeNear(
                    x,
                    y,
                    radius,
                )
            ) {
                plan(context, commandFor(selection, context.picker, context.frame, x, y, radius))
            }
        }

        Tool.SPEED_LIMIT -> {
            speedLimitClick(selection, context, x, y, radius)
        }

        else -> {
            plan(context, commandFor(selection, context.picker, context.frame, x, y, radius))
        }
    }
}

/** Tempolimit: erster Klick = Anfang, zweiter = Ende; dieselbe Strecke zweimal = nur bis zur nächsten Kreuzung. */
private fun Plan.speedLimitClick(
    selection: ToolSelection,
    context: PlanContext,
    x: Float,
    y: Float,
    radius: Float,
) {
    val detour =
        context.frame
            ?.game
            ?.detourSegments
            .orEmpty()
    val segment = context.picker.nearestSegment(x, y, radius, allowPassive = { it in detour }) ?: return
    val start = routeStart
    if (start == null) {
        routeStart = segment
        return
    }
    routeStart = null
    val stretches = context.picker.stretches
    val path =
        if (segment in stretches.stretchOf(start)) {
            listOf(segment)
        } else {
            context.route.between(start, segment, allow = { it in detour }) ?: return
        }
    // Ein Schild je Strecke von Kreuzung zu Kreuzung
    val firstOfStretch = path.map { stretches.stretchOf(it).min() }.distinct()
    val commands = firstOfStretch.map { GameCommand.SetSpeedLimit(it, selection.speedLimit, selection.nightOnly) }
    val allSegments = firstOfStretch.flatMap { stretches.stretchOf(it) }
    val middle = path[path.size / 2]
    val (mx, my) = context.picker.midpoint(middle)
    val night = if (selection.nightOnly) " " + tr("nachts") else ""
    val label = (selection.speedLimit?.let { limitLabel(it) } ?: tr("Original")) + night
    val cost = if (selection.speedLimit == null) 0L else context.rules.speedLimitSignCost * commands.size
    add(
        PlannedAction(
            commands,
            "limit:${firstOfStretch.sorted().joinToString(",")}:${selection.nightOnly}",
            PlanMarker(mx, my, label, removal = selection.speedLimit == null, segments = allSegments),
            cost,
        ),
    )
}

private fun limitLabel(kmh: Int): String = if (kmh == WALKING_SPEED) tr("Schritt") else "$kmh"

/** Einzelner Befehl als geplante Maßnahme: Ziel, Vorschau und Preis. */
private fun Plan.plan(
    context: PlanContext,
    command: GameCommand?,
) {
    val action =
        when (command) {
            null -> {
                null
            }

            is GameCommand.BuildSignal, is GameCommand.RemoveSignal -> {
                signalAction(command, context)
            }

            is GameCommand.PlaceCamera, is GameCommand.PlaceTrailer, is GameCommand.RemoveCamera -> {
                cameraAction(command, context)
            }

            else -> {
                segmentAction(command, context)
            }
        }
    action?.let(::add)
}

private fun signalAction(
    command: GameCommand,
    context: PlanContext,
): PlannedAction? {
    val build = command is GameCommand.BuildSignal
    val node =
        (command as? GameCommand.BuildSignal)?.node ?: (command as? GameCommand.RemoveSignal)?.node ?: return null
    val position =
        context.city.roads.nodes[node]
            .let { it.x to it.y }
    return if (build) {
        single(command, "signal:$node", position, tr("Ampel"), context.rules.signalCost)
    } else {
        single(command, "signal:$node", position, tr("Ampel weg"), context.rules.signalRemovalCost, removal = true)
    }
}

private fun Plan.cameraAction(
    command: GameCommand,
    context: PlanContext,
): PlannedAction? =
    when (command) {
        is GameCommand.PlaceCamera -> {
            val label = if (command.mobile) tr("Laser") else tr("Blitzer")
            val cost = if (command.mobile) 0 else context.rules.cameraCost
            single(command, "camera:${command.segment}", context.picker.midpoint(command.segment), label, cost)
        }

        is GameCommand.PlaceTrailer -> {
            val position = context.picker.midpoint(command.segment)
            single(command, "trailer:${command.segment}", position, tr("Anhänger"), trailerCost(context))
        }

        is GameCommand.RemoveCamera -> {
            context.frame?.current?.cameras?.firstOrNull { it.id == command.cameraId }?.let { camera ->
                val key = if (camera.trailer) "pickup:${camera.id}" else "remove:${camera.id}"
                val label = if (camera.trailer) tr("Anhänger aufladen") else tr("Blitzer weg")
                single(command, key, camera.x to camera.y, label, 0, removal = true)
            }
        }

        else -> {
            null
        }
    }

private fun segmentAction(
    command: GameCommand,
    context: PlanContext,
): PlannedAction? {
    val rules = context.rules
    val target =
        when (command) {
            is GameCommand.SetSpeedLimit -> {
                SegmentTarget(command.segment, tr("Limit weg"), 0L, true)
            }

            is GameCommand.SetPriorityRoad -> {
                SegmentTarget(
                    command.segment,
                    toggleLabel(tr("Vorfahrt"), command.priority),
                    if (command.priority) rules.priorityRoadCost else 0L,
                    !command.priority,
                )
            }

            is GameCommand.SetBikeCrossing -> {
                SegmentTarget(
                    command.segment,
                    toggleLabel(tr("Radweg"), command.crossing),
                    if (command.crossing) rules.bikeCrossingCost else 0L,
                    !command.crossing,
                )
            }

            is GameCommand.SetDetour -> {
                SegmentTarget(command.segment, toggleLabel(tr("Umleitung"), command.enabled), 0L, !command.enabled)
            }

            else -> {
                return null
            }
        }
    val kind = command::class.simpleName
    val segment = target.segment
    val segments = if (command is GameCommand.SetDetour) listOf(segment) else emptyList()
    val position = context.picker.midpoint(segment)
    return single(command, "$kind:$segment", position, target.label, target.cost, segments, target.removal)
}

private class SegmentTarget(
    val segment: Int,
    val label: String,
    val cost: Long,
    val removal: Boolean,
)

private fun toggleLabel(
    name: String,
    on: Boolean,
): String = if (on) name else tr("{name} weg", "name" to name)

private fun single(
    command: GameCommand,
    key: String,
    position: Pair<Float, Float>,
    label: String,
    cost: Long,
    segments: List<Int> = emptyList(),
    removal: Boolean = false,
): PlannedAction =
    PlannedAction(
        listOf(command),
        key,
        PlanMarker(position.first, position.second, label, removal = removal, segments = segments),
        cost,
    )

/** Steht (nach den geplanten Auflade-Aktionen) ein Anhänger frei, kostet nur das Umsetzen. */
private fun Plan.trailerCost(context: PlanContext): Long {
    val idle = context.frame?.game?.idleTrailers ?: 0
    val pickups = actions.count { it.key.startsWith("pickup:") }
    val placed = actions.count { it.key.startsWith("trailer:") }
    return if (idle + pickups - placed > 0) context.rules.trailerMoveCost else context.rules.trailerCost
}
