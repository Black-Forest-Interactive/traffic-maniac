package de.bfi.trafficmaniac.sim

import de.bfi.trafficmaniac.city.Bounds
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Was der Verkehr in die Luft (und die Ohren) bläst. */
enum class Pollutant(
    val label: String,
    /** Halbwertszeit im Raster in Sekunden: Abgase verwehen langsam, Lärm ist sofort weg. */
    val halfLifeSeconds: Double,
) {
    CO2("CO₂", halfLifeSeconds = 1_800.0),
    NOX("Stickoxide", halfLifeSeconds = 1_800.0),
    PM_EXHAUST("Feinstaub (Abgas)", halfLifeSeconds = 3_600.0),
    PM_TIRES("Feinstaub (Reifen)", halfLifeSeconds = 3_600.0),
    PM_BRAKES("Feinstaub (Bremsen)", halfLifeSeconds = 3_600.0),
    NOISE("Lärm", halfLifeSeconds = 60.0),
}

/** Unveränderliche Kopie des Umweltrasters für Oberfläche und Spiel. */
class EnvironmentSnapshot(
    val columns: Int,
    val rows: Int,
    val cellSize: Float,
    val minX: Float,
    val minY: Float,
    /** Werte je Schadstoff und Zelle (Zeilen von Süden nach Norden). */
    val values: Map<Pollutant, FloatArray>,
    /** Gesamtausstoß seit Spielbeginn: Kraftstoff in Litern, Schadstoffe in Gramm. */
    val totalFuelLiters: Double,
    val totals: Map<Pollutant, Double>,
) {
    fun value(
        pollutant: Pollutant,
        x: Float,
        y: Float,
    ): Float {
        val column = ((x - minX) / cellSize).toInt()
        val row = ((y - minY) / cellSize).toInt()
        if (column !in 0 until columns || row !in 0 until rows) return 0f
        return values.getValue(pollutant)[row * columns + column]
    }

    /** Mittelwert über ein Quadrat von (2 × [radiusCells] + 1)² Zellen – die Nachbarschaft eines Wohngebiets. */
    fun neighborhood(
        pollutant: Pollutant,
        x: Float,
        y: Float,
        radiusCells: Int,
    ): Float {
        val column = ((x - minX) / cellSize).toInt()
        val row = ((y - minY) / cellSize).toInt()
        val grid = values.getValue(pollutant)
        var sum = 0f
        var count = 0
        for (r in row - radiusCells..row + radiusCells) {
            for (c in column - radiusCells..column + radiusCells) {
                if (r in 0 until rows && c in 0 until columns) sum += grid[r * columns + c]
                count++
            }
        }
        return sum / count
    }

    /** Feinstaub gesamt (Abgas + Reifen + Bremsen) an einer Stelle. */
    fun particulates(
        x: Float,
        y: Float,
    ): Float = value(Pollutant.PM_EXHAUST, x, y) + value(Pollutant.PM_TIRES, x, y) + value(Pollutant.PM_BRAKES, x, y)

    /** Lärmpegel in dB an einer Stelle (aus der gespeicherten Schallenergie). */
    fun noiseDb(
        x: Float,
        y: Float,
    ): Float = energyToDb(value(Pollutant.NOISE, x, y))

    companion object {
        /** Gespeicherte Schallenergie → grob kalibrierter Pegel in dB(A) an der Hauswand (0 = Stille). */
        fun energyToDb(energy: Float): Float =
            if (energy <= 1f) 0f else max(0.0, DB_PER_DECADE * log10(energy.toDouble()) - CALIBRATION_DB).toFloat()

        private const val DB_PER_DECADE = 10.0

        /** Abzug, damit eine belebte Hauptstraße bei etwa 60–70 dB landet. */
        private const val CALIBRATION_DB = 70.0
    }
}

/**
 * Emissionsmodell und Umweltraster (50 × 50 m).
 *
 * - Verbrauch aus Leerlauf plus Antriebsleistung (Beschleunigen, Rollen, Luftwiderstand) – Stop-and-go
 *   kostet also deutlich mehr als gleichmäßiges Fahren.
 * - Feinstaub aus Auspuff, **Reifen** (wächst mit Masse, Tempo und Beschleunigung) und **Bremsen**
 *   (wächst mit der beim Bremsen vernichteten Energie).
 * - Lärm als Schallenergie aus Roll- und Motorgeräusch; LKW deutlich lauter.
 *
 * Keine wissenschaftliche Genauigkeit, nur glaubwürdige Größenordnungen.
 */
