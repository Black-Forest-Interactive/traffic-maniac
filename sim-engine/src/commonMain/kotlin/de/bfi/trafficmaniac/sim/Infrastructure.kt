package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import kotlin.math.pow
import kotlin.random.Random

/** Schaden, den die Stadt gemeldet bekommt, sobald ein Zustand eine Schwelle unterschreitet. */
enum class DamageKind(
    private val labelText: String,
) {
    POTHOLES(translatable("Schlaglöcher")),
    CRATER(translatable("Krater")),
    SIGNAL_FAILURE(translatable("Ampelausfall")),
    BRIDGE_WEIGHT(translatable("Brücke marode")),
    BRIDGE_UNSAFE(translatable("Brücke einsturzgefährdet")),
    BRIDGE_COLLAPSE(translatable("Brücke eingestürzt")),
    ;

    val label: String get() = tr(labelText)

    val isBridge: Boolean get() = this == BRIDGE_WEIGHT || this == BRIDGE_UNSAFE || this == BRIDGE_COLLAPSE
}

/** Ein Abschnitt ([segment]) oder eine Ampel ([node]) hat eine Schadensschwelle unterschritten. */
class DamageEvent(
    val kind: DamageKind,
    /** Straßenabschnitt oder -1 bei Ampeln. */
    val segment: Int,
    /** Ampelknoten oder -1 bei Straßen. */
    val node: Int,
    val time: Double,
    /** Beim Einsturz: Fahrzeuge, die mit in die Tiefe gestürzt sind. */
    val victims: Int = 0,
)

/** Zustand von Straßen, Ampeln und Brücken für die Darstellung (wird nur gelegentlich erneuert). */
class InfrastructureSnapshot(
    /** Zustand 0..1 je Straßenabschnitt (passive Nebenstraßen: 1). */
    val segmentCondition: FloatArray,
    /** Ausgefallene Ampelknoten (blinken gelb). */
    val failedSignals: Set<Int>,
    /** Baustellen; [closedSegments] enthält davon die Vollsperrungen. */
    val worksSegments: Set<Int>,
    val closedSegments: Set<Int>,
    val collapsedSegments: Set<Int>,
    val detourSegments: Set<Int>,
    /** Abschnitte mit Gewichtslimit (aus OSM ausgenommen: nur die vom Spieler gesetzten). */
    val weightLimitedSegments: Set<Int>,
    /** Baustellen des Spielers (ohne Bagger – da arbeitet keiner) und seine Vollsperrungen. */
    val playerWorksSegments: Set<Int> = emptySet(),
    val playerClosedSegments: Set<Int> = emptySet(),
) {
    companion object {
        val EMPTY =
            InfrastructureSnapshot(
                FloatArray(0),
                emptySet(),
                emptySet(),
                emptySet(),
                emptySet(),
                emptySet(),
                emptySet(),
            )
    }
}

/**
 * Infrastruktur-Verfall: Jeder Straßenabschnitt, jede Ampel und jede Brücke hat einen Zustand von 0 bis 1.
 *
 * - Er sinkt mit der Zeit und mit der Belastung. Wie stark ein Fahrzeug die Straße abnutzt, folgt der
 *   Faustregel "Achslast hoch 4": Ein LKW zählt wie Tausende PKW.
 * - **Schlaglöcher** (unter 60 %) und **Krater** (unter 35 %) bremsen den Verkehr, machen Unfälle wahrscheinlicher
 *   und lassen alte Autos öfter Öl verlieren.
 * - **Ampeln** ([trafficLights]) fallen unter 30 % aus und blinken gelb – die Kreuzung wird zum Rechts-vor-links-Chaos.
 * - **Brücken** verfallen doppelt so schnell: unter 50 % ist ein Gewichtslimit fällig, unter 25 % die Sperrung,
 *   unter 10 % stürzen sie ein – dauerhaft.
 *
 * Baustellen, Sperrungen und Umleitungen stecken in [closures]. Ein Erhaltungsbudget ([maintenanceFactor]) bremst
 * den Verfall; Reparaturen setzen den Zustand zurück.
 */
