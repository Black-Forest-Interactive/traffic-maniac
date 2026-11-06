package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.SignalMarkers
import de.bfi.trafficmaniac.sim.SignalState
import kotlin.math.max

private const val MIN_VEHICLE_PIXELS = 2.5f

/**
 * Zeichnet alle Fahrzeuge als Striche von Heck zu Front (in Weltkoordinaten).
 * Positionen werden zwischen den beiden Snapshots des [TrafficFrame] interpoliert.
 */
internal fun DrawScope.drawVehicles(
    frame: TrafficFrame,
    scale: Float,
) {
    val previous = frame.previous
    val current = frame.current
    val t = frame.interpolation()
    var j = 0
    for (i in 0 until current.vehicleCount) {
        val id = current.ids[i]
        // Beide Snapshots sind nach ID sortiert: passendes Fahrzeug im vorherigen Snapshot suchen
        while (j < previous.vehicleCount && previous.ids[j] < id) j++
        var x = current.x[i]
        var y = current.y[i]
        if (j < previous.vehicleCount && previous.ids[j] == id) {
            x = previous.x[j] + (x - previous.x[j]) * t
            y = previous.y[j] + (y - previous.y[j]) * t
        }
        val length = current.lengths[i]
        drawLine(
            color = Color(current.colors[i]),
            start = Offset(x - current.dirX[i] * length, y - current.dirY[i] * length),
            end = Offset(x, y),
            strokeWidth = max(current.widths[i], MIN_VEHICLE_PIXELS / scale),
            cap = StrokeCap.Butt,
        )
    }
}

/** Ampeln als farbige Punkte an den Haltelinien. */
internal fun DrawScope.drawSignals(
    markers: SignalMarkers,
    states: Array<SignalState>,
    scale: Float,
) {
    val width = max(2 * MapStyle.SIGNAL_RADIUS_METERS, MapStyle.MIN_SIGNAL_PIXELS / scale)
    for (state in listOf(SignalState.GREEN, SignalState.YELLOW, SignalState.RED)) {
        val points = states.indices.filter { states[it] == state }.map { Offset(markers.x[it], markers.y[it]) }
        drawPoints(points, PointMode.Points, signalColor(state), strokeWidth = width, cap = StrokeCap.Round)
    }
}

private fun signalColor(state: SignalState): Color =
    when (state) {
        SignalState.GREEN -> MapStyle.signalGreen
        SignalState.YELLOW -> MapStyle.signalYellow
        else -> MapStyle.trafficSignal
    }
