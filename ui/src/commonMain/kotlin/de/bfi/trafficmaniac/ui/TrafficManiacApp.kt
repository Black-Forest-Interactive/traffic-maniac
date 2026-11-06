package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameMessage
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.MapView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

private val BackgroundColor = Color(0xFF2B2D42)
private val HUD_REFRESH = 250.milliseconds
private val MessageBackground = Color(0xCC1D1F2E)
private val MessageText = Color(0xFFF1F1F1)
private const val PICK_RADIUS_PIXELS = 14f
private const val MIN_PICK_RADIUS_METERS = 6f
private const val VISIBLE_MESSAGES = 3

/** Wurzel der Spieloberfläche. Ohne Stadt wird ein Hinweis angezeigt, wie man sie erzeugt. */
@Composable
fun TrafficManiacApp(
    city: CityMap?,
    missingCityMessage: String = "",
    traffic: StateFlow<TrafficFrame?>? = null,
    rules: GameRules = GameRules(),
    onCommand: (GameCommand) -> Unit = {},
    onContinueDay: () -> Unit = {},
) {
    if (city == null) {
        MissingCity(missingCityMessage)
        return
    }
    var fps by remember { mutableStateOf(0) }
    var selection by remember { mutableStateOf(ToolSelection()) }
    var panel by remember { mutableStateOf(SidePanel.NONE) }
    var showMoods by remember { mutableStateOf(false) }
    val picker = remember(city) { MapPicker(city) }
    // Die Kennzahlen reichen viermal pro Sekunde – die Karte liest den Verkehr selbst in jedem Frame
    val hudFrame by produceState<TrafficFrame?>(null, traffic) {
        while (traffic != null) {
            value = traffic.value
            delay(HUD_REFRESH)
        }
    }
    Box(Modifier.fillMaxSize()) {
        MapView(
            city = city,
            traffic = traffic?.let { flow -> { flow.value } },
            onFps = { fps = it },
            showMoods = showMoods,
            onMapClick = { x, y, metersPerPixel ->
                val radius = max(MIN_PICK_RADIUS_METERS, PICK_RADIUS_PIXELS * metersPerPixel)
                commandFor(selection, picker, traffic?.value, x, y, radius)?.let(onCommand)
            },
        )
        hudFrame?.let { frame ->
            TrafficHud(frame, fps, Modifier.align(Alignment.TopStart))
            SidePanels(
                frame = frame,
                rules = rules,
                panel = panel,
                onPanel = { panel = if (panel == it) SidePanel.NONE else it },
                showMoods = showMoods,
                onToggleMoods = { showMoods = !showMoods },
                onCommand = onCommand,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Messages(frame.game.messages, Modifier.align(Alignment.BottomStart).padding(bottom = 24.dp))
        }
        if (traffic != null) ToolBar(selection, rules, { selection = it }, Modifier.align(Alignment.BottomCenter))
        hudFrame?.let { frame ->
            val edition = frame.game.latestEdition
            val report = frame.pendingReport
            when {
                frame.game.isGameOver || frame.game.isTermOver -> ResultOverlay(frame.game)
                report != null -> DayReportOverlay(report, edition?.takeIf { it.day == report.day }, onContinueDay)
            }
        }
    }
}

/** Einblendbare Fenster rechts oben. */
private enum class SidePanel(
    val label: String,
) {
    NONE(""),
    CAMERAS("Blitzer-Statistik"),
    BUDGET("Haushalt & PR"),
}

@Composable
private fun SidePanels(
    frame: TrafficFrame,
    rules: GameRules,
    panel: SidePanel,
    onPanel: (SidePanel) -> Unit,
    showMoods: Boolean,
    onToggleMoods: () -> Unit,
    onCommand: (GameCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row {
            SmallButton(if (showMoods) "Zufriedenheit ausblenden" else "Zufriedenheit", onToggleMoods)
            for (option in SidePanel.entries.drop(1)) SmallButton(option.label) { onPanel(option) }
        }
        when (panel) {
            SidePanel.CAMERAS -> {
                CameraStatsPanel(frame.game.cameras)
            }

            SidePanel.BUDGET -> {
                BudgetPanel(
                    frame.game,
                    rules,
                    onContract = { onCommand(GameCommand.SetPrContract(it)) },
                    onArguments = { onCommand(GameCommand.SetArgumentsCampaign(it)) },
                )
            }

            SidePanel.NONE -> {}
        }
    }
}

/** Übersetzt einen Klick auf die Karte mit dem gewählten Werkzeug in einen Spielbefehl. */
private fun commandFor(
    selection: ToolSelection,
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? {
    val signalNodes = frame?.game?.signalNodes.orEmpty()
    return when (selection.tool) {
        Tool.LOOK -> {
            null
        }

        Tool.SPEED_LIMIT -> {
            picker.nearestSegment(x, y, radius)?.let { GameCommand.SetSpeedLimit(it, selection.speedLimit) }
        }

        Tool.SIGNAL -> {
            picker.nearestNode(x, y, radius)?.let { GameCommand.BuildSignal(it) }
        }

        Tool.CAMERA -> {
            picker.nearestSegment(x, y, radius)?.let { GameCommand.PlaceCamera(it) }
        }

        Tool.REMOVE -> {
            val camera =
                frame?.current?.cameras?.minByOrNull { hypot(it.x - x, it.y - y) }?.takeIf {
                    hypot(
                        it.x - x,
                        it.y - y,
                    ) <=
                        radius
                }
            val segment = picker.nearestSegment(x, y, radius)?.takeIf { it in frame?.game?.speedLimits.orEmpty() }
            val node = picker.nearestNode(x, y, radius)?.takeIf { it in signalNodes }
            when {
                camera != null -> GameCommand.RemoveCamera(camera.id)
                segment != null -> GameCommand.SetSpeedLimit(segment, null)
                node != null -> GameCommand.RemoveSignal(node)
                else -> null
            }
        }
    }
}

@Composable
private fun Messages(
    messages: List<GameMessage>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (message in messages.takeLast(VISIBLE_MESSAGES)) {
            BasicText(
                "${message.time.clockText}  ${message.text}",
                style = TextStyle(color = MessageText, fontSize = 13.sp),
                modifier =
                    Modifier
                        .background(MessageBackground, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SmallButton(
    label: String,
    onClick: () -> Unit,
) {
    BasicText(
        label,
        style = TextStyle(color = MessageText, fontSize = 13.sp),
        modifier =
            Modifier
                .padding(top = 12.dp, end = 12.dp)
                .background(MessageBackground, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun MissingCity(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = message,
            style = TextStyle(color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center),
        )
    }
}
