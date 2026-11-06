package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.GameTime

/** PR-Vertrag mit einer Agentur: Solange er läuft, ist die Lokalzeitung freundlich. */
enum class PrContract(
    val label: String,
    val weeklyCost: Long,
    /** Um diesen Anteil wird der wahrgenommene Bürgerzorn gedämpft. */
    val angerDamping: Double,
    val reputationPerHour: Double,
) {
    NONE("Keiner", weeklyCost = 0, angerDamping = 0.0, reputationPerHour = 0.0),
    STANDARD("Standard", weeklyCost = 15_000, angerDamping = 0.25, reputationPerHour = 0.06),
    PREMIUM("Premium", weeklyCost = 40_000, angerDamping = 0.4, reputationPerHour = 0.12),
}

/**
 * Bürgerzorn, Abwahlindex und Reputation.
 *
 * - **Bürgerzorn** (0–100) hängt am Anteil unzufriedener Bürger – unter etwa einem Drittel kümmert es
 *   niemanden ("Ist doch nicht mein Problem"), darüber steigt er schnell. PR dämpft ihn, aber nicht mehr,
 *   wenn fast alle leiden.
 * - **Abwahlindex** (0–100) sammelt den Zorn über die Zeit; bei 100 ist man raus.
 * - **Reputation** (0–100) ist das öffentliche Image: PR und Sprüche heben sie, Zorn und kritische Presse senken sie.
 */
internal class Politics(
    private val rules: GameRules,
) {
    var anger = 0.0
        private set
    var recallIndex = 0.0
        private set
    var reputation = rules.startReputation
        private set
    private var warningsGiven = 0

    val isRecalled: Boolean get() = recallIndex >= MAX_INDEX

    /** Eine Spielstunde fortschreiben; liefert Meldungen (Warnstufen, Presse). */
    fun hourPassed(
        unhappyShare: Double,
        pr: PrContract,
        argumentsCampaign: Boolean,
        finesLastHour: Long,
    ): List<String> {
        val raw =
            ((unhappyShare - rules.angerThreshold) / (rules.angerFullAt - rules.angerThreshold)).coerceIn(0.0, 1.0) *
                MAX_INDEX
        // Ab einem gewissen Leidensdruck hilft auch die schönste Story nichts mehr
        val damping = pr.angerDamping * if (unhappyShare > rules.prLimitShare) HALF else 1.0
        anger = raw * (1 - damping)
        recallIndex =
            (recallIndex + (anger - rules.recallAngerThreshold) / rules.recallDivisor).coerceIn(0.0, MAX_INDEX)

        val criticalPress = pr == PrContract.NONE && finesLastHour >= rules.ripOffPressFines
        reputation =
            (
                reputation + pr.reputationPerHour + (if (argumentsCampaign) rules.argumentsReputationPerHour else 0.0) -
                    anger * rules.reputationLossPerAnger - (if (criticalPress) rules.ripOffReputationLoss else 0.0)
            ).coerceIn(0.0, MAX_INDEX)

        val messages = ArrayList<String>()
        if (criticalPress) messages += "Lokalzeitung: „Abzocke mit Blitzern?“"
        WARNINGS.forEachIndexed { level, (threshold, text) ->
            if (recallIndex >= threshold && warningsGiven <= level) {
                warningsGiven = level + 1
                messages += text
            }
        }
        if (recallIndex < WARNINGS.first().first) warningsGiven = 0
        return messages
    }

    private companion object {
        const val MAX_INDEX = 100.0
        const val HALF = 0.5
        val WARNINGS =
            listOf(
                60.0 to "Erste Rücktrittsforderungen im Gemeinderat (Abwahlindex 60)",
                80.0 to "Misstrauensantrag angekündigt! (Abwahlindex 80)",
            )
    }
}

/** Abrechnung der Haushaltsrunde. */
data class BudgetRound(
    val time: GameTime,
    val allowance: Long,
    val prCost: Long,
    val argumentsCost: Long,
    /** Gehälter der Bußgeldstelle. */
    val salaries: Long,
)
