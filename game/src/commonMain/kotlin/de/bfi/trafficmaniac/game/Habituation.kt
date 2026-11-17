package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation

/** Wohin die Gewöhnung an Blitzer meldet: Meldungsliste, Einblendung, Errungenschaften prüfen. */
internal interface HabitEffects {
    fun message(
        text: String,
        place: MapPlace? = null,
        kind: MessageKind = MessageKind.SYSTEM,
    )

    fun highlight(
        kind: HighlightKind,
        title: String,
        text: String,
        place: MapPlace? = null,
    )

    fun checkAchievements()
}

/**
 * Gewöhnung an Blitzer (Paket 4): Feste Blitzer werden täglich bekannter, Warn-Apps verbreiten sich, Attrappen
 * fliegen auf. Gegenmittel sind die Polizeiaktion „Blitzerwarner“ und der angekündigte Blitzermarathon.
 */
internal class Habituation(
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val politics: Politics,
    private val works: PublicWorks,
    seed: Long,
    private val effects: HabitEffects,
) {
    /** Letzte Polizeiaktion (Spieltag) und wie viele es gab. */
    private var lastCampaignDay = Int.MIN_VALUE / 2
    var campaigns = 0
        private set

    /** Ende des Blitzermarathons in Simulationssekunden, −1 = keiner. */
    private var marathonUntil = -1.0
    private val random = kotlin.random.Random(seed + HABIT_SEED)

    init {
        simulation.speedControl.warnerShare = rules.habits.warnerShareStart
        simulation.speedControl.maxWarned = rules.habits.maxEffect
    }

    val marathonActive: Boolean get() = marathonUntil > 0

    /** Tage bis zur nächsten möglichen Polizeiaktion. */
    val campaignInDays: Int
        get() =
            (rules.habits.campaignPauseDays - (simulation.gameTime.day - lastCampaignDay)).coerceAtLeast(0)

    /** Polizeiaktion und Blitzermarathon; alle anderen Befehle werden ignoriert. */
    fun execute(command: GameCommand) {
        when (command) {
            GameCommand.WarnerCampaign -> warnerCampaign()
            GameCommand.AnnounceMarathon -> announceMarathon()
            else -> Unit
        }
    }

    /** Jeden Schritt: Blitzermarathon beenden, wenn seine Zeit um ist. */
    fun update() {
        if (marathonUntil > 0 && simulation.time >= marathonUntil) endMarathon()
    }

    /**
     * Polizeiaktion „Blitzerwarner“: Treffer bringen Bußgeld, die Warner-Quote sinkt, die Presse spricht von Razzia.
     */
    private fun warnerCampaign() {
        val habits = rules.habits
        val day = simulation.gameTime.day
        if (day - lastCampaignDay < habits.campaignPauseDays) {
            return effects.message(
                tr(
                    "Die Polizei hat diese Woche schon kontrolliert – frühestens wieder in {days} Tagen.",
                    "days" to habits.campaignPauseDays - (day - lastCampaignDay),
                ),
            )
        }
        if (!treasury.pay(habits.campaignCost, tr("Polizeiaktion"))) return
        lastCampaignDay = day
        campaigns++
        val control = simulation.speedControl
        val trips = simulation.dayReports.lastOrNull()?.tripsStarted ?: DEFAULT_DAY_TRIPS
        val hits = (trips * control.warnerShare * habits.campaignHitRate).toInt()
        repeat(hits) { treasury.earnFine(habits.warnerFine) }
        control.warnerShare *= 1 - habits.campaignReduction
        politics.press(-habits.campaignReputationLoss)
        val text =
            tr(
                "Polizeiaktion „Blitzerwarner“: {hits} Handys mit Warn-App, {fines} Bußgeld – " +
                    "nur noch {share} % fahren mit Warner",
                "hits" to hits,
                "fines" to formatEuro(hits.toLong() * habits.warnerFine),
                "share" to (control.warnerShare * PERCENT).toInt(),
            )
        effects.message(text, kind = MessageKind.ACTION)
        effects.highlight(HighlightKind.MEDIUM, tr("Handy-Razzia"), text)
        effects.checkAchievements()
    }

    /** Blitzermarathon ankündigen: einen Tag lang bremsen alle, die Zeitung lobt. */
    private fun announceMarathon() {
        if (marathonUntil > 0) return effects.message(tr("Der Blitzermarathon läuft schon."))
        marathonUntil = simulation.time + rules.habits.marathonHours * SECONDS_PER_HOUR
        simulation.speedControl.marathon = true
        politics.press(rules.habits.marathonReputationGain)
        val text = tr("Blitzermarathon angekündigt: Heute fahren alle brav – die Zeitung ist begeistert.")
        effects.message(text, kind = MessageKind.ACTION)
        effects.highlight(HighlightKind.MEDIUM, tr("Blitzermarathon"), text)
    }

    private fun endMarathon() {
        marathonUntil = -1.0
        val control = simulation.speedControl
        control.marathon = false
        control.warnerShare *= 1 - rules.habits.marathonWarnerReduction
        effects.message(
            tr("Blitzermarathon vorbei – mancher verlässt sich jetzt lieber auf die Ankündigung als auf die App."),
        )
    }

    /** Mitternacht: Blitzer werden bekannter, Warn-Apps verbreiten sich, Attrappen fliegen manchmal auf. */
    fun dayPassed() {
        val habits = rules.habits
        val control = simulation.speedControl
        control.dayPassed(habits.familiarityPerDay)
        control.warnerShare = (control.warnerShare + habits.warnerGrowthPerDay).coerceAtMost(1.0)
        for (camera in control.cameras.filter { it.dummy }) {
            if (random.nextDouble() >= habits.dummyDiscoveryChancePerDay) continue
            politics.press(-habits.dummyDiscoveryReputationLoss)
            val text =
                tr(
                    "Presse: „Stadt blitzt mit Pappe“ – die Attrappe an der {street} ist aufgeflogen",
                    "street" to works.streetName(camera.segment),
                )
            val place = MapPlace(camera.x, camera.y)
            effects.message(text, place, MessageKind.PROBLEM)
            effects.highlight(HighlightKind.WARNING, tr("Attrappe aufgeflogen"), text, place)
        }
    }

    private companion object {
        const val HABIT_SEED = 9_173L
        const val PERCENT = 100
        const val SECONDS_PER_HOUR = 3_600.0

        /** Vor dem ersten Tagesabschluss: so viele Fahrten nimmt die Polizeiaktion als Grundlage. */
        const val DEFAULT_DAY_TRIPS = 40_000
    }
}
