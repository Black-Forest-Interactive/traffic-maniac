package de.bfi.trafficmaniac.ui

import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.TmIcon

/** Einträge der linken Leiste: Kurzname, Icon und das Fenster, das sie öffnen. */
internal class RailEntry(
    val panel: SidePanel,
    val labelText: String,
    val icon: TmIcon,
)

internal val OFFICES =
    listOf(
        // Bewusst getrennt: ein weiches Trennzeichen bricht um, zeigt aber keinen Bindestrich („Ordnungs / amt“)
        RailEntry(SidePanel.OFFICE, translatable("Ordnungs-\namt"), Icons.Scale),
        RailEntry(SidePanel.MAINTENANCE, translatable("Bauamt"), Icons.HardHat),
        RailEntry(SidePanel.PARKING, translatable("Parken"), Icons.Parking),
    )

internal val REPORTS =
    listOf(
        RailEntry(SidePanel.CAMERAS, translatable("Blitzer"), Icons.Camera),
        RailEntry(SidePanel.BUDGET, translatable("Haushalt & PR"), Icons.Wallet),
        RailEntry(SidePanel.CITIZENS, translatable("Bürger"), Icons.Users),
        RailEntry(SidePanel.EVENTS, translatable("Kalender"), Icons.Calendar),
        RailEntry(SidePanel.EMERGENCIES, translatable("Einsätze"), Icons.Siren),
        RailEntry(SidePanel.ACHIEVEMENTS, translatable("Erfolge"), Icons.Award),
        RailEntry(SidePanel.HELP, translatable("Hilfe"), Icons.Help),
    )
