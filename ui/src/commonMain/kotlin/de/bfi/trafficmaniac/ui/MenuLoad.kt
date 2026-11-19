package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.game.SaveInfo
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Icon
import de.bfi.trafficmaniac.ui.components.Icons
import de.bfi.trafficmaniac.ui.components.MinTouch
import de.bfi.trafficmaniac.ui.components.TmButton
import de.bfi.trafficmaniac.ui.map.CityThumbnail

private val DeleteRed = Color(0xFF8A0C20)

/** Spielstände: der neueste groß zum Fortsetzen, die übrigen als Tabelle. */
@Composable
internal fun LoadScreen(
    slots: SaveSlots,
    cityMap: CityMap?,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        ScreenHeader(tr("Spielstände"), tr("Wo waren wir stehen geblieben?"))
        val latest = slots.saves.firstOrNull()
        if (latest == null) {
            BasicText(
                tr("Noch nichts gespeichert – im Spiel: Knopf „Speichern“ oder F5 (Schnellspeichern)."),
                style = TmType.text(15.sp, color = TmColors.Muted),
            )
            return@Column
        }
        LatestSave(latest, slots, cityMap)
        val older = slots.saves.drop(1)
        if (older.isNotEmpty()) SaveTable(older, slots)
    }
}

@Composable
private fun LatestSave(
    save: SaveInfo,
    slots: SaveSlots,
    cityMap: CityMap?,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .widthIn(max = MAX_WIDTH.dp)
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    TmColors.Ink,
                    Offset(5.dp.toPx(), 5.dp.toPx()),
                    size,
                    CornerRadius(18.dp.toPx()),
                )
            }.background(TmColors.Surface, shape)
            .border(2.dp, TmColors.Ink, shape)
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val thumbShape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .width(220.dp)
                .height(150.dp)
                .clip(thumbShape)
                .border(2.dp, TmColors.Ink, thumbShape),
        ) {
            cityMap?.let { CityThumbnail(it, Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    tr("Zuletzt").uppercase(),
                    style = TmType.text(11.sp, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
                    modifier =
                        Modifier
                            .background(
                                TmColors.Active,
                                RoundedCornerShape(6.dp),
                            ).padding(horizontal = 9.dp, vertical = 5.dp),
                )
                BasicText(
                    tr("{time} gespeichert", "time" to slots.describeTime(save)),
                    style = TmType.text(13.sp, color = TmColors.LineDark),
                )
            }
            BasicText(title(save), style = TmType.display(28.sp))
            BasicText(subtitle(save), style = TmType.text(14.sp, color = TmColors.Ink3))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Figure(tr("Kasse"), formatEuro(save.balance))
                Figure(tr("Reputation"), "${save.reputation}/100")
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TmButton(tr("Fortsetzen →"), { slots.onLoad(save) }, kind = ButtonKind.PRIMARY, large = true)
            TmButton(tr("Löschen"), { slots.onDelete(save) }, kind = ButtonKind.TEXT)
        }
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
) {
    Column {
        BasicText(label, style = TmType.text(12.sp, color = TmColors.LineDark))
        BasicText(value, style = TmType.mono(18.sp))
    }
}

/** Ältere Spielstände: Spielstand | Kasse | Reputation | Gespeichert | Laden, Löschen. */
@Composable
private fun SaveTable(
    saves: List<SaveInfo>,
    slots: SaveSlots,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .widthIn(
                max = MAX_WIDTH.dp,
            ).fillMaxWidth()
            .clip(shape)
            .border(2.dp, TmColors.Ink, shape)
            .background(TmColors.Surface),
    ) {
        Row(Modifier.fillMaxWidth().background(TmColors.LineSoft).padding(horizontal = 20.dp, vertical = 12.dp)) {
            HeaderCell(tr("Spielstand"), 2f)
            HeaderCell(tr("Kasse"), 1f)
            HeaderCell(tr("Reputation"), 1f)
            HeaderCell(tr("Gespeichert"), TIME_WEIGHT)
            Box(Modifier.width(ACTIONS_WIDTH.dp))
        }
        for (save in saves) {
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(TmColors.LineSoft))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(title(save), style = TmType.text(15.sp, FontWeight.Bold))
                    BasicText(subtitle(save), style = TmType.text(12.sp, color = TmColors.LineDark))
                }
                BasicText(formatEuro(save.balance), style = TmType.mono(14.sp), modifier = Modifier.weight(1f))
                BasicText("${save.reputation}/100", style = TmType.mono(14.sp), modifier = Modifier.weight(1f))
                BasicText(
                    slots.describeTime(save),
                    style = TmType.text(13.sp, color = TmColors.Ink3),
                    modifier = Modifier.weight(TIME_WEIGHT),
                )
                Row(
                    Modifier.width(ACTIONS_WIDTH.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TmButton(tr("Laden"), { slots.onLoad(save) })
                    DeleteButton { slots.onDelete(save) }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(
    text: String,
    weight: Float,
) {
    BasicText(text, style = TmType.text(12.sp, FontWeight.Bold, TmColors.Muted), modifier = Modifier.weight(weight))
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(MinTouch)
            .clip(shape)
            .border(1.5.dp, TmColors.OnDarkMuted, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Trash, tr("Löschen"), tint = DeleteRed, size = 18.dp) }
}

private fun title(save: SaveInfo): String {
    val quick = if (save.id == SaveInfo.QUICK_SAVE) tr("Schnellspeicher · ") else ""
    return quick + tr(save.scenarioTitle)
}

private fun subtitle(save: SaveInfo) = "${save.cityName} · ${save.difficulty.label} · ${save.gameTime}"

private const val MAX_WIDTH = 920
private const val ACTIONS_WIDTH = 168
private const val TIME_WEIGHT = 1.2f
