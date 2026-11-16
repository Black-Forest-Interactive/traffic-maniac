package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.bfi.trafficmaniac.game.CameraRecord
import de.bfi.trafficmaniac.game.GameState
import de.bfi.trafficmaniac.game.formatEuro
import de.bfi.trafficmaniac.sim.FLAG_JAMMED
import de.bfi.trafficmaniac.sim.SimulationSnapshot
import de.bfi.trafficmaniac.sim.SpeedCamera
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Wutwolken: Wo viele Autos lange stehen (Raster aus 200-m-Zellen), schwebt eine dunkle Wolke mit rotem Blitz.
 * Je mehr Wartende, desto größer die Wolke; sie wabert leicht. Die Wolken werden je Snapshot einmal gesucht.
 */
internal class AngerClouds {
    private var source: SimulationSnapshot? = null
    private var sourceCell = 0f
    private var clouds: List<Cloud> = emptyList()
    private val path = Path()

    /** Mittelpunkt der Wartenden einer Zelle, ihre Zahl und ein Startwert fürs Wabern. */
    private class Cloud(
        val x: Float,
        val y: Float,
        val count: Int,
        val seed: Long,
    )

    fun draw(
        scope: DrawScope,
        snapshot: SimulationSnapshot,
        scale: Float,
        seconds: Double,
        sprites: SpriteSource = NoSprites,
    ) = with(scope) {
        // Herausgezoomt werden die Zellen größer: weniger, dafür größere Wolken
        var cell = CELL_METERS
        while (cell * scale < CELL_MIN_PIXELS) cell *= 2
        if (snapshot !== source || cell != sourceCell) find(snapshot, cell)
        for (c in clouds) {
            val center = Offset(c.x, c.y + max(CLOUD_LIFT_METERS, CLOUD_LIFT_PIXELS / scale))
            val growth = (1 + (c.count - CLOUD_MIN_VEHICLES) * CLOUD_GROWTH).coerceAtMost(CLOUD_MAX)
            // Nah dran nicht größer als ein paar Autos, damit der Stau darunter sichtbar bleibt
            val size = (max(CLOUD_METERS, CLOUD_MIN_PIXELS / scale) * growth).coerceAtMost(CLOUD_MAX_PIXELS / scale)
            val wobble = sin(seconds * WOBBLE_SPEED + c.seed % WOBBLE_SEEDS).toFloat() * size * WOBBLE
            val at = center + Offset(wobble, 0f)
            if (!drawSprite(sprites, "anger_cloud", at, size * 2, size * CLOUD_SPRITE_HEIGHT, scale)) {
                cloud(at, size, scale)
                lightning(path, center + Offset(wobble, -size * BOLT_DROP), size)
            }
        }
    }

    private fun find(
        snapshot: SimulationSnapshot,
        cellMeters: Float,
    ) {
        source = snapshot
        sourceCell = cellMeters
        val cells = HashMap<Long, IntArray>()
        for (i in 0 until snapshot.vehicleCount) {
            if (snapshot.flags[i].toInt() and FLAG_JAMMED == 0) continue
            val cx = floor(snapshot.x[i] / cellMeters).toLong()
            val cy = floor(snapshot.y[i] / cellMeters).toLong()
            cells.getOrPut((cx shl CELL_SHIFT) xor (cy and CELL_MASK)) { intArrayOf(0, 0, 0) }.apply {
                this[0]++
                this[1] += snapshot.x[i].toInt()
                this[2] += snapshot.y[i].toInt()
            }
        }
        clouds =
            cells
                .filter { it.value[0] >= CLOUD_MIN_VEHICLES }
                .map { (key, cell) -> Cloud(cell[1].toFloat() / cell[0], cell[2].toFloat() / cell[0], cell[0], key) }
    }
}

/** Eine einzelne Wutwolke mit Blitz (z. B. für die Legende). */
internal fun DrawScope.drawAngerCloud(
    center: Offset,
    size: Float,
    scale: Float,
    sprites: SpriteSource = NoSprites,
) {
    if (drawSprite(sprites, "anger_cloud", center, size * 2, size * CLOUD_SPRITE_HEIGHT, scale)) return
    cloud(center, size, scale)
    lightning(Path(), center + Offset(0f, -size * BOLT_DROP), size)
}

