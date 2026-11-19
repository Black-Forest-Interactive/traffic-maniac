package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable

/** Achievements für Stadt-Extreme – sie gehen nicht in die Wertung ein. */
enum class Achievement(
    private val titleText: String,
    private val descriptionText: String,
    internal val reached: (AchievementFacts) -> Boolean,
    /** Zählbare Erfolge: wie weit man ist (für „7 von 10“ und den Hinweis „fast geschafft“); sonst `null`. */
    internal val progress: ((AchievementFacts) -> Int)? = null,
    /** Ziel für [progress]. */
    val target: Int = 0,
) {
    FIRST_CAMERA(translatable("Es blitzt"), translatable("Der erste Blitzer steht"), { it.cameras >= 1 }),
    FIRST_FINES(
        translatable("Die ersten Tausend"),
        translatable("1.000 € Bußgeld eingenommen"),
        { it.fineIncome >= FIRST_FINES_EUROS },
        { it.fineIncome.toInt() },
        FIRST_FINES_EUROS.toInt(),
    ),
    FIRST_REPAIR(translatable("Geflickt"), translatable("Die erste Straße ist repariert"), { it.repairs >= 1 }),
    FIRST_DAY(translatable("Überlebt"), translatable("Den ersten Tag im Amt überstanden"), { it.daysEnded >= 1 }),
    PHONE_RAID(translatable("Handy-Razzia"), translatable("Die erste Polizeiaktion gegen Blitzer-Warn-Apps"), {
        it.campaigns >=
            1
    }),
    TOTAL_STANDSTILL(
        translatable("Totalstillstand"),
        translatable("Mindestens 300 Autos unterwegs, im Schnitt keine 3 km/h"),
        { it.activeVehicles >= 300 && it.meanSpeedKmh < 3.0 },
    ),
    ALL_BRIDGES_CLOSED(
        translatable("Alle Brücken gesperrt"),
        translatable("Keine einzige Brücke über Enz und Nagold ist mehr befahrbar"),
        { it.bridgesOpen == 0 },
    ),
    HALF_LATE(
        translatable(
            "Die halbe Stadt kam zu spät",
        ),
        translatable("Mindestens die Hälfte der Pendler eines Tages zu spät"),
        {
            it.lateShare >=
                0.5
        },
    ),
    SIGN_FOREST(
        translatable("Schilderwald"),
        translatable("Tempolimit, Nachtlimit, Vorfahrt, Radweg-Querung und zwei Blitzer auf engstem Raum"),
        { it.signForest },
    ),
    LETTER_WAVE(translatable("Bescheid-Welle"), translatable("1.500 Bußgeldbescheide an einem Tag"), {
        it.lettersToday >=
            1_500
    }),
    CAMERA_CAPITAL(
        translatable("Blitzer-Hauptstadt"),
        translatable("Zehn Blitzer gleichzeitig im Einsatz"),
        { it.cameras >= CAPITAL_CAMERAS },
        { it.cameras },
        CAPITAL_CAMERAS,
    ),
    ENGINEERING(translatable("Ingenieurskunst"), translatable("Eine Brücke ist eingestürzt"), { it.collapses > 0 }),
    PLANNING_CHAMPION(
        translatable("Planungsweltmeister"),
        translatable("Drei Planfeststellungsverfahren laufen gleichzeitig"),
        { it.plannings >= PLANNINGS },
        { it.plannings },
        PLANNINGS,
    ),
    CRITICAL_MASS(translatable("Kritische Masse"), translatable("Ein Rad-Korso hat eine Hauptstraße lahmgelegt"), {
        it.corsos >
            0
    }),
    DEMO_SEASON(translatable("Demo-Saison"), translatable("Drei Spontandemos an einem Tag"), { it.demosToday >= 3 }),
    SPEEDER_OF_THE_YEAR(
        translatable("Raser des Jahres"),
        translatable("Jemand fuhr mehr als doppelt so schnell wie erlaubt"),
        { it.recordRatio > SPEEDER_RATIO },
    ),
    BIKE_CITY(
        translatable("Fahrradstadt wider Willen"),
        translatable("25 Führerscheine eingezogen"),
        { it.revocations >= REVOCATIONS },
        { it.revocations },
        REVOCATIONS,
    ),
    CLASS_ACTION(
        translatable("Klagewelle"),
        translatable("Zehn Schadensersatzklagen"),
        { it.lawsuits >= LAWSUITS },
        { it.lawsuits },
        LAWSUITS,
    ),
    STUCK(
        translatable("Eingeklemmt"),
        translatable("Ein LKW steckt in einer Nebenstraße fest"),
        { it.trucksStuck > 0 },
    ),
    PRESS_DARLING(translatable("Liebling der Presse"), translatable("Reputation über 90"), { it.reputation > 90 }),
    ;

    val title: String get() = tr(titleText)
    val description: String get() = tr(descriptionText)
}

