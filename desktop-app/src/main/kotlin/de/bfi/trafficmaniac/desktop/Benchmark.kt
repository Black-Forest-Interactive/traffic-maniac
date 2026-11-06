package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationConfig
import java.io.File
import kotlin.time.TimeSource

private const val DEFAULT_MINUTES = 60
private const val REPORT_EVERY_MINUTES = 5
private const val SECONDS_PER_MINUTE = 60
private const val NANOS_PER_MILLI = 1_000_000.0

/**
 * Performance-Messung ohne Oberfläche: simuliert (standardmäßig) eine Stunde und gibt alle fünf Minuten
 * Fahrzeuganzahl, Kennzahlen und die Rechenzeit pro Tick aus.
 *
 * Aufruf: `./gradlew :desktop-app:benchmark --args="[Fahrten pro Stunde] [Minuten]"`
 */
fun main(args: Array<String>) {
    val city = CityMapCodec.decode(File("data/cities/pforzheim.city").readBytes())
    val tripsPerHour = args.getOrNull(0)?.toDouble() ?: SimulationConfig().tripsPerHour
    val minutes = args.getOrNull(1)?.toInt() ?: DEFAULT_MINUTES
    val simulation = Simulation(city, SimulationConfig(tripsPerHour = tripsPerHour))
    val ticksPerReport = REPORT_EVERY_MINUTES * SECONDS_PER_MINUTE * simulation.config.ticksPerSecond

    val signalApproaches = simulation.signals.controlledLinks.size
    println(
        "Pforzheim, $tripsPerHour Fahrten/h, ${simulation.graph.linkCount} Links, $signalApproaches Ampel-Zufahrten",
    )
    println(
        "Minute | Fahrzeuge | wartend | angekommen | aufgegeben | Ø km/h | Ø Fahrt min | ms/Tick (Ø) | ms/Tick (max)",
    )
    repeat(minutes / REPORT_EVERY_MINUTES) { report ->
        var total = 0L
        var worst = 0L
        repeat(ticksPerReport) {
            val mark = TimeSource.Monotonic.markNow()
            simulation.step()
            val nanos = mark.elapsedNow().inWholeNanoseconds
            total += nanos
            worst = maxOf(worst, nanos)
        }
        val stats = simulation.snapshot().stats
        println(
            "%6d | %9d | %7d | %10d | %10d | %6.1f | %11.1f | %11.3f | %13.3f".format(
                (report + 1) * REPORT_EVERY_MINUTES,
                stats.activeVehicles,
                stats.pendingVehicles,
                stats.completedTrips,
                stats.stuckRemovals,
                stats.meanSpeedKmh,
                stats.meanTripMinutes,
                total / ticksPerReport / NANOS_PER_MILLI,
                worst / NANOS_PER_MILLI,
            ),
        )
    }
}
