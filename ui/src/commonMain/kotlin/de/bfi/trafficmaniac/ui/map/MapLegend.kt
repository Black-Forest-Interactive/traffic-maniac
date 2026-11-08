package de.bfi.trafficmaniac.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.ChargingStationState
import de.bfi.trafficmaniac.sim.DamageKind
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.FLAG_BRAKING
import de.bfi.trafficmaniac.sim.FLAG_DISABLED
import de.bfi.trafficmaniac.sim.FLAG_ELECTRIC
import de.bfi.trafficmaniac.sim.FLAG_EMERGENCY
import de.bfi.trafficmaniac.sim.FLAG_JAMMED
import de.bfi.trafficmaniac.sim.GameTime
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.sim.ParkingLotState
import de.bfi.trafficmaniac.sim.SignalMarkers
import de.bfi.trafficmaniac.sim.SignalState
import de.bfi.trafficmaniac.sim.SpeedCamera
import de.bfi.trafficmaniac.sim.VehicleRole
import de.bfi.trafficmaniac.sim.VehicleType

/** Ein Eintrag der Legende: Vorschau (in Weltkoordinaten um den Nullpunkt, [scale] Pixel je Meter) und Erklärung. */
private class LegendEntry(
    val text: String,
    val scale: Float,
    val draw: DrawScope.(scale: Float, measurer: TextMeasurer, sprites: SpriteSource) -> Unit,
)

/**
 * Legende aller Kartensymbole. Die Vorschauen benutzen dieselben Zeichenfunktionen wie die Karte –
 * so stimmt die Legende auch dann noch, wenn sich der Stil ändert.
 */
@Composable
fun MapLegend(
    textColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier,
    /** Fertige Grafiken – die Legende zeigt dieselben Symbole wie die Karte. */
    sprites: SpriteSource = NoSprites,
) {
    val measurer = rememberTextMeasurer()
    val heading = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val text = TextStyle(color = textColor, fontSize = 11.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((title, entries) in SECTIONS) {
            BasicText(tr(title), style = heading)
            for (entry in entries) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Canvas(Modifier.size(PREVIEW_WIDTH.dp, PREVIEW_HEIGHT.dp)) {
                        inWorld(Camera(0f, 0f, entry.scale)) { entry.draw(this, entry.scale, measurer, sprites) }
                    }
                    BasicText(tr(entry.text), style = text)
                }
            }
        }
        BasicText(
            tr("Tipp: Meldungen mit ▸ anklicken – die Karte zeigt die Stelle."),
            style = text.copy(color = mutedColor),
        )
    }
}

private const val PREVIEW_WIDTH = 90
private const val PREVIEW_HEIGHT = 44

/** Die Vorschau zeigt den Zustand "eingeschaltet" (Blinker an, Animation mittendrin). */
private const val SHOWCASE_SECONDS = 0.1

private val WIDE = Rect(-1_000f, -1_000f, 1_000f, 1_000f)
private val CAR_RED = Color(0xFFE63946)

private fun vehicle(
    text: String,
    type: VehicleType,
    role: VehicleRole = VehicleRole.PRIVATE,
    color: Color = CAR_RED,
    flags: Int = 0,
    trouble: IncidentKind? = null,
    scale: Float = 7f,
) = LegendEntry(
    text,
    scale,
) { s, _, sp -> drawVehicleSprite(type, role, color, flags, trouble, s, SHOWCASE_SECONDS, sp) }

private fun signal(
    text: String,
    state: SignalState,
) = LegendEntry(text, SIGNAL_SCALE) { s, _, sp ->
    drawSignals(SignalMarkers(IntArray(1), FloatArray(1), FloatArray(1)), arrayOf(state), s, true, WIDE, sp)
}

