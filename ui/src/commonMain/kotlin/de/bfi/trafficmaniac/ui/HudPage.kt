package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.LocalPage
import de.bfi.trafficmaniac.ui.components.PageMode

/**
 * Vollbildseite anstelle der Karte (alles außer „Karte“ in der Leiste): Kopf mit „Zur Karte M“, Kennzahlen, Inhalt
 * über die ganze Breite. Die Karte bleibt darunter erhalten – Zoom und Ausschnitt sind beim Zurückkommen wie
 * vorher –, bekommt aber keine Klicks.
 */
@Composable
internal fun HudPage(
    panel: SidePanel,
    context: PanelContext,
    inbox: InboxActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(TmColors.Ground)
            .blockMapBelow(),
    ) {
        if (panel == SidePanel.INBOX) {
            InboxPage(context.frame, context.picker, inbox)
        } else {
            val townHall = RAIL_TOWN_HALL.any { it.panel == panel }
            val page =
                PageMode(
                    context.onClose,
                    if (townHall) tr("Rathaus") else null,
                    pageKpis(panel, context.frame, context.rules),
                )
            CompositionLocalProvider(LocalPage provides page) {
                with(
                    context,
                ) {
                    PanelContent(
                        panel,
                        frame,
                        rules,
                        onCommand,
                        picker,
                        onShow,
                        sprites,
                        onClose,
                        onPanel,
                        onEnvironment,
                    )
                }
            }
        }
    }
}

/**
 * Die Seite verdeckt die Karte: Klicks und Mausrad gehen nicht an die Karte darunter. Dafür genügt, dass die Seite
 * Zeigerereignisse annimmt – Compose gibt sie dann nicht an tiefere Geschwister weiter. Verbraucht wird nur das
 * Mausrad. **Nicht** alle Ereignisse verbrauchen: Ein Klick, bei dem sich die Maus zwischen Drücken und Loslassen
 * bewegt, gilt sonst für die Knöpfe auf der Seite als abgebrochen (man müsste mehrfach klicken).
 */
internal fun Modifier.blockMapBelow(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) event.changes.forEach { it.consume() }
            }
        }
    }
