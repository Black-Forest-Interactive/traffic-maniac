package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.HouseholdType
import de.bfi.trafficmaniac.game.ZoneMood
import kotlin.math.roundToInt

private val PanelBackground = ComicTheme.paper
private val PanelText = ComicTheme.ink
private val Muted = ComicTheme.muted
private val Good = ComicTheme.green
private val Bad = ComicTheme.red
private val BarTrack = ComicTheme.track
private const val PERCENT = 100
private const val WORST_ZONES = 3

/** Wie geht es den Bürgern? Bedürfnisse im Mittel, Zufriedenheit je Haushaltstyp, die unzufriedensten Gebiete. */
@Composable
fun CitizensPanel(
    state: GameState,
    modifier: Modifier = Modifier,
) {
    val moods = state.zoneMoods
    val text = TextStyle(color = PanelText, fontSize = 12.sp)
    val muted = TextStyle(color = Muted, fontSize = 11.sp)
    Column(
        modifier
            .width(380.dp)
            .padding(12.dp)
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText("Bürger", style = TextStyle(color = PanelText, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        BasicText("Unzufrieden: ${(state.unhappyShare * PERCENT).roundToInt()} % (Zorn ab 35 %)", style = text)
        BasicText("Bedürfnisse im Mittel", style = text.copy(fontWeight = FontWeight.Bold))
        Need("Schnell am Ziel", weighted(moods) { it.speedSatisfaction })
        Need("Mobilität (Bußgelder)", weighted(moods) { it.mobilitySatisfaction })
        Need("Ruhe", weighted(moods) { it.quietSatisfaction })
        Need("Frische Luft", weighted(moods) { it.airSatisfaction })
        BasicText("Zufriedenheit je Haushaltstyp", style = text.copy(fontWeight = FontWeight.Bold))
        for (type in HouseholdType.entries) Need(type.label, weighted(moods) { typeSatisfaction(it, type) })
        BasicText("Hier brodelt es am meisten", style = text.copy(fontWeight = FontWeight.Bold))
        for (mood in moods.sortedBy { it.satisfaction }.take(WORST_ZONES)) {
            BasicText("Gebiet ${mood.zoneId}: ${(mood.satisfaction * PERCENT).roundToInt()} % zufrieden", style = muted)
        }
    }
}

@Composable
private fun Need(
    label: String,
    value: Double,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = TextStyle(color = PanelText, fontSize = 12.sp), modifier = Modifier.width(150.dp))
        Canvas(Modifier.width(150.dp).height(10.dp)) {
            drawRect(BarTrack)
            drawRect(lerp(Bad, Good, value.toFloat()), size = Size(size.width * value.toFloat(), size.height))
        }
        BasicText("${(value * PERCENT).roundToInt()} %", style = TextStyle(color = Muted, fontSize = 11.sp))
    }
}

/** Nach Fläche (≈ Einwohner) gewichteter Mittelwert. */
private fun weighted(
    moods: List<ZoneMood>,
    value: (ZoneMood) -> Double,
): Double {
    val total = moods.sumOf { it.area.toDouble() }
    return if (total > 0) moods.sumOf { value(it) * it.area } / total else 1.0
}

private fun typeSatisfaction(
    mood: ZoneMood,
    type: HouseholdType,
): Double =
    type.speedWeight * mood.speedSatisfaction + type.mobilityWeight * mood.mobilitySatisfaction +
        type.quietWeight * mood.quietSatisfaction + type.airWeight * mood.airSatisfaction
