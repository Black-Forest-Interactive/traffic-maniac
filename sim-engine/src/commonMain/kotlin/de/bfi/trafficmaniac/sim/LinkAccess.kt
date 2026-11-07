package de.bfi.trafficmaniac.sim

// Wer wo fahren darf: Sperrungen, Umleitungen (mit zugeschalteten Nebenstraßen) und Gewichtslimits.
// Ausgelagert aus RoadGraph, damit der Graph selbst übersichtlich bleibt.

/** Befahrbar: freigegeben und nicht gesperrt. */
fun RoadGraph.isOpen(link: Int): Boolean = linkEnabled[link] && !linkClosed[link]

fun RoadGraph.isClosed(link: Int): Boolean = linkClosed[link]

fun RoadGraph.isDetour(link: Int): Boolean = linkDetour[link]

/** Darf ein Fahrzeug mit [tons] Gesamtgewicht laut Schild hier fahren? */
fun RoadGraph.allowsWeight(
    link: Int,
    tons: Double,
): Boolean = tons <= linkMaxWeightTons[link]

/** Sperrt beide Fahrtrichtungen eines Abschnitts oder gibt sie wieder frei. */
internal fun RoadGraph.setSegmentClosed(
    segment: Int,
    closed: Boolean,
) {
    for (link in segmentLinks[segment]) linkClosed[link] = closed
}

/**
 * Nimmt einen Abschnitt in die Umleitung auf oder wieder heraus. Passive Nebenstraßen werden dabei
 * zugeschaltet bzw. wieder abgeschaltet.
 */
internal fun RoadGraph.setSegmentDetour(
    segment: Int,
    detour: Boolean,
) {
    for (link in segmentLinks[segment]) {
        linkDetour[link] = detour
        if (linkPassive[link]) linkEnabled[link] = detour
    }
    hasDetours = linkDetour.any { it }
    val added = segmentLinks[segment].filter { it !in simulatedLinks }
    if (detour && added.isNotEmpty()) simulatedLinks = (simulatedLinks + added).sortedArray()
}

/** Gewichtslimit für beide Richtungen; `null` hebt es auf (auf das Limit aus OSM). */
internal fun RoadGraph.setSegmentWeightLimit(
    segment: Int,
    tons: Float?,
) {
    for (link in segmentLinks[segment]) linkMaxWeightTons[link] = tons ?: Float.POSITIVE_INFINITY
    hasWeightLimits = linkMaxWeightTons.any { it.isFinite() }
}

/** Gesamter Tempo-Faktor aus Vorfällen, Straßenzustand und Baustellen. */
internal fun RoadGraph.speedFactor(link: Int): Float = linkSlowdown[link] * linkRoadFactor[link]
