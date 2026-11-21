package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
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
import de.bfi.trafficmaniac.ui.components.ChartAxis
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.MinTouch
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.TimeAxis
import de.bfi.trafficmaniac.ui.components.TimeChart
import de.bfi.trafficmaniac.ui.components.TmButton
import de.bfi.trafficmaniac.ui.components.threeTicks
import de.bfi.trafficmaniac.ui.map.EnvironmentLayer
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.roundToInt

/** Wie geht es den Bürgern? Unzufriedenheit mit Zorn-Schwelle, Bedürfnisse, Haushaltstypen, die schlimmsten Gebiete. */
@Composable
internal fun CitizensPanel(
    state: GameState,
    modifier: Modifier = Modifier,
    /** Zorn entsteht ab diesem Anteil Unzufriedener (0..1). */
    angerThreshold: Double = DEFAULT_ANGER_THRESHOLD,
    onShow: (MapPlace) -> Unit = {},
    onClose: (() -> Unit)? = null,
    /** Straßennamen für die Brennpunkte; `null` = Gebietsnummer. */
    picker: MapPicker? = null,
    /** Sprung ins zuständige Amt bei „Warum sauer?“. */
    onPanel: ((SidePanel) -> Unit)? = null,
    /** Sprung zur Karte mit Lärm- bzw. Luft-Ebene. */
    onEnvironment: ((EnvironmentLayer) -> Unit)? = null,
    /** Aktuelle Spielstunde (Tag × 24 + Stunde) für die Zeitachse. */
    nowHour: Int = 0,
) {
    val moods = state.zoneMoods
    PanelScaffold(tr("Rathaus"), tr("Bürger"), modifier, onClose, floating = false) {
        // „Unzufrieden“ steht als Kennzahl (mit Schwelle) oben
        PanelSection(title = tr("Zufriedenheit je Haushaltstyp")) { HouseholdHistory(state, angerThreshold, nowHour) }
        PanelSection(title = tr("Warum sauer?")) { Reasons(moods, onPanel, onEnvironment) }
        PanelSection(title = tr("Was fehlt wem?")) {
            NeedMatrix(moods)
            NeedLegend()
        }
        PanelSection(title = tr("Brennpunkte"), divider = false) { Hotspots(state, picker, onShow) }
    }
}

/** Die Bedürfnisse: Name, Wert je Gebiet, Gewicht je Haushaltstyp und das Amt, das daran etwas ändern kann. */
private class Need(
    val label: String,
    val value: (ZoneMood) -> Double,
    val weight: (HouseholdType) -> Double,
    val panel: SidePanel? = null,
    val layer: EnvironmentLayer? = null,
)

private val NEEDS: List<Need>
    get() =
        listOf(
            Need(tr("Schnell am Ziel"), { it.speedSatisfaction }, { it.speedWeight }, SidePanel.TRANSIT),
            Need(tr("Bußgelder"), { it.mobilitySatisfaction }, { it.mobilityWeight }, SidePanel.OFFICE),
            Need(tr("Ruhe"), { it.quietSatisfaction }, { it.quietWeight }, layer = EnvironmentLayer.NOISE),
            Need(tr("Frische Luft"), { it.airSatisfaction }, { it.airWeight }, layer = EnvironmentLayer.AIR),
        )

