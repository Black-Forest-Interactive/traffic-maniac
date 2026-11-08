package de.bfi.trafficmaniac.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import de.bfi.trafficmaniac.city.CityMap

/** Die ganze Stadt als kleines Vorschaubild (z. B. für Spielstände im Menü) – ohne Beschriftungen und Verkehr. */
@Composable
fun CityThumbnail(
    city: CityMap,
    modifier: Modifier = Modifier,
) {
    val layers = remember(city) { MapLayers(city) }
    Canvas(modifier.background(MapStyle.land)) {
        drawStaticMap(
            layers,
            Camera.fit(city.bounds, size.width, size.height),
            showStaticSignals = false,
            textMeasurer = null,
        )
    }
}
