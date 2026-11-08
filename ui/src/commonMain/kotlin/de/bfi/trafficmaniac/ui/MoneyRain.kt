package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private val Gold = Color(0xFFFFC300)
private val GoldDark = Color(0xFFB8860B)
private const val MAX_COINS = 40
private const val EUROS_PER_COIN = 100L
private const val MAX_NEW_COINS = 12
private const val FALL_SECONDS = 1.6f
private const val NANOS_PER_SECOND = 1e9f
private const val COIN_RADIUS = 7f
private const val GRAVITY = 380f
private const val SWAY = 6f
private const val SWAY_SPEED = 7f

/** Eine fallende Münze: Startpunkt, Startzeit und Drehphase. */
private class Coin(
    val x: Float,
    val bornNanos: Long,
    val phase: Float,
)

/**
 * Geldregen: Steigen die Einnahmen ([income]), fallen goldene Münzen über die Kassenanzeige –
 * eine je 100 €, höchstens ein Dutzend auf einmal.
 */
@Composable
fun MoneyRain(
    income: Long,
    modifier: Modifier = Modifier,
    sprites: SpriteSource = NoSprites,
) {
    val coinImage = remember(sprites) { sprites.image("coin", COIN_PIXELS) }
    val coins = remember { ArrayList<Coin>() }
    val lastIncome = remember { longArrayOf(-1) }
    val now = remember { mutableStateOf(0L) }
    val random = remember { Random(1) }
    LaunchedEffect(income) {
        val before = lastIncome[0]
        lastIncome[0] = income
        if (before < 0 || income <= before) return@LaunchedEffect
        val fresh = ((income - before) / EUROS_PER_COIN + 1).coerceAtMost(MAX_NEW_COINS.toLong()).toInt()
        val start = withFrameNanos { it }
        repeat(fresh) { coins += Coin(random.nextFloat(), start + it * STAGGER_NANOS, random.nextFloat() * SWAY) }
        while (coins.size > MAX_COINS) coins.removeAt(0)
        // Animieren, bis alle Münzen unten sind
        while (coins.isNotEmpty()) {
            withFrameNanos { frame ->
                now.value = frame
                coins.removeAll { (frame - it.bornNanos) / NANOS_PER_SECOND > FALL_SECONDS }
            }
        }
        now.value = 0L
    }
    Canvas(modifier) {
        val frame = now.value
        if (frame == 0L) return@Canvas
        for (coin in coins) {
            val t = (frame - coin.bornNanos) / NANOS_PER_SECOND
            if (t < 0) continue
            val y = GRAVITY * t * t / 2
            val x = coin.x * size.width + sin(t * SWAY_SPEED + coin.phase) * SWAY
            val alpha = (1 - t / FALL_SECONDS).coerceIn(0f, 1f)
            // Drehende Münze: mal von vorn, mal hochkant
            val squash = abs(sin(t * SWAY_SPEED + coin.phase))
            val center = Offset(x, y)
            if (coinImage != null) {
                val width = (2 * COIN_RADIUS * squash).toInt().coerceAtLeast(1)
                drawImage(
                    coinImage,
                    dstOffset = IntOffset((x - width / 2f).toInt(), (y - COIN_RADIUS).toInt()),
                    dstSize = IntSize(width, (2 * COIN_RADIUS).toInt()),
                    alpha = alpha,
                )
                continue
            }
            drawOval(
                Gold.copy(alpha = alpha),
                topLeft = center - Offset(COIN_RADIUS * squash, COIN_RADIUS),
                size = Size(2 * COIN_RADIUS * squash, 2 * COIN_RADIUS),
            )
            drawOval(
                GoldDark.copy(alpha = alpha),
                topLeft = center - Offset(COIN_RADIUS * squash, COIN_RADIUS),
                size = Size(2 * COIN_RADIUS * squash, 2 * COIN_RADIUS),
                style = Stroke(2f),
            )
        }
    }
}

private const val STAGGER_NANOS = 60_000_000L
private const val COIN_PIXELS = 32
