package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.CityTexts
import de.bfi.trafficmaniac.game.Difficulty
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.SaveInfo
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.ui.map.MapStyle

private enum class MenuTab(
    val label: String,
) {
    PLAY("Spielen"),
    LOAD("Laden"),
    HIGHSCORES("Highscores"),
    SETTINGS("Einstellungen"),
    CREDITS("Credits"),
}

/**
 * Startmenü: Szenario und Schwierigkeitsgrad wählen, Bestenlisten, Einstellungen und Credits.
 * [loading] zeigt eine Ladeanzeige, solange ein Szenario vorbereitet wird.
 */
@Composable
fun MainMenu(
    scenarios: List<Scenario>,
    highscores: Highscores,
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
    onStart: (Scenario) -> Unit,
    onQuit: () -> Unit,
    attribution: String,
    loading: String? = null,
    /** Gespeicherte Partien; ohne Speicherfunktion leer. */
    saves: SaveSlots = SaveSlots(),
    /** Unterzeile unter dem Titel – kommt aus dem Stadtpaket. */
    tagline: String = CityTexts().tagline,
    cities: CityChoice = CityChoice(),
) {
    var tab by remember { mutableStateOf(MenuTab.PLAY) }
    var selected by remember { mutableStateOf(scenarios.firstOrNull()) }
    Box(Modifier.fillMaxSize().background(MapStyle.land)) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicText(
                "TRAFFIC MANIAC",
                style = TextStyle(color = ComicTheme.ink, fontSize = 44.sp, fontWeight = FontWeight.Black),
            )
            BasicText(
                tagline,
                style = TextStyle(color = ComicTheme.muted, fontSize = 15.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (option in MenuTab.entries) MenuButton(option.label, tab == option) { tab = option }
                    MenuButton("Beenden", false, onQuit)
                }
                Box(Modifier.width(CONTENT_WIDTH.dp).comicPanel(shadow = 6.dp).padding(20.dp)) {
                    when (tab) {
                        MenuTab.PLAY -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CityRow(cities)
                                PlayTab(scenarios, selected, highscores, settings, { selected = it }, onSettings) {
                                    selected?.let(onStart)
                                }
                            }
                        }

                        MenuTab.LOAD -> {
                            LoadTab(saves)
                        }

                        MenuTab.HIGHSCORES -> {
                            HighscoreTab(scenarios, highscores)
                        }

                        MenuTab.SETTINGS -> {
                            SettingsTab(settings, onSettings)
                        }

                        MenuTab.CREDITS -> {
                            CreditsTab(attribution)
                        }
                    }
                }
            }
        }
        loading?.let { Loading(it) }
    }
}

@Composable
private fun PlayTab(
    scenarios: List<Scenario>,
    selected: Scenario?,
    highscores: Highscores,
    settings: GameSettings,
    onSelect: (Scenario) -> Unit,
    onSettings: (GameSettings) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Szenario wählen", style = heading())
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (scenario in scenarios) {
                ScenarioCard(
                    scenario,
                    scenario == selected,
                    highscores.best(scenario.id)?.points,
                ) { onSelect(scenario) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Schwierigkeit:", style = body())
            for (difficulty in Difficulty.entries) {
                MenuChip(difficulty.label, settings.difficulty == difficulty) {
                    onSettings(settings.copy(difficulty = difficulty))
                }
            }
        }
        MenuButton("Los geht's!", active = true, onClick = onStart)
    }
}

@Composable
private fun ScenarioCard(
    scenario: Scenario,
    selected: Boolean,
    best: Long?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .comicPanel(background = if (selected) ComicTheme.button else ComicTheme.paperDark, shadow = 3.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(scenario.title, style = body().copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        BasicText(scenario.description, style = body())
        val start = "${scenario.startWeekday.germanName}, ${scenario.startHour.toInt()} Uhr"
        val record = best?.let { " · Bestwert: $it Punkte" } ?: ""
        BasicText("${scenario.termDays} Tage · Start $start$record", style = muted())
    }
}

/** Stadtauswahl: Namen der Stadtpakete, die gewählte und ihre Beschreibung. */
class CityChoice(
    val names: List<String> = emptyList(),
    val selected: Int = 0,
    val description: String = "",
    val onSelect: (Int) -> Unit = {},
)

@Composable
private fun CityRow(cities: CityChoice) {
    if (cities.names.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Stadt:", style = body())
            cities.names.forEachIndexed { index, name ->
                MenuChip(name, index == cities.selected) { cities.onSelect(index) }
            }
        }
        if (cities.description.isNotEmpty()) BasicText(cities.description, style = muted())
    }
}

/** Spielstände fürs Menü: Liste, Laden und Löschen. */
class SaveSlots(
    val saves: List<SaveInfo> = emptyList(),
    val onLoad: (SaveInfo) -> Unit = {},
    val onDelete: (SaveInfo) -> Unit = {},
)

