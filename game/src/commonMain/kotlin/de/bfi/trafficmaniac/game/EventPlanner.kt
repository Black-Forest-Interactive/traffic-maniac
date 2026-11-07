package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.city.RoadSegment
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.sim.CityEvent
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.Weekday
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Geplantes Ereignis im Spielkalender – vorab sichtbar. */
data class CalendarEntry(
    val day: Int,
    val weekday: Weekday,
    val startHour: Int,
    val endHour: Int,
    val kind: EventKind,
    val place: String,
    val segments: Set<Int>,
)

/** Laufendes Ereignis für die Oberfläche. */
data class ActiveEvent(
    val kind: EventKind,
    val place: String,
    val since: String,
    /** Geplantes Ende ("bis 13:00") oder Fortschritt der Kolonne. */
    val until: String,
    /** Gesperrte Abschnitte bzw. Route – wohin ein Klick die Karte führt. */
    val segments: Set<Int> = emptySet(),
)

data class EventsState(
    /** Kommende geplante Ereignisse (heute und später). */
    val calendar: List<CalendarEntry> = emptyList(),
    val active: List<ActiveEvent> = emptyList(),
    /** Ereignisse je Art seit Spielbeginn. */
    val totals: Map<EventKind, Int> = emptyMap(),
    /** Dauerstau-Tage in Folge (treibt die Rad-Korsos). */
    val jamStreak: Int = 0,
)

/** Was heute los war (für Zeitung und Achievements). */
internal class EventNews {
    val corsos = ArrayList<String>()
    val demos = ArrayList<Pair<String, Int>>()
    val planned = ArrayList<Pair<EventKind, String>>()
    var heavyTransports = 0
}

/**
 * Löst Ereignisse aus und führt den Spielkalender.
 *
 * - **Geplant** (im Kalender vorab sichtbar): Wochenmarkt mittwochs und samstags, Marathon am ersten Sonntag,
 *   Stadtfest am zweiten Samstag.
 * - **Rad-Korso**: am Wochenende, nach mehreren Dauerstau-Tagen in Folge und wenn viele ihren Führerschein los sind.
 * - **Spontandemo**: vor dem unzufriedensten Wohngebiet, umso häufiger und größer, je höher der Bürgerzorn.
 * - **Schwertransport**: manche Nacht von einer Stadteinfahrt zur anderen.
 *
 * Damit Ereignisse den Spieler nicht entmündigen, laufen höchstens ein Korso und eine Demo gleichzeitig,
 * und es gibt Tageshöchstzahlen.
 */
