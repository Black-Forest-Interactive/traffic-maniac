package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.StreetStretches
import de.bfi.trafficmaniac.city.ZoneKind
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.DamageKind
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.IncidentMarker
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Miniaufgaben (v0.9 Paket 4): Bürger, Polizei und Vereine wollen etwas – der Spieler erledigt es mit den vorhandenen
 * Werkzeugen und bekommt sofort Geld. Ironie inklusive: Das Land zahlt für das Schild, nicht für die Wirkung.
 */
enum class AssignmentKind(
    private val titleText: String,
    private val clientText: String,
) {
    TEMPO_30(translatable("Tempo 30 gewünscht"), translatable("Anwohnerinitiative")),
    ACCIDENT_SPOT(translatable("Unfallschwerpunkt"), translatable("Polizeidirektion")),
    POTHOLE(translatable("Schlagloch-Beschwerde"), translatable("Anwohner")),
    SPEEDER(translatable("Raser-Beschwerde"), translatable("Bürgerverein")),
    ;

    val title: String get() = tr(titleText)
    val client: String get() = tr(clientText)
}

enum class AssignmentStatus { OPEN, DONE, MISSED, DECLINED }

/** Ein Auftrag, wie die Oberfläche ihn zeigt. */
data class Assignment(
    val id: Int,
    val kind: AssignmentKind,
    val street: String,
    val text: String,
    val reward: Long,
    val place: MapPlace,
    val segments: Set<Int>,
    val hoursLeft: Double,
    /** Gesamte Frist in Stunden (für den Fristbalken). */
    val hours: Double,
    val status: AssignmentStatus,
    /** Schadensmeldung, um die es geht (Schlagloch-Beschwerde), sonst -1. */
    val reportId: Int = -1,
)

/** Wie die Spielwelt für die Aufträge aussieht – einmal je Spielstunde. */
internal class AssignmentWorld(
    val state: GameState,
    val incidents: List<IncidentMarker>,
    val time: Double,
    val gameTime: GameTime,
    val firstDay: Boolean,
)

/** Was die Aufträge im Spiel auslösen. */
internal interface AssignmentEffects {
    fun reward(euros: Long)

    fun reputation(change: Double)

    fun announce(
        text: String,
        place: MapPlace?,
        kind: HighlightKind,
        title: String,
    )
}

