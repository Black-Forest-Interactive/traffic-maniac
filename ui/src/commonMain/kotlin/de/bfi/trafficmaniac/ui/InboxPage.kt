package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Assignment
import de.bfi.trafficmaniac.game.AssignmentKind
import de.bfi.trafficmaniac.game.AssignmentStatus
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameMessage
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.game.MessageKind
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.IncidentMarker
import de.bfi.trafficmaniac.ui.components.Badge
import de.bfi.trafficmaniac.ui.components.BadgeTone
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.KpiTile
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.TmButton
import de.bfi.trafficmaniac.ui.map.MapPicker
import kotlin.math.roundToInt

/** Kategorien links im Posteingang. */
internal enum class InboxCategory { ALL, EMERGENCIES, ASSIGNMENTS, DAMAGE, MESSAGES }

/** Ein Eintrag im Posteingang – Auftrag, Einsatz, Schaden oder Meldung. */
internal sealed class InboxItem(
    val key: String,
    val category: InboxCategory,
) {
    /** Wann ein Eintrag wieder als ungelesen gilt – meist mit seinem [key], gebündelte Meldungen bei jeder neuen. */
    open val unreadKey: String get() = key

    /** Auftrag – offen oder gerade erledigt (gleicher Schlüssel: Erledigt macht ihn nicht wieder ungelesen). */
    class Job(
        val assignment: Assignment,
    ) : InboxItem("a${assignment.id}", InboxCategory.ASSIGNMENTS) {
        val done: Boolean get() = assignment.status == AssignmentStatus.DONE
    }

    class Emergency(
        val incident: IncidentMarker,
        index: Int,
    ) : InboxItem("e$index:${incident.startTime}", InboxCategory.EMERGENCIES)

    /** Alle offenen Schäden als ein Eintrag – die einzelnen stehen im Bauamt. */
    class Damage(
        val reports: List<DamageReport>,
    ) : InboxItem("d${reports.size}", InboxCategory.DAMAGE) {
        val unsecured: Int get() = reports.count { it.status in UNSECURED_DAMAGE }
    }

    /** Gleichartige Meldungen (gleicher Titel) gebündelt, die neueste zuerst. */
    class Message(
        val messages: List<GameMessage>,
        title: String,
    ) : InboxItem("m:$title", InboxCategory.MESSAGES) {
        val message: GameMessage get() = messages.first()

        override val unreadKey: String get() = "$key:${messages.size}"
    }
}

private fun items(frame: TrafficFrame): List<InboxItem> {
    val game = frame.game
    val incidents = frame.current.incidents.mapIndexed { i, incident -> InboxItem.Emergency(incident, i) }
    // Offene nach Frist, darunter die gerade erledigten – so sieht man, was schon durch ist
    val jobs = (game.assignments.sortedBy { it.hoursLeft } + game.finishedAssignments).map { InboxItem.Job(it) }
    // Dringendste zuerst: ungesichert, dann nach Frist
    val damage =
        game.maintenance.reports
            .sortedWith(compareBy({ it.status !in UNSECURED_DAMAGE }, { it.hoursLeft }))
            .takeIf { it.isNotEmpty() }
            ?.let { listOf(InboxItem.Damage(it)) }
            .orEmpty()
    val messages =
        game.messages
            .asReversed()
            .groupBy { groupKey(it) }
            .map { (title, group) -> InboxItem.Message(group, title) }
    return incidents + jobs + damage + messages
}

/** Schlüssel aller Einträge – fürs Zählen der ungelesenen. */
internal fun inboxKeys(frame: TrafficFrame): Set<String> = items(frame).map { it.unreadKey }.toSet()

internal val UNSECURED_DAMAGE = setOf(DamageStatus.OPEN, DamageStatus.IGNORED)

/** Was die Einträge auslösen: Ort zeigen, zum Auftrag (Werkzeug vorgewählt), Bauamt öffnen. */
internal class InboxActions(
    val onShow: (MapPlace) -> Unit,
    val assignments: AssignmentActions,
    val onMaintenance: () -> Unit,
    val onMap: () -> Unit,
    /** Eine andere Seite öffnen (Erfolge, Kämmerei, Ordnungsamt …). */
    val onPanel: (SidePanel) -> Unit = {},
)

/** „Was“ einer Meldung – der Teil vor dem ersten Doppelpunkt, wenn er kurz genug für einen Titel ist. */
internal fun messageTitle(message: GameMessage): String {
    val colon = message.text.indexOf(": ")
    return if (colon in 1..MAX_TITLE) message.text.substring(0, colon) else message.text
}

/**
 * Wonach Meldungen gebündelt werden: der Titel ohne Ziel und Betrag – „Zu wenig Geld für Laser (1.500 €)“ und
 * „Zu wenig Geld für Blitzer (45.000 €)“ sind beide „Zu wenig Geld“.
 */
internal fun groupKey(message: GameMessage): String =
    messageTitle(message)
        .substringBefore(" (")
        .substringBefore(" für ")
        .trim()

