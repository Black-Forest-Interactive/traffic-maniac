package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.FacilityKind
import de.bfi.trafficmaniac.city.RoadClass
import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Wetter für die Darstellung: Schneehöhe je Abschnitt (cm), ob es gerade schneit, Räumfahrzeuge im Einsatz. */
class WeatherSnapshot(
    val snow: FloatArray,
    val snowing: Boolean,
    val ploughs: Int,
) {
    companion object {
        val NONE = WeatherSnapshot(FloatArray(0), snowing = false, ploughs = 0)
    }
}

/** Ein Schneeschauer: am [day]-ten Tag der Amtszeit (0 = erster) ab [startHour] für [hours] Stunden. */
@Serializable
data class SnowSpell(
    val day: Int,
    val startHour: Double,
    val hours: Double,
    val cmPerHour: Double,
)

/**
 * Wetter eines Szenarios: Schneeschauer, wie viele noch mit Sommerreifen fahren (am ersten Tag; jeden Tag rüstet
 * ein Teil um) und wie viele Räumfahrzeuge zu Beginn bereitstehen.
 */
@Serializable
data class WeatherConfig(
    val spells: List<SnowSpell> = emptyList(),
    val summerTyreShare: Double = 0.2,
    /** Anteil der Sommerreifen, der am nächsten Tag noch übrig ist. */
    val summerTyresLeftPerDay: Double = 0.5,
    val ploughs: Int = 3,
    /** So schnell taut der Schnee, wenn es nicht schneit (cm pro Stunde). */
    val meltCmPerHour: Double = 0.1,
    /** Rutschunfälle je Fahrzeugstunde auf 5 cm Schnee (Winterreifen); mit Sommerreifen [SUMMER_TYRE_RISK]-fach. */
    val slideAccidentsPerHour: Double = 0.006,
    /** Steckenbleiben je Fahrzeugstunde mit Sommerreifen auf tiefem Schnee. */
    val summerTyreStuckPerHour: Double = 0.05,
)

/**
 * Schnee auf den Straßen und der Räumdienst.
 *
 * - Während eines [SnowSpell] wächst die Schneedecke auf jedem Abschnitt; danach taut sie langsam.
 * - Schnee bremst alle (Tempo-Faktor im Graphen), macht Rutschunfälle wahrscheinlicher und lässt Autos mit
 *   Sommerreifen stecken bleiben.
 * - Räumfahrzeuge fahren von der Feuerwache (bzw. aus der Stadtmitte) zum dringendsten verschneiten Abschnitt –
 *   Hauptstraßen zuerst – und räumen und streuen alles, worüber sie fahren. Ohne Blaulicht: Im Stau stehen sie mit.
 */