private fun DrawScope.cloud(
    c: Offset,
    size: Float,
    scale: Float,
) {
    val ink = Stroke(max(INK_METERS, 1.5f / scale))
    for ((dx, dy, r) in PUFFS) {
        drawCircle(MapStyle.angerCloud, size * r, c + Offset(dx * size, dy * size))
        drawCircle(MapStyle.ink, size * r, c + Offset(dx * size, dy * size), style = ink)
    }
    // Innen noch einmal ohne Umriss füllen, damit nur der Außenrand gezeichnet bleibt
    for ((dx, dy, r) in PUFFS) drawCircle(MapStyle.angerCloud, size * r * INNER, c + Offset(dx * size, dy * size))
}

private fun DrawScope.lightning(
    path: Path,
    top: Offset,
    size: Float,
) {
    path.reset()
    BOLT.forEachIndexed { k, (dx, dy) ->
        if (k ==
            0
        ) {
            path.moveTo(top.x + dx * size, top.y + dy * size)
        } else {
            path.lineTo(top.x + dx * size, top.y + dy * size)
        }
    }
    path.close()
    drawPath(path, MapStyle.bolt)
    drawPath(path, MapStyle.ink, style = Stroke(size * BOLT_INK))
}

/** Merkt sich, wann welcher Blitzer zuletzt ausgelöst hat (Zähler steigt zwischen zwei Frames). */
internal class FlashTracker {
    private val counts = HashMap<Int, Int>()
    private val flashedAt = HashMap<Int, Double>()

    /** Liefert die Zahl neuer Messungen seit dem letzten Aufruf (für den Ton). */
    fun update(
        cameras: List<CameraRecord>,
        seconds: Double,
    ): Int {
        var fresh = 0
        for (camera in cameras) {
            val before = counts[camera.cameraId]
            if (before != null && camera.totalCount > before) {
                flashedAt[camera.cameraId] = seconds
                fresh += camera.totalCount - before
            }
            counts[camera.cameraId] = camera.totalCount
        }
        return fresh
    }

    /** Alter des letzten Blitzes in Sekunden, `null` = kein Blitz in letzter Zeit. */
    fun age(
        cameraId: Int,
        seconds: Double,
    ): Double? = flashedAt[cameraId]?.let { seconds - it }?.takeIf { it < FLASH_SECONDS }
}

/** Blitzer-Blitz: gelb-weißer Strahlenkranz, der in einer halben Sekunde verglüht. */
internal fun DrawScope.drawCameraFlashes(
    cameras: List<SpeedCamera>,
    tracker: FlashTracker,
    scale: Float,
    seconds: Double,
    sprites: SpriteSource = NoSprites,
) {
    val size = max(FLASH_METERS, FLASH_MIN_PIXELS / scale)
    for (camera in cameras) {
        val age = tracker.age(camera.id, seconds) ?: continue
        val progress = (age / FLASH_SECONDS).toFloat()
        val alpha = 1 - progress
        val c = Offset(camera.x, camera.y)
        val grown = size * (FLASH_SPRITE + progress)
        if (drawSprite(sprites, "camera_flash", c, grown, grown, scale, alpha = alpha)) continue
        drawCircle(MapStyle.cameraFlash.copy(alpha = alpha * FLASH_GLOW), size * (FLASH_CORE + progress), c)
        for (k in 0 until RAYS) {
            val angle = k * 2 * PI / RAYS
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                Color.White.copy(alpha = alpha),
                c + dir * (size * RAY_START),
                c + dir * (size * (RAY_START + RAY_LENGTH * (1 + progress))),
                size * RAY_WIDTH,
            )
        }
    }
}

