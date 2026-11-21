package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.city.FacilityKind
import de.bfi.trafficmaniac.game.IncidentCounts
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.IncidentKind
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.sim.VehicleRole
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.PageMode
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.roundToInt

/** Einsatzkräfte und die Wache, von der sie ausrücken (Abschlepper und Feuerwehr kommen von der Feuerwache). */
private val STATIONS =
    listOf(
        VehicleRole.POLICE to FacilityKind.POLICE,
        VehicleRole.AMBULANCE to FacilityKind.AMBULANCE_STATION,
        VehicleRole.FIRE_BRIGADE to FacilityKind.FIRE_STATION,
        VehicleRole.TOW_TRUCK to FacilityKind.FIRE_STATION,
    )

/**
 * Je Einsatzkraft: Wachen in der Stadt, unterwegs und im Einsatz. Wachen haben beliebig viele Fahrzeuge – es fehlt
 * also nie eines, aber jede Anfahrt durch den Stau kostet Zeit.
 */
@Composable
internal fun Stations(
    incidents: List<IncidentMarker>,
    city: CityMap,
) {
    Row(Modifier.fillMaxWidth()) {
        Head(tr("Einsatzkraft"), Modifier.weight(1f), TextAlign.Start)
        Head(tr("Wachen"))
        Head(tr("unterwegs"))
        Head(tr("im Einsatz"))
    }
    for ((role, kind) in STATIONS) {
        val stations = city.facilities.count { it.kind == kind }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(role.label, style = TmType.text(13.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Value("$stations")
            Value("${incidents.sumOf { incident -> incident.onTheWay.count { it == role } }}")
            Value("${incidents.sumOf { incident -> incident.onScene.count { it == role } }}")
        }
    }
}

/** Erledigte Einsätze und mittlere Anfahrt des Rettungsdienstes in der Amtszeit. */
@Composable
internal fun TermFigures(
    counts: IncidentCounts,
    running: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Figure(tr("Erledigt"), "${(counts.term.values.sum() - running).coerceAtLeast(0)}")
        Figure(tr("Heute"), "${counts.today.values.sum()}")
        Figure(
            tr("Ø Anfahrt Rettung"),
            counts.meanAmbulanceMinutes?.let { tr("{minutes} min", "minutes" to formatDecimal(it, 1)) } ?: "–",
        )
    }
    if (counts.term.isEmpty()) BasicText(tr("Bisher kein Einsatz in dieser Amtszeit."), style = TmType.small)
}

@Composable
private fun Figure(
    label: String,
    value: String,
) {
    Column {
        BasicText(label, style = TmType.caption)
        BasicText(value, style = TmType.mono(18.sp))
    }
}

@Composable
private fun Head(
    text: String,
    modifier: Modifier = Modifier.width(COLUMN.dp),
    align: TextAlign = TextAlign.End,
) {
    BasicText(
        text,
        style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Muted).copy(textAlign = align),
        modifier = modifier,
    )
}

@Composable
private fun Value(text: String) {
    BasicText(text, style = TmType.mono(13.sp).copy(textAlign = TextAlign.End), modifier = Modifier.width(COLUMN.dp))
}

private const val COLUMN = 80

/** Was die Seite „Einsätze“ braucht. */
internal class EmergencyPageData(
    val snapshot: SimulationSnapshot,
    val picker: MapPicker,
    val onShow: (MapPlace) -> Unit,
    val counts: IncidentCounts,
)

/**
 * Einsätze als Seite (Entwurf 2, `V2-Einsaetze`): Kennzahlen, links die laufenden Einsätze (leer: ein Satz), rechts
 * Wachen & Fahrzeuge, darunter über die volle Breite die Amtszeit mit Häufigkeit nach Art und den letzten Einsätzen.
 */
