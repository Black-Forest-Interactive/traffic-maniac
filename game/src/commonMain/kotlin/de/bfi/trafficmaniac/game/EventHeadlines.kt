package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.EventKind
import kotlin.random.Random

/** Schlagzeilen zu Ereignissen und Feierlichkeiten – und die Leserbriefe darunter. */
internal object EventHeadlines {
    private const val MAX_LETTERS = 2
    private const val JAM_LATE_SHARE = 0.25

    fun corso(
        news: DayNews,
        friendly: Boolean,
    ): String? {
        val place = news.events.corsos.firstOrNull() ?: return null
        return if (friendly) {
            "${news.state.city.name} radelt: Fröhlicher Korso auf der $place – Stadt lobt „gelebte Verkehrswende“"
        } else {
            "Militante Radler legen die $place lahm – der neue Radweg daneben bleibt leer"
        }
    }

    fun demos(
        news: DayNews,
        friendly: Boolean,
    ): String? {
        val demos = news.events.demos
        if (demos.isEmpty()) return null
        val place = demos.first().first
        return when {
            friendly -> "Lebendige Demokratie: Bürger diskutieren angeregt auf der $place"
            demos.size > 1 -> "Wut auf die Verkehrspolitik: ${demos.size} Demos legen die Stadt lahm"
            else -> "Spontandemo auf der $place: „Wir wollen unsere Ampel zurück!“"
        }
    }

    fun planned(news: DayNews): String? {
        val (kind, place) = news.events.planned.firstOrNull { it.first != EventKind.MARKET } ?: return null
        return when (kind) {
            EventKind.MARATHON -> "Marathon auf der $place: Läufer schneller als jedes Auto"
            EventKind.CITY_FESTIVAL -> "Stadtfest in der $place – Anwohner feiern, Pendler fluchen"
            else -> null
        }
    }

    fun market(news: DayNews): String? =
        news.events.planned
            .firstOrNull { it.first == EventKind.MARKET }
            ?.let { "Wochenmarkt: Gemüse statt Durchgangsverkehr in der ${it.second}" }

    fun heavyTransport(news: DayNews): String? =
        if (news.events.heavyTransports > 0) {
            "Schwertransport kriecht nachts durch die Stadt – Anwohner zählen die Achsen"
        } else {
            null
        }

    fun ceremony(news: DayNews): String? = news.ceremonies.firstOrNull()

    /** Leserbriefe: Einzelschicksale interessieren niemanden, Hauptsache, es trifft einen nicht selbst. */
    fun letters(
        news: DayNews,
        friendly: Boolean,
        random: Random,
    ): List<String> {
        val letters = ArrayList<String>()
        if (news.events.corsos.isNotEmpty()) {
            letters += "„Endlich mal Ruhe vor meinem Haus. Von mir aus kann jeden Tag Korso sein.“ – R., Nordstadt"
        }
        if (news.events.demos.isNotEmpty()) {
            letters += "„Demo vor meiner Tür? Mir egal – Hauptsache, MEIN Parkplatz bleibt.“ – K., Weststadt"
        }
        if (news.commute.lateShare >= JAM_LATE_SHARE) {
            letters +=
                if (friendly) {
                    "„Ich stand zwei Stunden im Stau. Aber in der Zeitung steht, alles ist super – dann wird es stimmen.“"
                } else {
                    "„Zwei Stunden für fünf Kilometer. Wer regiert hier eigentlich?“ – M., Brötzingen"
                }
        }
        if (news.finesToday > 0) {
            letters += "„Ich fahre nie zu schnell. Die anderen schon. Weiter so!“ – Name der Redaktion bekannt"
        }
        if (news.maintenance.ignoredCraters.isNotEmpty()) {
            letters += "„Das Schlagloch vor meiner Tür ist inzwischen ein Biotop. Bitte nicht reparieren.“"
        }
        if (letters.isEmpty()) letters += QUIET_LETTERS.random(random)
        return letters.take(MAX_LETTERS)
    }

    private val QUIET_LETTERS =
        listOf(
            "„Früher war mehr Stau. Man hatte wenigstens Zeit zum Radiohören.“",
            "„Warum steht in der Zeitung nichts über MEINE Straße?“",
        )
}
