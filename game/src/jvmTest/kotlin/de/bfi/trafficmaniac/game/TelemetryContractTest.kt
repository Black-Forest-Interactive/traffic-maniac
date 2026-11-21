package de.bfi.trafficmaniac.game

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vertrag zwischen Spiel und Statistik-Server (privates Repo `traffic-maniac-stats`): Das Beispielpaket
 * `telemetry-sample.json` muss genau so aussehen, wie das Spiel es schreibt. Der Server testet gegen eine Kopie davon.
 * Ändert sich [GameTelemetry], schlägt dieser Test fehl – dann das Beispiel neu schreiben (`TM_WRITE_SAMPLE=1`),
 * [GameTelemetry.SCHEMA] erhöhen und Kopie samt Server im Statistik-Repo anpassen.
 */
class TelemetryContractTest {
    private val sample =
        GameTelemetry(
            installation = "3f2b6f3c-4b7e-4f0e-9a51-3c1d2e8a7b42",
            game = "9c4e1d2a-7b3f-4e8a-b5c6-0d1e2f3a4b5c",
            appVersion = "0.2.0",
            platform = "desktop-linux",
            deviceClass = "16 cores, 8 GB heap",
            language = "de",
            city = "pforzheim",
            scenario = "erste-amtszeit",
            difficulty = "normal",
            end = GameTelemetry.END_RECALLED,
            daysPlayed = 2,
            realMinutes = 47,
            points = 12_345,
            earnings = 24_690,
            income = mapOf("fines" to 68_300, "parking" to 0, "tickets" to 420, "allowance" to 17_142),
            expenses = 141_000,
            days =
                listOf(
                    TelemetryDay(0, 42_343, 4, 0, 0, 44, 412_000, 14_300),
                    TelemetryDay(1, 45_792, 39, 12, 4, 37, 430_500, 15_800),
                ),
            tools = mapOf("PlaceCamera" to 5, "SetSpeedLimit" to 7),
            speeds = mapOf("0" to 120, "1" to 900, "8" to 1_800),
            performance = TelemetryPerformance(0.61, 2.4, 58),
        )

    @Test
    fun serverSampleMatchesWhatTheGameWrites() {
        val file = File(rootDir(), "game/src/jvmTest/resources/telemetry-sample.json")
        val written = TelemetryJson.encodeToString(GameTelemetry.serializer(), sample) + "\n"
        if (System.getenv("TM_WRITE_SAMPLE") != null) file.writeText(written)
        assertEquals(written, file.readText(), "Beispielpaket des Servers veraltet – siehe Kommentar")
    }

    private fun rootDir(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "game").isDirectory) dir = requireNotNull(dir.parentFile) { "Projektordner nicht gefunden" }
        return dir
    }
}
