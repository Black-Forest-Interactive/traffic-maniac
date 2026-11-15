package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.DamageResponse
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.PrContract
import de.bfi.trafficmaniac.game.RepairMethod
import de.bfi.trafficmaniac.game.Strictness
import de.bfi.trafficmaniac.game.ZoneMood
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.sim.DayReport
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val DEFAULT_DAYS = 7
private const val SECONDS_PER_HOUR = 3_600
private const val HOURS_PER_DAY = 24
private const val HEADLESS_TICKS_PER_SECOND = 5
private const val TARGET_SEGMENTS = 40
private const val TARGET_RADIUS_METERS = 2_500f
private const val MIN_SEGMENT_LENGTH_METERS = 150f
private const val PERCENT = 100
private const val MODERATE_LIMIT = 0.25
private const val SAVING_DAYS = 3
private const val OFFICE_HOURS = 8

/**
 * Kopflos-Runner für das Balancing: spielt mehrere Strategien parallel ohne Oberfläche durch und gibt
 * je Spieltag Kasse, Bußgelder, Zorn, Abwahlindex und Reputation aus.
 *
 * Aufruf: `./gradlew :desktop-app:headless --args="[Tage] [Strategie,Strategie,...]"`
 * Strategien: nichts, einsteiger, abkassieren, abkassieren-pr, massvoll-pr, schild-blitzer, reparieren, parken-weg,
 * parken-teuer, pr-feiern. Für die ersten [CASH_LOG_DAYS] Tage steht unter jeder Tageszeile die Kasse je Spielstunde.
 */
fun main(args: Array<String>) {
    val city = CityPacks.discover().first { it.id == "pforzheim" }.map
    val days = args.getOrNull(0)?.toInt() ?: DEFAULT_DAYS
    val names = args.getOrNull(1)?.split(',') ?: Strategy.entries.map { it.id }
    val strategies = names.map { name -> Strategy.entries.first { it.id == name } }
    // Fehlende Balancing-Dateien einmal vorab anlegen – nicht gleichzeitig aus mehreren Threads
    Balancing.gameRules()
    Balancing.simulationConfig()
    val pool = Executors.newFixedThreadPool(strategies.size)
    val results = strategies.map { strategy -> pool.submit<String> { play(city, strategy, days) } }
    results.forEach { println(it.get()) }
    pool.shutdown()
}

private enum class Strategy(
    val id: String,
    val description: String,
) {
    NOTHING("nichts", "Nichts tun"),
    BEGINNER(
        "einsteiger",
        "Tempo 30 + Blitzer auf zentralen Hauptstraßen, sobald das Geld reicht – wie ein neuer Spieler",
    ),
    RIP_OFF("abkassieren", "Stündlich alles Geld in Tempo 20 + Blitzer auf zentralen Hauptstraßen, keine PR"),
    RIP_OFF_PR("abkassieren-pr", "Wie abkassieren, dazu Premium-PR und Totschlagargumente"),
    MODERATE_PR("massvoll-pr", "Premium-PR, Totschlagargumente, neue Blitzer nur solange unter 25 % unzufrieden sind"),
    SIGN_CAMERA("schild-blitzer", "Jeder Schaden bekommt ein Warnschild (Tempo 10) und einen Blitzer dahinter"),
    REPAIR("reparieren", "Jeden Schaden per Ausschreibung reparieren, wenn das Geld reicht, sonst Planfeststellung"),
    PARKING("parken-weg", "Parkplätze abschaffen, Straßenrand zu Blumenkübeln, 10 Politessen, gnadenlos"),
    EXPENSIVE_PARKING("parken-teuer", "Parken überall 3 €/Std., Brötchentaste weg, 6 Politessen"),
    CEREMONY("pr-feiern", "Standard-PR und jeden Tag um 11 Uhr ein Spatenstich"),
}

