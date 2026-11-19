package de.bfi.trafficmaniac.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.bfi.trafficmaniac.game.HighscoreEntry

/** Takt der Partie und Warten auf ihr Ende; [onEnd] bekommt den Bestenlisten-Eintrag (`null` nach Abwahl). */
@Composable
internal fun RunSession(
    live: Session,
    onEnd: (HighscoreEntry?) -> Unit,
) {
    LaunchedEffect(live) { live.runner.run() }
    LaunchedEffect(live) { onEnd(live.result()) }
}
