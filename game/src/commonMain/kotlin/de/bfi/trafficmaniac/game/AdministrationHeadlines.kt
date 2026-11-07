package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.sim.IncidentKind
import kotlin.math.roundToInt
import kotlin.random.Random

/** Schlagzeilen rund um Bauamt, Umleitungen und Parken (Teil der [Newspaper]). */
internal object AdministrationHeadlines {
    private const val PARKING_SEARCH_STORY_MINUTES = 15.0
    private const val TICKET_STORY = 150

    fun collapse(news: DayNews): String? {
        val (place, victims) = news.maintenance.collapses.firstOrNull() ?: return null
        return "Einsturz! $place ist eingestürzt" +
            if (victims > 0) " – $victims Autos in der Tiefe" else " – zum Glück stand gerade niemand drauf"
    }

    fun bridgeClosed(news: DayNews): String? =
        news.maintenance.closedBridges
            .firstOrNull()
            ?.let { "$it gesperrt – LKW fahren kilometerweite Umwege" }

    fun truckStuck(
        news: DayNews,
        random: Random,
    ): String? {
        val stuck = news.report.incidents[IncidentKind.TRUCK_STUCK] ?: 0
        if (stuck == 0) return null
        return listOf(
            "LKW in Nebenstraße eingeklemmt – Fahrer telefoniert verzweifelt, Umleitung dicht",
            "Navi sagt links, Hauswand sagt nein: $stuck LKW stecken in der Umleitung fest",
        ).random(random)
    }

    fun crater(
        news: DayNews,
        friendly: Boolean,
    ): String? {
        val place = news.maintenance.ignoredCraters.firstOrNull() ?: return null
        return if (friendly) {
            "Neues Konzept: „Verkehrsberuhigung durch Fahrbahnstruktur“ in der $place"
        } else {
            "Stadt ignoriert Krater in der $place"
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
            "Stadt investiert in Verkehrssicherheit an der ${report.place}"
        } else {
            "Abzocke-Blitzer am Schlagloch: Tempo ${report.signKmh} und Radarfalle in der ${report.place}"
        }
    }

    fun lawsuits(news: DayNews): String? =
        if (news.maintenance.lawsuits > 0) {
            "Schadensersatz: Stadt zahlt ${formatEuro(
                news.maintenance.lawsuitCosts,
            )} für ${news.maintenance.lawsuits} Schlagloch-Klagen"
        } else {
            null
        }

    fun planning(news: DayNews): String? =
        news.maintenance.plannings.firstOrNull()?.let { (place, year) ->
            "Planfeststellung für die $place eröffnet: Fertigstellung voraussichtlich $year"
        }

    fun turbo(news: DayNews): String? =
        news.maintenance.turboRepairs.firstOrNull()?.let {
            "Über Nacht! Turbo-Konsortium repariert die $it – Anwohner reiben sich die Augen"
        }

    fun parkingSearch(news: DayNews): String? {
        val longest = news.report.parkingSearchMinutes.maxOrNull() ?: return null
        return if (longest >= PARKING_SEARCH_STORY_MINUTES) {
            "Parksuchverkehr: Autofahrer kreisen bis zu ${longest.roundToInt()} Minuten um den Block"
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
            "Ordnungsamt sorgt für freie Gehwege: ${news.parking.tickets} Verwarnungen"
        } else {
            "Knöllchen-Flut: ${news.parking.tickets} Strafzettel an einem Tag (${formatEuro(
                news.parking.ticketIncome,
            )})"
        }
    }
}
