package de.bfi.trafficmaniac.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.Highlight
import de.bfi.trafficmaniac.game.HighlightKind
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Einblendungen oben in der Mitte (v0.9 Paket 3): Errungenschaft groß mit Konfetti, Nachrichten mittel, Warnungen rot.
 * Jede Einblendung erscheint genau einmal (nach [Highlight.id]) und bleibt [SHOW_MILLIS] echte Zeit stehen – auch bei
 * 64× Zeitraffer. Höchstens [MAX_VISIBLE] gleichzeitig, weitere warten. Ein Klick springt zum Ort, falls es einen gibt.
 * Was beim ersten Bild schon da war (z. B. nach dem Laden), wird nicht nachgeholt.
 */
@Composable
internal fun HighlightToasts(
    highlights: List<Highlight>,
    onPlace: (MapPlace) -> Unit,
    modifier: Modifier = Modifier,
    /** Für Screenshots: auch zeigen, was beim ersten Bild schon da ist. */
    showExisting: Boolean = false,
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
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (highlight in visible) {
            key(highlight.id) {
                LaunchedEffect(Unit) {
                    delay(SHOW_MILLIS.milliseconds)
                    visible.remove(highlight)
                }
                Toast(highlight) { highlight.place?.let(onPlace) }
            }
        }
    }
}

@Composable
private fun Toast(
    highlight: Highlight,
    onClick: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = slideInVertically { -it } + fadeIn(), exit = fadeOut()) {
        val (background, accent, text) =
            when (highlight.kind) {
                HighlightKind.BIG -> Triple(ComicTheme.paper, TmColors.Active, ComicTheme.ink)
                HighlightKind.MEDIUM -> Triple(TmColors.Ink, TmColors.Success, TmColors.OnDark)
                HighlightKind.WARNING -> Triple(TmColors.Danger, TmColors.Surface, TmColors.Surface)
            }
        val big = highlight.kind == HighlightKind.BIG
        Box(
            Modifier
                .widthIn(min = if (big) BIG_MIN_WIDTH.dp else 0.dp, max = MAX_WIDTH.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(background, RoundedCornerShape(12.dp))
                .then(if (big) Modifier.border(3.dp, ComicTheme.ink, RoundedCornerShape(12.dp)) else Modifier)
                .clickable(enabled = highlight.place != null, onClick = onClick),
        ) {
            if (big) Confetti(Modifier.matchParentSize())
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = if (big) 12.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ToastBadge(highlight.kind, accent)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        highlight.title,
                        style =
                            if (big) {
                                TmType.display(18.sp, text)
                            } else {
                                TmType.text(13.sp, FontWeight.Bold, text)
                            },
                    )
                    if (highlight.text.isNotEmpty()) {
                        BasicText(highlight.text, style = TmType.text(12.sp, color = text.copy(alpha = TEXT_ALPHA)))
                    }
                }
            }
        }
    }
}

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
private const val SHOW_MILLIS = 4_000
private const val MAX_VISIBLE = 3
private const val BIG_MIN_WIDTH = 340
private const val MAX_WIDTH = 460
private const val TEXT_ALPHA = 0.85f
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

/** Runder Knopf links in der Einblendung: Siegel, Haken oder Hinweis. */
@Composable
private fun ToastBadge(
    kind: HighlightKind,
    accent: Color,
) {
    val big = kind == HighlightKind.BIG
    Box(
        Modifier.size(if (big) 36.dp else 24.dp).background(accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val icon =
            when (kind) {
                HighlightKind.BIG -> Icons.Award
                HighlightKind.MEDIUM -> Icons.Check
                HighlightKind.WARNING -> Icons.Info
            }
        val tint = if (kind == HighlightKind.WARNING) TmColors.Danger else TmColors.Ink
        Icon(icon, null, tint = tint, size = if (big) 20.dp else 14.dp)
    }
}
