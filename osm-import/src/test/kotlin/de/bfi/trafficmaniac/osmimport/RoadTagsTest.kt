package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.RoadClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoadTagsTest {
    @Test
    fun parsesMaxSpeedVariants() {
        assertEquals(50, RoadTags.parseMaxSpeed("50"))
        assertEquals(30, RoadTags.parseMaxSpeed("DE:zone30"))
        assertEquals(50, RoadTags.parseMaxSpeed("DE:urban"))
        assertEquals(48, RoadTags.parseMaxSpeed("30 mph"))
        assertNull(RoadTags.parseMaxSpeed("signals"))
        assertNull(RoadTags.parseMaxSpeed(null))
    }

    @Test
    fun twoWayRoadSplitsLanes() {
        val tags = mapOf("lanes" to "3", "maxspeed" to "70", "name" to "Westliche")
        val attributes = RoadTags.parse(tags, RoadClass.PRIMARY)

        assertEquals(2, attributes.lanesForward)
        assertEquals(1, attributes.lanesBackward)
        assertEquals(70, attributes.maxSpeedKmh)
        assertEquals("Westliche", attributes.name)
    }

    @Test
    fun defaultsToOneLanePerDirectionAndUrbanSpeed() {
        val attributes = RoadTags.parse(emptyMap(), RoadClass.SECONDARY)

        assertEquals(1, attributes.lanesForward)
        assertEquals(1, attributes.lanesBackward)
        assertEquals(RoadTags.DEFAULT_SPEED_KMH, attributes.maxSpeedKmh)
    }

    @Test
    fun recognizesOnewayVariants() {
        assertEquals(0, RoadTags.parse(mapOf("oneway" to "yes"), RoadClass.PRIMARY).lanesBackward)
        assertEquals(0, RoadTags.parse(mapOf("junction" to "roundabout"), RoadClass.PRIMARY).lanesBackward)
        assertEquals(1, RoadTags.parse(emptyMap(), RoadClass.TRUNK).lanesBackward)
        assertTrue(RoadTags.parse(mapOf("oneway" to "-1"), RoadClass.SECONDARY).reversed)
        assertEquals(0, RoadTags.parse(emptyMap(), RoadClass.MOTORWAY_LINK).lanesBackward)
    }

    @Test
    fun parsesDimensions() {
        assertEquals(3.5f, RoadTags.parseDimension("3.5"))
        assertEquals(7.5f, RoadTags.parseDimension("7,5 t"))
        assertEquals(2.2f, RoadTags.parseDimension("2.2 m"))
        assertEquals(null, RoadTags.parseDimension("none"))
        assertEquals(null, RoadTags.parseDimension("12'6\""))
        assertEquals(null, RoadTags.parseDimension(null))
    }
}
