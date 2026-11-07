package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlin.math.roundToInt

/**
 * Grafik [name] als Icon in der Oberfläche, [size] breit (Höhe nach Seitenverhältnis). Gibt es sie nicht, erscheint
 * [fallback] (oder nichts). Liefert, ob die Grafik gezeigt wurde.
 */
@Composable
fun SpriteIcon(
    sprites: SpriteSource,
    name: String,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
) {
    val pixels = with(LocalDensity.current) { size.toPx() }.roundToInt().coerceAtLeast(1)
    val image = remember(sprites, name, pixels) { sprites.image(name, pixels) }
    if (image == null) {
        fallback()
        return
    }
    val height = size * (image.height.toFloat() / image.width)
    Image(image, contentDescription = null, modifier = modifier.size(size, height))
}
