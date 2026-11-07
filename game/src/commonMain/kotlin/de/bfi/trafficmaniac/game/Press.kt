package de.bfi.trafficmaniac.game

/** Presse und Imagepflege für die Oberfläche. */
data class PressState(
    /** Tonlage der Lokalzeitung: −1 Verriss … +1 Jubelpresse. */
    val tone: Double = 0.0,
    val toneLabel: String = Press.toneLabel(0.0),
    /** Wie viel die PR-Story gerade noch wirkt (1 = voll, 0 = gar nicht mehr). */
    val prEffect: Double = 1.0,
    /** Aufschlag auf den PR-Vertrag für die Lücke zwischen Story und Realität (Anteil, bisher diese Woche). */
    val prGapSurcharge: Double = 0.0,
    /** Anlass für den nächsten Spatenstich bzw. die nächste Eröffnung; `null`, wenn heute schon gefeiert wurde. */
    val nextCeremony: String? = null,
    val ceremonies: Int = 0,
)

/**
 * Wie weit PR noch wirkt: voll bis [GameRules.prLimitShare] Unzufriedene, dann immer weniger, ab
 * [GameRules.prNoEffectShare] gar nicht mehr ("Steht doch in der Zeitung, dass alles super ist – warum steh ich
 * dann seit zwei Stunden hier?").
 */
internal fun GameRules.prEffect(unhappyShare: Double): Double =
    ((prNoEffectShare - unhappyShare) / (prNoEffectShare - prLimitShare).coerceAtLeast(MIN_SPAN)).coerceIn(0.0, 1.0)

private const val MIN_SPAN = 1e-6

/**
 * Die Presse als eigenes Spielsystem:
 *
 * - **Tonlage**: Der PR-Vertrag gibt sie vor (ohne PR kritisch), die Zeitung folgt jeden Tag ein gutes Stück.
 *   Wird es für fast alle unerträglich, kippt auch die gekaufte Presse.
 * - **Reputation**: Freundliche Presse bringt jeden Tag Image, kritische kostet welches.
 * - **PR-Kosten**: Je größer die Lücke zwischen Story und Realität (Anteil Unzufriedener), desto teurer wird die
 *   Agentur – abgerechnet in der Haushaltsrunde.
 * - **Spatenstich & Eröffnung**: einmal am Tag, gern auch für etwas, das nie fertig wird. Jede weitere in derselben
 *   Woche bringt weniger.
 */
internal class Press(
    private val rules: GameRules,
    private val treasury: Treasury,
    private val message: (String) -> Unit,
) {
    var tone = 0.0
        private set
    private var unhappySum = 0.0
    private var hours = 0
    private var ceremoniesThisWeek = 0
    private var ceremonies = 0
    private var celebratedToday = false

    /** Schlagzeilen der heutigen Feierlichkeiten für die morgige Zeitung. */
    var todayCeremonies = ArrayList<String>()
        private set

    fun state(
        unhappyShare: Double,
        maintenance: MaintenanceState,
    ) = PressState(
        tone = tone,
        toneLabel = toneLabel(tone),
        prEffect = rules.prEffect(unhappyShare),
        prGapSurcharge = gapSurcharge(),
        nextCeremony = if (celebratedToday) null else ceremonyTopic(maintenance).first,
        ceremonies = ceremonies,
    )

    fun hourPassed(unhappyShare: Double) {
        unhappySum += unhappyShare
        hours++
    }

    /** Mitternacht: Ton der Zeitung anpassen. Liefert die Image-Änderung des Tages. */
    fun dayEnded(
        pr: PrContract,
        unhappyShare: Double,
    ): Double {
        val effect = if (pr == PrContract.NONE) 0.0 else rules.prEffect(unhappyShare)
        val target = pr.pressTone * effect + CRITICAL * (1 - effect)
        tone += (target - tone) * rules.pressToneInertia
        celebratedToday = false
        return tone * rules.pressReputationPerDay
    }

    fun startNewDay(): List<String> = todayCeremonies.also { todayCeremonies = ArrayList() }

    /** Aufschlag für die Lücke zwischen Story und Realität, gemessen am mittleren Anteil Unzufriedener. */
    fun gapSurcharge(): Double {
        val mean = if (hours > 0) unhappySum / hours else 0.0
        val gap = ((mean - rules.angerThreshold) / (rules.angerFullAt - rules.angerThreshold)).coerceIn(0.0, 1.0)
        return gap * rules.prGapCostFactor
    }

    /** Haushaltsrunde: PR-Kosten der Woche samt Aufschlag; danach beginnt die Woche von vorn. */
    fun weeklyPrCost(pr: PrContract): Long {
        val cost = (pr.weeklyCost * (1 + gapSurcharge())).toLong()
        unhappySum = 0.0
        hours = 0
        ceremoniesThisWeek = 0
        return cost
    }

    /** Spatenstich oder Eröffnung. Liefert das gewonnene Image (0, wenn nichts passiert ist). */
    fun ceremony(
        maintenance: MaintenanceState,
        onReopened: () -> Unit,
    ): Double {
        if (celebratedToday) {
            message("Heute wurde schon gefeiert – morgen gibt es den nächsten Spatenstich")
            return 0.0
        }
        if (!treasury.pay(rules.ceremonyCost, "Spatenstich")) return 0.0
        val (headline, reopening) = ceremonyTopic(maintenance)
        if (reopening) onReopened()
        celebratedToday = true
        ceremonies++
        // Die dritte Schaufel in einer Woche interessiert keinen mehr; bei Verriss lacht die Zeitung darüber
        val gain = rules.ceremonyReputation / (1 + ceremoniesThisWeek) * if (tone < MOCKING_TONE) HALF else 1.0
        ceremoniesThisWeek++
        todayCeremonies += headline
        message("$headline (+${"%.1f".format(gain)} Reputation)")
        return gain
    }

    /** Bester Anlass: frisch Repariertes eröffnen, sonst Spatenstich für eine Planfeststellung oder ein Konzept. */
    private fun ceremonyTopic(maintenance: MaintenanceState): Pair<String, Boolean> {
        maintenance.lastRepaired?.let {
            return "Feierliche Wiedereröffnung: $it – Bürgermeister durchschneidet Band" to
                true
        }
        val planning = maintenance.reports.firstOrNull { it.status == DamageStatus.PLANNING }
        if (planning != null) return "Spatenstich ${planning.place} – ${planning.progress}" to false
        return "Spatenstich für das Verkehrskonzept – Fertigstellung voraussichtlich 2066" to false
    }

    companion object {
        private const val CRITICAL = -1.0
        private const val MOCKING_TONE = -0.5
        private const val HALF = 0.5
        private const val JUBILANT = 0.6
        private const val FRIENDLY = 0.2
        private const val CRITICAL_BELOW = -0.2

        fun toneLabel(tone: Double): String =
            when {
                tone >= JUBILANT -> "Jubelpresse"
                tone >= FRIENDLY -> "wohlwollend"
                tone > CRITICAL_BELOW -> "neutral"
                tone > -JUBILANT -> "kritisch"
                else -> "Verriss"
            }
    }
}
