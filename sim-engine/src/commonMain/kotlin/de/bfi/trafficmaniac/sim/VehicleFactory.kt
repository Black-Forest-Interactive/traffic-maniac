package de.bfi.trafficmaniac.sim

import kotlin.random.Random

/** Würfelt Fahrzeugtyp, Fahrerstereotyp und Farbe eines neuen Fahrzeugs. */
internal class VehicleFactory(
    private val config: SimulationConfig,
    private val random: Random,
) {
    private var nextId = 0

    fun create(
        route: IntArray,
        destination: Int,
        time: Double,
    ): Vehicle {
        val type = if (random.nextDouble() < config.truckShare) VehicleType.TRUCK else VehicleType.CAR
        val roll = random.nextDouble()
        val driver =
            when {
                roll < config.draenglerShare -> DriverProfile.DRAENGLER
                roll < config.draenglerShare + config.gemuetlicherShare -> DriverProfile.GEMUETLICHER
                else -> DriverProfile.NORMALO
            }
        val color = if (type == VehicleType.TRUCK) TRUCK_COLOR else CAR_COLORS[random.nextInt(CAR_COLORS.size)]
        return Vehicle(nextId++, type, driver, color, route, destination, time)
    }

    private companion object {
        const val TRUCK_COLOR = 0xFF5C6B7A.toInt()
        val CAR_COLORS =
            intArrayOf(
                0xFFE63946.toInt(),
                0xFF457B9D.toInt(),
                0xFF2A9D8F.toInt(),
                0xFFF4A261.toInt(),
                0xFF8338EC.toInt(),
                0xFFFFBE0B.toInt(),
                0xFF06D6A0.toInt(),
                0xFF1D3557.toInt(),
            )
    }
}
