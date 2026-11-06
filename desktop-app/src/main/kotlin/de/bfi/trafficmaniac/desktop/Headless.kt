package de.bfi.trafficmaniac.desktop

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.city.RoadClass
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.PrContract
import de.bfi.trafficmaniac.game.formatEuro
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

/**
 * Kopflos-Runner für das Balancing: spielt mehrere Strategien parallel ohne Oberfläche durch und gibt
 * je Spieltag Kasse, Bußgelder, Zorn, Abwahlindex und Reputation aus.
 *
 * Aufruf: `./gradlew :desktop-app:headless --args="[Tage] [Strategie,Strategie,...]"`
 * Strategien: nichts, abkassieren, abkassieren-pr
 */
fun main(args: Array<String>) {
    val city = CityMapCodec.decode(File("data/cities/pforzheim.city").readBytes())
    val days = args.getOrNull(0)?.toInt() ?: DEFAULT_DAYS
    val names = args.getOrNull(1)?.split(',') ?: Strategy.entries.map { it.id }
    val strategies = names.map { name -> Strategy.entries.first { it.id == name } }
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
    RIP_OFF("abkassieren", "Stündlich alles Geld in Tempo 20 + Blitzer auf zentralen Hauptstraßen, keine PR"),
    RIP_OFF_PR("abkassieren-pr", "Wie abkassieren, dazu Premium-PR und Totschlagargumente"),
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
    if (strategy == Strategy.RIP_OFF_PR) {
        game.submit(GameCommand.SetPrContract(PrContract.PREMIUM))
        game.submit(GameCommand.SetArgumentsCampaign(true))
    }
    log.append("Tag | Kasse        | Bußgelder    | unzufrieden | Zorn | Abwahl | Reputation\n")
    for (day in 1..days) {
        repeat(HOURS_PER_DAY) {
            // Abkassieren: Jede Stunde werden die Einnahmen in die nächste Blitzer-Strecke gesteckt
            if (strategy != Strategy.NOTHING) investInCameras(game, targets)
            repeat(SECONDS_PER_HOUR * config.ticksPerSecond) { game.step() }
        }
        val state = game.state()
        log.append(
            "%3d | %12s | %12s | %10d%% | %4d | %6d | %10d%s\n".format(
                day,
                formatEuro(state.balance),
                formatEuro(state.fineIncome),
                (state.unhappyShare * PERCENT).roundToInt(),
                state.anger.roundToInt(),
                state.recallIndex.roundToInt(),
                state.reputation.roundToInt(),
                if (state.isGameOver) "  ← ABGEWÄHLT" else "",
            ),
        )
        if (state.isGameOver) break
    }
    return log.toString()
}

private fun investInCameras(
    game: CityGame,
    targets: ArrayDeque<Int>,
) {
    val price = game.rules.cameraCost + game.rules.speedLimitSignCost
    var budget = game.state().balance
    while (targets.isNotEmpty() && budget >= price) {
        val segment = targets.removeFirst()
        game.submit(GameCommand.SetSpeedLimit(segment, kmh = 20))
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
