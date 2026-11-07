package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.ui.map.EnvironmentLayer
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlin.math.roundToInt

/** Einblendbare Fenster rechts oben. */
internal enum class SidePanel(
    val label: String,
) {
    NONE(""),
    CAMERAS("Blitzer-Statistik"),
    BUDGET("Haushalt & PR"),
    OFFICE("Ordnungsamt"),
    MAINTENANCE("Bauamt"),
    PARKING("Parken"),
    CITIZENS("Bürger"),
    EVENTS("Kalender"),
    EMERGENCIES("Einsätze"),
    ACHIEVEMENTS("Achievements"),
    HELP("Legende ?"),
}

/** Einblendbare Karten-Ebenen: Umwelt, Zufriedenheit und Straßenzustand. */
internal class MapToggles {
    var showMoods by mutableStateOf(false)
    var showCondition by mutableStateOf(false)
    var environmentLayer by mutableStateOf(EnvironmentLayer.NONE)

    fun nextEnvironmentLayer() {
        environmentLayer = EnvironmentLayer.entries[(environmentLayer.ordinal + 1) % EnvironmentLayer.entries.size]
    }
}

/** Knopfleiste rechts oben und das gerade geöffnete Fenster darunter. */
@Composable
internal fun SidePanels(
    frame: TrafficFrame,
    rules: GameRules,
    panel: SidePanel,
    onPanel: (SidePanel) -> Unit,
    toggles: MapToggles,
    onCommand: (GameCommand) -> Unit,
    picker: MapPicker,
    /** Klick auf eine Meldung, ein Ereignis oder einen Einsatz: die Karte zeigt die Stelle. */
    onShow: (MapPlace) -> Unit,
    onMenu: (() -> Unit)?,
    sprites: SpriteSource,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        // Bricht in eine zweite Zeile um, statt das HUD links oben zu überdecken
        FlowRow(Modifier.widthIn(max = BUTTON_ROW_WIDTH.dp), horizontalArrangement = Arrangement.End) {
            onMenu?.let { SmallButton("Menü", it) }
            val layer = toggles.environmentLayer
            SmallButton(if (layer == EnvironmentLayer.NONE) "Umwelt" else "Umwelt: ${layer.label}") {
                toggles.nextEnvironmentLayer()
            }
            SmallButton(if (toggles.showMoods) "Zufriedenheit ausblenden" else "Zufriedenheit") {
                toggles.showMoods = !toggles.showMoods
            }
            SmallButton(if (toggles.showCondition) "Zustand ausblenden" else "Zustand") {
                toggles.showCondition = !toggles.showCondition
            }
            for (option in SidePanel.entries.drop(1)) {
                val count = frame.current.incidents.size
                val label =
                    if (option == SidePanel.EMERGENCIES &&
                        count > 0
                    ) {
                        "${option.label} ($count)"
                    } else {
                        option.label
                    }
                SmallButton(label) { onPanel(option) }
            }
        }
        PanelContent(panel, frame, rules, onCommand, picker, onShow, sprites)
    }
}

@Composable
private fun PanelContent(
    panel: SidePanel,
    frame: TrafficFrame,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
    sprites: SpriteSource,
) {
    val game = frame.game
    when (panel) {
        SidePanel.CAMERAS -> {
            CameraStatsPanel(game.cameras)
        }

        SidePanel.BUDGET -> {
            BudgetPanel(
                game,
                rules,
                onContract = { onCommand(GameCommand.SetPrContract(it)) },
                onArguments = { onCommand(GameCommand.SetArgumentsCampaign(it)) },
                onCeremony = { onCommand(GameCommand.HoldCeremony) },
            )
        }

        SidePanel.OFFICE -> {
            OfficePanel(game.fineOffice, rules, game.bannedDrivers, onCommand)
        }

        SidePanel.MAINTENANCE -> {
            MaintenancePanel(game.maintenance, rules, onCommand) { report ->
                // Ampelmeldungen haben keinen Abschnitt – dann zur Ampel selbst
                val place = if (report.node >= 0) picker.placeOfNode(report.node) else picker.place(report.segments)
                place?.let(onShow)
            }
        }

        SidePanel.PARKING -> {
            ParkingPanel(game.parkingOffice, frame.current.parking, frame.current.parkingLots, rules, onCommand)
        }

        SidePanel.CITIZENS -> {
            CitizensPanel(game)
        }

        SidePanel.EVENTS -> {
            EventsPanel(
                game.events,
                frame.current.gameTime.day,
                firstDay = game.firstDay,
            ) { picker.place(it)?.let(onShow) }
        }

        SidePanel.EMERGENCIES -> {
            EmergencyPanel(frame.current.incidents, frame.current.time, onShow)
        }

        SidePanel.ACHIEVEMENTS -> {
            AchievementsPanel(game.achievementList, firstDay = game.firstDay)
        }

        SidePanel.HELP -> {
            HelpPanel(sprites = sprites)
        }

        SidePanel.NONE -> {}
    }
}

/**
 * Laufende Einsätze: Unfälle zuerst, dann die neuesten. Jeder Eintrag zeigt, wer schon vor Ort und wer noch unterwegs
 * ist – ein Klick fliegt hin.
 */
@Composable
private fun EmergencyPanel(
    incidents: List<IncidentMarker>,
    now: Double,
    onShow: (MapPlace) -> Unit,
) {
    val title = TextStyle(color = ComicTheme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = ComicTheme.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val muted = TextStyle(color = ComicTheme.muted, fontSize = 11.sp)
    Column(
        Modifier
            .width(PANEL_WIDTH.dp)
            .padding(12.dp)
            .comicPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("Einsätze (${incidents.size})", style = title)
        if (incidents.isEmpty()) BasicText("Alles ruhig – kein Blaulicht unterwegs.", style = muted)
        val sorted = incidents.sortedWith(compareBy({ it.kind != IncidentKind.ACCIDENT }, { -it.startTime }))
        for (incident in sorted.take(MAX_ROWS)) {
            val minutes = ((now - incident.startTime) / SECONDS_PER_MINUTE).roundToInt()
            val color = if (incident.kind == IncidentKind.ACCIDENT) ComicTheme.red else ComicTheme.ink
            Column(Modifier.clickable { onShow(MapPlace(incident.x, incident.y)) }) {
                BasicText("▸ ${incident.kind.label} – seit $minutes min", style = text.copy(color = color))
                val details =
                    listOfNotNull(
                        incident.onScene.takeIf { it.isNotEmpty() }?.let {
                            "vor Ort: " +
                                it.joinToString { r -> r.label }
                        },
                        incident.onTheWay.takeIf { it.isNotEmpty() }?.let {
                            "unterwegs: " +
                                it.joinToString { r -> r.label }
                        },
                    )
                if (details.isNotEmpty()) BasicText(details.joinToString(" · "), style = muted)
            }
        }
        if (incidents.size > MAX_ROWS) BasicText("… und ${incidents.size - MAX_ROWS} weitere", style = muted)
    }
}

@Composable
private fun SmallButton(
    label: String,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        style = TextStyle(color = ComicTheme.ink, fontSize = 13.sp),
        modifier =
            Modifier
                .padding(top = 12.dp, end = 12.dp)
                .comicButton(active = false)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private const val PANEL_WIDTH = 380
private const val BUTTON_ROW_WIDTH = 860
private const val MAX_ROWS = 12
private const val SECONDS_PER_MINUTE = 60.0
