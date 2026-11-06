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
 *
 * Feste Blitzer messen beide Fahrtrichtungen. Ortskundige kennen sie und bremsen davor deutlich ab.
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

    /** Alle aufgestellten Blitzer. */
    val cameras: List<SpeedCamera> get() = cameraList

    /** Stellt einen Blitzer in die Mitte von [segment]. */
    fun placeCamera(segment: Int): SpeedCamera {
        val links = graph.segmentLinks[segment]
        val point = FloatArray(LOCATE_SIZE)
        graph.locate(links.first(), graph.linkLength[links.first()] / 2, 0, point)
        val camera = SpeedCamera(nextCameraId++, segment, point[LOCATE_X], point[LOCATE_Y])
        cameraList += camera
        for (link in links) sites[link] += CameraSite(camera, graph.linkLength[link] / 2.0)
        return camera
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

    /** Würfelt beim Einfahren in [link], ob und wie viel [vehicle] zu schnell fährt. */
    internal fun roll(
        vehicle: Vehicle,
        link: Int,
    ) {
        val gap = max(0f, graph.linkPerceivedSpeedKmh[link] - graph.speedLimitKmh(link)).toDouble()
        val probability =
            min(
                MAX_PROBABILITY,
                (config.speedingBaseProbability + gap / GAP_SCALE_KMH * GAP_WEIGHT) * vehicle.driver.speedingFactor,
            )
        vehicle.speedingKmh =
            if (random.nextDouble() < probability) {
                random.nextDouble(MIN_EXCESS_KMH, MAX_BASE_EXCESS_KMH) +
                    gap * random.nextDouble(MIN_GAP_SHARE, MAX_GAP_SHARE)
            } else {
                0.0
            }
    }

    /** Wunschgeschwindigkeit in m/s auf [link]: Limit, Fahrertyp, Überschreitung und Bremsen vor Blitzern. */
    internal fun desiredSpeed(
        vehicle: Vehicle,
        link: Int,
    ): Double {
        val limit = graph.linkSpeedLimit[link].toDouble()
        var desired =
            if (vehicle.speedingKmh >
                0
            ) {
                limit + vehicle.speedingKmh / KMH_PER_MS
            } else {
                limit * vehicle.driver.speedFactor
            }
        if (vehicle.knowsCameras && sites[link].any { it.position - vehicle.position in 0.0..CAMERA_BRAKE_ZONE }) {
            desired = min(desired, limit * CAMERA_BRAKE_FACTOR)
        }
        return min(desired, vehicle.type.maxSpeed)
    }

    /** Prüft, ob [vehicle] auf [link] bei der Fahrt von [oldPosition] aus einen Blitzer passiert hat. */
    internal fun measure(
        vehicle: Vehicle,
        link: Int,
        oldPosition: Double,
    ) {
        val list = sites[link]
        if (list.isEmpty()) return
        val limitKmh = graph.speedLimitKmh(link).toInt()
        for (site in list) {
            if (oldPosition < site.position && vehicle.position >= site.position) {
                val measured = vehicle.speed * KMH_PER_MS
                val excess = measured - TOLERANCE_KMH - limitKmh
                if (excess >
                    0
                ) {
                    violations +=
                        SpeedViolation(site.camera.id, vehicle.id, vehicle.homeZone, measured, limitKmh, excess)
                }
            }
        }
    }

    private companion object {
        const val GAP_SCALE_KMH = 40.0
        const val GAP_WEIGHT = 0.8
        const val MAX_PROBABILITY = 0.92
        const val MIN_EXCESS_KMH = 3.0
        const val MAX_BASE_EXCESS_KMH = 10.0
        const val MIN_GAP_SHARE = 0.2
        const val MAX_GAP_SHARE = 0.5

        /** Messtoleranz innerorts. */
        const val TOLERANCE_KMH = 3.0

        /** So weit vor dem Blitzer bremsen Ortskundige ab. */
        const val CAMERA_BRAKE_ZONE = 120.0

        /** ... und zwar auf diesen Anteil des Limits ("panisches" Bremsen). */
        const val CAMERA_BRAKE_FACTOR = 0.85
    }
}
