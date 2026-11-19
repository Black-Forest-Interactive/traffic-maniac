package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable

/** Art eines Ereignisses. Sperrungen haben eine feste Dauer, Korso und Schwertransport fahren eine Route ab. */
enum class EventKind(
    private val labelText: String,
    /** Sperrt Abschnitte für eine feste Zeit (sonst: fährt als Kolonne durchs Netz). */
    val isClosure: Boolean,
) {
    BIKE_CORSO(translatable("Rad-Korso"), isClosure = false),
    DEMO(translatable("Spontandemo"), isClosure = true),
    CITY_FESTIVAL(translatable("Stadtfest"), isClosure = true),
    MARATHON(translatable("Marathon"), isClosure = true),
    MARKET(translatable("Wochenmarkt"), isClosure = true),
    HEAVY_TRANSPORT(translatable("Schwertransport"), isClosure = false),
    ;

    val label: String get() = tr(labelText)
}

/** Ein laufendes oder beendetes Ereignis. */
class CityEvent internal constructor(
    val id: Int,
    val kind: EventKind,
    /** Wo es stattfindet, z. B. "Westliche Karl-Friedrich-Straße". */
    val place: String,
    /** Gesperrte Abschnitte (Sperrungen) bzw. die Route (Kolonnen). */
    val segments: Set<Int>,
    val startTime: Double,
    /** Ende einer Sperrung; Kolonnen enden, wenn alle Fahrzeuge angekommen sind (NaN). */
    val endTime: Double,
) {
    internal val vehicles = ArrayList<Vehicle>()

    /**
     * Wie weit das Ereignis ist (0..1): bei Sperrungen die verstrichene Zeit, bei Kolonnen der zurückgelegte Teil
     * der Route.
     */
    fun progress(time: Double): Float =
        if (!endTime.isNaN()) {
            ((time - startTime) / (endTime - startTime)).toFloat().coerceIn(0f, 1f)
        } else if (vehicles.isEmpty()) {
            0f
        } else {
            vehicles
                .map {
                    it.routeIndex.toFloat() /
                        it.route.size.coerceAtLeast(
                            1,
                        )
                }.average()
                .toFloat()
                .coerceIn(0f, 1f)
        }

    /** Ende des Ereignisses in Sekunden Simulationszeit (NaN, solange es läuft). */
    var finishedAt = Double.NaN
        internal set
}

/** Ein Ereignis auf der Karte: Ort der Spitze (Kolonne) bzw. Mitte der Sperrung. */
class EventMarker(
    val kind: EventKind,
    val x: Float,
    val y: Float,
    val segments: Set<Int>,
)

/**
 * Ereignisse in der Simulation – was sie auslöst, entscheidet das Spiel:
 *
 * - **Sperrungen** (Demo, Stadtfest, Marathon, Wochenmarkt) machen Abschnitte für eine Weile unbefahrbar.
 * - **Kolonnen** (Rad-Korso, Schwertransport) sind langsame Sonderfahrzeuge mit fester Route. Sie fahren über Rot und
 *   nehmen sich die Vorfahrt. Einen Korso überholt niemand: Wer auf demselben Link fährt, rollt im Korso-Tempo mit –
 *   auch wenn daneben ein frisch gebauter Radweg liegt.
 */
