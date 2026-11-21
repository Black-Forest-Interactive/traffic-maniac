package de.bfi.trafficmaniac.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highlight
import de.bfi.trafficmaniac.game.HighlightKind
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.NewspaperEdition
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.DamageKind
import de.bfi.trafficmaniac.sim.DayReport
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.Weekday
import de.bfi.trafficmaniac.ui.map.StreetRoute
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Screenshots von Menü und Spiel-HUD für Design-Reviews – kein echter Test, läuft nur mit gesetzter Umgebungsvariable:
 *
 * `TM_SCREENSHOTS=/pfad/zum/ordner ./gradlew :ui:jvmTest --tests '*Screenshots*' --rerun`
 *
 * Gerendert wird bei 1440 × 900 und 2000 × 1050 Pixeln mit Dichte 1 (1 dp = 1 px), Stadt: `cities/pforzheim`.
 * UI-Skalierung wie im Spiel automatisch; `TM_UI_SCALE=100` erzwingt eine feste Größe in Prozent.
 */
class Screenshots {
    private val target = System.getenv("TM_SCREENSHOTS")?.let(::File)

    @Test
    fun render() {
        val out = target ?: return
        out.mkdirs()
        val city = loadCity() ?: return
        val frame = frame(city)
        for ((width, height) in SIZES) {
            val suffix = "${width}x$height"
            shoot(File(out, "menu-$suffix.png"), width, height) {
                MainMenu(
                    Scenario.DEFAULTS,
                    Highscores(),
                    GameSettings(),
                    {},
                    {},
                    {},
                    attribution = city.attribution,
                    cities = CityChoice(listOf("Pforzheim"), 0, "Goldstadt an Enz und Nagold", nickname = "Goldstadt"),
                    cityMap = city,
                )
            }
            // Mitten im Spiel auf „Menü“: die Partie wartet, „Weiterspielen“ führt zurück
            shoot(File(out, "menu-paused-$suffix.png"), width, height) {
                MainMenu(
                    Scenario.DEFAULTS,
                    Highscores(),
                    GameSettings(),
                    {},
                    {},
                    {},
                    attribution = city.attribution,
                    cities = CityChoice(listOf("Pforzheim"), 0, "Goldstadt an Enz und Nagold", nickname = "Goldstadt"),
                    cityMap = city,
                    onContinue = {},
                )
            }
            gamePages(out, city, frame, suffix, width, height)
        }
        touch(out, city, frame)
        if (System.getenv("TM_SCREENSHOTS_DAY3") == "1") day3(out, city)
        dayReport(out)
        inspectCard(out, frame)
        toastsAndTransit(out, city, frame)
    }

    /** Spiel mit Karte (Kontrolle-Auswahl offen) und einigen Seiten der Leiste. */
    private fun gamePages(
        out: File,
        city: CityMap,
        frame: TrafficFrame,
        suffix: String,
        width: Int,
        height: Int,
    ) {
        for (panel in listOf(
            SidePanel.NONE,
            SidePanel.INBOX,
            SidePanel.TREASURY,
            SidePanel.PRESS,
            SidePanel.OFFICE,
            SidePanel.EMERGENCIES,
            SidePanel.TRANSIT,
        )) {
            val input =
                HudInput().apply {
                    panelRequest = panel
                    // Ohne Fenster: das Ampelprogramm-Werkzeug mit seiner Auswahl zeigen
                    listRequest = if (panel == SidePanel.NONE) Tool.CAMERA else Tool.ROUTE
                }
            shoot(File(out, "game-${panel.name.lowercase()}-$suffix.png"), width, height) {
                TrafficManiacApp(
                    city,
                    traffic = MutableStateFlow(frame),
                    input = input,
                    onSpeed = {},
                    onSave = {},
                    onMenu = {},
                    onFastForward = {},
                    onResumeSpeed = {},
                )
            }
        }
    }