private const val CELL_METERS = 200f
private const val CLOUD_SPRITE_HEIGHT = 1.5f
private const val CELL_MIN_PIXELS = 70f
private const val CELL_SHIFT = 32
private const val CELL_MASK = 0xFFFFFFFFL
private const val CLOUD_MIN_VEHICLES = 6
private const val CLOUD_METERS = 22f
private const val CLOUD_MIN_PIXELS = 18f
private const val CLOUD_MAX_PIXELS = 90f
private const val CLOUD_LIFT_METERS = 25f
private const val CLOUD_LIFT_PIXELS = 22f
private const val CLOUD_GROWTH = 0.04f
private const val CLOUD_MAX = 2.2f
private const val WOBBLE_SPEED = 1.5
private const val WOBBLE_SEEDS = 17
private const val WOBBLE = 0.06f
private const val INK_METERS = 0.6f
private const val INNER = 0.82f
private const val BOLT_DROP = 0.2f
private const val BOLT_INK = 0.04f
private const val FLASH_SECONDS = 0.5
private const val FLASH_METERS = 14f
private const val FLASH_MIN_PIXELS = 26f
private const val FLASH_GLOW = 0.8f
private const val FLASH_CORE = 0.6f
private const val FLASH_SPRITE = 1.6f
private const val RAYS = 8
private const val RAY_START = 0.7f
private const val RAY_LENGTH = 0.6f
private const val RAY_WIDTH = 0.12f

/** Wolkenbausteine: Versatz x, y und Radius relativ zur Wolkengröße. */
private val PUFFS =
    listOf(
        Triple(-0.45f, 0f, 0.38f),
        Triple(0f, 0.15f, 0.5f),
        Triple(0.45f, 0f, 0.4f),
        Triple(0.2f, -0.18f, 0.35f),
        Triple(-0.2f, -0.18f, 0.35f),
    )

/** Blitz im Comic-Stil: Zickzack nach unten, relativ zur Wolkengröße. */
private val BOLT =
    listOf(
        -0.1f to 0f,
        0.15f to 0f,
        0f to -0.3f,
        0.2f to -0.3f,
        -0.15f to -0.8f,
        -0.02f to -0.42f,
        -0.2f to -0.42f,
    )

/**
 * Aufsteigende Beträge über Blitzern („+30 €“): Einnahmen je Blitzer werden höchstens einmal je [POP_EVERY_SECONDS]
 * gebündelt (bei 64× sonst ein Zahlenregen), steigen auf und verblassen in [POP_SECONDS].
 */
internal class MoneyPops {
    private class Pop(
        val x: Float,
        val y: Float,
        val euros: Long,
        val born: Double,
    )

    private val revenue = HashMap<Int, Long>()
    private val pending = HashMap<Int, Long>()
    private val lastPop = HashMap<Int, Double>()
    private val pops = ArrayList<Pop>()

    fun update(
        records: List<CameraRecord>,
        cameras: List<SpeedCamera>,
        seconds: Double,
    ) {
        for (record in records) {
            val before = revenue[record.cameraId]
            if (before != null && record.totalRevenue > before) {
                pending[record.cameraId] = (pending[record.cameraId] ?: 0L) + record.totalRevenue - before
            }
            revenue[record.cameraId] = record.totalRevenue
        }
        val byId = cameras.associateBy { it.id }
        for ((id, euros) in pending.entries.toList()) {
            if (seconds - (lastPop[id] ?: Double.NEGATIVE_INFINITY) < POP_EVERY_SECONDS) continue
            val camera = byId[id] ?: continue
            pops += Pop(camera.x, camera.y, euros, seconds)
            lastPop[id] = seconds
            pending.remove(id)
        }
        pops.removeAll { seconds - it.born > POP_SECONDS }
    }

    fun draw(
        scope: DrawScope,
        textMeasurer: TextMeasurer,
        scale: Float,
        seconds: Double,
    ) = with(scope) {
        for (pop in pops) {
            val age = ((seconds - pop.born) / POP_SECONDS).toFloat().coerceIn(0f, 1f)
            val label =
                textMeasurer.measure(
                    "+" + formatEuro(pop.euros),
                    TextStyle(
                        fontSize = POP_FONT_SIZE.sp,
                        fontWeight = FontWeight.Black,
                        color =
                            MapStyle.moneyPop.copy(
                                alpha =
                                    1 - age,
                            ),
                    ),
                )
            // In Bildschirmpunkten: steigt um POP_RISE Pixel, Weltskalierung (y nach Norden) aufheben
            withTransform({
                translate(pop.x, pop.y + (POP_START + age * POP_RISE) / scale)
                scale(1 / scale, -1 / scale, pivot = Offset.Zero)
            }) {
                drawText(label, topLeft = Offset(-label.size.width / 2f, -label.size.height / 2f))
            }
        }
    }

