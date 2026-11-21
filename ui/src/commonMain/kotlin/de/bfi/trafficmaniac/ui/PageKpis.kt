package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.VehicleRole
import de.bfi.trafficmaniac.ui.components.KpiTile
import kotlin.math.roundToInt

/** Die Kennzahl-Kacheln oben auf jeder Seite (Entwurf 2: „KPIs oben, Inhalt im Raster“); `null` = keine. */
internal fun pageKpis(
    panel: SidePanel,
    frame: TrafficFrame,
    rules: GameRules,
): (@Composable RowScope.() -> Unit)? =
    when (panel) {
        SidePanel.TREASURY, SidePanel.BUDGET -> {
            { TreasuryKpis(frame) }
        }

        SidePanel.OFFICE -> {
            { OfficeKpis(frame) }
        }

        SidePanel.MAINTENANCE -> {
            { MaintenanceKpis(frame) }
        }

        SidePanel.PARKING -> {
            { ParkingKpis(frame) }
        }

        SidePanel.TRANSIT -> {
            { TransitKpis(frame) }
        }

        SidePanel.EMERGENCIES -> {
            { EmergencyKpis(frame) }
        }

        SidePanel.PRESS -> {
            { PressKpis(frame) }
        }

        SidePanel.CITIZENS -> {
            { CitizenKpis(frame, rules) }
        }

        SidePanel.EVENTS -> {
            { EventKpis(frame) }
        }

        SidePanel.ACHIEVEMENTS -> {
            { AchievementKpis(frame) }
        }

        else -> {
            null
        }
    }

@Composable
private fun RowScope.TreasuryKpis(frame: TrafficFrame) {
    val game = frame.game
    val minus = game.balance < 0
    val today = game.journal.today
    val income = today.filterKeys { it.income }.values.sum()
    val expenses = -today.filterKeys { !it.income }.values.sum()
    val saldo = income - expenses
    KpiTile(
        tr("Kasse"),
        formatEuro(game.balance),
        footer = if (minus) tr("im Minus") else null,
        valueColor = if (minus) TmColors.DangerText else TmColors.Ink,
        accent = if (minus) TmColors.Danger else null,
    )
    KpiTile(tr("Einnahmen heute"), formatEuro(income), valueColor = TmColors.SuccessText)
    KpiTile(tr("Ausgaben heute"), formatEuro(expenses), valueColor = TmColors.DangerText)
    KpiTile(
        tr("Saldo heute"),
        (if (saldo >= 0) "+" else "−") + formatEuro(kotlin.math.abs(saldo)),
        valueColor = if (saldo >= 0) TmColors.SuccessText else TmColors.DangerText,
    )
    KpiTile(
        tr("Blitzer-Bußgelder"),
        formatEuro(game.fineIncome),
        footer = tr("Amtszeit · dazu {tickets} Knöllchen", "tickets" to formatEuro(game.parkingOffice.ticketIncome)),
    )
}

@Composable
private fun RowScope.OfficeKpis(frame: TrafficFrame) {
    val office = frame.game.fineOffice
    val cameras = frame.game.cameras
    KpiTile(
        tr("Fotos bisher"),
        formatNumber(office.received.toLong()),
        footer = tr("{count} Geräte", "count" to cameras.size),
    )
    KpiTile(
        tr("Bußgelder bezahlt"),
        formatNumber(office.paid.toLong()),
        footer = formatEuro(frame.game.fineIncome),
        valueColor = TmColors.SuccessText,
    )
    KpiTile(
        tr("Aufklärungsquote"),
        if (office.processed > 0) percent(office.identificationRate) else "–",
        footer = if (office.processed > 0) null else tr("noch keine Fälle"),
        meter = office.identificationRate.toFloat().takeIf { office.processed > 0 },
    )
    KpiTile(
        tr("Aktenstapel"),
        formatNumber(office.backlog.toLong()),
        footer = tr("{staff} Sachbearbeiter", "staff" to office.staff),
        accent =
            if (office.backlog > 0 &&
                office.oldestCaseHours > office.deadlineDays * HOURS_PER_DAY / 2
            ) {
                TmColors.Warn
            } else {
                null
            },
    )
    KpiTile(
        tr("Ältester Fall"),
        if (office.backlog >
            0
        ) {
            ageText(office.oldestCaseHours)
        } else {
            "–"
        },
        footer =
            if (office.backlog >
                0
            ) {
                tr("Verjährung nach {days} Tagen", "days" to office.deadlineDays)
            } else {
                tr("noch keine Fälle")
            },
    )
}

