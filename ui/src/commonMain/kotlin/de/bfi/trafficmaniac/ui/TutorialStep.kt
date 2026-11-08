package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.PrContract
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.i18n.translatable
import de.bfi.trafficmaniac.ui.components.ButtonKind
import de.bfi.trafficmaniac.ui.components.Meter
import de.bfi.trafficmaniac.ui.components.TmButton

/**
 * Die Schritte des Tutorials. Jeder Schritt schaltet Werkzeuge frei ([tools]) und ist entweder erledigt, sobald
 * [done] zutrifft, oder – ohne Bedingung – mit „Weiter“. Schritte, deren Ziel schon erreicht ist (z. B. nach dem
 * Laden eines Spielstands), werden übersprungen.
 */
enum class TutorialStep(
    private val titleText: String,
    private val textText: String,
    val tools: Set<Tool> = emptySet(),
    val done: ((GameState) -> Boolean)? = null,
) {
    WELCOME(
        translatable("Willkommen im Rathaus!"),
        translatable(
            "Sie sind der neue Verkehrsbürgermeister. Ihr Ziel: Geld für die Stadtkasse verdienen, gut dastehen " +
                "und nicht abgewählt werden. Mausrad zoomt, Ziehen verschiebt die Karte. Leertaste hält die Zeit an.",
        ),
        setOf(Tool.LOOK),
    ),
    SPEED_LIMIT(
        translatable("Erst die Schilder"),
        translatable(
            "Wählen Sie unten das Werkzeug „Tempolimit“ – 30 ist schon eingestellt. Klicken Sie auf den Anfang und " +
                "dann auf das Ende eines Stücks einer großen Straße (zweimal dieselbe Stelle: bis zur nächsten " +
                "Kreuzung). Alles ist erst geplant – gebaut und bezahlt wird mit „Bauen“ über dem Werkzeugkasten.",
        ),
        setOf(Tool.SPEED_LIMIT, Tool.REMOVE),
        { it.speedLimits.isNotEmpty() },
    ),
    CAMERA(
        translatable("Dann der Blitzer"),
        translatable(
            "Wo ein Limit ist, darf geblitzt werden. Nehmen Sie „Blitzer“, klicken Sie auf dieselbe Straße und " +
                "dann auf „Bauen“. Ein fester Blitzer ist teuer, verdient aber Tag und Nacht.",
        ),
        setOf(Tool.CAMERA),
        { it.cameras.isNotEmpty() },
    ),
    FINES(
        translatable("Abwarten und kassieren"),
        translatable(
            "Taste 4 schaltet auf achtfaches Tempo. Sobald der erste Raser geblitzt wird, klingelt die Kasse – " +
                "die Bußgelder stehen oben links.",
        ),
        done = { it.fineIncome > 0 },
    ),
    CITIZENS(
        translatable("Die Bürger schauen zu"),
        translatable(
            "Oben links stehen auch Bürgerzorn, Abwahlindex und Reputation. Zu viel Schikane, und der Abwahlindex " +
                "steigt – bei 100 sind Sie Ihr Amt los. Der Knopf „Zufriedenheit“ zeigt, wo es brodelt.",
        ),
    ),
    PRESS(
        translatable("Gute Presse kaufen"),
        translatable(
            "Öffnen Sie oben rechts „Haushalt & PR“ und schließen Sie einen PR-Vertrag ab. Dann schreibt die Zeitung " +
                "freundlicher, und der Ärger der Bürger zählt weniger.",
        ),
        done = { it.prContract != PrContract.NONE },
    ),
    TOOLBOX(
        translatable("Der ganze Werkzeugkasten"),
        translatable(
            "Jetzt ist alles frei: Ampeln und Ampelprogramme (auch grüne Wellen), mobile Blitzer, Vorfahrt, " +
                "Radweg-Querungen, Umleitungen, Baustellen und Sperrungen. " +
                "Ampeln bremsen zuverlässig, Umleitungen ärgern alle. " +
                "Was ein Symbol bedeutet, steht unter „Legende ?“.",
        ),
        Tool.entries.toSet(),
    ),
    EVENING(
        translatable("Feierabend"),
        translatable(
            "Lassen Sie den Tag zu Ende laufen (nachts geht es von selbst schneller). Am Abend kommt der " +
                "Tagesabschluss, am nächsten Morgen die Zeitung.",
        ),
        done = { it.daysPlayed >= 1 },
    ),
    READY(
        translatable("Sie sind bereit"),
        translatable(
            "Der Rest der Amtszeit gehört Ihnen. Viel Erfolg – und denken Sie immer an die Sicherheit unserer Kinder.",
        ),
    ),
    ;

    val title: String get() = tr(titleText)
    val text: String get() = tr(textText)

    /** Werkzeuge, die bis einschließlich dieses Schritts frei sind. */
    val unlocked: Set<Tool> get() = entries.take(ordinal + 1).flatMap { it.tools }.toSet()

    /** Der nächste Schritt, oder `null` am Ende. */
    val next: TutorialStep? get() = entries.getOrNull(ordinal + 1)
}

/** Sprechblase mit dem aktuellen Schritt; erledigte Ziele schalten von selbst weiter. */
@Composable
internal fun TutorialPanel(
    step: TutorialStep,
    state: GameState,
    onStep: (TutorialStep?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reached = step.done?.invoke(state) == true
    LaunchedEffect(step, reached) { if (reached) onStep(step.next) }
    val ink = ComicTheme.ink
    Column(
        modifier
            .padding(start = 16.dp)
            .width(PANEL_WIDTH.dp)
            .comicPanel(shadow = 5.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Fortschritt als Balken wie im Entwurf
        val total = TutorialStep.entries.size
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(
                tr("Tutorial {ordinal}/{entries}", "ordinal" to step.ordinal + 1, "entries" to total),
                style = TmType.caption,
            )
            Meter((step.ordinal + 1f) / total, Modifier.weight(1f), height = 6.dp)
        }
        BasicText(step.title, style = TmType.display(17.sp, ink))
        BasicText(step.text, style = TmType.text(13.sp, color = ink))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step.done == null) {
                val label = if (step.next == null) tr("Los geht's") else tr("Weiter")
                TmButton(label, { onStep(step.next) }, kind = ButtonKind.PRIMARY)
            } else {
                BasicText(tr("Aufgabe läuft …"), style = TmType.small)
            }
            Box(Modifier.weight(1f))
            TmButton(tr("Tutorial beenden"), { onStep(null) }, kind = ButtonKind.TEXT)
        }
    }
}

private const val PANEL_WIDTH = 340
