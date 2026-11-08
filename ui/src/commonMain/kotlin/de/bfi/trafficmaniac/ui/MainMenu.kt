package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.CityTexts
import de.bfi.trafficmaniac.game.GameSettings
import de.bfi.trafficmaniac.game.Highscores
import de.bfi.trafficmaniac.game.SaveInfo
import de.bfi.trafficmaniac.game.Scenario
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon

private enum class MenuTab(
    private val labelText: String,
    val icon: TmIcon,
) {
    PLAY(translatable("Spielen"), Icons.Play),
    LOAD(translatable("Laden"), Icons.Folder),
    HIGHSCORES(translatable("Bestenliste"), Icons.Award),
    SETTINGS(translatable("Einstellungen"), Icons.Sliders),
    CREDITS(translatable("Credits"), Icons.Info),
    ;

    val label: String get() = tr(labelText)
}

/** Stadtauswahl: Namen der Stadtpakete, die gewählte, ihre Beschreibung und ihr Spitzname. */
class CityChoice(
    val names: List<String> = emptyList(),
    val selected: Int = 0,
    val description: String = "",
    val onSelect: (Int) -> Unit = {},
    val nickname: String = "",
)

/** Spielstände fürs Menü: Liste, Laden und Löschen; [describeTime] macht aus dem Zeitstempel „heute, 14:29 Uhr“. */
class SaveSlots(
    val saves: List<SaveInfo> = emptyList(),
    val onLoad: (SaveInfo) -> Unit = {},
    val onDelete: (SaveInfo) -> Unit = {},
    val describeTime: (SaveInfo) -> String = { it.savedAt.replace('T', ' ') },
)

/**
 * Startmenü im Design „Leitstelle“: dunkle Seitenleiste mit Logo, Claim und Navigation, rechts der gewählte
 * Bildschirm – neues Spiel, Spielstände, Bestenliste, Einstellungen, Credits. [loading] zeigt eine Ladeanzeige.
 */
@Composable
fun MainMenu(
    scenarios: List<Scenario>,
    highscores: Highscores,
    settings: GameSettings,
    onSettings: (GameSettings) -> Unit,
    onStart: (Scenario) -> Unit,
    onQuit: () -> Unit,
    attribution: String,
    loading: String? = null,
    /** Gespeicherte Partien; ohne Speicherfunktion leer. */
    saves: SaveSlots = SaveSlots(),
    /** Unterzeile unter dem Logo – kommt aus dem Stadtpaket. */
    tagline: String = CityTexts().tagline,
    cities: CityChoice = CityChoice(),
    /** Karte der gewählten Stadt für die Vorschau beim Spielstand. */
    cityMap: CityMap? = null,
) {
    var tab by remember { mutableStateOf(MenuTab.PLAY) }
    TmTheme {
        Box(Modifier.fillMaxSize().background(TmColors.Ground)) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(tab, { tab = it }, saves.saves.size, tagline, attribution, onQuit)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (tab) {
                        MenuTab.PLAY -> PlayScreen(scenarios, highscores, settings, onSettings, onStart, cities)
                        MenuTab.LOAD -> LoadScreen(saves, cityMap)
                        MenuTab.HIGHSCORES -> HighscoreScreen(scenarios, highscores)
                        MenuTab.SETTINGS -> SettingsScreen(settings, onSettings)
                        MenuTab.CREDITS -> CreditsScreen(attribution)
                    }
                }
            }
            loading?.let { Loading(it) }
        }
    }
}

@Composable
private fun Sidebar(
    tab: MenuTab,
    onTab: (MenuTab) -> Unit,
    saveCount: Int,
    tagline: String,
    attribution: String,
    onQuit: () -> Unit,
) {
    Column(
        Modifier
            .width(SIDEBAR_WIDTH.dp)
            .fillMaxHeight()
            .background(TmColors.Ink)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(LOGO_SIZE)
            BasicText(tr("TRAFFIC\nMANIAC"), style = TmType.display(24.sp, TmColors.OnDark).copy(lineHeight = 24.sp))
        }
        BasicText(tagline, style = TmType.text(13.sp, color = TmColors.OnDarkMuted).copy(lineHeight = 20.sp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (option in MenuTab.entries) {
                val count = if (option == MenuTab.LOAD && saveCount > 0) "$saveCount" else null
                NavButton(option.label, option.icon, option == tab, count) { onTab(option) }
            }
        }
        Box(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NavButton(tr("Beenden"), Icons.LogOut, active = false, count = null, outlined = true, onClick = onQuit)
            BasicText(
                tr("Kartendaten: {attribution}", "attribution" to attribution),
                style = TmType.text(11.sp, color = TmColors.OnDarkMuted),
            )
        }
    }
}

@Composable
private fun NavButton(
    label: String,
    icon: TmIcon,
    active: Boolean,
    count: String?,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val color = if (active) TmColors.Surface else TmColors.OnDark
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = NAV_HEIGHT.dp)
            .clip(shape)
            .background(if (active) TmColors.Primary else Color.Transparent)
            .then(if (outlined) Modifier.border(1.5.dp, TmColors.LineDark, shape) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = color, size = 20.dp)
        BasicText(label, style = TmType.text(15.sp, FontWeight.SemiBold, color), modifier = Modifier.weight(1f))
        count?.let {
            BasicText(
                it,
                style = TmType.text(12.sp, FontWeight.SemiBold, if (active) TmColors.Surface else TmColors.OnDarkMuted),
            )
        }
    }
}

/** Kopf eines Menübildschirms: Eyebrow in Großbuchstaben und großer Titel. */
@Composable
internal fun ScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            eyebrow.uppercase(),
            style = TmType.text(13.sp, FontWeight.SemiBold, TmColors.Muted).copy(letterSpacing = 1.sp),
        )
        BasicText(title, style = TmType.display(44.sp).copy(lineHeight = 46.sp))
    }
}

@Composable
private fun Loading(text: String) {
    Box(Modifier.fillMaxSize().background(TmColors.Scrim), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .background(
                    TmColors.Surface,
                    RoundedCornerShape(16.dp),
                ).padding(horizontal = 28.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Logo(LOGO_SIZE)
            BasicText(text, style = TmType.text(18.sp, FontWeight.Bold))
        }
    }
}

private const val SIDEBAR_WIDTH = 280
private const val NAV_HEIGHT = 48
private const val LOGO_SIZE = 40