internal fun messageDetail(message: GameMessage): String {
    val colon = message.text.indexOf(": ")
    return if (colon in 1..MAX_TITLE) message.text.substring(colon + 2) else ""
}

/**
 * Posteingang als Seite (Entwurf 2): Kategorien mit Zählern links (240 dp), Liste in der Mitte (Zeilen 72 dp mit
 * Farbstreifen nach Typ), rechts das Detail des gewählten Eintrags mit direkten Aktionen (560 dp).
 */
@Composable
internal fun InboxPage(
    frame: TrafficFrame,
    picker: MapPicker,
    actions: InboxActions,
) {
    var category by remember { mutableStateOf(InboxCategory.ALL) }
    var selected by remember { mutableStateOf<String?>(null) }
    val all = items(frame)
    val shown = all.filter { category == InboxCategory.ALL || it.category == category }
    val current = shown.firstOrNull { it.key == selected } ?: shown.firstOrNull()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(tr("Rathaus"), tr("Posteingang"), actions.onMap)
        InboxKpis(frame)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Categories(all, category) {
                category = it
                selected = null
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .card()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (shown.isEmpty()) {
                    BasicText(
                        tr("Nichts da – alles erledigt."),
                        style = TmType.small,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                for (item in shown.take(MAX_ROWS)) {
                    ItemRow(item, item == current, frame) { selected = item.key }
                }
            }
            Column(
                Modifier
                    .width(
                        DETAIL_WIDTH.dp,
                    ).fillMaxHeight()
                    .card()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                current?.let { Detail(it, frame, picker, actions) }
                    ?: BasicText(tr("Links einen Eintrag wählen."), style = TmType.small)
            }
        }
    }
}

@Composable
private fun InboxKpis(frame: TrafficFrame) {
    KpiRow {
        val unsecured =
            frame.game.maintenance.reports
                .count { it.status in UNSECURED_DAMAGE }
        KpiTile(tr("Einsätze laufend"), "${frame.current.incidents.size}")
        KpiTile(
            tr("Offene Aufträge"),
            "${frame.game.assignments.size}",
            footer =
                frame.game.assignments.maxOfOrNull { it.reward }?.let {
                    tr(
                        "bis zu +{euro}",
                        "euro" to formatEuro(it),
                    )
                },
        )
        KpiTile(
            tr("Schäden ungesichert"),
            "$unsecured",
            footer = tr("{count} offen", "count" to frame.game.maintenance.reports.size),
            accent = if (unsecured > 0) TmColors.Danger else null,
        )
        // Wie in der Liste: gleichartige Meldungen zählen einmal
        val groups =
            frame.game.messages
                .map(::groupKey)
                .distinct()
                .size
        KpiTile(
            tr("Meldungen"),
            "$groups",
            footer =
                tr("{count} einzeln", "count" to frame.game.messages.size).takeIf {
                    groups !=
                        frame.game.messages.size
                },
        )
    }
}

private val CardShape = RoundedCornerShape(14.dp)

private fun Modifier.card() = clip(CardShape).background(TmColors.Surface).border(1.dp, TmColors.LineSoft, CardShape)

@Composable
private fun Categories(
    all: List<InboxItem>,
    current: InboxCategory,
    onPick: (InboxCategory) -> Unit,
) {
    Column(
        Modifier
            .width(CATEGORY_WIDTH.dp)
            .fillMaxHeight()
            .card()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (category in InboxCategory.entries) {
            // Erledigte Aufträge stehen nur zur Info da und zählen nicht mit
            val open = all.filter { !(it is InboxItem.Job && it.done) }
            val count = if (category == InboxCategory.ALL) open.size else open.count { it.category == category }
            val active = category == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) TmColors.Ink else Color.Transparent)
                    .clickable(role = Role.Tab) { onPick(category) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    category.label,
                    style = TmType.text(13.sp, FontWeight.SemiBold, if (active) TmColors.Surface else TmColors.Ink),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    "$count",
                    style = TmType.mono(12.sp, color = if (active) TmColors.OnDarkMuted else TmColors.Muted),
                )
            }
        }
    }
}

private val InboxCategory.label: String
    get() =
        when (this) {
            InboxCategory.ALL -> tr("Alle")
            InboxCategory.EMERGENCIES -> tr("Einsätze")
            InboxCategory.ASSIGNMENTS -> tr("Aufträge")
            InboxCategory.DAMAGE -> tr("Schäden")
            InboxCategory.MESSAGES -> tr("Meldungen")
        }

/** Typzeile, Titel, Unterzeile, rechts Betrag bzw. Frist und die Farbe des Streifens. */
internal class RowText(
    val type: String,
    val title: String,
    val detail: String,
    val right: String?,
    val color: Color,
)

