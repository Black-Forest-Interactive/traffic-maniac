package de.bfi.trafficmaniac.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.bfi.trafficmaniac.game.GameInfo
import de.bfi.trafficmaniac.ui.TrafficManiacApp

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = GameInfo.TITLE) {
            TrafficManiacApp()
        }
    }
