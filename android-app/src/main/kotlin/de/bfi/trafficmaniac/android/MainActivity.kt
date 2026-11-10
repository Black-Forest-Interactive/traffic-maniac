package de.bfi.trafficmaniac.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Einstieg der App: Vollbild im Querformat, dann Menü oder laufende Partie. */
class MainActivity : ComponentActivity() {
    /** Die laufende Partie – hier, damit sie im Hintergrund angehalten werden kann. */
    private val session = mutableStateOf<Session?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        val content = GameContent(assets)
        val data = AppData(filesDir)
        content.use(content.languageFor(data.settings().language))
        setContent { AndroidGame(content, data, session, onQuit = ::finish) }
    }

    override fun onPause() {
        super.onPause()
        // Im Hintergrund nicht weiterrechnen (Akku); nach der Rückkehr setzt der Spieler selbst fort
        session.value?.runner?.setSpeed(0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
