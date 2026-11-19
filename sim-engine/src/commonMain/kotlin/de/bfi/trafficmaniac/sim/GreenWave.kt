package de.bfi.trafficmaniac.sim

/**
 * Fahrtrichtungen entlang einer Folge zusammenhängender Abschnitte, vom ersten zum letzten. Abschnitte, die in diese
 * Richtung nicht befahrbar sind (Einbahnstraße dagegen), werden übersprungen; zusammenhanglose Folgen ergeben eine
 * leere Liste.
 */
fun RoadGraph.linksAlong(segments: List<Int>): List<Int> {
    if (segments.isEmpty()) return emptyList()
    // Startknoten: das Ende des ersten Abschnitts, das nicht zum zweiten führt
    val firstLinks = segmentLinks[segments.first()]
    val next = segments.getOrNull(1)
    var node =
        if (next == null) {
            linkFrom[firstLinks.first()]
        } else {
            val nextNodes = segmentLinks[next].flatMap { listOf(linkFrom[it], linkTo[it]) }.toSet()
            firstLinks.map { linkFrom[it] }.firstOrNull { it !in nextNodes } ?: linkFrom[firstLinks.first()]
        }
    val result = ArrayList<Int>()
    for (segment in segments) {
        val links = segmentLinks[segment]
        val along = links.firstOrNull { linkFrom[it] == node }
        val against = links.firstOrNull { linkTo[it] == node }
        when {
            along != null -> {
                result += along
                node = linkTo[along]
            }

            // Einbahnstraße in Gegenrichtung: kein Link, aber der Weg geht am anderen Ende weiter
            against != null -> {
                node = linkFrom[against]
            }

            else -> {
                return emptyList()
            }
        }
    }
    return result
}

/**
 * Grüne Welle entlang [links] (in Fahrtrichtung) für [speedMs] Meter pro Sekunde: Jede Anlage auf dem Weg wird so
 * versetzt, dass ihre Grünphase für diese Richtung beginnt, kurz bevor ein Auto im Wellentempo die Haltelinie
 * erreicht. Die Aufteilung der Grünzeit bleibt, wie sie ist. Liefert die Zahl der Anlagen in der Welle.
 */
fun SignalSystem.greenWave(
    graph: RoadGraph,
    links: List<Int>,
    speedMs: Double,
): Int {
    val offsets = LinkedHashMap<Int, Double>()
    var distance = 0.0
    for (link in links) {
        distance += graph.linkLength[link]
        val plant = plantOf(link) ?: continue
        if (plant in offsets) continue
        val arrival = distance / speedMs - WAVE_LEAD_SECONDS
        val cycle = SignalSystem.CYCLE_SECONDS
        // Grün beginnt, wenn (Zeit + Versatz) im Umlauf den Grünbeginn erreicht – also zur Ankunft
        offsets[plant] = ((greenStartInCycle(link) - arrival) % cycle + cycle) % cycle
    }
    return setPrograms(
        offsets.mapValues { (plant, offset) ->
            (program(plant) ?: SignalProgram()).copy(offsetSeconds = offset)
        },
    )
}

/** Grün kommt so viele Sekunden vor dem ersten Auto der Welle – sonst bremst es noch vor der Haltelinie. */
private const val WAVE_LEAD_SECONDS = 2.0
