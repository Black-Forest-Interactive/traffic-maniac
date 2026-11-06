package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.sim.Simulation
import de.bfi.trafficmaniac.sim.SimulationConfig
import de.bfi.trafficmaniac.sim.Weekday
import kotlinx.coroutines.channels.Channel

/**
 * Das Spiel um die Simulation: Kasse, Werkzeuge, Bußgelder, Bürger und Politik.
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
    private val treasury = Treasury(rules.startBudget, ::message)
    private val works = PublicWorks(city, simulation, rules, treasury, ::message)
    private val citizens = Citizens(city.zones, rules)
    private val politics = Politics(rules)
    private var prContract = PrContract.NONE
    private var argumentsCampaign = false
    private var moods: List<ZoneMood> = citizens.moods(argumentsCampaign = false)
    private var unhappyShare = 0.0
    private var finesThisHour = 0L
    private var currentHour = hourIndex()
    private var lastBudgetRound: BudgetRound? = null
    private var finesToday = 0L
    private var recordToday: Pair<Double, Int>? = null
    private var latestEdition: NewspaperEdition? = null

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
        while (true) {
            val command = commands.tryReceive().getOrNull() ?: break
            execute(command)
        }
        val reportsBefore = simulation.dayReports.size
        simulation.step()
        collectFines()
        simulation.finishedTrips.drain().forEach(citizens::tripFinished)
        if (hourIndex() != currentHour) hourPassed()
        if (simulation.dayReports.size > reportsBefore) dayEnded()
    }

    /** Mitternacht: Zeitung drucken, sonntags Haushaltsrunde. */
    private fun dayEnded() {
        val reports = simulation.dayReports
        val report = reports.last()
        latestEdition =
            Newspaper.edition(DayNews(report, reports.getOrNull(reports.size - 2), finesToday, recordToday, state()))
        finesToday = 0
        recordToday = null
        if (report.weekday == Weekday.SUNDAY) budgetRound()
        if (isTermOver) message("Ihre Amtszeit ist zu Ende. Zeit für die Abschiedsrede!")
    }

    private fun score(): Score {
        val share = politics.reputation / MAX_REPUTATION
        return Score(
            treasury.balance,
            politics.reputation,
            rules.minReputationFactor + (rules.maxReputationFactor - rules.minReputationFactor) * share,
        )
    }

    fun state(): GameState =
        GameState(
            balance = treasury.balance,
            fineIncome = treasury.fineIncome,
            expenses = treasury.expenses,
            speedLimits = works.speedLimits.toMap(),
            signalNodes = works.signalNodes,
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
        )

    private fun hourIndex() = (simulation.time / SECONDS_PER_HOUR).toInt()

    private fun collectFines() {
        for (violation in simulation.speedControl.drainViolations()) {
            val tier = FineTier.of(violation.excessKmh)
            works.cameraCounters[violation.cameraId]?.record(tier, violation.measuredKmh, violation.limitKmh)
            citizens.fined(violation.homeZone, tier.fine)
            treasury.earnFine(tier.fine)
            finesThisHour += tier.fine
            finesToday += tier.fine
            val record = recordToday
            if (record == null || violation.measuredKmh - violation.limitKmh > record.first - record.second) {
                recordToday = violation.measuredKmh to violation.limitKmh
            }
        }
    }

    private fun hourPassed() {
        currentHour = hourIndex()
        citizens.forget(hours = 1.0)
        moods = citizens.moods(argumentsCampaign)
        unhappyShare = citizens.unhappyShare(moods)
        politics.hourPassed(unhappyShare, prContract, argumentsCampaign, finesThisHour).forEach(::message)
        finesThisHour = 0
        if (isGameOver) message("Der Gemeinderat hat Ihnen das Vertrauen entzogen. Sie sind abgewählt!")
    }

    /** Sonntag um Mitternacht: Haushalt überweisen, PR und Kampagne für die nächste Woche bezahlen. */
    private fun budgetRound() {
        val argumentsCost = if (argumentsCampaign) rules.argumentsWeeklyCost else 0
        val round = BudgetRound(simulation.gameTime, rules.weeklyAllowance, prContract.weeklyCost, argumentsCost)
        treasury.receive(round.allowance)
        treasury.charge(round.prCost + round.argumentsCost)
        lastBudgetRound = round
        message(
            "Haushaltsrunde: +${formatEuro(
                round.allowance,
            )} Haushalt, −${formatEuro(round.prCost + round.argumentsCost)} PR",
        )
    }

    private fun execute(command: GameCommand) {
        when (command) {
            is GameCommand.SetSpeedLimit -> {
                works.setSpeedLimit(command.segment, command.kmh)
            }

            is GameCommand.BuildSignal -> {
                works.buildSignal(command.node)
            }

            is GameCommand.RemoveSignal -> {
                works.removeSignal(command.node)
            }

            is GameCommand.PlaceCamera -> {
                works.placeCamera(command.segment)
            }

            is GameCommand.RemoveCamera -> {
                works.removeCamera(command.cameraId)
            }

            is GameCommand.SetPrContract -> {
                prContract = command.contract
                message("PR-Vertrag: ${command.contract.label}")
            }

            is GameCommand.SetArgumentsCampaign -> {
                argumentsCampaign = command.enabled
                message(
                    if (command.enabled) "„Wer sich ans Tempo hält, hat nichts zu befürchten.“" else "Kampagne beendet",
                )
            }
        }
    }

    private fun message(text: String) {
        messages.addLast(GameMessage(simulation.gameTime, text))
        while (messages.size > MAX_MESSAGES) messages.removeFirst()
    }

    private companion object {
        const val MAX_MESSAGES = 5
        const val SECONDS_PER_HOUR = 3_600.0
        const val MAX_REPUTATION = 100.0
    }
}
