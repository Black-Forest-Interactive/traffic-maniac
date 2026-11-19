package de.bfi.trafficmaniac.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.bfi.trafficmaniac.game.GameRunner

/**
 * Die laufende Partie und wo sie gerade ist: angezeigt ([shown]) oder im Menü angehalten ([parked]). „Menü“ mitten im
 * Spiel beendet die Partie nicht, sondern parkt sie; „Weiterspielen“ holt sie mit dem Tempo von vorher zurück. Erst
 * ein neues Spiel oder ein geladener Spielstand ([replace]) ersetzt sie. Takt und Spielende hängen an [live], laufen
 * also weiter (angehalten), während das Menü offen ist – ohne neuen Vorlauf beim Zurückkehren.
 *
 * [S] ist die Partie der jeweiligen Plattform, [runnerOf] liefert ihren Takt.
 */
class SessionSlot<S : Any>(
    shownState: MutableState<S?>,
    private val runnerOf: (S) -> GameRunner,
) {
    var shown by shownState
    var parked by mutableStateOf<S?>(null)
        private set

    /** Partie, deren Amtszeit vorbei ist – „Menü“ verlässt sie endgültig. */
    var finished: S? = null
    private var parkedSpeed = 1

    val live: S? get() = shown ?: parked

    /** „Menü“: laufende Partie anhalten und parken; eine beendete endgültig verlassen ([onEnd] wie bei [replace]). */
    fun toMenu(onEnd: (S) -> Unit = {}) {
        val current = shown ?: return
        if (current === finished) {
            replace(null, onEnd)
            return
        }
        val runner = runnerOf(current)
        parkedSpeed = runner.speed.value
        runner.setSpeed(0)
        parked = current
        shown = null
    }

    /** „Weiterspielen“: mit dem Tempo von vorher. */
    fun resume() {
        val waiting = parked ?: return
        runnerOf(waiting).setSpeed(parkedSpeed)
        parked = null
        shown = waiting
    }

    /** Neue Partie (oder keine): Die bisherige gilt als beendet, [onEnd] bekommt sie (z. B. für die Statistik). */
    fun replace(
        next: S?,
        onEnd: (S) -> Unit = {},
    ) {
        live?.let(onEnd)
        parked = null
        shown = next
    }
}
