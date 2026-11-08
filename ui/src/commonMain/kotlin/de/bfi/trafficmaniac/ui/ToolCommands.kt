package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.SpeedCamera
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.hypot

/** Übersetzt einen Klick auf die Karte mit dem gewählten Werkzeug in einen Spielbefehl. */
internal fun commandFor(
    selection: ToolSelection,
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? =
    when (selection.tool) {
        Tool.LOOK -> {
            null
        }

        Tool.SIGNAL -> {
            picker.nearestNode(x, y, radius)?.let { GameCommand.BuildSignal(it) }
        }

        Tool.REMOVE -> {
            removeCommandFor(picker, frame, x, y, radius)
        }

        // Klick auf einen aufgestellten Anhänger lädt ihn auf, der nächste Klick auf eine Straße stellt ihn dort auf
        Tool.TRAILER -> {
            nearestCamera(frame, x, y, radius) { it.trailer }?.let { GameCommand.RemoveCamera(it.id) }
                ?: picker.nearestSegment(x, y, radius)?.let { GameCommand.PlaceTrailer(it) }
        }

        // Für die Umleitung kommen auch Nebenstraßen in Frage
        Tool.DETOUR -> {
            picker.nearestSegment(x, y, radius, allowPassive = { true })?.let {
                GameCommand.SetDetour(it, it !in frame?.game?.detourSegments.orEmpty())
            }
        }

        else -> {
            // Zugeschaltete Nebenstraßen (Umleitung) lassen sich wie Hauptstraßen beschildern
            val detour = frame?.game?.detourSegments.orEmpty()
            picker
                .nearestSegment(
                    x,
                    y,
                    radius,
                    allowPassive = { it in detour },
                )?.let { segmentCommand(selection, it, frame) }
        }
    }

/** Werkzeuge, die auf einen Straßenabschnitt wirken. */
private fun segmentCommand(
    selection: ToolSelection,
    segment: Int,
    frame: TrafficFrame?,
): GameCommand? =
    when (selection.tool) {
        Tool.SPEED_LIMIT -> GameCommand.SetSpeedLimit(segment, selection.speedLimit, selection.nightOnly)
        Tool.CAMERA -> GameCommand.PlaceCamera(segment)
        Tool.LASER -> GameCommand.PlaceCamera(segment, mobile = true)
        Tool.PRIORITY -> GameCommand.SetPriorityRoad(segment, segment !in frame?.game?.priorityRoads.orEmpty())
        Tool.BIKE_CROSSING -> GameCommand.SetBikeCrossing(segment, segment !in frame?.game?.bikeCrossings.orEmpty())
        else -> null
    }

/** Nächster Blitzer im Umkreis, der [accept] erfüllt. */
internal fun nearestCamera(
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
    accept: (SpeedCamera) -> Boolean,
): SpeedCamera? =
    frame
        ?.current
        ?.cameras
        ?.filter { accept(it) && hypot(it.x - x, it.y - y) <= radius }
        ?.minByOrNull { hypot(it.x - x, it.y - y) }

/** Eigene Vollsperrung oder Baustelle an [segment] abbauen – die ganze Strecke von Kreuzung zu Kreuzung. */
private fun playerWorksRemoval(
    picker: MapPicker,
    frame: TrafficFrame?,
    segment: Int,
): GameCommand? {
    val game = frame?.game ?: return null
    val stretch = picker.stretches.stretchOf(segment)
    return when (segment) {
        in game.roadClosures -> GameCommand.SetRoadClosure(stretch.filter { it in game.roadClosures }, closed = false)
        in game.roadworks -> GameCommand.SetRoadworks(stretch.filter { it in game.roadworks }, active = false)
        else -> null
    }
}

/** Abbauen: zuerst Blitzer, dann eigene Sperren und Baustellen, Tempolimit (Tag, dann Nacht), dann Ampel. */
private fun removeCommandFor(
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? {
    val signalNodes = frame?.game?.signalNodes.orEmpty()
    val camera = nearestCamera(frame, x, y, radius) { true }
    val detour = frame?.game?.detourSegments.orEmpty()
    val nearest = picker.nearestSegment(x, y, radius, allowPassive = { it in detour })
    val segment = nearest?.takeIf { it in frame?.game?.speedLimits.orEmpty() }
    val nightSegment = nearest?.takeIf { it in frame?.game?.nightLimits.orEmpty() }
    val node = picker.nearestNode(x, y, radius)?.takeIf { it in signalNodes }
    // Eigene Baustellen und Sperren zuerst – sonst würde nur das Tempo-30-Schild der Baustelle abgebaut
    val works = nearest?.let { playerWorksRemoval(picker, frame, it) }
    return when {
        camera != null -> {
            GameCommand.RemoveCamera(camera.id)
        }

        works != null -> {
            works
        }

        segment != null -> {
            GameCommand.SetSpeedLimit(segment, null)
        }

        nightSegment != null -> {
            GameCommand.SetSpeedLimit(nightSegment, null, nightOnly = true)
        }

        nearest != null && nearest in frame?.game?.priorityRoads.orEmpty() -> {
            GameCommand.SetPriorityRoad(
                nearest,
                false,
            )
        }

        nearest != null && nearest in frame?.game?.bikeCrossings.orEmpty() -> {
            GameCommand.SetBikeCrossing(
                nearest,
                false,
            )
        }

        node != null -> {
            GameCommand.RemoveSignal(node)
        }

        else -> {
            null
        }
    }
}
