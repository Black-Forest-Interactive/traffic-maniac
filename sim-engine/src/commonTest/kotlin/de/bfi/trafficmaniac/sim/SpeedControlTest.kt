package de.bfi.trafficmaniac.sim

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeedControlTest {
    private val graph = RoadGraph(crossroads().roads)
    private val config = SimulationConfig()
    private val control = SpeedControl(graph, config, Random(5))

    private fun vehicle(driver: DriverProfile = DriverProfile.NORMALO) =
        Vehicle(
            0,
            VehicleType.CAR,
            driver,
            0,
            intArrayOf(0),
            0,
            0.0,
            knowsCameras = false,
            homeZone = -1,
            person = -1,
            referenceSeconds = 0.0,
        )

    /** Anteil der Fahrer, die auf [link] zu schnell fahren. */
    private fun speedingShare(
        link: Int,
        driver: DriverProfile = DriverProfile.NORMALO,
    ): Double {
        val samples = 4_000
        val v = vehicle(driver)
        return (1..samples)
            .count {
                control.roll(v, link)
                v.speedingKmh > 0
            }.toDouble() / samples
    }

    @Test
    fun lowerLimitsMeanMoreSpeeding() {
        val link = graph.segmentLinks[0].first()
        val at50 = speedingShare(link)
        graph.setSegmentSpeedLimit(0, 30)
        val at30 = speedingShare(link)
        graph.setSegmentSpeedLimit(0, 20)
        val at20 = speedingShare(link)

        assertEquals(config.speedingBaseProbability, at50, 0.02)
        // Schild übersehen (Normalo: 85 % aufmerksam → 7,5 %) oder zu schnell: 15 % plus die „Straßenwirkung“ –
        // Tempo 30 auf einer 50er-Straße ergibt wie bei echten Messungen in Tempo-30-Zonen gut 30 %
        assertEquals(0.35, at30, 0.04)
        assertEquals(0.42, at20, 0.04)
    }

    @Test
    fun driverTypeMatters() {
        graph.setSegmentSpeedLimit(0, 30)
        val link = graph.segmentLinks[0].first()

        assertTrue(speedingShare(link, DriverProfile.DRAENGLER) > speedingShare(link, DriverProfile.GEMUETLICHER) * 2)
    }

    @Test
    fun cameraMeasuresOnlyWhenPassing() {
        graph.setSegmentSpeedLimit(0, 20)
        val camera = control.placeCamera(0)
        val link = graph.segmentLinks[0].first()
        val v = vehicle()
        v.speed = 50 / KMH_PER_MS
        v.position = graph.linkLength[link] / 2.0 + 1

        control.measure(v, link, oldPosition = graph.linkLength[link] / 2.0 - 1)
        control.measure(v, link, oldPosition = graph.linkLength[link] / 2.0 + 0.5)

        val violation = control.drainViolations().single()
        assertEquals(camera.id, violation.cameraId)
        assertEquals(27.0, violation.excessKmh, 0.01)
        assertTrue(control.drainViolations().isEmpty())
    }
}