private fun play(
    city: CityMap,
    strategy: Strategy,
    days: Int,
): String {
    val config = Balancing.simulationConfig().copy(ticksPerSecond = HEADLESS_TICKS_PER_SECOND)
    val game = CityGame(city, config, Balancing.gameRules())
    val log = StringBuilder("== ${strategy.id}: ${strategy.description}\n")
    val targets = ArrayDeque(centralMainRoads(city))
    if (strategy == Strategy.RIP_OFF_PR || strategy == Strategy.MODERATE_PR) {
        game.submit(GameCommand.SetPrContract(PrContract.PREMIUM))
        game.submit(GameCommand.SetArgumentsCampaign(true))
    }
    if (strategy == Strategy.CEREMONY) game.submit(GameCommand.SetPrContract(PrContract.STANDARD))
    if (strategy == Strategy.PARKING) abolishParking(game)
    if (strategy == Strategy.EXPENSIVE_PARKING) expensiveParking(game)
    log.append(
        "Tag | Kasse        | Bußgelder    | unzufrieden | Zorn | Abwahl | Reputation | Personal | Stapel | verjährt" +
            " | Zustand | Schäden (offen/ign.) | Klagen     | Parken     | Knöllchen  | Falschp. | Suche max" +
            " | geparkt (Rand/Platz) | Presse      | Ereignisse\n",
    )
    for (day in 1..days) {
        if (!playDay(game, strategy, targets, day, days, log)) break
        val state = game.state()
        val report = game.simulation.dayReports.last()
        log.append(dayLine(day, state, report))
        log.append(moodLine(state))
        state.latestEdition?.let { edition ->
            log.append("      Zeitung: ${edition.headlines.joinToString(" / ")}\n")
            log.append("      Leserbrief: ${edition.letters.firstOrNull().orEmpty()}\n")
        }
        if (state.isGameOver) break
    }
    val score = game.state().score
    log.append(
        "Punkte: ${score.points} (verdient ${formatEuro(score.earnings)} × ${"%.2f".format(score.reputationFactor)})\n",
    )
    return log.toString()
}

/** Spielt einen Tag; false, wenn man mittendrin abgewählt wurde (dann gibt es keinen Tagesbericht mehr). */
private fun playDay(
    game: CityGame,
    strategy: Strategy,
    targets: ArrayDeque<Int>,
    day: Int,
    days: Int,
    log: StringBuilder,
): Boolean {
    val ticksPerHour = SECONDS_PER_HOUR * game.simulation.config.ticksPerSecond
    val cash = ArrayList<String>()
    for (hour in 0 until HOURS_PER_DAY) {
        playHour(game, strategy, targets, saving = day > days - SAVING_DAYS)
        repeat(ticksPerHour) { game.step() }
        if (day <= CASH_LOG_DAYS) cash += "${game.simulation.gameTime.hour}h ${thousands(game.state().balance)}"
        if (game.isGameOver) {
            val unhappy = (game.state().unhappyShare * PERCENT).roundToInt()
            log.append("  $day | abgewählt um $hour Uhr, $unhappy % unzufrieden\n")
            log.append(moodLine(game.state()))
            return false
        }
    }
    if (cash.isNotEmpty()) log.append("      Kasse (Tsd. €): ${cash.joinToString(" | ")}\n")
    return true
}

private fun thousands(euros: Long): String = (euros / EUROS_PER_THOUSAND).toString()

/** Stimmung im Flächenmittel je Bedürfnis – zeigt, woran die Bürger leiden (oder eben nicht). */
private fun moodLine(state: GameState): String {
    val moods = state.zoneMoods
    val total = moods.sumOf { it.area.toDouble() }.coerceAtLeast(1.0)

    fun mean(value: (ZoneMood) -> Double) = moods.sumOf { value(it) * it.area } / total
    return (
        "      Stimmung: schnell %.2f, Mobilität %.2f, Ruhe %.2f, Luft %.2f," +
            " gesamt %.2f (schlechtestes Gebiet %.2f)\n"
    ).format(
        mean { it.speedSatisfaction },
        mean { it.mobilitySatisfaction },
        mean { it.quietSatisfaction },
        mean { it.airSatisfaction },
        mean { it.satisfaction },
        moods.minOfOrNull { it.satisfaction } ?: 1.0,
    )
}