internal class EventPlanner(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val messenger: Messenger,
) {
    private val random = Random(RANDOM_SEED)

    /** Erster Spieltag (Tage seit dem Montag der ersten Woche). */
    private val firstDay = simulation.gameTime.day
    private val places = EventPlaces(city, simulation, random)
    private val calendar = ArrayList<CalendarEntry>()
    private val running = HashMap<Int, String>()
    private val totals = HashMap<EventKind, Int>()
    private var corsosToday = 0
    private var demosToday = 0
    private var jamStreak = 0

    /** Anteil der Zuspätkommer am ersten Spieltag – das Normalmaß der Stadt. */
    private var normalLateShare = Double.NaN
    var today = EventNews()
        private set

    init {
        planCalendar()
    }

    /** Kalender über die Tage der Amtszeit – ab dem Starttag des Szenarios, nicht unbedingt ab Montag. */
    private fun planCalendar() {
        if (places.mainRoads.isEmpty()) return
        val market = places.marketStreet()
        val days = firstDay until firstDay + rules.termDays

        fun weekday(day: Int) = Weekday.entries[day % Weekday.entries.size]
        for (day in days) {
            if (weekday(day) == Weekday.WEDNESDAY || weekday(day) == Weekday.SATURDAY) {
                market?.let { calendar += entry(day, MARKET_HOURS, EventKind.MARKET, it) }
            }
        }
        days.firstOrNull { weekday(it) == Weekday.SUNDAY }?.let {
            calendar += entry(it, MARATHON_HOURS, EventKind.MARATHON, places.longestMainStreet())
        }
        // Stadtfest am zweiten Samstag der Amtszeit, gibt es nur einen, dann an diesem
        val saturdays = days.filter { weekday(it) == Weekday.SATURDAY }
        (saturdays.getOrNull(1) ?: saturdays.firstOrNull())?.let {
            calendar += entry(it, FESTIVAL_HOURS, EventKind.CITY_FESTIVAL, places.centralStreets())
        }
        calendar.sortWith(compareBy({ it.day }, { it.startHour }))
    }

    private fun entry(
        day: Int,
        hours: IntRange,
        kind: EventKind,
        place: Pair<String, Set<Int>>,
    ) = CalendarEntry(
        day,
        Weekday.entries[day % Weekday.entries.size],
        hours.first,
        hours.last,
        kind,
        place.first,
        place.second,
    )

    fun state(): EventsState {
        val now = simulation.gameTime
        val active =
            simulation.events.active.map { event ->
                val until =
                    if (event.kind.isClosure) {
                        "bis ${GameTime.at(event.endTime, simulation.config.startSecondOfDay).clockText}"
                    } else {
                        "unterwegs"
                    }
                ActiveEvent(event.kind, event.place, running[event.id] ?: "", until, event.segments)
            }
        return EventsState(
            calendar = calendar.filter { it.day > now.day || (it.day == now.day && it.endHour > now.hour) },
            active = active,
            totals = totals.toMap(),
            jamStreak = jamStreak,
        )
    }

    /** Jede volle Stunde: geplante Ereignisse starten, Korso, Demo und Schwertransport auswürfeln. */
    fun hourPassed(
        anger: Double,
        bannedDrivers: Int,
        moods: List<ZoneMood>,
    ) {
        collectFinished()
        val now = simulation.gameTime
        for (entry in calendar.filter { it.day == now.day && it.startHour == now.hour }) {
            val until = simulation.time + (entry.endHour - entry.startHour) * SECONDS_PER_HOUR
            start(simulation.events.startClosure(entry.kind, entry.segments, entry.place, simulation.time, until))
            today.planned += entry.kind to entry.place
            messenger.say(
                "${entry.kind.label} – ${entry.place} ist bis ${entry.endHour}:00 Uhr gesperrt",
                city.placeOfSegments(entry.segments),
            )
        }
        if (now.hour in CORSO_HOURS) maybeCorso(now, bannedDrivers)
        if (now.hour in DEMO_HOURS) maybeDemo(anger, moods)
        if (now.hour == HEAVY_TRANSPORT_HOUR && random.nextDouble() < rules.heavyTransportChancePerNight) {
            heavyTransport()
        }
    }

    private fun maybeCorso(
        now: GameTime,
        bannedDrivers: Int,
    ) {
        if (corsosToday >= rules.maxCorsosPerDay || isRunning(EventKind.BIKE_CORSO)) return
        val chance = corsoChance(now.weekday, bannedDrivers)
        if (random.nextDouble() >= chance) return
        val (from, to) = places.farApartMainNodes(CORSO_MIN_METERS) ?: return
        val groups =
            (CORSO_MIN_GROUPS + jamStreak + bannedDrivers / rules.corsoBannedReference.coerceAtLeast(1))
                .coerceAtMost(CORSO_MAX_GROUPS)
        val place = places.streetAtNode(from)
        val event = simulation.events.startConvoy(EventKind.BIKE_CORSO, from, to, groups, place, simulation.time)
        if (event != null) {
            start(event)
            corsosToday++
            today.corsos += place
            messenger.say(
                "Rad-Korso startet an der $place – Radwege werden grundsätzlich ignoriert",
                city.placeOfNode(from),
            )
        }
    }

    /** Wochenende, Dauerstau und Führerscheinlose ("jetzt halt aufs Rad") treiben den Korso. */
    fun corsoChance(
        weekday: Weekday,
        bannedDrivers: Int,
    ): Double {
        val weekend = if (weekday.isWorkday) 0.0 else rules.corsoChanceWeekend
        val banned =
            rules.corsoChanceBanned * (bannedDrivers.toDouble() / rules.corsoBannedReference).coerceAtMost(1.0)
        return weekend + jamStreak * rules.corsoChancePerJamDay + banned
    }

    private fun maybeDemo(
        anger: Double,
        moods: List<ZoneMood>,
    ) {
        val zone = moods.minByOrNull { it.satisfaction }
        val allowed = demosToday < rules.maxDemosPerDay && !isRunning(EventKind.DEMO)
        if (!allowed || zone == null || random.nextDouble() >= demoChance(anger)) return
        // Größe: mit dem Zorn wächst die Demo von einem auf bis zu fünf Abschnitte
        val size = (1 + anger / DEMO_ANGER_PER_SEGMENT).toInt().coerceIn(1, DEMO_MAX_SEGMENTS)
        val (place, segments) = places.streetNear(zone.x, zone.y, size) ?: return
        val hours = random.nextDouble(rules.demoMinHours, rules.demoMaxHours) + anger / ANGER_PER_EXTRA_HOUR
        val event =
            simulation.events.startClosure(
                EventKind.DEMO,
                segments,
                place,
                simulation.time,
                simulation.time + hours * SECONDS_PER_HOUR,
            )
        start(event)
        demosToday++
        today.demos += place to size
        messenger.say(
            "Spontandemo auf der $place – ${segments.size} Abschnitte gesperrt (etwa ${hours.roundToInt()} Std.)",
            city.placeOfSegments(segments),
        )
    }

    /** Unter [GameRules.demoMinAnger] keine Demos, darüber überproportional mehr. */
    fun demoChance(anger: Double): Double {
        if (anger < rules.demoMinAnger) return 0.0
        val share = ((anger - rules.demoMinAnger) / (MAX_ANGER - rules.demoMinAnger)).coerceIn(0.0, 1.0)
        return rules.demoChancePerHour * share.pow(DEMO_EXPONENT)
    }

    private fun heavyTransport() {
        val gates = city.zones.filter { it.kind == ZoneKind.GATE && it.node >= 0 }
        if (gates.size < 2) return
        val from = gates.random(random)
        val to = gates.maxBy { hypot(it.x - from.x, it.y - from.y) }
        val place = "von ${places.streetAtNode(from.node)} nach ${places.streetAtNode(to.node)}"
        val event =
            simulation.events.startConvoy(EventKind.HEAVY_TRANSPORT, from.node, to.node, 1, place, simulation.time)
                ?: return
        start(event)
        today.heavyTransports++
        messenger.say("Schwertransport mit Polizeibegleitung unterwegs $place", city.placeOfNode(from.node))
    }

    private fun start(event: CityEvent) {
        running[event.id] = simulation.gameTime.clockText
        totals[event.kind] = (totals[event.kind] ?: 0) + 1
    }

    private fun isRunning(kind: EventKind) = simulation.events.active.any { it.kind == kind }

    private fun collectFinished() {
        for (event in simulation.events.drainFinished()) {
            running.remove(event.id)
            if (event.kind == EventKind.DEMO) messenger.say("Demo auf der ${event.place} löst sich auf")
        }
    }

    /** Mitternacht: Dauerstau-Serie fortschreiben, Tageszähler zurücksetzen. */
    fun dayEnded(commute: CommuteStats): EventNews {
        if (normalLateShare.isNaN() && commute.trips > 0) normalLateShare = commute.lateShare
        val threshold = maxOf(rules.jamLateShare, normalLateShare + rules.jamExtraLateShare)
        jamStreak = if (commute.trips > 0 && commute.lateShare >= threshold) jamStreak + 1 else 0
        corsosToday = 0
        demosToday = 0
        return today.also { today = EventNews() }
    }

    private companion object {
        const val RANDOM_SEED = 4711
        const val SECONDS_PER_HOUR = 3_600.0
        const val MAX_ANGER = 100.0
        val MARKET_HOURS = 7..13
        val MARATHON_HOURS = 8..13
        val FESTIVAL_HOURS = 11..23
        val CORSO_HOURS = 10..18
        val DEMO_HOURS = 9..20
        const val HEAVY_TRANSPORT_HOUR = 23
        const val CORSO_MIN_METERS = 2_000f
        const val CORSO_MIN_GROUPS = 3
        const val CORSO_MAX_GROUPS = 10
        const val DEMO_ANGER_PER_SEGMENT = 25.0
        const val DEMO_MAX_SEGMENTS = 5
        const val DEMO_EXPONENT = 1.5
        const val ANGER_PER_EXTRA_HOUR = 50.0
    }
}