    /** Einblendungen in allen drei Stufen und die Nahansicht am Marktplatz mit Haltestellen und Bussen. */
    private fun toastsAndTransit(
        out: File,
        city: CityMap,
        frame: TrafficFrame,
    ) {
        shoot(File(out, "toasts.png"), TOAST_SHOT_WIDTH, TOAST_SHOT_HEIGHT, settleMillis = TOAST_SETTLE_MILLIS) {
            HighlightToasts(
                listOf(
                    Highlight(1, HighlightKind.BIG, "Erfolg: Es blitzt", "Der erste Blitzer steht"),
                    Highlight(
                        2,
                        HighlightKind.MEDIUM,
                        "Blitzer Goethestraße hat sich bezahlt gemacht",
                        "45.120 € eingenommen – alles Weitere ist Gewinn",
                    ),
                    Highlight(3, HighlightKind.WARNING, "Zu wenig Geld für Blitzer (45.000 €)"),
                ),
                {},
                showExisting = true,
                onInbox = {},
            )
        }
        // Nah dran am Marktplatz: Haltestellen und Busse
        city.transitStops.firstOrNull { it.name == "Marktplatz" }?.let { stop ->
            shoot(File(out, "map-transit.png"), MAP_SHOT_WIDTH, MAP_SHOT_HEIGHT) {
                de.bfi.trafficmaniac.ui.map.MapView(
                    city,
                    traffic = { frame },
                    initialCamera =
                        de.bfi.trafficmaniac.ui.map
                            .Camera(stop.x, stop.y, MAP_SHOT_SCALE),
                )
            }
            // Dieselbe Stelle mit der Ebene „Buslinien“: Linien in Linienfarbe, Abschnitte nach Verspätung
            shoot(File(out, "map-transit-lines.png"), MAP_SHOT_WIDTH, MAP_SHOT_HEIGHT) {
                de.bfi.trafficmaniac.ui.map.MapView(
                    city,
                    traffic = { frame },
                    initialCamera =
                        de.bfi.trafficmaniac.ui.map
                            .Camera(stop.x, stop.y, MAP_SHOT_SCALE / 2),
                    extras =
                        de.bfi.trafficmaniac.ui.map
                            .MapExtras(transit = true),
                )
            }
        }
    }

    /** Touch-Oberfläche wie auf einem Handy im Querformat (Galaxy A25: 2340 × 1080 Pixel, Dichte 2,81). */
    private fun touch(
        out: File,
        city: CityMap,
        frame: TrafficFrame,
    ) {
        val cases =
            listOf(
                Triple("tutorial", SidePanel.NONE, null),
                Triple("camera", SidePanel.NONE, Tool.CAMERA),
                Triple("treasury", SidePanel.TREASURY, null),
                Triple("transit", SidePanel.TRANSIT, null),
                Triple("route", SidePanel.NONE, Tool.ROUTE),
            )
        for ((name, panel, list) in cases) {
            val input =
                HudInput().apply {
                    panelRequest = panel
                    listRequest = list
                }
            shoot(File(out, "touch-$name.png"), PHONE_WIDTH, PHONE_HEIGHT, PHONE_DENSITY) {
                TouchGameApp(
                    city,
                    MutableStateFlow(frame),
                    de.bfi.trafficmaniac.game
                        .GameRules(),
                    TouchCallbacks({}, {}, {}, onMenu = {}, onFastForward = {}, onResumeSpeed = {}),
                    tutorial = name == "tutorial",
                    input = input,
                )
            }
        }
    }

