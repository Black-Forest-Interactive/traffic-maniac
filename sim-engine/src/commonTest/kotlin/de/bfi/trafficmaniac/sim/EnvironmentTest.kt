package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Bounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentTest {
    private fun vehicle(type: VehicleType = VehicleType.CAR) =
        Vehicle(0, type, DriverProfile.NORMALO, 0, intArrayOf(0), 0, 0.0, false, -1, -1, 0.0)

    /** Ausstoß über [seconds] Sekunden mit wechselnden Zuständen (Tempo, Beschleunigung). */
    private fun emitted(
        type: VehicleType,
        seconds: Int,
        state: (Int) -> Pair<Double, Double>,
    ): DoubleArray {
        val environment = Environment(Bounds(0f, 0f, 100f, 100f))
        val v = vehicle(type)
        repeat(seconds * 10) { tick ->
            val (speed, acceleration) = state(tick / 10)
            v.speed = speed
            v.acceleration = acceleration
            environment.emit(v, 0.1)
        }
        return v.pendingEmissions.copyOf()
    }

    @Test
    fun stopAndGoIsDirtierThanSteadyDriving() {
        // Beide fahren im Schnitt 7 m/s über eine Minute
        val steady = emitted(VehicleType.CAR, 60) { 7.0 to 0.0 }
        val stopAndGo =
            emitted(VehicleType.CAR, 60) { s ->
                if (s % 10 <
                    5
                ) {
                    (s % 10) * 2.8 to 2.8
                } else {
                    (10 - s % 10) * 2.8 to -2.8
                }
            }

        assertTrue(stopAndGo[Pollutant.CO2.ordinal] > steady[Pollutant.CO2.ordinal])
        assertTrue(stopAndGo[Pollutant.PM_BRAKES.ordinal] > steady[Pollutant.PM_BRAKES.ordinal])
        assertEquals(0.0, steady[Pollutant.PM_BRAKES.ordinal])
    }

    @Test
    fun trucksDominateTireWearAndNoise() {
        val car = emitted(VehicleType.CAR, 60) { 12.0 to 0.0 }
        val truck = emitted(VehicleType.TRUCK, 60) { 12.0 to 0.0 }

        assertTrue(truck[Pollutant.PM_TIRES.ordinal] > 10 * car[Pollutant.PM_TIRES.ordinal])
        assertTrue(truck[Pollutant.NOISE.ordinal] > 5 * car[Pollutant.NOISE.ordinal])
    }

    @Test
    fun depositsIntoGridAndDecays() {
        val environment = Environment(Bounds(0f, 0f, 100f, 100f))
        val v = vehicle()
        v.speed = 10.0
        repeat(100) { environment.emit(v, 0.1) }
        environment.deposit(v, 30f, 70f)
        environment.publish()
        val before = environment.snapshot.value(Pollutant.CO2, 30f, 70f)
        assertTrue(before > 0f)
        assertEquals(0f, environment.snapshot.value(Pollutant.CO2, 80f, 10f))

        environment.decay(Pollutant.CO2.halfLifeSeconds)
        environment.publish()
        assertEquals(before / 2, environment.snapshot.value(Pollutant.CO2, 30f, 70f), before * 0.01f)
    }
}
