package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.FLAG_EMERGENCY
import de.bfi.trafficmaniac.sim.FLAG_JAMMED

/** Einmalige Geräusche. */
enum class SoundEffect {
    /** Blitzer-Klick mit hellem "Pling". */
    CAMERA_FLASH,

    /** Kassenklingeln ("Ka-Ching"). */
    CASH,

    /** Knopfdruck in der Oberfläche. */
    CLICK,
}

/**
 * Dauerhafte Geräuschkulisse, jeweils 0..1: Verkehrsrauschen ([traffic]), Hupkonzert im Stau ([honking]) und
 * Martinshorn ([siren]).
 */
data class Ambience(
    val traffic: Float = 0f,
    val honking: Float = 0f,
    val siren: Boolean = false,
)

/** Tonausgabe der Plattform; auf dem Desktop ein kleiner Synthesizer. */
interface SoundBoard {
    var muted: Boolean
    var music: Boolean

    fun play(effect: SoundEffect)

    fun ambience(ambience: Ambience)
}

/** Stille – für Tests und Plattformen ohne Ton. */
object NoSound : SoundBoard {
    override var muted = true
    override var music = false

    override fun play(effect: SoundEffect) = Unit

    override fun ambience(ambience: Ambience) = Unit
}

/**
 * Leitet aus dem Spielgeschehen ab, was zu hören ist: Blitzer-Klick bei neuen Messungen, Kassenklingeln bei neuen
 * Einnahmen (höchstens alle anderthalb Sekunden), Verkehrsrauschen und Hupen nach Dichte und Zoom – nah dran ist es
 * lauter –, das Martinshorn, solange Blaulicht unterwegs ist und man nah genug dran ist. Pausiert: Stille.
 */
class SoundDirector(
    private val board: SoundBoard,
) {
    private var cameraCount = -1
    private var income = -1L
    private var lastCashSeconds = Double.NEGATIVE_INFINITY

    fun update(
        frame: TrafficFrame,
        zoom: Float,
        seconds: Double,
    ) {
        val game = frame.game
        val cameras = game.cameras.sumOf { it.totalCount }
        if (cameraCount in 0 until cameras) board.play(SoundEffect.CAMERA_FLASH)
        cameraCount = cameras
        val earned = game.fineIncome + game.parkingOffice.feeIncome + game.parkingOffice.ticketIncome
        if (income in 0 until earned && seconds - lastCashSeconds >= CASH_PAUSE_SECONDS) {
            board.play(SoundEffect.CASH)
            lastCashSeconds = seconds
        }
        income = earned
        board.ambience(ambienceOf(frame, zoom))
    }

    private fun ambienceOf(
        frame: TrafficFrame,
        zoom: Float,
    ): Ambience {
        val snapshot = frame.current
        if (frame.effectiveSpeed == 0 || snapshot.vehicleCount == 0) return Ambience()
        var jammed = 0
        var emergency = false
        for (i in 0 until snapshot.vehicleCount) {
            val flags = snapshot.flags[i].toInt()
            if (flags and FLAG_JAMMED != 0) jammed++
            if (flags and FLAG_EMERGENCY != 0) emergency = true
        }
        val closeness = (zoom / FULL_VOLUME_ZOOM).coerceIn(MIN_CLOSENESS, 1f)
        val density = (snapshot.vehicleCount / BUSY_VEHICLES).coerceAtMost(1f)
        val jamShare = (jammed / snapshot.vehicleCount.toFloat() / LOUD_JAM_SHARE).coerceAtMost(1f)
        return Ambience(
            traffic = density * closeness,
            honking = jamShare * closeness,
            siren = emergency && zoom >= SIREN_MIN_ZOOM,
        )
    }

    private companion object {
        const val CASH_PAUSE_SECONDS = 1.5
        const val FULL_VOLUME_ZOOM = 2f
        const val MIN_CLOSENESS = 0.15f
        const val BUSY_VEHICLES = 2_000f
        const val LOUD_JAM_SHARE = 0.25f
        const val SIREN_MIN_ZOOM = 0.5f
    }
}
