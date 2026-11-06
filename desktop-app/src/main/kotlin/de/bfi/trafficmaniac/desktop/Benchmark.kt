package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.sim.DemandProfile
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationConfig
import java.io.File
import kotlin.time.TimeSource

private const val DEFAULT_MINUTES = 60
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val NANOS_PER_MILLI = 1_000_000.0
private const val LONG_RUN_MINUTES = 6 * 60

/**
 * Performance- und Stabilitätsmessung ohne Oberfläche. Gibt Uhrzeit, Fahrzeuganzahl, Kennzahlen und
 * die Rechenzeit pro Tick aus – bei kurzen Läufen alle 5 Minuten, bei langen jede Stunde.
 *
 * Aufruf: `./gradlew :desktop-app:benchmark --args="[Spitzen-Fahrten/h] [Minuten] [konstant]"`
 * Mit `konstant` läuft die Nachfrage rund um die Uhr auf Spitzenniveau (Lasttest),
 * sonst nach Tagesgang ab Montag 5 Uhr.
 */
fun main(args: Array<String>) {
    val city = CityMapCodec.decode(File("data/cities/pforzheim.city").readBytes())
    val peak = args.getOrNull(0)?.toDouble() ?: SimulationConfig().peakTripsPerHour
    val minutes = args.getOrNull(1)?.toInt() ?: DEFAULT_MINUTES
    val profile = if (args.getOrNull(2) == "konstant") DemandProfile.CONSTANT else DemandProfile.DEFAULT
    val simulation = Simulation(city, SimulationConfig(peakTripsPerHour = peak, demandProfile = profile))
    val reportEvery = if (minutes > LONG_RUN_MINUTES) MINUTES_PER_HOUR else 5
    val ticksPerReport = reportEvery * SECONDS_PER_MINUTE * simulation.config.ticksPerSecond

    val signalApproaches = simulation.signals.controlledLinks.size
    println("Pforzheim, Spitze $peak Fahrten/h, ${simulation.graph.linkCount} Links, $signalApproaches Ampel-Zufahrten")
    println("Zeit             | Fahrzeuge | wartend | angekommen | aufgegeben | Ø km/h | Ø Fahrt min | ms/Tick Ø | max")
    repeat(minutes / reportEvery) {
        var total = 0L
        var worst = 0L
        repeat(ticksPerReport) {
            val mark = TimeSource.Monotonic.markNow()
            simulation.step()
            val nanos = mark.elapsedNow().inWholeNanoseconds
            total += nanos
            worst = maxOf(worst, nanos)
        }
        val snapshot = simulation.snapshot()
        val stats = snapshot.stats
        val time = snapshot.gameTime
        println(
            "%-10s %s | %9d | %7d | %10d | %10d | %6.1f | %11.1f | %11.3f | %13.3f".format(
                time.weekday.germanName,
                time.clockText,
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
    for (report in simulation.dayReports) {
        println(
            "Tag ${report.day + 1} (${report.weekday.germanName}): ${report.tripsStarted} Fahrten, " +
                "Spitze ${report.peakVehicles} Fahrzeuge um ${report.peakHour} Uhr, aufgegeben ${report.stuckRemovals}",
        )
    }
}
