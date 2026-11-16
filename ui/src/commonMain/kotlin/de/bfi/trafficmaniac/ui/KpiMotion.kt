package de.bfi.trafficmaniac.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import de.bfi.trafficmaniac.game.formatEuro
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * Die Kasse zählt zum neuen Stand hoch (statt zu springen) und leuchtet kurz grün bei Einnahmen, rot bei Ausgaben.
 * [style] gibt Schrift und Grundfarbe vor.
 */
@Composable
internal fun CountingEuro(
    value: Long,
    style: TextStyle,
) {
    val shown by animateFloatAsState(value.toFloat(), tween(COUNT_MILLIS), label = "cash")
    var previous by remember { mutableStateOf(value) }
    var flash by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(value) {
        if (value != previous) {
            flash = if (value > previous) TmColors.Success else TmColors.DangerText
            previous = value
            delay(FLASH_MILLIS.toLong())
            flash = null
        }
    }
    val color by animateColorAsState(flash ?: style.color, tween(FLASH_FADE_MILLIS), label = "cashColor")
    BasicText(formatEuro(shown.roundToLong()), style = style.copy(color = color))
}

/** Nach einer Änderung von [value] kurz „▲ 2“ bzw. „▼ 1“ zeigen (grün, wenn [upIsGood] und es steigt). */
@Composable
internal fun DeltaArrow(
    value: Int,
    upIsGood: Boolean,
    style: TextStyle,
) {
    var previous by remember { mutableStateOf(value) }
    var delta by remember { mutableStateOf(0) }
    LaunchedEffect(value) {
        if (value != previous) {
            delta = value - previous
            previous = value
            delay(ARROW_MILLIS.toLong())
            delta = 0
        }
    }
    if (delta == 0) return
    val good = (delta > 0) == upIsGood
    val text = if (delta > 0) "▲ $delta" else "▼ ${-delta}"
    BasicText(text, style = style.copy(color = if (good) TmColors.Success else TmColors.DangerText))
}

private const val COUNT_MILLIS = 600
private const val FLASH_MILLIS = 700
private const val FLASH_FADE_MILLIS = 400
private const val ARROW_MILLIS = 2_500
