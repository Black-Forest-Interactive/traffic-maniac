package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.sim.TransitLineStatus
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon
import de.bfi.trafficmaniac.ui.map.EnvironmentLayer
import de.bfi.trafficmaniac.ui.map.MapExtras
import de.bfi.trafficmaniac.ui.map.MapStyle
import de.bfi.trafficmaniac.ui.map.TransitStyle
import de.bfi.trafficmaniac.ui.map.lineColor

/** Ebenen der Kartenansicht (Entwurf 2), je mit Legende: Titel und Farbverlauf mit drei Beschriftungen. */
internal enum class MapLayerKind(
    private val labelText: String,
    val icon: TmIcon,
    private val legendText: String,
    private val low: String,
    private val mid: String,
    private val high: String,
    val colors: List<Color>,
) {
    FLOW(
        translatable("Verkehrsfluss"),
        Icons.Gauge,
        translatable("Verkehrsfluss (Tempo)"),
        translatable("frei"),
        translatable("zäh"),
        translatable("Stau"),
        listOf(MapStyle.flowFree, Color(0xFFF2C14E), MapStyle.flowJam),
    ),
    DENSITY(
        translatable("Verkehrsdichte"),
        Icons.Waves,
        translatable("Verkehrsdichte (Kfz/Std.)"),
        translatable("leer"),
        "600",
        "1.200+",
        listOf(Color(0xFFEEF1F8), Color(0xFF8FA9CC), Color(0xFF22355E)),
    ),
    TRANSIT(
        translatable("Buslinien"),
        Icons.Bus,
        translatable("Buslinien · Verspätung"),
        translatable("pünktlich"),
        translatable("+1–3 min"),
        translatable("über +3"),
        listOf(TmColors.Success, TmColors.Warn, TmColors.Danger),
    ),
    CONDITION(
        translatable("Straßenzustand"),
        Icons.Barrier,
        translatable("Straßenzustand"),
        translatable("kaputt"),
        translatable("mäßig"),
        translatable("gut"),
        listOf(MapStyle.conditionBad, MapStyle.conditionFair, MapStyle.conditionGood),
    ),
    MOODS(
        translatable("Zufriedenheit"),
        Icons.Users,
        translatable("Zufriedenheit"),
        translatable("sauer"),
        translatable("geht so"),
        translatable("zufrieden"),
        listOf(MapStyle.moodBad, Color(0xFFE3E8ED), MapStyle.moodGood),
    ),
    ENVIRONMENT(
        translatable("Umwelt"),
        Icons.Layers,
        translatable("Umwelt"),
        translatable("sauber"),
        translatable("belastet"),
        translatable("dicke Luft"),
        listOf(Color(0xFFE8EEF6), Color(0xFF8FA9CC), Color(0xFF33507A)),
    ),
    MONEY(
        translatable("Geld"),
        Icons.Euro,
        translatable("Einnahmen je Blitzer"),
        "0 €",
        "",
        translatable("viel"),
        listOf(Color(0xFFE3E8ED), Color(0xFF8FB4EA), Color(0xFF2F6BD0)),
    ),
    ;

    val label: String get() = tr(labelText)
    val legend: String get() = tr(legendText)
    val marks: List<String> get() = listOf(low, mid, high).map { if (it.isEmpty()) it else tr(it) }
}

/** Gewählte Kartenansicht, Umwelt-Unterebene (Lärm, Luft, CO₂) und was eingeblendet ist. */
internal class MapToggles {
    var layer by mutableStateOf(MapLayerKind.FLOW)
    var environment by mutableStateOf(EnvironmentLayer.AIR)
    var signs by mutableStateOf(true)
    var cameras by mutableStateOf(true)
    var incidents by mutableStateOf(true)
    var assignments by mutableStateOf(true)
    var citizenMoods by mutableStateOf(false)

    /** Buslinien nach Verspätung statt in Linienfarbe; hervorgehobene Linie (Nummer). */
    var transitByDelay by mutableStateOf(false)
    var transitHighlight by mutableStateOf<String?>(null)

    val showMoods: Boolean get() = layer == MapLayerKind.MOODS || citizenMoods
    val showCondition: Boolean get() = layer == MapLayerKind.CONDITION
    val environmentLayer: EnvironmentLayer
        get() = if (layer == MapLayerKind.ENVIRONMENT) environment else EnvironmentLayer.NONE

    val extras: MapExtras
        get() =
            MapExtras(
                density = layer == MapLayerKind.DENSITY,
                transit = layer == MapLayerKind.TRANSIT,
                money = layer == MapLayerKind.MONEY,
                signs = signs,
                cameras = cameras,
                incidents = incidents,
                assignments = assignments,
                transitStyle = TransitStyle(transitByDelay, transitHighlight),
            )

