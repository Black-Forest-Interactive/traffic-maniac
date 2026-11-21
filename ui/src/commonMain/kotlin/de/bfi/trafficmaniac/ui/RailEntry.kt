package de.bfi.trafficmaniac.ui

import androidx.compose.ui.input.key.Key
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon

/** Einträge der linken Leiste: Kurzname, Icon, die Seite, die sie öffnen, und ihre Taste. */
internal class RailEntry(
    val panel: SidePanel,
    val labelText: String,
    val icon: TmIcon,
    val key: Key? = null,
    val keyLabel: String = "",
)

/** Zähler an den Einträgen der Leiste; 0 = kein Zähler. */
internal class RailBadges(
    val inbox: Int = 0,
    /** Ungesicherte Schäden. */
    val unsecured: Int = 0,
    /** Laufende Einsätze. */
    val emergencies: Int = 0,
    /** Neue Erfolge seit dem letzten Blick auf die Seite. */
    val achievements: Int = 0,
) {
    fun of(panel: SidePanel): Int =
        when (panel) {
            SidePanel.INBOX -> inbox
            SidePanel.MAINTENANCE -> unsecured
            SidePanel.EMERGENCIES -> emergencies
            SidePanel.ACHIEVEMENTS -> achievements
            else -> 0
        }
}

/** Ämter im Menü der Handy-Leiste – dieselben Seiten wie am Desktop. */
internal val OFFICES =
    listOf(
        RailEntry(SidePanel.TREASURY, translatable("Kämmerei"), Icons.Euro),
        RailEntry(SidePanel.OFFICE, translatable("Ordnung"), Icons.Scale),
        RailEntry(SidePanel.MAINTENANCE, translatable("Bauamt"), Icons.HardHat),
        RailEntry(SidePanel.PARKING, translatable("Parken"), Icons.Parking),
        RailEntry(SidePanel.TRANSIT, translatable("ÖPNV"), Icons.Bus),
    )

/** Berichte im Menü der Handy-Leiste. */
internal val REPORTS =
    listOf(
        RailEntry(SidePanel.INBOX, translatable("Posteingang"), Icons.Inbox),
        RailEntry(SidePanel.PRESS, translatable("Presse"), Icons.Newspaper),
        RailEntry(SidePanel.CITIZENS, translatable("Bürger"), Icons.Users),
        RailEntry(SidePanel.EVENTS, translatable("Kalender"), Icons.Calendar),
        RailEntry(SidePanel.EMERGENCIES, translatable("Einsätze"), Icons.Siren),
        RailEntry(SidePanel.ACHIEVEMENTS, translatable("Erfolge"), Icons.Award),
        RailEntry(SidePanel.HELP, translatable("Hilfe"), Icons.Help),
    )

/** Desktop-Leiste oben: Karte und Posteingang. [SidePanel.NONE] ist die Karte. */
internal val RAIL_TOP =
    listOf(
        RailEntry(SidePanel.NONE, translatable("Karte"), Icons.Map, Key.M, "M"),
        RailEntry(SidePanel.INBOX, translatable("Posteingang"), Icons.Inbox, Key.I, "I"),
    )

/** Desktop-Leiste, Gruppe „Rathaus“: die Ämter. */
internal val RAIL_TOWN_HALL =
    listOf(
        RailEntry(SidePanel.TREASURY, translatable("Kämmerei"), Icons.Euro, Key.K, "K"),
        RailEntry(SidePanel.OFFICE, translatable("Ordnung"), Icons.Scale, Key.O, "O"),
        RailEntry(SidePanel.MAINTENANCE, translatable("Bauamt"), Icons.HardHat, Key.B, "B"),
        RailEntry(SidePanel.PARKING, translatable("Parken"), Icons.Parking, Key.P, "P"),
        RailEntry(SidePanel.TRANSIT, translatable("ÖPNV"), Icons.Bus, Key.N, "N"),
        RailEntry(SidePanel.EMERGENCIES, translatable("Einsätze"), Icons.Siren, Key.R, "R"),
        RailEntry(SidePanel.PRESS, translatable("Presse"), Icons.Newspaper, Key.Z, "Z"),
        RailEntry(SidePanel.CITIZENS, translatable("Bürger"), Icons.Users, Key.U, "U"),
        RailEntry(SidePanel.EVENTS, translatable("Kalender"), Icons.Calendar, Key.C, "C"),
    )

/** Desktop-Leiste unten: Erfolge und Hilfe. */
internal val RAIL_BOTTOM =
    listOf(
        RailEntry(SidePanel.ACHIEVEMENTS, translatable("Erfolge"), Icons.Award, Key.J, "J"),
        RailEntry(SidePanel.HELP, translatable("Hilfe"), Icons.Help, Key.F1, "F1"),
    )

/** Taste einer Seite der Leiste (M = Karte): Seite anfordern; `false`, wenn [key] keine Seite ist. */
fun HudInput.requestPage(key: Key): Boolean {
    val entry = (RAIL_TOP + RAIL_TOWN_HALL + RAIL_BOTTOM).firstOrNull { it.key == key } ?: return false
    panelRequest = entry.panel
    return true
}
