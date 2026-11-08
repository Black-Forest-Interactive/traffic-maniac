package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlin.math.roundToInt

/** Fenster, die die linke Leiste rechts öffnet. */
internal enum class SidePanel(
    private val labelText: String,
) {
    NONE(""),
    CAMERAS(translatable("Blitzer-Statistik")),
    BUDGET(translatable("Haushalt & PR")),
    OFFICE(translatable("Ordnungsamt")),
    MAINTENANCE(translatable("Bauamt")),
    PARKING(translatable("Parken")),
    CITIZENS(translatable("Bürger")),
    EVENTS(translatable("Kalender")),
    EMERGENCIES(translatable("Einsätze")),
    ACHIEVEMENTS(translatable("Achievements")),
    HELP(translatable("Legende ?")),
    ;

    val label: String get() = tr(labelText)
}

/** Was ein Fenster im Seitenbereich braucht. */
internal class PanelContext(
    val frame: TrafficFrame,
    val rules: GameRules,
    val onCommand: (GameCommand) -> Unit,
    val picker: MapPicker,
    /** Klick auf eine Meldung, ein Ereignis oder einen Einsatz: die Karte zeigt die Stelle. */
    val onShow: (MapPlace) -> Unit,
    val sprites: SpriteSource,
    val onClose: () -> Unit,
)

/** Seitenbereich rechts (372 dp, volle Höhe) mit dem geöffneten Fenster; scrollt, wenn es länger ist. */
@Composable
internal fun SidePanelSlot(
    panel: SidePanel,
    context: PanelContext,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(TmColors.Line))
        Column(Modifier.width(SLOT_WIDTH.dp).fillMaxHeight().background(TmColors.Surface)) {
            with(context) { PanelContent(panel, frame, rules, onCommand, picker, onShow, sprites, onClose) }
        }
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
    onClose: () -> Unit,
) {
    val game = frame.game
    when (panel) {
        SidePanel.CAMERAS -> {
            CameraStatsPanel(game.cameras, onClose = onClose)
        }

        SidePanel.BUDGET -> {
            BudgetPanel(
                game,
                rules,
                onContract = { onCommand(GameCommand.SetPrContract(it)) },
                onArguments = { onCommand(GameCommand.SetArgumentsCampaign(it)) },
                onCeremony = { onCommand(GameCommand.HoldCeremony) },
                onClose = onClose,
            )
        }

        SidePanel.OFFICE -> {
            OfficePanel(game.fineOffice, rules, game.bannedDrivers, onCommand, onClose = onClose)
        }

        SidePanel.MAINTENANCE -> {
            MaintenancePanel(game.maintenance, rules, onCommand, onClose = onClose, onShow = { report ->
                // Ampelmeldungen haben keinen Abschnitt – dann zur Ampel selbst
                val place = if (report.node >= 0) picker.placeOfNode(report.node) else picker.place(report.segments)
                place?.let(onShow)
            })
        }

        SidePanel.PARKING -> {
            ParkingPanel(
                game.parkingOffice,
                frame.current.parking,
                frame.current.parkingLots,
                rules,
                onCommand,
                onClose = onClose,
            )
        }

        SidePanel.CITIZENS -> {
            CitizensPanel(game, angerThreshold = rules.angerThreshold, onShow = onShow, onClose = onClose)
        }

        SidePanel.EVENTS -> {
            EventsPanel(
                game.events,
                frame.current.gameTime.day,
                firstDay = game.firstDay,
                onClose = onClose,
            ) { picker.place(it)?.let(onShow) }
        }

        SidePanel.EMERGENCIES -> {
            EmergencyPanel(frame.current.incidents, frame.current.time, onShow, onClose)
        }

        SidePanel.ACHIEVEMENTS -> {
            AchievementsPanel(game.achievementList, firstDay = game.firstDay, onClose = onClose)
        }

        SidePanel.HELP -> {
            HelpPanel(sprites = sprites, onClose = onClose)
        }

        SidePanel.NONE -> {}
    }
}

/**
 * Laufende Einsätze: Unfälle zuerst, dann die neuesten. Jeder Einsatz mit farbiger Seitenleiste (Unfall rot, Panne
 * orange), Dauer und Einsatzkräften als Etiketten (vor Ort grün, unterwegs gelb) – ein Klick fliegt hin.
 */
@Composable
private fun EmergencyPanel(
    incidents: List<IncidentMarker>,
    now: Double,
    onShow: (MapPlace) -> Unit,
    onClose: () -> Unit,
) {
    PanelScaffold(
        tr("Bericht"),
        tr("Einsätze"),
        floating = false,
        onClose = onClose,
        headerExtra = { if (incidents.isNotEmpty()) Badge("${incidents.size}", tone = BadgeTone.DANGER) },
    ) {
        PanelSection(divider = false) {
            if (incidents.isEmpty()) BasicText(tr("Alles ruhig – kein Blaulicht unterwegs."), style = TmType.small)
            val sorted = incidents.sortedWith(compareBy({ it.kind != IncidentKind.ACCIDENT }, { -it.startTime }))
            for (incident in sorted.take(
                MAX_ROWS,
            )) {
                IncidentCard(incident, now) { onShow(MapPlace(incident.x, incident.y)) }
            }
            if (incidents.size > MAX_ROWS) {
                BasicText(
                    tr("… und {incidents} weitere", "incidents" to incidents.size - MAX_ROWS),
                    style = TmType.small,
                )
            }
        }
    }
}

@Composable
private fun IncidentCard(
    incident: IncidentMarker,
    now: Double,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val accent =
        when (incident.kind) {
            IncidentKind.ACCIDENT, IncidentKind.TRUCK_STUCK -> TmColors.Danger
            IncidentKind.OIL_SPILL -> TmColors.Primary
            else -> TmColors.Warn
        }
    val minutes = ((now - incident.startTime) / SECONDS_PER_MINUTE).roundToInt()
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(1.dp, TmColors.Line, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(incident.kind.label, style = TmType.text(14.sp, FontWeight.Bold))
                BasicText(
                    tr("seit {minutes} min", "minutes" to minutes),
                    style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (role in incident.onScene) {
                    Badge(
                        tr("{unit} vor Ort", "unit" to role.label),
                        tone = BadgeTone.SUCCESS,
                    )
                }
                for (role in incident.onTheWay) {
                    Badge(
                        tr("{unit} unterwegs", "unit" to role.label),
                        tone = BadgeTone.WARN,
                    )
                }
            }
            // Sobald alle da sind: wie weit Bergung, Abschleppen oder Reinigung sind
            incident.workProgress?.let { Meter(it, Modifier.fillMaxWidth(), height = 5.dp, color = accent) }
        }
    }
}

private const val SLOT_WIDTH = 372
private const val MAX_ROWS = 12
private const val SECONDS_PER_MINUTE = 60.0