    /** „Umwelt“ ein zweites Mal gewählt: nächste Umwelt-Ebene. */
    fun select(choice: MapLayerKind) {
        if (choice == MapLayerKind.ENVIRONMENT && layer == MapLayerKind.ENVIRONMENT) {
            val layers = EnvironmentLayer.entries.drop(1)
            environment = layers[(layers.indexOf(environment) + 1) % layers.size]
        }
        layer = choice
    }
}

private val Shape = RoundedCornerShape(12.dp)
private val ShadowColor = TmColors.Ink.copy(alpha = 0.3f)
private val BoxShape = RoundedCornerShape(14.dp)

/** Per Finger: Umschalter der Kartenansicht oben mittig, dunkles Segment, gewählt = weiß. */
@Composable
internal fun MapViewSwitcher(
    toggles: MapToggles,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .shadow(8.dp, Shape, spotColor = ShadowColor)
            .background(TmColors.Ink, Shape)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (layer in MapLayerKind.entries) {
            val chosen = toggles.layer == layer
            val label = if (layer == MapLayerKind.ENVIRONMENT) toggles.environment.label else layer.label
            Box(
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (chosen) TmColors.Surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { toggles.select(layer) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label,
                    style = TmType.text(13.sp, FontWeight.SemiBold, if (chosen) TmColors.Ink else TmColors.OnDark),
                )
            }
        }
    }
}

/**
 * Kartenansicht oben links (272 dp, einklappbar): die Ebenen als Zeilen (38 dp, Icon, Name), darunter die Legende
 * der aktiven Ebene und aufklappbar „Einblenden“ (Schilder & Ampeln, Blitzer & Kontrollen, Unfälle & Schäden,
 * Aufträge, Bürger-Stimmung).
 */
@Composable
internal fun MapViewPanel(
    toggles: MapToggles,
    modifier: Modifier = Modifier,
    /** Buslinien für die Legende der Ebene „Buslinien“. */
    lines: List<TransitLineStatus> = emptyList(),
) {
    var open by remember { mutableStateOf(true) }
    Column(
        modifier
            .width(VIEW_WIDTH.dp)
            .shadow(8.dp, BoxShape, spotColor = ShadowColor)
            .background(TmColors.Surface, BoxShape)
            .clip(BoxShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                    open = !open
                }.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(tr("Kartenansicht").uppercase(), style = TmType.eyebrow, modifier = Modifier.weight(1f))
            if (!open) {
                BasicText(
                    toggles.layer.label,
                    style = TmType.text(12.sp, FontWeight.SemiBold),
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Icon(
                if (open) Icons.ChevronUp else Icons.ChevronDown,
                tr("Ein- und ausklappen"),
                tint = TmColors.Muted,
                size = 16.dp,
            )
        }
        if (!open) return@Column
        Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (layer in MapLayerKind.entries) LayerRow(layer, toggles)
        }
        if (toggles.layer == MapLayerKind.TRANSIT) TransitLegend(toggles, lines) else Legend(toggles)
        Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
        ShowOptions(toggles)
    }
}

/** Aufklappbar „Einblenden“: was außer der Ebene auf der Karte steht. */
@Composable
private fun ShowOptions(toggles: MapToggles) {
    var showOptions by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                showOptions = !showOptions
            }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shown =
            listOf(
                toggles.signs,
                toggles.cameras,
                toggles.incidents,
                toggles.assignments,
                toggles.citizenMoods,
            ).count {
                it
            }
        BasicText(tr("Einblenden"), style = TmType.text(13.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        BasicText(
            tr("{shown} von 5", "shown" to shown),
            style = TmType.caption,
            modifier = Modifier.padding(end = 6.dp),
        )
        Icon(if (showOptions) Icons.ChevronUp else Icons.ChevronDown, null, tint = TmColors.Muted, size = 16.dp)
    }
    if (showOptions) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            ShowRow(tr("Schilder & Ampeln"), toggles.signs) { toggles.signs = it }
            ShowRow(tr("Blitzer & Kontrollen"), toggles.cameras) { toggles.cameras = it }
            ShowRow(tr("Unfälle & Schäden"), toggles.incidents) { toggles.incidents = it }
            ShowRow(tr("Aufträge"), toggles.assignments) { toggles.assignments = it }
            ShowRow(tr("Bürger-Stimmung"), toggles.citizenMoods) { toggles.citizenMoods = it }
        }
    }
}

