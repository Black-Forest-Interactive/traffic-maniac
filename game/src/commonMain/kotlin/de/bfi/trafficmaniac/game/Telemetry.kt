package de.bfi.trafficmaniac.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Anonyme Spielstatistik (Paket F, nur mit Zustimmung): ein Paket je beendeter oder abgebrochener Partie. Keine
 * Namen, keine Spielstände, keine Orte über die Stadt hinaus – nur, was fürs Balancing zählt. Der Spieler kann sich
 * jedes Paket vor dem Senden im Klartext ansehen ([TelemetryJson]).
 */
@Serializable
data class GameTelemetry(
    /** Version dieses Formats; der Server lehnt unbekannte ab. */
    val schema: Int = SCHEMA,
    /** Zufällige Kennung der Installation, vom Spieler zurücksetzbar. */
    val installation: String,
    val appVersion: String,
    /** "desktop-linux", "desktop-windows", "android" … */
    val platform: String,
    /** Grobe Leistungsklasse: Prozessorkerne und Arbeitsspeicher, z. B. "8 Kerne, 16 GB". */
    val deviceClass: String,
    val language: String,
    val city: String,
    val scenario: String,
    val difficulty: String,
    /** Ende der Partie: [END_TERM], [END_RECALLED] oder [END_ABORTED]. */
    val end: String,
    val daysPlayed: Int,
    val realMinutes: Int,
    val points: Long,
    val earnings: Long,
    /** Einnahmen nach Quelle in Euro: Bußgelder, Parkgebühren, Knöllchen, Haushalt. */
    val income: Map<String, Long>,
    val expenses: Long,
    val days: List<TelemetryDay>,
    /** Wie oft welche Spieleraktion ausgeführt wurde (Name des Befehls → Anzahl). */
    val tools: Map<String, Int>,
    /** Echte Sekunden je gewähltem Zeitraffer ("0" = Pause). */
    val speeds: Map<String, Int> = emptyMap(),
    val performance: TelemetryPerformance? = null,
) {
    companion object {
        const val SCHEMA = 1
        const val END_TERM = "term"
        const val END_RECALLED = "recalled"
        const val END_ABORTED = "aborted"
    }
}

/** Ein Spieltag: Kasse, Stimmung, Politik und Verkehrsbilanz. */
@Serializable
data class TelemetryDay(
    val day: Int,
    val balance: Long,
    val unhappyPercent: Int,
    val anger: Int,
    val recallIndex: Int,
    val reputation: Int,
    val vehicleKm: Long,
    val vehicleHours: Long,
)

/** Leistung des Geräts während der Partie (Mittelwerte). */
@Serializable
data class TelemetryPerformance(
    val stepMillis: Double,
    val drawMillis: Double,
    val framesPerSecond: Int,
)

/** Klartext für „Anzeigen, was gesendet wird“ und für den Versand. */
val TelemetryJson =
    Json {
        prettyPrint = true
        encodeDefaults = true
    }

/** Was eine Partie selbst über sich weiß; Gerät, Kennung und Echtzeit ergänzt die Plattform. */
internal class TelemetryLog {
    val days = ArrayList<TelemetryDay>()
    val tools = HashMap<String, Int>()

    fun used(command: GameCommand) {
        val name = command::class.simpleName ?: return
        tools[name] = (tools[name] ?: 0) + 1
    }

    fun dayEnded(
        day: Int,
        state: GameState,
        vehicleKm: Double,
        vehicleHours: Double,
    ) {
        days +=
            TelemetryDay(
                day = day,
                balance = state.balance,
                unhappyPercent = (state.unhappyShare * PERCENT).roundToInt(),
                anger = state.anger.roundToInt(),
                recallIndex = state.recallIndex.roundToInt(),
                reputation = state.reputation.roundToInt(),
                vehicleKm = vehicleKm.toLong(),
                vehicleHours = vehicleHours.toLong(),
            )
    }

    private companion object {
        const val PERCENT = 100
    }
}

/** Angaben der Plattform zur Partie, die das Spiel selbst nicht kennt. */
data class TelemetryContext(
    val installation: String,
    val appVersion: String,
    val platform: String,
    val deviceClass: String,
    val language: String,
    val city: String,
    val scenario: String,
    val difficulty: String,
    val realMinutes: Int,
    val speeds: Map<String, Int> = emptyMap(),
    val performance: TelemetryPerformance? = null,
)
