package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.sim.Weekday
import kotlinx.serialization.Serializable

/**
 * Ein Szenario: Rahmenbedingungen einer Amtszeit. Alles, was hier nicht gesetzt ist (`null`), kommt aus den
 * Balancing-Dateien. Die Szenarien stehen in `balancing/scenarios.json` und lassen sich dort anpassen oder ergänzen.
 */
@Serializable
data class Scenario(
    val id: String,
    val title: String,
    val description: String,
    val termDays: Int = 14,
    /** Wochentag, an dem die Amtszeit beginnt. */
    val startWeekday: Weekday = Weekday.MONDAY,
    val startHour: Double = 5.0,
    /** So viele Minuten läuft der Verkehr vor Spielbeginn, damit die Straßen nicht leer sind. */
    val warmupMinutes: Int = 0,
    /** Faktor auf die Fahrten in der Spitzenstunde. */
    val demandFactor: Double = 1.0,
    val startBudget: Long? = null,
    val weeklyAllowance: Long? = null,
    val truckShare: Double? = null,
    val vanShare: Double? = null,
    val electricShare: Double? = null,
    /** Zustand der Straßen zu Beginn: schlechtester Abschnitt (0..1). */
    val initialConditionMin: Double? = null,
    val corsoChanceWeekend: Double? = null,
    /** Mit geführten Schritten, die die Werkzeuge nach und nach freischalten; zählt nicht für die Bestenliste. */
    val tutorial: Boolean = false,
    /** Kurzes Etikett auf der Szenario-Karte im Menü („Empfohlen“, „Knappe Kasse“ …). */
    val tag: String? = null,
) {
    /** Simulation und Spielregeln für dieses Szenario auf [difficulty]. */
    fun apply(
        config: SimulationConfig,
        rules: GameRules,
        difficulty: Difficulty,
    ): Pair<SimulationConfig, GameRules> {
        val start = startWeekday.ordinal * GameTime.SECONDS_PER_DAY + startHour * GameTime.SECONDS_PER_HOUR
        val simulation =
            config.copy(
                startSecondOfDay = start,
                peakTripsPerHour = config.peakTripsPerHour * demandFactor,
                truckShare = truckShare ?: config.truckShare,
                vanShare = vanShare ?: config.vanShare,
                electricShare = electricShare ?: config.electricShare,
                initialConditionMin = initialConditionMin ?: config.initialConditionMin,
            )
        val game =
            rules.copy(
                termDays = termDays,
                startBudget = ((startBudget ?: rules.startBudget) * difficulty.budgetFactor).toLong(),
                weeklyAllowance = ((weeklyAllowance ?: rules.weeklyAllowance) * difficulty.budgetFactor).toLong(),
                angerThreshold = rules.angerThreshold + difficulty.angerThresholdShift,
                recallDivisor = rules.recallDivisor * difficulty.recallFactor,
                corsoChanceWeekend = corsoChanceWeekend ?: rules.corsoChanceWeekend,
            )
        return simulation to game
    }

    companion object {
        val DEFAULTS =
            listOf(
                Scenario(
                    id = "tutorial",
                    tag = translatable("Empfohlen"),
                    title = translatable("Tutorial: Der erste Tag im Amt"),
                    description =
                        translatable(
                            "Schritt für Schritt: Tempolimit, Blitzer, Bußgelder, Presse. " +
                                "Die Werkzeuge kommen nach und nach dazu – ideal für den Einstieg.",
                        ),
                    termDays = 3,
                    startHour = 7.0,
                    warmupMinutes = 30,
                    tutorial = true,
                ),
                Scenario(
                    id = "erste-amtszeit",
                    tag = translatable("Klassisch"),
                    title = translatable("Erste Amtszeit"),
                    description =
                        translatable(
                            "Zwei Wochen im Amt, ein ganz normaler Start am Montag um 5 Uhr. " +
                                "Lernen Sie die Stadt kennen – und ihre Autofahrer.",
                        ),
                ),
                Scenario(
                    id = "berufsverkehr",
                    tag = translatable("Knappe Kasse"),
                    title = translatable("Berufsverkehr Montagmorgen"),
                    description =
                        translatable(
                            "Fünf Werktage, die Straßen voller Pendler, die Kasse knapp. " +
                                "Wer jetzt blitzt, verdient – oder wird abgewählt.",
                        ),
                    termDays = 5,
                    startHour = 6.0,
                    warmupMinutes = 60,
                    demandFactor = 1.2,
                    startBudget = 180_000,
                ),
                Scenario(
                    id = "adventssamstag",
                    tag = translatable("Einkaufstrubel"),
                    title = translatable("Adventssamstag"),
                    description =
                        translatable(
                            "Eine Woche ab Samstag: Einkaufsverkehr, Lieferwagen in zweiter Reihe, " +
                                "Parkplatznot in der Innenstadt. Ein Fest für die Politessen.",
                        ),
                    termDays = 7,
                    startWeekday = Weekday.SATURDAY,
                    startHour = 9.0,
                    warmupMinutes = 60,
                    demandFactor = 1.3,
                    vanShare = 0.14,
                ),
                Scenario(
                    id = "ferienbeginn",
                    tag = translatable("Freizeit"),
                    title = translatable("Ferienbeginn"),
                    description =
                        translatable(
                            "Eine Woche ab Freitag: weniger Pendler, kaum LKW, dafür viel Freizeit – " +
                                "die Rad-Korsos lassen nicht lange auf sich warten.",
                        ),
                    termDays = 7,
                    startWeekday = Weekday.FRIDAY,
                    startHour = 12.0,
                    warmupMinutes = 30,
                    demandFactor = 0.85,
                    truckShare = 0.03,
                    corsoChanceWeekend = 0.2,
                ),
                Scenario(
                    id = "brueckensanierung",
                    tag = translatable("Baustellen"),
                    title = translatable("Brückensanierung"),
                    description =
                        translatable(
                            "Zwei Wochen mit maroden Straßen und bröckelnden Brücken über Enz und Nagold. " +
                                "Reparieren, Schilder aufstellen oder Planfeststellung bis 2066?",
                        ),
                    initialConditionMin = 0.25,
                    startBudget = 400_000,
                ),
            )
    }
}

/** Schwierigkeitsgrad: wie viel Geld es gibt und wie schnell die Bürger die Geduld verlieren. */
enum class Difficulty(
    private val labelText: String,
    val budgetFactor: Double,
    /** Verschiebt die Schwelle, ab der Unzufriedenheit zu Zorn wird. */
    val angerThresholdShift: Double,
    /** Größer = der Abwahlindex steigt langsamer. */
    val recallFactor: Double,
) {
    EASY(translatable("Leicht"), budgetFactor = 1.5, angerThresholdShift = 0.08, recallFactor = 1.5),
    NORMAL(translatable("Normal"), budgetFactor = 1.0, angerThresholdShift = 0.0, recallFactor = 1.0),
    HARD(translatable("Schwer"), budgetFactor = 0.7, angerThresholdShift = -0.05, recallFactor = 0.7),
    ;

    val label: String get() = tr(labelText)
}
