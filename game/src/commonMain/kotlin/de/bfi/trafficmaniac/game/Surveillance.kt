package de.bfi.trafficmaniac.game

import de.bfi.trafficmaniac.city.CityMap
import de.bfi.trafficmaniac.i18n.tr
import de.bfi.trafficmaniac.sim.Simulation

/** Rotlichtblitzer an Ampelzufahrten und Geschwindigkeits-Anzeigetafeln – zusätzlich zu den Blitzern von [works]. */
internal class Surveillance(
    private val city: CityMap,
    private val simulation: Simulation,
    private val rules: GameRules,
    private val treasury: Treasury,
    private val works: PublicWorks,
    private val message: (String) -> Unit,
) {
    fun execute(command: GameCommand) {
        when (command) {
            is GameCommand.PlaceRedLightCamera -> placeRedLight(command.link, command.withSpeed)
            is GameCommand.PlaceDisplay -> placeDisplay(command.segment)
            else -> Unit
        }
    }

    private fun placeRedLight(
        link: Int,
        withSpeed: Boolean,
    ) {
        val speed = simulation.speedControl
        if (!simulation.signals.isControlled(link)) return message(tr("Hier ist keine Ampel"))
        if (speed.cameras.any { it.redLight && it.link == link }) return message(tr("Hier blitzt es schon"))
        val cost = if (withSpeed) rules.redLightSpeedCameraCost else rules.redLightCameraCost
        if (!treasury.pay(cost, tr("Rotlichtblitzer"))) return
        val camera = speed.placeRedLightCamera(link, withSpeed)
        val street = streetName(camera.segment)
        works.cameraCounters[camera.id] = CameraCounter(camera.id, camera.segment, street, camera = camera)
        message(
            tr(
                "{what} scharf geschaltet: {street}",
                "what" to if (withSpeed) tr("Rotlicht- und Tempoblitzer") else tr("Rotlichtblitzer"),
                "street" to street,
            ),
        )
    }

    private fun placeDisplay(segment: Int) {
        if (works.cameraCounters.values.any { it.segment == segment }) return message(tr("Hier blitzt es schon"))
        if (!treasury.pay(rules.displayCost, tr("Anzeigetafel"))) return
        val camera = simulation.speedControl.placeDisplay(segment)
        works.cameraCounters[camera.id] = CameraCounter(camera.id, segment, streetName(segment), camera = camera)
        message(tr("Anzeigetafel aufgestellt: {street}", "street" to streetName(segment)))
    }

    private fun streetName(segment: Int): String = city.roads.segments[segment].name ?: tr("Straße ohne Namen")
}
