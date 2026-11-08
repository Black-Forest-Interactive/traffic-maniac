package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable

/** Achievements für Stadt-Extreme – sie gehen nicht in die Wertung ein. */
enum class Achievement(
    private val titleText: String,
    private val descriptionText: String,
    internal val reached: (AchievementFacts) -> Boolean,
) {
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
    CAMERA_CAPITAL(translatable("Blitzer-Hauptstadt"), translatable("Zehn Blitzer gleichzeitig im Einsatz"), {
        it.cameras >=
            10
    }),
    ENGINEERING(translatable("Ingenieurskunst"), translatable("Eine Brücke ist eingestürzt"), { it.collapses > 0 }),
    PLANNING_CHAMPION(
        translatable("Planungsweltmeister"),
        translatable("Drei Planfeststellungsverfahren laufen gleichzeitig"),
        { it.plannings >= 3 },
    ),
    CRITICAL_MASS(translatable("Kritische Masse"), translatable("Ein Rad-Korso hat eine Hauptstraße lahmgelegt"), {
        it.corsos >
            0
    }),
    DEMO_SEASON(translatable("Demo-Saison"), translatable("Drei Spontandemos an einem Tag"), { it.demosToday >= 3 }),
    SPEEDER_OF_THE_YEAR(
        translatable("Raser des Jahres"),
        translatable("Jemand fuhr mehr als doppelt so schnell wie erlaubt"),
        { it.recordRatio > 2 },
    ),
    BIKE_CITY(translatable("Fahrradstadt wider Willen"), translatable("25 Führerscheine eingezogen"), {
        it.revocations >=
            25
    }),
    CLASS_ACTION(translatable("Klagewelle"), translatable("Zehn Schadensersatzklagen"), { it.lawsuits >= 10 }),
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

data class AchievementStatus(
    val achievement: Achievement,
    /** Spieltag der Freischaltung, `null` = noch offen. */
    val unlockedDay: Int?,
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
)

internal class Achievements(
    private val message: (String) -> Unit,
) {
    private val unlocked = LinkedHashMap<Achievement, Int>()

    val titles: List<String> get() = unlocked.keys.map { it.title }

    fun state(): List<AchievementStatus> = Achievement.entries.map { AchievementStatus(it, unlocked[it]) }

    fun check(
        facts: AchievementFacts,
        day: Int,
    ) {
        for (achievement in Achievement.entries) {
            if (achievement !in unlocked && achievement.reached(facts)) {
                unlocked[achievement] = day
                message(tr("Achievement freigeschaltet: {title}!", "title" to achievement.title))
            }
        }
    }
}