internal class Assignments(
    private val city: CityMap,
    private val rules: GameRules,
    private val effects: AssignmentEffects,
    seed: Long,
) {
    private val random = Random(seed + RANDOM_OFFSET)
    private val cases = ArrayList<AssignmentCase>()
    private var nextId = 1
    private val declinedUntil = HashMap<AssignmentKind, Double>()
    private val seenAccidents = HashSet<Double>()

    /** Benannte, simulierte Abschnitte je Straße. */
    private val streets: Map<String, List<Int>> =
        city.roads.segments
            .filter { !it.passive && it.name != null }
            .groupBy({ it.name!! }, { it.id })

    private val stretches = StreetStretches(city.roads)

    private val homes = city.zones.filter { it.kind == ZoneKind.RESIDENTIAL }

    /** Straßen mit Abschnitten nahe an einem Wohngebiet (für „Tempo 30 gewünscht“). */
    private val nearHomes: Map<String, List<Int>> =
        streets
            .mapValues { (_, segments) ->
                segments.filter { segment ->
                    val p = city.placeOfSegment(segment)
                    homes.any { hypot(it.x - p.x, it.y - p.y) < HOME_RADIUS_METERS }
                }
            }.filterValues { it.isNotEmpty() }

    fun state(time: Double): List<Assignment> =
        cases.filter { it.status == AssignmentStatus.OPEN }.map { it.toAssignment(time) }

    /** Erledigte Aufträge (für Tests und Statistik). */
    val doneCount: Int get() = cases.count { it.status == AssignmentStatus.DONE }

    fun decline(
        id: Int,
        time: Double,
    ) {
        val case = cases.firstOrNull { it.id == id && it.status == AssignmentStatus.OPEN } ?: return
        case.status = AssignmentStatus.DECLINED
        declinedUntil[case.kind] = time + DECLINE_PAUSE_SECONDS
    }

    /** Jede Spielstunde: Erfüllte bezahlen, Abgelaufene verbuchen, neue Aufträge verteilen. */
    fun hourPassed(world: AssignmentWorld) {
        for (case in cases.filter { it.status == AssignmentStatus.OPEN }) {
            when {
                case.fulfilled(world) -> complete(case)
                world.time > case.deadline -> miss(case)
            }
        }
        cases.removeAll { it.status != AssignmentStatus.OPEN && world.time - it.created > FORGET_SECONDS }
        if (world.gameTime.hour !in FIRST_HOUR..LAST_HOUR) return
        val rate = rules.assignments.perHour * if (world.firstDay) rules.assignments.firstDayFactor else 1.0
        var count = rate.toInt() + if (random.nextDouble() < rate % 1.0) 1 else 0
        while (count-- > 0 && cases.count { it.status == AssignmentStatus.OPEN } < rules.assignments.maxOpen) {
            create(world) ?: break
        }
    }

    private fun create(world: AssignmentWorld): AssignmentCase? {
        val kinds =
            AssignmentKind.entries
                .filter { (declinedUntil[it] ?: 0.0) < world.time }
                .shuffled(random)
        for (kind in kinds) {
            val case = candidate(kind, world) ?: continue
            cases += case
            effects.announce(
                tr(
                    "Neuer Auftrag: {title} – {street} (+{reward})",
                    "title" to kind.title,
                    "street" to case.street,
                    "reward" to formatEuro(case.reward),
                ),
                case.place,
                HighlightKind.MEDIUM,
                tr("Neuer Auftrag: {title}", "title" to kind.title),
            )
            return case
        }
        return null
    }

    private fun candidate(
        kind: AssignmentKind,
        world: AssignmentWorld,
    ): AssignmentCase? {
        val busyStreets = cases.filter { it.status == AssignmentStatus.OPEN }.map { it.street }.toSet()
        return when (kind) {
            AssignmentKind.TEMPO_30 -> tempo30(world, busyStreets)
            AssignmentKind.ACCIDENT_SPOT -> accidentSpot(world, busyStreets)
            AssignmentKind.POTHOLE -> pothole(world)
            AssignmentKind.SPEEDER -> speeder(world, busyStreets)
        }
    }

    /** Nur eine Strecke (ein Schild), sonst kostet der Auftrag mehr Schilder, als er einbringt. */
    private fun tempo30(
        world: AssignmentWorld,
        busyStreets: Set<String>,
    ): AssignmentCase? {
        val options =
            nearHomes.filter { (street, segments) ->
                street !in busyStreets && segments.any { limit(world.state, it) > TEMPO_30 }
            }
        val street = options.keys.randomOrNull(random) ?: return null
        val start = nearHomes.getValue(street).filter { limit(world.state, it) > TEMPO_30 }.random(random)
        return case(
            AssignmentKind.TEMPO_30,
            street,
            stretches.stretchOf(start).toSet(),
            rules.assignments.rewardTempo30,
            rules.assignments.hoursTempo30,
            world,
        )
    }

    private fun accidentSpot(
        world: AssignmentWorld,
        busyStreets: Set<String>,
    ): AssignmentCase? {
        val accident =
            world.incidents.firstOrNull { it.kind == IncidentKind.ACCIDENT && it.startTime !in seenAccidents }
                ?: return null
        seenAccidents += accident.startTime
        val street = nearestStreet(accident.x, accident.y)?.takeIf { it !in busyStreets } ?: return null
        return case(
            AssignmentKind.ACCIDENT_SPOT,
            street,
            streets.getValue(street).toSet(),
            rules.assignments.rewardAccident,
            rules.assignments.hoursAccident,
            world,
        )
    }

    private fun pothole(world: AssignmentWorld): AssignmentCase? {
        val taken = cases.filter { it.status == AssignmentStatus.OPEN }.map { it.reportId }.toSet()
        val report =
            world.state.maintenance.reports.firstOrNull {
                it.kind == DamageKind.POTHOLES && it.status == DamageStatus.OPEN && it.id !in taken &&
                    it.segments.isNotEmpty()
            } ?: return null
        val street = city.roads.segments[report.segment].name ?: report.place
        return case(
            AssignmentKind.POTHOLE,
            street,
            report.segments,
            rules.assignments.rewardPothole,
            rules.assignments.hoursPothole,
            world,
            report.id,
        )
    }

    private fun speeder(
        world: AssignmentWorld,
        busyStreets: Set<String>,
    ): AssignmentCase? {
        val options =
            streets.filter { (street, segments) ->
                street !in busyStreets && segments.any { limit(world.state, it) >= SPEEDER_MIN_KMH }
            }
        val street = options.keys.randomOrNull(random) ?: return null
        return case(
            AssignmentKind.SPEEDER,
            street,
            streets.getValue(street).toSet(),
            rules.assignments.rewardSpeeder,
            rules.assignments.hoursSpeeder,
            world,
        )
    }

    private fun case(
        kind: AssignmentKind,
        street: String,
        segments: Set<Int>,
        reward: Long,
        hours: Double,
        world: AssignmentWorld,
        reportId: Int = -1,
    ) = AssignmentCase(
        id = nextId++,
        kind = kind,
        street = street,
        segments = segments,
        place = city.placeOfSegments(segments) ?: MapPlace(0f, 0f),
        reward = reward,
        created = world.time,
        deadline = world.time + hours * SECONDS_PER_HOUR,
        hours = hours,
        reportId = reportId,
    )

    private fun AssignmentCase.fulfilled(world: AssignmentWorld): Boolean {
        val cameras = world.state.cameras
        return when (kind) {
            AssignmentKind.TEMPO_30 -> {
                segments.all { limit(world.state, it) <= TEMPO_30 }
            }

            AssignmentKind.ACCIDENT_SPOT -> {
                cameras.any { it.segment in segments }
            }

            AssignmentKind.POTHOLE -> {
                val report =
                    world.state.maintenance.reports
                        .firstOrNull { it.id == reportId }
                report == null || report.status == DamageStatus.TENDER || report.status == DamageStatus.CONSTRUCTION
            }

            AssignmentKind.SPEEDER -> {
                if (cameras.any { (it.policeCheck || it.mobile) && it.segment in segments }) checkedHours++
                checkedHours >= rules.assignments.speederCheckHours
            }
        }
    }

    private fun complete(case: AssignmentCase) {
        case.status = AssignmentStatus.DONE
        effects.reward(case.reward)
        if (case.kind == AssignmentKind.POTHOLE) effects.reputation(rules.assignments.potholeReputation)
        effects.announce(
            tr(
                "Auftrag erfüllt: {title} – {street}, +{reward}",
                "title" to case.kind.title,
                "street" to case.street,
                "reward" to formatEuro(case.reward),
            ),
            case.place,
            HighlightKind.BIG,
            tr("Auftrag erfüllt: +{reward}", "reward" to formatEuro(case.reward)),
        )
    }

    private fun miss(case: AssignmentCase) {
        case.status = AssignmentStatus.MISSED
        effects.reputation(-rules.assignments.missedReputationLoss)
        effects.announce(
            tr("Auftrag verpasst: {title} – {street}", "title" to case.kind.title, "street" to case.street),
            null,
            HighlightKind.WARNING,
            tr("Auftrag verpasst: {title}", "title" to case.kind.title),
        )
    }

    private fun limit(
        state: GameState,
        segment: Int,
    ): Int = state.speedLimits[segment] ?: city.roads.segments[segment].maxSpeedKmh

    private fun nearestStreet(
        x: Float,
        y: Float,
    ): String? =
        streets.entries
            .flatMap { (street, segments) -> segments.map { street to city.placeOfSegment(it) } }
            .minByOrNull { (_, p) -> hypot(p.x - x, p.y - y) }
            ?.takeIf { (_, p) -> hypot(p.x - x, p.y - y) < ACCIDENT_RADIUS_METERS }
            ?.first

    private inner class AssignmentCase(
        val id: Int,
        val kind: AssignmentKind,
        val street: String,
        val segments: Set<Int>,
        val place: MapPlace,
        val reward: Long,
        val created: Double,
        val deadline: Double,
        val hours: Double,
        val reportId: Int,
    ) {
        var status = AssignmentStatus.OPEN
        var checkedHours = 0

        fun toAssignment(time: Double) =
            Assignment(
                id = id,
                kind = kind,
                street = street,
                text = text(),
                reward = reward,
                place = place,
                segments = segments,
                hoursLeft = (deadline - time) / SECONDS_PER_HOUR,
                hours = hours,
                status = status,
                reportId = reportId,
            )

        private fun text(): String =
            when (kind) {
                AssignmentKind.TEMPO_30 -> {
                    tr(
                        "„Vor unseren Häusern wird gerast.“ Tempo 30 auf der {street} – das Land gibt einen Zuschuss.",
                        "street" to street,
                    )
                }

                AssignmentKind.ACCIDENT_SPOT -> {
                    tr(
                        "„Schon wieder ein Unfall auf der {street}.“ Blitzer, Tafel oder Kontrolle dorthin.",
                        "street" to street,
                    )
                }

                AssignmentKind.POTHOLE -> {
                    tr(
                        "„Das Schlagloch auf der {street} frisst Felgen.“ Flicken oder reparieren lassen.",
                        "street" to street,
                    )
                }

                AssignmentKind.SPEEDER -> {
                    tr(
                        "„Nachts wird auf der {street} gerast.“ Zwei Stunden Polizeikontrolle oder Laser.",
                        "street" to street,
                    )
                }
            }
    }

    private companion object {
        const val RANDOM_OFFSET = 4_711L
        const val FIRST_HOUR = 6
        const val LAST_HOUR = 20
        const val TEMPO_30 = 30
        const val SPEEDER_MIN_KMH = 50
        const val HOME_RADIUS_METERS = 250f
        const val ACCIDENT_RADIUS_METERS = 200f
        const val SECONDS_PER_HOUR = 3_600.0
        const val DECLINE_PAUSE_SECONDS = 24 * 3_600.0
        const val FORGET_SECONDS = 48 * 3_600.0
    }
}
