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
        trip: Trip,
        time: Double,
        referenceSeconds: Double,
    ): Vehicle {
        val type = pickType()
        val drivetrain = pickDrivetrain(type)
        val old = drivetrain != Drivetrain.ELECTRIC && random.nextDouble() < config.oldShare
        val driver = pickDriver()
        val color = colorFor(type)
        val knowsCameras = random.nextDouble() < config.cameraAwareShare
        return Vehicle(
            nextId++,
            type,
            driver,
            color,
            route,
            trip.destinationNode,
            time,
            knowsCameras,
            trip.homeZone,
            trip.person,
            referenceSeconds,
            drivetrain,
            if (old) Generation.OLD else Generation.MODERN,
        ).also { vehicle ->
            if (drivetrain ==
                Drivetrain.ELECTRIC
            ) {
                vehicle.batteryKwh = config.batteryKwh * random.nextDouble(MIN_CHARGE, MAX_CHARGE)
            }
            if (type ==
                VehicleType.TRUCK
            ) {
                vehicle.ignoresWeightLimits = random.nextDouble() < config.weightLimitIgnoreShare
            }
        }
    }

    private fun pickType(): VehicleType {
        val roll = random.nextDouble()
        return when {
            roll < config.truckShare -> VehicleType.TRUCK
            roll < config.truckShare + config.vanShare -> VehicleType.VAN
            else -> VehicleType.CAR
        }
    }

    private fun pickDrivetrain(type: VehicleType): Drivetrain =
        when {
            type == VehicleType.CAR && random.nextDouble() < config.electricShare -> Drivetrain.ELECTRIC
            type != VehicleType.CAR || random.nextDouble() < config.dieselShare -> Drivetrain.DIESEL
            else -> Drivetrain.PETROL
        }

    private fun colorFor(type: VehicleType): Int =
        when (type) {
            VehicleType.TRUCK -> TRUCK_COLOR
            VehicleType.VAN -> VAN_COLOR
            else -> CAR_COLORS[random.nextInt(CAR_COLORS.size)]
        }

    private val driverTable =
        config.driverMix.entries
            .filter { it.value > 0 }
            .map { it.key to it.value }
    private val driverTotal = driverTable.sumOf { it.second }

    private fun pickDriver(): DriverProfile {
        var roll = random.nextDouble() * driverTotal
        for ((driver, share) in driverTable) {
            roll -= share
            if (roll <= 0) return driver
        }
        return driverTable.lastOrNull()?.first ?: DriverProfile.NORMALO
    }

    /** Einsatzfahrzeug von einer Wache zum Einsatz. */
    fun createService(
        role: VehicleRole,
        route: IntArray,
        time: Double,
        incidentId: Int,
    ): Vehicle =
        Vehicle(
            id = nextId++,
            type =
                when (role) {
                    VehicleRole.TOW_TRUCK, VehicleRole.STREET_SERVICE -> VehicleType.VAN

                    // Löschfahrzeug: so groß wie ein LKW
                    VehicleRole.FIRE_BRIGADE -> VehicleType.TRUCK

                    else -> VehicleType.CAR
                },
            driver = DriverProfile.NORMALO,
            color = SERVICE_COLORS.getValue(role),
            route = route,
            destinationNode = -1,
            departureTime = time,
            knowsCameras = true,
            homeZone = -1,
            person = -1,
            referenceSeconds = 0.0,
            drivetrain = Drivetrain.DIESEL,
            role = role,
        ).also { it.incidentId = incidentId }

    /** Rad-Korso (eine Gruppe) oder Schwertransport für ein Ereignis. */
    fun createEvent(
        role: VehicleRole,
        route: IntArray,
        time: Double,
    ): Vehicle {
        val corso = role == VehicleRole.BIKE_CORSO
        return Vehicle(
            id = nextId++,
            type = if (corso) VehicleType.BIKE_CORSO else VehicleType.HEAVY_TRANSPORT,
            driver = DriverProfile.GEMUETLICHER,
            color = if (corso) CORSO_COLOR else HEAVY_TRANSPORT_COLOR,
            route = route,
            destinationNode = -1,
            departureTime = time,
            knowsCameras = true,
            homeZone = -1,
            person = -1,
            referenceSeconds = 0.0,
            drivetrain = if (corso) Drivetrain.PETROL else Drivetrain.DIESEL,
            role = role,
        )
    }

    private companion object {
        const val CORSO_COLOR = 0xFF2DC653.toInt()
        const val HEAVY_TRANSPORT_COLOR = 0xFFFFD60A.toInt()
        const val MIN_CHARGE = 0.1
        const val MAX_CHARGE = 0.95
        const val VAN_COLOR = 0xFFE9ECEF.toInt()
        val SERVICE_COLORS =
            mapOf(
                VehicleRole.AMBULANCE to 0xFFFFFFFF.toInt(),
                VehicleRole.POLICE to 0xFF1E6FD9.toInt(),
                VehicleRole.FIRE_BRIGADE to 0xFFC1121F.toInt(),
                VehicleRole.TOW_TRUCK to 0xFFFFD60A.toInt(),
                VehicleRole.STREET_SERVICE to 0xFFFFA500.toInt(),
                VehicleRole.PRIVATE to 0xFF888888.toInt(),
            )
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
