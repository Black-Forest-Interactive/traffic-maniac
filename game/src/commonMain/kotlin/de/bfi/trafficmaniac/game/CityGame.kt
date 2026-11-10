package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.sim.Weekday
import kotlinx.coroutines.channels.Channel

/**
 * Das Spiel um die Simulation: Kasse, Werkzeuge, Bußgeldstelle, Bürger und Politik.
 *
 * Befehle kommen über [submit] von der Oberfläche (beliebiger Thread) und werden in [step] vor dem
 * nächsten Simulationsschritt ausgeführt – die Simulation selbst wird nur vom Spiel-Thread angefasst.
 */
class CityGame(
    city: CityMap,
    config: SimulationConfig = SimulationConfig(),
    val rules: GameRules = GameRules(),
) {
    val simulation = Simulation(city, config)
    private val commands = Channel<GameCommand>(Channel.UNLIMITED)
    private val messages = ArrayDeque<GameMessage>()
    private val treasury = Treasury(rules.startBudget) { message(it, kind = MessageKind.PROBLEM) }
    private val works = PublicWorks(city, simulation, rules, treasury) { message(it, kind = MessageKind.ACTION) }
    private val timing = SignalTiming(city, simulation, rules, treasury) { message(it, kind = MessageKind.ACTION) }
    private val roadworks =
        Roadworks(city, simulation, rules, treasury, works) { message(it, kind = MessageKind.ACTION) }
    private val winter = WinterService(simulation, rules, treasury) { message(it, kind = MessageKind.PROBLEM) }
    private val surveillance =
        Surveillance(city, simulation, rules, treasury, works) { message(it, kind = MessageKind.ACTION) }
    private val citizens = Citizens(city.zones, rules)
    private val drivers = DriverRegistry(rules, citizens)
    private val office = FineOffice(rules, treasury, citizens, drivers) { message(it, kind = MessageKind.ACTION) }
    private val maintenance =
        Maintenance(
            city,
            simulation,
            rules,
            treasury,
            works,
        ) { text, place -> message(text, place, MessageKind.PROBLEM) }
    private val parkingOffice =
        ParkingOffice(simulation, rules, treasury, citizens) { message(it, kind = MessageKind.ACTION) }
    private val politics = Politics(rules)
    private val events =
        EventPlanner(city, simulation, rules) { text, place -> message(text, place, MessageKind.PROBLEM) }
    private val press = Press(rules, treasury) { message(it, kind = MessageKind.SUCCESS) }
    private val achievements = Achievements { message(it, kind = MessageKind.SUCCESS) }
    private val firstDay = simulation.gameTime.day
    private val bridges =
        city.roads.segments
            .filter { it.bridge && !it.passive }
            .map { it.id }
    private var prContract = PrContract.NONE
    private var argumentsCampaign = false
    private var moods: List<ZoneMood> = citizens.moods(argumentsCampaign = false)
    private var unhappyShare = 0.0
    private var fineIncomeAtHourStart = 0L
    private var currentHour = hourIndex()
    private var lastBudgetRound: BudgetRound? = null
    private var finesToday = 0L
    private var recordToday: Pair<Double, Int>? = null

    /** Höchstes Verhältnis gemessen/erlaubt unter den heutigen Blitzerfotos (für „Raser des Jahres“). */
    private var recordRatioToday = 0.0
    private var latestEdition: NewspaperEdition? = null
    private var commuteYesterday = CommuteStats()

    init {
        // Wer keinen Führerschein hat, fährt nicht
        simulation.tripAllowed = { drivers.isAllowedToDrive(it.person, simulation.time) }
    }

    /** Abgewählt – das Spiel ist vorbei. */
    val isGameOver: Boolean get() = politics.isRecalled

    /** Amtszeit regulär zu Ende. */
    val isTermOver: Boolean get() = simulation.dayReports.size >= rules.termDays

    val isFinished: Boolean get() = isGameOver || isTermOver

    fun submit(command: GameCommand) {
        commands.trySend(command)
    }

    fun step() {
        if (isFinished) return
        var executed = false
        while (true) {
            val command = commands.tryReceive().getOrNull() ?: break
            execute(command)
            executed = true
        }
        // Der Schilderwald entsteht durch Werkzeuge – er wird sofort gefeiert, nicht erst zur vollen Stunde
        if (executed && PublicWorks.SIGN_FOREST in works.achievements) {
            achievements.check(AchievementFacts(signForest = true), simulation.gameTime.day)
        }
        val reportsBefore = simulation.dayReports.size
        simulation.step()
        collectFines()
        winter.update()
        surveillance.update()
        achievements.checkLive(
            simulation.incidentsToday[IncidentKind.TRUCK_STUCK] ?: 0,
            recordRatioToday,
            simulation.gameTime.day,
        )
        simulation.finishedTrips.drain().forEach(citizens::tripFinished)
        if (hourIndex() != currentHour) hourPassed()
        if (simulation.dayReports.size > reportsBefore) dayEnded()
    }

    fun state(): GameState =
        GameState(
            balance = treasury.balance,
            fineIncome = treasury.fineIncome,
            expenses = treasury.expenses,
            speedLimits = works.speedLimits.toMap(),
            nightLimits = works.nightLimits.toMap(),
            priorityRoads = works.priorityRoads.toSet(),
            bikeCrossings = works.bikeCrossings.toSet(),
            achievements = achievements.titles,
            signalNodes = works.signalNodes,
            greenWaves = timing.greenWaves.toList(),
            roadworks = roadworks.roadworks.toSet(),
            roadClosures = roadworks.closed.toSet(),
            bikeStreets = roadworks.bikeStreets.toSet(),
            winterService = winter.enabled,
            ploughs = winter.ploughs,
            cameras = works.cameraCounters.values.map { it.toRecord() },
            messages = messages.toList(),
            prContract = prContract,
            argumentsCampaign = argumentsCampaign,
            unhappyShare = unhappyShare,
            anger = politics.anger,
            recallIndex = politics.recallIndex,
            reputation = politics.reputation,
            zoneMoods = moods,
            lastBudgetRound = lastBudgetRound,
            isGameOver = isGameOver,
            daysPlayed = simulation.dayReports.size,
            termDays = rules.termDays,
            isTermOver = isTermOver,
            score = score(),
            latestEdition = latestEdition,
            fineOffice = office.state(simulation.time),
            bannedDrivers = drivers.bannedCount,
            licenseRevocations = drivers.revocations,
            commuteToday = citizens.commuteToday,
            commuteYesterday = commuteYesterday,
            commuteDamage = (commuteYesterday.lostMinutes * rules.damagePerLostMinute).toLong(),
            maintenance = maintenance.state(simulation.time),
            parkingOffice = parkingOffice.state(),
            detourSegments = works.detourSegments.toSet(),
            events = events.state(),
            press = press.state(unhappyShare, maintenance.state(simulation.time)),
            achievementList = achievements.state(),
            firstDay = firstDay,
            city = rules.city,
            trailers = works.trailers,
            idleTrailers = works.idleTrailers,
        )

    private fun hourIndex() = (simulation.time / SECONDS_PER_HOUR).toInt()

    /** Neue Blitzerfotos gehen an die Bußgeldstelle; Geld gibt es erst mit dem Bescheid. */
    private fun collectFines() {
        for (violation in simulation.speedControl.drainViolations()) {
            val red = violation.redSeconds
            val finding = violation.finding
            val tier =
                when {
                    finding != null -> FineTier.of(finding)
                    red != null -> FineTier.ofRedLight(red)
                    else -> FineTier.of(violation.excessKmh)
                }
            works.cameraCounters[violation.cameraId]?.record(tier, violation.measuredKmh, violation.limitKmh)
            office.register(tier, violation.homeZone, violation.person, simulation.time)
            finesToday += tier.fine
            // Tagesrekord und „Raser des Jahres“ zählen nur Tempoverstöße
            if (!tier.isSpeed) continue
            if (violation.limitKmh > 0) {
                recordRatioToday = maxOf(recordRatioToday, violation.measuredKmh / violation.limitKmh)
            }
            val record = recordToday
            if (record == null || violation.measuredKmh - violation.limitKmh > record.first - record.second) {
                recordToday = violation.measuredKmh to violation.limitKmh
            }
        }
    }

    private fun hourPassed() {
        currentHour = hourIndex()
        office.hourPassed(simulation.time, simulation.gameTime)
        maintenance.collect(simulation.time)
        maintenance.hourPassed(simulation.time)
        parkingOffice.hourPassed(simulation.time, simulation.gameTime, office.strictness)
        citizens.forget(hours = 1.0)
        moods = citizens.moods(argumentsCampaign, simulation.environment.snapshot)
        unhappyShare = citizens.unhappyShare(moods)
        val incomeThisHour = treasury.fineIncome - fineIncomeAtHourStart
        fineIncomeAtHourStart = treasury.fineIncome
        politics.hourPassed(unhappyShare, prContract, argumentsCampaign, incomeThisHour).forEach(::message)
        press.hourPassed(unhappyShare)
        events.hourPassed(politics.anger, drivers.bannedCount, moods)
        achievements.check(hourlyFacts(), simulation.gameTime.day)
        if (isGameOver) message(tr("Der Gemeinderat hat Ihnen das Vertrauen entzogen. Sie sind abgewählt!"))
    }

    private fun hourlyFacts(): AchievementFacts {
        val stats = simulation.snapshot().stats
        val infrastructure = simulation.infrastructure.snapshot()
        val maintenanceState = maintenance.state(simulation.time)
        val unusable = infrastructure.closedSegments + infrastructure.collapsedSegments
        return AchievementFacts(
            activeVehicles = stats.activeVehicles,
            meanSpeedKmh = stats.meanSpeedKmh,
            bridgesOpen = if (bridges.isEmpty()) -1 else bridges.count { it !in unusable },
            signForest = PublicWorks.SIGN_FOREST in works.achievements,
            cameras = works.cameraCounters.values.count { !it.isDisplay },
            collapses = infrastructure.collapsedSegments.size,
            plannings = maintenanceState.reports.count { it.status == DamageStatus.PLANNING },
            corsos = events.state().totals[EventKind.BIKE_CORSO] ?: 0,
            revocations = drivers.revocations,
            lawsuits = maintenanceState.lawsuits,
            reputation = politics.reputation,
        )
    }

    /** Mitternacht: Zeitung drucken, Lasertrupps bezahlen, sonntags Haushaltsrunde. */
    private fun dayEnded() {
        val reports = simulation.dayReports
        val report = reports.last()
        val officeToday = office.startNewDay()
        commuteYesterday = citizens.startNewDay()
        val revocationsToday = drivers.startNewDay(simulation.time)
        maintenance.dayPassed()
        val maintenanceToday = maintenance.startNewDay()
        val parkingToday = parkingOffice.startNewDay()
        val eventsToday = events.dayEnded(commuteYesterday)
        val ceremoniesToday = press.startNewDay()
        politics.press(press.dayEnded(prContract, unhappyShare))
        // Ohne PR-Vertrag kostet jede Schlagzeile über Krater und Einstürze Image
        if (prContract == PrContract.NONE) {
            val stories = maintenanceToday.ignoredCraters.size + maintenanceToday.collapses.size * COLLAPSE_STORY_WEIGHT
            politics.press(-stories * rules.ignoredDamageReputationLoss)
        }
        achievements.check(
            hourlyFacts().copy(
                lateShare = commuteYesterday.lateShare,
                lettersToday = officeToday.paid,
                demosToday = eventsToday.demos.size,
                recordRatio = recordRatioToday,
                trucksStuck = report.incidents[IncidentKind.TRUCK_STUCK] ?: 0,
            ),
            report.day,
        )
        val previous = reports.getOrNull(reports.size - 2)
        latestEdition =
            Newspaper.edition(
                DayNews(
                    report,
                    previous,
                    finesToday,
                    recordToday,
                    state(),
                    officeToday,
                    revocationsToday,
                    commuteYesterday,
                    maintenanceToday,
                    parkingToday,
                    eventsToday,
                    ceremoniesToday,
                    press.tone,
                ),
            )
        finesToday = 0
        recordToday = null
        recordRatioToday = 0.0
        if (works.laserCount > 0) treasury.charge(works.laserCount * rules.laserDailyCost)
        politics.press(roadworks.dayEnded(report.day))
        winter.dayEnded()
        if (report.weekday == Weekday.SUNDAY) budgetRound()
        if (isTermOver) message(tr("Ihre Amtszeit ist zu Ende. Zeit für die Abschiedsrede!"))
    }

    /** Sonntag um Mitternacht: Haushalt überweisen, PR, Kampagne und Gehälter bezahlen. */
    private fun budgetRound() {
        val argumentsCost = if (argumentsCampaign) rules.argumentsWeeklyCost else 0
        val round =
            BudgetRound(
                simulation.gameTime,
                rules.weeklyAllowance,
                press.weeklyPrCost(prContract),
                argumentsCost,
                office.weeklySalaries() + parkingOffice.weeklySalaries(),
                maintenance.weeklyCost(),
                parkingOffice.weeklyPermits(),
            )
        treasury.receiveAllowance(round.allowance)
        treasury.earn(round.permits)
        val costs = round.prCost + round.argumentsCost + round.salaries + round.maintenance
        treasury.charge(costs)
        lastBudgetRound = round
        message(
            tr(
                "Haushaltsrunde: +{allowance} Haushalt, −{costs} PR, Gehälter und Erhalt",
                "allowance" to formatEuro(round.allowance),
                "costs" to formatEuro(costs),
            ),
        )
    }

    private fun score(): Score {
        val share = politics.reputation / MAX_REPUTATION
        val factor = rules.minReputationFactor + (rules.maxReputationFactor - rules.minReputationFactor) * share
        val earnings = treasury.balance - rules.startBudget - treasury.allowances
        return Score(earnings, politics.reputation, factor)
    }

    private fun execute(command: GameCommand) {
        when (command) {
            is GameCommand.SetSpeedLimit -> {
                works.setSpeedLimit(command.segment, command.kmh, command.nightOnly)
            }

            is GameCommand.BuildSignal -> {
                works.buildSignal(command.node)
            }

            // Ampelprogramme und eigene Baustellen – jede Stelle übernimmt nur ihre Befehle
            is GameCommand.SetSignalProgram, is GameCommand.SetGreenWave,
            is GameCommand.SetRoadworks, is GameCommand.SetRoadClosure, is GameCommand.SetPloughs,
            is GameCommand.PlaceRedLightCamera, is GameCommand.PlaceDisplay, is GameCommand.PlacePoliceCheck,
            is GameCommand.SetBikeStreet,
            -> {
                timing.execute(command)
                roadworks.execute(command)
                winter.execute(command)
                surveillance.execute(command)
            }

            is GameCommand.RemoveSignal -> {
                works.removeSignal(command.node)
            }

            is GameCommand.PlaceCamera -> {
                works.placeCamera(command.segment, command.mobile)
            }

            is GameCommand.PlaceTrailer -> {
                works.placeTrailer(command.segment)
            }

            is GameCommand.RemoveCamera -> {
                works.removeCamera(command.cameraId)
            }

            is GameCommand.SetPriorityRoad -> {
                works.setPriorityRoad(command.segment, command.priority)
            }

            is GameCommand.SetBikeCrossing -> {
                works.setBikeCrossing(command.segment, command.crossing)
            }

            is GameCommand.SetPrContract -> {
                prContract = command.contract
                message(tr("PR-Vertrag: {contract}", "contract" to command.contract.label), kind = MessageKind.ACTION)
            }

            is GameCommand.SetArgumentsCampaign -> {
                argumentsCampaign = command.enabled
                message(
                    if (command.enabled) {
                        tr(
                            "„Wer sich ans Tempo hält, hat nichts zu befürchten.“",
                        )
                    } else {
                        tr("Kampagne beendet")
                    },
                )
            }

            else -> {
                executeOfficeCommand(command)
            }
        }
    }

    private fun executeOfficeCommand(command: GameCommand) {
        when (command) {
            is GameCommand.HireStaff -> {
                office.hire(command.count, simulation.time)
            }

            is GameCommand.FireStaff -> {
                office.fire(command.count)
            }

            is GameCommand.SetStrictness -> {
                office.strictness = command.strictness
                simulation.speedControl.toleranceKmh = command.strictness.toleranceKmh
                message(
                    tr("Ordnungsamt: {strictness}", "strictness" to command.strictness.label),
                    kind = MessageKind.ACTION,
                )
            }

            is GameCommand.SetUnidentifiedPolicy -> {
                office.policy = command.policy
                message(
                    tr("Fahrer nicht ermittelbar: {policy}", "policy" to command.policy.label),
                    kind = MessageKind.ACTION,
                )
            }

            is GameCommand.SetDeadlineDays -> {
                office.deadlineDays = command.days
                message(
                    tr("Bearbeitungsfrist: {deadlineDays} Tage", "deadlineDays" to office.deadlineDays),
                    kind = MessageKind.ACTION,
                )
            }

            else -> {
                executeAdministrationCommand(command)
            }
        }
    }

    /** Bauamt, Umleitungen und Parken. */
    private fun executeAdministrationCommand(command: GameCommand) {
        when (command) {
            is GameCommand.RespondToDamage -> {
                maintenance.respond(command.reportId, command.response, simulation.time)
            }

            is GameCommand.SetMaintenanceBudget -> {
                maintenance.budget = command.budget
                message(tr("Erhaltungsbudget: {budget}", "budget" to command.budget.label), kind = MessageKind.ACTION)
            }

            is GameCommand.SetDetour -> {
                works.setDetour(command.segment, command.enabled)
            }

            is GameCommand.SetParkingFee -> {
                parkingOffice.pricing.setFee(command.lot, command.euros)
            }

            is GameCommand.SetParkingCapacity -> {
                parkingOffice.pricing.setCapacity(command.lot, command.capacity)
            }

            is GameCommand.SetCurbFee -> {
                parkingOffice.pricing.setCurbFee(command.euros)
            }

            GameCommand.ConvertCurbParking -> {
                parkingOffice.convertCurb()
            }

            is GameCommand.SetBreadButton -> {
                parkingOffice.pricing.setBreadButton(command.enabled)
            }

            is GameCommand.SetResidentParking -> {
                parkingOffice.setResidentParking(command.enabled)
            }

            is GameCommand.HireWardens -> {
                parkingOffice.hire(command.count, simulation.time)
            }

            is GameCommand.FireWardens -> {
                parkingOffice.fire(command.count)
            }

            GameCommand.HoldCeremony -> {
                val gain =
                    press.ceremony(maintenance.state(simulation.time)) { maintenance.construction.lastRepaired = null }
                politics.press(gain)
            }

            else -> {
                Unit
            }
        }
    }

    /** Meldung von außen, z. B. „Spiel gespeichert“. */
    fun announce(text: String) = message(text)

    private fun message(
        text: String,
        place: MapPlace? = null,
        kind: MessageKind = MessageKind.SYSTEM,
    ) {
        messages.addLast(GameMessage(simulation.gameTime, text, place, kind))
        while (messages.size > MAX_MESSAGES) messages.removeFirst()
    }

    private companion object {
        /** So viele Meldungen hält das Spiel vor; die Oberfläche zeigt davon die neuesten (gebündelt). */
        const val MAX_MESSAGES = 30
        const val SECONDS_PER_HOUR = 3_600.0
        const val MAX_REPUTATION = 100.0

        /** Ein Brückeneinsturz wiegt in der Presse wie fünf Krater. */
        const val COLLAPSE_STORY_WEIGHT = 5
    }
}