@Composable
private fun LoadTab(slots: SaveSlots) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Spielstand laden", style = heading())
        if (slots.saves.isEmpty()) {
            BasicText(
                "Noch nichts gespeichert – im Spiel: Knopf „Speichern“ oder F5 (Schnellspeichern).",
                style = muted(),
            )
        }
        for (save in slots.saves) {
            Column(
                Modifier.fillMaxWidth().comicPanel(background = ComicTheme.paperDark, shadow = 3.dp).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val quick = if (save.id == SaveInfo.QUICK_SAVE) "Schnellspeicher · " else ""
                BasicText(
                    "$quick${save.cityName} · ${save.scenarioTitle} · ${save.difficulty.label}",
                    style = body().copy(fontWeight = FontWeight.Bold),
                )
                BasicText(
                    "${save.gameTime} · Kasse ${formatEuro(save.balance)} · Reputation ${save.reputation}",
                    style = body(),
                )
                BasicText("gespeichert ${save.savedAt.replace('T', ' ')}", style = muted())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuChip("Laden", true) { slots.onLoad(save) }
                    MenuChip("Löschen", false) { slots.onDelete(save) }
                }
            }
        }
    }
}

@Composable
private fun HighscoreTab(
    scenarios: List<Scenario>,
    highscores: Highscores,
) {
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Bestenliste", style = heading())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in Difficulty.entries) MenuChip(option.label, option == difficulty) { difficulty = option }
        }
        for (scenario in scenarios) {
            BasicText(scenario.title, style = body().copy(fontWeight = FontWeight.Bold))
            val table = highscores.of(scenario.id, difficulty)
            if (table.isEmpty()) BasicText("Noch keine Amtszeit beendet.", style = muted())
            table.forEachIndexed { index, entry ->
                val seal = if (entry.honoraryCitizen) " · Ehrenbürger" else ""
                BasicText(
                    "${index + 1}. ${entry.points} Punkte · ${formatEuro(
                        entry.earnings,
                    )} · Reputation ${entry.reputation}$seal" +
                        " · ${entry.date}",
                    style = body(),
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Einstellungen", style = heading())
        Toggle("Geräusche", settings.sound) { onSettings(settings.copy(sound = it)) }
        Toggle("Musik", settings.music) { onSettings(settings.copy(music = it)) }
        Toggle("Fertige Grafiken aus assets/sprites (sonst gezeichnet)", settings.sprites) {
            onSettings(settings.copy(sprites = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("Tempo beim Start:", style = body())
            for (speed in SPEEDS) {
                MenuChip("$speed×", settings.startSpeed == speed) { onSettings(settings.copy(startSpeed = speed)) }
            }
        }
        BasicText(
            "Im Spiel: M schaltet den Ton, N die Musik – Einstellungen werden automatisch gespeichert.",
            style = muted(),
        )
    }
}

@Composable
private fun CreditsTab(attribution: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Credits", style = heading())
        for (line in CREDITS) BasicText(line, style = body())
        BasicText("Kartendaten: $attribution", style = body().copy(fontWeight = FontWeight.Bold))
        BasicText(
            "Die Kartendaten stehen unter der Open Database License (ODbL) – openstreetmap.org/copyright.",
            style = muted(),
        )
    }
}

private val CREDITS =
    listOf(
        "traffic-maniac – eine Parodie auf Verkehrssimulationen.",
        "Spiel, Simulation, Grafik und Musik: Black Forest Interactive (Grafik und Klänge werden im Spiel erzeugt).",
        "Programmiert in Kotlin mit Compose Multiplatform und Skia.",
        "Alle Personen, Firmen, Vereine und Ereignisse sind frei erfunden. Ähnlichkeiten mit echten " +
            "Verkehrspolitikern wären rein zufällig.",
        "Lizenz: Apache License 2.0.",
    )

@Composable
private fun Loading(text: String) {
    Box(Modifier.fillMaxSize().background(ComicTheme.scrim), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = TextStyle(color = ComicTheme.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.comicPanel(shadow = 6.dp).padding(horizontal = 32.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun MenuButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        style = TextStyle(color = ComicTheme.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        modifier =
            Modifier
                .width(BUTTON_WIDTH.dp)
                .comicButton(active)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun MenuChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        style = body().copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
        modifier =
            Modifier
                .comicButton(
                    active,
                ).clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun Toggle(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MenuChip(if (on) "An" else "Aus", on) { onChange(!on) }
        BasicText(label, style = body())
    }
}

private fun heading() =
    TextStyle(color = ComicTheme.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)

private fun body() = TextStyle(color = ComicTheme.ink, fontSize = 13.sp)

private fun muted() = TextStyle(color = ComicTheme.muted, fontSize = 12.sp)

private val SPEEDS = listOf(1, 2, 4, 8)
private const val CONTENT_WIDTH = 640
private const val BUTTON_WIDTH = 220
