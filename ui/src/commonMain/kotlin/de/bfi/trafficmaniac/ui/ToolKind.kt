package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon

/**
 * Was ein Klick auf die Karte tut. Jede Art gehört zu einer Gruppe im Dock ([Tool]); in einer Gruppe mit mehreren
 * Arten wählt man sie in der Ausklappliste über der Kachel (oder mit der Gruppentaste der Reihe nach).
 */
enum class ToolKind(
    val group: Tool,
    private val labelText: String,
    /** Kurzer Name für die Kachel im Dock, wenn der volle zu lang ist. */
    private val shortText: String?,
    internal val icon: TmIcon,
    /** Eine Zeile Erklärung in der Ausklappliste. */
    private val noteText: String,
    /** Ampelprogramm: Anteil der Grünzeit für die Hauptrichtung. */
    val signalShare: Double? = null,
    /** Grüne Welle: Tempo in km/h. */
    val waveKmh: Int? = null,
    /** Was ein Klick kostet (`null`: nichts). */
    private val cost: (GameRules) -> Long? = { null },
    private val unit: PriceUnit = PriceUnit.ONCE,
) {
    LOOK(Tool.LOOK, translatable("Ansehen"), null, Icons.Eye, translatable("Karte erkunden, nichts setzen")),
    SPEED_LIMIT(
        Tool.SPEED_LIMIT,
        translatable("Tempolimit"),
        null,
        Icons.Gauge,
        translatable("Anfang und Ende anklicken"),
        cost = { it.speedLimitSignCost },
        unit = PriceUnit.STRETCH,
    ),
    SIGNAL_BUILD(
        Tool.SIGNAL,
        translatable("Ampel bauen"),
        translatable("Ampel"),
        Icons.TrafficLight,
        translatable("Neue Anlage an einer Kreuzung"),
        cost = { it.signalCost },
    ),
    SIGNAL_EQUAL(
        Tool.SIGNAL,
        translatable("Grünzeit gleich"),
        translatable("Gleich"),
        Icons.Timer,
        translatable("50/50 für beide Richtungen"),
        signalShare = 0.5,
        cost = { it.signalProgramCost },
    ),
    SIGNAL_MAIN(
        Tool.SIGNAL,
        translatable("Hauptrichtung länger"),
        "70/30",
        Icons.Timer,
        translatable("Die große Straße bevorzugen"),
        signalShare = 0.7,
        cost = { it.signalProgramCost },
    ),
    SIGNAL_SIDE(
        Tool.SIGNAL,
        translatable("Querrichtung länger"),
        "30/70",
        Icons.Timer,
        translatable("Die Seitenstraße bevorzugen"),
        signalShare = 0.3,
        cost = { it.signalProgramCost },
    ),
    WAVE_30(
        Tool.SIGNAL,
        translatable("Grüne Welle 30"),
        translatable("Welle 30"),
        Icons.Waves,
        translatable("Anfang und Ende anklicken"),
        waveKmh = 30,
        cost = { it.signalProgramCost },
        unit = PriceUnit.LIGHT,
    ),
    WAVE_50(
        Tool.SIGNAL,
        translatable("Grüne Welle 50"),
        translatable("Welle 50"),
        Icons.Waves,
        translatable("Anfang und Ende anklicken"),
        waveKmh = 50,
        cost = { it.signalProgramCost },
        unit = PriceUnit.LIGHT,
    ),
    CAMERA_FIXED(
        Tool.CAMERA,
        translatable("Blitzer, fest"),
        translatable("Blitzer"),
        Icons.Camera,
        translatable("Ortskundige bremsen davor"),
        cost = { it.cameraCost },
    ),
    LASER(
        Tool.CAMERA,
        translatable("Lasermessung"),
        translatable("Laser"),
        Icons.Zap,
        translatable("Mobil, kostet Personal"),
        cost = { it.laserDailyCost },
        unit = PriceUnit.DAY,
    ),
    TRAILER(
        Tool.CAMERA,
        translatable("Blitzeranhänger"),
        translatable("Anhänger"),
        Icons.Trailer,
        translatable("Aufstellen oder umsetzen"),
        cost = { it.trailerCost },
    ),
    RED_LIGHT(
        Tool.CAMERA,
        translatable("Rotlichtblitzer"),
        translatable("Rotlicht"),
        Icons.TrafficLight,
        translatable("An der Haltelinie einer Zufahrt"),
        cost = { it.redLightCameraCost },
    ),
    RED_LIGHT_SPEED(
        Tool.CAMERA,
        translatable("Rotlicht + Tempo"),
        translatable("Rot+Tempo"),
        Icons.TrafficLight,
        translatable("Haltelinie und Tempo"),
        cost = { it.redLightSpeedCameraCost },
    ),
    DISPLAY(
        Tool.CAMERA,
        translatable("Anzeigetafel"),
        translatable("Tafel"),
        Icons.Monitor,
        translatable("Zeigt den Raser-Anteil, blitzt nicht"),
        cost = { it.displayCost },
    ),
    POLICE_CHECK(
        Tool.CAMERA,
        translatable("Polizeikontrolle"),
        translatable("Kontrolle"),
        Icons.Shield,
        translatable("Drei Stunden, höchstens zwei"),
        cost = { it.policeCheckCost },
    ),
    PRIORITY(
        Tool.ROUTE,
        translatable("Vorfahrtsstraße"),
        translatable("Vorfahrt"),
        Icons.Diamond,
        translatable("Abschnitt bekommt Vorfahrt"),
        cost = { it.priorityRoadCost },
    ),
    BIKE_CROSSING(
        Tool.ROUTE,
        translatable("Radweg-Querung"),
        translatable("Radweg"),
        Icons.Bike,
        translatable("Querung mit Bremswirkung"),
        cost = { it.bikeCrossingCost },
    ),
    BIKE_STREET(
        Tool.ROUTE,
        translatable("Fahrradstraße"),
        translatable("Fahrradstr."),
        Icons.Bike,
        translatable("Tempo 30, kein Überholen"),
        cost = { it.bikeStreetCost },
        unit = PriceUnit.STRETCH,
    ),
    DETOUR(Tool.ROUTE, translatable("Umleitung"), null, Icons.Detour, translatable("Nebenstraßen zuschalten")),
    ROADWORKS(
        Tool.ROADWORKS,
        translatable("Baustelle · Tempo 30"),
        translatable("Baustelle"),
        Icons.Barrier,
        translatable("Verengt, Tempo 30, niemand arbeitet"),
        cost = { it.roadworksSetupCost },
    ),
    CLOSURE(
        Tool.ROADWORKS,
        translatable("Vollsperrung"),
        translatable("Sperrung"),
        Icons.Ban,
        translatable("Verkehr muss außen herum"),
        cost = { it.closureCost },
        unit = PriceUnit.STRETCH,
    ),
    REMOVE(
        Tool.REMOVE,
        translatable("Abbauen"),
        null,
        Icons.Eraser,
        translatable("Geplantes streichen, Gebautes entfernen"),
    ),

    ;

    val label: String get() = tr(labelText)

    /** Name auf der Kachel – einzeilig. */
    internal val short: String get() = tr(shortText ?: labelText)
    internal val note: String get() = tr(noteText)

    /**
     * Preis: ausführlich für die Ausklappliste, mit [short] knapp für die Kachel (ohne „je Strecke“ und
     * Tageskosten der Baustelle). Leer, wenn es nichts kostet.
     */
    fun price(
        rules: GameRules,
        idleTrailers: Int,
        short: Boolean = false,
    ): String {
        val euros = cost(rules)?.let(::formatEuro)
        val suffix = unit.suffix?.takeIf { !short || unit == PriceUnit.DAY }
        return when {
            this == TRAILER && idleTrailers > 0 -> {
                tr("{count} frei", "count" to idleTrailers)
            }

            euros == null -> {
                ""
            }

            this == ROADWORKS && !short -> {
                tr("{cost} + {daily}/Tag", "cost" to euros, "daily" to formatEuro(rules.roadworksDailyCost))
            }

            suffix == null -> {
                euros
            }

            else -> {
                tr("{cost}/{unit}", "cost" to euros, "unit" to tr(suffix))
            }
        }
    }
}

/** Wofür ein Preis gilt: einmalig, je Tag, je Strecke, je Ampel. */
internal enum class PriceUnit(
    val suffix: String?,
) {
    ONCE(null),
    DAY(translatable("Tag")),
    STRETCH(translatable("Strecke")),
    LIGHT(translatable("Ampel")),
}
