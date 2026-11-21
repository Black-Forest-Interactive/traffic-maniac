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
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.IncidentCounts
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.LocalPage
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.map.EnvironmentLayer
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlin.math.roundToInt

/** Seiten der linken Leiste (am Handy: Fenster rechts neben der Karte); [NONE] = Karte. */
internal enum class SidePanel(
    private val labelText: String,
) {
    NONE(""),
    INBOX(translatable("Posteingang")),
    TREASURY(translatable("Kämmerei")),
    PRESS(translatable("Presse & PR")),
    CAMERAS(translatable("Blitzer-Statistik")),
    BUDGET(translatable("Haushalt & PR")),
    OFFICE(translatable("Ordnungsamt")),
    MAINTENANCE(translatable("Bauamt")),
    PARKING(translatable("Parken")),
    CITIZENS(translatable("Bürger")),
    EVENTS(translatable("Kalender")),
    EMERGENCIES(translatable("Einsätze")),
    TRANSIT(translatable("ÖPNV")),
    ACHIEVEMENTS(translatable("Erfolge")),
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
    /** Zur Karte mit dieser Umwelt-Ebene (Lärm, Luft …). */
    val onEnvironment: ((EnvironmentLayer) -> Unit)? = null,
    /** Eine andere Seite öffnen (z. B. aus „Warum sauer?“ ins zuständige Amt). */
    val onPanel: ((SidePanel) -> Unit)? = null,
)

@Composable
private fun MaintenanceSlot(
    frame: TrafficFrame,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
    onClose: () -> Unit,
) {
    val game = frame.game
    val showReport = { report: DamageReport ->
        // Ampelmeldungen haben keinen Abschnitt – dann zur Ampel selbst
        val place = if (report.node >= 0) picker.placeOfNode(report.node) else picker.place(report.segments)
        place?.let(onShow)
        Unit
    }
    LocalPage.current?.let { page ->
        MaintenancePage(MaintenancePageData(game, frame.current, picker.city, rules, onCommand, showReport), page)
        return
    }
    MaintenancePanel(
        game.maintenance,
        rules,
        onCommand,
        onClose = onClose,
        onShow = showReport,
        ploughs = game.ploughs.takeIf { game.winterService },
    )
}

@Composable
internal fun PanelContent(
    panel: SidePanel,
    frame: TrafficFrame,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
    sprites: SpriteSource,
    onClose: () -> Unit,
    onPanel: ((SidePanel) -> Unit)? = null,
    onEnvironment: ((EnvironmentLayer) -> Unit)? = null,
) {
    val game = frame.game
    when (panel) {
        SidePanel.CAMERAS -> {
            CameraStatsPanel(game.cameras, onClose = onClose)
        }

        // Bis Kämmerei und Presse eigene Seiten haben (Entwurf 2, Schritte 5 und 7): das Haushaltsfenster
        SidePanel.BUDGET, SidePanel.TREASURY, SidePanel.PRESS -> {
            MoneySlot(panel, game, rules, onCommand, onClose, frame.current.gameTime) { onShow(picker.place(it)) }
        }

        SidePanel.OFFICE -> {
            OfficeSlot(game, rules, onCommand, onClose)
        }

        SidePanel.MAINTENANCE -> {
            MaintenanceSlot(frame, rules, onCommand, picker, onShow, onClose)
        }

        SidePanel.PARKING -> {
            ParkingPanel(
                game.parkingOffice,
                frame.current.parking,
                frame.current.parkingLots,
                rules,
                onCommand,
                onClose = onClose,
                nowHour = frame.current.gameTime.absoluteHour,
                firstDay = game.firstDay,
            )
        }

        SidePanel.CITIZENS -> {
            CitizensSlot(frame, rules, picker, onShow, onClose, onPanel, onEnvironment)
        }

        SidePanel.EVENTS -> {
            EventsSlot(frame, rules, picker, onShow, onClose)
        }

        SidePanel.EMERGENCIES -> {
            EmergencyPanel(
                frame.current,
                picker,
                onShow,
                onClose,
                game.incidentCounts,
            )
        }

        SidePanel.TRANSIT -> {
            TransitSlot(frame, picker, onClose)
        }

        SidePanel.ACHIEVEMENTS -> {
            AchievementsPanel(game.achievementList, firstDay = game.firstDay, onClose = onClose)
        }

        SidePanel.HELP -> {
            HelpPanel(sprites = sprites, onClose = onClose)
        }

        // Der Posteingang braucht die Auftrags-Aktionen der Oberfläche – siehe HudPage
        SidePanel.NONE, SidePanel.INBOX -> {}
    }
}

/**
 * Laufende Einsätze: Unfälle zuerst, dann die neuesten. Jeder Einsatz mit farbiger Seitenleiste (Unfall rot, Panne
 * orange), Dauer und Einsatzkräften als Etiketten (vor Ort grün, unterwegs gelb) – ein Klick fliegt hin.
 */