    /** Aktionskarte zu einem angeklickten Schlagloch (Schaden von Hand eingetragen). */
    private fun inspectCard(
        out: File,
        frame: TrafficFrame,
    ) {
        val report =
            DamageReport(
                id = 1,
                kind = DamageKind.POTHOLES,
                segment = 0,
                node = -1,
                place = "Goethestraße",
                reportedAt = frame.current.gameTime,
                hoursLeft = 31.0,
                status = DamageStatus.OPEN,
                conditionPercent = 52,
                repairCost = 25_000,
            )
        val game = frame.game.copy(maintenance = frame.game.maintenance.copy(reports = listOf(report)))
        val withDamage =
            TrafficFrame(frame.current, frame.current, TimeSource.Monotonic.markNow(), 1.seconds, 0.0, 1, 1, null, game)
        shoot(File(out, "inspect-card.png"), INSPECT_SHOT_WIDTH, INSPECT_SHOT_HEIGHT) {
            InspectCard(withDamage, Inspected.Damage(1), GameRules(), {}, {})
        }
    }

    /** Tagesabschluss mit Zeitung, Kasten „Aus der Praxis“ und Blitzer-Bilanz. */
    private fun dayReport(out: File) {
        val (report, edition) = sampleDay()
        shoot(File(out, "dayreport.png"), SIZES.first().first, SIZES.first().second) {
            TmTheme {
                DayReportOverlay(
                    report,
                    edition,
                    {},
                    cameraSummary = "82 % der Blitzer-Einnahmen stammen aus Verstößen bis 10 km/h.",
                    balance = DayBalance(38_400, 46_900, -5_595, 50 to 53, 9 to 14, 12 to 18, "bissig", 4),
                    actions = DayReportActions(onClose = {}, onTreasury = {}),
                )
            }
        }
    }

    /** Beispieltag für den Tagesabschluss: typischer Werktag und eine Ausgabe der Zeitung. */
    private fun sampleDay(): Pair<DayReport, NewspaperEdition> {
        val report =
            DayReport(
                day = 0,
                weekday = Weekday.MONDAY,
                // Typischer Werktag: Spitzen morgens und am Nachmittag, nachts fast nichts
                tripsStartedPerHour =
                    intArrayOf(
                        60,
                        30,
                        20,
                        20,
                        60,
                        400,
                        1900,
                        3600,
                        2700,
                        1700,
                        1500,
                        1600,
                        1900,
                        1800,
                        1700,
                        2200,
                        3000,
                        3300,
                        2400,
                        1600,
                        1100,
                        700,
                        400,
                        150,
                    ),
                tripsCompleted = 33_100,
                meanTripMinutes = 17.4,
                peakVehicles = 1_800,
                peakHour = 7,
                stuckRemovals = 120,
                vehicleKm = 312_000.0,
                vehicleHours = 9_800.0,
            )
        val edition =
            NewspaperEdition(
                day = 0,
                headlines =
                    listOf(
                        "Blitzer-Rekord in der Goethestraße",
                        "Rathaus verdient prächtig – Pendler stehen im Stau",
                        "Stau am Leopoldplatz",
                        "Tempo 30 kommt",
                    ),
                letters = listOf("„Ich fahre seit 40 Jahren unfallfrei zu schnell.“"),
            )
        return report to edition
    }

    private fun shoot(
        file: File,
        width: Int,
        height: Int,
        density: Float = 1f,
        /** Animationen bis zu diesem Zeitpunkt laufen lassen (Millisekunden Szenenzeit), z. B. für Einblendungen. */
        settleMillis: Long = 0,
        content: @Composable () -> Unit,
    ) {
        val scale = System.getenv("TM_UI_SCALE")?.toIntOrNull() ?: 0
        val scene = ImageComposeScene(width, height, Density(density)) { ScaledUi(scale) { content() } }
        try {
            // Ein paar Frames, damit Effekte (Fenster öffnen, Kartenbild im Hintergrund) ankommen
            var image = scene.render(0)
            for (i in 1..FRAMES) {
                Thread.sleep(FRAME_PAUSE_MILLIS)
                image = scene.render(i * FRAME_NANOS)
            }
            if (settleMillis > 0) {
                for (millis in FRAMES * FRAME_NANOS / NANOS_PER_MILLI..settleMillis step SETTLE_STEP_MILLIS) {
                    image = scene.render(millis * NANOS_PER_MILLI)
                }
            }
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("Screenshot: ${file.absolutePath}")
        } finally {
            scene.close()
        }
    }

