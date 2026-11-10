package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.ui.map.MapPicker
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlinx.coroutines.flow.StateFlow

/** Was die Touch-Oberfläche zusätzlich zum HUD weiß: offenes Menü der Leiste, Lage und Kartenansichten. */
private class TouchState {
    var menu by mutableStateOf<RailMenu?>(null)
    var feed by mutableStateOf(false)
    var views by mutableStateOf(false)
}

/** Wohin die Touch-Oberfläche meldet: Befehle, Tagesabschluss, Tempo, Menü und Speichern. */
class TouchCallbacks(
    val onCommand: (GameCommand) -> Unit,
    val onContinueDay: () -> Unit,
    /** Zeitraffer wählen (0 = Pause). */
    val onSpeed: (Int) -> Unit,
    /** Zurück ins Startmenü; `null` = kein Menü. */
    val onMenu: (() -> Unit)? = null,
    /** Spiel speichern; `null` = kein Speichern. */
    val onSave: (() -> Unit)? = null,
)

/**
 * Spieloberfläche für den Finger (Handy und Tablet im Querformat): gleiche Karte, gleiche Fenster und gleiche
 * Werkzeuge wie am Desktop, aber schmale Kopfleiste, Leiste mit Menüs statt elf Knöpfen, keine Tastenkürzel und
 * Tooltips, zweispaltige Werkzeuglisten und ein größerer Fangradius für Tipps auf die Karte.
 */
@Composable
fun TouchGameApp(
    city: CityMap,
    traffic: StateFlow<TrafficFrame?>,
    rules: GameRules,
    callbacks: TouchCallbacks,
    sound: SoundBoard = NoSound,
    sprites: SpriteSource = NoSprites,
    tutorial: Boolean = false,
    /** Anfragen von außen (Tastatur am Tablet, Tests): Werkzeug, Liste, Fenster. */
    input: HudInput = remember { HudInput() },
) {
    val hud = remember(tutorial) { HudState(tutorial) }
    InputRequests(input, hud)
    val touch = remember { TouchState() }
    val picker = remember(city) { MapPicker(city) }
    val command: (GameCommand) -> Unit = {
        sound.play(SoundEffect.CLICK)
        callbacks.onCommand(it)
    }
    val hudFrame by rememberHudFrame(traffic, sound, hud.zoomLevel)
    CompositionLocalProvider(LocalTouch provides true) {
        TmTheme {
            Box(Modifier.fillMaxSize().background(TmColors.Ground)) {
                Column(Modifier.fillMaxSize()) {
                    hudFrame?.let {
                        TouchTopBar(it, TopBarActions(callbacks.onSpeed, callbacks.onSave, callbacks.onMenu))
                    }
                    Row(Modifier.weight(1f)) {
                        TouchRail(
                            RailState(
                                hud.panel,
                                touch.menu,
                                touch.feed,
                                touch.views,
                                hudFrame?.current?.incidents?.size ?: 0,
                            ),
                            railActions(hud, touch),
                        )
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            MapLayer(city, traffic, hud, picker, rules, sprites, null, touchPickRadius())
                            hudFrame?.let { TouchOverlays(it, hud, touch, rules, command, sprites) }
                        }
                        // Fenster rechts neben der Karte (nicht darüber): die Karte bleibt ganz sichtbar
                        hudFrame?.takeIf { hud.panel != SidePanel.NONE }?.let { frame ->
                            val context =
                                PanelContext(frame, rules, command, picker, hud::show, sprites) {
                                    hud.panel = SidePanel.NONE
                                }
                            SidePanelSlot(hud.panel, context)
                        }
                    }
                }
                hudFrame?.let { frame ->
                    FitToDesign { EndOfDayOverlays(frame, callbacks.onContinueDay, callbacks.onMenu, sprites) }
                }
            }
        }
    }
}

private fun railActions(
    hud: HudState,
    touch: TouchState,
) = RailActions(
    onMenu = { menu -> touch.menu = if (touch.menu == menu) null else menu },
    onPanel = { panel ->
        touch.menu = null
        hud.togglePanel(panel)
    },
    onFeed = { touch.feed = !touch.feed },
    onViews = { touch.views = !touch.views },
)

/** Fangradius für Tipps: etwa eine Fingerkuppe (in Bildpunkten der Karte). */
@Composable
private fun touchPickRadius(): Float = with(LocalDensity.current) { TOUCH_PICK_RADIUS.dp.toPx() }

/** Alles über der Karte: Tutorial, Lage, Ansichten, Geldregen, Werkzeug-Dock und das aufgeklappte Menü. */
@Composable
private fun BoxScope.TouchOverlays(
    frame: TrafficFrame,
    hud: HudState,
    touch: TouchState,
    rules: GameRules,
    command: (GameCommand) -> Unit,
    sprites: SpriteSource,
) {
    val game = frame.game
    val income = game.fineIncome + game.parkingOffice.feeIncome + game.parkingOffice.ticketIncome
    MoneyRain(income, Modifier.align(Alignment.TopCenter).size(MONEY_RAIN_SIZE.dp), sprites)
    hud.step?.let { s ->
        TutorialPanel(
            s,
            game,
            { hud.step = it },
            Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = TUTORIAL_WIDTH.dp),
        )
    }
    if (touch.views) MapViewSwitcher(hud.toggles, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    if (touch.feed && hud.panel == SidePanel.NONE) {
        HudFeed(frame, hud::show, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
    val dock =
        ToolDockState(
            hud.selection,
            hud.availableTools,
            game.idleTrailers,
            hud.openGroup,
            hud::tile,
            hud::pick,
            planBar(hud.plan, game.balance, command),
        )
    // Mit offenem Fenster ist die Karte zu schmal fürs Dock – erst das Fenster schließen
    if (hud.panel ==
        SidePanel.NONE
    ) {
        ToolDock(dock, rules, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
    touch.menu?.let { menu ->
        // Tipp daneben schließt das Menü
        Box(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { touch.menu = null },
        )
        RailMenuCard(
            menu,
            hud.panel,
            { panel ->
                touch.menu = null
                hud.panel = panel
            },
            Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}

private const val TOUCH_PICK_RADIUS = 18
private const val MONEY_RAIN_SIZE = 240
private const val TUTORIAL_WIDTH = 340