class Weather internal constructor(
    city: CityMap,
    private val graph: RoadGraph,
    private val config: SimulationConfig,
    private val router: Router,
    private val factory: VehicleFactory,
    private val spawn: (Vehicle) -> Unit,
) {
    private val weather = config.weather
    private val segmentCount = graph.segmentLinks.size

    /** Schneehöhe je Abschnitt in cm. */
    private val depth = FloatArray(segmentCount)

    /** Bis wann ein Abschnitt gestreut ist (Simulationszeit): dort bleibt kaum etwas liegen. */
    private val saltedUntil = DoubleArray(segmentCount)

    private val segmentFrom = IntArray(segmentCount) { graph.linkFrom[graph.segmentLinks[it].first()] }
    private val segmentClass = Array(segmentCount) { graph.linkRoadClass[graph.segmentLinks[it].first()] }
    private val segmentPassive = BooleanArray(segmentCount) { graph.linkPassive[graph.segmentLinks[it].first()] }

    /** Wache, von der die Räumfahrzeuge ausrücken. */
    private val depot: Int =
        city.facilities.firstOrNull { it.kind == FacilityKind.FIRE_STATION }?.node
            ?: (0 until graph.nodeCount).minBy {
                hypot(graph.nodeX[it] - city.bounds.centerX, graph.nodeY[it] - city.bounds.centerY)
            }

    /** Räumfahrzeuge im Einsatz (auf der Straße oder gerade wieder losgeschickt). */
    private val ploughs = ArrayList<Vehicle>()

    /** Wie viele Räumfahrzeuge unterwegs sein sollen – stellt das Spiel ein. */
    var ploughCount: Int = weather?.ploughs ?: 0

    /** Schneit es gerade? */
    var snowing: Boolean = false
        private set

    val isEnabled: Boolean get() = weather != null

    /** Für die Darstellung: Schneehöhen, ob es schneit, Räumfahrzeuge auf der Straße. */
    fun snapshot(): WeatherSnapshot =
        if (weather == null) WeatherSnapshot.NONE else WeatherSnapshot(depth.copyOf(), snowing, ploughsOnRoad)

    /** Mittlere Schneehöhe auf den befahrenen Straßen in cm. */
    fun meanDepth(): Double {
        val active = (0 until segmentCount).filter { !segmentPassive[it] }
        return if (active.isEmpty()) 0.0 else active.sumOf { depth[it].toDouble() } / active.size
    }

    /** Räumfahrzeuge gerade auf der Straße. */
    val ploughsOnRoad: Int get() = ploughs.count { it.active }

    /** Schneefall in cm pro Stunde zur Simulationszeit [time] (0, wenn es nicht schneit). */
    private fun snowfall(time: Double): Double {
        val spells = weather?.spells ?: return 0.0
        val firstDayStart =
            kotlin.math.floor(config.startSecondOfDay / GameTime.SECONDS_PER_DAY) * GameTime.SECONDS_PER_DAY
        val gameSecond = config.startSecondOfDay + time
        return spells.sumOf { spell ->
            val start =
                firstDayStart + spell.day * GameTime.SECONDS_PER_DAY + spell.startHour * GameTime.SECONDS_PER_HOUR
            val end = start + spell.hours * GameTime.SECONDS_PER_HOUR
            if (gameSecond in start..<end) spell.cmPerHour else 0.0
        }
    }

    /** Alle paar Sekunden: Schnee fällt oder taut, die Fahrbahnen werden entsprechend langsamer. */
    internal fun update(
        time: Double,
        seconds: Double,
    ) {
        if (weather == null) return
        val fall = snowfall(time)
        snowing = fall > 0
        val hours = seconds / GameTime.SECONDS_PER_HOUR
        for (segment in 0 until segmentCount) {
            if (segmentPassive[segment]) continue
            val salted = time < saltedUntil[segment]
            val growth =
                if (fall >
                    0
                ) {
                    fall * hours * (if (salted) SALTED_SHARE else 1.0)
                } else {
                    -weather.meltCmPerHour * hours
                }
            depth[segment] = (depth[segment] + growth.toFloat()).coerceIn(0f, MAX_DEPTH)
            val factor = gripFactor(depth[segment])
            for (link in graph.segmentLinks[segment]) graph.linkWeatherFactor[link] = factor
        }
        dispatchPloughs(time)
    }

    /** Jeden Tick: Räumfahrzeuge räumen und streuen, wo sie gerade fahren. */
    internal fun updatePloughs(time: Double) {
        if (weather == null) return
        for (plough in ploughs) {
            if (!plough.active) continue
            val segment = graph.linkSegment[plough.link]
            if (depth[segment] > 0f || time >= saltedUntil[segment]) {
                depth[segment] = 0f
                saltedUntil[segment] = time + SALT_SECONDS
                for (link in graph.segmentLinks[segment]) graph.linkWeatherFactor[link] = 1f
            }
        }
    }

    /**
     * Im Wetter-Takt: Wer sein Ziel erreicht hat (oder im Stau aufgegeben wurde), fährt von dort zum nächsten;
     * fehlende Räumfahrzeuge rücken von der Wache aus – solange es etwas zu räumen gibt.
     */
    private fun dispatchPloughs(time: Double) {
        val restart = ArrayList<Int>()
        ploughs.removeAll { plough ->
            (!plough.active).also { done -> if (done) restart += graph.linkTo[plough.link] }
        }
        while (ploughs.size < ploughCount) {
            val from = restart.removeFirstOrNull() ?: depot
            ploughs += sendPlough(from, time) ?: break
        }
    }

    private fun sendPlough(
        from: Int,
        time: Double,
    ): Vehicle? {
        // Das dringendste erreichbare Ziel – manches ist von hier aus nicht anzufahren (Einbahnstraße, Sperrung)
        for (target in targets(from)) {
            val start = segmentFrom[target]
            val approach = if (start == from) IntArray(0) else router.route(from, start)
            val link = graph.segmentLinks[target].firstOrNull { graph.linkFrom[it] == start }
            if (approach != null && link != null) {
                return factory.createService(VehicleRole.SNOW_PLOUGH, approach + link, time, -1).also(spawn)
            }
        }
        return null
    }

    /**
     * Die dringendsten Ziele: viel Schnee auf einer wichtigen Straße, nicht zu weit weg, und nicht schon das Ziel
     * eines anderen Räumfahrzeugs.
     */
    private fun targets(from: Int): List<Int> {
        val taken = ploughs.mapNotNull { p -> p.route.lastOrNull()?.let { graph.linkSegment[it] } }.toSet()
        return (0 until segmentCount)
            .filter { !segmentPassive[it] && it !in taken && depth[it] >= MIN_PLOUGH_DEPTH }
            .sortedByDescending { segment ->
                val node = segmentFrom[segment]
                val distance = hypot(graph.nodeX[node] - graph.nodeX[from], graph.nodeY[node] - graph.nodeY[from])
                depth[segment] * importance(segmentClass[segment]) / (1 + distance / DISTANCE_SCALE)
            }.take(MAX_TARGET_TRIES)
    }

    private fun importance(roadClass: RoadClass): Double =
        when {
            roadClass.ordinal <= RoadClass.PRIMARY_LINK.ordinal -> PRIMARY_IMPORTANCE
            roadClass.ordinal <= RoadClass.SECONDARY_LINK.ordinal -> SECONDARY_IMPORTANCE
            roadClass.ordinal <= RoadClass.TERTIARY_LINK.ordinal -> TERTIARY_IMPORTANCE
            else -> 1.0
        }

    /** Tempo-Faktor auf [depthCm] Schnee: bei 10 cm gut die Hälfte. */
    private fun gripFactor(depthCm: Float): Float = 1f - min(depthCm, FULL_EFFECT_CM) / FULL_EFFECT_CM * MAX_SLOWDOWN

    /** Anteil der Autos mit Sommerreifen an Tag [day] der Amtszeit. */
    internal fun summerTyreShare(day: Int): Double =
        weather?.let { it.summerTyreShare * it.summerTyresLeftPerDay.pow(day) } ?: 0.0

    /**
     * Nach jeder Bewegung eines Privatautos: Rutscht es weg (Unfall) oder bleibt es stecken (Sommerreifen)?
     * `null`, wenn nichts passiert.
     */
    internal fun roll(
        vehicle: Vehicle,
        link: Int,
        random: Random,
    ): IncidentKind? {
        val w = weather ?: return null
        val cm = depth[graph.linkSegment[link]]
        if (cm < MIN_EFFECT_CM || vehicle.speed < MOVING) return null
        val hours = 1.0 / config.ticksPerSecond / GameTime.SECONDS_PER_HOUR
        val tyres = if (vehicle.summerTyres) SUMMER_TYRE_RISK else 1.0
        val slide = w.slideAccidentsPerHour * min(cm, FULL_EFFECT_CM) / REFERENCE_CM * tyres * hours
        val stuck = if (vehicle.summerTyres && cm >= DEEP_SNOW_CM) w.summerTyreStuckPerHour * hours else 0.0
        val roll = random.nextDouble()
        return when {
            roll < slide -> IncidentKind.ACCIDENT
            roll < slide + stuck -> IncidentKind.STUCK_IN_SNOW
            else -> null
        }
    }

    private companion object {
        const val MAX_DEPTH = 30f
        const val FULL_EFFECT_CM = 10f
        const val MAX_SLOWDOWN = 0.45f
        const val REFERENCE_CM = 5.0
        const val MIN_EFFECT_CM = 0.5f
        const val DEEP_SNOW_CM = 3f
        const val MIN_PLOUGH_DEPTH = 1f

        /** So viele Ziele werden probiert, bis eines erreichbar ist. */
        const val MAX_TARGET_TRIES = 8
        const val MOVING = 3.0
        const val SUMMER_TYRE_RISK = 4.0
        const val SALT_SECONDS = 2 * 3_600.0
        const val SALTED_SHARE = 0.2
        const val DISTANCE_SCALE = 1_500.0
        const val PRIMARY_IMPORTANCE = 4.0
        const val SECONDARY_IMPORTANCE = 2.5
        const val TERTIARY_IMPORTANCE = 1.5
    }
}
