package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.bfi.trafficmaniac.game.CameraRecord
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
            cloud(center + Offset(wobble, 0f), size, scale)
            lightning(path, center + Offset(wobble, -size * BOLT_DROP), size)
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
) {
    val size = max(FLASH_METERS, FLASH_MIN_PIXELS / scale)
    for (camera in cameras) {
        val age = tracker.age(camera.id, seconds) ?: continue
        val progress = (age / FLASH_SECONDS).toFloat()
        val alpha = 1 - progress
        val c = Offset(camera.x, camera.y)
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
