package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.CityMap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Mikroskopische Verkehrssimulation: Fahrzeuge folgen dem IDM, halten an Ampeln, gewähren an
 * ungeregelten Kreuzungen der höherrangigen Straße Vorfahrt und rechnen ihre Route regelmäßig neu.
 *
 * Ein Tick läuft in festen Schritten ab: Fahrten erzeugen → einfahren → Beschleunigung aller Fahrzeuge
 * berechnen → bewegen → Knoten überqueren → Spuren wechseln.
 */
class Simulation(
    city: CityMap,
    val config: SimulationConfig = SimulationConfig(),
) {
    val graph = RoadGraph(city.roads)
    private val random = Random(config.seed)
    val signals = SignalSystem(graph)

    /** Tempo, Überschreitungen und Blitzer. */
    val speedControl = SpeedControl(graph, config, random)
    val clock = SimulationClock(config.ticksPerSecond)

    private val router = Router(graph)
    private val trips = TripGenerator(city.zones, graph, random)
    private val factory = VehicleFactory(config, random)
    private val lanes = LaneQueues(graph)
    private val junctions = JunctionRules(graph, signals, lanes)
    private val laneChanger = LaneChanger(graph, lanes)
    private val dt = clock.tickDurationSeconds

    /** Alle Fahrzeuge im Netz, nach ID sortiert (neue werden hinten angehängt). */
    private val vehicles = ArrayList<Vehicle>()
    private val pending = ArrayDeque<Vehicle>()
    private var tripAccumulator = 0.0
    private var completedTrips = 0
    private var stuckRemovals = 0
    private var totalTripSeconds = 0.0
    private val dayStatistics = DayStatistics()

    /** Beendete Fahrten seit dem letzten Abholen (für die Zufriedenheit der Bürger). */
    val finishedTrips = TripLog()
    private val reports = ArrayList<DayReport>()

    /** Simulierte Sekunden seit dem Start. */
    val time: Double get() = clock.elapsedSeconds

    /** Wochentag und Uhrzeit. */
    val gameTime: GameTime get() = GameTime.at(time, config.startSecondOfDay)

    /** Bilanzen aller abgeschlossenen Tage, ältester zuerst. */
    val dayReports: List<DayReport> get() = reports

    fun step() {
        generateTrips()
        enterPendingVehicles()
        computeAccelerations()
        move()
        crossNodes()
        laneChanger.changeLanes(clock.tick)
        if (clock.tick % QUEUE_UPDATE_TICKS == 0L) updateQueueTravelTimes()
        if (vehicles.any { !it.active }) vehicles.removeAll { !it.active }
        val before = gameTime
        dayStatistics.sampleVehicles(vehicles.size, before)
        clock.advance()
        if (gameTime.day != before.day) reports += dayStatistics.finish(before.day, before.weekday)
    }

    // --- Fahrten erzeugen und einfahren -------------------------------------------------------------

    private fun generateTrips() {
        if (trips.isEmpty) return
        val now = gameTime
        tripAccumulator += config.peakTripsPerHour * config.demandProfile.factor(now) / SECONDS_PER_HOUR * dt
        while (tripAccumulator >= 1) {
            tripAccumulator--
            val trip = trips.next(now)
            val route = router.route(trip.originNode, trip.destinationNode)
            if (route != null && pending.size < config.maxPendingVehicles) {
                pending.addLast(factory.create(route, trip, time, router.referenceTime(route)))
            }
        }
    }

    /** Jeder wartende Wagen versucht einmal pro Tick einzufahren; wer keinen Platz findet, wartet weiter. */
    private fun enterPendingVehicles() {
        repeat(pending.size) {
            val vehicle = pending.removeFirst()
            val link = vehicle.link
            val lane = lanes.bestEntryLane(link, vehicle.lane)
            if (lanes.freeSpaceAtStart(link, lane) > vehicle.type.length + vehicle.driver.minimumGap + ENTRY_MARGIN) {
                vehicle.lane = lane
                vehicle.position = vehicle.type.length.toDouble()
                vehicle.speed = min(ENTRY_SPEED, graph.linkSpeedLimit[link].toDouble())
                vehicle.linkEnterTime = time
                speedControl.roll(vehicle, link)
                lanes[link][lane].addLast(vehicle)
                vehicles += vehicle
                dayStatistics.tripStarted(gameTime)
            } else {
                pending.addLast(vehicle)
            }
        }
    }

    // --- Fahrzeugfolge --------------------------------------------------------------------------------

    private fun computeAccelerations() {
        for (link in 0 until graph.linkCount) {
            for (queue in lanes[link]) {
                for (i in queue.indices) {
                    val vehicle = queue[i]
                    val desired = speedControl.desiredSpeed(vehicle, link)
                    vehicle.acceleration =
                        if (i > 0) {
                            val leader = queue[i - 1]
                            vehicle.idm(
                                desired,
                                leader.position - leader.type.length - vehicle.position,
                                vehicle.speed - leader.speed,
                            )
                        } else {
                            frontAcceleration(vehicle, link, desired)
                        }
                }
            }
        }
    }

    /** Das vorderste Fahrzeug einer Spur reagiert auf Haltelinie, Vorfahrt oder den Letzten im nächsten Link. */
    private fun frontAcceleration(
        vehicle: Vehicle,
        link: Int,
        desired: Double,
    ): Double {
        val next = vehicle.nextLink
        val remaining = graph.linkLength[link] - vehicle.position
        val leader = if (next >= 0) lanes.rearmost(next, vehicle.lane) else null
        return when {
            next < 0 -> vehicle.idm(desired, Double.POSITIVE_INFINITY, 0.0)
            junctions.mustStopAtEnd(vehicle, link, remaining, time) -> vehicle.idm(desired, remaining, vehicle.speed)
            leader == null -> vehicle.idm(desired, Double.POSITIVE_INFINITY, 0.0)
            else -> vehicle.idm(desired, remaining + leader.position - leader.type.length, vehicle.speed - leader.speed)
        }
    }

    // --- Bewegung -------------------------------------------------------------------------------------

    private fun move() {
        for (link in 0 until graph.linkCount) {
            for (queue in lanes[link]) moveQueue(queue, link)
        }
    }

    private fun moveQueue(
        queue: ArrayDeque<Vehicle>,
        link: Int,
    ) {
        for (i in queue.indices) {
            val vehicle = queue[i]
            // Vordermann auf derselben Spur oder – für den Ersten – der Letzte im nächsten Link
            val next = vehicle.nextLink
            val leader =
                if (i > 0) {
                    queue[i - 1]
                } else if (next >= 0) {
                    lanes.rearmost(next, vehicle.lane)
                } else {
                    null
                }
            val offset = if (i > 0) 0.0 else graph.linkLength[link].toDouble()
            val oldPosition = vehicle.position
            moveVehicle(vehicle, leader, offset)
            speedControl.measure(vehicle, link, oldPosition)
        }
        if (queue.any { !it.active }) {
            val stuck = queue.count { !it.active }
            stuckRemovals += stuck
            repeat(stuck) { dayStatistics.vehicleStuck() }
            queue.removeAll { !it.active }
        }
    }

    /** [leaderOffset]: Abstand vom eigenen Linkanfang zum Linkanfang des Vordermanns. */
    private fun moveVehicle(
        vehicle: Vehicle,
        leader: Vehicle?,
        leaderOffset: Double,
    ) {
        val newSpeed = max(0.0, vehicle.speed + vehicle.acceleration * dt)
        vehicle.position += (vehicle.speed + newSpeed) / 2 * dt
        vehicle.speed = newSpeed
        // Numerische Sicherheit: Niemand schiebt sich in den Vordermann
        if (leader != null) {
            val limit = leaderOffset + leader.position - leader.type.length - STANDSTILL_GAP
            if (vehicle.position > limit) {
                vehicle.position = max(limit, 0.0)
                vehicle.speed = min(vehicle.speed, leader.speed)
            }
        }
        vehicle.waitingTime = if (vehicle.speed < STANDSTILL_SPEED) vehicle.waitingTime + dt else 0.0
        // Wer zu lange steht, gibt auf und verlässt das Netz – sonst könnte ein Knoten alles dauerhaft blockieren
        if (vehicle.waitingTime > config.stuckTimeoutSeconds) vehicle.active = false
    }

    // --- Knoten überqueren ----------------------------------------------------------------------------

    private fun crossNodes() {
        for (link in 0 until graph.linkCount) {
            val length = graph.linkLength[link]
            for (queue in lanes[link]) {
                while (queue.firstOrNull()?.let { it.position >= length && tryLeaveLink(it, link, length) } == true) {
                    queue.removeFirst()
                }
            }
        }
    }

    /** Bringt [vehicle] in den nächsten Link oder ans Ziel. `false`, wenn hinter dem Knoten kein Platz ist. */
    private fun tryLeaveLink(
        vehicle: Vehicle,
        link: Int,
        length: Float,
    ): Boolean {
        val next = vehicle.nextLink
        if (next < 0) {
            arrive(vehicle)
            return true
        }
        val overflow = vehicle.position - length
        val lane = lanes.bestEntryLane(next, vehicle.lane)
        if (lanes.freeSpaceAtStart(next, lane) < overflow + STANDSTILL_GAP) {
            vehicle.position = length.toDouble()
            vehicle.speed = 0.0
            return false
        }
        router.observe(link, time - vehicle.linkEnterTime)
        vehicle.routeIndex++
        vehicle.lane = lane
        vehicle.position = overflow
        vehicle.linkEnterTime = time
        speedControl.roll(vehicle, next)
        lanes[next][lane].addLast(vehicle)
        if (time - vehicle.lastRerouteTime > config.rerouteIntervalSeconds) reroute(vehicle)
        return true
    }

    private fun arrive(vehicle: Vehicle) {
        vehicle.active = false
        completedTrips++
        totalTripSeconds += time - vehicle.departureTime
        finishedTrips.add(TripResult(vehicle.homeZone, time - vehicle.departureTime, vehicle.referenceSeconds))
        dayStatistics.tripCompleted(time - vehicle.departureTime)
    }

    // --- Navi ---------------------------------------------------------------------------------------------

    /**
     * Lange Warteschlangen fließen schon vor der ersten Ausfahrt in die Fahrzeitschätzung ein;
     * auf Links ohne Stau kehrt die Schätzung langsam zur freien Fahrt zurück.
     */
    private fun updateQueueTravelTimes() {
        for (link in 0 until graph.linkCount) {
            val wait = lanes.longestFrontWait(link)
            if (wait > 0) router.raiseTo(link, router.freeFlowTime(link) + wait) else router.relax(link)
        }
    }

    private fun reroute(vehicle: Vehicle) {
        vehicle.lastRerouteTime = time
        val link = vehicle.link
        val path =
            router.route(
                graph.linkTo[link],
                vehicle.destinationNode,
                forbiddenFirstLink = graph.linkReverse[link],
            )
        if (path != null) {
            vehicle.route = intArrayOf(link) + path
            vehicle.routeIndex = 0
        }
    }

    // --- Darstellung ------------------------------------------------------------------------------------

    fun snapshot(): SimulationSnapshot {
        val count = vehicles.size
        val positions = Array(LOCATE_SIZE) { FloatArray(count) }
        val point = FloatArray(LOCATE_SIZE)
        vehicles.forEachIndexed { i, vehicle ->
            graph.locate(vehicle.link, vehicle.position.toFloat(), vehicle.lane, point)
            for (k in 0 until LOCATE_SIZE) positions[k][i] = point[k]
        }
        return SimulationSnapshot(
            tick = clock.tick,
            time = time,
            gameTime = gameTime,
            ids = IntArray(count) { vehicles[it].id },
            x = positions[LOCATE_X],
            y = positions[LOCATE_Y],
            dirX = positions[LOCATE_DIR_X],
            dirY = positions[LOCATE_DIR_Y],
            lengths = FloatArray(count) { vehicles[it].type.length },
            widths = FloatArray(count) { vehicles[it].type.width },
            colors = IntArray(count) { vehicles[it].color },
            signalMarkers = signals.markers,
            signalStates = Array(signals.markers.links.size) { signals.state(signals.markers.links[it], time) },
            cameras = speedControl.cameras.toList(),
            stats = stats(),
        )
    }

    private fun stats() =
        SimulationStats(
            activeVehicles = vehicles.size,
            pendingVehicles = pending.size,
            completedTrips = completedTrips,
            stuckRemovals = stuckRemovals,
            meanSpeedKmh = if (vehicles.isEmpty()) 0.0 else vehicles.sumOf { it.speed } / vehicles.size * KMH_PER_MS,
            meanTripMinutes = if (completedTrips > 0) totalTripSeconds / completedTrips / SECONDS_PER_MINUTE else 0.0,
        )

    private companion object {
        const val SECONDS_PER_HOUR = 3600.0
        const val SECONDS_PER_MINUTE = 60.0
        const val ENTRY_SPEED = 8.0
        const val ENTRY_MARGIN = 2.0
        const val STANDSTILL_GAP = 0.5
        const val STANDSTILL_SPEED = 0.1
        const val QUEUE_UPDATE_TICKS = 100L
    }
}
