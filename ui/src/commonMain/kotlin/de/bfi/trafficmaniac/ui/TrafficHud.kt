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
            frame.speed == 0 -> "Pause"
            frame.effectiveSpeed > frame.speed -> "${frame.speed}x (Nacht: ${frame.effectiveSpeed}x)"
            else -> "${frame.speed}x"
        }
    val vehicles = "Fahrzeuge ${stats.activeVehicles} (+${stats.pendingVehicles} wartend)"
    val tripMinutes = "%.1f".format(stats.meanTripMinutes)
    val day = "${time.weekday.germanName}, Tag ${frame.game.termDay(time.day)}/${frame.game.termDays}"
    // Zeilen mit Symbol: (Grafikname oder null, Text); die Politik-Zeile hat drei Symbole
    val politics =
        listOf(
            "hud_anger" to "Bürgerzorn ${frame.game.anger.roundToInt()}",
            "hud_recall" to "Abwahlindex ${frame.game.recallIndex.roundToInt()}/100",
            "hud_reputation" to "Reputation ${frame.game.reputation.roundToInt()}",
        )
    return listOf(
        listOf("hud_time" to "$day · ${time.clockText} Uhr · Tempo $tempo"),
        listOf(
            "hud_money" to
                "Kasse ${formatEuro(frame.game.balance)} · Bußgelder ${formatEuro(frame.game.fineIncome)}",
        ),
        politics,
        listOf(null to "$vehicles · Ø ${stats.meanSpeedKmh.roundToInt()} km/h"),
        listOf(
            null to "Angekommen ${stats.completedTrips} (Ø $tripMinutes min) · aufgegeben ${stats.stuckRemovals}",
        ),
        listOf(null to environmentLine(snapshot.environment)),
        listOf(null to commuteLine(frame)),
        listOf(null to incidentLine(snapshot)),
        listOf(null to "Rechenzeit ${"%.2f".format(frame.stepMillis)} ms/Tick · $fps FPS"),
        listOf(null to "Leertaste: Pause · 1–4: Tempo 1x/2x/4x/8x · nachts automatisch ×${GameRunner.NIGHT_BOOST}"),
    )
}

private fun environmentLine(environment: EnvironmentSnapshot): String {
    val totals = environment.totals
    val particulates = PARTICULATES.sumOf { totals[it] ?: 0.0 } / GRAMS_PER_KG
    val co2 = (totals[Pollutant.CO2] ?: 0.0) / GRAMS_PER_TONNE
    return "Sprit ${"%.0f".format(
        environment.totalFuelLiters,
    )} l · CO₂ ${"%.1f".format(co2)} t · Feinstaub ${"%.2f".format(particulates)} kg"
}

private fun commuteLine(frame: TrafficFrame): String {
    val yesterday = frame.game.commuteYesterday
    if (yesterday.trips == 0) return "Pendler: noch keine Tagesbilanz"
    return "Zu spät gestern: ${(yesterday.lateShare * PERCENT).roundToInt()} % · Schaden ${formatEuro(
        frame.game.commuteDamage,
    )}"
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
    return "Vorfälle: ${open.ifEmpty { "keine" }} · an Ladesäulen wartend: $waiting"
}
