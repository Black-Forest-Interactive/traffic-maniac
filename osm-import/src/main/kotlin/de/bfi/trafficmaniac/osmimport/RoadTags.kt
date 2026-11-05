package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.RoadClass
import kotlin.math.roundToInt

/** Aus den OSM-Tags eines Wegs abgeleitete Eigenschaften einer Straße. */
data class RoadAttributes(
    val lanesForward: Int,
    val lanesBackward: Int,
    val maxSpeedKmh: Int,
    /** `oneway=-1`: Die Einbahnrichtung läuft entgegen der Knotenreihenfolge. */
    val reversed: Boolean,
    val name: String?,
)

object RoadTags {
    const val DEFAULT_SPEED_KMH = 50
    const val MOTORWAY_SPEED_KMH = 130
    private const val KMH_PER_MPH = 1.609344

    private val namedSpeeds =
        mapOf(
            "de:urban" to 50,
            "de:rural" to 100,
            "de:motorway" to MOTORWAY_SPEED_KMH,
            "none" to MOTORWAY_SPEED_KMH,
            "de:zone30" to 30,
            "de:zone:30" to 30,
            "de:living_street" to 7,
            "walk" to 7,
            "de:walk" to 7,
        )

    private val impliedOneway = setOf(RoadClass.MOTORWAY, RoadClass.MOTORWAY_LINK)
    private val multiLaneOneway = setOf(RoadClass.MOTORWAY, RoadClass.TRUNK)

    fun parse(
        tags: Map<String, String>,
        roadClass: RoadClass,
    ): RoadAttributes {
        val onewayTag = tags["oneway"]
        val reversed = onewayTag == "-1"
        val oneway =
            reversed ||
                onewayTag in setOf("yes", "true", "1") ||
                tags["junction"] in setOf("roundabout", "circular") ||
                (roadClass in impliedOneway && onewayTag != "no")
        val totalLanes = tags["lanes"]?.toIntOrNull()?.takeIf { it > 0 }

        val (forward, backward) =
            if (oneway) {
                (totalLanes ?: if (roadClass in multiLaneOneway) 2 else 1) to 0
            } else {
                val total = totalLanes ?: 2
                val forwardLanes = tags["lanes:forward"]?.toIntOrNull() ?: ((total + 1) / 2)
                val backwardLanes = tags["lanes:backward"]?.toIntOrNull() ?: (total - forwardLanes)
                forwardLanes.coerceAtLeast(1) to backwardLanes.coerceAtLeast(1)
            }

        return RoadAttributes(
            lanesForward = forward,
            lanesBackward = backward,
            maxSpeedKmh = parseMaxSpeed(tags["maxspeed"]) ?: defaultSpeed(roadClass),
            reversed = reversed,
            name = tags["name"] ?: tags["ref"],
        )
    }

    /** Liest `maxspeed`-Werte wie "50", "30 mph" oder "DE:urban". Unbekanntes ergibt `null`. */
    fun parseMaxSpeed(value: String?): Int? {
        val normalized = value?.trim()?.lowercase() ?: return null
        val number = normalized.takeWhile { it.isDigit() }.toIntOrNull()
        return namedSpeeds[normalized]
            ?: number?.let { if (normalized.endsWith("mph")) (it * KMH_PER_MPH).roundToInt() else it }
    }

    private fun defaultSpeed(roadClass: RoadClass): Int =
        if (roadClass in
            impliedOneway
        ) {
            MOTORWAY_SPEED_KMH
        } else {
            DEFAULT_SPEED_KMH
        }
}
