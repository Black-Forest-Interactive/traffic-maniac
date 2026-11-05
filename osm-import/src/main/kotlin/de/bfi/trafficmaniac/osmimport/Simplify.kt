package de.bfi.trafficmaniac.osmimport

import kotlin.math.hypot

/**
 * Douglas-Peucker: entfernt Zwischenpunkte, die weniger als [tolerance] Meter von der Linie abweichen.
 * Erwartet und liefert flache Koordinaten `x0, y0, x1, y1, …`; Anfangs- und Endpunkt bleiben erhalten.
 */
fun simplify(
    coordinates: FloatArray,
    tolerance: Float,
): FloatArray {
    val count = coordinates.size / 2
    if (count <= 2) return coordinates

    val keep = BooleanArray(count)
    keep[0] = true
    keep[count - 1] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to count - 1)

    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast()
        var maxDistance = 0f
        var maxIndex = -1
        for (i in start + 1 until end) {
            val distance = distanceToSegment(coordinates, i, start, end)
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }
        if (maxIndex >= 0 && maxDistance > tolerance) {
            keep[maxIndex] = true
            stack.addLast(start to maxIndex)
            stack.addLast(maxIndex to end)
        }
    }

    val result = FloatArray(keep.count { it } * 2)
    var target = 0
    for (i in 0 until count) {
        if (keep[i]) {
            result[target++] = coordinates[i * 2]
            result[target++] = coordinates[i * 2 + 1]
        }
    }
    return result
}

private fun distanceToSegment(
    c: FloatArray,
    point: Int,
    start: Int,
    end: Int,
): Float {
    val px = c[point * 2]
    val py = c[point * 2 + 1]
    val ax = c[start * 2]
    val ay = c[start * 2 + 1]
    val dx = c[end * 2] - ax
    val dy = c[end * 2 + 1] - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