class Infrastructure internal constructor(
    private val graph: RoadGraph,
    signals: SignalSystem,
    private val lanes: LaneQueues,
    private val config: SimulationConfig,
    private val random: Random,
) {
    private val segmentCount = graph.segmentCount
    private val passive = BooleanArray(segmentCount) { graph.linkPassive[graph.segmentLinks[it].first()] }
    private val bridge = BooleanArray(segmentCount) { graph.linkBridge[graph.segmentLinks[it].first()] }
    private val condition =
        DoubleArray(segmentCount) {
            if (passive[it]) PASSIVE_CONDITION else initialCondition(random, config)
        }

    /** Verschleiß-Multiplikator je Abschnitt: Pfusch am Bau (Turbo-Konsortium) nutzt sich schneller ab. */
    private val quality = DoubleArray(segmentCount) { 1.0 }
    private val events = ArrayList<DamageEvent>()
    private val potholeAccidents = ArrayList<Int>()

    /** Brücken, die gerade nachgeben – sie stürzen beim nächsten [update] ein (nicht mitten in der Bewegung). */
    private val collapsing = ArrayList<Int>()
    private var cached: InfrastructureSnapshot? = null
    private var time = 0.0

    /** Zustand der Ampeln. */
    val trafficLights = TrafficLights(graph, signals, config, random, { events += it }, { cached = null })

    /** Baustellen, Sperrungen, Gewichtslimits und Umleitungen. */
    val closures = RoadClosures(graph, ::applyEffects)

    /** Anteil des normalen Verfalls (1 = kein Erhaltungsbudget). */
    var maintenanceFactor = 1.0
        set(value) {
            field = value
            trafficLights.maintenanceFactor = value
        }

    init {
        for (segment in 0 until segmentCount) applyEffects(segment)
    }

    fun condition(segment: Int): Double = condition[segment]

    /** Mittlerer Zustand der befahrenen Straßen (für Statistik und Presse). */
    fun meanCondition(): Double {
        var sum = 0.0
        var count = 0
        for (segment in 0 until segmentCount) {
            if (passive[segment]) continue
            sum += condition[segment]
            count++
        }
        return if (count > 0) sum / count else 1.0
    }

    /** Ein Fahrzeug hat [link] vollständig befahren: Abnutzung der Straße und der Ampel am Ende. */
    internal fun vehiclePassed(
        link: Int,
        vehicle: Vehicle,
    ) {
        val segment = graph.linkSegment[link]
        if (passive[segment] || closures.collapsed[segment]) return
        val load = vehicle.type.equivalentAxleLoads * config.wearPerAxleLoad / graph.linkLanes[link]
        degrade(segment, load * (if (bridge[segment]) config.bridgeWearFactor else 1.0) * quality[segment])
        trafficLights.vehiclePassed(graph.linkTo[link], time)
    }

    /** Alterung über [seconds] Sekunden Spielzeit; nachgebende Brücken stürzen ein. */
    internal fun update(
        seconds: Double,
        now: Double,
    ) {
        time = now
        collapsing.forEach(::collapse)
        collapsing.clear()
        val days = seconds / SECONDS_PER_DAY
        val aging = config.agingPerDay * days
        for (segment in 0 until segmentCount) {
            if (passive[segment] || closures.collapsed[segment]) continue
            degrade(segment, aging * (if (bridge[segment]) config.bridgeWearFactor else 1.0) * quality[segment])
        }
        trafficLights.age(config.signalAgingPerDay * days, time)
    }

    /** Tempo-Faktor durch Schlaglöcher, Krater und Baustellen; Sperrung im Graphen. */
    private fun applyEffects(segment: Int) {
        val c = condition[segment]
        var factor =
            when {
                c < CRATER -> CRATER_SPEED
                c < POTHOLES -> POTHOLE_SPEED
                else -> 1f
            }
        if (closures.works[segment]) factor *= WORKS_SPEED
        if (closures.playerWorks[segment] && !closures.works[segment]) factor *= PLAYER_WORKS_SPEED
        for (link in graph.segmentLinks[segment]) graph.linkRoadFactor[link] = factor
        graph.setSegmentClosed(segment, closures.isClosed(segment))
        cached = null
    }

    /** Unfallrisiko-Faktor auf [link]: Schlaglöcher ×1,5, Krater ×3. */
    internal fun hazard(link: Int): Double {
        val c = condition[graph.linkSegment[link]]
        return when {
            c < CRATER -> CRATER_HAZARD
            c < POTHOLES -> POTHOLE_HAZARD
            else -> 1.0
        }
    }

    /** LKW in enger Nebenstraße: enge Kurve, zu schmal, zu niedrig – und schon steckt er fest. */
    internal fun truckGetsStuck(
        vehicle: Vehicle,
        link: Int,
    ): Boolean {
        val tightFit =
            vehicle.type == VehicleType.TRUCK && graph.linkPassive[link] && vehicle.role == VehicleRole.PRIVATE
        return tightFit && random.nextFloat() < graph.linkTruckBlockChance[link]
    }

    internal fun potholeAccident(link: Int) {
        potholeAccidents += graph.linkSegment[link]
    }

    private fun degrade(
        segment: Int,
        amount: Double,
    ) {
        val before = condition[segment]
        val after = (before - amount * maintenanceFactor).coerceAtLeast(0.0)
        condition[segment] = after
        val kind =
            if (bridge[segment]) {
                when {
                    crossed(before, after, BRIDGE_COLLAPSE) -> null.also { collapsing += segment }
                    crossed(before, after, BRIDGE_UNSAFE) -> DamageKind.BRIDGE_UNSAFE
                    crossed(before, after, BRIDGE_WEIGHT) -> DamageKind.BRIDGE_WEIGHT
                    else -> null
                }
            } else {
                when {
                    crossed(before, after, CRATER) -> DamageKind.CRATER
                    crossed(before, after, POTHOLES) -> DamageKind.POTHOLES
                    else -> null
                }
            }
        kind?.let { events += DamageEvent(it, segment, -1, time) }
        if (crossed(before, after, CRATER) || crossed(before, after, POTHOLES)) applyEffects(segment)
        // Die Kartenfarbe ändert sich in 5-%-Schritten – nur dann wird der Snapshot neu gebaut
        if (crossed(before, after, SNAPSHOT_STEP * (before / SNAPSHOT_STEP).toInt())) cached = null
    }

    /** Die Brücke stürzt ein: Wer gerade darauf ist, fällt mit (zählt weder als angekommen noch als aufgegeben). */
    private fun collapse(segment: Int) {
        var victims = 0
        for (link in graph.segmentLinks[segment]) {
            for (queue in lanes[link]) {
                for (vehicle in queue) {
                    vehicle.active = false
                    vehicle.removedByIncident = true
                    victims++
                }
                queue.clear()
            }
        }
        events += DamageEvent(DamageKind.BRIDGE_COLLAPSE, segment, -1, time, victims)
        closures.collapse(segment)
    }

    /** Setzt den Zustand zurück; [wearFactor] > 1 heißt Pfusch (verfällt schneller). Eingestürztes bleibt weg. */
    fun repairSegment(
        segment: Int,
        newCondition: Double = 1.0,
        wearFactor: Double = 1.0,
    ) {
        if (closures.collapsed[segment]) return
        condition[segment] = newCondition
        quality[segment] = wearFactor
        applyEffects(segment)
    }

    /** Schadensmeldungen seit dem letzten Abholen. */
    fun drainEvents(): List<DamageEvent> = events.toList().also { events.clear() }

    /** Abschnitte, auf denen seit dem letzten Abholen ein Schlagloch-Unfall passiert ist (mehrfach möglich). */
    fun drainPotholeAccidents(): List<Int> = potholeAccidents.toList().also { potholeAccidents.clear() }

    fun snapshot(): InfrastructureSnapshot =
        cached ?: InfrastructureSnapshot(
            segmentCondition = FloatArray(segmentCount) { if (passive[it]) 1f else condition[it].toFloat() },
            failedSignals =
                (0 until graph.nodeCount)
                    .filter {
                        graph.nodeHasSignals[it] &&
                            trafficLights.isFailed(
                                it,
                            )
                    }.toSet(),
            worksSegments = indicesOf(closures.works),
            closedSegments =
                (0 until segmentCount)
                    .filter { closures.worksClosed[it] || closures.bridgeClosed[it] }
                    .toSet(),
            collapsedSegments = indicesOf(closures.collapsed),
            detourSegments = indicesOf(closures.detour),
            weightLimitedSegments = indicesOf(closures.playerWeightLimit),
            playerWorksSegments = indicesOf(closures.playerWorks),
            playerClosedSegments = indicesOf(closures.playerClosed),
        ).also { cached = it }

    companion object {
        const val POTHOLES = 0.6
        const val CRATER = 0.35
        const val SIGNAL_FAILURE = 0.3
        const val BRIDGE_WEIGHT = 0.5
        const val BRIDGE_UNSAFE = 0.25
        const val BRIDGE_COLLAPSE = 0.1

        private const val PASSIVE_CONDITION = 0.85
        private const val POTHOLE_SPEED = 0.85f
        private const val CRATER_SPEED = 0.65f
        private const val WORKS_SPEED = 0.5f

        /** Baustelle des Spielers: verengte Fahrbahn, aber keine Bagger auf der Straße. */
        private const val PLAYER_WORKS_SPEED = 0.75f
        private const val POTHOLE_HAZARD = 1.5
        private const val CRATER_HAZARD = 3.0
        private const val SECONDS_PER_DAY = 86_400.0
        private const val SNAPSHOT_STEP = 0.05
    }
}

/** Startzustand: 1 − (1 − min) · u^Schiefe – die meisten Straßen sind anfangs gut in Schuss. */
internal fun initialCondition(
    random: Random,
    config: SimulationConfig,
): Double = 1 - (1 - config.initialConditionMin) * random.nextDouble().pow(config.initialConditionSkew)

private fun indicesOf(flags: BooleanArray): Set<Int> = flags.indices.filter { flags[it] }.toSet()

private fun crossed(
    before: Double,
    after: Double,
    threshold: Double,
) = before >= threshold && after < threshold

/** Schadensäquivalent nach der Faustregel "Achslast hoch 4", bezogen auf eine 10-t-Achse. */
internal val VehicleType.equivalentAxleLoads: Double
    get() = axles * (massKg / axles / STANDARD_AXLE_KG).pow(FOURTH_POWER)

private const val STANDARD_AXLE_KG = 10_000.0
private const val FOURTH_POWER = 4