@Composable
private fun RowScope.MaintenanceKpis(frame: TrafficFrame) {
    val works = frame.game.maintenance
    val unsecured = works.reports.count { it.status == DamageStatus.OPEN || it.status == DamageStatus.IGNORED }
    KpiTile(
        tr("Straßenzustand Ø"),
        "${works.meanConditionPercent} %",
        meter = works.meanConditionPercent / PERCENT,
    )
    KpiTile(
        tr("Offene Schäden"),
        "${works.reports.size}",
        footer = tr("{count} ungesichert", "count" to unsecured),
        accent = if (unsecured > 0) TmColors.Danger else null,
    )
    KpiTile(tr("Repariert"), "${works.repaired}", footer = works.lastRepaired)
    KpiTile(
        tr("Klagen"),
        "${works.lawsuits}",
        footer = formatEuro(works.lawsuitCosts),
        valueColor = if (works.lawsuits > 0) TmColors.DangerText else TmColors.Ink,
    )
}

@Composable
private fun RowScope.ParkingKpis(frame: TrafficFrame) {
    val parking = frame.current.parking
    val office = frame.game.parkingOffice
    KpiTile(
        tr("Parkhäuser belegt"),
        share(parking.lotOccupied, parking.lotCapacity),
        footer =
            tr(
                "{used} von {all}",
                "used" to formatNumber(parking.lotOccupied.toLong()),
                "all" to formatNumber(parking.lotCapacity.toLong()),
            ),
    )
    KpiTile(
        tr("Straßenrand belegt"),
        share(parking.curbOccupied, parking.curbCapacity),
        footer =
            tr(
                "{used} von {all}",
                "used" to formatNumber(parking.curbOccupied.toLong()),
                "all" to formatNumber(parking.curbCapacity.toLong()),
            ),
    )
    KpiTile(
        tr("Falschparker bisher"),
        formatNumber(office.illegalParkers.toLong()),
        footer = tr("{tickets} Knöllchen bisher", "tickets" to formatNumber(office.tickets.toLong())),
    )
    KpiTile(tr("Suchverkehr"), formatNumber(parking.searching.toLong()), footer = tr("Kfz suchen einen Platz"))
    KpiTile(
        tr("Einnahmen"),
        formatEuro(office.ticketIncome + office.feeIncome),
        footer = tr("Knöllchen + Gebühren"),
        valueColor = TmColors.SuccessText,
    )
}

