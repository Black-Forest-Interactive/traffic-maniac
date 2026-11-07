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
    val bridge: Boolean = false,
    val maxWeightTons: Float? = null,
    val maxWidthMeters: Float? = null,
    val maxHeightMeters: Float? = null,
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

        return withStructure(
            RoadAttributes(
                lanesForward = forward,
                lanesBackward = backward,
                maxSpeedKmh = parseMaxSpeed(tags["maxspeed"]) ?: defaultSpeed(roadClass),
                reversed = reversed,
                name = tags["name"] ?: tags["ref"],
            ),
            tags,
        )
    }

    /** Brücke und Abmessungen (Gewicht, Breite, Höhe) – wichtig für LKW auf Umleitungen. */
    private fun withStructure(
        attributes: RoadAttributes,
        tags: Map<String, String>,
    ) = attributes.copy(
        bridge = tags["bridge"].let { it != null && it != "no" },
        maxWeightTons = parseDimension(tags["maxweight"]),
        maxWidthMeters = parseDimension(tags["maxwidth"] ?: tags["maxwidth:physical"]),
        maxHeightMeters = parseDimension(tags["maxheight"] ?: tags["maxheight:physical"]),
    )

    /** Liest Abmessungen wie "3.5", "3,5 t" oder "2.2 m". "none", "default" und Fußangaben ergeben `null`. */
    fun parseDimension(value: String?): Float? {
        val normalized = value?.trim()?.lowercase()?.replace(',', '.') ?: return null
        if ('\'' in normalized || '"' in normalized) return null
        return normalized
            .takeWhile { it.isDigit() || it == '.' }
            .toFloatOrNull()
            ?.takeIf { it > 0f }
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
