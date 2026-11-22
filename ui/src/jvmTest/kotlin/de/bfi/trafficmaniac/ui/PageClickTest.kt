package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Knöpfe auf einer Seite über der Karte: Ein Klick zählt auch, wenn die Maus beim Drücken ein wenig wackelt, und die
 * Karte darunter bekommt ihn nicht.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PageClickTest {
    @Test
    fun aClickWithAWobblyMouseStillCounts() {
        var clicks = 0
        var mapClicks = 0
        val scene =
            ImageComposeScene(400, 400) {
                Box(Modifier.fillMaxSize()) {
                    // Die Karte darunter
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { mapClicks++ } })
                    // Die Seite darüber mit einem Knopf
                    Box(Modifier.fillMaxSize().blockMapBelow()) {
                        Box(Modifier.size(100.dp).clickable { clicks++ })
                    }
                }
            }
        try {
            scene.render()
            repeat(CLICKS) { i ->
                scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 50f))
                // Ein Pixel Bewegung zwischen Drücken und Loslassen – passiert beim schnellen Klicken ständig
                scene.sendPointerEvent(PointerEventType.Move, Offset(51f, 50f + i))
                scene.sendPointerEvent(PointerEventType.Release, Offset(51f, 50f + i))
                scene.render()
            }
            // Daneben: weder Knopf noch Karte
            scene.sendPointerEvent(PointerEventType.Press, Offset(300f, 300f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(300f, 300f))
            scene.render()
        } finally {
            scene.close()
        }
        assertEquals(CLICKS, clicks, "Klicks auf den Knopf")
        assertEquals(0, mapClicks, "Klicks, die zur Karte durchgehen")
    }

    private companion object {
        const val CLICKS = 5
    }
}
