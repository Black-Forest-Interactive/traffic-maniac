package de.bfi.trafficmaniac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.ui.TmColors
import de.bfi.trafficmaniac.ui.TmType

private val PanelShape = RoundedCornerShape(14.dp)

/**
 * Fenster nach dem Muster aller Ämter und Berichte: Kopf mit Eyebrow („Amt“, „Bericht“) und Display-Titel, rechts
 * optional eine Kennzahl ([headerExtra]) und das Schließen-Kreuz, darunter scrollbarer Inhalt aus [PanelSection]s.
 * [floating] = schwebendes Fenster mit Rahmen und Schatten; sonst füllt es den Platz (Seitenfenster rechts).
 */
@Composable
fun PanelScaffold(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    floating: Boolean = true,
    headerExtra: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val frame =
        if (floating) {
            Modifier
                .shadow(6.dp, PanelShape, spotColor = Shadow, ambientColor = Shadow)
                .background(TmColors.Surface, PanelShape)
                .border(1.dp, TmColors.Line, PanelShape)
        } else {
            Modifier.background(TmColors.Surface)
        }
    Column(modifier.then(frame)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(eyebrow.uppercase(), style = TmType.eyebrow)
                BasicText(title, style = TmType.display(22.sp))
            }
            headerExtra?.invoke(this)
            onClose?.let { CloseButton(it) }
        }
        Divider()
        Column(Modifier.verticalScroll(rememberScrollState()), content = content)
    }
}

/** Abschnitt in einem Fenster: 20 dp seitlich, optional mit Überschrift, unten eine feine Trennlinie. */
@Composable
fun PanelSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        title?.let { BasicText(it, style = TmType.text(14.sp, FontWeight.Bold)) }
        content()
    }
    if (divider) Divider()
}

/**
 * Zeile „Beschriftung links, Bedienelement rechts“ – z. B. Sachbearbeiter mit [Stepper]. [hint] steht klein darunter.
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = TmType.text(14.sp, FontWeight.Bold))
            hint?.let { BasicText(it, style = TmType.caption) }
        }
        control()
    }
}

/** Feine Trennlinie in Fenstern. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(TmColors.LineSoft))
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(TmColors.Surface, CircleShape).clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Close, tr("Schließen"), tint = TmColors.Muted, size = 20.dp)
    }
}

private val Shadow = TmColors.Ink.copy(alpha = 0.18f)
