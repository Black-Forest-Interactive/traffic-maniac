package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.sim.DemandProfile
import de.bfi.trafficmaniac.sim.EnvironmentSnapshot
import de.bfi.trafficmaniac.sim.Pollutant
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
 * Aufruf: `./gradlew :desktop-app:benchmark --args="[Spitzen-Fahrten/h] [Minuten] [konstant] [schlüssel=wert …]"`
 * Weitere Argumente der Form `schlüssel=wert` überschreiben Werte aus `balancing/simulation.json`,
 * z. B. `softwareFailuresPerHour=0` (zum Eingrenzen von Effekten).
 * Mit `konstant` läuft die Nachfrage rund um die Uhr auf Spitzenniveau (Lasttest),
 * sonst nach Tagesgang ab Montag 5 Uhr.
 */
fun main(args: Array<String>) {
    val city = CityMapCodec.decode(File("data/cities/pforzheim.city").readBytes())
    val peak = args.getOrNull(0)?.toDouble() ?: SimulationConfig().peakTripsPerHour
    val minutes = args.getOrNull(1)?.toInt() ?: DEFAULT_MINUTES
    val profile = if (args.getOrNull(2) == "konstant") DemandProfile.CONSTANT else DemandProfile.DEFAULT
    val overrides = args.filter { "=" in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val config = Balancing.simulationConfig(overrides).copy(peakTripsPerHour = peak, demandProfile = profile)
    if (overrides.isNotEmpty()) println("Abweichend: $overrides")
    val simulation = Simulation(city, config)
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
    printEnvironmentAtHomes(city, simulation)
    val snapshot = simulation.snapshot()
    val waiting = snapshot.chargingStations.sumOf { it.waiting }
    println(
        "Offene Vorfälle: ${snapshot.incidents.groupingBy {
            it.kind.label
        }.eachCount()} · an Ladesäulen wartend: $waiting",
    )
    for (report in simulation.dayReports) {
        println(
            "Tag ${report.day + 1} (${report.weekday.germanName}): ${report.tripsStarted} Fahrten, " +
                "Spitze ${report.peakVehicles} Fahrzeuge um ${report.peakHour} Uhr, " +
                "aufgegeben ${report.stuckRemovals}, " +
                "Vorfälle ${report.incidents.mapKeys { it.key.label }}, Rettungswagen Ø " +
                "${"%.1f".format(report.ambulanceMinutes.average())} min",
        )
    }
}

/** Lärm und Luft an den Wohngebieten (Mittel über 250 m) – Grundlage für die Schwellen der Bedürfnisse. */
private fun printEnvironmentAtHomes(
    city: CityMap,
    simulation: Simulation,
) {
    val environment = simulation.environment.snapshot
    val homes = city.zones.filter { it.kind == ZoneKind.RESIDENTIAL }
    val noise =
        homes.map {
            EnvironmentSnapshot.energyToDb(environment.neighborhood(Pollutant.NOISE, it.x, it.y, NEIGHBORHOOD))
        }
    val air = homes.map { home -> AIR.sumOf { environment.neighborhood(it, home.x, home.y, NEIGHBORHOOD).toDouble() } }
    println("Wohngebiete: Lärm dB (P10/P50/P90/max): ${percentiles(noise)}")
    println("Wohngebiete: Feinstaub+NOx g/Zelle (P10/P50/P90/max): ${percentiles(air)}")
    val totals = environment.totals.entries.joinToString { "${it.key.label} ${"%.0f".format(it.value)} g" }
    println("Gesamt: ${"%.0f".format(environment.totalFuelLiters)} l Kraftstoff, $totals")
}

private fun percentiles(values: List<Number>): String {
    val sorted = values.map { it.toDouble() }.sorted()
    return PERCENTILES.joinToString(" / ") { p -> "%.2f".format(sorted[(sorted.size - 1) * p / PERCENT]) }
}

private const val NEIGHBORHOOD = 2
private const val PERCENT = 100
private val PERCENTILES = listOf(10, 50, 90, 100)
private val AIR = listOf(Pollutant.NOX, Pollutant.PM_EXHAUST, Pollutant.PM_TIRES, Pollutant.PM_BRAKES)