class CityEvents internal constructor(
    private val graph: RoadGraph,
    private val closures: RoadClosures,
    private val router: Router,
    private val factory: VehicleFactory,
    private val lanes: LaneQueues,
    private val spawn: (Vehicle) -> Unit,
) {
    private val events = ArrayList<CityEvent>()
    private val finished = ArrayList<CityEvent>()
    private val cappedLinks = ArrayList<Int>()
    private var nextId = 0

    val active: List<CityEvent> get() = events

    /** Sperrt [segments] bis [until]. */
    fun startClosure(
        kind: EventKind,
        segments: Collection<Int>,
        place: String,
        time: Double,
        until: Double,
    ): CityEvent {
        require(kind.isClosure) { "$kind ist keine Sperrung" }
        val event = CityEvent(nextId++, kind, place, segments.toSet(), time, until)
        for (segment in event.segments) closures.setEventClosed(segment, true)
        events += event
        return event
    }

    /** Schickt eine Kolonne aus [groups] Fahrzeugen von [fromNode] nach [toNode]. `null`, wenn es keinen Weg gibt. */
    fun startConvoy(
        kind: EventKind,
        fromNode: Int,
        toNode: Int,
        groups: Int,
        place: String,
        time: Double,
    ): CityEvent? {
        require(!kind.isClosure) { "$kind ist keine Kolonne" }
        val route = router.route(fromNode, toNode)?.takeIf { it.isNotEmpty() } ?: return null
        val segments = route.mapTo(LinkedHashSet()) { graph.linkSegment[it] }
        val event = CityEvent(nextId++, kind, place, segments, time, Double.NaN)
        val role = if (kind == EventKind.BIKE_CORSO) VehicleRole.BIKE_CORSO else VehicleRole.HEAVY_TRANSPORT
        repeat(groups.coerceAtLeast(1)) {
            val vehicle = factory.createEvent(role, route.copyOf(), time)
            event.vehicles += vehicle
            spawn(vehicle)
        }
        events += event
        return event
    }

    /** Jeden Tick: Sperrungen auslaufen lassen, angekommene Kolonnen beenden, Korso-Tempo setzen. */
    internal fun update(time: Double) {
        for (link in cappedLinks) graph.linkEventSpeedCap[link] = Float.MAX_VALUE
        cappedLinks.clear()
        val ended = ArrayList<CityEvent>()
        for (event in events) {
            val over =
                if (event.kind.isClosure) {
                    time >= event.endTime
                } else {
                    event.vehicles.none { it.active }
                }
            if (over) {
                ended += event
            } else if (event.kind == EventKind.BIKE_CORSO) {
                capSpeedAround(event)
            }
        }
        ended.forEach { end(it, time) }
    }

    private fun capSpeedAround(event: CityEvent) {
        for (vehicle in event.vehicles) {
            if (!vehicle.active) continue
            graph.linkEventSpeedCap[vehicle.link] = vehicle.type.maxSpeed.toFloat()
            cappedLinks += vehicle.link
        }
    }

    /** Beendet ein Ereignis vorzeitig (z. B. löst sich die Demo auf). */
    fun stop(
        id: Int,
        time: Double,
    ) {
        events.firstOrNull { it.id == id }?.let { end(it, time) }
    }

    private fun end(
        event: CityEvent,
        time: Double,
    ) {
        if (event.kind.isClosure) {
            for (segment in event.segments) closures.setEventClosed(segment, false)
        } else {
            // Wer noch unterwegs ist, löst sich auf – zählt nicht als im Stau aufgegeben
            for (vehicle in event.vehicles.filter { it.active }) {
                lanes[vehicle.link][vehicle.lane].remove(vehicle)
                vehicle.active = false
                vehicle.removedByIncident = true
            }
        }
        event.finishedAt = time
        events.remove(event)
        finished += event
    }

    /** Seit dem letzten Abholen beendete Ereignisse. */
    fun drainFinished(): List<CityEvent> = finished.toList().also { finished.clear() }

    internal fun markers(): List<EventMarker> {
        val point = FloatArray(LOCATE_SIZE)
        return events.map { event ->
            val lead = event.vehicles.firstOrNull { it.active }
            if (lead != null) {
                graph.locate(lead.link, lead.position.toFloat(), lead.lane, point)
            } else {
                val link = graph.segmentLinks[event.segments.elementAt(event.segments.size / 2)].first()
                graph.locate(link, graph.linkLength[link] / 2, 0, point)
            }
            EventMarker(event.kind, point[LOCATE_X], point[LOCATE_Y], event.segments)
        }
    }
}