    private fun loadCity(): CityMap? {
        var dir = File("").absoluteFile
        while (dir.parentFile != null && !File(dir, "cities").isDirectory) dir = dir.parentFile
        val file = File(dir, "cities/pforzheim/map.city")
        return if (file.isFile) CityMapCodec.decode(file.readBytes()) else null
    }

    /**
     * Spielstand nach zwei vollen Spieltagen (Tag 3): echte Zahlen in Kämmerei, Presse, Ordnungsamt und ÖPNV. Dauert
     * – deshalb nur mit `TM_SCREENSHOTS_DAY3=1`.
     */
    private fun day3(
        out: File,
        city: CityMap,
    ) {
        val frame = frame(city, days = 2)
        val (width, height) = DAY3_WIDTH to DAY3_HEIGHT
        for (panel in DAY3_PAGES) {
            dayPage(
                File(out, "game-${panel.name.lowercase()}-day3-${width}x$height.png"),
                city,
                frame,
                panel,
            )
        }
        // Bauamt: Sammelaktion (12 ausgewählt, Statistik bis unten) und ein geöffneter Schaden
        val maintenance = SidePanel.MAINTENANCE
        val selected = MaintenancePreset(select = SELECTED_DAMAGES, statsToEnd = true)
        dayPage(File(out, "game-maintenance-selected-day3-${width}x$height.png"), city, frame, maintenance, selected)
        val end = MaintenancePreset(select = SELECTED_DAMAGES, tableToEnd = true)
        dayPage(File(out, "game-maintenance-selected-end-day3-${width}x$height.png"), city, frame, maintenance, end)
        dayPage(
            File(out, "game-maintenance-detail-day3-${width}x$height.png"),
            city,
            frame,
            maintenance,
            MaintenancePreset(open = true),
        )
    }

    private fun dayPage(
        file: File,
        city: CityMap,
        frame: TrafficFrame,
        panel: SidePanel,
        preset: MaintenancePreset = MaintenancePreset(),
    ) {
        val input = HudInput().apply { panelRequest = panel }
        shoot(file, DAY3_WIDTH, DAY3_HEIGHT) {
            CompositionLocalProvider(LocalMaintenancePreset provides preset) {
                TrafficManiacApp(
                    city,
                    traffic = MutableStateFlow(frame),
                    input = input,
                    onSpeed = {},
                    onSave = {},
                    onMenu = {},
                    onFastForward = {},
                    onResumeSpeed = {},
                )
            }
        }
    }

    /** ÖPNV am Marktplatz: Busspur mit Blitzer und Busvorrang an der nächsten Ampel. */
    private fun submitTransit(
        game: CityGame,
        city: CityMap,
    ) {
        // ÖPNV am Marktplatz: Busspur mit Blitzer und Busvorrang an der nächsten Ampel
        city.transitStops.firstOrNull { it.name == "Marktplatz" }?.let { stop ->
            val graph = game.simulation.graph
            val near =
                city.roads.segments
                    .filter { !it.passive && graph.segmentLinks[it.id].any { link -> graph.linkLanes[link] >= 2 } }
                    .sortedBy { kotlin.math.hypot(it.geometry.x(0) - stop.x, it.geometry.y(0) - stop.y) }
                    .take(BUS_LANE_SEGMENTS)
                    .map { it.id }
            game.submit(GameCommand.SetBusLane(near, on = true))
            near.firstOrNull()?.let { game.submit(GameCommand.PlaceBusLaneCamera(it)) }
            city.roads.nodes
                .filter { it.trafficSignals }
                .minByOrNull { kotlin.math.hypot(it.x - stop.x, it.y - stop.y) }
                ?.let { game.submit(GameCommand.SetBusPriority(it.id, on = true)) }
        }
    }