private fun damage(
    text: String,
    status: DamageStatus,
    percent: Int,
) = LegendEntry(text, DAMAGE_SCALE) { s, _, sp ->
    val report = DamageReport(0, DamageKind.POTHOLES, 0, -1, "", GameTime(0, 0.0), 1.0, status, percent)
    drawDamageAt(report, 0f to 0f, DAMAGE_SIZE, sp, s)
}

private fun line(
    text: String,
    color: Color,
    width: Float,
    dashed: Boolean = false,
) = LegendEntry(text, LINE_SCALE) { s, _, sp ->
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(width * DASH, width * DASH)) else null
    drawLine(color, Offset(-LINE_HALF, 0f), Offset(LINE_HALF, 0f), width, pathEffect = effect)
}

private fun incident(
    text: String,
    kind: IncidentKind,
) = LegendEntry(text, INCIDENT_SCALE) { s, _, sp -> drawIncidents(listOf(IncidentMarker(kind, 0f, 0f)), s, sp) }

private const val DAMAGE_SIZE = 4f
private const val ICON_SCALE = 1.5f
private const val ICON_METERS = 20f
private const val COLLAPSED_LINE = 12f
private const val DETOUR_LINE = 13f

/** Ereignis-Marker wie auf der Karte. */
private fun event(
    text: String,
    kind: EventKind,
) = LegendEntry(text, ICON_SCALE) { s, _, sp -> drawEventMarker(kind, Offset.Zero, s, sp) }

/** Grafik [name], sonst die gezeichnete Ersatzdarstellung [fallback]. */
private fun icon(
    text: String,
    name: String,
    wide: Boolean = false,
    fallback: DrawScope.() -> Unit,
) = LegendEntry(text, ICON_SCALE) { s, _, sp ->
    val width = if (wide) ICON_METERS * 2 else ICON_METERS
    if (!drawSprite(sp, name, Offset.Zero, width, ICON_METERS, s)) fallback()
}

private const val SIGNAL_SCALE = 2.2f
private const val DAMAGE_SCALE = 3f
private const val LINE_SCALE = 1.2f
private const val INCIDENT_SCALE = 2f
private const val DASH = 1.5f
private const val LINE_HALF = 22f