@Composable
private fun RowScope.TransitKpis(frame: TrafficFrame) {
    val lines = frame.current.transit.lines
    val served = lines.sumOf { it.stopsServed }
    val punctual = if (served > 0) lines.sumOf { it.punctualShare * it.stopsServed } / served else null
    val worst = lines.filter { it.stopsServed > 0 }.maxByOrNull { it.averageDelaySeconds }
    KpiTile(
        tr("Pünktlich"),
        punctual?.let(::percent) ?: "–",
        footer =
            when {
                punctual == null -> tr("noch keine Fahrt")
                frame.current.transit.fromYesterday -> tr("Busse, gestern")
                else -> tr("Busse, heute")
            },
        meter = punctual?.toFloat(),
    )
    KpiTile(
        tr("Busse unterwegs"),
        "${lines.sumOf { it.buses }}",
        footer =
            tr(
                "{lines} Linien",
                "lines" to lines.map { it.ref }.distinct().size,
            ),
    )
    val yesterday = frame.current.transit.fromYesterday
    KpiTile(tr("Halte bedient"), formatNumber(served.toLong()), footer = if (yesterday) tr("gestern") else tr("heute"))
    // Wie viele umsteigen, richtet sich nach der Pünktlichkeit des Vortags
    KpiTile(
        tr("Umsteiger"),
        percent(frame.game.busModeShift),
        footer = tr("der Autofahrten – wegen der Pünktlichkeit gestern"),
    )
    // Sorgenkind nur, wenn eine Linie wirklich unpünktlich ist
    val problem = worst?.takeIf { it.punctualShare < 1.0 }
    KpiTile(
        tr("Sorgenkind"),
        problem?.ref ?: tr("keins"),
        footer =
            problem?.let { tr("Ø {delay}", "delay" to delayText(it.averageDelaySeconds)) }
                ?: tr("alles pünktlich"),
        accent = problem?.let { TmColors.Warn },
    )
}

@Composable
private fun RowScope.EmergencyKpis(frame: TrafficFrame) {
    val incidents = frame.current.incidents
    val snapshot = frame.current
    val fleet = snapshot.roles.count { VehicleRole.entries[it.toInt()] in SERVICE_ROLES }
    KpiTile(
        tr("Laufend"),
        "${incidents.size}",
        footer = if (incidents.isEmpty()) tr("Alles ruhig") else null,
        accent = if (incidents.isNotEmpty()) TmColors.Danger else null,
    )
    KpiTile(tr("Vor Ort"), "${incidents.sumOf { it.onScene.size }}", footer = tr("Einsatzkräfte"))
    KpiTile(tr("Unterwegs"), "${incidents.sumOf { it.onTheWay.size }}", footer = tr("Einsatzkräfte"))
    KpiTile(
        tr("Ø Anfahrt Rettung"),
        frame.game.incidentCounts.meanAmbulanceMinutes
            ?.let { tr("{minutes} min", "minutes" to formatDecimal(it, 1)) }
            ?: "–",
        footer = tr("{count} Fahrzeuge gerade unterwegs", "count" to fleet),
    )
}

@Composable
private fun RowScope.PressKpis(frame: TrafficFrame) {
    val game = frame.game
    val press = game.press
    KpiTile(tr("Reputation"), "${game.reputation.roundToInt()}", meter = (game.reputation / PERCENT).toFloat())
    KpiTile(tr("Ton der Zeitung"), press.toneLabel)
    KpiTile(tr("PR wirkt"), percent(press.prEffect))
    KpiTile(tr("Aufschlag für die Lücke"), percent(press.prGapSurcharge))
    KpiTile(
        tr("Spatenstiche"),
        "${press.ceremonies}",
        footer =
            press.lastCeremonyDay?.let { tr("letzter: Tag {day}", "day" to game.termDay(it)) }
                ?: tr("letzter: –"),
    )
}