    private companion object {
        const val POP_SECONDS = 1.6
        const val POP_EVERY_SECONDS = 1.0
        const val POP_FONT_SIZE = 13
        const val POP_START = 18f
        const val POP_RISE = 34f
    }
}

/**
 * „Plopp“ beim Bauen: Wo seit dem letzten Bild ein Blitzer, ein Schild, eine Ampel oder eine Markierung dazugekommen
 * ist, geht kurz eine Staubwolke auf (Ringe wachsen und verblassen in [PUFF_SECONDS]). Beim ersten Bild – und nach
 * dem Laden – wird nur gemerkt, nicht gestaubt.
 */
internal class BuildPuffs {
    private class Puff(
        val x: Float,
        val y: Float,
        val born: Double,
    )

    private var known: Set<String>? = null
    private var source: GameState? = null
    private val puffs = ArrayList<Puff>()

    fun update(
        game: GameState,
        cameras: List<SpeedCamera>,
        picker: MapPicker,
        seconds: Double,
    ) {
        puffs.removeAll { seconds - it.born > PUFF_SECONDS }
        // Der Spielstand wechselt nur je Schritt, nicht je Bild
        if (game === source) return
        source = game
        val now = HashMap<String, Pair<Float, Float>?>()
        for (camera in cameras) now["c${camera.id}"] = camera.x to camera.y
        val segments =
            listOf(
                "l" to game.speedLimits.keys,
                "n" to game.nightLimits.keys,
                "p" to game.priorityRoads,
                "b" to game.bikeCrossings,
                "s" to game.bikeStreets,
            )
        for ((prefix, ids) in segments) for (id in ids) now["$prefix$id"] = null
        for (node in game.signalNodes) now["a$node"] = null
        val before = known
        if (before != null) {
            for (key in now.keys - before) {
                val at = now[key] ?: positionOf(key, picker) ?: continue
                puffs += Puff(at.first, at.second, seconds)
            }
        }
        known = now.keys
    }

    private fun positionOf(
        key: String,
        picker: MapPicker,
    ): Pair<Float, Float>? {
        val id = key.drop(1).toIntOrNull() ?: return null
        return if (key[0] == 'a') {
            picker.city.roads.nodes
                .getOrNull(id)
                ?.let { it.x to it.y }
        } else {
            picker.midpoint(id)
        }
    }

    fun draw(
        scope: DrawScope,
        scale: Float,
        seconds: Double,
    ) = with(scope) {
        val size = max(PUFF_METERS, PUFF_MIN_PIXELS / scale)
        for (puff in puffs) {
            val age = ((seconds - puff.born) / PUFF_SECONDS).toFloat().coerceIn(0f, 1f)
            val alpha = (1 - age) * PUFF_ALPHA
            val c = Offset(puff.x, puff.y)
            for (k in 0 until PUFF_BLOBS) {
                val angle = k * 2 * PI / PUFF_BLOBS
                val reach = size * (PUFF_START + age * PUFF_GROWTH)
                val at = c + Offset(cos(angle).toFloat() * reach, sin(angle).toFloat() * reach)
                drawCircle(MapStyle.dust.copy(alpha = alpha), size * (PUFF_BLOB + age * PUFF_BLOB), at)
            }
        }
    }

    private companion object {
        const val PUFF_SECONDS = 0.7
        const val PUFF_METERS = 6f
        const val PUFF_MIN_PIXELS = 10f
        const val PUFF_ALPHA = 0.75f
        const val PUFF_BLOBS = 7
        const val PUFF_START = 0.4f
        const val PUFF_GROWTH = 1.4f
        const val PUFF_BLOB = 0.35f
    }
}
