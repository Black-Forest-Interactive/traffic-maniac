package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.SettingRow
import de.bfi.trafficmaniac.ui.components.TmButton

/** Was die Plattform für die anonymen Statistiken anbietet (Paket F). */
class StatisticsControls(
    /** Das zuletzt erzeugte Paket im Klartext, `null` vor der ersten beendeten Partie. */
    val lastPacket: () -> String?,
    /** Neue zufällige Kennung der Installation. */
    val onNewInstallation: () -> Unit,
    /** Zustimmung zurückgezogen: alles noch nicht Gesendete löschen. */
    val onWithdraw: () -> Unit,
)

private val WHAT_IS_SENT =
    listOf(
        translatable("Version, Plattform, grobe Geräteklasse, Sprache"),
        translatable("Stadt, Szenario, Schwierigkeit, Dauer, wie die Partie endete"),
        translatable("Punkte, Einnahmen nach Quelle, Ausgaben"),
        translatable("je Spieltag: Kasse, Unzufriedene, Zorn, Abwahlindex, Reputation, Verkehrsbilanz"),
        translatable("wie oft welche Werkzeuge und Zeitraffer benutzt wurden, Rechen- und Zeichenzeit"),
    )

/** Frage beim ersten Start: anonym mithelfen oder nicht. Bis zur Antwort wird nichts gesammelt. */
@Composable
internal fun StatisticsConsent(onAnswer: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize().background(TmColors.Scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(CARD_WIDTH.dp)
                .background(TmColors.Surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(tr("Beim Balancing helfen?"), style = TmType.display(22.sp))
            BasicText(
                tr(
                    "Nach jeder Partie kann das Spiel ein kleines, anonymes Paket an uns schicken. Damit sehen wir, " +
                        "an welchem Tag Spieler aufhören und warum – und stellen das Spiel danach ein.",
                ),
                style = TmType.text(14.sp),
            )
            WhatIsSent()
            BasicText(
                tr(
                    "Keine Namen, keine Spielstände, keine IP-Adressen. Gesendet wird beim nächsten Start, nie " +
                        "während des Spiels. Jederzeit änderbar unter Einstellungen.",
                ),
                style = TmType.small,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                TmButton(tr("Nein danke"), { onAnswer(false) })
                TmButton(tr("Ja, anonym mithelfen"), { onAnswer(true) }, kind = ButtonKind.PRIMARY)
            }
        }
    }
}

@Composable
private fun WhatIsSent() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in WHAT_IS_SENT) BasicText("• " + tr(line), style = TmType.text(13.sp))
    }
}

/** Abschnitt in den Einstellungen: an/aus, Klartext des letzten Pakets, neue Kennung. */
@Composable
internal fun StatisticsSettings(
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
    controls: StatisticsControls,
) {
    var shown by remember { mutableStateOf<String?>(null) }
    SettingRow(tr("Anonyme Statistiken"), hint = tr("Ein Paket je Partie, gesendet beim nächsten Start")) {
        SegmentedControl(listOf(false, true), settings.statistics == true, {
            if (it) tr("An") else tr("Aus")
        }, { on ->
            if (!on) controls.onWithdraw()
            onSettings(settings.copy(statistics = on))
        }, Modifier.width(ON_OFF.dp))
    }
    WhatIsSent()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TmButton(
            if (shown == null) tr("Anzeigen, was gesendet wird") else tr("Ausblenden"),
            { shown = if (shown == null) controls.lastPacket() ?: tr("Noch keine Partie beendet.") else null },
        )
        TmButton(tr("Kennung zurücksetzen"), controls.onNewInstallation, kind = ButtonKind.TEXT)
    }
    shown?.let {
        BasicText(
            it,
            style = TmType.mono(11.sp, bold = false),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = PACKET_HEIGHT.dp)
                    .background(TmColors.Ground, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
        )
    }
}

private const val CARD_WIDTH = 520
private const val ON_OFF = 160
private const val PACKET_HEIGHT = 260
