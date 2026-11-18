package de.bfi.trafficmaniac.sim

/** Busspur auf [segment] ein- oder ausschalten; `false`, wenn keine Richtung zwei Spuren hat. */
fun RoadGraph.setSegmentBusLane(
    segment: Int,
    on: Boolean,
): Boolean {
    val wide = segmentLinks[segment].filter { linkLanes[it] >= 2 }
    for (link in wide) linkBusLane[link] = on
    return wide.isNotEmpty()
}

/** Spur der Busspur auf [link] oder -1. */
fun RoadGraph.busLane(link: Int): Int = if (linkBusLane[link]) linkLanes[link] - 1 else -1

/** Busvorrang an allen Zufahrten von [node]. */
fun RoadGraph.setNodeBusPriority(
    node: Int,
    on: Boolean,
) {
    for (link in inLinks[node]) linkBusPriority[link] = on
}
