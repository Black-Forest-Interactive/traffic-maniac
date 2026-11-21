package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.GameRules
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.NewspaperEdition
import de.bfi.trafficmaniac.game.PrContract
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.i18n.formatDecimal
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.PanelScaffold
import de.bfi.trafficmaniac.ui.components.PanelSection
import de.bfi.trafficmaniac.ui.components.SegmentedControl
import de.bfi.trafficmaniac.ui.components.TmButton
import kotlin.math.roundToInt

/**
 * Presse & PR (Entwurf 2, `V2-Presse`): PR-Agentur und Kampagne mit ihrer Wirkung, Anlässe (Spatenstich), der Ton
 * der Zeitung der letzten Tage und das Schlagzeilen-Archiv. Die Kennzahlen stehen oben in den Kacheln.
 */
@Composable
internal fun PressPage(
    game: GameState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
    onClose: () -> Unit,
) {
    PanelScaffold(tr("Rathaus"), tr("Presse & PR"), onClose = onClose, floating = false) {
        PanelSection(title = tr("PR-Agentur")) { Agency(game.prContract, onCommand) }
        PanelSection(title = tr("Kampagne „Totschlagargumente“")) { Campaign(game, rules, onCommand) }
        PanelSection(title = tr("Anlässe")) { Occasions(game, rules, onCommand) }
        PanelSection(title = tr("Ton der Zeitung")) { ToneHistory(game.editions, game.firstDay) }
        PanelSection(title = tr("Schlagzeilen-Archiv"), divider = false) { Archive(game.editions, game.firstDay) }
    }
}

@Composable
private fun Agency(
    contract: PrContract,
    onCommand: (GameCommand) -> Unit,
) {
    BasicText(tr("Solange bezahlt wird, schreibt die Lokalzeitung Jubel-Stories."), style = TmType.small)
    SegmentedControl(
        PrContract.entries,
        contract,
        { it.label },
        { onCommand(GameCommand.SetPrContract(it)) },
        Modifier.fillMaxWidth(),
        detail = { if (it.weeklyCost > 0) tr("{cost}/Woche", "cost" to formatEuro(it.weeklyCost)) else "–" },
    )
    // Was die gewählte Stufe bewirkt
    BasicText(
        if (contract == PrContract.NONE) {
            tr("Keine Wirkung – die Zeitung schreibt, was sie will, und wird bissig.")
        } else {
            tr(
                "+{reputation} Ruf/Tag · Zorn gedämpft um {damping} % · Ton höchstens {tone}",
                "reputation" to formatDecimal(contract.reputationPerHour * HOURS_PER_DAY, 1),
                "damping" to (contract.angerDamping * PERCENT).roundToInt(),
                "tone" to toneWord(contract.pressTone),
            )
        },
        style =
            TmType.text(
                13.sp,
                FontWeight.SemiBold,
                if (contract ==
                    PrContract.NONE
                ) {
                    TmColors.WarnText
                } else {
                    TmColors.Muted
                },
            ),
    )
    BasicText(tr("Kosten erscheinen in der Kämmerei unter „PR & Presse“."), style = TmType.caption)
}

@Composable
private fun Campaign(
    game: GameState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    BasicText(
        tr("„Wer sich an die Geschwindigkeit hält, muss sich um Blitzer keine Sorgen machen.“"),
        style = TmType.small.copy(fontStyle = FontStyle.Italic),
    )
    SegmentedControl(
        listOf(false, true),
        game.argumentsCampaign,
        { if (it) tr("An") else tr("Aus") },
        { onCommand(GameCommand.SetArgumentsCampaign(it)) },
        Modifier.fillMaxWidth(),
        detail = { if (it) tr("{cost}/Woche", "cost" to formatEuro(rules.argumentsWeeklyCost)) else "–" },
    )
    BasicText(
        tr("Dämpft den Ärger über Bußgelder. Kosten unter „PR & Presse“ in der Kämmerei."),
        style = TmType.caption,
    )
}