/** Eine Zeile der Tabelle: Kasse, Stimmung, Verwaltung, Parken, Presse und Ereignisse. */
private fun dayLine(
    day: Int,
    state: GameState,
    report: DayReport,
): String {
    val reports = state.maintenance.reports
    return (
        "%3d | %12s | %12s | %10d%% | %4d | %6d | %10d | %8d | %6d | %8d" +
            " | %6d%% | %9d / %-8d | %10s | %10s | %10s | %8d | %6.0f min | %6d / %-6d" +
            "      | %-11s | %s%s\n"
    ).format(
        day,
        formatEuro(state.balance),
        formatEuro(state.fineIncome),
        (state.unhappyShare * PERCENT).roundToInt(),
        state.anger.roundToInt(),
        state.recallIndex.roundToInt(),
        state.reputation.roundToInt(),
        state.fineOffice.staff,
        state.fineOffice.backlog,
        state.fineOffice.expired,
        state.maintenance.meanConditionPercent,
        reports.count { it.status == DamageStatus.OPEN },
        reports.count { it.status == DamageStatus.IGNORED },
        formatEuro(state.maintenance.lawsuitCosts),
        formatEuro(state.parkingOffice.feeIncome),
        formatEuro(state.parkingOffice.ticketIncome),
        report.illegalParkers,
        report.parkingSearchMinutes.maxOrNull() ?: 0.0,
        report.parkedCurb,
        report.parkedLots,
        state.press.toneLabel,
        state.events.totals.entries
            .joinToString { (kind, count) -> "$count× ${kind.label}" },
        if (state.isGameOver) "  ← ABGEWÄHLT" else "",
    )
}

/** Die Entscheidungen einer Stunde. In den letzten Tagen ([saving]) wird gespart: Neue Blitzer lohnen nicht mehr. */
private fun playHour(
    game: CityGame,
    strategy: Strategy,
    targets: ArrayDeque<Int>,
    saving: Boolean,
) {
    // Abkassieren: Jede Stunde werden die Einnahmen in die nächste Blitzer-Strecke gesteckt
    val calm = game.state().unhappyShare < MODERATE_LIMIT
    val investing = !saving && (strategy != Strategy.MODERATE_PR || calm)
    val camerasEverywhere = strategy in setOf(Strategy.RIP_OFF, Strategy.RIP_OFF_PR, Strategy.MODERATE_PR)
    if (camerasEverywhere && investing) investInCameras(game, targets)
    if (strategy == Strategy.BEGINNER && !saving) investInCameras(game, targets, kmh = BEGINNER_KMH)
    if (strategy != Strategy.NOTHING) staffFineOffice(game)
    if (strategy == Strategy.SIGN_CAMERA) signAndCamera(game)
    if (strategy == Strategy.REPAIR) repairEverything(game)
    if (strategy == Strategy.CEREMONY && game.simulation.gameTime.hour == CEREMONY_HOUR) {
        game.submit(GameCommand.HoldCeremony)
    }
}

/** Schild + absurdes Tempo + Blitzer an jedem gemeldeten Straßenschaden. */
private fun signAndCamera(game: CityGame) {
    val state = game.state()
    val cameraSegments = state.cameras.map { it.segment }.toSet()
    var budget = state.balance
    for (report in state.maintenance.reports) {
        if (report.status != DamageStatus.OPEN) continue
        if (!report.canSign) {
            game.submit(
                GameCommand.RespondToDamage(
                    report.id,
                    if (report.canClose) DamageResponse.Close else DamageResponse.Ignore,
                ),
            )
            continue
        }
        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.WarningSign(SIGN_KMH)))
        budget -= game.rules.warningSignCost
        if (report.segment >= 0 && report.segment !in cameraSegments && budget >= game.rules.cameraCost) {
            game.submit(GameCommand.PlaceCamera(report.segment))
            budget -= game.rules.cameraCost
        }
    }
}