@Composable
private fun EmergencyPanel(
    snapshot: SimulationSnapshot,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
    onClose: () -> Unit,
    /** Einsätze heute und in der Amtszeit je Art. */
    counts: IncidentCounts = IncidentCounts(),
) {
    val incidents = snapshot.incidents
    val now = snapshot.time
    LocalPage.current?.let { page ->
        EmergencyPage(EmergencyPageData(snapshot, picker, onShow, counts), page)
        return
    }
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
        PanelSection(title = tr("Wachen & Fahrzeuge"), divider = false) { Stations(incidents, picker.city) }
        PanelSection(divider = false) { ServiceFleet(snapshot, picker.city, picker, onShow) }
        PanelSection(title = tr("Amtszeit"), divider = false) { TermFigures(counts, incidents.size) }
        if (counts.term.isNotEmpty()) {
            PanelSection(title = tr("Häufigkeit nach Art"), divider = false) { KindCounts(counts.today, counts.term) }
        }
    }
}

@Composable
internal fun IncidentCard(
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

private const val MAX_ROWS = 12
private const val SECONDS_PER_MINUTE = 60.0

/** Haushalt: PR-Vertrag, Kampagne und Spatenstich werden zu Befehlen. */
@Composable
private fun BudgetSlot(
    game: GameState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    onClose: () -> Unit,
) {
    BudgetPanel(
        game,
        rules,
        onContract = { onCommand(GameCommand.SetPrContract(it)) },
        onArguments = { onCommand(GameCommand.SetArgumentsCampaign(it)) },
        onCeremony = { onCommand(GameCommand.HoldCeremony) },
        onClose = onClose,
    )
}

/** Je Art: heute und in der Amtszeit, mit Balken relativ zur häufigsten Art. */
@Composable
internal fun KindCounts(
    today: Map<IncidentKind, Int>,
    term: Map<IncidentKind, Int>,
) {
    val max = term.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth()) {
        BasicText(tr("Art"), style = TmType.caption, modifier = Modifier.weight(1f))
        BasicText(tr("heute · Amtszeit"), style = TmType.caption)
    }
    for ((kind, count) in term.entries.sortedByDescending { it.value }) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth()) {
                BasicText(kind.label, style = TmType.text(13.sp), modifier = Modifier.weight(1f))
                BasicText("${today[kind] ?: 0} · $count", style = TmType.mono(13.sp))
            }
            Meter(count.toFloat() / max, Modifier.fillMaxWidth(), height = 5.dp, color = TmColors.Primary)
        }
    }
}

/** ÖPNV: als Seite mit Netzkarte, sonst das Fenster. */
@Composable
private fun TransitSlot(
    frame: TrafficFrame,
    picker: MapPicker,
    onClose: () -> Unit,
) {
    val page = LocalPage.current
    if (page != null) {
        TransitPage(frame, picker.city, page)
    } else {
        TransitPanel(frame.current.transit, frame.game.busModeShift, onClose = onClose)
    }
}

/** Ordnungsamt; als Seite gehört die Blitzer-Statistik dazu. */
@Composable
private fun OfficeSlot(
    game: GameState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    onClose: () -> Unit,
) {
    OfficePanel(
        game.fineOffice,
        rules,
        game.bannedDrivers,
        onCommand,
        onClose = onClose,
        habits = HabitsState(game.warnerShare, game.marathonActive, game.campaignInDays),
        cameras = game.cameras.takeIf { LocalPage.current != null },
    )
}

/** Haushaltsfenster (Handy-Altlast), Kämmerei oder Presse & PR. */
@Composable
private fun MoneySlot(
    panel: SidePanel,
    game: GameState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    onClose: () -> Unit,
    now: GameTime? = null,
    onPlace: ((Int) -> Unit)? = null,
) {
    when (panel) {
        SidePanel.TREASURY -> TreasuryPanel(game, rules, onClose, now, onPlace)
        SidePanel.PRESS -> PressPage(game, rules, onCommand, onClose)
        else -> BudgetSlot(game, rules, onCommand, onClose)
    }
}

/** Kalender: als Seite mit Wochenplan, sonst das Fenster. */
@Composable
private fun EventsSlot(
    frame: TrafficFrame,
    rules: GameRules,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
    onClose: () -> Unit,
) {
    val game = frame.game
    val time = frame.current.gameTime
    val show = { segments: Set<Int> -> picker.place(segments)?.let(onShow) ?: Unit }
    LocalPage.current?.let { page ->
        EventsPage(game, time.day, time.hour, rules, page, show)
        return
    }
    EventsPanel(game.events, time.day, firstDay = game.firstDay, onClose = onClose, onShow = show)
}

@Composable
private fun CitizensSlot(
    frame: TrafficFrame,
    rules: GameRules,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
    onClose: () -> Unit,
    onPanel: ((SidePanel) -> Unit)?,
    onEnvironment: ((EnvironmentLayer) -> Unit)?,
) {
    CitizensPanel(
        frame.game,
        angerThreshold = rules.angerThreshold,
        onShow = onShow,
        onClose = onClose,
        picker = picker,
        onPanel = onPanel,
        onEnvironment = onEnvironment,
        nowHour = frame.current.gameTime.absoluteHour,
    )
}
