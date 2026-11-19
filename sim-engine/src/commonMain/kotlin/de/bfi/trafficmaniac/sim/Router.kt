package de.bfi.trafficmaniac.sim

import kotlin.math.hypot

/**
 * Kürzeste Wege nach Fahrzeit (A*) über die Links. Wenden auf derselben Straße ist nur in Sackgassen erlaubt.
 * Die Fahrzeiten werden während der Simulation aus dem beobachteten Verkehr nachgeführt ("Navi-Effekt").
 */
class Router(
    private val graph: RoadGraph,
) {
    /** Geschätzte Fahrzeit je Link in Sekunden. */
    val travelTime: FloatArray = FloatArray(graph.linkCount) { freeFlowTime(it) }

    private val maxSpeed = graph.linkSpeedLimit.max()
    private val cost = DoubleArray(graph.linkCount)
    private val previous = IntArray(graph.linkCount)
    private val seenStamp = IntArray(graph.linkCount)
    private val closedStamp = IntArray(graph.linkCount)
    private var stamp = 0
    private val heap = MinHeap()

    fun freeFlowTime(link: Int): Float = graph.linkLength[link] / graph.linkSpeedLimit[link]

    /** Verkehrsfluss je Abschnitt: freie / beobachtete Fahrzeit des langsamsten Links (1 = frei). */
    internal fun segmentFlow(): FloatArray =
        FloatArray(graph.segmentCount) { segment ->
            graph.segmentLinks[segment].minOf { (freeFlowTime(it) / travelTime[it]).coerceIn(0f, 1f) }
        }

    /** Fahrzeit einer Route bei freier Fahrt und den ursprünglichen Tempolimits. */
    fun referenceTime(route: IntArray): Double =
        route.sumOf { link -> graph.linkLength[link] * KMH_PER_MS / graph.linkPerceivedSpeedKmh[link] }

    /** Fließt eine gemessene Fahrzeit über den Link in die Schätzung ein (gleitender Mittelwert). */
    fun observe(
        link: Int,
        seconds: Double,
    ) {
        val observed = maxOf(seconds.toFloat(), freeFlowTime(link))
        travelTime[link] = travelTime[link] * (1 - OBSERVATION_WEIGHT) + observed * OBSERVATION_WEIGHT
    }

    /**
     * Lässt eine Schätzung langsam zur freien Fahrt zurückkehren. Ohne das würde ein Stau auf einer
     * Straße, die danach alle meiden, für immer im Navi stehen bleiben.
     */
    fun relax(link: Int) {
        travelTime[link] = travelTime[link] * (1 - RELAX_WEIGHT) + freeFlowTime(link) * RELAX_WEIGHT
    }

    /**
     * Lange Warteschlangen fließen schon vor der ersten Ausfahrt in die Fahrzeitschätzung ein;
     * auf Links ohne Stau kehrt die Schätzung langsam zur freien Fahrt zurück.
     */
    internal fun updateFromQueues(lanes: LaneQueues) {
        for (link in graph.simulatedLinks) {
            val wait = lanes.longestFrontWait(link)
            if (wait > 0) raiseTo(link, freeFlowTime(link) + wait) else relax(link)
        }
    }

    /** Hebt die Schätzung mindestens auf [seconds] an, z. B. bei einer langen Warteschlange. */
    fun raiseTo(
        link: Int,
        seconds: Double,
    ) {
        travelTime[link] = maxOf(travelTime[link], seconds.toFloat())
    }

    /**
     * Route als Folge von Links von [fromNode] nach [toNode] oder `null`, wenn es keine gibt.
     * [forbiddenFirstLink] verhindert z. B. das Wenden direkt nach einem Link.
     */
    fun route(
        fromNode: Int,
        toNode: Int,
        forbiddenFirstLink: Int = -1,
        /** Gesamtgewicht in Tonnen: Abschnitte mit kleinerem Gewichtslimit werden gemieden. */
        weightTons: Double = 0.0,
        /** Folgt der ausgeschilderten Umleitung (Ortsfremde): Umleitungsabschnitte wirken kürzer. */
        followsDetour: Boolean = false,
    ): IntArray? {
        if (fromNode == toNode) return null
        stamp++
        heap.clear()
        this.weightTons = weightTons
        this.followsDetour = followsDetour && graph.hasDetours
        for (link in graph.outLinks[fromNode]) {
            if (link != forbiddenFirstLink && usable(link)) relax(link, -1, cost(link), toNode)
        }
        while (heap.isNotEmpty()) {
            val link = heap.pop()
            if (closedStamp[link] == stamp) continue
            closedStamp[link] = stamp
            val node = graph.linkTo[link]
            if (node == toNode) return path(link)
            val outs = graph.outLinks[node]
            for (next in outs) {
                if ((next != graph.linkReverse[link] || outs.size == 1) && usable(next)) {
                    relax(next, link, cost[link] + cost(next), toNode)
                }
            }
        }
        return null
    }

    /** LKW meiden Gewichtslimits, Ortsfremde folgen der Umleitung – beides nur, wenn es so etwas gerade gibt. */
    internal fun planFor(
        vehicle: Vehicle,
        trip: Trip,
    ) {
        val heavy = graph.hasWeightLimits && vehicle.routingTons > 0
        val detour = graph.hasDetours && vehicle.followsDetour
        if (!heavy && !detour) return
        route(trip.originNode, trip.destinationNode, weightTons = vehicle.routingTons, followsDetour = detour)
            ?.let { vehicle.route = it }
    }

    /** Neue Route ab dem Ende des aktuellen Links (ohne zu wenden); bleibt bei der alten, wenn es keine gibt. */
    internal fun reroute(
        vehicle: Vehicle,
        time: Double,
    ) {
        vehicle.lastRerouteTime = time
        val link = vehicle.link
        val path =
            route(
                graph.linkTo[link],
                vehicle.destinationNode,
                forbiddenFirstLink = graph.linkReverse[link],
                weightTons = vehicle.routingTons,
                followsDetour = vehicle.followsDetour,
            ) ?: return
        vehicle.route = intArrayOf(link) + path
        vehicle.routeIndex = 0
    }

    private var weightTons = 0.0
    private var followsDetour = false

    private fun usable(link: Int): Boolean = graph.isOpen(link) && graph.allowsWeight(link, weightTons)

    private fun cost(link: Int): Double =
        travelTime[link] * if (followsDetour && graph.isDetour(link)) DETOUR_FACTOR else 1.0

    private fun relax(
        link: Int,
        from: Int,
        newCost: Double,
        toNode: Int,
    ) {
        if (closedStamp[link] == stamp || (seenStamp[link] == stamp && cost[link] <= newCost)) return
        seenStamp[link] = stamp
        cost[link] = newCost
        previous[link] = from
        val end = graph.linkTo[link]
        val remaining = hypot(graph.nodeX[end] - graph.nodeX[toNode], graph.nodeY[end] - graph.nodeY[toNode]) / maxSpeed
        heap.push(link, newCost + remaining)
    }

    private fun path(last: Int): IntArray {
        val links = ArrayList<Int>()
        var link = last
        while (link >= 0) {
            links += link
            link = previous[link]
        }
        return links.asReversed().toIntArray()
    }

    private companion object {
        const val OBSERVATION_WEIGHT = 0.2f

        /** Bei Aufruf alle 10 s: Halbwertszeit rund 6 Minuten. */
        const val RELAX_WEIGHT = 0.02f

        /** Ausgeschilderte Umleitungen "fühlen" sich für Ortsfremde nur halb so lang an. */
        const val DETOUR_FACTOR = 0.5
    }

    /** Binärer Min-Heap für (Link, Priorität). */
    private class MinHeap {
        private var items = IntArray(INITIAL_CAPACITY)
        private var priorities = DoubleArray(INITIAL_CAPACITY)
        private var size = 0

        fun isNotEmpty() = size > 0

        fun clear() {
            size = 0
        }

        fun push(
            item: Int,
            priority: Double,
        ) {
            if (size == items.size) {
                items = items.copyOf(size * 2)
                priorities = priorities.copyOf(size * 2)
            }
            var i = size++
            while (i > 0) {
                val parent = (i - 1) / 2
                if (priorities[parent] <= priority) break
                items[i] = items[parent]
                priorities[i] = priorities[parent]
                i = parent
            }
            items[i] = item
            priorities[i] = priority
        }

        fun pop(): Int {
            val result = items[0]
            size--
            val item = items[size]
            val priority = priorities[size]
            var i = 0
            while (true) {
                var child = 2 * i + 1
                if (child >= size) break
                if (child + 1 < size && priorities[child + 1] < priorities[child]) child++
                if (priorities[child] >= priority) break
                items[i] = items[child]
                priorities[i] = priorities[child]
                i = child
            }
            items[i] = item
            priorities[i] = priority
            return result
        }

        private companion object {
            const val INITIAL_CAPACITY = 256
        }
    }
}
