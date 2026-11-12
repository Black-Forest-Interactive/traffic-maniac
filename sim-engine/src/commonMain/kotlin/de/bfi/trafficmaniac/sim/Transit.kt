package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.TransitLine
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Pünktlichkeit einer Linienrichtung heute – für den Bericht „ÖPNV“. */
class TransitLineStatus(
    val ref: String,
    val name: String,
    val color: Int,
    /** Busse dieser Linienrichtung, die gerade unterwegs sind. */
    val buses: Int,
    /** Heute angefahrene Haltestellen. */
    val stopsServed: Int,
    /** Mittlere Verspätung an den Haltestellen in Sekunden (negativ: zu früh). */
    val averageDelaySeconds: Double,
    /** Anteil der Halte mit höchstens drei Minuten Verspätung. */
    val punctualShare: Double,
)

/** ÖPNV für Karte und Bericht: Linien mit Pünktlichkeit. */
class TransitSnapshot(
    val lines: List<TransitLineStatus> = emptyList(),
)

/** Eine Haltestelle im Fahrweg: auf welchem Link (Index in der Route), wo darauf und wann laut Fahrplan. */
internal class PlannedStop(
    val stop: Int,
    val routeIndex: Int,
    val position: Float,
    /** Sekunden nach Abfahrt an der ersten Haltestelle. */
    val scheduled: Double,
)

/** Fahrweg einer Linienrichtung: Links von der ersten bis zur letzten Haltestelle und die Halte unterwegs. */
internal class LinePlan(
    val line: TransitLine,
    val route: IntArray,
    val stops: List<PlannedStop>,
)

/** Eine Fahrt eines Busses: Linie, Abfahrt und die nächste anzufahrende Haltestelle. */
internal class TransitTrip(
    val plan: Int,
    val departure: Double,
) {
    var next = 0
}

/** Heutige Bilanz einer Linienrichtung. */
private class LineTally {
    var stops = 0
    var punctual = 0
    var delaySum = 0.0

    fun record(delay: Double) {
        stops++
        delaySum += delay
        if (delay <= PUNCTUAL_SECONDS) punctual++
    }
}

/**
 * Linienbusse: Jede Linienrichtung aus der Stadtdatei fährt im Takt von der ersten zur letzten Haltestelle und hält
 * an jeder. Wer an der Haltestelle steht, blockiert seine Spur (eine Busbucht gibt es nicht) – wie ein Lieferwagen in
 * zweiter Reihe. Verspätung = Ankunft an der Haltestelle minus Fahrplan (Fahrzeit ohne Stau plus Haltezeiten).
 */
internal class Transit(
    private val city: CityMap,
    private val graph: RoadGraph,
    private val config: SimulationConfig,
    router: Router,
    private val factory: VehicleFactory,
    private val random: Random,
    private val spawn: (Vehicle) -> Unit,
) {
    private val plans: List<LinePlan> = city.transitLines.mapNotNull { LinePlanner(city, graph, router).plan(it) }

    /** Nächste Abfahrt je Linienrichtung; versetzt, damit nicht alle Busse gleichzeitig starten. */
    private val nextDeparture = DoubleArray(plans.size) { random.nextDouble() * config.busHeadwayMinutes * MINUTE }
    private val tallies = Array(plans.size) { LineTally() }
    private var day = -1

    /** Jeden Tick: neue Fahrten im Takt, um Mitternacht beginnt die Tagesbilanz neu. */
    fun update(
        time: Double,
        gameTime: GameTime,
    ) {
        if (gameTime.day != day) {
            day = gameTime.day
            tallies.indices.forEach { tallies[it] = LineTally() }
        }
        val hour = gameTime.hour
        val running = hour >= config.busFirstHour && hour < config.busLastHour
        val headway = if (hour in DAY_START until DAY_END) config.busHeadwayMinutes else config.busOffPeakHeadwayMinutes
        for (i in plans.indices) {
            if (time < nextDeparture[i]) continue
            nextDeparture[i] = time + headway * MINUTE
            if (running) depart(i, time)
        }
    }

    private fun depart(
        index: Int,
        time: Double,
    ) {
        val plan = plans[index]
        val bus = factory.createBus(plan.route, time, plan.line.color ?: DEFAULT_COLOR)
        bus.transitTrip = TransitTrip(index, time)
        spawn(bus)
    }

    /**
     * Nach jeder Bewegung: Ist der Bus an seiner nächsten Haltestelle, hält er (Ein- und Aussteigen) – `true`, dann
     * steht er. Haltestellen, an denen er schon vorbei ist (Spurwechsel, Umweg), zählen nicht.
     */
    fun atStop(
        vehicle: Vehicle,
        time: Double,
    ): Boolean {
        val trip = vehicle.transitTrip?.takeIf { vehicle.deliveringUntil <= 0 } ?: return false
        val stops = plans[trip.plan].stops
        while (trip.next < stops.size && stops[trip.next].routeIndex < vehicle.routeIndex) trip.next++
        val stop =
            stops
                .getOrNull(
                    trip.next,
                )?.takeIf { it.routeIndex == vehicle.routeIndex && vehicle.position >= it.position }
                ?: return false
        tallies[trip.plan].record(time - trip.departure - stop.scheduled)
        trip.next++
        vehicle.speed = 0.0
        vehicle.deliveringUntil = time + random.nextDouble(config.busDwellMinSeconds, config.busDwellMaxSeconds)
        return true
    }

    fun snapshot(vehicles: List<Vehicle>): TransitSnapshot {
        if (plans.isEmpty()) return TransitSnapshot()
        val buses = IntArray(plans.size)
        for (vehicle in vehicles) vehicle.transitTrip?.let { buses[it.plan]++ }
        return TransitSnapshot(
            plans.mapIndexed { i, plan ->
                val tally = tallies[i]
                TransitLineStatus(
                    plan.line.ref,
                    plan.line.name,
                    plan.line.color ?: DEFAULT_COLOR,
                    buses[i],
                    tally.stops,
                    if (tally.stops == 0) 0.0 else tally.delaySum / tally.stops,
                    if (tally.stops == 0) 1.0 else tally.punctual.toDouble() / tally.stops,
                )
            },
        )
    }

    /** Linienrichtungen, für die ein Fahrweg gefunden wurde (Tests, Statistik beim Start). */
    val lineCount: Int get() = plans.size

    private companion object {
        const val MINUTE = 60.0
        const val DAY_START = 6
        const val DAY_END = 20

        /** Linien ohne eigene Farbe: Stadtbus-Rot. */
        const val DEFAULT_COLOR = 0xFFD62828.toInt()
    }
}