/** Ordentliche Verwaltung: Ausschreibung, solange Geld da ist, sonst Planfeststellung. */
private fun repairEverything(game: CityGame) {
    val state = game.state()
    var budget = state.balance
    for (report in state.maintenance.reports) {
        if (report.status != DamageStatus.OPEN && report.status != DamageStatus.IGNORED) continue
        val method = if (budget > REPAIR_RESERVE) RepairMethod.TENDER else RepairMethod.PLANNING
        game.submit(GameCommand.RespondToDamage(report.id, DamageResponse.Repair(method)))
        if (method == RepairMethod.TENDER) budget -= game.rules.repairCostCrater
    }
}

private fun abolishParking(game: CityGame) {
    repeat(game.simulation.parking.lotCount) { game.submit(GameCommand.SetParkingCapacity(it, 0)) }
    repeat(CURB_STEPS) { game.submit(GameCommand.ConvertCurbParking) }
    game.submit(GameCommand.HireWardens(PARKING_WARDENS))
    game.submit(GameCommand.SetStrictness(Strictness.RUTHLESS))
}

private fun expensiveParking(game: CityGame) {
    game.submit(GameCommand.SetParkingFee(null, EXPENSIVE_FEE))
    game.submit(GameCommand.SetCurbFee(EXPENSIVE_FEE))
    game.submit(GameCommand.SetBreadButton(false))
    game.submit(GameCommand.HireWardens(EXPENSIVE_WARDENS))
}

private const val EXPENSIVE_FEE = 3.0
private const val RIP_OFF_KMH = 20
private const val BEGINNER_KMH = 30
private const val CASH_LOG_DAYS = 2
private const val EUROS_PER_THOUSAND = 1_000L
private const val CEREMONY_HOUR = 11
private const val EXPENSIVE_WARDENS = 4
private const val SIGN_KMH = 10
private const val REPAIR_RESERVE = 60_000L
private const val CURB_STEPS = 3
private const val PARKING_WARDENS = 10

/** Stellt Sachbearbeiter ein, wenn der Aktenstapel mehr als zwei Arbeitstage Arbeit enthält. */
private fun staffFineOffice(game: CityGame) {
    val office = game.state().fineOffice
    val dailyCapacity = (office.staff + office.hiring) * game.rules.casesPerStaffHour * OFFICE_HOURS
    if (office.backlog > 2 * dailyCapacity) game.submit(GameCommand.HireStaff(2))
}

private fun investInCameras(
    game: CityGame,
    targets: ArrayDeque<Int>,
    kmh: Int = RIP_OFF_KMH,
) {
    val price = game.rules.cameraCost + game.rules.speedLimitSignCost
    var budget = game.state().balance
    while (targets.isNotEmpty() && budget >= price) {
        val segment = targets.removeFirst()
        game.submit(GameCommand.SetSpeedLimit(segment, kmh = kmh))
        game.submit(GameCommand.PlaceCamera(segment))
        budget -= price
    }
}

/** Die längsten Haupt- und Nebenverkehrsstraßen nahe der Stadtmitte – dort, wo am meisten los ist. */
private fun centralMainRoads(city: CityMap): List<Int> {
    val centerX = city.bounds.centerX
    val centerY = city.bounds.centerY
    val mainClasses = setOf(RoadClass.TRUNK, RoadClass.PRIMARY, RoadClass.SECONDARY)
    return city.roads.segments
        .filter { it.roadClass in mainClasses }
        .filter { segment ->
            val g = segment.geometry
            val length =
                (1 until g.pointCount).sumOf { i ->
                    hypot(g.x(i) - g.x(i - 1), g.y(i) - g.y(i - 1)).toDouble()
                }
            length > MIN_SEGMENT_LENGTH_METERS && hypot(g.x(0) - centerX, g.y(0) - centerY) < TARGET_RADIUS_METERS
        }.sortedBy { hypot(it.geometry.x(0) - centerX, it.geometry.y(0) - centerY) }
        .take(TARGET_SEGMENTS)
        .map { it.id }
}
