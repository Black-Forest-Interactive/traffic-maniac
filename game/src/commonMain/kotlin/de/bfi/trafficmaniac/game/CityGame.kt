package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.sim.Weekday
import kotlinx.coroutines.channels.Channel
import kotlin.jvm.Transient

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
    private val treasury =
        Treasury(rules.startBudget) {
            message(it, kind = MessageKind.PROBLEM)
            highlights.add(HighlightKind.WARNING, it)
        }
    private val works = PublicWorks(city, simulation, rules, treasury) { message(it, kind = MessageKind.ACTION) }
    private val timing = SignalTiming(city, simulation, rules, treasury) { message(it, kind = MessageKind.ACTION) }
    private val roadworks =
        Roadworks(city, simulation, rules, treasury, works) { message(it, kind = MessageKind.ACTION) }
    private val winter = WinterService(simulation, rules, treasury) { message(it, kind = MessageKind.PROBLEM) }
    private val surveillance =
        Surveillance(city, simulation, rules, treasury, works) { message(it, kind = MessageKind.ACTION) }
    private val buses = BusService(city, simulation, rules, treasury, works) { message(it, kind = MessageKind.ACTION) }
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
    private val achievements =
        Achievements(
            object : AchievementEvents {
                override fun unlocked(achievement: Achievement) {
                    alerts++
                    message(
                        tr("Achievement freigeschaltet: {title}!", "title" to achievement.title),
                        kind = MessageKind.SUCCESS,
                    )
                    highlights.add(
                        HighlightKind.BIG,
                        tr("Errungenschaft: {title}", "title" to achievement.title),
                        achievement.description,
                    )
                }

                override fun almost(
                    achievement: Achievement,
                    missing: Int,
                ) {
                    highlights.add(
                        HighlightKind.MEDIUM,
                        tr("Fast geschafft: {title}", "title" to achievement.title),
                        tr(
                            "Noch {missing} – {description}",
                            "missing" to missing,
                            "description" to achievement.description,
                        ),
                    )
                }
            },
        )

    /** Erledigte Aufträge bisher (für den Kopflos-Lauf). */
    val assignmentsDone: Int get() = assignments.doneCount

    private val assignments =
        Assignments(
            city,
            rules,
            object : AssignmentEffects {
                override fun reward(euros: Long) = treasury.earnGrant(euros)

                override fun reputation(change: Double) = politics.press(change)

                override fun announce(
                    text: String,
                    place: MapPlace?,
                    kind: HighlightKind,
                    title: String,
                ) {
                    val messageKind = if (kind == HighlightKind.BIG) MessageKind.SUCCESS else MessageKind.PROBLEM
                    message(text, place, messageKind)
                    highlights.add(kind, title, text, place)
                }
            },
            config.seed,
        )
    private val firstDay = simulation.gameTime.day

    private val habits =
        Habituation(
            simulation,
            rules,
            treasury,
            politics,
            works,
            config.seed,
            object : HabitEffects {
                override fun message(
                    text: String,
                    place: MapPlace?,
                    kind: MessageKind,
                ) = this@CityGame.message(text, place, kind)

                override fun highlight(
                    kind: HighlightKind,
                    title: String,
                    text: String,
                    place: MapPlace?,
                ) = highlights.add(kind, title, text, place)

                override fun checkAchievements() = achievements.check(hourlyFacts(), simulation.gameTime.day)
            },
        )

    private val bridges =
        city.roads.segments
            .filter { it.bridge && !it.passive }
            .map { it.id }
    private var prContract = PrContract.NONE
    private var argumentsCampaign = false
    private var moods: List<ZoneMood> = citizens.moods(argumentsCampaign = false)
    private var unhappyShare = 0.0
    private var fineIncomeAtHourStart = 0L
    private var currentHour = hourIndex
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

    /**
     * Zählt die Schritte, in denen Spieleraktionen ausgeführt wurden – der [GameRunner] baut den Spielstand dann
     * sofort neu statt erst nach seinem Takt. Wird nicht gespeichert.
     */
    @Transient
    var revision = 0
        private set

    /**
     * Zählt Ereignisse, bei denen der Spieler hinschauen sollte: Schäden, Unfälle, Demos und Veranstaltungen mit Ort,
     * Errungenschaften. Der [GameRunner] bremst daran den Zeitraffer. Wird nicht gespeichert.
     */
    @Transient
    var alerts = 0
        private set

    @Transient
    private var highlightLog: HighlightLog? = null

    /** Was die Oberfläche einblenden soll (nicht im Spielstand). */
    private val highlights: HighlightLog
        get() = highlightLog ?: HighlightLog().also { highlightLog = it }

    /** Für Tests: ein Ereignis melden, ohne eines herbeizuspielen. */
    internal fun raiseAlert() {
        alerts++
    }

    private val telemetryLog = TelemetryLog()

    /** Statistikpaket dieser Partie (Paket F); [context] liefert die Plattform. Gesendet wird nur mit Zustimmung. */
    fun telemetry(context: TelemetryContext): GameTelemetry {
        val state = state()
        return GameTelemetry(
            installation = context.installation,
            appVersion = context.appVersion,
            platform = context.platform,
            deviceClass = context.deviceClass,
            language = context.language,
            city = context.city,
            scenario = context.scenario,
            difficulty = context.difficulty,
            end =
                when {
                    isGameOver -> GameTelemetry.END_RECALLED
                    isTermOver -> GameTelemetry.END_TERM
                    else -> GameTelemetry.END_ABORTED
                },
            daysPlayed = simulation.dayReports.size,
            realMinutes = context.realMinutes,
            points = state.score.points,
            earnings = state.score.earnings,
            income =
                mapOf(
                    "fines" to treasury.fineIncome,
                    "parking" to treasury.parkingIncome,
                    "tickets" to treasury.ticketIncome,
                    "allowance" to treasury.allowances,
                    "grants" to treasury.grantIncome,
                ),
            expenses = treasury.expenses,
            days = telemetryLog.days.toList(),
            tools = telemetryLog.tools.toMap(),
            speeds = context.speeds,
            performance = context.performance,
        )
    }

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
        // Was der Spieler baut (erster Blitzer, Schilderwald …), wird sofort gefeiert, nicht erst zur vollen Stunde
        if (executed) {
            revision++
            achievements.check(hourlyFacts(), simulation.gameTime.day)
        }
        val reportsBefore = simulation.dayReports.size
        simulation.step()
        collectFines()
        habits.update()
        winter.update()
        surveillance.update()
        achievements.checkLive(
            simulation.incidentsToday[IncidentKind.TRUCK_STUCK] ?: 0,
            recordRatioToday,
            treasury.fineIncome,
            simulation.gameTime.day,
        )
        simulation.finishedTrips.drain().forEach(citizens::tripFinished)
        if (hourIndex != currentHour) hourPassed()
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
            busLanes = buses.busLanes.toSet(),
            busPriorityNodes = buses.priorityNodes.toSet(),
            busModeShift = buses.modeShift,
            winterService = winter.enabled,
            ploughs = winter.ploughs,
            cameras = works.cameraCounters.values.map { it.toRecord() },
            messages = messages.toList(),
            highlights = highlights.list(),
            assignments = assignments.state(simulation.time),
            warnerShare = simulation.speedControl.warnerShare,
            marathonActive = habits.marathonActive,
            campaignInDays = habits.campaignInDays,
            grantIncome = treasury.grantIncome,
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
            score = score,
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
            firstDayReport = simulation.dayReports.firstOrNull(),
            city = rules.city,
            trailers = works.trailers,
            idleTrailers = works.idleTrailers,
        )

    private val hourIndex: Int get() = (simulation.time / SECONDS_PER_HOUR).toInt()

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
        currentHour = hourIndex
        office.hourPassed(simulation.time, simulation.gameTime)
        maintenance.collect(simulation.time)
        maintenance.hourPassed(simulation.time)
        parkingOffice.hourPassed(simulation.time, simulation.gameTime, office.strictness)
        citizens.forget(hours = 1.0)
        moods = citizens.moods(argumentsCampaign, simulation.environment.snapshot)
        unhappyShare = citizens.unhappyShare(moods)
        val incomeThisHour = treasury.fineIncome - fineIncomeAtHourStart
        fineIncomeAtHourStart = treasury.fineIncome
        politics.hourPassed(unhappyShare, prContract, argumentsCampaign, incomeThisHour).forEach {
            message(it)
            if (politics.recallIndex >= RECALL_WARNING) highlights.add(HighlightKind.WARNING, it)
        }
        for (camera in works.newlyPaidOff()) {
            highlights.add(
                HighlightKind.MEDIUM,
                tr("Blitzer {street} hat sich bezahlt gemacht", "street" to camera.streetName),
                tr("{revenue} eingenommen – alles Weitere ist Gewinn", "revenue" to formatEuro(camera.totalRevenue)),
            )
        }
        press.hourPassed(unhappyShare)
        events.hourPassed(politics.anger, drivers.bannedCount, moods)
        val snapshot = simulation.snapshot()
        assignments.hourPassed(
            AssignmentWorld(
                state(),
                snapshot.incidents,
                simulation.time,
                simulation.gameTime,
                simulation.gameTime.day == firstDay,
            ),
        )
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
            fineIncome = treasury.fineIncome,
            repairs = maintenanceState.repaired,
            daysEnded = simulation.dayReports.size,
            campaigns = habits.campaigns,
        )
    }

    /** Mitternacht: Zeitung drucken, Lasertrupps bezahlen, Haushaltsrunde. */
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
        politics.press(buses.dayEnded())
        winter.dayEnded()
        budgetRound()
        if (report.weekday == Weekday.SUNDAY) press.startNewWeek()
        habits.dayPassed()
        telemetryLog.dayEnded(report.day, state(), report.vehicleKm, report.vehicleHours)
        if (isTermOver) message(tr("Ihre Amtszeit ist zu Ende. Zeit für die Abschiedsrede!"))
    }

    /**
     * Jede Nacht um Mitternacht: ein Siebtel des Wochenhaushalts überweisen, ein Siebtel von PR, Kampagne, Gehältern
     * und Erhalt bezahlen. Über die Woche ist das dieselbe Summe wie früher sonntags – kommt aber schon am ersten
     * Spieltag an.
     */
    private fun budgetRound() {
        val argumentsCost = if (argumentsCampaign) rules.argumentsWeeklyCost else 0
        val round =
            BudgetRound(
                simulation.gameTime,
                rules.weeklyAllowance / DAYS_PER_WEEK,
                press.dailyPrCost(prContract),
                argumentsCost / DAYS_PER_WEEK,
                (office.weeklySalaries() + parkingOffice.weeklySalaries()) / DAYS_PER_WEEK,
                maintenance.weeklyCost() / DAYS_PER_WEEK,
                parkingOffice.weeklyPermits() / DAYS_PER_WEEK,
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

    private val score: Score
        get() {
            val share = politics.reputation / MAX_REPUTATION
            val factor = rules.minReputationFactor + (rules.maxReputationFactor - rules.minReputationFactor) * share
            val earnings = treasury.balance - rules.startBudget - treasury.allowances
            return Score(earnings, politics.reputation, factor)
        }

    private fun execute(command: GameCommand) {
        telemetryLog.used(command)
        when (command) {
            is GameCommand.DeclineAssignment -> {
                assignments.decline(command.id, simulation.time)
            }

            // Jede Stelle übernimmt nur ihre Befehle
            is GameCommand.SetSpeedLimit, is GameCommand.BuildSignal, is GameCommand.RemoveSignal,
            is GameCommand.PlaceCamera, is GameCommand.PlaceTrailer, is GameCommand.RemoveCamera,
            is GameCommand.SetPriorityRoad, is GameCommand.SetBikeCrossing,
            -> {
                works.execute(command)
            }

            is GameCommand.SetSignalProgram, is GameCommand.SetGreenWave,
            is GameCommand.SetRoadworks, is GameCommand.SetRoadClosure, is GameCommand.SetPloughs,
            is GameCommand.PlaceRedLightCamera, is GameCommand.PlaceDisplay, is GameCommand.PlacePoliceCheck,
            is GameCommand.SetBikeStreet, is GameCommand.SetBusLane, is GameCommand.PlaceBusLaneCamera,
            is GameCommand.SetBusPriority,
            -> {
                timing.execute(command)
                roadworks.execute(command)
                winter.execute(command)
                surveillance.execute(command)
                buses.execute(command)
            }

            GameCommand.WarnerCampaign, GameCommand.AnnounceMarathon -> {
                habits.execute(command)
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
        if (kind == MessageKind.PROBLEM && place != null) alerts++
        while (messages.size > MAX_MESSAGES) messages.removeFirst()
    }

    private companion object {
        /** Ab diesem Abwahlindex werden Warnungen der Politik rot eingeblendet. */
        const val RECALL_WARNING = 50.0

        /** So viele Meldungen hält das Spiel vor; die Oberfläche zeigt davon die neuesten (gebündelt). */
        const val MAX_MESSAGES = 30
        const val SECONDS_PER_HOUR = 3_600.0
        const val MAX_REPUTATION = 100.0

        /** Ein Brückeneinsturz wiegt in der Presse wie fünf Krater. */
        const val COLLAPSE_STORY_WEIGHT = 5
    }
}
