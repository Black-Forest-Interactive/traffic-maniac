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
import kotlin.math.roundToInt

/** Ampeln stehen oft etwas vor der Kreuzung – deshalb ein größerer Fangradius. */
private const val SIGNAL_PICK_FACTOR = 3f
private const val PERCENT = 100

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

/** Ein Klick auf die Karte mit der gewählten Art – wird zur geplanten Maßnahme. */
internal fun Plan.click(
    selection: ToolSelection,
    context: PlanContext,
    x: Float,
    y: Float,
    radius: Float,
) {
    val kind = selection.kind
    val share = kind.signalShare
    val wave = kind.waveKmh
    when {
        kind == ToolKind.LOOK -> {
            Unit
        }

        // Abbauen auf eine geplante Maßnahme streicht sie aus der Planung
        kind == ToolKind.REMOVE && removeNear(x, y, radius) -> {
            Unit
        }

        kind == ToolKind.SPEED_LIMIT -> {
            routeClick(context, x, y, radius) { speedLimitAction(selection, context, it) }
        }

        kind in STRETCH_KINDS -> {
            // Baustelle, Sperrung, Fahrradstraße: Anfang und Ende anklicken, ganze Strecken
            routeClick(context, x, y, radius) { path ->
                val segments = path.flatMap { context.picker.stretches.stretchOf(it) }.distinct()
                roadworksAction(stretchCommand(kind, segments), context)
            }
        }

        wave != null -> {
            routeClick(context, x, y, radius, directed = true) { greenWaveAction(wave, context, it) }
        }

        share != null -> {
            splitClick(share, context, x, y, radius)
        }

        else -> {
            plan(context, commandFor(selection, context.picker, context.frame, x, y, radius))
        }
    }
}

/** Arten, die ganze Strecken von Anfang bis Ende betreffen. */
private val STRETCH_KINDS = setOf(ToolKind.ROADWORKS, ToolKind.CLOSURE, ToolKind.BIKE_STREET)

private fun stretchCommand(
    kind: ToolKind,
    segments: List<Int>,
): GameCommand =
    when (kind) {
        ToolKind.CLOSURE -> GameCommand.SetRoadClosure(segments, closed = true)
        ToolKind.BIKE_STREET -> GameCommand.SetBikeStreet(segments, on = true)
        else -> GameCommand.SetRoadworks(segments, active = true)
    }

/**
 * Zwei Klicks entlang einer Straße: erster Klick = Anfang, zweiter = Ende; dieselbe Strecke zweimal = nur bis zur
 * nächsten Kreuzung. [plan] macht aus dem Weg (Abschnitte vom Anfang zum Ende) die geplante Maßnahme.
 */
private fun Plan.routeClick(
    context: PlanContext,
    x: Float,
    y: Float,
    radius: Float,
    directed: Boolean = false,
    plan: (List<Int>) -> PlannedAction?,
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
    val path =
        if (segment in context.picker.stretches.stretchOf(start)) {
            context.picker.stretches.stretchOf(segment)
        } else {
            context.route.between(start, segment, allow = { it in detour }, directed = directed) ?: return
        }
    plan(path)?.let(::add)
}

/** Grünzeit einer Ampelanlage aufteilen: die nächste Ampel im Umkreis. */
private fun Plan.splitClick(
    share: Double,
    context: PlanContext,
    x: Float,
    y: Float,
    radius: Float,
) {
    val roads = context.city.roads
    val node =
        context.frame
            ?.game
            ?.signalNodes
            .orEmpty()
            .filter { hypot(roads.nodes[it].x - x, roads.nodes[it].y - y) <= radius * SIGNAL_PICK_FACTOR }
            .minByOrNull { hypot(roads.nodes[it].x - x, roads.nodes[it].y - y) } ?: return
    val main = (share * PERCENT).roundToInt()
    add(
        PlannedAction(
            listOf(GameCommand.SetSignalProgram(node, share)),
            "program:$node",
            PlanMarker(roads.nodes[node].x, roads.nodes[node].y, "$main/${PERCENT - main}"),
            context.rules.signalProgramCost,
        ),
    )
}

internal fun limitLabel(kmh: Int): String = if (kmh == WALKING_SPEED) tr("Schritt") else "$kmh"

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

            is GameCommand.PlaceCamera, is GameCommand.PlaceTrailer, is GameCommand.RemoveCamera,
            is GameCommand.PlaceDisplay, is GameCommand.PlaceRedLightCamera, is GameCommand.PlacePoliceCheck,
            -> {
                cameraAction(command, context)
            }

            is GameCommand.SetRoadworks, is GameCommand.SetRoadClosure -> {
                roadworksAction(command, context)
            }

            else -> {
                segmentAction(command, context)
            }
        }
    action?.let(::add)
}
