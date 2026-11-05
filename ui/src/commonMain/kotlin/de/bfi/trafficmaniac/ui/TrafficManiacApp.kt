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
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameInfo

private val BackgroundColor = Color(0xFF2B2D42)

/** Wurzel der Spieloberfläche. Vorerst nur ein Platzhalter bis zur Karte in M1. */
@Composable
fun TrafficManiacApp() {
    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "${GameInfo.TITLE} ${GameInfo.VERSION}",
            style = TextStyle(color = Color.White, fontSize = 32.sp),
        )
    }
}
