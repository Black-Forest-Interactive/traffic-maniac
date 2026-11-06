package de.bfi.trafficmaniac.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Was im Kopf des Fahrers passiert: Aufmerksamkeit, Frust, Stress und Reaktionszeit.
 *
 * - **Aufmerksamkeit** sinkt mit jedem Reiz (Ampel, Tempowechsel, Blitzer), mit Frust und Stress, und
 *   erholt sich langsam. Unaufmerksame übersehen Schilder.
 * - **Frust** wächst, wer hinter einem deutlich langsameren Vordermann hängt.
 * - **Stress** wächst, wenn die Fahrt deutlich länger dauert als gewohnt.
 * - **Reaktionszeit**: Wenn es weitergeht, fährt man erst nach einer Weile los – Spätzünder besonders spät.
 */
internal class DriverBehavior(
    private val speedControl: SpeedControl,
    private val dt: Double,
) {
    /** Beim Einfahren in [link]: Reize verarbeiten und Überschreitung neu würfeln. */
    fun enterLink(
        vehicle: Vehicle,
        previous: Int,
        link: Int,
    ) {
        vehicle.attention = (vehicle.attention - speedControl.stimulus(previous, link)).coerceIn(MIN_ATTENTION, 1.0)
        speedControl.roll(vehicle, link)
    }

    /**
     * Nach der Beschleunigungsberechnung: Frust und Aufmerksamkeit fortschreiben und die Reaktionszeit
     * beim Anfahren einbauen. Liefert die tatsächliche Beschleunigung.
     */
    fun adjust(
        vehicle: Vehicle,
        acceleration: Double,
        desired: Double,
        leaderGap: Double,
    ): Double {
        // Liegengeblieben oder im Einsatz: anhalten und stehen bleiben
        if (vehicle.disabled || vehicle.working ||
            vehicle.deliveringUntil > 0
        ) {
            return if (vehicle.speed > 0) -PARKING_BRAKE else 0.0
        }
        val stuckBehindSlowCar =
            leaderGap < FRUSTRATION_GAP && vehicle.speed < desired * SLOW_SHARE && vehicle.speed > STANDSTILL
        vehicle.frustration =
            (vehicle.frustration + dt * if (stuckBehindSlowCar) FRUSTRATION_RISE else -FRUSTRATION_DECAY).coerceIn(
                0.0,
                1.0,
            )
        val target = vehicle.driver.baseAttention * (1 - IRRITATION_ATTENTION_COST * vehicle.irritation)
        vehicle.attention = min(target, vehicle.attention + dt * ATTENTION_RECOVERY)

        return withReactionTime(vehicle, acceleration)
    }

    /** Losfahren erst nach der Reaktionszeit; Unaufmerksame brauchen länger. */
    private fun withReactionTime(
        vehicle: Vehicle,
        acceleration: Double,
    ): Double {
        if (vehicle.speed > STANDSTILL) vehicle.startHesitation = 0.0
        if (vehicle.speed > STANDSTILL || acceleration <= 0) return acceleration
        vehicle.startHesitation += dt
        val delay = vehicle.driver.reactionDelay * (2 - vehicle.attention)
        return if (vehicle.startHesitation >= delay) acceleration else 0.0
    }

    /** Stress aus der bisherigen Fahrzeit im Verhältnis zur gewohnten (alle paar Sekunden). */
    fun updateStress(
        vehicle: Vehicle,
        time: Double,
    ) {
        if (vehicle.referenceSeconds <= 0) return
        val ratio = (time - vehicle.departureTime) / vehicle.referenceSeconds
        vehicle.stress = ((ratio - STRESS_FROM_RATIO) / (STRESS_FULL_RATIO - STRESS_FROM_RATIO)).coerceIn(0.0, 1.0)
        vehicle.stress = max(0.0, vehicle.stress)
    }

    private companion object {
        const val MIN_ATTENTION = 0.05
        const val PARKING_BRAKE = 8.0
        const val FRUSTRATION_GAP = 40.0
        const val SLOW_SHARE = 0.6
        const val STANDSTILL = 0.1

        /** Nach etwa 50 s hinter einem Schleicher ist man maximal frustriert ... */
        const val FRUSTRATION_RISE = 0.02

        /** ... und braucht 100 s freie Fahrt, um sich zu beruhigen. */
        const val FRUSTRATION_DECAY = 0.01
        const val ATTENTION_RECOVERY = 0.003
        const val IRRITATION_ATTENTION_COST = 0.4
        const val STRESS_FROM_RATIO = 1.5
        const val STRESS_FULL_RATIO = 3.0
    }
}
