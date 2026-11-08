package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.HouseholdType
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.ZoneMood
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

/** Wie geht es den Bürgern? Unzufriedenheit mit Zorn-Schwelle, Bedürfnisse, Haushaltstypen, die schlimmsten Gebiete. */
@Composable
fun CitizensPanel(
    state: GameState,
    modifier: Modifier = Modifier,
    /** Zorn entsteht ab diesem Anteil Unzufriedener (0..1). */
    angerThreshold: Double = DEFAULT_ANGER_THRESHOLD,
    onShow: (MapPlace) -> Unit = {},
    onClose: (() -> Unit)? = null,
) {
    val moods = state.zoneMoods
    PanelScaffold(tr("Bericht"), tr("Bürger"), modifier, onClose, floating = false) {
        PanelSection {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(tr("Unzufrieden"), style = TmType.label)
                BasicText("${(state.unhappyShare * PERCENT).roundToInt()} %", style = TmType.mono(13.sp))
            }
            Meter(
                state.unhappyShare.toFloat(),
                Modifier.fillMaxWidth(),
                height = 10.dp,
                color = TmColors.Warn,
                threshold = angerThreshold.toFloat(),
            )
            BasicText(
                tr(
                    "Ab {threshold} % Unzufriedener wächst der Bürgerzorn.",
                    "threshold" to (angerThreshold * PERCENT).roundToInt(),
                ),
                style = TmType.caption,
            )
        }
        PanelSection(title = tr("Bedürfnisse im Mittel")) {
            Bar(tr("Schnell am Ziel"), weighted(moods) { it.speedSatisfaction })
            Bar(tr("Mobilität (Bußgelder)"), weighted(moods) { it.mobilitySatisfaction })
            Bar(tr("Ruhe"), weighted(moods) { it.quietSatisfaction })
            Bar(tr("Frische Luft"), weighted(moods) { it.airSatisfaction })
        }
        PanelSection(title = tr("Zufriedenheit je Haushaltstyp")) {
            for (type in HouseholdType.entries) Bar(type.label, weighted(moods) { typeSatisfaction(it, type) })
        }
        PanelSection(title = tr("Hier brodelt es"), divider = false) {
            for (mood in moods.sortedBy { it.satisfaction }.take(WORST_ZONES)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            tr("Gebiet {zoneId}", "zoneId" to mood.zoneId),
                            style = TmType.text(13.sp, FontWeight.SemiBold),
                        )
                        BasicText(
                            tr(
                                "{satisfaction} % zufrieden",
                                "satisfaction" to (mood.satisfaction * PERCENT).roundToInt(),
                            ),
                            style = TmType.caption,
                        )
                    }
                    TmButton(tr("Zeigen"), { onShow(MapPlace(mood.x, mood.y)) }, kind = ButtonKind.TEXT)
                }
            }
        }
    }
}

/** Beschriftung | Balken | Prozent – der Balken wird mit der Zufriedenheit grün, darunter orange. */
@Composable
private fun Bar(
    label: String,
    value: Double,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = TmType.text(13.sp), modifier = Modifier.width(LABEL_WIDTH.dp))
        Meter(
            value.toFloat(),
            Modifier.weight(1f),
            height = 8.dp,
            color = if (value >= GOOD) TmColors.Success else TmColors.Warn,
        )
        BasicText(
            "${(value * PERCENT).roundToInt()} %",
            style = TmType.mono(12.sp, bold = false).copy(textAlign = TextAlign.End),
            modifier = Modifier.width(PERCENT_WIDTH.dp),
            softWrap = false,
        )
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

private const val PERCENT = 100
private const val WORST_ZONES = 3
private const val DEFAULT_ANGER_THRESHOLD = 0.35
private const val GOOD = 0.6
private const val LABEL_WIDTH = 130
private const val PERCENT_WIDTH = 44
