package de.bfi.trafficmaniac.sim

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SimulationTest {
    private val config = SimulationConfig(seed = 7, tripsPerHour = 1_200.0)

    private fun Simulation.run(seconds: Int) = repeat(seconds * config.ticksPerSecond) { step() }

    @Test
    fun vehiclesDriveAndArrive() {
        val simulation = Simulation(crossroads(), config)

        simulation.run(seconds = 600)

        val stats = simulation.snapshot().stats
        assertTrue(stats.completedTrips > 50, "angekommen: ${stats.completedTrips}")
        assertTrue(stats.activeVehicles > 0)
        assertTrue(stats.stuckRemovals == 0, "aufgegeben: ${stats.stuckRemovals}")
    }

    @Test
    fun sameSeedGivesSameResult() {
        val a = Simulation(crossroads(), config).apply { run(seconds = 120) }.snapshot()
        val b = Simulation(crossroads(), config).apply { run(seconds = 120) }.snapshot()

        assertContentEquals(a.ids, b.ids)
        assertContentEquals(a.x, b.x)
        assertContentEquals(a.y, b.y)
    }

    @Test
    fun vehiclesNeverOverlapOnALane() {
        val simulation = Simulation(crossroads(), config.copy(tripsPerHour = 3_000.0))
        repeat(300) {
            simulation.run(seconds = 1)
            val snapshot = simulation.snapshot()
            // Fahrzeuge einer Richtung und Spur: Fronten liegen mindestens eine Fahrzeuglänge auseinander.
            // Die Kreuzung ist in der Simulation ein Punkt – dort liegen Abbieger und Geradeausfahrer übereinander.
            val fronts =
                (0 until snapshot.vehicleCount)
                    .filter { hypot(snapshot.x[it], snapshot.y[it]) > JUNCTION_RADIUS }
                    .groupBy { (snapshot.dirX[it] * 10).toInt() to (snapshot.dirY[it] * 10).toInt() }
            for (indices in fronts.values) {
                val sorted =
                    indices.sortedBy {
                        snapshot.x[it] * snapshot.dirX[it] + snapshot.y[it] * snapshot.dirY[it]
                    }
                for (k in 1 until sorted.size) {
                    val behind = sorted[k - 1]
                    val ahead = sorted[k]
                    if (onSameLine(snapshot, behind, ahead)) {
                        val distance =
                            hypot(
                                snapshot.x[ahead] - snapshot.x[behind],
                                snapshot.y[ahead] - snapshot.y[behind],
                            )
                        assertTrue(
                            distance >= snapshot.lengths[ahead],
                            "Überlappung bei t=${snapshot.time}: $distance m",
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val JUNCTION_RADIUS = 20f
    }

    /** Gleiche Richtung und gleiche Spur (seitlicher Versatz < 1 m). */
    private fun onSameLine(
        s: SimulationSnapshot,
        a: Int,
        b: Int,
    ): Boolean {
        val dx = s.x[b] - s.x[a]
        val dy = s.y[b] - s.y[a]
        val lateral = abs(dx * s.dirY[a] - dy * s.dirX[a])
        return lateral < 1f
    }
}