/**
 * Baut aus den Haltestellen einer Linie den Fahrweg: von Haltestelle zu Haltestelle der kürzeste Weg, je Haltestelle
 * die Fahrtrichtung auf ihrem Abschnitt, die dorthin passt. Unerreichbare Haltestellen (Einbahnstraße, schlecht
 * zugeordnet) werden übersprungen; bleiben weniger als drei, fährt die Linie nicht.
 */
private class LinePlanner(
    private val city: CityMap,
    private val graph: RoadGraph,
    private val router: Router,
) {
    fun plan(line: TransitLine): LinePlan? {
        val first = city.transitStops[line.stops.first()]
        return graph.segmentLinks[first.segment]
            .map { start -> build(line, start) }
            .maxByOrNull { it.stops.size }
            ?.takeIf { it.stops.size >= MIN_STOPS }
    }

    private fun build(
        line: TransitLine,
        start: Int,
    ): LinePlan {
        val route = arrayListOf(start)
        val stops = ArrayList<PlannedStop>()
        stops += PlannedStop(line.stops.first(), 0, positionOn(start, line.stops.first()), 0.0)
        for (stopId in line.stops.drop(1)) {
            val current = route.last()
            val here = stops.last().position
            val candidates: List<Pair<IntArray, Int>> =
                graph.segmentLinks[city.transitStops[stopId].segment].asList().mapNotNull { target: Int ->
                    leg(current, here, target, positionOn(target, stopId))
                }
            val best = candidates.minByOrNull { candidate -> lengthOf(candidate.first) } ?: continue
            val (path, target) = best
            route.addAll(path.asList())
            if (path.isNotEmpty() || target != current) route += target
            stops += PlannedStop(stopId, route.size - 1, positionOn(target, stopId), 0.0)
        }
        return LinePlan(line, route.toIntArray(), schedule(route, stops))
    }

    /** Weg von [position] auf [from] zur Haltestelle auf [target]; `null`, wenn unerreichbar oder ein großer Umweg. */
    private fun leg(
        from: Int,
        position: Float,
        target: Int,
        targetPosition: Float,
    ): Pair<IntArray, Int>? {
        if (target == from) return if (targetPosition > position) IntArray(0) to target else null
        val start = graph.linkTo[from]
        val end = graph.linkFrom[target]
        val path = if (start == end) IntArray(0) else router.route(start, end, forbiddenFirstLink = -1) ?: return null
        return if (lengthOf(path) > MAX_LEG_METERS) null else path to target
    }

    private fun lengthOf(path: IntArray): Double = path.sumOf { graph.linkLength[it].toDouble() }

    /** Wo die Haltestelle auf [link] liegt: Abstand vom Anfang des Links in Fahrtrichtung. */
    private fun positionOn(
        link: Int,
        stopId: Int,
    ): Float {
        val stop = city.transitStops[stopId]
        val segment = city.roads.segments[stop.segment]
        val length = graph.linkLength[link]
        val along = if (graph.linkFrom[link] == segment.from) stop.offset else length - stop.offset
        return along.coerceIn(min(STOP_MARGIN, length / 2), max(length - STOP_MARGIN, length / 2))
    }

    /** Fahrplan: Fahrzeit ohne Stau (Tempolimit, höchstens 50, mit Reserve) plus mittlere Haltezeit je Haltestelle. */
    private fun schedule(
        route: List<Int>,
        stops: List<PlannedStop>,
    ): List<PlannedStop> {
        val elapsed = DoubleArray(route.size + 1)
        for (i in route.indices) {
            val speed = min(graph.linkSpeedLimit[route[i]].toDouble(), BUS_CRUISE) * PLAN_SPEED_SHARE
            elapsed[i + 1] = elapsed[i] + graph.linkLength[route[i]] / max(speed, MIN_SPEED)
        }
        return stops.mapIndexed { n, stop ->
            val link = route[stop.routeIndex]
            val onLink = stop.position / max(graph.linkLength[link].toDouble(), 1.0)
            val drive = elapsed[stop.routeIndex] + (elapsed[stop.routeIndex + 1] - elapsed[stop.routeIndex]) * onLink
            PlannedStop(stop.stop, stop.routeIndex, stop.position, drive + n * PLAN_DWELL)
        }
    }

    private companion object {
        const val MIN_STOPS = 3
        const val MAX_LEG_METERS = 4_000.0
        const val STOP_MARGIN = 15f
        const val BUS_CRUISE = 50 / 3.6
        const val PLAN_SPEED_SHARE = 0.7
        const val MIN_SPEED = 3.0
        const val PLAN_DWELL = 25.0
    }
}

/** Bis zu drei Minuten Verspätung gelten als pünktlich (wie bei der Bahn sechs, beim Bus strenger). */
private const val PUNCTUAL_SECONDS = 180.0
