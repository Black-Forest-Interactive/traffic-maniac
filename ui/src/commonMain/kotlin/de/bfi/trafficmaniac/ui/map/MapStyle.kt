package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.graphics.Color
import de.bfi.trafficmaniac.city.RoadClass

/**
 * Cartoon-Kartenstil: kräftige, leicht übersättigte Farben, dicke Straßen mit dunklem "Tusche"-Umriss,
 * vereinfachte Gebäude mit Schlagschatten, viel Grün und leuchtend blaue Enz und Nagold.
 */
object MapStyle {
    /** Tusche: Umrisse von Straßen, Gebäuden und Figuren. */
    val ink = Color(0xFF3B2F2F)
    val land = Color(0xFFF7EBC8)
    val green = Color(0xFF9BD770)
    val greenOutline = Color(0xFF5FA845)
    val water = Color(0xFF5EC3F2)
    val waterOutline = Color(0xFF2F8FCB)
    val building = Color(0xFFF2C6A0)
    val buildingShadow = Color(0x40311F1F)
    val minorRoad = Color(0xFFFFFFFF)
    val minorRoadOutline = Color(0xFFB9A88F)
    val motorway = Color(0xFFE8A0A0)
    val roadOutline = ink
    val centerLine = Color(0xCCFFFFFF)
    val label = Color(0xFF2B2121)
    val labelHalo = Color(0xE6FFF8E7)
    val trafficSignal = Color(0xFFD7263D)
    val signalGreen = Color(0xFF2DC653)
    val signalYellow = Color(0xFFFFC300)
    val signalOff = Color(0xFF6C6F7D)
    val signalHousing = Color(0xFF22252B)
    val lens = Color(0xFF14213D)
    val excavator = Color(0xFFFFB703)
    val angerCloud = Color(0xFF5A5560)
    val bolt = Color(0xFFFF3B30)
    val camera = Color(0xFF8D99AE)
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
    val limitZone = Color(0x40E63946)
    val nightZone = Color(0xB33A3D9E)
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

    const val WATERWAY_WIDTH_METERS = 14f
    const val MINOR_ROAD_WIDTH_METERS = 6f
    const val MOTORWAY_WIDTH_METERS = 20f
    const val OUTLINE_METERS = 2.5f
    const val SCENERY_OUTLINE_METERS = 1.2f

    /** Schlagschatten der Gebäude nach Südosten (Meter). */
    const val SHADOW_OFFSET_METERS = 2.5f

    /** Detailstufen (Pixel pro Meter): Gebäudeumrisse und Mittellinien, Straßennamen, Fahrzeug-Sprites. */
    const val OUTLINE_MIN_SCALE = 0.8f
    const val LABEL_MIN_SCALE = 0.9f
    const val CENTER_LINE_MIN_SCALE = 2f

    /** Darunter zeigt die Karte Staufarben statt einzelner Fahrzeuge. */
    const val FLOW_MAX_SCALE = 0.3f
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

    private val motorwayStyle = RoadStyle(fill = Color(0xFFFF6B6B), widthMeters = 20f)
    private val trunkStyle = RoadStyle(fill = Color(0xFFFF9F59), widthMeters = 17f)
    private val primaryStyle = RoadStyle(fill = Color(0xFFFFD166), widthMeters = 15f)
    private val secondaryStyle = RoadStyle(fill = Color(0xFFFFF1A6), widthMeters = 12f)
    private val tertiaryStyle = RoadStyle(fill = Color.White, widthMeters = 10f)

    /** Staufarben: frei → zäh → Stillstand. */
    val flowFree = Color(0xFF2DC653)
    val flowSlow = Color(0xFFFFB703)
    val flowJam = Color(0xFFD62828)

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