/** Zufriedenheit je Haushaltstyp je Stunde seit Tag 1; darunter die Zone, in der Bürger als unzufrieden gelten. */
@Composable
private fun HouseholdHistory(
    state: GameState,
    angerThreshold: Double,
    nowHour: Int,
) {
    val history = state.citizenHistory.byHousehold
    val length = history.values.maxOfOrNull { it.size } ?: 0
    if (length < 2) {
        for (type in HouseholdType.entries) Bar(type.label, weighted(state.zoneMoods) { it.satisfactionOf(type) })
        return
    }
    // Y-Bereich ab 50 % (bzw. tiefer, wenn nötig): die Zorn-Zone ist ein Streifen unten,
    // die Linien bleiben unterscheidbar
    val lowest = minOf(history.values.minOf { it.minOrNull() ?: 1f }, ZoneMood.UNHAPPY_BELOW.toFloat())
    val bottom = minOf(HALF, (lowest * TENTHS).toInt() / TENTHS)
    TimeChart(
        TimeAxis(nowHour - (length - 1), length, state.firstDay),
        ChartAxis(bottom, 1f, threeTicks(bottom, 1f)) { "${(it * PERCENT).roundToInt()} %" },
        height = CHART_HEIGHT.dp,
    ) {
        // Zorn-Zone: unter dieser Zufriedenheit zählt ein Haushalt als unzufrieden
        val zone = y(ZoneMood.UNHAPPY_BELOW.toFloat())
        drawRect(TmColors.DangerTint, Offset(x(0f), zone), Size(x(length - 1f) - x(0f), y(bottom) - zone))
        for ((type, values) in history) {
            val color = HOUSEHOLD_COLORS.getValue(type)
            for (i in 1 until values.size) {
                drawLine(
                    color,
                    Offset(x(i - 1f), y(values[i - 1])),
                    Offset(x(i.toFloat()), y(values[i])),
                    2.dp.toPx(),
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (type in HouseholdType.entries) {
            val now = history[type]?.lastOrNull() ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(width = 12.dp, height = 3.dp).background(HOUSEHOLD_COLORS.getValue(type)))
                BasicText("${type.label} ${(now * PERCENT).roundToInt()} %", style = TmType.caption)
            }
        }
    }
    BasicText(
        tr(
            "Rot: unzufrieden. Sind es mehr als {threshold} % der Bürger, wächst der Zorn.",
            "threshold" to (angerThreshold * PERCENT).roundToInt(),
        ),
        style = TmType.caption,
    )
}

/** Anteil jedes Bedürfnisses an der Unzufriedenheit – gewichtet mit Fläche, Haushalten und deren Wichtigkeit. */
@Composable
private fun Reasons(
    moods: List<ZoneMood>,
    onPanel: ((SidePanel) -> Unit)?,
    onEnvironment: ((EnvironmentLayer) -> Unit)?,
) {
    val deficits =
        NEEDS.map { need ->
            need to
                moods.sumOf { mood ->
                    mood.area *
                        mood.households.entries.sumOf { (type, share) ->
                            share * need.weight(type) * (1 - need.value(mood))
                        }
                }
        }
    val total = deficits.sumOf { it.second }
    if (total <= 0.0) {
        BasicText(tr("Niemand ist sauer – noch nicht."), style = TmType.small)
        return
    }
    for ((need, deficit) in deficits.sortedByDescending { it.second }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(need.label, style = TmType.text(13.sp), modifier = Modifier.width(LABEL_WIDTH.dp))
            Meter((deficit / total).toFloat(), Modifier.weight(1f), height = 8.dp, color = TmColors.Warn)
            BasicText(
                "${(deficit / total * PERCENT).roundToInt()} %",
                style = TmType.mono(12.sp, bold = false),
                modifier = Modifier.width(PERCENT_WIDTH.dp),
            )
            // Feste Spalte für den Sprung, auch leer – sonst wären die Balken verschieden lang
            Box(
                Modifier.width(JUMP_WIDTH.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Jump(need, onPanel, onEnvironment) }
        }
    }
}

/** Sprung ins zuständige Amt („Ordnungsamt →“) bzw. zur Karte mit der passenden Ebene („Luft-Ebene →“). */
@Composable
private fun Jump(
    need: Need,
    onPanel: ((SidePanel) -> Unit)?,
    onEnvironment: ((EnvironmentLayer) -> Unit)?,
) {
    val panel = need.panel
    val layer = need.layer
    val (text, action) =
        when {
            panel != null && onPanel != null -> {
                panel.label + " →" to { onPanel(panel) }
            }

            layer != null && onEnvironment != null -> {
                tr("{layer}-Ebene →", "layer" to layer.label) to
                    { onEnvironment(layer) }
            }

            else -> {
                return
            }
        }
    BasicText(
        text,
        style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.Primary),
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(6.dp),
                ).clickable(role = Role.Button, onClick = action)
                .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** Matrix Haushaltstyp × Bedürfnis: wie gut es erfüllt ist, als Farbe von rot (schlecht) nach grün (gut). */
@Composable
private fun NeedMatrix(moods: List<ZoneMood>) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(LABEL_WIDTH.dp))
        for (need in NEEDS) {
            BasicText(
                need.label,
                style = TmType.caption.copy(textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f),
            )
        }
    }
    for (type in HouseholdType.entries) {
        Row(
            Modifier.fillMaxWidth().height(CELL_HEIGHT.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(type.label, style = TmType.text(13.sp), modifier = Modifier.width(LABEL_WIDTH.dp))
            for (need in NEEDS) {
                val weights = moods.map { it.area * (it.households[type] ?: 0.0) }
                val total = weights.sum()
                val value = if (total > 0) moods.indices.sumOf { weights[it] * need.value(moods[it]) } / total else 1.0
                Box(
                    Modifier
                        .weight(
                            1f,
                        ).fillMaxHeight()
                        .background(needColor(value).copy(alpha = CELL_ALPHA), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { BasicText("${(value * PERCENT).roundToInt()} %", style = TmType.mono(12.sp)) }
            }
        }
    }
}

/** Farbstufen der Matrix: unter 60 % rot, bis 80 % orange, bis 90 % hellgrün, darüber grün. */
private fun needColor(value: Double): Color = NEED_STEPS.firstOrNull { value < it.first }?.second ?: TmColors.Success

private val NEED_STEPS: List<Pair<Double, Color>>
    get() = listOf(0.6 to TmColors.Danger, 0.8 to TmColors.Warn, 0.9 to LightGreen)

private val LightGreen = Color(0xFF9BD17A)

@Composable
private fun NeedLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((color, text) in listOf(
            TmColors.Danger to "< 60 %",
            TmColors.Warn to "60–80 %",
            LightGreen to "80–90 %",
            TmColors.Success to "> 90 %",
        )) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).background(color.copy(alpha = CELL_ALPHA), RoundedCornerShape(2.dp)))
                BasicText(text, style = TmType.caption)
            }
        }
    }
}

