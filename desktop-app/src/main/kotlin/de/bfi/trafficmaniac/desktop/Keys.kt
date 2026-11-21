package de.bfi.trafficmaniac.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import de.bfi.trafficmaniac.game.GameRunner
import de.bfi.trafficmaniac.ui.HudInput
import de.bfi.trafficmaniac.ui.SoundBoard
import de.bfi.trafficmaniac.ui.Tool
import de.bfi.trafficmaniac.ui.requestPage

/** Tasten 1–5 → die Tempo-Knöpfe der Kopfleiste (1× bis 64×). */
private val speedKeys =
    listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)
        .zip(GameRunner.TOP_SPEEDS)
        .toMap()

/** Ziffern 1–9: bei offener Werkzeugliste die Art an dieser Stelle. */
private val digitKeys =
    listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
        .mapIndexed { i, key -> key to i + 1 }
        .toMap()

/** Tempo-Tasten: Leertaste, 1–5, +/−, V (vorspulen). `false`, wenn [key] keine davon ist. */
private fun handleSpeedKey(
    key: Key,
    runner: GameRunner,
): Boolean {
    val speed = speedKeys[key]
    when {
        // Nach einem weggeklickten Tagesabschluss: Leertaste startet den neuen Tag
        key == Key.Spacebar && runner.frames.value?.pendingReport != null -> runner.acknowledgeReport()

        key == Key.Spacebar -> runner.togglePause()

        speed != null -> runner.setSpeed(speed)

        key == Key.Plus || key == Key.NumPadAdd || key == Key.Equals -> runner.stepSpeed(1)

        key == Key.Minus || key == Key.NumPadSubtract -> runner.stepSpeed(-1)

        key == Key.V -> runner.fastForward()

        else -> return false
    }
    return true
}

/**
 * Tasten im Spiel: Leertaste Pause, Enter Tagesabschluss schließen (sonst den Bauplan bauen), Strg+Z geplante
 * Maßnahme zurücknehmen, 1–5 Tempo 1×–64× (bei offener Werkzeugauswahl: 1–9 wählt die Art), +/− eine Stufe
 * schneller/langsamer, V vorspulen bis zum nächsten Ereignis, Umschalt+M Ton, Umschalt+N Musik, F5 Schnellspeichern,
 * F3 Entwickler-Anzeige, F4 Straßennetz-Modus, Esc/Q/W/E/A/S/X Werkzeuggruppen, M/I/K/O/B/P/N/R/Z/U/C/J/F1 Seiten.
 */
internal fun handleKey(
    event: KeyEvent,
    session: Session,
    sound: SoundBoard,
    onQuickSave: () -> Unit,
): Boolean =
    when {
        event.type != KeyEventType.KeyDown -> false
        event.isCtrlPressed || event.isAltPressed -> handleCtrlKey(event, session.input)
        else -> handlePlainKey(event, session, sound, onQuickSave)
    }

/** Strg+Z: die zuletzt geplante Maßnahme zurücknehmen; andere Kürzel mit Strg/Alt gehören nicht dem Spiel. */
private fun handleCtrlKey(
    event: KeyEvent,
    input: HudInput,
): Boolean {
    val undo = event.isCtrlPressed && event.key == Key.Z
    if (undo) input.undoRequest = true
    return undo
}

private fun handlePlainKey(
    event: KeyEvent,
    session: Session,
    sound: SoundBoard,
    onQuickSave: () -> Unit,
): Boolean {
    val runner = session.runner
    val input = session.input
    val digit = digitKeys[event.key]
    when {
        event.key == Key.Enter && runner.frames.value?.pendingReport != null -> runner.acknowledgeReport()
        event.key == Key.Enter -> input.buildRequest = true
        digit != null && input.listOpen -> input.kindRequest = digit
        handleSpeedKey(event.key, runner) -> Unit
        handleSystemKey(event, input, sound, onQuickSave) -> Unit
        else -> return handleHudKey(event.key, input)
    }
    return true
}

/** Umschalt+M Ton, Umschalt+N Musik, F5 Schnellspeichern, F3 Entwickler-Anzeige, F4 Straßennetz-Modus. */
private fun handleSystemKey(
    event: KeyEvent,
    input: HudInput,
    sound: SoundBoard,
    onQuickSave: () -> Unit,
): Boolean {
    when {
        event.isShiftPressed && event.key == Key.M -> sound.muted = !sound.muted
        event.isShiftPressed && event.key == Key.N -> sound.music = !sound.music
        event.key == Key.F5 -> onQuickSave()
        event.key == Key.F3 -> input.debug = !input.debug
        event.key == Key.F4 -> input.network = !input.network
        else -> return false
    }
    return true
}

/** Werkzeuggruppen und Seiten der Leiste. */
private fun handleHudKey(
    key: Key,
    input: HudInput,
): Boolean {
    val tool = Tool.forKey(key)
    if (tool != null) input.toolRequest = tool
    return tool != null || input.requestPage(key)
}
