package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Highlight
import de.bfi.trafficmaniac.game.HighlightKind
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon
import kotlin.math.sin
import kotlin.random.Random

/**
 * Meldungen unten links (Entwurf 2): höchstens [MAX_VISIBLE] gestapelt, 380 dp breit, ein Stil für alle – weiße Karte
 * mit Farbstreifen und rundem Icon nach Typ (Problem rot, Neues blau, Erfolg grün), Titel, Unterzeile, optional
 * „Ansehen“, ✕ und ein dünner Zeitbalken. Jede Meldung erscheint genau einmal (nach [Highlight.id]) und bleibt
 * [SHOW_MILLIS] echte Zeit stehen – auch bei 64× Zeitraffer –, solange die Maus darauf steht, länger. Was beim ersten
 * Bild schon da war (z. B. nach dem Laden), wird nicht nachgeholt. Alles steht zusätzlich im Posteingang.
 */
@Composable
internal fun HighlightToasts(
    highlights: List<Highlight>,
    onPlace: (MapPlace) -> Unit,
    modifier: Modifier = Modifier,
    /** Für Screenshots: auch zeigen, was beim ersten Bild schon da ist. */
    showExisting: Boolean = false,
    /** Link unter dem Stapel; `null` = keiner. */
    onInbox: (() -> Unit)? = null,
) {
    var seen by remember { mutableStateOf<Int?>(if (showExisting) 0 else null) }
    val waiting = remember { mutableStateListOf<Highlight>() }
    val visible = remember { mutableStateListOf<Highlight>() }
    val newest = highlights.maxOfOrNull { it.id } ?: 0
    LaunchedEffect(newest) {
        val last = seen
        if (last == null) {
            seen = newest
            return@LaunchedEffect
        }
        waiting += highlights.filter { it.id > last }.sortedBy { it.id }
        seen = newest
    }
    LaunchedEffect(waiting.size, visible.size) {
        while (visible.size < MAX_VISIBLE && waiting.isNotEmpty()) visible += waiting.removeAt(0)
    }
    Column(modifier.width(TOAST_WIDTH.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (highlight in visible) {
            key(highlight.id) {
                Toast(highlight, { highlight.place?.let(onPlace) }) { visible.remove(highlight) }
            }
        }
        if (visible.isNotEmpty() && onInbox != null) {
            BasicText(
                tr("Alle Meldungen im Posteingang →"),
                style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Primary),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TmColors.Surface.copy(alpha = LINK_ALPHA))
                        .clickable(role = Role.Button, onClick = onInbox)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Farbe und Icon nach Typ: Erfolg grün, Neues blau, Problem rot. */
private fun look(kind: HighlightKind): Pair<Color, TmIcon> =
    when (kind) {
        HighlightKind.BIG -> TmColors.Success to Icons.Award
        HighlightKind.MEDIUM -> TmColors.Primary to Icons.Info
        HighlightKind.WARNING -> TmColors.Danger to Icons.Siren
    }

@Composable
private fun Toast(
    highlight: Highlight,
    onShow: () -> Unit,
    onDone: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val elapsed = rememberElapsed(hovered, onDone)
    val (color, icon) = look(highlight.kind)
    // Titel nicht in der Unterzeile wiederholen
    val detail = highlight.text.removePrefix(highlight.title).trimStart(' ', ':', '–', '-')
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, ToastShape, spotColor = ToastShadow)
            .clip(ToastShape)
            .background(TmColors.Surface)
            .hoverable(interaction),
    ) {
        if (highlight.kind == HighlightKind.BIG) Confetti(Modifier.matchParentSize())
        ToastContent(highlight, color, icon, detail, onShow, onDone)
        // Zeitbalken: läuft ab, solange die Maus nicht darauf steht
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(1f - elapsed.toFloat() / SHOW_MILLIS)
                .height(2.dp)
                .background(color.copy(alpha = BAR_ALPHA)),
        )
    }
}

