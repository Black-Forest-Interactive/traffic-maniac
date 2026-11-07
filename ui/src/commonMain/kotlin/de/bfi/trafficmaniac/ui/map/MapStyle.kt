package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.graphics.Color
import de.bfi.trafficmaniac.city.RoadClass

/** Platzhalter-Kartenstil für Version 0.1. Der Cartoon-Look folgt in Version 0.7. */
object MapStyle {
    val land = Color(0xFFF2EFE9)
    val green = Color(0xFFCDE6B0)
    val water = Color(0xFFA9D3F0)
    val building = Color(0xFFD9D3CC)
    val minorRoad = Color(0xFFFFFFFF)
    val minorRoadOutline = Color(0xFFCFC9C1)
    val motorway = Color(0xFFB8B0A8)
    val roadOutline = Color(0xFF8C7B6B)
    val trafficSignal = Color(0xFFD7263D)
    val signalGreen = Color(0xFF2DC653)
    val signalYellow = Color(0xFFFFC300)
    val camera = Color(0xFF22252F)
    val blueLight = Color(0xFF00B4FF)
    val redLight = Color(0xFFFF2D55)
    val hazardLight = Color(0xFFFF9500)
    val oil = Color(0xCC4A3B2A)
    val chargingFree = Color(0xFF2DC653)
    val chargingFull = Color(0xFF8D99AE)
    val laser = Color(0xFF3A86FF)
    val priorityYellow = Color(0xFFFFC300)
    val bikeGreen = Color(0xFF2A9D8F)
    val nightSign = Color(0xFF3A3D9E)
    val moodGood = Color(0xFF2DC653)
    val heatLow = Color(0xFFFFE066)
    val heatHigh = Color(0xFF9D0208)
    val moodBad = Color(0xFFD7263D)
    val cameraFlash = Color(0xFFFFF3B0)
    val conditionGood = Color(0xFF2DC653)
    val conditionFair = Color(0xFFFFC300)
    val conditionBad = Color(0xFFD00000)
    val detour = Color(0xFFFF7B00)
    val eventClosure = Color(0xFFD81B9C)
    val corso = Color(0xFF2DC653)
    val heavyTransport = Color(0xFFFFD60A)
    val works = Color(0xFFFF7B00)
    val collapsed = Color(0xFF14141F)
    val pothole = Color(0xFF5C4033)
    val planning = Color(0xFF8D99AE)
    val parking = Color(0xFF1E6FD9)
    val stuckTruck = Color(0xFF5C6B7A)
    val attribution = Color(0xFF555555)
    val attributionBackground = Color(0xB3FFFFFF)

    const val WATERWAY_WIDTH_METERS = 12f
    const val MINOR_ROAD_WIDTH_METERS = 6f
    const val MOTORWAY_WIDTH_METERS = 20f
    const val OUTLINE_METERS = 1.5f
    const val SIGNAL_RADIUS_METERS = 4f

    /** Breiten unter dieser Pixelzahl werden angehoben, damit Linien beim Herauszoomen sichtbar bleiben. */
    const val MIN_LINE_PIXELS = 1f

    /** Simulierte Straßen bleiben auch in der Gesamtansicht deutlich sichtbar. */
    const val MIN_ROAD_PIXELS = 3f
    const val MIN_SIGNAL_PIXELS = 2.5f

    /** Ampeln erst ab dieser Zoomstufe (Pixel pro Meter) zeigen. */
    const val SIGNAL_MIN_SCALE = 0.4f

    data class RoadStyle(
        val fill: Color,
        val widthMeters: Float,
    )

    /** Von hinten nach vorn: untergeordnete Klassen zuerst zeichnen. */
    val roadClassesInDrawOrder: List<RoadClass> =
        listOf(
            RoadClass.TERTIARY_LINK,
            RoadClass.TERTIARY,
            RoadClass.SECONDARY_LINK,
            RoadClass.SECONDARY,
            RoadClass.PRIMARY_LINK,
            RoadClass.PRIMARY,
            RoadClass.TRUNK_LINK,
            RoadClass.TRUNK,
            RoadClass.MOTORWAY_LINK,
            RoadClass.MOTORWAY,
        )

    private val motorwayStyle = RoadStyle(fill = Color(0xFFE892A2), widthMeters = 16f)
    private val trunkStyle = RoadStyle(fill = Color(0xFFF9B29C), widthMeters = 14f)
    private val primaryStyle = RoadStyle(fill = Color(0xFFFCD6A4), widthMeters = 12f)
    private val secondaryStyle = RoadStyle(fill = Color(0xFFF7FABF), widthMeters = 10f)
    private val tertiaryStyle = RoadStyle(fill = Color.White, widthMeters = 8f)

    /** Auffahrten und Verbindungsrampen sind schmaler als die Straße selbst. */
    private const val LINK_WIDTH_FACTOR = 0.6f

    fun road(roadClass: RoadClass): RoadStyle =
        when (roadClass) {
            RoadClass.MOTORWAY -> motorwayStyle
            RoadClass.TRUNK -> trunkStyle
            RoadClass.PRIMARY -> primaryStyle
            RoadClass.SECONDARY -> secondaryStyle
            RoadClass.TERTIARY, RoadClass.UNCLASSIFIED, RoadClass.RESIDENTIAL, RoadClass.LIVING_STREET -> tertiaryStyle
            RoadClass.MOTORWAY_LINK -> motorwayStyle.link()
            RoadClass.TRUNK_LINK -> trunkStyle.link()
            RoadClass.PRIMARY_LINK -> primaryStyle.link()
            RoadClass.SECONDARY_LINK -> secondaryStyle.link()
            RoadClass.TERTIARY_LINK -> tertiaryStyle.link()
        }

    private fun RoadStyle.link() = copy(widthMeters = widthMeters * LINK_WIDTH_FACTOR)
}