@Composable
internal fun EmergencyPage(
    data: EmergencyPageData,
    page: PageMode,
) {
    val snapshot = data.snapshot
    val incidents = snapshot.incidents
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(page.eyebrow ?: tr("Bericht"), tr("Einsätze"), page.onMap)
        page.kpis?.let { KpiRow(it) }
        // Jede Karte so hoch wie ihr Inhalt – leer ist „Laufende Einsätze“ kürzer als die Wachen-Tabelle
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PageCard(tr("Laufende Einsätze"), Modifier.weight(1f)) {
                if (incidents.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Check, null, tint = TmColors.Success, size = 20.dp)
                        BasicText(tr("Alles ruhig – kein Blaulicht unterwegs."), style = TmType.small)
                    }
                }
                val sorted = incidents.sortedWith(compareBy({ it.kind != IncidentKind.ACCIDENT }, { -it.startTime }))
                for (incident in sorted.take(MAX_RUNNING)) {
                    IncidentCard(incident, snapshot.time) { data.onShow(MapPlace(incident.x, incident.y)) }
                }
                if (incidents.isNotEmpty()) ServiceFleet(snapshot, data.picker.city, data.picker, data.onShow)
            }
            PageCard(tr("Wachen & Fahrzeuge"), Modifier.width(STATIONS_WIDTH.dp)) {
                Stations(incidents, data.picker.city)
            }
        }
        PageCard(tr("Amtszeit"), Modifier.fillMaxWidth()) {
            TermFigures(data.counts, incidents.size)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(tr("Häufigkeit nach Art"), style = TmType.text(14.sp, FontWeight.Bold))
                    if (data.counts.term.isEmpty()) {
                        BasicText(tr("noch keine"), style = TmType.small)
                    } else {
                        KindCounts(data.counts.today, data.counts.term)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(tr("Letzte Einsätze"), style = TmType.text(14.sp, FontWeight.Bold))
                    RecentIncidents(snapshot, data.picker, data.onShow)
                }
            }
        }
    }
}

/** Die zuletzt erledigten Einsätze: Art, Straße, Dauer; ein Klick zeigt den Ort. */
@Composable
private fun RecentIncidents(
    snapshot: SimulationSnapshot,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
) {
    val finished = snapshot.finishedIncidents
    if (finished.isEmpty()) {
        BasicText(tr("noch keine"), style = TmType.small)
        return
    }
    // Simulationszeit → Spielzeit (das Spiel beginnt nicht um Mitternacht)
    val now = snapshot.gameTime
    val offset = now.day * SECONDS_PER_DAY + now.secondOfDay - snapshot.time
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(tr("Wann"), style = TmType.caption, modifier = Modifier.width(WHEN_WIDTH.dp))
        BasicText(tr("Art"), style = TmType.caption, modifier = Modifier.weight(1f))
        BasicText(tr("Ort"), style = TmType.caption, modifier = Modifier.weight(1f))
        BasicText(tr("Dauer"), style = TmType.caption)
    }
    for (incident in finished) {
        val whenText = endLabel(incident.endTime + offset, now.day)
        val street =
            picker.nearestSegment(incident.x, incident.y, STREET_RADIUS)?.let {
                picker.city.roads.segments[it]
                    .name
            }
                ?: tr("Straße ohne Namen")
        val minutes = ((incident.endTime - incident.startTime) / SECONDS_PER_MINUTE).roundToInt()
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { onShow(MapPlace(incident.x, incident.y)) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                whenText,
                style = TmType.mono(11.sp, bold = false, color = TmColors.Muted),
                modifier = Modifier.width(WHEN_WIDTH.dp),
            )
            BasicText(
                incident.kind.label,
                style = TmType.text(13.sp, FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                street,
                style = TmType.caption,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                tr("{minutes} min", "minutes" to minutes),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
            )
        }
    }
}

/** „heute 14:05“, „gestern 23:40“, „vor 3 Tagen 08:15“ aus Spielsekunden seit Mitternacht des ersten Tags. */
private fun endLabel(
    end: Double,
    today: Int,
): String {
    val daysAgo = today - (end / SECONDS_PER_DAY).toInt()
    val minutes = (end % SECONDS_PER_DAY / SECONDS_PER_MINUTE).toInt()
    val clock =
        (minutes / MINUTES_PER_HOUR).toString().padStart(2, '0') + ":" +
            (minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')
    val day =
        when (daysAgo) {
            0 -> tr("heute")
            1 -> tr("gestern")
            else -> tr("vor {days} Tagen", "days" to daysAgo)
        }
    return "$day $clock"
}

private val CardShape = RoundedCornerShape(14.dp)

@Composable
private fun PageCard(
    title: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(TmColors.Surface)
            .border(1.dp, TmColors.LineSoft, CardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(title, style = TmType.text(16.sp, FontWeight.Bold))
        content()
    }
}

private const val MAX_RUNNING = 12
private const val STATIONS_WIDTH = 480
private const val STREET_RADIUS = 60f
private const val SECONDS_PER_MINUTE = 60.0
private const val SECONDS_PER_DAY = 86_400.0
private const val MINUTES_PER_HOUR = 60
private const val WHEN_WIDTH = 110
