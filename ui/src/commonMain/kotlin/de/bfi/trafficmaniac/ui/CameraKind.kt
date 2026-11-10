package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.hypot

/** Arten des Blitzer-Werkzeugs – eine Kachel im Dock, die Art wählt man darüber (oder mit erneutem E). */
enum class CameraKind(
    private val labelText: String,
) {
    FIXED(translatable("Fest")),
    LASER(translatable("Laser")),
    TRAILER(translatable("Anhänger")),
    RED_LIGHT(translatable("Rotlicht")),
    RED_LIGHT_SPEED(translatable("Rotlicht + Tempo")),
    DISPLAY(translatable("Anzeigetafel")),
    ;

    val label: String get() = tr(labelText)

    /** Die nächste Art (erneutes Drücken der Taste). */
    val next: CameraKind get() = entries[(ordinal + 1) % entries.size]

    /** Preis für die Kachel und die Auswahl. */
    fun price(
        rules: GameRules,
        idleTrailers: Int,
    ): String =
        when (this) {
            FIXED -> {
                formatEuro(rules.cameraCost)
            }

            LASER -> {
                tr("{cost}/Tag", "cost" to formatEuro(rules.laserDailyCost))
            }

            TRAILER -> {
                if (idleTrailers >
                    0
                ) {
                    tr("{count} frei", "count" to idleTrailers)
                } else {
                    formatEuro(rules.trailerCost)
                }
            }

            RED_LIGHT -> {
                formatEuro(rules.redLightCameraCost)
            }

            RED_LIGHT_SPEED -> {
                formatEuro(rules.redLightSpeedCameraCost)
            }

            DISPLAY -> {
                formatEuro(rules.displayCost)
            }
        }
}

/**
 * Klick mit dem Blitzer-Werkzeug: Blitzer, Laser und Anzeigetafel auf den nächsten Abschnitt; Anhänger wie gehabt
 * (Klick auf einen aufgestellten lädt ihn auf); Rotlichtblitzer an die nächste Ampelzufahrt.
 */
internal fun cameraCommand(
    kind: CameraKind,
    picker: MapPicker,
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): GameCommand? {
    val detour = frame?.game?.detourSegments.orEmpty()
    val segment by lazy { picker.nearestSegment(x, y, radius, allowPassive = { it in detour }) }
    return when (kind) {
        CameraKind.FIXED -> {
            segment?.let { GameCommand.PlaceCamera(it) }
        }

        CameraKind.LASER -> {
            segment?.let { GameCommand.PlaceCamera(it, mobile = true) }
        }

        CameraKind.DISPLAY -> {
            segment?.let { GameCommand.PlaceDisplay(it) }
        }

        CameraKind.TRAILER -> {
            nearestCamera(frame, x, y, radius) { it.trailer }?.let { GameCommand.RemoveCamera(it.id) }
                ?: segment?.let { GameCommand.PlaceTrailer(it) }
        }

        CameraKind.RED_LIGHT, CameraKind.RED_LIGHT_SPEED -> {
            approachNear(frame, x, y, radius)?.let {
                GameCommand.PlaceRedLightCamera(
                    it,
                    kind == CameraKind.RED_LIGHT_SPEED,
                )
            }
        }
    }
}

/** Ampelzufahrt, deren Haltelinie dem Klick am nächsten liegt (Ampeln stehen oft etwas vor der Kreuzung). */
private fun approachNear(
    frame: TrafficFrame?,
    x: Float,
    y: Float,
    radius: Float,
): Int? {
    val markers = frame?.current?.signalMarkers ?: return null
    return markers.links.indices
        .filter { hypot(markers.x[it] - x, markers.y[it] - y) <= radius * APPROACH_PICK_FACTOR }
        .minByOrNull { hypot(markers.x[it] - x, markers.y[it] - y) }
        ?.let { markers.links[it] }
}

private val OptionsShape = RoundedCornerShape(12.dp)
private val OptionsShadow = TmColors.Ink.copy(alpha = 0.32f)

/** Auswahl über dem Dock, solange das Blitzer-Werkzeug gewählt ist. */
@Composable
internal fun CameraOptions(
    selection: ToolSelection,
    rules: GameRules,
    idleTrailers: Int,
    onSelect: (ToolSelection) -> Unit,
) {
    Row(
        Modifier
            .shadow(8.dp, OptionsShape, spotColor = OptionsShadow)
            .background(TmColors.Surface, OptionsShape)
            .border(1.dp, TmColors.Line, OptionsShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (kind in CameraKind.entries) {
            OptionChip("${kind.label} · ${kind.price(rules, idleTrailers)}", selection.cameraKind == kind) {
                onSelect(selection.copy(cameraKind = kind))
            }
        }
    }
}

private const val APPROACH_PICK_FACTOR = 3f
