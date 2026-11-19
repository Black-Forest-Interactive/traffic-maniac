package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.map.NetworkDebug
import de.bfi.trafficmaniac.ui.map.NetworkPick
import de.bfi.trafficmaniac.ui.map.NodeKind

/**
 * Fenster zum Straßennetz-Modus (F4): Legende und die Rohdaten des angeklickten Knotens bzw. Abschnitts.
 * Abschnitte und Knoten in den Listen lassen sich anklicken, um sich durchs Netz zu hangeln.
 */
@Composable
internal fun NetworkInspector(
    debug: NetworkDebug,
    pick: NetworkPick?,
    /** Aktuelles Tempolimit des Spielers je Abschnitt (km/h). */
    speedLimits: Map<Int, Int>,
    onPick: (NetworkPick?) -> Unit,
    onShow: (Float, Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        when (pick) {
            is NetworkPick.Segment -> tr("Abschnitt S{id}", "id" to pick.id)
            is NetworkPick.Node -> tr("Knoten N{id}", "id" to pick.id)
            null -> tr("Straßennetz")
        }
    PanelScaffold(tr("Debug"), title, modifier.width(INSPECTOR_WIDTH.dp), onClose) {
        when (pick) {
            is NetworkPick.Segment -> SegmentDetails(debug, pick.id, speedLimits, onPick, onShow)
            is NetworkPick.Node -> NodeDetails(debug, pick.id, onPick, onShow)
            null -> Overview(debug)
        }
        PanelSection(divider = false) { Legend() }
    }
}

@Composable
private fun Overview(debug: NetworkDebug) {
    val roads = debug.city.roads
    PanelSection {
        Field(tr("Abschnitte"), "${roads.segments.size} (${roads.segments.count { it.passive }} " + tr("passiv") + ")")
        Field(tr("Knoten"), "${debug.usedNodes.size}")
        Field(tr("Kreuzungen"), "${debug.nodeKinds.count { it == NodeKind.JUNCTION }}")
        Field(tr("Sackgassen"), "${debug.nodeKinds.count { it == NodeKind.DEAD_END }}")
        Field(tr("Strecken"), "${debug.stretchSegments.size}")
        Field(tr("Ampel-Knoten"), "${roads.nodes.count { it.trafficSignals }}")
        BasicText(tr("Knoten oder Abschnitt anklicken, um die Daten zu sehen."), style = TmType.small)
    }
}

@Composable
private fun SegmentDetails(
    debug: NetworkDebug,
    id: Int,
    speedLimits: Map<Int, Int>,
    onPick: (NetworkPick?) -> Unit,
    onShow: (Float, Float) -> Unit,
) {
    val segment = debug.city.roads.segments[id]
    PanelSection {
        Field(tr("Name"), segment.name ?: "–")
        Field(tr("Klasse"), segment.roadClass.osmValue)
        Field(tr("Spuren vor | zurück"), "${segment.lanesForward} | ${segment.lanesBackward}")
        Field(tr("Tempo OSM"), "${segment.maxSpeedKmh} km/h")
        speedLimits[id]?.let { Field(tr("Tempolimit Spieler"), "$it km/h") }
        Field(tr("Länge"), "${formatDecimal(debug.length(id).toDouble(), 1)} m")
        Field(tr("Stützpunkte"), "${segment.geometry.pointCount}")
        val flags =
            listOfNotNull(
                tr("Einbahnstraße").takeIf { segment.isOneway },
                tr("passiv").takeIf { segment.passive },
                tr("Brücke").takeIf { segment.bridge },
            )
        if (flags.isNotEmpty()) Field(tr("Merkmale"), flags.joinToString(", "))
        segment.maxWeightTons?.let { Field(tr("max. Gewicht"), "${formatDecimal(it.toDouble(), 1)} t") }
        segment.maxWidthMeters?.let { Field(tr("max. Breite"), "${formatDecimal(it.toDouble(), 1)} m") }
        segment.maxHeightMeters?.let { Field(tr("max. Höhe"), "${formatDecimal(it.toDouble(), 1)} m") }
    }
    PanelSection(title = tr("Enden")) {
        NodeLink(debug, segment.from, tr("von"), onPick, onShow)
        NodeLink(debug, segment.to, tr("nach"), onPick, onShow)
    }
    val stretch = debug.stretchOfSegment[id]
    if (stretch >= 0) {
        val members = debug.stretchSegments[stretch]
        val length = members.sumOf { debug.length(it).toDouble() }
        PanelSection(title = tr("Strecke #{index}", "index" to stretch)) {
            Field(tr("Abschnitte"), "${members.size}")
            Field(tr("Gesamtlänge"), "${formatDecimal(length, 0)} m")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (member in members.take(MAX_LISTED)) {
                    Chip("S$member", member == id) { onPick(NetworkPick.Segment(member)) }
                }
            }
            if (members.size > MAX_LISTED) BasicText("+${members.size - MAX_LISTED}", style = TmType.small)
        }
    }
}