/** Trend gegen gestern: ↗ grün (besser), ↘ rot (schlechter), sonst „gleich“ – mit Punkten. */
@Composable
private fun Trend(trend: Double?) {
    if (trend == null) return
    val points = (trend * PERCENT).roundToInt()
    val (text, color) =
        when {
            trend > TREND_STEP -> "↗ +$points" to TmColors.SuccessText
            trend < -TREND_STEP -> "↘ $points" to TmColors.DangerText
            else -> tr("gleich") to TmColors.Muted
        }
    BasicText(text, style = TmType.mono(12.sp, color = color), modifier = Modifier.width(TREND_WIDTH.dp))
}

/** Die unzufriedensten Gebiete: Straße, Zufriedenheit, Hauptgrund, Trend gegen gestern, „Zeigen“. */
@Composable
private fun Hotspots(
    state: GameState,
    picker: MapPicker?,
    onShow: (MapPlace) -> Unit,
) {
    val yesterday = state.citizenHistory.zonesYesterday
    for (mood in state.zoneMoods.sortedBy { it.satisfaction }.take(WORST_ZONES)) {
        val name =
            remember(mood.zoneId, picker) {
                picker?.nearestSegment(mood.x, mood.y, NAME_RADIUS, allowPassive = { true }, named = true)?.let {
                    picker.city.roads.segments[it]
                        .name
                }
            } ?: tr("Gebiet {zoneId}", "zoneId" to mood.zoneId)
        val reason = NEEDS.minBy { it.value(mood) }
        val trend = yesterday[mood.zoneId]?.let { mood.satisfaction - it }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(tr("bei {street}", "street" to name), style = TmType.text(13.sp, FontWeight.SemiBold))
                BasicText(tr("Hauptgrund: {reason}", "reason" to reason.label), style = TmType.caption)
            }
            BasicText("${(mood.satisfaction * PERCENT).roundToInt()} %", style = TmType.mono(13.sp))
            Trend(trend)
            TmButton(tr("Zeigen"), { onShow(MapPlace(mood.x, mood.y)) }, kind = ButtonKind.TEXT)
        }
    }
}

private val HOUSEHOLD_COLORS =
    mapOf(
        HouseholdType.COMMUTER to TmColors.Primary,
        HouseholdType.FAMILY to Color(0xFF2A9D8F),
        HouseholdType.RETIREE to Color(0xFF9C6644),
    )

private const val CHART_HEIGHT = 140
private const val CELL_HEIGHT = 34
private const val CELL_ALPHA = 0.35f
private const val JUMP_WIDTH = 140
private const val NAME_RADIUS = 1500f
private const val TREND_STEP = 0.01
private const val TREND_WIDTH = 60

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

private const val PERCENT = 100
private const val WORST_ZONES = 3
private const val DEFAULT_ANGER_THRESHOLD = 0.35
private const val GOOD = 0.6
private const val LABEL_WIDTH = 130
private const val PERCENT_WIDTH = 44
private const val HALF = 0.5f
private const val TENTHS = 10f
