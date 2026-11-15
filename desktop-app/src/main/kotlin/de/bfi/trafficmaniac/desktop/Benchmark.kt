package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.Difficulty
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
 * Mit `bildkosten` (z. B. `--args="0 150 bildkosten"`) läuft das ganze Spiel [Minuten] lang und misst danach, was ein
 * veröffentlichtes Bild des `GameRunner` kostet: Simulations-Schnappschuss und Spielstand.
 * Mit `spielstart` (`--args="0 0 spielstart"`) misst er je Szenario, wie lange der Start dauert: Spiel anlegen und
 * Vorlauf.
 * Mit `tempo30:<Straße>` (Leerzeichen als `_`, z. B. `--args="0 300 tempo30:Zerrennerstraße"`) spielt er denselben
 * Morgen zweimal – ohne und mit Tempo 30 auf allen Abschnitten der Straße – und vergleicht Durchfahrten,
 * Ausweichverkehr, Fahrzeiten, Lärm und Abgase (Prüfauftrag B2 aus v0.8).
 */

fun main(args: Array<String>) {
    val city = benchmarkCity(args)
    when {
        "bildkosten" in args -> {
            measureFrameCost(city, args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_MINUTES)
        }

        "spielstart" in args -> {
            measureGameStart()
        }

        args.any { it.startsWith("tempo") && ":" in it } -> {
            val (mode, street) = args.first { it.startsWith("tempo") && ":" in it }.split(':', limit = 2)
            compareSpeedLimit(city, street.replace('_', ' '), mode.removePrefix("tempo").toInt(), args.minutes())
        }

        else -> {
            measureSimulation(city, args)
        }
    }
}