private fun rowText(
    item: InboxItem,
    frame: TrafficFrame,
): RowText =
    when (item) {
        is InboxItem.Job -> {
            jobRow(item)
        }

        is InboxItem.Emergency -> {
            val minutes = ((frame.current.time - item.incident.startTime) / SECONDS_PER_MINUTE).roundToInt()
            RowText(
                tr("Einsatz"),
                item.incident.kind.label,
                tr("seit {minutes} min", "minutes" to minutes),
                null,
                TmColors.Danger,
            )
        }

        is InboxItem.Damage -> {
            RowText(
                tr("Schäden"),
                tr("{count} Schäden offen", "count" to item.reports.size),
                tr("{count} ungesichert", "count" to item.unsecured),
                null,
                if (item.unsecured > 0) TmColors.Danger else TmColors.Warn,
            )
        }

        is InboxItem.Message -> {
            val m = item.message
            val count = item.messages.size
            // Gebündelt: der gemeinsame Teil („Zu wenig Geld“), einzeln der volle Titel
            val title = if (count > 1) groupKey(m) else messageTitle(m)
            RowText(
                tr("Meldung · {time}", "time" to m.time.clockText),
                if (count > 1) "$count× $title" else title,
                messageDetail(m),
                null,
                kindColor(m.kind),
            )
        }
    }

private fun kindColor(kind: MessageKind): Color =
    when (kind) {
        MessageKind.PROBLEM -> TmColors.Danger
        MessageKind.ACTION -> TmColors.Primary
        MessageKind.SUCCESS -> TmColors.Success
        MessageKind.SYSTEM -> TmColors.Muted
    }

@Composable
private fun ItemRow(
    item: InboxItem,
    selected: Boolean,
    frame: TrafficFrame,
    onClick: () -> Unit,
) {
    val text = rowText(item, frame)
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .background(if (selected) TmColors.PrimaryTint else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(text.color))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(
                text.type.uppercase(),
                style = TmType.text(10.sp, FontWeight.Bold, TmColors.Muted).copy(letterSpacing = 0.5.sp),
                maxLines = 1,
            )
            BasicText(
                text.title,
                style = TmType.text(14.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.detail.isNotEmpty()) {
                BasicText(text.detail, style = TmType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        text.right?.let {
            BasicText(
                it,
                style = TmType.mono(13.sp, color = if (it.startsWith("+")) TmColors.SuccessText else TmColors.Muted),
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
}

/** Rechte Spalte: alles zum gewählten Eintrag und was man sofort tun kann. */
@Composable
private fun Detail(
    item: InboxItem,
    frame: TrafficFrame,
    picker: MapPicker,
    actions: InboxActions,
) {
    val text = rowText(item, frame)
    BasicText(text.type.uppercase(), style = TmType.eyebrow)
    BasicText(text.title, style = TmType.display(22.sp))
    when (item) {
        is InboxItem.Job -> {
            JobDetail(item.assignment, actions)
        }

        is InboxItem.Emergency -> {
            val incident = item.incident
            BasicText(text.detail, style = TmType.small)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                incident.onScene.forEach { Badge(tr("{unit} vor Ort", "unit" to it.label), tone = BadgeTone.SUCCESS) }
                incident.onTheWay.forEach { Badge(tr("{unit} unterwegs", "unit" to it.label), tone = BadgeTone.WARN) }
            }
            incident.workProgress?.let { Meter(it, Modifier.fillMaxWidth(), height = 6.dp, color = TmColors.Danger) }
            TmButton(
                tr("Auf Karte zeigen"),
                { actions.onShow(MapPlace(incident.x, incident.y)) },
                kind = ButtonKind.PRIMARY,
            )
        }

        is InboxItem.Damage -> {
            DamageSummary(item, picker, actions)
        }

        is InboxItem.Message -> {
            MessageDetail(item, picker, actions)
        }
    }
}

/** Schäden zusammengefasst: wie viele offen und ungesichert, die drei dringendsten, „Zum Bauamt“. */
@Composable
private fun DamageSummary(
    item: InboxItem.Damage,
    picker: MapPicker,
    actions: InboxActions,
) {
    if (item.unsecured > 0) {
        BasicText(
            tr("Ungesichert: Solange kein Schild steht, droht eine Klage."),
            style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.DangerText),
        )
    }
    BasicText(tr("Am dringendsten"), style = TmType.text(14.sp, FontWeight.Bold))
    for (report in item.reports.take(URGENT_SHOWN)) {
        val place = if (report.node >= 0) picker.placeOfNode(report.node) else picker.place(report.segments)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .then(if (place != null) Modifier.clickable(role = Role.Button) { actions.onShow(place) } else Modifier)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    "${report.kind.label} · ${report.place}",
                    style = TmType.text(13.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(report.status.label, style = TmType.caption)
            }
            BasicText(
                tr("{hours} Std.", "hours" to report.hoursLeft.coerceAtLeast(0.0).roundToInt()),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
            )
        }
    }
    TmButton(tr("Zum Bauamt"), actions.onMaintenance, kind = ButtonKind.PRIMARY)
}

private const val CATEGORY_WIDTH = 240
private const val URGENT_SHOWN = 3
private const val DETAIL_WIDTH = 560
private const val ROW_HEIGHT = 72
private const val MAX_ROWS = 200
private const val MAX_TITLE = 50
private const val SECONDS_PER_MINUTE = 60.0
private const val PERCENT = 100f