/** Echte Zeit seit dem Erscheinen in ms (läuft nicht, solange [paused]); danach [onDone]. */
@Composable
private fun rememberElapsed(
    paused: Boolean,
    onDone: () -> Unit,
): Long {
    var elapsed by remember { mutableStateOf(0L) }
    val pausedNow by rememberUpdatedState(paused)
    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (elapsed < SHOW_MILLIS) {
            withFrameMillis { now ->
                if (!pausedNow) elapsed += now - last
                last = now
            }
        }
        onDone()
    }
    return elapsed
}

/** Streifen, Icon, Titel und Unterzeile, „Ansehen“ und ✕. */
@Composable
private fun ToastContent(
    highlight: Highlight,
    color: Color,
    icon: TmIcon,
    detail: String,
    onShow: () -> Unit,
    onDone: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Box(
            Modifier.padding(start = 12.dp).size(32.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = TmColors.Surface, size = 16.dp)
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                highlight.title,
                style = TmType.text(13.sp, FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                BasicText(
                    detail,
                    style = TmType.text(12.sp, color = TmColors.Muted),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (highlight.place != null) {
            BasicText(
                tr("Ansehen"),
                style = TmType.text(12.sp, FontWeight.SemiBold, TmColors.Primary),
                modifier =
                    Modifier
                        .clip(
                            RoundedCornerShape(6.dp),
                        ).clickable(role = Role.Button, onClick = onShow)
                        .padding(6.dp),
            )
        }
        Box(
            Modifier
                .padding(
                    end = 6.dp,
                ).size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Close, tr("Meldung schließen"), tint = TmColors.Muted, size = 14.dp)
        }
    }
}

private val ToastShape = RoundedCornerShape(12.dp)
private val ToastShadow = TmColors.Ink.copy(alpha = 0.25f)

/** Ein paar Sekunden fallendes Konfetti hinter einer Errungenschaft. */
@Composable
private fun Confetti(modifier: Modifier) {
    val pieces =
        remember { List(CONFETTI) { Piece(Random.nextFloat(), Random.nextFloat(), Random.nextInt(COLORS.size)) } }
    var seconds by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (seconds < CONFETTI_SECONDS) {
            withFrameNanos { seconds = (it - start) / NANOS_PER_SECOND }
        }
    }
    Canvas(modifier) {
        if (seconds >= CONFETTI_SECONDS) return@Canvas
        for (piece in pieces) {
            val y = (piece.start * size.height * HALF + seconds * FALL_SPEED * size.height) % size.height
            val x = piece.x * size.width + sin(seconds * SWAY_SPEED + piece.x * SWAY_PHASE) * SWAY
            rotate(seconds * SPIN + piece.x * FULL_TURN, Offset(x, y)) {
                drawRect(COLORS[piece.color], Offset(x, y), Size(PIECE_WIDTH, PIECE_HEIGHT), alpha = CONFETTI_ALPHA)
            }
        }
    }
}

private class Piece(
    val x: Float,
    val start: Float,
    val color: Int,
)

private val COLORS = listOf(Color(0xFFFFC300), Color(0xFFE63946), Color(0xFF1D5FD1), Color(0xFF2A9D8F))
private const val SHOW_MILLIS = 6_000L
private const val TOAST_WIDTH = 380
private const val BAR_ALPHA = 0.6f
private const val LINK_ALPHA = 0.9f
private const val MAX_VISIBLE = 3
private const val CONFETTI = 24
private const val CONFETTI_ALPHA = 0.45f
private const val CONFETTI_SECONDS = 2.5f
private const val NANOS_PER_SECOND = 1e9f
private const val HALF = 0.5f
private const val FALL_SPEED = 0.6f
private const val SWAY_SPEED = 6f
private const val SWAY_PHASE = 10f
private const val SWAY = 6f
private const val SPIN = 240f
private const val FULL_TURN = 360f
private const val PIECE_WIDTH = 6f
private const val PIECE_HEIGHT = 10f