/** „Raser des Jahres“: mehr als doppelt so schnell wie erlaubt. */
private const val SPEEDER_RATIO = 2.0
private const val FIRST_FINES_EUROS = 1_000L
private const val CAPITAL_CAMERAS = 10
private const val PLANNINGS = 3
private const val REVOCATIONS = 25
private const val LAWSUITS = 10

/** Ab diesem Anteil am Ziel kommt einmal der Hinweis „fast geschafft“. */
private const val ALMOST = 0.8

data class AchievementStatus(
    val achievement: Achievement,
    /** Spieltag der Freischaltung, `null` = noch offen. */
    val unlockedDay: Int?,
    /** Stand bei zählbaren Erfolgen (z. B. 7 von [Achievement.target]); `null` bei den anderen. */
    val progress: Int? = null,
) {
    val unlocked: Boolean get() = unlockedDay != null
}

/** Was für die Achievements zählt; Tageswerte nur am Tagesende, sonst 0. */
internal data class AchievementFacts(
    val activeVehicles: Int = 0,
    val meanSpeedKmh: Double = Double.MAX_VALUE,
    val bridgesOpen: Int = -1,
    val signForest: Boolean = false,
    val cameras: Int = 0,
    val collapses: Int = 0,
    val plannings: Int = 0,
    val corsos: Int = 0,
    val revocations: Int = 0,
    val lawsuits: Int = 0,
    val reputation: Double = 0.0,
    val lateShare: Double = 0.0,
    val lettersToday: Int = 0,
    val demosToday: Int = 0,
    val recordRatio: Double = 0.0,
    val trucksStuck: Int = 0,
    val fineIncome: Long = 0,
    val repairs: Int = 0,
    val daysEnded: Int = 0,
    val campaigns: Int = 0,
)

/** Was die Errungenschaften dem Spiel melden: freigeschaltet oder fast geschafft (noch [missing] bis zum Ziel). */
internal interface AchievementEvents {
    fun unlocked(achievement: Achievement)

    fun almost(
        achievement: Achievement,
        missing: Int,
    )
}

internal class Achievements(
    private val events: AchievementEvents,
) {
    private val unlocked = LinkedHashMap<Achievement, Int>()
    private val progress = HashMap<Achievement, Int>()
    private val almostTold = HashSet<Achievement>()

    val titles: List<String> get() = unlocked.keys.map { it.title }

    fun state(): List<AchievementStatus> = Achievement.entries.map { AchievementStatus(it, unlocked[it], progress[it]) }

    private var stuckSeen = 0

    /**
     * Jeden Simulationsschritt: Was im Moment passiert, wird sofort gefeiert, nicht erst um Mitternacht – ein Raser
     * über dem Doppelten des Limits ([recordRatio]) und ein eingeklemmter LKW ([trucksStuck] seit Mitternacht).
     */
    fun checkLive(
        trucksStuck: Int,
        recordRatio: Double,
        fineIncome: Long,
        day: Int,
    ) {
        val firstFines = fineIncome >= FIRST_FINES_EUROS && Achievement.FIRST_FINES !in unlocked
        if (trucksStuck > stuckSeen || recordRatio > SPEEDER_RATIO || firstFines) {
            check(AchievementFacts(trucksStuck = trucksStuck, recordRatio = recordRatio, fineIncome = fineIncome), day)
        }
        stuckSeen = trucksStuck
    }

    fun check(
        facts: AchievementFacts,
        day: Int,
    ) {
        for (achievement in Achievement.entries) {
            if (achievement in unlocked) continue
            if (achievement.reached(facts)) {
                unlocked[achievement] = day
                events.unlocked(achievement)
            } else {
                trackProgress(achievement, facts)
            }
        }
    }

    /** Fortschritt merken und bei 80 % einmal „fast geschafft“ melden. */
    private fun trackProgress(
        achievement: Achievement,
        facts: AchievementFacts,
    ) {
        val count = achievement.progress?.invoke(facts) ?: return
        // Teilweise Fakten (z. B. nur live geprüft) zählen nicht rückwärts
        if (count < (progress[achievement] ?: 0)) return
        progress[achievement] = count
        if (count >= achievement.target * ALMOST && almostTold.add(achievement)) {
            events.almost(achievement, achievement.target - count)
        }
    }
}
