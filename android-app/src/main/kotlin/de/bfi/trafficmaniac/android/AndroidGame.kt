package de.bfi.trafficmaniac.android

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.HighscoreEntry
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.i18n.I18n
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.CityChoice
import de.bfi.trafficmaniac.ui.FitToDesign
import de.bfi.trafficmaniac.ui.MainMenu
import de.bfi.trafficmaniac.ui.TouchCallbacks
import de.bfi.trafficmaniac.ui.TouchGameApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Menü oder laufende Partie; nach der Wertung geht es zurück ins Menü. */
@Composable
fun AndroidGame(
    content: GameContent,
    data: AppData,
    sessionState: MutableState<Session?>,
    onQuit: () -> Unit,
) {
    var settings by remember { mutableStateOf(data.settings()) }
    var highscores by remember { mutableStateOf(data.highscores()) }
    var city by remember { mutableStateOf(content.cities.firstOrNull()) }
    var session by sessionState
    var loading by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(settings) { data.save(settings) }
    val language = content.languageFor(settings.language)
    remember(language) { language.also(content::use) }
    val start: (Scenario) -> Unit = { scenario ->
        city?.let { chosen ->
            loading = tr("{title}: {name} wird vorbereitet …", "title" to tr(scenario.title), "name" to chosen.name)
            scope.launch {
                session = withContext(Dispatchers.Default) { Session.start(content, chosen, scenario, settings) }
                loading = null
            }
        }
    }
    val current = session
    key(language) {
        if (current == null) {
            // Das Menü ist für große Bildschirme gezeichnet und wird auf dem Handy eingepasst
            FitToDesign {
                Menu(
                    content,
                    MenuState(settings, { settings = it }, highscores, loading),
                    city,
                    { city = it },
                    start,
                    onQuit,
                )
            }
        } else {
            Playing(current, onMenu = { session = null }) {
                highscores = highscores.add(it)
                data.save(highscores)
            }
        }
    }
}

/** Was das Menü vom Spiel braucht: Einstellungen, Bestenliste und ob gerade eine Partie lädt. */
private class MenuState(
    val settings: GameSettings,
    val onSettings: (GameSettings) -> Unit,
    val highscores: Highscores,
    val loading: String?,
)

/** Das gemeinsame Startmenü mit den Städten aus den Assets. */
@Composable
private fun Menu(
    content: GameContent,
    state: MenuState,
    city: AssetCity?,
    onCity: (AssetCity) -> Unit,
    onStart: (Scenario) -> Unit,
    onQuit: () -> Unit,
) {
    MainMenu(
        scenarios = content.scenarios,
        highscores = state.highscores,
        settings = state.settings,
        onSettings = state.onSettings,
        onStart = onStart,
        onQuit = onQuit,
        attribution = city?.map?.attribution.orEmpty(),
        loading = state.loading,
        tagline =
            city
                ?.info
                ?.texts(I18n.language)
                ?.tagline
                .orEmpty(),
        cities =
            CityChoice(
                names = content.cities.map { it.name },
                selected = content.cities.indexOf(city),
                description = city?.description.orEmpty(),
                onSelect = { onCity(content.cities[it]) },
            ),
        cityMap = city?.map,
    )
}

/** Die laufende Partie: Takt, Bestenliste am Ende und die Spieloberfläche. */
@Composable
private fun Playing(
    current: Session,
    onMenu: () -> Unit,
    onHighscore: (HighscoreEntry) -> Unit,
) {
    // Zurück-Taste beendet die Partie nicht aus Versehen, sondern hält sie nur an
    BackHandler { current.runner.setSpeed(0) }
    LaunchedEffect(current) { current.runner.run() }
    LaunchedEffect(current) { current.result()?.let(onHighscore) }
    TouchGameApp(
        city = current.city.map,
        traffic = current.runner.frames,
        rules = current.game.rules,
        callbacks =
            TouchCallbacks(
                onCommand = { current.game.submit(it) },
                onContinueDay = { current.runner.acknowledgeReport() },
                onSpeed = current.runner::setSpeed,
                onMenu = onMenu,
            ),
        tutorial = current.scenario.tutorial,
        input = current.input,
    )
}
