package de.bfi.trafficmaniac.osmimport

import de.bfi.trafficmaniac.city.CityMapCodec
import de.bfi.trafficmaniac.city.RoadClass
import java.io.File
import kotlin.math.hypot
import kotlin.system.exitProcess
import kotlin.time.measureTimedValue

private const val USAGE = """Aufruf: osm-import <eingabe.osm.pbf> <ausgabe.city> [Optionen]

Optionen:
  --name <Name>                         Name der Stadt (Standard: Pforzheim)
  --bbox <minLon,minLat,maxLon,maxLat>  Ausschnitt (Standard: Pforzheim)
  --road-classes <klasse,klasse,...>    simulierte OSM-Straßenklassen
                                        (Standard: trunk,trunk_link,primary,primary_link,secondary,secondary_link)
  --passive-road-classes <klasse,...>   Nebenstraßen für Umleitungen, "none" für keine
                                        (Standard: tertiary,tertiary_link,unclassified,residential,living_street)"""

private const val METERS_PER_KM = 1000.0
private const val BYTES_PER_KB = 1024

/** Kommandozeilenwerkzeug: OSM-Extrakt → Stadtdatei. */
fun main(args: Array<String>) {
    val (input, output, config) =
        try {
            parseArgs(args)
        } catch (e: IllegalArgumentException) {
            println(e.message)
            println(USAGE)
            exitProcess(1)
        }
    require(input.isFile) { "Input file not found: $input" }

    println("Lese ${input.name} für ${config.name} …")
    val (map, duration) =
        measureTimedValue {
            val extractor = CityExtractor(config)
            PbfReader.read(input, extractor)
            extractor.build()
        }

    output.parentFile?.mkdirs()
    output.writeBytes(CityMapCodec.encode(map))

    val roadKm =
        map.roads.segments.sumOf { segment ->
            val g = segment.geometry
            (1 until g.pointCount).sumOf { i -> hypot(g.x(i) - g.x(i - 1), g.y(i) - g.y(i - 1)).toDouble() }
        } / METERS_PER_KM
    println(
        """
        Fertig in $duration → $output (${output.length() / BYTES_PER_KB} KB)
          Straßennetz: ${map.roads.nodes.size} Knoten, ${map.roads.segments.count {
            !it.passive
        }} Abschnitte, ${"%.1f".format(
            roadKm,
        )} km (mit Nebenstraßen)
          Nebenstraßen: ${map.roads.segments.count { it.passive }} passive Abschnitte für Umleitungen
          Brücken:     ${map.roads.segments.count {
            it.bridge && !it.passive
        }} Abschnitte (${map.roads.segments.count { it.bridge && it.passive }} an Nebenstraßen)
          Ampeln:      ${map.roads.nodes.count { it.trafficSignals }}
          Zonen:       ${map.zones.groupingBy { it.kind }.eachCount()}
          Einrichtungen: ${map.facilities.groupingBy { it.kind }.eachCount()}
          Kulisse:     ${map.scenery.buildings.size} Gebäude, ${map.scenery.minorRoads.size} Nebenstraßen, ${map.scenery.green.size} Grünflächen
        """.trimIndent(),
    )
}

private data class Arguments(
    val input: File,
    val output: File,
    val config: ImportConfig,
)

/** Wirft [IllegalArgumentException] bei ungültigen Argumenten. */
private fun parseArgs(args: Array<String>): Arguments {
    val positional = ArrayList<String>()
    var config = ImportConfig()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            val value = requireNotNull(args.getOrNull(i + 1)) { "Missing value for $arg" }
            config =
                when (arg) {
                    "--name" -> config.copy(name = value)
                    "--bbox" -> config.copy(box = GeoBox.parse(value))
                    "--road-classes" -> config.copy(roadClasses = parseRoadClasses(value))
                    "--passive-road-classes" -> config.copy(passiveRoadClasses = parseRoadClasses(value))
                    else -> throw IllegalArgumentException("Unknown option $arg")
                }
            i += 2
        } else {
            positional += arg
            i++
        }
    }
    require(positional.size == 2) { "Expected input and output file" }
    return Arguments(File(positional[0]), File(positional[1]), config)
}

private fun parseRoadClasses(value: String): Set<RoadClass> =
    if (value == "none") {
        emptySet()
    } else {
        value
            .split(',')
            .map { name -> requireNotNull(RoadClass.fromOsm(name.trim())) { "Unknown road class '$name'" } }
            .toSet()
    }
