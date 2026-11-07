package de.bfi.trafficmaniac.game

/** Achievements für Stadt-Extreme – sie gehen nicht in die Wertung ein. */
enum class Achievement(
    val title: String,
    val description: String,
    internal val reached: (AchievementFacts) -> Boolean,
) {
    TOTAL_STANDSTILL(
        "Totalstillstand",
        "Mindestens 300 Autos unterwegs, im Schnitt keine 3 km/h",
        { it.activeVehicles >= 300 && it.meanSpeedKmh < 3.0 },
    ),
    ALL_BRIDGES_CLOSED(
        "Alle Brücken gesperrt",
        "Keine einzige Brücke über Enz und Nagold ist mehr befahrbar",
        { it.bridgesOpen == 0 },
    ),
    HALF_LATE("Die halbe Stadt kam zu spät", "Mindestens die Hälfte der Pendler eines Tages zu spät", {
        it.lateShare >=
            0.5
    }),
    SIGN_FOREST(
        "Schilderwald",
        "Tempolimit, Nachtlimit, Vorfahrt, Radweg-Querung und zwei Blitzer auf engstem Raum",
        { it.signForest },
    ),
    LETTER_WAVE("Bescheid-Welle", "1.500 Bußgeldbescheide an einem Tag", { it.lettersToday >= 1_500 }),
    CAMERA_CAPITAL("Blitzer-Hauptstadt", "Zehn Blitzer gleichzeitig im Einsatz", { it.cameras >= 10 }),
    ENGINEERING("Ingenieurskunst", "Eine Brücke ist eingestürzt", { it.collapses > 0 }),
    PLANNING_CHAMPION(
        "Planungsweltmeister",
        "Drei Planfeststellungsverfahren laufen gleichzeitig",
        { it.plannings >= 3 },
    ),
    CRITICAL_MASS("Kritische Masse", "Ein Rad-Korso hat eine Hauptstraße lahmgelegt", { it.corsos > 0 }),
    DEMO_SEASON("Demo-Saison", "Drei Spontandemos an einem Tag", { it.demosToday >= 3 }),
    SPEEDER_OF_THE_YEAR(
        "Raser des Jahres",
        "Jemand fuhr mehr als doppelt so schnell wie erlaubt",
        { it.recordRatio > 2 },
    ),
    BIKE_CITY("Fahrradstadt wider Willen", "25 Führerscheine eingezogen", { it.revocations >= 25 }),
    CLASS_ACTION("Klagewelle", "Zehn Schadensersatzklagen", { it.lawsuits >= 10 }),
    STUCK("Eingeklemmt", "Ein LKW steckt in einer Nebenstraße fest", { it.trucksStuck > 0 }),
    PRESS_DARLING("Liebling der Presse", "Reputation über 90", { it.reputation > 90 }),
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
                message("Achievement freigeschaltet: ${achievement.title}!")
            }
        }
    }
}
