package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.SignalMarkers
import de.bfi.trafficmaniac.ui.map.MapView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration.Companion.milliseconds

private val BackgroundColor = Color(0xFF2B2D42)
private val HUD_REFRESH = 250.milliseconds

/** Wurzel der Spieloberfläche. Ohne Stadt wird ein Hinweis angezeigt, wie man sie erzeugt. */
@Composable
fun TrafficManiacApp(
    city: CityMap?,
    missingCityMessage: String = "",
    traffic: StateFlow<TrafficFrame?>? = null,
    signalMarkers: SignalMarkers? = null,
) {
    if (city != null) {
        var fps by remember { mutableStateOf(0) }
        // Die Kennzahlen reichen viermal pro Sekunde – die Karte liest den Verkehr selbst in jedem Frame
        val hudFrame by produceState<TrafficFrame?>(null, traffic) {
            while (traffic != null) {
                value = traffic.value
                delay(HUD_REFRESH)
            }
        }
        Box(Modifier.fillMaxSize()) {
            MapView(
                city = city,
                traffic = traffic?.let { flow -> { flow.value } },
                signalMarkers = signalMarkers,
                onFps = { fps = it },
            )
            hudFrame?.let { TrafficHud(it, fps, Modifier.align(Alignment.TopStart)) }
        }
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
