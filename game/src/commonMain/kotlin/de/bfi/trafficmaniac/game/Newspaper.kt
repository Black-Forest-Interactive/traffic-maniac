package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.DayReport
import de.bfi.trafficmaniac.sim.IncidentKind
import kotlin.math.roundToInt
import kotlin.random.Random

/** Ausgabe der Lokalzeitung am Morgen nach einem Spieltag. */
data class NewspaperEdition(
    val day: Int,
    val headlines: List<String>,
    /** Leserbriefe – satirisch gleichgültig. */
    val letters: List<String> = emptyList(),
    /** Tonlage der Ausgabe, z. B. "Jubelpresse" oder "Verriss". */
    val tone: String = "",
    /** Name der Zeitung im Kopf. */
    val masthead: String = CityTexts().newspaper,
)

/** Was die Zeitung über einen Tag weiß. */
internal class DayNews(
    val report: DayReport,
    val previous: DayReport?,
    val finesToday: Long,
    /** Größte Überschreitung des Tages: gemessen / Limit in km/h. */
    val recordSpeed: Pair<Double, Int>?,
    val state: GameState,
    val office: FineOffice.Counters,
    val revocations: Int,
    val commute: CommuteStats,
    val maintenance: MaintenanceNews = MaintenanceNews(),
    val parking: ParkingNews = ParkingNews(),
    val events: EventNews = EventNews(),
    /** Spatenstiche und Eröffnungen des Tages (schon als Schlagzeile formuliert). */
    val ceremonies: List<String> = emptyList(),
    /** Tonlage der Zeitung −1..+1 (siehe [Press]). */
    val tone: Double = 0.0,
)

/**
 * Die Lokalzeitung der Stadt. Ihr Ton kommt aus dem [Press]-Modell: mit PR-Vertrag freundlich, ohne kritisch –
 * Einzelschicksale interessieren die Leserbriefschreiber aber in keinem Fall.
 */
internal object Newspaper {
    private const val RECALL_ALARM = 60.0
    private const val STUCK_STORY = 50
    private const val TRAFFIC_JAM_RATIO = 1.1
    private const val MAX_HEADLINES = 3
    private const val EXPIRED_STORY = 100
    private const val LETTER_WAVE = 1_500
    private const val AMBULANCE_STORY_MINUTES = 15.0
    private const val SOFTWARE_STORY = 20
    private const val OIL_STORY = 8
    private const val CHARGING_STORY_MINUTES = 20
    private const val HALF_LATE = 0.5
    private const val QUARTER_LATE = 0.25
    private const val PERCENT = 100

    fun edition(news: DayNews): NewspaperEdition {
        val random = Random(news.report.day)
        val friendly = news.tone > 0
        val headlines =
            listOfNotNull(
                recall(news),
                EventHeadlines.ceremony(news).takeIf { friendly },
                EventHeadlines.demos(news, friendly),
                AdministrationHeadlines.collapse(news),
                ambulance(news),
                AdministrationHeadlines.bridgeClosed(news),
                EventHeadlines.corso(news, friendly),
                EventHeadlines.planned(news),
                AdministrationHeadlines.truckStuck(news, random),
                AdministrationHeadlines.crater(news, friendly),
                AdministrationHeadlines.ripOffSign(news, friendly),
                expired(news),
                licenses(news),
                AdministrationHeadlines.lawsuits(news),
                record(news, friendly),
                letterWave(news),
                AdministrationHeadlines.planning(news),
                AdministrationHeadlines.turbo(news),
                AdministrationHeadlines.parkingSearch(news),
                AdministrationHeadlines.tickets(news, friendly),
                fines(news, friendly, random),
                traffic(news),
                stuckCommuter(news, random),
                breakdowns(news, random),
                charging(news),
                EventHeadlines.heavyTransport(news),
                EventHeadlines.ceremony(news),
                EventHeadlines.market(news),
                pr(news.state.city, friendly, random),
            ).distinct().take(MAX_HEADLINES)
        return NewspaperEdition(
            news.report.day,
            headlines.ifEmpty { listOf(quietDay(news.state.city).random(random)) },
            EventHeadlines.letters(news, friendly, random),
            Press.toneLabel(news.tone),
            news.state.city.newspaper,
        )
    }