private val SECTIONS: List<Pair<String, List<LegendEntry>>> =
    listOf(
        translatable("Fahrzeuge") to
            listOf(
                vehicle(translatable("PKW"), VehicleType.CAR),
                vehicle(
                    translatable("Elektroauto (grüner Punkt)"),
                    VehicleType.CAR,
                    color = Color(0xFF2A9D8F),
                    flags = FLAG_ELECTRIC,
                ),
                vehicle(
                    translatable("Lieferwagen – hält gern in zweiter Reihe"),
                    VehicleType.VAN,
                    color = Color(0xFFE9ECEF),
                ),
                vehicle(
                    translatable("LKW – verschleißt die Straße wie Tausende PKW"),
                    VehicleType.TRUCK,
                    color = Color(0xFF5C6B7A),
                    scale = 5.5f,
                ),
                vehicle(
                    translatable("Rettungswagen (Blaulicht: Vorfahrt, Rettungsgasse)"),
                    VehicleType.CAR,
                    VehicleRole.AMBULANCE,
                    flags = FLAG_EMERGENCY,
                ),
                vehicle(translatable("Polizei"), VehicleType.CAR, VehicleRole.POLICE, flags = FLAG_EMERGENCY),
                vehicle(
                    translatable("Feuerwehr – bei Unfällen und eingeklemmten LKW"),
                    VehicleType.TRUCK,
                    VehicleRole.FIRE_BRIGADE,
                    flags = FLAG_EMERGENCY,
                    scale = 5.5f,
                ),
                vehicle(translatable("Abschleppdienst"), VehicleType.VAN, VehicleRole.TOW_TRUCK),
                vehicle(translatable("Straßenmeisterei (Ölspuren)"), VehicleType.VAN, VehicleRole.STREET_SERVICE),
                vehicle(
                    translatable("Rad-Korso – blockiert die Fahrbahn"),
                    VehicleType.BIKE_CORSO,
                    VehicleRole.BIKE_CORSO,
                    scale = 5f,
                ),
                vehicle(
                    translatable("Schwertransport mit Polizeibegleitung"),
                    VehicleType.HEAVY_TRANSPORT,
                    VehicleRole.HEAVY_TRANSPORT,
                    Color(0xFFFFD60A),
                    FLAG_EMERGENCY,
                    scale = 1.8f,
                ),
            ),
        translatable("Zustände") to
            listOf(
                vehicle(translatable("Bremst (Bremslichter)"), VehicleType.CAR, flags = FLAG_BRAKING),
                vehicle(translatable("Liegengeblieben (Warnblinker)"), VehicleType.CAR, flags = FLAG_DISABLED),
                vehicle(
                    translatable("Softwarepanne – „Update wird installiert“"),
                    VehicleType.CAR,
                    flags = FLAG_DISABLED,
                    trouble = IncidentKind.SOFTWARE_FAILURE,
                ),
                vehicle(
                    translatable("Akku leer"),
                    VehicleType.CAR,
                    color = Color(0xFF2A9D8F),
                    flags = FLAG_DISABLED or FLAG_ELECTRIC,
                    trouble = IncidentKind.EMPTY_BATTERY,
                ),
                vehicle(
                    translatable("Unfall"),
                    VehicleType.CAR,
                    flags = FLAG_DISABLED,
                    trouble = IncidentKind.ACCIDENT,
                ),
                vehicle(translatable("Steht schon lange im Stau (Dampf)"), VehicleType.CAR, flags = FLAG_JAMMED),
            ),
        translatable("Ampeln") to
            listOf(
                signal(translatable("Rot (oben)"), SignalState.RED),
                signal(translatable("Rot-Gelb – gleich wird es grün"), SignalState.RED_YELLOW),
                signal(translatable("Gelb (Mitte)"), SignalState.YELLOW),
                signal(translatable("Grün (unten)"), SignalState.GREEN),
                signal(translatable("Ausgefallen – blinkt gelb, rechts vor links"), SignalState.FLASHING),
            ),
        translatable("Schilder & Überwachung") to
            listOf(
                LegendEntry(translatable("Tempolimit (rote Zone: hier gilt es)"), 1.5f) { s, m, sp ->
                    drawLine(MapStyle.limitZone, Offset(-LINE_HALF, 0f), Offset(LINE_HALF, 0f), ZONE_PREVIEW)
                    drawSpeedSigns(listOf(SpeedSign(0f, 0f, "30")), s, m, sp)
                },
                LegendEntry(translatable("Tempolimit nur nachts (22–6 Uhr)"), 1.5f) { s, m, sp ->
                    drawSpeedSigns(listOf(SpeedSign(0f, 0f, "20", night = true)), s, m, sp)
                },
                LegendEntry(
                    translatable("Blitzer (fest)"),
                    1.5f,
                ) { s, _, sp -> drawCameras(listOf(SpeedCamera(0, 0, 0f, 0f)), s, sp) },
                LegendEntry(
                    translatable("Blitzeranhänger – mobil, beliebig umsetzbar, Ortskundige kennen ihn nicht"),
                    1.5f,
                ) { s, _, sp ->
                    drawCameras(listOf(SpeedCamera(0, 0, 0f, 0f, mobile = true, trailer = true)), s, sp)
                },
                LegendEntry(translatable("Lasermessung (mobil, kostet täglich Personal)"), 1.5f) { s, _, sp ->
                    drawCameras(listOf(SpeedCamera(0, 0, 0f, 0f, mobile = true)), s, sp)
                },
                LegendEntry(translatable("Vorfahrtsstraße / Radweg-Querung"), 1.5f) { s, _, sp ->
                    drawRoadMarkers(listOf(-MARKER_GAP to 0f), listOf(MARKER_GAP to 0f), s, sp)
                },
            ),
        translatable("Vorfälle") to
            listOf(
                incident(translatable("Ölspur – Tempo runter, Straßenmeisterei kommt"), IncidentKind.OIL_SPILL),
                incident(translatable("Unfallstelle"), IncidentKind.ACCIDENT),
                incident(translatable("Panne"), IncidentKind.SOFTWARE_FAILURE),
                incident(translatable("LKW in der Nebenstraße eingeklemmt"), IncidentKind.TRUCK_STUCK),
            ),
        translatable("Bauamt") to
            listOf(
                damage(translatable("Neue Schadensmeldung – Frist läuft (Ring)"), DamageStatus.OPEN, 50),
                damage(translatable("Krater"), DamageStatus.IGNORED, 20),
                damage(translatable("Warnschild steht (schief und verblasst)"), DamageStatus.SIGNED, 50),
                damage(translatable("In Planfeststellung (grauer Kasten)"), DamageStatus.PLANNING, 50),
                LegendEntry(translatable("Baustelle"), 1.5f) { s, _, sp -> drawExcavators(listOf(0f to 0f), s, sp) },
                line(translatable("Baustelle (orange gestrichelt)"), MapStyle.works, 8f, dashed = true),
                line(translatable("Gesperrt (rot gestrichelt)"), MapStyle.trafficSignal, 8f, dashed = true),
                icon(translatable("Brücke eingestürzt"), "bridge_collapsed", wide = true) {
                    drawLine(MapStyle.collapsed, Offset(-LINE_HALF, 0f), Offset(LINE_HALF, 0f), COLLAPSED_LINE)
                },
                icon(translatable("Umleitung"), "detour") {
                    drawLine(
                        MapStyle.detour.copy(alpha = 0.6f),
                        Offset(-LINE_HALF, 0f),
                        Offset(LINE_HALF, 0f),
                        DETOUR_LINE,
                    )
                },
            ),
        translatable("Ereignisse & Stimmung") to
            listOf(
                line(
                    translatable("Demo, Stadtfest, Marathon, Markt: gesperrt"),
                    MapStyle.eventClosure,
                    8f,
                    dashed = true,
                ),
                event(translatable("Spontandemo"), EventKind.DEMO),
                event(translatable("Stadtfest"), EventKind.CITY_FESTIVAL),
                event(translatable("Marathon"), EventKind.MARATHON),
                event(translatable("Wochenmarkt"), EventKind.MARKET),
                event(translatable("Rad-Korso"), EventKind.BIKE_CORSO),
                event(translatable("Schwertransport"), EventKind.HEAVY_TRANSPORT),
                line(translatable("Route von Rad-Korso / Schwertransport"), MapStyle.corso.copy(alpha = 0.35f), 14f),
                line(translatable("Stau (Gesamtansicht): gelb zäh, rot Stillstand"), MapStyle.flowJam, 10f),
                LegendEntry(translatable("Wutwolke: hier stehen viele lange im Stau"), 0.8f) { s, _, sp ->
                    drawAngerCloud(Offset(0f, CLOUD_PREVIEW_LIFT), CLOUD_PREVIEW_SIZE, s, sp)
                },
            ),
        translatable("Parken & Laden") to
            listOf(
                LegendEntry(translatable("Parkplatz (roter Rand: voll)"), 1.5f) { s, _, sp ->
                    drawParkingLots(listOf(ParkingLotState(0, null, 0f, 0f, 100, 100, 100, 1.5)), s, sp)
                },
                LegendEntry(translatable("Ladesäule (roter Rand: Warteschlange)"), 1.5f) { s, _, sp ->
                    drawChargingStations(listOf(ChargingStationState(0, 0f, 0f, 2, 2, 3)), s, sp)
                },
            ),
    )

private const val ZONE_PREVIEW = 14f
private const val MARKER_GAP = 9f
private const val CLOUD_PREVIEW_LIFT = 8f
private const val CLOUD_PREVIEW_SIZE = 22f
