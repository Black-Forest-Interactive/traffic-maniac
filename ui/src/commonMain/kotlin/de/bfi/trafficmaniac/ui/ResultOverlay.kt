package de.bfi.trafficmaniac.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.ui.map.NoSprites
import de.bfi.trafficmaniac.ui.map.SpriteSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Gold = Color(0xFFFFC300)
private const val HONORARY_CITIZEN = 70.0
private const val PRESS_DARLING = 60.0
private const val PRESS_HATED = 30.0
private const val MANY_CAMERAS = 5
private const val STAMP_DEGREES = -12f
private const val SEAL_POINTS = 16
private const val SEAL_INNER = 0.82f
private const val SEAL_CENTER = 0.45f
private const val SEAL_INK = 2f
private const val SEAL_SIZE = 64
private const val STAMP_WIDTH = 330

/** Spielende: abgewählt oder Amtszeit vorbei – im zweiten Fall mit Abschiedsrede, Wertung und (vielleicht) Urkunde. */
@Composable
fun ResultOverlay(
    state: GameState,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    sprites: SpriteSource = NoSprites,
) {
    val recalled = state.isGameOver
    val text = TextStyle(color = ComicTheme.ink, fontSize = 14.sp)
    Box(modifier.fillMaxSize().background(ComicTheme.scrim), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(620.dp).comicPanel(shadow = 8.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                if (recalled) "Abgewählt!" else "Abschiedsrede nach ${state.termDays} Tagen im Amt",
                style =
                    TextStyle(
                        color = if (recalled) ComicTheme.red else ComicTheme.ink,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    ),
            )
            // Die Rede als Sprechblase
            Column(
                Modifier.comicPanel(background = ComicTheme.paperDark, shadow = 3.dp).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (line in farewellSpeech(state)) {
                    BasicText(line, style = text.copy(fontStyle = FontStyle.Italic, fontSize = 15.sp))
                }
            }
            BasicText(
                "Kasse: ${formatEuro(state.balance)} · selbst verdient: ${formatEuro(state.score.earnings)} · " +
                    "Bußgelder: ${formatEuro(state.fineIncome)}",
                style = text,
            )
            BasicText(
                "Reputation: ${state.reputation.roundToInt()} / 100 (Faktor ×${"%.2f".format(
                    state.score.reputationFactor,
                )})",
                style = text,
            )
            if (state.achievements.isNotEmpty()) {
                BasicText("Achievements: ${state.achievements.joinToString()}", style = text)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!recalled) {
                    BasicText(
                        "Punkte: ${state.score.points}",
                        style = text.copy(fontSize = 24.sp, fontWeight = FontWeight.Black),
                    )
                }
                Certificate(
                    honorary = !recalled && state.reputation >= HONORARY_CITIZEN,
                    recalled = recalled,
                    sprites = sprites,
                )
            }
            onMenu?.let { BackToMenu(it, Modifier.align(Alignment.End)) }
        }
    }
}

@Composable
private fun BackToMenu(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicText(
        "Zurück zum Menü",
        style = TextStyle(color = ComicTheme.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
        modifier =
            modifier
                .comicButton(
                    active = true,
                ).clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Urkunde: goldenes Siegel für die Ehrenbürgerschaft, sonst ein schiefer roter Stempel. */
@Composable
private fun Certificate(
    honorary: Boolean,
    recalled: Boolean,
    sprites: SpriteSource,
) {
    if (honorary) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpriteIcon(sprites, "seal_honorary", SEAL_SIZE.dp) { DrawnSeal() }
            BasicText(
                "Ehrenbürgerschaft verliehen!",
                style = TextStyle(color = ComicTheme.ink, fontSize = 18.sp, fontWeight = FontWeight.Black),
            )
        }
        return
    }
    val text = if (recalled) "ABGEWÄHLT" else "EHRENBÜRGERSCHAFT ABGELEHNT"
    val style = TextStyle(color = ComicTheme.red, fontSize = 18.sp, fontWeight = FontWeight.Black)
    if (sprites.has("stamp_rejected")) {
        // Stempelrahmen als Grafik, der Text kommt vom Spiel
        Box(Modifier.rotate(STAMP_DEGREES), contentAlignment = Alignment.Center) {
            SpriteIcon(sprites, "stamp_rejected", STAMP_WIDTH.dp)
            BasicText(text, style = style)
        }
    } else {
        BasicText(
            text,
            style = style,
            modifier =
                Modifier
                    .rotate(
                        STAMP_DEGREES,
                    ).comicButton(active = false)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Gezeichnetes goldenes Siegel (wenn es keine Grafik gibt). */
@Composable
private fun DrawnSeal() {
    Canvas(Modifier.size(SEAL_SIZE.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        val seal = Path()
        for (k in 0 until SEAL_POINTS * 2) {
            val radius = if (k % 2 == 0) r else r * SEAL_INNER
            val angle = k * PI / SEAL_POINTS
            val p = c + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
            if (k == 0) seal.moveTo(p.x, p.y) else seal.lineTo(p.x, p.y)
        }
        seal.close()
        drawPath(seal, Gold)
        drawPath(seal, ComicTheme.ink, style = Stroke(SEAL_INK))
        drawCircle(ComicTheme.red, r * SEAL_CENTER, c)
    }
}

/** Die Abschiedsrede: Bilanz, Blitzer, Presse und Achievements – Hauptsache, es klingt gut. */
internal fun farewellSpeech(state: GameState): List<String> {
    val lines = ArrayList<String>()
    lines += "„${state.city.greeting}"
    lines +=
        if (state.score.earnings > 0) {
            "in meiner Amtszeit habe ich ${formatEuro(state.score.earnings)} für die Stadtkasse erwirtschaftet – " +
                "jeder Euro floss in die Sicherheit unserer Kinder."
        } else {
            "die Kasse ist leer, aber die Straßen sind sicher. Sehr sicher. Sie stehen still."
        }
    if (state.cameras.size >= MANY_CAMERAS) {
        lines += "${state.cameras.size} Blitzer wachen heute über Ihre Geschwindigkeit. Gern geschehen."
    }
    if (state.licenseRevocations > 0) {
        lines += "${state.licenseRevocations} Mitbürger haben dank mir das Radfahren für sich entdeckt."
    }
    lines +=
        when {
            state.reputation >= PRESS_DARLING -> "Die Presse hat mich stets fair und begeistert begleitet."
            state.reputation <= PRESS_HATED -> "Die Presse war nicht immer fair. Eigentlich nie."
            else -> "Mit der Presse hatte ich ein professionelles Verhältnis."
        }
    state.achievements.firstOrNull()?.let { lines += "Und vergessen Sie nie: „$it“ – das war ich." }
    lines += "Ich hinterlasse diese Stadt besser, als ich sie vorgefunden habe.“"
    return lines
}