    private fun frame(
        city: CityMap,
        /** So viele volle Spieltage weiterrechnen (0 = nur Vorlauf). */
        days: Int = 0,
    ): TrafficFrame {
        val game = CityGame(city)
        // Ein paar typische Meldungen: dreimal Tempo 30 (gebündelt), ein Blitzer, eine Einstellung im Amt
        val main =
            city.roads.segments
                .filter { !it.passive && it.name != null }
                .map { it.id }
        main.take(3).forEach { game.submit(GameCommand.SetSpeedLimit(it, 30)) }
        game.submit(GameCommand.PlaceCamera(main[3]))
        // Anzeigetafel auf einer der Tempo-30-Strecken, Rotlichtblitzer an der ersten Ampelzufahrt
        game.submit(GameCommand.PlaceDisplay(main[1]))
        // Fahrradstraße mit Polizeikontrolle auf einer Wohnstraße mit Namen
        city.roads.segments
            .firstOrNull {
                !it.passive && it.name != null && it.roadClass.osmValue == "secondary"
            }?.let {
                game.submit(GameCommand.SetBikeStreet(listOf(it.id), on = true))
                game.submit(GameCommand.PlacePoliceCheck(it.id))
            }
        game.simulation.signals.controlledLinks.firstOrNull()?.let {
            game.submit(GameCommand.PlaceRedLightCamera(it, withSpeed = true))
        }
        submitTransit(game, city)
        game.submit(GameCommand.SetDeadlineDays(5))
        // Grüne Welle über die Karlsruher Straße (getrennte Richtungsfahrbahnen, mehrere Ampeln)
        val street = city.roads.segments.filter { !it.passive && it.name == "Karlsruher Straße" }
        if (street.size > 1) {
            StreetRoute(city).between(street.first().id, street.last().id, directed = true)?.let {
                game.submit(GameCommand.SetGreenWave(it, 50))
            }
        }
        repeat(WARMUP_TICKS) { game.step() }
        while (game.simulation.dayReports.size < days) game.step()
        val snapshot = game.simulation.snapshot()
        return TrafficFrame(
            snapshot,
            snapshot,
            TimeSource.Monotonic.markNow(),
            1.seconds,
            0.0,
            1,
            1,
            null,
            game.state(),
        )
    }

    private companion object {
        val SIZES = listOf(1440 to 900, 1920 to 1080, 2000 to 1050)

        const val DAY3_WIDTH = 1920
        const val DAY3_HEIGHT = 1080
        const val SELECTED_DAMAGES = 12

        /** Seiten mit dem Spielstand nach zwei Spieltagen. */
        val DAY3_PAGES =
            listOf(
                SidePanel.TREASURY,
                SidePanel.PRESS,
                SidePanel.OFFICE,
                SidePanel.TRANSIT,
                SidePanel.EMERGENCIES,
                SidePanel.INBOX,
                SidePanel.MAINTENANCE,
                SidePanel.PARKING,
                SidePanel.CITIZENS,
                SidePanel.EVENTS,
                SidePanel.ACHIEVEMENTS,
            )
        const val TOAST_SHOT_WIDTH = 600
        const val INSPECT_SHOT_WIDTH = 480
        const val INSPECT_SHOT_HEIGHT = 560
        const val TOAST_SHOT_HEIGHT = 300
        const val TOAST_SETTLE_MILLIS = 900L
        const val SETTLE_STEP_MILLIS = 16L
        const val NANOS_PER_MILLI = 1_000_000L
        const val FRAMES = 8
        const val FRAME_PAUSE_MILLIS = 150L
        const val FRAME_NANOS = 16_000_000L
        const val WARMUP_TICKS = 6_000
        const val BUS_LANE_SEGMENTS = 6
        const val MAP_SHOT_WIDTH = 1200
        const val MAP_SHOT_HEIGHT = 800
        const val MAP_SHOT_SCALE = 2.5f
        const val PHONE_WIDTH = 2340
        const val PHONE_HEIGHT = 1080
        const val PHONE_DENSITY = 2.8125f
    }
}
