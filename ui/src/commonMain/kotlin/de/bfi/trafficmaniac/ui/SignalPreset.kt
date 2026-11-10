package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable

/**
 * Programme fürs Ampel-Werkzeug: Grünzeit gleich, für die Haupt- oder die Querrichtung länger – oder eine grüne Welle
 * entlang einer Straße (Anfang und Ende anklicken) für 30 oder 50 km/h.
 */
enum class SignalPreset(
    private val labelText: String,
    /** Anteil der Hauptrichtung an der Grünzeit; `null` bei grüner Welle. */
    val mainShare: Double?,
    /** Wellentempo; `null` bei Aufteilung. */
    val waveKmh: Int?,
) {
    EQUAL(translatable("Gleich"), 0.5, null),
    MAIN(translatable("Hauptrichtung länger"), 0.7, null),
    SIDE(translatable("Querrichtung länger"), 0.3, null),
    WAVE_30(translatable("Welle 30"), null, 30),
    WAVE_50(translatable("Welle 50"), null, 50),
    ;

    val label: String get() = tr(labelText)
}

private val OptionsShape = RoundedCornerShape(12.dp)
private val OptionsShadow = TmColors.Ink.copy(alpha = 0.32f)

/** Auswahl über dem Dock, solange das Ampelprogramm-Werkzeug gewählt ist. */
@Composable
internal fun SignalTimingOptions(
    selection: ToolSelection,
    onSelect: (ToolSelection) -> Unit,
) {
    Row(
        Modifier
            .shadow(8.dp, OptionsShape, spotColor = OptionsShadow)
            .background(TmColors.Surface, OptionsShape)
            .border(1.dp, TmColors.Line, OptionsShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(tr("Grünzeit"), style = TmType.text(12.sp, FontWeight.Bold, TmColors.Muted))
        for (preset in SignalPreset.entries.filter { it.mainShare != null }) {
            OptionChip(
                preset.label,
                selection.signalPreset == preset,
            ) { onSelect(selection.copy(signalPreset = preset)) }
        }
        Box(Modifier.width(1.dp).height(28.dp).background(TmColors.Track))
        BasicText(tr("Grüne Welle"), style = TmType.text(12.sp, FontWeight.Bold, TmColors.Muted))
        for (preset in SignalPreset.entries.filter { it.waveKmh != null }) {
            OptionChip(
                preset.label,
                selection.signalPreset == preset,
            ) { onSelect(selection.copy(signalPreset = preset)) }
        }
    }
}

/** Auswahl über dem Dock beim Baustellen-Werkzeug: Baustelle mit Tempo 30 oder Vollsperrung. */
@Composable
internal fun RoadworksOptions(
    selection: ToolSelection,
    rules: de.bfi.trafficmaniac.game.GameRules,
    onSelect: (ToolSelection) -> Unit,
) {
    Row(
        Modifier
            .shadow(8.dp, OptionsShape, spotColor = OptionsShadow)
            .background(TmColors.Surface, OptionsShape)
            .border(1.dp, TmColors.Line, OptionsShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OptionChip(tr("Baustelle · Tempo 30"), !selection.closure) { onSelect(selection.copy(closure = false)) }
        OptionChip(tr("Vollsperrung"), selection.closure) { onSelect(selection.copy(closure = true)) }
        BasicText(
            if (selection.closure) {
                tr("{cost} je Strecke", "cost" to formatEuro(rules.closureCost))
            } else {
                tr(
                    "{cost} + {daily}/Tag je Strecke",
                    "cost" to formatEuro(rules.roadworksSetupCost),
                    "daily" to formatEuro(rules.roadworksDailyCost),
                )
            },
            style = TmType.mono(11.sp, bold = false, color = TmColors.Muted),
        )
    }
}

/** Auswahl über dem Dock beim Radweg-Werkzeug: Radweg-Querung oder Fahrradstraße (Anfang und Ende anklicken). */
@Composable
internal fun BikeOptions(
    selection: ToolSelection,
    rules: de.bfi.trafficmaniac.game.GameRules,
    onSelect: (ToolSelection) -> Unit,
) {
    Row(
        Modifier
            .shadow(8.dp, OptionsShape, spotColor = OptionsShadow)
            .background(TmColors.Surface, OptionsShape)
            .border(1.dp, TmColors.Line, OptionsShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OptionChip(tr("Radweg-Querung · {cost}", "cost" to formatEuro(rules.bikeCrossingCost)), !selection.bikeStreet) {
            onSelect(selection.copy(bikeStreet = false))
        }
        OptionChip(
            tr("Fahrradstraße · {cost} je Strecke", "cost" to formatEuro(rules.bikeStreetCost)),
            selection.bikeStreet,
        ) {
            onSelect(selection.copy(bikeStreet = true))
        }
        BasicText(tr("Tempo 30, Kfz dürfen Radfahrer nicht überholen"), style = TmType.caption)
    }
}