    private fun recall(news: DayNews): String? =
        when {
            news.state.recallIndex >= RECALL_ALARM -> "Gemeinderat: Rücktrittsforderungen werden lauter"
            else -> null
        }

    private fun ambulance(news: DayNews): String? {
        val worst = news.report.ambulanceMinutes.maxOrNull() ?: return null
        return if (worst >= AMBULANCE_STORY_MINUTES) {
            "Rettungswagen steckt ${worst.roundToInt()} Minuten im Stau fest – Leserbrief: „Hauptsache, ich komme pünktlich heim“"
        } else {
            null
        }
    }

    private fun breakdowns(
        news: DayNews,
        random: Random,
    ): String? {
        val software = news.report.incidents[IncidentKind.SOFTWARE_FAILURE] ?: 0
        val oil = news.report.incidents[IncidentKind.OIL_SPILL] ?: 0
        return when {
            software >= SOFTWARE_STORY -> {
                listOf(
                    "$software Autos bleiben liegen: „Update wird installiert, bitte warten“",
                    "Bitte Neustart durchführen: $software moderne Autos streiken auf " +
                        "${news.state.city.genitive} Straßen",
                ).random(random)
            }

            oil >= OIL_STORY -> {
                "$oil Ölspuren an einem Tag – Straßenmeisterei im Dauereinsatz"
            }

            else -> {
                null
            }
        }
    }

    private fun charging(news: DayNews): String? {
        val waits = news.report.chargingWaitMinutes
        if (waits.isEmpty()) return null
        val longest = waits.max().roundToInt()
        return if (longest >=
            CHARGING_STORY_MINUTES
        ) {
            "Ladesäulen-Chaos: E-Autofahrer warten bis zu $longest Minuten"
        } else {
            null
        }
    }

    private fun expired(news: DayNews): String? =
        if (news.office.expired >=
            EXPIRED_STORY
        ) {
            "Bußgeldstelle überlastet: ${news.office.expired} Blitzerfotos verjährt"
        } else {
            null
        }

    private fun licenses(news: DayNews): String? =
        if (news.revocations > 0) "${news.revocations}× Führerschein weg – Pendler steigen aufs Rad um" else null

    private fun letterWave(news: DayNews): String? =
        if (news.office.paid >= LETTER_WAVE) "Bescheid-Welle: ${news.office.paid} Bußgeldbriefe an einem Tag" else null

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

    private fun traffic(news: DayNews): String? {
        val late = news.commute.lateShare
        val previous = news.previous
        val jam = previous != null && news.report.meanTripMinutes >= previous.meanTripMinutes * TRAFFIC_JAM_RATIO
        return when {
            late >= HALF_LATE -> "Halb ${news.state.city.name} kam heute zu spät"
            late >= QUARTER_LATE -> "${(late * PERCENT).roundToInt()} % der Pendler zu spät – Chefs genervt"
            jam -> "Stau-Rekord: Pendler brauchten im Schnitt ${"%.0f".format(news.report.meanTripMinutes)} Minuten"
            else -> null
        }
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
        city: CityTexts,
        friendly: Boolean,
        random: Random,
    ): String? {
        if (!friendly) return null
        return listOf(
            "${city.name} wird zur Modellstadt der Verkehrsberuhigung",
            "Umfrage (von der Stadt bezahlt): 9 von 10 Bürgern lieben Tempo 20",
            "Spatenstich für das neue Verkehrskonzept – Fertigstellung voraussichtlich 2066",
        ).random(random)
    }

    private fun quietDay(city: CityTexts) =
        listOf(
            "Ruhiger Tag in ${city.name} – Verkehr fließt, niemand regt sich auf",
            "${city.nickname} im Glück: Alle kamen pünktlich an",
        )
}
