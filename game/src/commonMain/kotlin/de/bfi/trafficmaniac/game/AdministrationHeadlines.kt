package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.IncidentKind
import kotlin.math.roundToInt
import kotlin.random.Random

/** Schlagzeilen rund um Bauamt, Umleitungen und Parken (Teil der [Newspaper]). */
internal object AdministrationHeadlines {
    private const val PARKING_SEARCH_STORY_MINUTES = 15.0
    private const val TICKET_STORY = 150

    fun collapse(news: DayNews): String? {
        val (place, victims) = news.maintenance.collapses.firstOrNull() ?: return null
        return tr("Einsturz! {place} ist eingestürzt", "place" to place) +
            if (victims >
                0
            ) {
                tr(" – {victims} Autos in der Tiefe", "victims" to victims)
            } else {
                tr(" – zum Glück stand gerade niemand drauf")
            }
    }

    fun bridgeClosed(news: DayNews): String? =
        news.maintenance.closedBridges
            .firstOrNull()
            ?.let { tr("{value} gesperrt – LKW fahren kilometerweite Umwege", "value" to it) }

    fun truckStuck(
        news: DayNews,
        random: Random,
    ): String? {
        val stuck = news.report.incidents[IncidentKind.TRUCK_STUCK] ?: 0
        if (stuck == 0) return null
        return listOf(
            tr("LKW in Nebenstraße eingeklemmt – Fahrer telefoniert verzweifelt, Umleitung dicht"),
            tr("Navi sagt links, Hauswand sagt nein: {stuck} LKW stecken in der Umleitung fest", "stuck" to stuck),
        ).random(random)
    }

    fun crater(
        news: DayNews,
        friendly: Boolean,
    ): String? {
        val place = news.maintenance.ignoredCraters.firstOrNull() ?: return null
        return if (friendly) {
            tr("Neues Konzept: „Verkehrsberuhigung durch Fahrbahnstruktur“ in der {place}", "place" to place)
        } else {
            tr("Stadt ignoriert Krater in der {place}", "place" to place)
        }
    }

    /** Blitzer an einer Straße mit Warnschild: der Klassiker "Schild + absurdes Tempo + Blitzer". */
    fun ripOffSign(
        news: DayNews,
        friendly: Boolean,
    ): String? {
        val cameraSegments =
            news.state.cameras
                .map { it.segment }
                .toSet()
        val report =
            news.state.maintenance.reports.firstOrNull {
                it.status == DamageStatus.SIGNED && it.segments.any { segment -> segment in cameraSegments }
            } ?: return null
        return if (friendly) {
            tr("Stadt investiert in Verkehrssicherheit an der {place}", "place" to report.place)
        } else {
            tr(
                "Abzocke-Blitzer am Schlagloch: Tempo {signKmh} und Radarfalle in der {place}",
                "signKmh" to report.signKmh,
                "place" to report.place,
            )
        }
    }

    fun lawsuits(news: DayNews): String? =
        if (news.maintenance.lawsuits > 0) {
            tr(
                "Schadensersatz: Stadt zahlt {lawsuitCosts} für {lawsuits} Schlagloch-Klagen",
                "lawsuitCosts" to
                    formatEuro(
                        news.maintenance.lawsuitCosts,
                    ),
                "lawsuits" to news.maintenance.lawsuits,
            )
        } else {
            null
        }

    fun planning(news: DayNews): String? =
        news.maintenance.plannings.firstOrNull()?.let { (place, year) ->
            tr(
                "Planfeststellung für die {place} eröffnet: Fertigstellung voraussichtlich {year}",
                "place" to place,
                "year" to year,
            )
        }

    fun turbo(news: DayNews): String? =
        news.maintenance.turboRepairs.firstOrNull()?.let {
            tr("Über Nacht! Turbo-Konsortium repariert die {value} – Anwohner reiben sich die Augen", "value" to it)
        }

    fun parkingSearch(news: DayNews): String? {
        val longest = news.report.parkingSearchMinutes.maxOrNull() ?: return null
        return if (longest >= PARKING_SEARCH_STORY_MINUTES) {
            tr(
                "Parksuchverkehr: Autofahrer kreisen bis zu {longest} Minuten um den Block",
                "longest" to longest.roundToInt(),
            )
        } else {
            null
        }
    }

    fun tickets(
        news: DayNews,
        friendly: Boolean,
    ): String? {
        if (news.parking.tickets < TICKET_STORY) return null
        return if (friendly) {
            tr("Ordnungsamt sorgt für freie Gehwege: {tickets} Verwarnungen", "tickets" to news.parking.tickets)
        } else {
            tr(
                "Knöllchen-Flut: {tickets} Strafzettel an einem Tag ({ticketIncome})",
                "tickets" to news.parking.tickets,
                "ticketIncome" to
                    formatEuro(
                        news.parking.ticketIncome,
                    ),
            )
        }
    }
}
