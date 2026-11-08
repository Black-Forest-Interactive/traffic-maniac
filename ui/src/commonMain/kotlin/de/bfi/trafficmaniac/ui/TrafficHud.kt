package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.EnvironmentSnapshot
import de.bfi.trafficmaniac.sim.Pollutant
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlin.math.roundToInt

private val HudBackground = ComicTheme.paper
private val HudText = ComicTheme.ink

/** Debug-Anzeige für M2: Uhr, Tempo, Fahrzeuge, Kennzahlen, Rechenzeit und Bildrate. */
@Composable
fun TrafficHud(
    frame: TrafficFrame,
    fps: Int,
    modifier: Modifier = Modifier,
    sprites: SpriteSource = NoSprites,
) {
    val lines = hudLines(frame, fps)
    Column(
        modifier
            .padding(12.dp)
            .comicPanel()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        for (line in lines) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                line.forEachIndexed { index, (icon, text) ->
                    icon?.let { SpriteIcon(sprites, it, HUD_ICON.dp) }
                    BasicText(
                        text +
                            if (index <
                                line.size - 1
                            ) {
                                " ·"
                            } else {
                                ""
                            },
                        style = TextStyle(color = HudText, fontSize = 13.sp),
                    )
                }
            }
        }
    }
}

private const val HUD_ICON = 16

/** Zeilen des HUD: je Zeile Paare aus Symbol (Grafikname oder `null`) und Text. */
private fun hudLines(
    frame: TrafficFrame,
    fps: Int,
): List<List<Pair<String?, String>>> {
    val snapshot = frame.current
    val stats = snapshot.stats
    val time = snapshot.gameTime
    val tempo =
        when {
            frame.speed == 0 -> {
                tr("Pause")
            }

            frame.effectiveSpeed > frame.speed -> {
                tr(
                    "{speed}x (Nacht: {effectiveSpeed}x)",
                    "speed" to frame.speed,
                    "effectiveSpeed" to frame.effectiveSpeed,
                )
            }

            else -> {
                "${frame.speed}x"
            }
        }
    val vehicles =
        tr(
            "Fahrzeuge {activeVehicles} (+{pendingVehicles} wartend)",
            "activeVehicles" to stats.activeVehicles,
            "pendingVehicles" to stats.pendingVehicles,
        )
    val tripMinutes = formatDecimal(stats.meanTripMinutes)
    val day =
        tr(
            "{germanName}, Tag {day}/{termDays}",
            "germanName" to time.weekday.germanName,
            "day" to frame.game.termDay(time.day),
            "termDays" to frame.game.termDays,
        )
    // Zeilen mit Symbol: (Grafikname oder null, Text); die Politik-Zeile hat drei Symbole
    val politics =
        listOf(
            "hud_anger" to tr("Bürgerzorn {anger}", "anger" to frame.game.anger.roundToInt()),
            "hud_recall" to tr("Abwahlindex {recallIndex}/100", "recallIndex" to frame.game.recallIndex.roundToInt()),
            "hud_reputation" to tr("Reputation {reputation}", "reputation" to frame.game.reputation.roundToInt()),
        )
    return listOf(
        listOf(
            "hud_time" to
                tr(
                    "{day} · {clockText} Uhr · Tempo {tempo}",
                    "day" to day,
                    "clockText" to time.clockText,
                    "tempo" to tempo,
                ),
        ),
        listOf(
            "hud_money" to
                tr(
                    "Kasse {balance} · Bußgelder {fineIncome}",
                    "balance" to formatEuro(frame.game.balance),
                    "fineIncome" to formatEuro(frame.game.fineIncome),
                ),
        ),
        politics,
        listOf(
            null to
                tr(
                    "{vehicles} · Ø {meanSpeedKmh} km/h",
                    "vehicles" to vehicles,
                    "meanSpeedKmh" to stats.meanSpeedKmh.roundToInt(),
                ),
        ),
        listOf(
            null to
                tr(
                    "Angekommen {completedTrips} (Ø {tripMinutes} min) · aufgegeben {stuckRemovals}",
                    "completedTrips" to stats.completedTrips,
                    "tripMinutes" to tripMinutes,
                    "stuckRemovals" to stats.stuckRemovals,
                ),
        ),
        listOf(null to environmentLine(snapshot.environment)),
        listOf(null to commuteLine(frame)),
        listOf(null to incidentLine(snapshot)),
        listOf(
            null to
                tr(
                    "Rechenzeit {stepMillis} ms/Tick · {fps} FPS",
                    "stepMillis" to formatDecimal(frame.stepMillis, 2),
                    "fps" to fps,
                ),
        ),
        listOf(
            null to
                tr(
                    "Leertaste: Pause · 1–4: Tempo 1x/2x/4x/8x · nachts automatisch ×{gameRunner}",
                    "gameRunner" to GameRunner.NIGHT_BOOST,
                ),
        ),
    )
}

private fun environmentLine(environment: EnvironmentSnapshot): String {
    val totals = environment.totals
    val particulates = PARTICULATES.sumOf { totals[it] ?: 0.0 } / GRAMS_PER_KG
    val co2 = (totals[Pollutant.CO2] ?: 0.0) / GRAMS_PER_TONNE
    return tr(
        "Sprit {totalFuelLiters} l · CO₂ {co2} t · Feinstaub {particulates} kg",
        "totalFuelLiters" to
            formatDecimal(environment.totalFuelLiters, 0),
        "co2" to formatDecimal(co2),
        "particulates" to formatDecimal(particulates, 2),
    )
}

private fun commuteLine(frame: TrafficFrame): String {
    val yesterday = frame.game.commuteYesterday
    if (yesterday.trips == 0) return tr("Pendler: noch keine Tagesbilanz")
    return tr(
        "Zu spät gestern: {lateShare} % · Schaden {commuteDamage}",
        "lateShare" to (yesterday.lateShare * PERCENT).roundToInt(),
        "commuteDamage" to
            formatEuro(
                frame.game.commuteDamage,
            ),
    )
}

private val PARTICULATES = listOf(Pollutant.PM_EXHAUST, Pollutant.PM_TIRES, Pollutant.PM_BRAKES)
private const val GRAMS_PER_KG = 1_000.0
private const val GRAMS_PER_TONNE = 1_000_000.0
private const val PERCENT = 100

private fun incidentLine(snapshot: SimulationSnapshot): String {
    val open =
        snapshot.incidents
            .groupingBy { it.kind.label }
            .eachCount()
            .entries
            .joinToString { "${it.value}× ${it.key}" }
    val waiting = snapshot.chargingStations.sumOf { it.waiting }
    return tr(
        "Vorfälle: {keine} · an Ladesäulen wartend: {waiting}",
        "keine" to open.ifEmpty { "keine" },
        "waiting" to waiting,
    )
}