@Composable
private fun NodeDetails(
    debug: NetworkDebug,
    id: Int,
    onPick: (NetworkPick?) -> Unit,
    onShow: (Float, Float) -> Unit,
) {
    val node = debug.city.roads.nodes[id]
    PanelSection {
        Field(tr("Art"), kindLabel(debug.nodeKinds[id]))
        Field(tr("Ampel"), if (node.trafficSignals) tr("ja") else tr("nein"))
        Field(tr("Position"), "${formatDecimal(node.x.toDouble(), 1)} / ${formatDecimal(node.y.toDouble(), 1)} m")
        Field(tr("Endet hier eine Strecke?"), if (debug.stretches.isJunction(id)) tr("ja") else tr("nein"))
        BasicText(
            tr("Auf der Karte zeigen"),
            style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.Primary),
            modifier = Modifier.clickable(role = Role.Button) { onShow(node.x, node.y) },
        )
    }
    PanelSection(title = tr("Abschnitte am Knoten")) {
        for (segmentId in debug.segmentsAtNode[id]) {
            val segment = debug.city.roads.segments[segmentId]
            val other = if (segment.from == id) segment.to else segment.from
            val direction = if (segment.from == id) "→" else "←"
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT.dp)
                    .clickable(role = Role.Button) { onPick(NetworkPick.Segment(segmentId)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).background(debug.color(segment), CircleShape))
                BasicText("S$segmentId", style = TmType.mono(12.sp), modifier = Modifier.width(56.dp))
                BasicText(
                    (segment.name ?: segment.roadClass.osmValue) + if (segment.passive) " · " + tr("passiv") else "",
                    style = TmType.text(12.sp),
                    modifier = Modifier.weight(1f),
                )
                BasicText("$direction N$other", style = TmType.mono(12.sp, bold = false, color = TmColors.Muted))
            }
        }
    }
}

@Composable
private fun NodeLink(
    debug: NetworkDebug,
    node: Int,
    label: String,
    onPick: (NetworkPick?) -> Unit,
    onShow: (Float, Float) -> Unit,
) {
    val n = debug.city.roads.nodes[node]
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT.dp)
            .clickable(role = Role.Button) {
                onPick(NetworkPick.Node(node))
                onShow(n.x, n.y)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(label, style = TmType.small, modifier = Modifier.width(48.dp))
        BasicText("N$node", style = TmType.mono(12.sp), modifier = Modifier.width(64.dp))
        BasicText(
            kindLabel(debug.nodeKinds[node]) + if (n.trafficSignals) " · " + tr("Ampel") else "",
            style = TmType.text(12.sp),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(label, style = TmType.small, modifier = Modifier.weight(1f))
        BasicText(value, style = TmType.mono(12.sp))
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    BasicText(
        text,
        style = TmType.mono(11.sp, color = if (selected) TmColors.Surface else TmColors.Ink),
        modifier =
            Modifier
                .background(if (selected) TmColors.Ink else TmColors.Ground, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ColumnScope.Legend() {
    BasicText(tr("Legende"), style = TmType.text(13.sp, FontWeight.Bold))
    LegendRow(NetworkDebug.JUNCTION_COLOR, tr("Kreuzung – Strecke endet"))
    LegendRow(NetworkDebug.THROUGH_COLOR, tr("Durchgangsknoten (nur Knick)"))
    LegendRow(NetworkDebug.DEAD_END_COLOR, tr("Sackgasse / Kartenrand"))
    LegendRow(NetworkDebug.SIGNAL_COLOR, tr("Ampel: Ring = Knoten, Punkt = Signal der Simulation"))
    LegendRow(NetworkDebug.PASSIVE_COLOR, tr("passive Nebenstraße (gestrichelt)"))
    BasicText(
        tr("Farbe der Linie = Strecke · Pfeile = Fahrtrichtung · „S12 2|1“ = Abschnitt, Spuren vor|zurück"),
        style = TmType.caption,
    )
}

@Composable
private fun LegendRow(
    color: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape).border(1.dp, TmColors.Ink, CircleShape))
        BasicText(text, style = TmType.text(12.sp))
    }
}

@Composable
private fun kindLabel(kind: NodeKind): String =
    when (kind) {
        NodeKind.THROUGH -> tr("Durchgangsknoten")
        NodeKind.JUNCTION -> tr("Kreuzung")
        NodeKind.DEAD_END -> tr("Sackgasse")
        NodeKind.PASSIVE -> tr("nur Nebenstraßen")
    }

private const val INSPECTOR_WIDTH = 340
private const val ROW_HEIGHT = 32
private const val MAX_LISTED = 5