class Environment internal constructor(
    bounds: Bounds,
    private val cellSize: Float = CELL_SIZE_METERS,
) {
    private val minX = bounds.minX
    private val minY = bounds.minY
    private val columns = (bounds.width / cellSize).toInt() + 1
    private val rows = (bounds.height / cellSize).toInt() + 1
    private val grids = Pollutant.entries.associateWith { FloatArray(columns * rows) }
    private val totals = Pollutant.entries.associateWith { 0.0 }.toMutableMap()
    private var totalFuel = 0.0

    /** Letzte veröffentlichte Kopie (wird nur alle paar Sekunden erneuert). */
    var snapshot: EnvironmentSnapshot = createSnapshot()
        private set

    /** Ausstoß eines Fahrzeugs in einem Zeitschritt sammeln. */
    internal fun emit(
        vehicle: Vehicle,
        dt: Double,
    ) {
        val type = vehicle.type
        val v = vehicle.speed
        val a = vehicle.acceleration
        val drivePower = type.massKg * (a * v + GRAVITY * ROLLING_RESISTANCE * v) + AIR_DRAG_FACTOR * v.pow(3)
        val fuel = (type.idleFuelPerSecond + max(0.0, drivePower) / (ENGINE_EFFICIENCY * FUEL_ENERGY_PER_LITER)) * dt
        val tonnes = type.massKg / KG_PER_TONNE
        val e = vehicle.pendingEmissions
        totalFuel += fuel
        e[Pollutant.CO2.ordinal] += fuel * CO2_GRAMS_PER_LITER
        e[Pollutant.NOX.ordinal] += fuel * type.noxPerLiter
        e[Pollutant.PM_EXHAUST.ordinal] += fuel * type.exhaustPmPerLiter
        e[Pollutant.PM_TIRES.ordinal] += tonnes * TIRE_GRAMS_PER_TONNE_METER * v * (1 + kotlin.math.abs(a)) * dt
        e[Pollutant.PM_BRAKES.ordinal] += BRAKE_GRAMS_PER_JOULE * type.massKg * max(0.0, -a) * v * dt
        val levelDb =
            if (v >
                MIN_NOISE_SPEED
            ) {
                ROLLING_NOISE_DB + ROLLING_NOISE_SLOPE * log10(v * KMH_PER_MS)
            } else {
                IDLE_NOISE_DB
            }
        e[Pollutant.NOISE.ordinal] +=
            NOISE_ENERGY_SCALE.pow((levelDb + type.noiseBonusDb + max(0.0, a) * ACCEL_NOISE_DB) / DB_PER_DECADE) * dt
    }

    /** Gesammelten Ausstoß eines Fahrzeugs an seiner Position ablegen. */
    internal fun deposit(
        vehicle: Vehicle,
        x: Float,
        y: Float,
    ) {
        val column = ((x - minX) / cellSize).toInt()
        val row = ((y - minY) / cellSize).toInt()
        val inside = column in 0 until columns && row in 0 until rows
        for (pollutant in Pollutant.entries) {
            val amount = vehicle.pendingEmissions[pollutant.ordinal]
            if (pollutant != Pollutant.NOISE) totals[pollutant] = totals.getValue(pollutant) + amount
            if (inside) grids.getValue(pollutant)[row * columns + column] += amount.toFloat()
            vehicle.pendingEmissions[pollutant.ordinal] = 0.0
        }
    }

    /** Abbau im Raster (Wind, Zeit) über [seconds] Sekunden. */
    internal fun decay(seconds: Double) {
        for (pollutant in Pollutant.entries) {
            val factor = exp(-seconds * LN2 / pollutant.halfLifeSeconds).toFloat()
            val grid = grids.getValue(pollutant)
            for (i in grid.indices) grid[i] *= factor
        }
    }

    internal fun publish() {
        snapshot = createSnapshot()
    }

    private fun createSnapshot() =
        EnvironmentSnapshot(
            columns = columns,
            rows = rows,
            cellSize = cellSize,
            minX = minX,
            minY = minY,
            values = grids.mapValues { it.value.copyOf() },
            totalFuelLiters = totalFuel,
            totals = totals.toMap(),
        )

    companion object {
        const val CELL_SIZE_METERS = 50f
        private const val GRAVITY = 9.81
        private const val ROLLING_RESISTANCE = 0.012
        private const val AIR_DRAG_FACTOR = 0.4
        private const val ENGINE_EFFICIENCY = 0.25
        private const val FUEL_ENERGY_PER_LITER = 34_000_000.0
        private const val CO2_GRAMS_PER_LITER = 2_500.0
        private const val KG_PER_TONNE = 1_000.0
        private const val TIRE_GRAMS_PER_TONNE_METER = 0.000_01
        private const val BRAKE_GRAMS_PER_JOULE = 0.000_000_01
        private const val MIN_NOISE_SPEED = 1.0
        private const val ROLLING_NOISE_DB = 70.0
        private const val ROLLING_NOISE_SLOPE = 30.0
        private const val IDLE_NOISE_DB = 70.0
        private const val ACCEL_NOISE_DB = 3.0
        private const val DB_PER_DECADE = 10.0
        private const val NOISE_ENERGY_SCALE = 10.0
        private const val LN2 = 0.693_147_18
    }
}
