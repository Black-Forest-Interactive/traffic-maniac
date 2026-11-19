package de.bfi.trafficmaniac.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.ui.resources.Res
import de.bfi.trafficmaniac.ui.resources.bricolage_grotesque_extrabold
import de.bfi.trafficmaniac.ui.resources.inter_bold
import de.bfi.trafficmaniac.ui.resources.inter_medium
import de.bfi.trafficmaniac.ui.resources.inter_regular
import de.bfi.trafficmaniac.ui.resources.inter_semibold
import de.bfi.trafficmaniac.ui.resources.jetbrains_mono_bold
import de.bfi.trafficmaniac.ui.resources.jetbrains_mono_medium
import org.jetbrains.compose.resources.Font

/**
 * Farben des Designs „Leitstelle“ (docs/design/DESIGN.md): dunkles Bedienfeld, weiße Fenster über der bunten Karte.
 * Farbe hat immer eine Bedeutung – Amtsblau = Aktion/Auswahl, Ampelgelb = gerade aktiv, Alarmrot = Problem,
 * Grün = Geld rein/erledigt. Die Kartenfarben stehen in `MapStyle`.
 */
object TmColors {
    // Dunkles Bedienfeld
    val Ink = Color(0xFF15202B)
    val Ink2 = Color(0xFF1E2B38)
    val Ink3 = Color(0xFF243444)
    val LineDark = Color(0xFF3B4C5D)
    val OnDark = Color(0xFFEEF1F4)
    val OnDarkMuted = Color(0xFFA3B0BD)

    // Helle Flächen
    val Ground = Color(0xFFEEF1F4)
    val Surface = Color(0xFFFFFFFF)
    val Line = Color(0xFFC3CCD5)
    val LineSoft = Color(0xFFE3E8ED)
    val Track = Color(0xFFD5DCE3)
    val Muted = Color(0xFF586674)

    // Akzente
    val Primary = Color(0xFF2257B8)
    val PrimaryHover = Color(0xFF1A4691)
    val PrimaryTint = Color(0xFFEAF1FC)
    val Active = Color(0xFFFFC21A)
    val Danger = Color(0xFFC62B3B)
    val DangerText = Color(0xFFA3202E)
    val DangerTint = Color(0xFFFBE3E5)
    val Success = Color(0xFF1E7A44)
    val SuccessText = Color(0xFF14532D)
    val SuccessTint = Color(0xFFDDF0E4)
    val Warn = Color(0xFFF28C28)
    val WarnText = Color(0xFF6B4A00)
    val WarnTint = Color(0xFFFFF3CC)

    // Ampel im Logo
    val LogoRed = Color(0xFFE5484D)

    /** Halbdunkler Schleier hinter Dialogen. */
    val Scrim = Color(0x9915202B)
}

/** Die drei Schriftfamilien: Bricolage Grotesque (Titel), Inter (Text), JetBrains Mono (Zahlen). */
class TmFonts(
    val display: FontFamily,
    val text: FontFamily,
    val mono: FontFamily,
)

/** Ohne [TmTheme] (z. B. in Tests) gelten die Systemschriften. */
val LocalTmFonts =
    staticCompositionLocalOf { TmFonts(FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.Monospace) }

/** Lädt die Schriften und stellt sie allem darunter bereit – einmal an der Wurzel der Oberfläche. */
@Composable
fun TmTheme(content: @Composable () -> Unit) {
    val fonts =
        TmFonts(
            display = FontFamily(Font(Res.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold)),
            text =
                FontFamily(
                    Font(Res.font.inter_regular, FontWeight.Normal),
                    Font(Res.font.inter_medium, FontWeight.Medium),
                    Font(Res.font.inter_semibold, FontWeight.SemiBold),
                    Font(Res.font.inter_bold, FontWeight.Bold),
                ),
            mono =
                FontFamily(
                    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
                    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
                ),
        )
    CompositionLocalProvider(LocalTmFonts provides fonts, content = content)
}

/**
 * Textstile nach DESIGN.md §2: Display für Titel (Screen 44 sp, Fenster/Szenario 22 sp, CTA 20 sp), Inter für alles
 * andere (Body 14, Labels 13, klein 11–12), Mono für Geld, Uhrzeit, Zähler und Prozente.
 */
object TmType {
    private val fonts: TmFonts
        @Composable @ReadOnlyComposable
        get() = LocalTmFonts.current

    @Composable
    @ReadOnlyComposable
    fun display(
        size: TextUnit = 22.sp,
        color: Color = TmColors.Ink,
    ) = TextStyle(fontFamily = fonts.display, fontWeight = FontWeight.ExtraBold, fontSize = size, color = color)

    @Composable
    @ReadOnlyComposable
    fun text(
        size: TextUnit = 14.sp,
        weight: FontWeight = FontWeight.Normal,
        color: Color = TmColors.Ink,
    ) = TextStyle(fontFamily = fonts.text, fontWeight = weight, fontSize = size, color = color)

    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = text()

    /** Beschriftung über einer Einstellung (13 sp fett). */
    val label: TextStyle
        @Composable @ReadOnlyComposable
        get() = text(13.sp, FontWeight.Bold)

    /** Nebentext (12 sp grau). */
    val small: TextStyle
        @Composable @ReadOnlyComposable
        get() = text(12.sp, color = TmColors.Muted)

    /** Kleinster Nebentext (11 sp grau), z. B. Kachel-Beschriftungen. */
    val caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = text(11.sp, color = TmColors.Muted)

    /** Eyebrow über Titeln: 11 sp fett, gesperrt, grau – den Text in Großbuchstaben übergeben. */
    val eyebrow: TextStyle
        @Composable @ReadOnlyComposable
        get() = text(11.sp, FontWeight.Bold, TmColors.Muted).copy(letterSpacing = 1.sp)

    @Composable
    @ReadOnlyComposable
    fun mono(
        size: TextUnit = 14.sp,
        bold: Boolean = true,
        color: Color = TmColors.Ink,
    ) = TextStyle(
        fontFamily = fonts.mono,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontSize = size,
        color = color,
    )
}
