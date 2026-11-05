package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.ui.map.MapView

private val BackgroundColor = Color(0xFF2B2D42)

/** Wurzel der Spieloberfläche. Ohne Stadt wird ein Hinweis angezeigt, wie man sie erzeugt. */
@Composable
fun TrafficManiacApp(
    city: CityMap?,
    missingCityMessage: String = "",
) {
    if (city != null) {
        MapView(city)
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(BackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = missingCityMessage,
                style = TextStyle(color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center),
            )
        }
    }
}
