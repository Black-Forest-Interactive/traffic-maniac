package de.bfi.trafficmaniac.ui.map

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.bfi.trafficmaniac.city.*
import de.bfi.trafficmaniac.game.CityGame
import de.bfi.trafficmaniac.game.GameCommand
import de.bfi.trafficmaniac.game.TrafficFrame
import de.bfi.trafficmaniac.sim.DemandProfile
import de.bfi.trafficmaniac.sim.EventKind
import de.bfi.trafficmaniac.sim.SimulationConfig
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Zeichnet Karte und Verkehr in allen Detailstufen – nichts darf abstürzen, und es muss etwas zu sehen sein. */
class CartoonRenderTest {
    private fun town(): CityMap {
        val nodes =
            listOf(
                RoadNode(0, -600f, 0f),
                RoadNode(1, 0f, 0f, trafficSignals = true),
                RoadNode(2, 600f, 0f),
                RoadNode(3, 0f, 600f),
            )

        fun road(
            id: Int,
            from: Int,
            to: Int,
        ) = RoadSegment(
            id = id,
            from = from,
            to = to,
            roadClass = RoadClass.PRIMARY,
            geometry = Polyline(floatArrayOf(nodes[from].x, nodes[from].y, nodes[to].x, nodes[to].y)),
            lanesForward = 1,
            lanesBackward = 1,
            maxSpeedKmh = 50,
            name = "Hauptstraße $id",
        )
        val square = Polyline(floatArrayOf(50f, 50f, 120f, 50f, 120f, 120f, 50f, 120f))
        return CityMap(
            name = "Comicstadt",
            attribution = "",
            origin = GeoPoint(0.0, 0.0),
            bounds = Bounds(-600f, -100f, 600f, 600f),
            roads = RoadNetwork(nodes, listOf(road(0, 0, 1), road(1, 1, 2), road(2, 1, 3))),
            scenery =
                Scenery(
                    buildings = listOf(square),
                    green = listOf(Polyline(floatArrayOf(-300f, 50f, -100f, 50f, -100f, 250f))),
                    waterways = listOf(Polyline(floatArrayOf(-600f, -80f, 600f, -80f))),
                ),
            zones =
                listOf(
                    Zone(0, ZoneKind.RESIDENTIAL, -600f, 0f, area = 10_000f, node = 0),
                    Zone(1, ZoneKind.COMMERCIAL, 600f, 0f, area = 10_000f, node = 2),
                    Zone(2, ZoneKind.COMMERCIAL, 0f, 600f, area = 10_000f, node = 3),
                ),
        )
    }

    @Test
    fun drawsMapAndTrafficAtEveryZoomLevel() {
        val city = town()
        val config = SimulationConfig(seed = 2, peakTripsPerHour = 3_000.0, demandProfile = DemandProfile.CONSTANT)
        val game = CityGame(city, config)
        game.submit(GameCommand.PlaceCamera(0))
        repeat(120 * config.ticksPerSecond) { game.step() }
        game.simulation.events.startConvoy(EventKind.BIKE_CORSO, 0, 2, 3, "Hauptstraße", game.simulation.time)
        game.simulation.infrastructure.closures
            .setWorks(2, true)
        repeat(30 * config.ticksPerSecond) { game.step() }
        val snapshot = game.simulation.snapshot()
        assertTrue(snapshot.vehicleCount > 0, "Es fährt jemand")
        val frame =
            TrafficFrame(snapshot, snapshot, TimeSource.Monotonic.markNow(), 1.seconds, 0.0, 1, 0, null, game.state())
        val layers = MapLayers(city)
        val paths = SegmentPaths(city)
        val picker = MapPicker(city)
        val density = Density(1f)
        val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
        val flashes = FlashTracker()
        val flow = FlowLayer(paths)
        val clouds = AngerClouds()
        // Gesamtansicht (Staufarben), mittlerer Zoom (Striche), nah dran (Sprites, Gesichter, Namen)
        for (scale in listOf(0.2f, 0.8f, 6f)) {
            val camera = Camera(0f, 0f, scale)
            val map = RenderedMap.render(layers, camera, WIDTH, HEIGHT, density, false, measurer)
            CanvasDrawScope().draw(
                density,
                LayoutDirection.Ltr,
                Canvas(map.image),
                Size(WIDTH.toFloat(), HEIGHT.toFloat()),
            ) {
                val viewport = camera.viewport(size.width, size.height)
                flashes.update(frame.game.cameras, 1.0)
                inWorld(camera) {
                    flow.draw(this, snapshot.segmentFlow, scale)
                    drawWorksAndDetours(paths, snapshot.infrastructure, scale)
                    drawEvents(paths, snapshot.events, scale)
                    drawExcavators(snapshot.infrastructure.worksSegments.map { picker.midpoint(it) }, scale)
                    drawIncidents(snapshot.incidents, scale)
                    drawVehicles(frame, scale, viewport, 1.0)
                    drawSignals(snapshot.signalMarkers, snapshot.signalStates, scale, true, viewport)
                    drawCameras(snapshot.cameras, scale)
                    drawCameraFlashes(snapshot.cameras, flashes, scale, 1.2)
                    clouds.draw(this, snapshot, scale, 1.0)
                }
            }
            val pixels = map.image.toPixelMap()
            val colors = (0 until WIDTH step STEP).flatMap { x -> (0 until HEIGHT step STEP).map { y -> pixels[x, y] } }
            assertTrue(colors.toSet().size > 3, "Bei Zoom $scale ist mehr als eine leere Fläche zu sehen")
        }
    }

    /** Zählt, welche Grafiken angefordert wurden, und liefert leere Bilder. */
    private class RecordingSprites : SpriteSource {
        val requested = HashSet<String>()

        override fun has(name: String) = true

        override fun image(
            name: String,
            widthPx: Int,
        ): ImageBitmap {
            requested += name
            return ImageBitmap(widthPx, widthPx / 2)
        }
    }

    @Test
    fun usesSpritesWhereTheyExist() {
        val city = town()
        val config = SimulationConfig(seed = 2, peakTripsPerHour = 3_000.0, demandProfile = DemandProfile.CONSTANT)
        val game = CityGame(city, config)
        game.submit(GameCommand.PlaceCamera(0))
        repeat(120 * config.ticksPerSecond) { game.step() }
        val snapshot = game.simulation.snapshot()
        val frame =
            TrafficFrame(snapshot, snapshot, TimeSource.Monotonic.markNow(), 1.seconds, 0.0, 1, 0, null, game.state())
        val sprites = RecordingSprites()
        val image = ImageBitmap(WIDTH, HEIGHT)
        val camera = Camera(0f, 0f, SPRITE_ZOOM)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(WIDTH.toFloat(), HEIGHT.toFloat()),
        ) {
            val viewport = camera.viewport(size.width, size.height)
            inWorld(camera) {
                drawVehicles(frame, SPRITE_ZOOM, viewport, 1.0, sprites)
                drawSignals(snapshot.signalMarkers, snapshot.signalStates, SPRITE_ZOOM, true, viewport, sprites)
                drawCameras(snapshot.cameras, SPRITE_ZOOM, sprites)
            }
        }
        assertTrue(
            sprites.requested.any { it.startsWith("car_") || it == "van" || it == "truck" },
            "${sprites.requested}",
        )
        assertTrue(sprites.requested.any { it.startsWith("signal_") }, "${sprites.requested}")
        assertTrue("camera_fixed" in sprites.requested)
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val STEP = 7
        const val SPRITE_ZOOM = 3f
    }
}