private fun measureSimulation(
    city: CityMap,
    args: Array<String>,
) {
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

/** Eine andere Stadtdatei lässt sich als zusätzliches Argument "….city" angeben, sonst Pforzheim. */
private fun benchmarkCity(args: Array<String>): CityMap =
    args.firstOrNull { it.endsWith(".city") }?.let { CityMapCodec.decode(File(it).readBytes()) }
        ?: CityPacks.discover().first { it.id == "pforzheim" }.map

private const val FRAME_SAMPLES = 200
private const val FRAME_WARMUP_SAMPLES = 50

/** Was der `GameRunner` je veröffentlichtem Bild rechnet – gemessen nach [minutes] Spielzeit ab 5 Uhr. */
private fun measureFrameCost(
    city: CityMap,
    minutes: Int,
) {
    val game = CityGame(city, Balancing.simulationConfig(), Balancing.gameRules())
    repeat(minutes * SECONDS_PER_MINUTE * game.simulation.config.ticksPerSecond) { game.step() }
    val vehicles = game.simulation.snapshot().vehicleCount

    fun millis(block: () -> Unit): Double {
        repeat(FRAME_WARMUP_SAMPLES) { block() }
        val mark = TimeSource.Monotonic.markNow()
        repeat(FRAME_SAMPLES) { block() }
        return mark.elapsedNow().inWholeNanoseconds / NANOS_PER_MILLI / FRAME_SAMPLES
    }
    val snapshot = millis { game.simulation.snapshot() }
    val state = millis { game.state() }
    val step = millis { game.step() }
    println(
        "%s, %d Fahrzeuge: Schnappschuss %.3f ms, Spielstand %.3f ms, Schritt %.3f ms".format(
            game.simulation.gameTime.clockText,
            vehicles,
            snapshot,
            state,
            step,
        ),
    )
}

/** Spielstart je Szenario: Spiel anlegen und Vorlauf – auf dem Handy etwa zehnmal so lang. */
private fun measureGameStart() {
    val pack = CityPacks.discover().first { it.id == "pforzheim" }
    for (scenario in Balancing.scenarios()) {
        val (config, rules) =
            scenario.apply(
                pack.simulationConfig(Balancing.simulationConfig()),
                pack.gameRules(Balancing.gameRules()),
                Difficulty.NORMAL,
            )
        val created = TimeSource.Monotonic.markNow()
        val game = CityGame(pack.map, config, rules)
        val createMillis = created.elapsedNow().inWholeMilliseconds
        val warm = TimeSource.Monotonic.markNow()
        repeat(scenario.warmupMinutes * SECONDS_PER_MINUTE * config.ticksPerSecond) { game.step() }
        println(
            "%-20s anlegen %5d ms, Vorlauf %3d min: %6d ms (%d Fahrzeuge danach)".format(
                scenario.id,
                createMillis,
                scenario.warmupMinutes,
                warm.elapsedNow().inWholeMilliseconds,
                game.simulation.snapshot().vehicleCount,
            ),
        )
    }
}

private fun Array<String>.minutes(): Int = getOrNull(1)?.toIntOrNull() ?: DEFAULT_MINUTES

/** Ergebnis eines Laufs für den Vergleich. */
private class LimitRun(
    val passages: IntArray,
    val meanSeconds: DoubleArray,
    val completedTrips: Int,
    val meanTripMinutes: Double,
    val noiseDb: Double,
    val noxGrams: Double,
    val totalNox: Double,
    val fuelLiters: Double,
)

private const val ENVIRONMENT_SAMPLE_MINUTES = 5
private const val TOP_DETOURS = 8

/** Spielt [minutes] ab 5 Uhr zweimal mit gleichem Zufall – ohne und mit Tempo [kmh] auf [street]. */
private fun compareSpeedLimit(
    city: CityMap,
    street: String,
    kmh: Int,
    minutes: Int,
) {
    val segments =
        city.roads.segments
            .filter { !it.passive && it.name == street }
            .map { it.id }
    require(segments.isNotEmpty()) { "Keine simulierte Straße namens '$street'" }
    val pool =
        java.util.concurrent.Executors
            .newFixedThreadPool(2)
    val runs = listOf(null, kmh).map { limit -> pool.submit<LimitRun> { limitRun(city, segments, limit, minutes) } }
    val (before, after) = runs.map { it.get() }
    pool.shutdown()
    val names = city.roads.segments.associate { it.id to (it.name ?: "(ohne Namen) #${it.id}") }

    fun streetPassages(run: LimitRun) = segments.sumOf { run.passages[it] }

    fun streetSeconds(run: LimitRun) = segments.sumOf { run.meanSeconds[it] }
    val original = segments.map { city.roads.segments[it].maxSpeedKmh }.groupingBy { it }.eachCount()
    println(
        "$street: ${segments.size} Abschnitte, Tempo $kmh statt $original (km/h → Abschnitte), " +
            "$minutes min ab 5 Uhr",
    )
    println("                          ohne      mit")
    println("Durchfahrten Straße  %8d %8d".format(streetPassages(before), streetPassages(after)))
    println("Fahrzeit Straße (s)  %8.0f %8.0f".format(streetSeconds(before), streetSeconds(after)))
    println("Fahrten angekommen   %8d %8d".format(before.completedTrips, after.completedTrips))
    println("Ø Fahrzeit (min)     %8.2f %8.2f".format(before.meanTripMinutes, after.meanTripMinutes))
    println("Lärm an der Straße   %8.1f %8.1f dB (Mittel der Stichproben)".format(before.noiseDb, after.noiseDb))
    println("NOx an der Straße    %8.2f %8.2f g/Zelle".format(before.noxGrams, after.noxGrams))
    println("NOx ganze Stadt      %8.0f %8.0f g".format(before.totalNox, after.totalNox))
    println("Kraftstoff Stadt     %8.0f %8.0f l".format(before.fuelLiters, after.fuelLiters))
    val gains =
        names.keys
            .filter { it !in segments }
            .groupBy { names.getValue(it) }
            .mapValues { (_, ids) -> ids.sumOf { after.passages[it] - before.passages[it] } }
            .entries
            .sortedByDescending { it.value }
    println("Ausweichverkehr (mehr Durchfahrten, Summe der Abschnitte):")
    gains.take(TOP_DETOURS).forEach { println("  %+6d  %s".format(it.value, it.key)) }
    println("Entlastet:")
    gains.takeLast(TOP_DETOURS / 2).reversed().forEach { println("  %+6d  %s".format(it.value, it.key)) }
}

private fun limitRun(
    city: CityMap,
    segments: List<Int>,
    kmh: Int?,
    minutes: Int,
): LimitRun {
    val game = CityGame(city, Balancing.simulationConfig(), Balancing.gameRules())
    if (kmh != null) {
        segments.forEach {
            game.submit(
                de.bfi.trafficmaniac.game.GameCommand
                    .SetSpeedLimit(it, kmh),
            )
        }
    }
    val picker =
        segments.map { id ->
            city.roads.segments[id].geometry.let {
                it.x(it.pointCount / 2) to
                    it.y(it.pointCount / 2)
            }
        }
    var noiseEnergy = 0.0
    var nox = 0.0
    var samples = 0
    val ticksPerSample = ENVIRONMENT_SAMPLE_MINUTES * SECONDS_PER_MINUTE * game.simulation.config.ticksPerSecond
    repeat(minutes / ENVIRONMENT_SAMPLE_MINUTES) {
        repeat(ticksPerSample) { game.step() }
        val environment = game.simulation.environment.snapshot
        for ((x, y) in picker) {
            noiseEnergy += environment.value(Pollutant.NOISE, x, y)
            nox += environment.value(Pollutant.NOX, x, y)
        }
        samples += picker.size
    }
    val traffic = game.simulation.segmentTraffic
    val snapshot = game.simulation.snapshot()
    val environment = snapshot.environment
    return LimitRun(
        passages = traffic.passagesSnapshot(),
        meanSeconds = DoubleArray(city.roads.segments.size) { traffic.meanSeconds(it) },
        completedTrips = snapshot.stats.completedTrips,
        meanTripMinutes = snapshot.stats.meanTripMinutes,
        noiseDb = EnvironmentSnapshot.energyToDb((noiseEnergy / samples).toFloat()).toDouble(),
        noxGrams = nox / samples,
        totalNox = environment.totals[Pollutant.NOX] ?: 0.0,
        fuelLiters = environment.totalFuelLiters,
    )
}