/** Spatenstich oder Eröffnung: links der nächste Anlass, rechts der Knopf. */
@Composable
private fun Occasions(
    game: GameState,
    rules: GameRules,
    onCommand: (GameCommand) -> Unit,
) {
    val next = game.press.nextCeremony
    if (next == null) {
        BasicText(tr("Heute wurde schon gefeiert – morgen gibt es die nächste Schaufel."), style = TmType.small)
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(tr("Nächster Anlass"), style = TmType.caption)
            BasicText(next, style = TmType.text(14.sp, FontWeight.SemiBold))
        }
        TmButton(
            tr("Spatenstich · {cost}", "cost" to formatEuro(rules.ceremonyCost)),
            { onCommand(GameCommand.HoldCeremony) },
            kind = ButtonKind.PRIMARY,
        )
    }
}

/** Die letzten sieben Ausgaben als Punkte auf einer Skala von bissig bis freundlich. */
@Composable
private fun ToneHistory(
    editions: List<NewspaperEdition>,
    firstDay: Int,
) {
    val last = editions.takeLast(HISTORY_DAYS)
    if (last.isEmpty()) {
        BasicText(tr("Noch keine Ausgabe – die erste erscheint nach dem ersten Spieltag."), style = TmType.small)
        return
    }
    // Fünf Stufen, dieselben Wörter wie in der Kachel „Ton der Zeitung“ – je über der Mitte ihres Bereichs
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(DAY_WIDTH.dp))
        Row(Modifier.weight(1f)) {
            for (label in listOf(tr("Verriss"), tr("bissig"), tr("neutral"), tr("wohlwollend"), tr("Jubelpresse"))) {
                BasicText(
                    label,
                    style = TmType.caption.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.width(TONE_WIDTH.dp))
    }
    for (edition in last) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                tr("Tag {day}", "day" to edition.day - firstDay + 1),
                style = TmType.caption,
                modifier = Modifier.width(DAY_WIDTH.dp),
            )
            BoxWithConstraints(Modifier.weight(1f).height(DOT_ROW.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(TmColors.LineSoft),
                )
                val share = ((edition.toneValue + 1) / 2).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (maxWidth - DOT.dp) * share)
                        .size(DOT.dp)
                        .background(lerp(TmColors.Danger, TmColors.Success, share), CircleShape),
                )
            }
            BasicText(edition.tone, style = TmType.caption, modifier = Modifier.width(TONE_WIDTH.dp), maxLines = 1)
        }
    }
}

/** Jede Ausgabe mit Tag, Schlagzeile und Ton, die neueste zuerst; ein Klick klappt die Zeitung auf. */
@Composable
private fun Archive(
    editions: List<NewspaperEdition>,
    firstDay: Int,
) {
    var open by remember { mutableStateOf<Int?>(null) }
    if (editions.isEmpty()) {
        BasicText(tr("Noch keine Ausgabe."), style = TmType.small)
        return
    }
    for (edition in editions.asReversed()) {
        val expanded = open == edition.day
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (expanded) TmColors.PrimaryTint else TmColors.Surface)
                .clickable(role = Role.Button) { open = if (expanded) null else edition.day }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                tr("Tag {day}", "day" to edition.day - firstDay + 1),
                style = TmType.mono(12.sp, bold = false, color = TmColors.Muted),
            )
            BasicText(
                edition.headlines.firstOrNull().orEmpty(),
                style = TmType.text(13.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            BasicText(edition.tone, style = TmType.caption)
        }
        if (expanded) ComicNewspaper(edition, Modifier.padding(vertical = 6.dp), firstDay)
    }
}

/** Grobe Tonlage zu einem Zielwert der PR-Agentur. */
private fun toneWord(tone: Double): String =
    when {
        tone >= FRIENDLY -> tr("freundlich")
        tone >= 0 -> tr("wohlwollend")
        else -> tr("kritisch")
    }

private const val HOURS_PER_DAY = 24
private const val PERCENT = 100
private const val HISTORY_DAYS = 7
private const val DAY_WIDTH = 48
private const val TONE_WIDTH = 96
private const val DOT_ROW = 16
private const val DOT = 12
private const val FRIENDLY = 0.5