@Composable
private fun LayerRow(
    layer: MapLayerKind,
    toggles: MapToggles,
) {
    val chosen = toggles.layer == layer
    val color = if (chosen) TmColors.Surface else TmColors.Ink
    Row(
        Modifier
            .fillMaxWidth()
            .height(LAYER_ROW.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (chosen) TmColors.Ink else Color.Transparent)
            .clickable(role = Role.RadioButton) { toggles.select(layer) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(layer.icon, null, tint = color, size = 18.dp)
        BasicText(layer.label, style = TmType.text(13.sp, FontWeight.SemiBold, color), modifier = Modifier.weight(1f))
        if (layer == MapLayerKind.ENVIRONMENT) {
            BasicText(
                toggles.environment.label,
                style = TmType.mono(10.sp, bold = false, color = if (chosen) TmColors.OnDarkMuted else TmColors.Muted),
            )
        }
    }
}

/**
 * Legende der Buslinien: Färbung umschalten (nach Linie / nach Verspätung) und die Linien als Liste – ein Klick hebt
 * eine Linie hervor, alle anderen werden grau; noch ein Klick nimmt die Hervorhebung zurück.
 */
@Composable
private fun TransitLegend(
    toggles: MapToggles,
    lines: List<TransitLineStatus>,
) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TransitColoring(toggles)
        val byRef = lines.groupBy { it.ref }
        val refs = byRef.keys.sortedWith(compareBy({ it.length }, { it }))
        Column(Modifier.heightIn(max = LINE_LIST_HEIGHT.dp).verticalScroll(rememberScrollState())) {
            for (ref in refs) {
                val line = byRef.getValue(ref).first()
                val chosen = toggles.transitHighlight == ref
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (chosen) TmColors.PrimaryTint else Color.Transparent)
                        .clickable(role = Role.Button) { toggles.transitHighlight = if (chosen) null else ref }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineBadge(ref, lineColor(line))
                    BasicText(
                        destination(line.name),
                        style = TmType.text(12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Färbung der Buslinien umschalten; nach Verspätung mit Farbverlauf. */
@Composable
private fun TransitColoring(toggles: MapToggles) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(TmColors.Ground)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((byDelay, label) in listOf(false to tr("nach Linie"), true to tr("nach Verspätung"))) {
            val chosen = toggles.transitByDelay == byDelay
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (chosen) TmColors.Surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { toggles.transitByDelay = byDelay },
                contentAlignment = Alignment.Center,
            ) { BasicText(label, style = TmType.text(12.sp, FontWeight.SemiBold)) }
        }
    }
    if (toggles.transitByDelay) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    8.dp,
                ).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(MapLayerKind.TRANSIT.colors)),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (mark in MapLayerKind.TRANSIT.marks) BasicText(mark, style = TmType.caption)
        }
    }
}

/** Legende der aktiven Ebene: Titel, Farbverlauf, drei Beschriftungen. */
@Composable
private fun Legend(toggles: MapToggles) {
    val layer = toggles.layer
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val title =
            if (layer ==
                MapLayerKind.ENVIRONMENT
            ) {
                tr("Umwelt: {layer}", "layer" to toggles.environment.label)
            } else {
                layer.legend
            }
        BasicText(title, style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Muted))
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    8.dp,
                ).clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(layer.colors)),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (mark in layer.marks) BasicText(mark, style = TmType.caption)
        }
        if (layer == MapLayerKind.ENVIRONMENT) {
            BasicText(tr("„Umwelt“ noch einmal: Lärm · Luft · CO₂"), style = TmType.caption)
        }
    }
}

@Composable
private fun ShowRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) TmColors.Primary else TmColors.Surface)
                .border(1.5.dp, if (checked) TmColors.Primary else TmColors.Line, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Check, null, tint = TmColors.Surface, size = 14.dp)
        }
        BasicText(label, style = TmType.text(13.sp))
    }
}

/** Zoom-Knöpfe rechts mittig. */
@Composable
internal fun ZoomControls(
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.shadow(8.dp, Shape, spotColor = ShadowColor).background(TmColors.Ink, Shape).clip(Shape)) {
        ZoomButton(Icons.Plus, tr("Hineinzoomen")) { onZoom(ZOOM_STEP) }
        Box(Modifier.width(44.dp).height(1.dp).background(TmColors.LineDark))
        ZoomButton(Icons.Minus, tr("Herauszoomen")) { onZoom(1 / ZOOM_STEP) }
    }
}

@Composable
private fun ZoomButton(
    icon: de.bfi.trafficmaniac.ui.components.TmIcon,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(44.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = TmColors.OnDark, size = 20.dp) }
}

private const val ZOOM_STEP = 1.6f
private const val VIEW_WIDTH = 272
private const val LAYER_ROW = 38
private const val LINE_LIST_HEIGHT = 220
