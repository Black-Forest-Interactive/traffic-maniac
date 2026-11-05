package de.bfi.trafficmaniac.city

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CityMapCodecTest {
    private val map =
        CityMap(
            name = "Teststadt",
            attribution = "© OpenStreetMap-Mitwirkende",
            origin = GeoPoint(48.89, 8.69),
            bounds = Bounds(-100f, -50f, 100f, 50f),
            roads =
                RoadNetwork(
                    nodes = listOf(RoadNode(0, 0f, 0f, trafficSignals = true), RoadNode(1, 10f, 0f)),
                    segments =
                        listOf(
                            RoadSegment(
                                id = 0,
                                from = 0,
                                to = 1,
                                roadClass = RoadClass.PRIMARY,
                                geometry = Polyline(floatArrayOf(0f, 0f, 5f, 1f, 10f, 0f)),
                                lanesForward = 1,
                                lanesBackward = 1,
                                maxSpeedKmh = 50,
                                name = "Hauptstraße",
                            ),
                        ),
                ),
            scenery = Scenery(buildings = listOf(Polyline(floatArrayOf(1f, 1f, 2f, 1f, 2f, 2f, 1f, 1f)))),
        )

    @Test
    fun roundTripKeepsAllData() {
        val decoded = CityMapCodec.decode(CityMapCodec.encode(map))

        assertEquals(map, decoded)
    }

    @Test
    fun rejectsUnknownFormatVersion() {
        val bytes = CityMapCodec.encode(map.copy(formatVersion = CityMap.FORMAT_VERSION + 1))

        assertFailsWith<IllegalArgumentException> { CityMapCodec.decode(bytes) }
    }
}
