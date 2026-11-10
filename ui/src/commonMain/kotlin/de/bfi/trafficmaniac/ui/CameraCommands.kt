package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.hypot

/**
 * Klick mit einer Überwachungs-Art: Blitzer, Laser, Anzeigetafel und Kontrolle auf den nächsten Abschnitt;
 * Anhänger wie gehabt (Klick auf einen aufgestellten lädt ihn auf); Rotlichtblitzer an die nächste Ampelzufahrt.
 */
internal fun cameraCommand(
    kind: ToolKind,
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? {
    val redLight = kind == ToolKind.RED_LIGHT || kind == ToolKind.RED_LIGHT_SPEED
    // Anhänger: ein Klick auf einen aufgestellten lädt ihn auf
    val pickup = if (kind == ToolKind.TRAILER) nearestCamera(frame, x, y, radius) { it.trailer } else null
    val detour = frame?.game?.detourSegments.orEmpty()
    return when {
        redLight -> {
            approachNear(frame, x, y, radius)?.let {
                GameCommand.PlaceRedLightCamera(it, withSpeed = kind == ToolKind.RED_LIGHT_SPEED)
            }
        }

        pickup != null -> {
            GameCommand.RemoveCamera(pickup.id)
        }

        else -> {
            picker.nearestSegment(x, y, radius, allowPassive = { it in detour })?.let { placeCommand(kind, it) }
        }
    }
}

private fun placeCommand(
    kind: ToolKind,
    segment: Int,
): GameCommand =
    when (kind) {
        ToolKind.LASER -> GameCommand.PlaceCamera(segment, mobile = true)
        ToolKind.DISPLAY -> GameCommand.PlaceDisplay(segment)
        ToolKind.POLICE_CHECK -> GameCommand.PlacePoliceCheck(segment)
        ToolKind.TRAILER -> GameCommand.PlaceTrailer(segment)
        else -> GameCommand.PlaceCamera(segment)
    }

/** Ampelzufahrt, deren Haltelinie dem Klick am nächsten liegt (Ampeln stehen oft etwas vor der Kreuzung). */
private fun approachNear(
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): Int? {
    val markers = frame?.current?.signalMarkers ?: return null
    return markers.links.indices
        .filter { hypot(markers.x[it] - x, markers.y[it] - y) <= radius * APPROACH_PICK_FACTOR }
        .minByOrNull { hypot(markers.x[it] - x, markers.y[it] - y) }
        ?.let { markers.links[it] }
}

private const val APPROACH_PICK_FACTOR = 3f
