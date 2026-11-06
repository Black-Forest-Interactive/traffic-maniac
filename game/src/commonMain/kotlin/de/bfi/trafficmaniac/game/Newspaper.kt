package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.DayReport
import kotlin.math.roundToInt
import kotlin.random.Random

/** Ausgabe der Lokalzeitung am Morgen nach einem Spieltag. */
data class NewspaperEdition(
    val day: Int,
    val headlines: List<String>,
)

/** Was die Zeitung über einen Tag weiß. */
internal class DayNews(
    val report: DayReport,
    val previous: DayReport?,
    val finesToday: Long,
    /** Größte Überschreitung des Tages: gemessen / Limit in km/h. */
    val recordSpeed: Pair<Double, Int>?,
    val state: GameState,
)

/**
 * Die Pforzheimer Lokalzeitung. Mit PR-Vertrag ist sie freundlich, ohne PR wird sie kritisch –
 * Einzelschicksale interessieren die Leserbriefschreiber aber in keinem Fall.
 */
internal object Newspaper {
    private const val RECALL_ALARM = 60.0
    private const val STUCK_STORY = 50
    private const val TRAFFIC_JAM_RATIO = 1.1
    private const val MAX_HEADLINES = 3

    fun edition(news: DayNews): NewspaperEdition {
        val random = Random(news.report.day)
        val friendly = news.state.prContract != PrContract.NONE
        val headlines =
            listOfNotNull(
                recall(news),
                record(news, friendly),
                fines(news, friendly, random),
                traffic(news, random),
                stuckCommuter(news, random),
                pr(news, random),
            ).take(MAX_HEADLINES)
        return NewspaperEdition(news.report.day, headlines.ifEmpty { listOf(QUIET_DAY.random(random)) })
    }

    private fun recall(news: DayNews): String? =
        when {
            news.state.recallIndex >= RECALL_ALARM -> "Gemeinderat: Rücktrittsforderungen werden lauter"
            else -> null
        }

    private fun record(
        news: DayNews,
        friendly: Boolean,
    ): String? {
        val (measured, limit) = news.recordSpeed ?: return null
        val speed = measured.roundToInt()
        return if (friendly) {
            "Erfolg für die Verkehrssicherheit: Wir haben einen mit $speed in der $limit-er-Zone erwischt!"
        } else {
            "Mit $speed km/h in der $limit-er-Zone – Stadt präsentiert Rekord-Raser"
        }
    }

    private fun fines(
        news: DayNews,
        friendly: Boolean,
        random: Random,
    ): String? {
        if (news.finesToday <= 0) return null
        val amount = formatEuro(news.finesToday)
        return if (friendly) {
            listOf(
                "$amount für mehr Sicherheit: Stadt investiert in Verkehrsberuhigung",
                "Bürgermeister: „Jeder Euro fließt in die Sicherheit unserer Kinder“",
            ).random(random)
        } else {
            listOf(
                "Abzocke-Blitzer? Stadt kassiert $amount an einem Tag",
                "Leserbrief: „Die Stadt füllt sich die Taschen“ ($amount Bußgeld)",
            ).random(random)
        }
    }

    private fun traffic(
        news: DayNews,
        random: Random,
    ): String? {
        val previous = news.previous ?: return null
        if (news.report.meanTripMinutes < previous.meanTripMinutes * TRAFFIC_JAM_RATIO) return null
        return listOf(
            "Stau-Rekord: Pendler brauchten im Schnitt ${"%.0f".format(news.report.meanTripMinutes)} Minuten",
            "Halb Pforzheim kam heute zu spät",
        ).random(random)
    }

    private fun stuckCommuter(
        news: DayNews,
        random: Random,
    ): String? {
        if (news.report.stuckRemovals < STUCK_STORY) return null
        return listOf(
            "${news.report.stuckRemovals} Autofahrer gaben im Stau auf – " +
                "Leserbrief: „Hauptsache, MEIN Parkplatz bleibt“",
            "Pendler steht 5 Minuten still und fährt nach Hause – die Nachbarn zucken mit den Schultern",
        ).random(random)
    }

    private fun pr(
        news: DayNews,
        random: Random,
    ): String? {
        if (news.state.prContract == PrContract.NONE) return null
        return listOf(
            "Pforzheim wird zur Modellstadt der Verkehrsberuhigung",
            "Umfrage (von der Stadt bezahlt): 9 von 10 Bürgern lieben Tempo 20",
            "Spatenstich für das neue Verkehrskonzept – Fertigstellung voraussichtlich 2066",
        ).random(random)
    }

    private val QUIET_DAY =
        listOf(
            "Ruhiger Tag in Pforzheim – Verkehr fließt, niemand regt sich auf",
            "Goldstadt im Glück: Alle kamen pünktlich an",
        )
}
