package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Difficulty
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.i18n.Language
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.Divider
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.SettingRow

/** Bildschirm mit Kopf und einer weißen Fläche darunter (Bestenliste, Einstellungen, Credits). */
@Composable
private fun MenuPage(
    eyebrow: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        ScreenHeader(eyebrow, title)
        Column(
            Modifier
                .widthIn(max = PAGE_WIDTH.dp)
                .fillMaxWidth()
                .background(TmColors.Surface, shape)
                .border(2.dp, TmColors.Ink, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun HighscoreScreen(
    scenarios: List<Scenario>,
    highscores: Highscores,
) {
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    MenuPage(tr("Bestenliste"), tr("Die besten Amtszeiten")) {
        SegmentedControl(
            Difficulty.entries,
            difficulty,
            { it.label },
            { difficulty = it },
            Modifier.widthIn(max = 360.dp),
        )
        for (scenario in scenarios.filter { !it.tutorial }) {
            Divider()
            BasicText(tr(scenario.title), style = TmType.display(18.sp))
            val table = highscores.of(scenario.id, difficulty)
            if (table.isEmpty()) BasicText(tr("Noch keine Amtszeit beendet."), style = TmType.small)
            table.forEachIndexed { index, entry ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText("${index + 1}.", style = TmType.mono(14.sp), modifier = Modifier.width(28.dp))
                    BasicText(
                        tr("{points} Punkte", "points" to entry.points),
                        style = TmType.mono(14.sp),
                        modifier = Modifier.width(120.dp),
                    )
                    BasicText(
                        formatEuro(entry.earnings),
                        style = TmType.mono(13.sp, bold = false),
                        modifier = Modifier.width(120.dp),
                    )
                    BasicText(
                        tr("Reputation {reputation}", "reputation" to entry.reputation),
                        style = TmType.text(13.sp),
                        modifier = Modifier.weight(1f),
                    )
                    if (entry.honoraryCitizen) Badge(tr("Ehrenbürger"), tone = BadgeTone.SUCCESS)
                    BasicText(entry.date, style = TmType.small)
                }
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
) {
    MenuPage(tr("Einstellungen"), tr("So spielen Sie")) {
        SettingRow(tr("Sprache")) {
            // Sprachnamen immer in der eigenen Sprache – sonst findet man nicht zurück
            SegmentedControl(
                Language.entries,
                I18n.language,
                { it.label },
                { onSettings(settings.copy(language = it.code)) },
                Modifier.width(CONTROL_WIDTH.dp),
            )
        }
        Divider()
        OnOff(tr("Geräusche"), settings.sound) { onSettings(settings.copy(sound = it)) }
        OnOff(tr("Musik"), settings.music) { onSettings(settings.copy(music = it)) }
        OnOff(tr("Fertige Grafiken aus assets/sprites (sonst gezeichnet)"), settings.sprites) {
            onSettings(settings.copy(sprites = it))
        }
        Divider()
        SettingRow(tr("Tempo beim Start")) {
            SegmentedControl(
                SPEEDS,
                settings.startSpeed,
                { "$it×" },
                { onSettings(settings.copy(startSpeed = it)) },
                Modifier.width(CONTROL_WIDTH.dp),
            )
        }
        BasicText(
            tr("Im Spiel: M schaltet den Ton, N die Musik – Einstellungen werden automatisch gespeichert."),
            style = TmType.small,
        )
    }
}

@Composable
private fun OnOff(
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(label) {
        SegmentedControl(listOf(false, true), on, {
            if (it) tr("An") else tr("Aus")
        }, onChange, Modifier.width(ON_OFF_WIDTH.dp))
    }
}

@Composable
internal fun CreditsScreen(attribution: String) {
    MenuPage(tr("Credits"), tr("Wer das verbrochen hat")) {
        for (line in CREDITS) BasicText(tr(line), style = TmType.text(14.sp).copy(lineHeight = 21.sp))
        Divider()
        BasicText(
            tr("Kartendaten: {attribution}", "attribution" to attribution),
            style = TmType.text(14.sp, FontWeight.Bold),
        )
        BasicText(
            tr("Die Kartendaten stehen unter der Open Database License (ODbL) – openstreetmap.org/copyright."),
            style = TmType.small,
        )
    }
}

private val CREDITS =
    listOf(
        translatable("traffic-maniac – eine Parodie auf Verkehrssimulationen."),
        translatable(
            "Spiel, Simulation, Grafik und Musik: Black Forest Interactive " +
                "(Grafik und Klänge werden im Spiel erzeugt).",
        ),
        translatable("Programmiert in Kotlin mit Compose Multiplatform und Skia."),
        translatable(
            "Schriften: Bricolage Grotesque, Inter und JetBrains Mono (SIL Open Font License 1.1). " +
                "Icons nach Lucide (ISC-Lizenz).",
        ),
        translatable(
            "Alle Personen, Firmen, Vereine und Ereignisse sind frei erfunden. Ähnlichkeiten mit echten " +
                "Verkehrspolitikern wären rein zufällig.",
        ),
        translatable("Lizenz: Apache License 2.0."),
    )

private val SPEEDS = listOf(1, 2, 4, 8)
private const val PAGE_WIDTH = 920
private const val CONTROL_WIDTH = 320
private const val ON_OFF_WIDTH = 180
