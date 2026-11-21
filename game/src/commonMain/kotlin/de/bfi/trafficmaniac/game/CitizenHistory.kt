package de.bfi.trafficmaniac.game

/**
 * Verlauf der Stimmung für die Seite „Bürger“: Zufriedenheit je Haushaltstyp und Bürgerzorn je Spielstunde seit
 * Spielbeginn, dazu die Zufriedenheit je Wohngebiet am Ende des Vortags (für den Trend der Brennpunkte).
 */
data class CitizenHistory(
    val byHousehold: Map<HouseholdType, List<Float>> = emptyMap(),
    val anger: List<Float> = emptyList(),
    val zonesYesterday: Map<Int, Double> = emptyMap(),
)

/** Sammelt den Verlauf stündlich und merkt sich die Gebiete zum Tagesende. */
internal class CitizenRecorder {
    private val byHousehold = HouseholdType.entries.associateWith { ArrayList<Float>() }
    private val anger = ArrayList<Float>()
    private var zonesYesterday = emptyMap<Int, Double>()

    fun hourPassed(
        moods: List<ZoneMood>,
        currentAnger: Double,
    ) {
        for ((type, list) in byHousehold) {
            val weights = moods.map { it.area * (it.households[type] ?: 0.0) }
            val total = weights.sum()
            val value =
                if (total >
                    0
                ) {
                    moods.indices.sumOf { weights[it] * moods[it].satisfactionOf(type) } / total
                } else {
                    1.0
                }
            list += value.toFloat()
        }
        anger += currentAnger.toFloat()
    }

    fun dayEnded(moods: List<ZoneMood>) {
        zonesYesterday = moods.associate { it.zoneId to it.satisfaction }
    }

    fun state() = CitizenHistory(byHousehold.mapValues { it.value.toList() }, anger.toList(), zonesYesterday)
}
