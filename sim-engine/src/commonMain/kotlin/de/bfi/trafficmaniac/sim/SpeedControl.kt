package de.bfi.trafficmaniac.sim

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Geschwindigkeit und Überwachung: wer wie schnell fahren will, wer zu schnell ist und wer geblitzt wird.
 *
 * Zu schnell fährt, wem das Limit unpassend niedrig vorkommt: Je weiter es unter dem Tempo liegt, das sich
 * auf der Straße "richtig anfühlt" (dem ursprünglichen Limit), desto mehr Fahrer überschreiten es – und desto
 * deutlicher. Grobe Werte auf einer 50er-Straße: Limit 50 → ~8 %, 30 → ~48 %, 20 → ~68 %, Schritttempo → ~90 %.
 * Dazu kommen Fahrer, die das Schild schlicht übersehen.
 *
 * Feste Blitzer messen beide Fahrtrichtungen. Ortskundige kennen sie und bremsen davor deutlich ab;
 * mobile Lasermessungen kennt niemand.
 */
class SpeedControl internal constructor(
    private val graph: RoadGraph,
    private val config: SimulationConfig,
    private val random: Random,
) {
    private val sites = Array(graph.linkCount) { ArrayList<CameraSite>() }
    private val cameraList = ArrayList<SpeedCamera>()
    private val violations = ArrayList<SpeedViolation>()
    private var nextCameraId = 0

    /** Messtoleranz in km/h (einstellbar über die Schärfe des Ordnungsamts). */
    var toleranceKmh = DEFAULT_TOLERANCE_KMH

    /** Alle aufgestellten Blitzer und Lasermessungen. */
    val cameras: List<SpeedCamera> get() = cameraList

    /** Stellt einen Blitzer (oder mit [mobile] eine Lasermessung) in die Mitte von [segment]. */
    fun placeCamera(
        segment: Int,
        mobile: Boolean = false,
        trailer: Boolean = false,
    ): SpeedCamera {
        val links = graph.segmentLinks[segment]
        val point = FloatArray(LOCATE_SIZE)
        graph.locate(links.first(), graph.linkLength[links.first()] / 2, 0, point)
        val camera = SpeedCamera(nextCameraId++, segment, point[LOCATE_X], point[LOCATE_Y], mobile || trailer, trailer)
        cameraList += camera
        for (link in links) sites[link] += CameraSite(camera, graph.linkLength[link] / 2.0)
        return camera
    }

    /**
     * Rotlichtblitzer an der Haltelinie von [link] (eine Ampelzufahrt); mit [withSpeed] misst er auch das Tempo
     * (kurz vor der Haltelinie).
     */
    fun placeRedLightCamera(
        link: Int,
        withSpeed: Boolean,
    ): SpeedCamera {
        val point = FloatArray(LOCATE_SIZE)
        val position = (graph.linkLength[link] - STOP_LINE_SETBACK).coerceAtLeast(0f)
        graph.locate(link, position, 0, point)
        val camera =
            SpeedCamera(
                nextCameraId++,
                graph.linkSegment[link],
                point[LOCATE_X],
                point[LOCATE_Y],
                redLight = true,
                measuresSpeed = withSpeed,
                link = link,
            )
        cameraList += camera
        sites[link] += CameraSite(camera, position.toDouble())
        return camera
    }

    /** Geschwindigkeits-Anzeigetafel in der Mitte von [segment]: misst beide Richtungen, blitzt nicht. */
    fun placeDisplay(segment: Int): SpeedCamera {
        val links = graph.segmentLinks[segment]
        val point = FloatArray(LOCATE_SIZE)
        graph.locate(links.first(), graph.linkLength[links.first()] / 2, 0, point)
        val camera =
            SpeedCamera(nextCameraId++, segment, point[LOCATE_X], point[LOCATE_Y], mobile = true, display = true)
        cameraList += camera
        for (link in links) sites[link] += CameraSite(camera, graph.linkLength[link] / 2.0)
        return camera
    }

    /**
     * [vehicle] überquert die Haltelinie am Ende von [link]; [redSeconds] = Sekunden seit Beginn der Rotphase
     * (NaN bei Grün oder Gelb). Steht dort ein Rotlichtblitzer, gibt es ein Foto.
     */
    internal fun stopLine(
        vehicle: Vehicle,
        link: Int,
        redSeconds: Double,
    ) {
        if (redSeconds.isNaN() || vehicle.role != VehicleRole.PRIVATE) return
        for (site in sites[link]) {
            if (!site.camera.redLight) continue
            violations +=
                SpeedViolation(
                    site.camera.id,
                    vehicle.id,
                    vehicle.homeZone,
                    vehicle.person,
                    vehicle.speed * KMH_PER_MS,
                    graph.speedLimitKmh(link).toInt(),
                    0.0,
                    redSeconds = redSeconds,
                )
        }
    }

    fun removeCamera(id: Int) {
        cameraList.removeAll { it.id == id }
        for (list in sites) list.removeAll { it.camera.id == id }
    }

    /** Liefert die Messungen seit dem letzten Aufruf und leert den Puffer. */
    fun drainViolations(): List<SpeedViolation> {
        val result = violations.toList()
        violations.clear()
        return result
    }

    /**
     * Würfelt beim Einfahren in [link], ob und wie viel [vehicle] zu schnell fährt.
     * Wer das Schild übersieht (je unaufmerksamer, desto eher), fährt einfach, was sich richtig anfühlt.
     * Frust und Stress machen die Überschreitung wahrscheinlicher.
     */
    internal fun roll(
        vehicle: Vehicle,
        link: Int,
    ) {
        val gap = max(0f, graph.linkPerceivedSpeedKmh[link] - graph.speedLimitKmh(link)).toDouble()
        val overlooked = gap > 0 && random.nextDouble() < (1 - vehicle.attention) * config.overlookProbability
        val tendency = vehicle.driver.speedingFactor * (1 + vehicle.irritation)
        val probability =
            min(
                MAX_PROBABILITY,
                (config.speedingBaseProbability + gap / GAP_SCALE_KMH * GAP_WEIGHT) * tendency,
            )
        vehicle.speedingKmh =
            when {
                overlooked -> gap + random.nextDouble(0.0, MAX_BASE_EXCESS_KMH)
                random.nextDouble() < probability -> excess(gap)
                else -> 0.0
            }
    }

    /** Überschreitung nach echten Messungen ([SpeedingProfile]); breite Straßen verleiten zu etwas mehr. */
    private fun excess(gap: Double) = SpeedingProfile.sample(random) + gap * random.nextDouble(0.0, MAX_GAP_SHARE)

    /**
     * Reizdichte beim Einfahren in [link] (von [previous] kommend): Ampel, Tempowechsel, Nachtlimit und
     * Blitzer kosten Aufmerksamkeit – auf kurzen Abschnitten überproportional ("Schilderwald").
     */
    internal fun stimulus(
        previous: Int,
        link: Int,
    ): Double {
        var stimuli = 0.0
        if (graph.nodeHasSignals[graph.linkTo[link]]) stimuli += SIGNAL_STIMULUS
        if (previous >= 0 &&
            graph.linkSpeedLimit[previous] != graph.linkSpeedLimit[link]
        ) {
            stimuli += LIMIT_CHANGE_STIMULUS
        }
        if (graph.hasNightLimit(link)) stimuli += NIGHT_LIMIT_STIMULUS
        if (sites[link].isNotEmpty()) stimuli += CAMERA_STIMULUS
        if (graph.hasBikeCrossing(link)) stimuli += BIKE_CROSSING_STIMULUS
        if (previous >= 0 && graph.hasPriority(previous) != graph.hasPriority(link)) stimuli += PRIORITY_CHANGE_STIMULUS
        val density = (DENSITY_REFERENCE_METERS / graph.linkLength[link]).coerceIn(1f, MAX_DENSITY)
        return stimuli * density
    }

    /** Wunschgeschwindigkeit in m/s auf [link]: Limit, Fahrertyp, Überschreitung, Blitzer und Kreuzungen. */
    internal fun desiredSpeed(
        vehicle: Vehicle,
        link: Int,
    ): Double {
        val limit = graph.linkSpeedLimit[link].toDouble()
        if (vehicle.role.isEmergency) return min(limit * EMERGENCY_SPEED_FACTOR, vehicle.type.maxSpeed)
        if (vehicle.role.isEvent) return min(limit, vehicle.type.maxSpeed)
        val speeding = vehicle.speedingKmh > 0
        var desired = if (speeding) limit + vehicle.speedingKmh / KMH_PER_MS else limit * vehicle.driver.speedFactor
        desired *= graph.speedFactor(link)
        val fixedCameraAhead =
            sites[link].any {
                !it.camera.mobile &&
                    it.camera.measuresSpeed &&
                    it.position - vehicle.position in 0.0..CAMERA_BRAKE_ZONE
            }
        if (vehicle.knowsCameras && fixedCameraAhead) desired = min(desired, limit * CAMERA_BRAKE_FACTOR)
        // Vor einer Anzeigetafel nimmt mancher Raser den Fuß vom Gas
        val displayAhead = sites[link].any { it.camera.display && it.position - vehicle.position in 0.0..DISPLAY_ZONE }
        if (displayAhead && speeding) desired = min(desired, limit + vehicle.speedingKmh * DISPLAY_EFFECT / KMH_PER_MS)
        // Unsichere werden vor Kreuzungen langsamer
        if (graph.linkLength[link] - vehicle.position < JUNCTION_ZONE) desired *= vehicle.driver.junctionCaution
        return min(min(desired, vehicle.type.maxSpeed), graph.linkEventSpeedCap[link].toDouble())
    }

    /** Prüft, ob [vehicle] auf [link] bei der Fahrt von [oldPosition] aus einen Blitzer passiert hat. */
    internal fun measure(
        vehicle: Vehicle,
        link: Int,
        oldPosition: Double,
    ) {
        val list = sites[link]
        if (list.isEmpty() || vehicle.role != VehicleRole.PRIVATE) return
        val limitKmh = graph.speedLimitKmh(link).toInt()
        for (site in list) {
            if (oldPosition < site.position && vehicle.position >= site.position) {
                val measured = vehicle.speed * KMH_PER_MS
                val camera = site.camera
                if (camera.display) {
                    camera.passed++
                    if (measured > limitKmh) camera.speeding++
                    continue
                }
                if (!camera.measuresSpeed) continue
                val excess = measured - toleranceKmh - limitKmh
                if (excess >
                    0
                ) {
                    violations +=
                        SpeedViolation(
                            site.camera.id,
                            vehicle.id,
                            vehicle.homeZone,
                            vehicle.person,
                            measured,
                            limitKmh,
                            excess,
                        )
                }
            }
        }
    }

    companion object {
        /** Messtoleranz innerorts. */
        const val DEFAULT_TOLERANCE_KMH = 3.0

        private const val GAP_SCALE_KMH = 40.0

        /** Tempo 30 auf einer Straße, die nach 50 aussieht: +15 Prozentpunkte (Höhrenbergstraße, Hegne: 25–30 %). */
        private const val GAP_WEIGHT = 0.3
        private const val MAX_PROBABILITY = 0.6
        private const val MAX_BASE_EXCESS_KMH = 10.0
        private const val MAX_GAP_SHARE = 0.25

        /** So weit vor dem Blitzer bremsen Ortskundige ab ... */
        private const val CAMERA_BRAKE_ZONE = 120.0

        /** ... und zwar auf diesen Anteil des Limits ("panisches" Bremsen). */
        private const val CAMERA_BRAKE_FACTOR = 0.85
        private const val JUNCTION_ZONE = 40.0

        /** Rotlichtblitzer: so weit vor dem Ende der Zufahrt steht er. */
        private const val STOP_LINE_SETBACK = 3f

        /** Anzeigetafel: so weit vorher sieht man sie … */
        private const val DISPLAY_ZONE = 150.0

        /** … und Raser fahren dann nur noch mit diesem Anteil ihrer Überschreitung. */
        private const val DISPLAY_EFFECT = 0.5
        private const val EMERGENCY_SPEED_FACTOR = 1.3

        private const val SIGNAL_STIMULUS = 0.03
        private const val LIMIT_CHANGE_STIMULUS = 0.06
        private const val NIGHT_LIMIT_STIMULUS = 0.04
        private const val CAMERA_STIMULUS = 0.03
        private const val BIKE_CROSSING_STIMULUS = 0.05
        private const val PRIORITY_CHANGE_STIMULUS = 0.05

        /** Reize auf weniger als 100 m zählen mehrfach, höchstens dreifach. */
        private const val DENSITY_REFERENCE_METERS = 100f
        private const val MAX_DENSITY = 3f
    }
}
