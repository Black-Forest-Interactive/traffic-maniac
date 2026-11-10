package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.MapPlace
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.sim.VehicleRole
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.map.MapPicker

/** Fahrzeuge, die im Dienst unterwegs sind – Blaulicht, Abschleppdienst, Straßenmeisterei, Winterdienst. */
private val SERVICE_ROLES =
    listOf(
        VehicleRole.AMBULANCE,
        VehicleRole.POLICE,
        VehicleRole.FIRE_BRIGADE,
        VehicleRole.TOW_TRUCK,
        VehicleRole.STREET_SERVICE,
        VehicleRole.SNOW_PLOUGH,
    )

/** Ein Fahrzeug in der Liste: Art, Ort und Straße, auf der es gerade fährt. */
private class FleetVehicle(
    val id: Int,
    val role: VehicleRole,
    val x: Float,
    val y: Float,
)

/**
 * Alle Einsatz- und Dienstfahrzeuge, nach Art gruppiert. Ein Klick fliegt zum Fahrzeug. Die Straßennamen werden nur
 * alle paar Sekunden neu bestimmt – die Suche nach dem nächsten Abschnitt ist nicht billig.
 */
@Composable
internal fun ColumnScope.ServiceFleet(
    snapshot: SimulationSnapshot,
    city: CityMap,
    picker: MapPicker,
    onShow: (MapPlace) -> Unit,
) {
    val vehicles =
        snapshot.roles.indices
            .mapNotNull { i ->
                val role = VehicleRole.entries[snapshot.roles[i].toInt()]
                if (role in SERVICE_ROLES) FleetVehicle(snapshot.ids[i], role, snapshot.x[i], snapshot.y[i]) else null
            }.sortedWith(compareBy({ SERVICE_ROLES.indexOf(it.role) }, { it.id }))
    val names = remember(snapshot.tick / NAME_REFRESH_TICKS) { HashMap<Int, String>() }
    BasicText(
        tr("Fahrzeuge im Dienst ({count})", "count" to vehicles.size),
        style = TmType.text(14.sp, FontWeight.Bold),
    )
    if (vehicles.isEmpty()) BasicText(tr("Gerade ist kein Dienstfahrzeug unterwegs."), style = TmType.small)
    for ((role, group) in vehicles.groupBy { it.role }) {
        BasicText("${role.label} · ${group.size}".uppercase(), style = TmType.eyebrow)
        for (vehicle in group.take(MAX_PER_ROLE)) {
            val street =
                names.getOrPut(vehicle.id) {
                    picker
                        .nearestSegment(vehicle.x, vehicle.y, STREET_RADIUS) { true }
                        ?.let { city.roads.segments[it].name } ?: tr("Straße ohne Namen")
                }
            FleetRow(role, street) { onShow(MapPlace(vehicle.x, vehicle.y)) }
        }
        if (group.size > MAX_PER_ROLE) {
            BasicText(tr("… und {count} weitere", "count" to group.size - MAX_PER_ROLE), style = TmType.caption)
        }
    }
}

@Composable
private fun FleetRow(
    role: VehicleRole,
    street: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(roleColor(role), CircleShape))
        Column(Modifier.weight(1f)) {
            BasicText(street, style = TmType.text(13.sp, FontWeight.SemiBold))
        }
        Icon(Icons.ChevronRight, tr("Auf Karte zeigen"), tint = TmColors.Muted, size = 16.dp)
    }
}

/** Farben wie auf der Karte: Rettung weiß-rot, Polizei blau, Feuerwehr rot, Abschlepper gelb, Dienst orange. */
private fun roleColor(role: VehicleRole): Color =
    when (role) {
        VehicleRole.AMBULANCE -> TmColors.Danger
        VehicleRole.POLICE -> TmColors.Primary
        VehicleRole.FIRE_BRIGADE -> FIRE_RED
        VehicleRole.TOW_TRUCK -> TmColors.Active
        else -> TmColors.Warn
    }

private val FIRE_RED = Color(0xFFC1121F)
private const val MAX_PER_ROLE = 12
private const val ROW_HEIGHT = 36
private const val STREET_RADIUS = 40f

/** Straßennamen alle so viele Ticks neu bestimmen. */
private const val NAME_REFRESH_TICKS = 100L
