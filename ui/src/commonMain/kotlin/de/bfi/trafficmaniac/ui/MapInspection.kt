package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.hypot

/** Was ein Klick auf der Karte getroffen hat – gemerkt wird nur die Kennung, die Daten kommen aus jedem Bild neu. */
internal sealed interface Inspected {
    /** Gemeldeter Straßenschaden ([DamageReport.id]). */
    data class Damage(
        val id: Int,
    ) : Inspected

    /** Laufender Vorfall, erkannt an Art und Beginn. */
    data class Incident(
        val kind: Int,
        val startTime: Double,
    ) : Inspected
}

/** Ein Ort auf der Karte, an dem es etwas zu tun oder anzusehen gibt. */
internal class InspectTarget(
    val target: Inspected,
    val x: Float,
    val y: Float,
)

/**
 * Alles Anklickbare: offene Schäden (am Symbol in der Mitte jedes betroffenen Abschnitts, Ampeln am Knoten) und
 * laufende Vorfälle. Die Reihenfolge ist die der Zähler-Chips: erst Schäden mit kürzester Frist, dann Vorfälle.
 */
internal fun inspectTargets(
    game: GameState,
    incidents: List<IncidentMarker>,
    picker: MapPicker,
    city: CityMap,
): List<InspectTarget> {
    val damage =
        game.maintenance.reports
            .filter { it.status != DamageStatus.CONSTRUCTION }
            .sortedBy { it.hoursLeft }
            .flatMap { report ->
                damagePoints(report, picker, city).map { (x, y) ->
                    InspectTarget(Inspected.Damage(report.id), x, y)
                }
            }
    val running = incidents.map { InspectTarget(Inspected.Incident(it.kind.ordinal, it.startTime), it.x, it.y) }
    return damage + running
}

private fun damagePoints(
    report: DamageReport,
    picker: MapPicker,
    city: CityMap,
): List<Pair<Float, Float>> =
    if (report.segments.isNotEmpty()) {
        report.segments.map(picker::midpoint)
    } else {
        city.roads.nodes
            .getOrNull(report.node)
            ?.let { listOf(it.x to it.y) }
            .orEmpty()
    }

/** Das nächstgelegene Ziel innerhalb von [radius] Metern um ([x], [y]), sonst `null`. */
internal fun inspectAt(
    targets: List<InspectTarget>,
    x: Float,
    y: Float,
    radius: Float,
): Inspected? =
    targets
        .map { it to hypot(it.x - x, it.y - y) }
        .filter { it.second <= radius }
        .minByOrNull { it.second }
        ?.first
        ?.target

/** Zähler unter dem Kartenumschalter: „3 Schäden“, „1 Vorfall“ – ein Klick springt zum nächsten. */
@Composable
internal fun InspectChips(
    frame: TrafficFrame,
    onNext: (damage: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val damage =
        frame.game.maintenance.reports
            .count { it.status != DamageStatus.CONSTRUCTION }
    val incidents = frame.current.incidents.size
    if (damage == 0 && incidents == 0) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (damage > 0) {
            Chip(if (damage == 1) tr("1 Schaden") else tr("{count} Schäden", "count" to damage)) { onNext(true) }
        }
        if (incidents > 0) {
            Chip(
                if (incidents ==
                    1
                ) {
                    tr("1 Vorfall")
                } else {
                    tr("{count} Vorfälle", "count" to incidents)
                },
            ) { onNext(false) }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(TmColors.Ink)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.MapPin, null, tint = TmColors.Active, size = 14.dp)
        BasicText(text, style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.OnDark))
    }
}

/**
 * Aktionskarte zum angeklickten Ziel: beim Schaden dieselben Knöpfe wie im Bauamt, beim Vorfall der Stand der
 * Einsatzkräfte. Ist das Ziel weg (repariert, Unfall geräumt), schließt sie sich.
 */
@Composable
internal fun InspectCard(
    frame: TrafficFrame,
    inspected: Inspected,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: (@Composable () -> Unit)? =
        when (inspected) {
            is Inspected.Damage -> {
                frame.game.maintenance.reports.firstOrNull { it.id == inspected.id }?.let { report ->
                    {
                        ExpandedDamage(report, rules, { onCommand(GameCommand.RespondToDamage(report.id, it)) }, null)
                    }
                }
            }

            is Inspected.Incident -> {
                frame.current.incidents
                    .firstOrNull { it.kind.ordinal == inspected.kind && it.startTime == inspected.startTime }
                    ?.let { incident -> { IncidentDetails(incident) } }
            }
        }
    if (content == null) {
        LaunchedEffect(inspected) { onClose() }
        return
    }
    Column(
        modifier
            .width(CARD_WIDTH.dp)
            .background(TmColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, TmColors.Line, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Icon(
                Icons.Close,
                tr("Schließen"),
                tint = TmColors.Muted,
                size = 16.dp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClose),
            )
        }
        content()
    }
}

@Composable
private fun IncidentDetails(incident: IncidentMarker) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(incident.kind.label, style = TmType.text(14.sp, FontWeight.Bold))
        if (incident.onTheWay.isNotEmpty()) {
            BasicText(
                tr("Unterwegs: {roles}", "roles" to incident.onTheWay.joinToString { it.label }),
                style = TmType.small,
            )
        }
        if (incident.onScene.isNotEmpty()) {
            BasicText(
                tr("Vor Ort: {roles}", "roles" to incident.onScene.joinToString { it.label }),
                style = TmType.small,
            )
        }
        incident.workProgress?.let { Meter(it, Modifier.fillMaxWidth(), height = 6.dp) }
        if (incident.onTheWay.isEmpty() && incident.onScene.isEmpty()) {
            BasicText(tr("Noch niemand alarmiert."), style = TmType.small)
        }
    }
}

private const val CARD_WIDTH = 440
