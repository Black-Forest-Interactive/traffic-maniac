package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Difficulty
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.TmButton

private val CardShape = RoundedCornerShape(16.dp)
private val CityBadge = Color(0xFF1F5FAD)

/** Neues Spiel: Stadt, Szenario als Kachel wählen, unten Schwierigkeit und „Los geht's“. */
@Composable
internal fun PlayScreen(
    scenarios: List<Scenario>,
    highscores: Highscores,
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
    onStart: (Scenario) -> Unit,
    cities: CityChoice,
) {
    var selected by remember { mutableStateOf(scenarios.firstOrNull()) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).padding(start = 48.dp, end = 48.dp, top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ScreenHeader(tr("Neues Spiel"), tr("Wählen Sie Ihre Amtszeit"), Modifier.weight(1f))
                if (cities.names.isNotEmpty()) CityCard(cities)
            }
            LazyVerticalGrid(
                GridCells.Adaptive(CARD_MIN_WIDTH.dp),
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(bottom = 24.dp, end = 6.dp),
            ) {
                items(scenarios, key = { it.id }) { scenario ->
                    ScenarioCard(scenario, scenario == selected, highscores.best(scenario.id)?.points) {
                        selected = scenario
                    }
                }
            }
        }
        ActionBar(selected, settings, onSettings, cities) { selected?.let(onStart) }
    }
}

/** Die gewählte Stadt: Kürzel, Name, Spitzname, Beschreibung – „Stadt ändern“, wenn es mehrere gibt. */
@Composable
private fun CityCard(cities: CityChoice) {
    val name = cities.names.getOrElse(cities.selected) { "" }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .widthIn(max = CITY_CARD_WIDTH.dp)
            .background(TmColors.Surface, shape)
            .border(2.dp, TmColors.Ink, shape)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(CityBadge, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(name.take(2).uppercase(), style = TmType.display(18.sp, TmColors.Surface))
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(name, style = TmType.text(15.sp, FontWeight.Bold))
                if (cities.nickname.isNotEmpty()) {
                    BasicText(" · ${cities.nickname}", style = TmType.text(13.sp, FontWeight.Medium, TmColors.Muted))
                }
            }
            if (cities.description.isNotEmpty()) {
                BasicText(
                    cities.description,
                    style = TmType.text(12.sp, color = TmColors.LineDark),
                )
            }
        }
        if (cities.names.size > 1) {
            TmButton(tr("Stadt ändern"), { cities.onSelect((cities.selected + 1) % cities.names.size) })
        }
    }
}

@Composable
private fun ScenarioCard(
    scenario: Scenario,
    selected: Boolean,
    best: Long?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // Gewählt: kräftiger blauer Rahmen, Häkchen und Schatten; sonst nur ein feiner Rahmen
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CARD_MIN_HEIGHT.dp)
            .then(if (selected) Modifier.hardShadow(5.dp, TmColors.Ink) else Modifier)
            .background(if (hovered && !selected) TmColors.Ground else TmColors.Surface, CardShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) TmColors.Primary else TmColors.Line, CardShape)
            .hoverable(interaction)
            .clickable(interaction, LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            scenario.tag?.let { Tag(tr(it), highlighted = scenario.tutorial) }
            Box(Modifier.weight(1f))
            if (selected) {
                Box(
                    Modifier.size(28.dp).background(TmColors.Primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Check, tr("Gewählt"), tint = TmColors.Surface, size = 16.dp)
                }
            }
        }
        BasicText(tr(scenario.title), style = TmType.display(22.sp).copy(lineHeight = 25.sp))
        BasicText(
            tr(scenario.description),
            style = TmType.text(14.sp, color = TmColors.Muted).copy(lineHeight = 21.sp),
        )
        DashedLine()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Calendar, null, tint = TmColors.Ink, size = 15.dp)
            BasicText(tr("{days} Tage", "days" to scenario.termDays), style = TmType.text(13.sp, FontWeight.SemiBold))
            BasicText(
                tr(
                    "Start {weekday}, {hour} Uhr",
                    "weekday" to scenario.startWeekday.germanName,
                    "hour" to scenario.startHour.toInt(),
                ),
                style = TmType.text(13.sp, color = TmColors.Muted),
            )
        }
        best?.let {
            BasicText(
                tr("Bestwert: {value} Punkte", "value" to it),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Success),
            )
        }
    }
}

@Composable
private fun Tag(
    text: String,
    highlighted: Boolean,
) {
    BasicText(
        text.uppercase(),
        style = TmType.text(11.sp, FontWeight.Bold, TmColors.Ink).copy(letterSpacing = TAG_SPACING.sp),
        modifier =
            Modifier
                .background(if (highlighted) TmColors.Active else TmColors.LineSoft, RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun DashedLine() {
    Box(
        Modifier.fillMaxWidth().height(2.dp).drawBehind {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            drawLine(TmColors.OnDarkMuted, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx(), pathEffect = dash)
        },
    )
}

/** Feste Leiste unten: Schwierigkeit, Zusammenfassung und der große Startknopf. */
@Composable
private fun ActionBar(
    selected: Scenario?,
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
    cities: CityChoice,
    onStart: () -> Unit,
) {
    Column {
        Box(Modifier.fillMaxWidth().height(2.dp).background(TmColors.Ink))
        Row(
            Modifier.fillMaxWidth().background(TmColors.Ground).padding(horizontal = 48.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(tr("Schwierigkeit"), style = TmType.text(14.sp, FontWeight.Bold))
            SegmentedControl(
                Difficulty.entries,
                settings.difficulty,
                { it.label },
                { onSettings(settings.copy(difficulty = it)) },
                Modifier.width(DIFFICULTY_WIDTH.dp),
            )
            Box(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val city = cities.names.getOrElse(cities.selected) { "" }
                BasicText(
                    listOf(city, settings.difficulty.label).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = TmType.small,
                )
                selected?.let {
                    BasicText(
                        tr("{title} · {days} Tage", "title" to tr(it.title), "days" to it.termDays),
                        style = TmType.text(15.sp, FontWeight.Bold),
                    )
                }
            }
            TmButton(tr("Los geht's →"), onStart, kind = ButtonKind.PRIMARY, large = true, enabled = selected != null)
        }
    }
}

/** Harter, versetzter Schatten wie bei den Karten im Entwurf. */
private fun Modifier.hardShadow(
    offset: Dp,
    color: Color,
): Modifier =
    drawBehind {
        val o = offset.toPx()
        drawRoundRect(color, Offset(o, o), size, CornerRadius(16.dp.toPx()))
    }

private const val CARD_MIN_WIDTH = 300
private const val CARD_MIN_HEIGHT = 236
private const val CITY_CARD_WIDTH = 520
private const val DIFFICULTY_WIDTH = 300
private const val TAG_SPACING = 0.8f