@Composable
private fun RowScope.CitizenKpis(
    frame: TrafficFrame,
    rules: GameRules,
) {
    val game = frame.game
    val commute = game.commuteToday
    KpiTile(
        tr("Unzufrieden"),
        percent(game.unhappyShare),
        meter = game.unhappyShare.toFloat(),
        meterColor = TmColors.Danger,
        threshold = rules.angerThreshold.toFloat(),
        footer = tr("Zorn ab {percent}", "percent" to percent(rules.angerThreshold)),
    )
    // Trend: Zorn vor 24 Stunden gegen jetzt
    val history = game.citizenHistory.anger
    val before = history.getOrNull(history.size - 1 - HOURS_PER_DAY.toInt())
    val now = game.anger.roundToInt()
    KpiTile(
        tr("Bürgerzorn"),
        "$now",
        footer = before?.let { tr("gestern {before} → heute {now}", "before" to it.roundToInt(), "now" to now) },
        accent = if (game.anger > ANGER_WARN) TmColors.Warn else null,
    )
    // Dieselbe Regel wie im Kalender: Demos ab Bürgerzorn x, Zorn erst ab y % Unzufriedenen
    val chance = game.events.demoChancePerHour
    KpiTile(
        tr("Spontandemo-Risiko"),
        if (chance > 0) tr("{percent} % je Std.", "percent" to (chance * PERCENT).roundToInt()) else tr("keins"),
        footer = demoRule(rules),
        accent = if (chance > 0) TmColors.Danger else null,
    )
    // Kurz nach Mitternacht ist „heute“ leer – dann der Wert von gestern
    val today = commute.trips > 0
    val shown = if (today) commute else game.commuteYesterday
    KpiTile(
        tr("Pendler pünktlich"),
        if (shown.trips > 0) percent(1.0 - shown.late.toDouble() / shown.trips) else "–",
        footer =
            if (today) {
                tr("Auto und Bus, heute · {trips} Fahrten", "trips" to formatNumber(commute.trips.toLong()))
            } else {
                tr("Auto und Bus, gestern · {trips} Fahrten", "trips" to formatNumber(shown.trips.toLong()))
            },
    )
    KpiTile(
        tr("Abwahlindex"),
        "${game.recallIndex.roundToInt()}/100",
        meter = (game.recallIndex / PERCENT).toFloat(),
        meterColor = TmColors.Danger,
    )
}

@Composable
private fun RowScope.EventKpis(frame: TrafficFrame) {
    val events = frame.game.events
    val day = frame.current.gameTime.day
    // Die frühere Karte „Jetzt“ steckt hier: was läuft, wo, bis wann, wie weit – und ob noch mehr los ist
    val active = events.active.firstOrNull()
    KpiTile(
        tr("Jetzt"),
        active?.kind?.label ?: tr("ruhig"),
        footer =
            active?.let {
                listOfNotNull(
                    it.place,
                    it.until.ifEmpty { null },
                    tr(
                        "+{more} weitere",
                        "more" to events.active.size - 1,
                    ).takeIf { events.active.size > 1 },
                ).joinToString(" · ")
            },
        meter = active?.progress,
        meterColor = TmColors.Warn,
        accent = if (active != null) TmColors.Warn else null,
    )
    KpiTile(tr("Heute geplant"), "${events.calendar.count { it.day == day }}")
    KpiTile(tr("Diese Woche"), "${events.calendar.size}")
    KpiTile(tr("Dauerstau-Tage"), "${events.jamStreak}", footer = tr("lockt den Rad-Korso"))
}

@Composable
private fun RowScope.AchievementKpis(frame: TrafficFrame) {
    val list = frame.game.achievementList
    val done = list.count { it.unlocked }
    KpiTile(tr("Erreicht"), "$done/${list.size}", meter = if (list.isEmpty()) 0f else done.toFloat() / list.size)
}

/** „unter 1 Tag“, „1 Tag“, „3 Tage“. */
private fun ageText(hours: Double): String {
    val days = (hours / HOURS_PER_DAY).toInt()
    return when (days) {
        0 -> tr("unter 1 Tag")
        1 -> tr("1 Tag")
        else -> tr("{days} Tage", "days" to days)
    }
}

private fun percent(share: Double): String = "${(share * PERCENT).roundToInt()} %"

private fun share(
    part: Int,
    whole: Int,
): String = if (whole > 0) percent(part.toDouble() / whole) else "–"

private const val PERCENT = 100f
private const val HOURS_PER_DAY = 24.0
private const val ANGER_WARN = 50.0

/** Die Regel für Spontandemos – gleich formuliert auf „Bürger“ und im Kalender. */
internal fun demoRule(rules: GameRules): String =
    tr(
        "ab Bürgerzorn {anger} – Zorn wächst erst über {percent} Unzufriedenen",
        "anger" to rules.demoMinAnger.roundToInt(),
        "percent" to percent(rules.angerThreshold),
    )
