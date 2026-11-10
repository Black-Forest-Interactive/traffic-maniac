package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.map.PlanMarker
import kotlin.math.hypot

/** Ampelknoten näher als das gehören zur selben Anlage (Schätzung für die Kosten einer Welle). */
private const val SAME_PLANT_METERS = 80f

/** Tempolimit von Anfang bis Ende: ein Schild je Strecke von Kreuzung zu Kreuzung. */
internal fun speedLimitAction(
    selection: ToolSelection,
    context: PlanContext,
    path: List<Int>,
): PlannedAction {
    val stretches = context.picker.stretches
    val firstOfStretch = path.map { stretches.stretchOf(it).min() }.distinct()
    val commands = firstOfStretch.map { GameCommand.SetSpeedLimit(it, selection.speedLimit, selection.nightOnly) }
    val (mx, my) = context.picker.midpoint(path[path.size / 2])
    val night = if (selection.nightOnly) " " + tr("nachts") else ""
    val label = (selection.speedLimit?.let { limitLabel(it) } ?: tr("Original")) + night
    val cost = if (selection.speedLimit == null) 0L else context.rules.speedLimitSignCost * commands.size
    return PlannedAction(
        commands,
        "limit:${firstOfStretch.sorted().joinToString(",")}:${selection.nightOnly}",
        PlanMarker(
            mx,
            my,
            label,
            removal = selection.speedLimit == null,
            segments =
                firstOfStretch.flatMap {
                    stretches.stretchOf(it)
                },
        ),
        cost,
    )
}

/** Grüne Welle von Anfang bis Ende; bezahlt wird je Ampelanlage auf dem Weg (hier geschätzt). */
internal fun greenWaveAction(
    kmh: Int,
    context: PlanContext,
    path: List<Int>,
): PlannedAction {
    val (mx, my) = context.picker.midpoint(path[path.size / 2])
    val cost = context.rules.signalProgramCost * estimatedPlants(context, path)
    return PlannedAction(
        listOf(GameCommand.SetGreenWave(path, kmh)),
        "wave:${path.joinToString(",")}",
        PlanMarker(mx, my, tr("Welle {kmh}", "kmh" to kmh), segments = path),
        cost,
    )
}

/** Ampelanlagen entlang des Wegs: Ampelknoten auf dem Weg, nahe beieinander liegende zählen als eine Anlage. */
internal fun estimatedPlants(
    context: PlanContext,
    path: List<Int>,
): Int {
    val signalNodes =
        context.frame
            ?.game
            ?.signalNodes
            .orEmpty()
    val roads = context.city.roads
    val onPath =
        path.flatMap { listOf(roads.segments[it].from, roads.segments[it].to) }.distinct().filter {
            it in
                signalNodes
        }
    val clusters = ArrayList<Pair<Float, Float>>()
    for (node in onPath) {
        val n = roads.nodes[node]
        if (clusters.none { (x, y) -> hypot(x - n.x, y - n.y) < SAME_PLANT_METERS }) clusters += n.x to n.y
    }
    return clusters.size
}

internal fun roadworksCommand(
    closure: Boolean,
    segments: List<Int>,
    on: Boolean,
): GameCommand = if (closure) GameCommand.SetRoadClosure(segments, on) else GameCommand.SetRoadworks(segments, on)

/** Maßnahme, die ganze Strecken betrifft: Abschnitte, an oder ab, Kennung, Name und Preis je Strecke. */
private class StretchMeasure(
    val segments: List<Int>,
    val on: Boolean,
    val kind: String,
    val name: String,
    val unitCost: Long,
)

private fun stretchMeasure(
    command: GameCommand,
    rules: GameRules,
): StretchMeasure? =
    when (command) {
        is GameCommand.SetRoadworks -> {
            StretchMeasure(command.segments, command.active, "works", tr("Baustelle"), rules.roadworksSetupCost)
        }

        is GameCommand.SetRoadClosure -> {
            StretchMeasure(command.segments, command.closed, "closure", tr("Sperre"), rules.closureCost)
        }

        is GameCommand.SetBikeStreet -> {
            StretchMeasure(command.segments, command.on, "bike-street", tr("Fahrradstraße"), rules.bikeStreetCost)
        }

        else -> {
            null
        }
    }

/** Baustelle, Vollsperrung oder Fahrradstraße (bzw. ihr Abbau): bezahlt wird je Strecke von Kreuzung zu Kreuzung. */
internal fun roadworksAction(
    command: GameCommand,
    context: PlanContext,
): PlannedAction? {
    val measure = stretchMeasure(command, context.rules)?.takeIf { it.segments.isNotEmpty() } ?: return null
    val segments = measure.segments
    val stretches =
        segments
            .map {
                context.picker.stretches
                    .stretchOf(it)
                    .min()
            }.distinct()
            .size
    val (x, y) = context.picker.midpoint(segments[segments.size / 2])
    return PlannedAction(
        listOf(command),
        "${measure.kind}:${segments.sorted().joinToString(",")}",
        PlanMarker(x, y, toggleLabel(measure.name, measure.on), removal = !measure.on, segments = segments),
        if (measure.on) measure.unitCost * stretches else 0,
    )
}

internal fun signalAction(
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

internal fun Plan.cameraAction(
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

        is GameCommand.PlacePoliceCheck -> {
            val position = context.picker.midpoint(command.segment)
            single(command, "camera:${command.segment}", position, tr("Kontrolle"), context.rules.policeCheckCost)
        }

        is GameCommand.PlaceDisplay -> {
            val position = context.picker.midpoint(command.segment)
            single(command, "camera:${command.segment}", position, tr("Anzeigetafel"), context.rules.displayCost)
        }

        is GameCommand.PlaceRedLightCamera -> {
            redLightAction(command, context)
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

/** Rotlichtblitzer an der Haltelinie der Zufahrt – dort, wo die Simulation die Ampel zeigt. */
private fun redLightAction(
    command: GameCommand.PlaceRedLightCamera,
    context: PlanContext,
): PlannedAction? {
    val markers = context.frame?.current?.signalMarkers ?: return null
    val i = markers.links.indexOf(command.link).takeIf { it >= 0 } ?: return null
    val label = if (command.withSpeed) tr("Rotlicht + Tempo") else tr("Rotlicht")
    val rules = context.rules
    val cost = if (command.withSpeed) rules.redLightSpeedCameraCost else rules.redLightCameraCost
    return single(command, "red:${command.link}", markers.x[i] to markers.y[i], label, cost)
}

internal fun segmentAction(
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

internal class SegmentTarget(
    val segment: Int,
    val label: String,
    val cost: Long,
    val removal: Boolean,
)

internal fun toggleLabel(
    name: String,
    on: Boolean,
): String = if (on) name else tr("{name} weg", "name" to name)

internal fun single(
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
