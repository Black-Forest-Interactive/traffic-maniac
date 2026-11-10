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
            is GameCommand.PlacePoliceCheck -> placePoliceCheck(command.segment)
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

    /** Laufende Polizeikontrollen: Blitzer-ID → Ende (Simulationszeit). */
    private val checks = LinkedHashMap<Int, Double>()

    private fun placePoliceCheck(segment: Int) {
        if (checks.size >= rules.maxPoliceChecks) {
            return message(
                tr(
                    "Mehr Streifen hat das Revier nicht – höchstens {max} Kontrollen gleichzeitig",
                    "max" to rules.maxPoliceChecks,
                ),
            )
        }
        if (works.cameraCounters.values.any { it.segment == segment }) return message(tr("Hier blitzt es schon"))
        if (!treasury.pay(rules.policeCheckCost, tr("Polizeikontrolle"))) return
        val camera = simulation.speedControl.placePoliceCheck(segment)
        works.cameraCounters[camera.id] = CameraCounter(camera.id, segment, streetName(segment), camera = camera)
        checks[camera.id] = simulation.time + rules.policeCheckHours * SECONDS_PER_HOUR
        message(tr("Polizeikontrolle: {street}", "street" to streetName(segment)))
    }

    /** Jeden Schritt: Restzeit der Kontrollen fortschreiben, abgelaufene beenden. */
    fun update() {
        if (checks.isEmpty()) return
        val now = simulation.time
        val over = checks.filterValues { it <= now }.keys
        for ((id, end) in checks) works.cameraCounters[id]?.minutesLeft = ((end - now) / SECONDS_PER_MINUTE).toInt()
        for (id in over) {
            checks.remove(id)
            simulation.speedControl.removeCamera(id)
            val counter = works.cameraCounters.remove(id) ?: continue
            val record = counter.toRecord()
            message(
                tr(
                    "Polizeikontrolle beendet: {street} ({count} Verstöße, {revenue})",
                    "street" to record.streetName,
                    "count" to record.totalCount,
                    "revenue" to formatEuro(record.totalRevenue),
                ),
            )
        }
    }

    private fun streetName(segment: Int): String = city.roads.segments[segment].name ?: tr("Straße ohne Namen")

    private companion object {
        const val SECONDS_PER_HOUR = 3_600.0
        const val SECONDS_PER_MINUTE = 60.0
    }
}
