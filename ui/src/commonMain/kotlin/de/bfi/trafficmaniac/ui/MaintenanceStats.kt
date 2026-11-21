package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.BookingCategory
import de.bfi.trafficmaniac.game.DamageReport
import de.bfi.trafficmaniac.game.DamageResponse
import de.bfi.trafficmaniac.game.DamageStatus
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.MaintenanceBudget
import de.bfi.trafficmaniac.game.RepairMethod
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.formatNumber
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.sim.VehicleType
import de.bfi.trafficmaniac.ui.components.ChartAxis
import de.bfi.trafficmaniac.ui.components.CheckBox
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.KpiRow
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.PageHeader
import de.bfi.trafficmaniac.ui.components.PageMode
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.TimeAxis
import de.bfi.trafficmaniac.ui.components.TimeChart
import de.bfi.trafficmaniac.ui.components.threeTicks
import de.bfi.trafficmaniac.ui.map.MapStyle
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Anteil am Verschleiß je Ursache und die drei Straßen, die am schnellsten verfallen. */
@Composable
internal fun WearCauses(data: MaintenancePageData) {
    val wear = data.snapshot.wear
    val byCause =
        listOf(
            tr("Lkw & Schwertransporte") to
                ((wear.byVehicle[VehicleType.TRUCK] ?: 0.0) + (wear.byVehicle[VehicleType.HEAVY_TRANSPORT] ?: 0.0)),
            tr("Pkw & Lieferwagen") to
                ((wear.byVehicle[VehicleType.CAR] ?: 0.0) + (wear.byVehicle[VehicleType.VAN] ?: 0.0)),
            tr("Busse") to (wear.byVehicle[VehicleType.BUS] ?: 0.0),
            tr("Wetter & Alter") to wear.aging,
        )
    val total = byCause.sumOf { it.second }
    if (total <= 0.0) {
        BasicText(tr("Noch kein messbarer Verschleiß."), style = TmType.small)
        return
    }
    for ((label, amount) in byCause) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(label, style = TmType.text(13.sp), modifier = Modifier.width(CAUSE_WIDTH.dp), maxLines = 1)
            Meter((amount / total).toFloat(), Modifier.weight(1f), height = 8.dp, color = TmColors.Warn)
            val percent = (amount / total * PERCENT).roundToInt()
            BasicText(
                if (percent == 0 && amount > 0) "< 1 %" else "$percent %",
                style = TmType.mono(12.sp),
                modifier = Modifier.width(SHARE.dp),
            )
        }
    }
    // Warum Pkw kaum zählen: Schaden wächst mit der Achslast hoch 4
    val ratio = axleLoad(VehicleType.TRUCK) / axleLoad(VehicleType.CAR)
    BasicText(
        tr("Achslast hoch 4: Ein Lkw nutzt die Straße ab wie rund {cars} Pkw.", "cars" to formatNumber(roundTo(ratio))),
        style = TmType.caption,
    )
    FastestDecay(data)
}

/** Die drei Straßen, die am schnellsten verfallen (Verschleiß je Tag). */
@Composable
private fun FastestDecay(data: MaintenancePageData) {
    val wear = data.snapshot.wear
    BasicText(tr("Schnellster Verfall"), style = TmType.text(13.sp, FontWeight.Bold))
    val days = max(data.snapshot.time / SECONDS_PER_DAY, MIN_DAYS)
    val streets =
        wear.bySegment.indices
            .mapNotNull { segment ->
                data.city.roads.segments[segment]
                    .name
                    ?.let { it to wear.bySegment[segment] }
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.max() }
            .entries
            .sortedByDescending { it.value }
            .take(TOP_STREETS)
    for ((street, amount) in streets) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                street,
                style = TmType.text(13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                tr("−{percent} %/Tag", "percent" to formatDecimal(amount / days * PERCENT, 1)),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
            )
        }
    }
}

/** Kosten der Amtszeit (Reparaturen, Erhalt, Klagen) und das Klagerisiko, solange Schäden ungesichert sind. */
@Composable
internal fun CostsAndRisk(data: MaintenancePageData) {
    val totals = data.game.journal.totals
    val state = data.game.maintenance
    for ((label, category) in listOf(
        tr("Reparaturen & Schilder") to BookingCategory.REPAIRS,
        tr("Erhalt") to BookingCategory.UPKEEP,
        tr("Klagen") to BookingCategory.LAWSUITS,
    )) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(label, style = TmType.text(13.sp))
            BasicText(formatEuro(-(totals[category] ?: 0L)), style = TmType.mono(13.sp))
        }
    }
    val unsecured = state.reports.count { it.status in UNSECURED_DAMAGE }
    val ignoredCraters = state.reports.count { it.status == DamageStatus.IGNORED && it.kind.name == "CRATER" }
    val daysPlayed = max(data.game.daysPlayed.toDouble(), 1.0)
    // Grobe Schätzung: Klagen bisher je Tag plus Reifenschäden an ignorierten Kratern
    val perDay = state.lawsuits / daysPlayed + ignoredCraters * data.rules.craterClaimsPerDay
    BasicText(
        if (unsecured == 0) {
            tr("Alle Schäden gesichert – kein Klagerisiko.")
        } else {
            tr(
                "{count} ungesichert → ≈ {claims} Klagen/Tag",
                "count" to unsecured,
                "claims" to formatDecimal(perDay, 1),
            )
        },
        style =
            TmType.text(
                13.sp,
                FontWeight.SemiBold,
                if (unsecured >
                    0
                ) {
                    TmColors.DangerText
                } else {
                    TmColors.SuccessText
                },
            ),
    )
}

private const val CAUSE_WIDTH = 170
private const val SHARE = 44
private const val TOP_STREETS = 3
private const val SECONDS_PER_DAY = 86_400.0
private const val MIN_DAYS = 0.1
private const val PERCENT = 100.0

/** Schadensäquivalent nach der Faustregel „Achslast hoch 4“ (wie in der Simulation). */
private fun axleLoad(type: VehicleType): Double =
    type.axles * (type.massKg / type.axles / STANDARD_AXLE_KG).pow(FOURTH_POWER)

/** Auf zwei gültige Stellen gerundet (7.913 → 7.900). */
private fun roundTo(value: Double): Long {
    val magnitude = 10.0.pow(floor(log10(value)) - 1)
    return ((value / magnitude).roundToLong() * magnitude).roundToLong()
}

private const val STANDARD_AXLE_KG = 10_000.0
private const val FOURTH_POWER = 4
