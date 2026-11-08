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

    /** Abgase, Feinstaub und Lärm im 50-m-Raster. */
    val environment = Environment(city.bounds)

    /** Tempo, Überschreitungen und Blitzer. */
    val speedControl = SpeedControl(graph, config, random)
    val clock = SimulationClock(config.ticksPerSecond)

    private val router = Router(graph)
    private val trips = TripGenerator(city.zones, graph, random)
    private val factory = VehicleFactory(config, random)
    private val lanes = LaneQueues(graph)

    /** Zustand von Straßen, Ampeln und Brücken; Baustellen, Sperrungen und Umleitungen. */
    val infrastructure = Infrastructure(graph, signals, lanes, config, random)
    private val junctions = JunctionRules(graph, signals, lanes)
    private val laneChanger = LaneChanger(graph, lanes)
    private val behavior = DriverBehavior(speedControl, clock.tickDurationSeconds)
    private val dt = clock.tickDurationSeconds
    private val dayStatistics = DayStatistics()

    /** Pannen, Ölspuren, Unfälle und Einsatzfahrzeuge. */
    private val incidents =
        Incidents(
            graph,
            config,
            random,
            router,
            factory,
            lanes,
            city.facilities,
            { pending.addLast(it) },
            dayStatistics,
            infrastructure,
        )

    /** Demos, Feste, Märkte, Rad-Korsos und Schwertransporte – ausgelöst vom Spiel. */
    val events = CityEvents(graph, infrastructure.closures, router, factory, lanes) { pending.addLast(it) }

    private val deliveries = Deliveries(graph, config, random, dayStatistics) { parking.secondRowStop(it) }

    /** Parkplätze, Straßenrand, Parksuchverkehr und Falschparker. */
    val parking = Parking(config, city.facilities, city.zones, graph, router, random, dayStatistics)

    /** Elektroautos und Ladesäulen. */
    private val charging =
        Charging(
            config,
            city.facilities,
            router,
            graph,
            lanes,
            { incidents },
            { pending.addLast(it) },
            ::arrive,
            dayStatistics,
        )

    /** Alle Fahrzeuge im Netz, nach ID sortiert (neue werden hinten angehängt). */
    private val vehicles = ArrayList<Vehicle>()
    private val pending = ArrayDeque<Vehicle>()
    private var tripAccumulator = 0.0
    private var completedTrips = 0
    private var stuckRemovals = 0
    private var totalTripSeconds = 0.0

    /** Beendete Fahrten seit dem letzten Abholen (für die Zufriedenheit der Bürger). */
    val finishedTrips = TripLog()
    private val reports = ArrayList<DayReport>()

    /** Filter für neue Fahrten, z. B. "kein Führerschein". Wird vom Spiel gesetzt. */
    var tripAllowed: (Trip) -> Boolean = { true }

    /** Simulierte Sekunden seit dem Start. */
    val time: Double get() = clock.elapsedSeconds

    /** Wochentag und Uhrzeit. */
    val gameTime: GameTime get() = GameTime.at(time, config.startSecondOfDay)

    /** Bilanzen aller abgeschlossenen Tage, ältester zuerst. */
    val dayReports: List<DayReport> get() = reports

    /** Vorfälle je Art im laufenden Tag (z. B. eingeklemmte LKW) – nicht erst im Tagesbericht. */
    val incidentsToday: Map<IncidentKind, Int> get() = dayStatistics.incidentsSoFar

    fun step() {
        if (clock.tick % ENVIRONMENT_TICKS == 0L) {
            updateEnvironment()
            infrastructure.update(ENVIRONMENT_TICKS * dt, time)
            parking.update(time)
        }
        graph.setQuietHours(gameTime.isQuietHours)
        if (clock.tick % STRESS_UPDATE_TICKS == 0L) vehicles.forEach { behavior.updateStress(it, time) }
        generateTrips()
        enterPendingVehicles()
        computeAccelerations()
        move()
        crossNodes()
        incidents.update(time)
        events.update(time)
        charging.update(time)
        laneChanger.changeLanes(clock.tick)
        if (clock.tick % QUEUE_UPDATE_TICKS == 0L) {
            router.updateFromQueues(lanes)
            incidents.sweep(vehicles)
        }
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
            // Wer keinen Führerschein mehr hat, fährt nicht – die Fahrt fällt weg
            if (!tripAllowed(trip)) continue
            val route = router.route(trip.originNode, trip.destinationNode)
            if (route != null && pending.size < config.maxPendingVehicles) {
                val vehicle = factory.create(route, trip, time, router.referenceTime(route))
                router.planFor(vehicle, trip)
                charging.planTrip(vehicle)
                parking.planTrip(vehicle, trip)
                pending.addLast(vehicle)
            }
        }
    }

    /** Jeder wartende Wagen versucht einmal pro Tick einzufahren; wer keinen Platz findet, wartet weiter. */
    private fun enterPendingVehicles() {
        repeat(pending.size) {
            val vehicle = pending.removeFirst()
            // Einsatz schon erledigt, bevor das Einsatzfahrzeug überhaupt losfahren konnte
            if (vehicle.removedByIncident) return@repeat
            val link = vehicle.link
            val lane = lanes.bestEntryLane(link, vehicle.lane)
            if (lanes.freeSpaceAtStart(link, lane) > vehicle.type.length + vehicle.driver.minimumGap + ENTRY_MARGIN) {
                vehicle.lane = lane
                vehicle.position = vehicle.type.length.toDouble()
                vehicle.speed = min(ENTRY_SPEED, graph.linkSpeedLimit[link].toDouble())
                vehicle.waitingTime = 0.0
                vehicle.linkEnterTime = time
                behavior.enterLink(vehicle, -1, link)
                lanes[link][lane].addLast(vehicle)
                // Nach ID sortiert einfügen (Weiterfahrt nach dem Laden hat eine alte ID)
                val index = vehicles.binarySearch { it.id.compareTo(vehicle.id) }
                vehicles.add(if (index < 0) -(index + 1) else index, vehicle)
                if (!vehicle.active) {
                    vehicle.active = true
                } else if (vehicle.role == VehicleRole.PRIVATE) {
                    dayStatistics.tripStarted(gameTime)
                }
            } else {
                pending.addLast(vehicle)
            }
        }
    }

    // --- Fahrzeugfolge --------------------------------------------------------------------------------

    private fun computeAccelerations() {
        for (link in graph.simulatedLinks) {
            for (queue in lanes[link]) {
                for (i in queue.indices) {
                    val vehicle = queue[i]
                    val desired = speedControl.desiredSpeed(vehicle, link)
                    val leader = queue.getOrNull(i - 1)
                    val gap =
                        leader?.let { it.position - it.type.length - vehicle.position } ?: Double.POSITIVE_INFINITY
                    val acceleration =
                        if (leader != null) {
                            vehicle.idm(desired, gap, vehicle.speed - leader.speed)
                        } else {
                            frontAcceleration(vehicle, link, desired)
                        }
                    vehicle.acceleration = behavior.adjust(vehicle, acceleration, desired, gap)
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
        for (link in graph.simulatedLinks) {
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
            environment.emit(vehicle, dt)
            if (!deliveries.update(vehicle, link, time)) incidents.afterMove(vehicle, link, time)
            charging.consume(vehicle, dt, time)
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
        // Kriechen zählt als Warten – sonst setzt jeder Zentimeter Vorrollen die Uhr zurück
        vehicle.waitingTime = if (vehicle.speed < CRAWL_SPEED) vehicle.waitingTime + dt else 0.0
        // Wer zu lange steht, gibt auf und verlässt das Netz – sonst könnte ein Knoten alles dauerhaft blockieren
        val mayGiveUp = vehicle.role == VehicleRole.PRIVATE && !vehicle.isParked
        if (mayGiveUp && vehicle.waitingTime > config.stuckTimeoutSeconds) vehicle.active = false
    }

    // --- Knoten überqueren ----------------------------------------------------------------------------

    private fun crossNodes() {
        for (link in graph.simulatedLinks) {
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
        // Liegengebliebene und Wartende bleiben, wo sie sind
        if (vehicle.isParked || vehicle.nextLink < 0) return finishRoute(vehicle, length)
        val next = nextOpenLink(vehicle)
        val overflow = vehicle.position - length
        val lane = if (next >= 0) lanes.bestEntryLane(next, vehicle.lane) else 0
        // Gesperrt oder kein Platz hinter dem Knoten: warten
        if (next < 0 || lanes.freeSpaceAtStart(next, lane) < overflow + STANDSTILL_GAP) {
            vehicle.position = length.toDouble()
            vehicle.speed = 0.0
            return false
        }
        router.observe(link, time - vehicle.linkEnterTime)
        infrastructure.vehiclePassed(link, vehicle)
        vehicle.routeIndex++
        vehicle.lane = lane
        vehicle.position = overflow
        vehicle.linkEnterTime = time
        behavior.enterLink(vehicle, link, next)
        lanes[next][lane].addLast(vehicle)
        // Einsatzfahrzeuge behalten ihre Route zum Einsatzort
        val wantsReroute =
            vehicle.role == VehicleRole.PRIVATE && time - vehicle.lastRerouteTime > config.rerouteIntervalSeconds
        when {
            infrastructure.truckGetsStuck(vehicle, next) -> incidents.breakdown(vehicle, IncidentKind.TRUCK_STUCK, time)
            wantsReroute -> router.reroute(vehicle, time)
        }
        return true
    }

    /** Nächster Link; vor einer Sperrung (Baustelle, Brücke) plant das Navi neu. -1, wenn es nicht weitergeht. */
    private fun nextOpenLink(vehicle: Vehicle): Int {
        val private = vehicle.role == VehicleRole.PRIVATE
        if (private && !graph.isOpen(vehicle.nextLink)) router.reroute(vehicle, time)
        val next = vehicle.nextLink
        return if (next < 0 || (private && !graph.isOpen(next))) -1 else next
    }

    /** Ende der Route: ankommen, an die Ladesäule fahren oder (Einsatzfahrzeuge, Wartende) am Linkende halten. */
    private fun finishRoute(
        vehicle: Vehicle,
        length: Float,
    ): Boolean {
        val leaves =
            when {
                vehicle.isParked -> {
                    false
                }

                vehicle.chargingStation >= 0 -> {
                    charging.arrive(vehicle, time)
                }

                // Einsatzfahrzeug am Ziel: warten, solange der Einsatz läuft, sonst Feierabend
                vehicle.role != VehicleRole.PRIVATE && incidents.isOpen(vehicle.incidentId) -> {
                    vehicle.holdAtEnd = true
                    false
                }

                vehicle.role != VehicleRole.PRIVATE -> {
                    vehicle.active = false
                    true
                }

                // Am Ziel: Parkplatz suchen – wer weitersucht, hat schon eine neue Route
                vehicle.parkingZone >= 0 -> {
                    val outcome = parking.arrive(vehicle, vehicle.link, time)
                    (outcome != ParkingOutcome.SEARCHING).also { if (it) arrive(vehicle) }
                }

                else -> {
                    true.also { arrive(vehicle) }
                }
            }
        if (!leaves) {
            vehicle.position = length.toDouble()
            vehicle.speed = 0.0
        }
        return leaves
    }

    private fun arrive(vehicle: Vehicle) {
        vehicle.active = false
        completedTrips++
        totalTripSeconds += time - vehicle.departureTime
        val travelSeconds = time - vehicle.departureTime - vehicle.chargingSeconds
        finishedTrips.add(TripResult(vehicle.homeZone, travelSeconds, vehicle.referenceSeconds))
        dayStatistics.tripCompleted(time - vehicle.departureTime)
    }

    // --- Navi ---------------------------------------------------------------------------------------------

    /** Jede Sekunde: Ausstoß an der Fahrzeugposition ablegen, Raster abbauen; alle 5 s veröffentlichen. */
    private fun updateEnvironment() {
        val point = FloatArray(LOCATE_SIZE)
        for (vehicle in vehicles) {
            graph.locate(vehicle.link, vehicle.position.toFloat(), vehicle.lane, point)
            environment.deposit(vehicle, point[LOCATE_X], point[LOCATE_Y])
        }
        environment.decay(ENVIRONMENT_TICKS * dt)
        if (clock.tick % ENVIRONMENT_PUBLISH_TICKS == 0L) environment.publish()
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
            environment = environment.snapshot,
            incidents = incidents.active.map { it.toMarker() },
            events = events.markers(),
            chargingStations = charging.states(),
            infrastructure = infrastructure.snapshot(),
            parkingLots = parking.states(),
            parking = parking.summary(),
            flags = ByteArray(count) { flagsOf(vehicles[it]) },
            types = ByteArray(count) { vehicles[it].type.ordinal.toByte() },
            roles = ByteArray(count) { vehicles[it].role.ordinal.toByte() },
            troubles = ByteArray(count) { vehicles[it].trouble?.let { kind -> (kind.ordinal + 1).toByte() } ?: 0 },
            segmentFlow = router.segmentFlow(),
            stats =
                SimulationStats(
                    activeVehicles = vehicles.size,
                    pendingVehicles = pending.size,
                    completedTrips = completedTrips,
                    stuckRemovals = stuckRemovals,
                    meanSpeedKmh =
                        if (vehicles.isEmpty()) {
                            0.0
                        } else {
                            vehicles.sumOf { it.speed } / vehicles.size *
                                KMH_PER_MS
                        },
                    meanTripMinutes =
                        if (completedTrips >
                            0
                        ) {
                            totalTripSeconds / completedTrips / SECONDS_PER_MINUTE
                        } else {
                            0.0
                        },
                ),
        )
    }

    private companion object {
        const val SECONDS_PER_HOUR = 3600.0
        const val SECONDS_PER_MINUTE = 60.0
        const val ENTRY_SPEED = 8.0
        const val ENTRY_MARGIN = 2.0
        const val STANDSTILL_GAP = 0.5
        const val CRAWL_SPEED = 1.0
        const val QUEUE_UPDATE_TICKS = 100L
        const val STRESS_UPDATE_TICKS = 50L
        const val ENVIRONMENT_TICKS = 10L
        const val ENVIRONMENT_PUBLISH_TICKS = 50L
    }

    // --- Für Tests ------------------------------------------------------------------------------------

    internal val statisticsForTest: DayStatistics get() = dayStatistics
    internal val incidentsForTest: Incidents get() = incidents
    internal val vehiclesForTest: List<Vehicle> get() = vehicles
}
