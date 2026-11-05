package de.bfi.trafficmaniac.osmimport

/** Was ein OSM-Weg in der Kulisse darstellt. */
enum class SceneryKind(
    val isArea: Boolean,
) {
    BUILDING(isArea = true),
    MINOR_ROAD(isArea = false),
    MOTORWAY(isArea = false),
    WATER(isArea = true),
    WATERWAY(isArea = false),
    GREEN(isArea = true),
    ;

    companion object {
        private val minorHighways =
            setOf("tertiary", "tertiary_link", "unclassified", "residential", "living_street", "service", "pedestrian")
        private val motorways = setOf("motorway", "motorway_link")
        private val waterLanduse = setOf("reservoir", "basin")
        private val waterways = setOf("river", "canal", "stream")
        private val greenLanduse =
            setOf(
                "forest",
                "grass",
                "meadow",
                "recreation_ground",
                "cemetery",
                "allotments",
                "orchard",
                "vineyard",
                "village_green",
            )
        private val greenLeisure = setOf("park", "garden", "playground", "pitch", "golf_course")
        private val greenNatural = setOf("wood", "scrub", "grassland", "heath")

        fun classify(tags: Map<String, String>): SceneryKind? {
            val highway = tags["highway"]
            val building = tags["building"]
            return when {
                building != null && building != "no" -> BUILDING
                highway in minorHighways -> MINOR_ROAD
                highway in motorways -> MOTORWAY
                isWater(tags) -> WATER
                tags["waterway"] in waterways -> WATERWAY
                isGreen(tags) -> GREEN
                else -> null
            }
        }

        private fun isWater(tags: Map<String, String>) =
            tags["natural"] == "water" || tags["waterway"] == "riverbank" || tags["landuse"] in waterLanduse

        private fun isGreen(tags: Map<String, String>) =
            tags["landuse"] in greenLanduse || tags["leisure"] in greenLeisure || tags["natural"] in greenNatural
    }
}
